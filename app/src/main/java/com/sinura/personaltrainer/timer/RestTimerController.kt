package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerController(
    context: Context,
    private val store: RestTimerStore,
    private val persistence: RestTimerStatePersistence? = null,
) : RestTimerGateway {
    private val appContext = context.applicationContext
    private val alarms = RestTimerAlarmScheduler(appContext)

    /** Application-lifetime; only used to announce a rest that ended while we were dead. */
    private val announceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val snapshot: StateFlow<RestTimerSnapshot> = store.snapshot

    override val remainingSeconds: Flow<Int> = snapshot.flatMapLatest { state ->
        if (!state.running) {
            flowOf(0)
        } else {
            flow {
                while (true) {
                    val left = state.remainingSeconds(SystemClock.elapsedRealtime())
                    emit(left)
                    if (left <= 0) break
                    delay(200)
                }
            }
        }
    }.distinctUntilChanged()

    override val runningSessionId: Flow<String?> = snapshot.map { snap ->
        snap.sessionId.takeIf { snap.running }
    }.distinctUntilChanged()

    override fun start(totalSeconds: Int, sessionId: String?) {
        // A new rest must be allowed to announce even though the previous one just did.
        RestTimerCompletion.reset()
        store.start(totalSeconds, sessionId, SystemClock.elapsedRealtime())
        scheduleAlarmForCurrent()
        dispatch(RestTimerService.ACTION_SYNC)
    }

    override fun adjust(deltaSeconds: Int) {
        store.adjust(deltaSeconds, SystemClock.elapsedRealtime())
        if (store.current().running) {
            scheduleAlarmForCurrent()
            dispatch(RestTimerService.ACTION_SYNC)
        } else {
            alarms.cancel()
            dispatch(RestTimerService.ACTION_STOP)
        }
    }

    override fun stop(fromService: Boolean) {
        val wasRunning = store.current().running
        store.clear()
        // Always drop the pending wakeup: a cancelled rest must never fire an alert later.
        alarms.cancel()
        if (!fromService) {
            if (wasRunning) {
                dispatch(RestTimerService.ACTION_STOP)
            }
            RestTimerNotifications.cancelDone(appContext)
        }
    }

    /**
     * Restore a timer that outlived its process (swipe-kill, low-memory kill, reboot).
     * Called at app start and from the service's sticky restart.
     *
     * @return true when a still-running rest was restored.
     */
    override fun rehydrate(): Boolean {
        if (store.current().running) return true
        val outcome = RestTimerRehydrator.rehydrate(
            stored = persistence?.load(),
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            nowWallClockMillis = System.currentTimeMillis(),
        )
        return when (outcome) {
            is RestTimerRehydration.Running -> {
                RestTimerCompletion.reset()
                store.restore(
                    endsAtElapsedRealtime = outcome.endsAtElapsedRealtime,
                    totalSeconds = outcome.totalSeconds,
                    sessionId = outcome.sessionId,
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                )
                scheduleAlarmForCurrent()
                dispatch(RestTimerService.ACTION_SYNC)
                true
            }
            is RestTimerRehydration.Expired -> {
                // Rest ended while the process was dead, but recently enough to still matter.
                // This is the alarm-woke-a-dead-process path, so it must alert with sound and
                // vibration, not just a notification — completeOnce owns that, and its
                // idempotence guard means the receiver's own call moments later is a no-op.
                val endsAtKey = outcome.endsAtElapsedRealtime
                announceScope.launch {
                    RestTimerCompletion.completeOnce(appContext, endsAtKey, outcome.sessionId)
                }
                false
            }
            RestTimerRehydration.None -> {
                persistence?.clear()
                false
            }
        }
    }

    private fun scheduleAlarmForCurrent() {
        val state = store.current()
        if (!state.running) return
        alarms.schedule(state.endsAtElapsedRealtime, state.sessionId)
    }

    private fun dispatch(action: String) {
        val intent = Intent(appContext, RestTimerService::class.java).setAction(action)
        try {
            appContext.startForegroundService(intent)
        } catch (_: Exception) {
            try {
                appContext.startService(intent)
            } catch (_: Exception) {
                // Service cannot start (restricted background). In-app state still updates.
            }
        }
    }
}
