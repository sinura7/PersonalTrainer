package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val alarms: RestTimerAlarmScheduler = RestTimerAlarmScheduler(context.applicationContext),
) : RestTimerGateway {
    private val appContext = context.applicationContext
    private val _lastAlarmSchedule = MutableStateFlow(AlarmScheduleResult.FAILED)
    private val _exactAlarmAttempt = MutableStateFlow(alarms.currentAttempt())
    override val lastAlarmSchedule: StateFlow<AlarmScheduleResult> = _lastAlarmSchedule.asStateFlow()
    override val exactAlarmAttempt: StateFlow<ExactAlarmAttempt> = _exactAlarmAttempt.asStateFlow()

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

    override fun stopIfCurrent(timerId: String, fromService: Boolean): Boolean {
        val current = store.current()
        if (current.running && current.timerId != timerId) return false
        stop(fromService)
        return true
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
                store.restore(
                    endsAtElapsedRealtime = outcome.endsAtElapsedRealtime,
                    totalSeconds = outcome.totalSeconds,
                    sessionId = outcome.sessionId,
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                    timerId = outcome.timerId,
                )
                scheduleAlarmForCurrent()
                dispatch(RestTimerService.ACTION_SYNC)
                true
            }
            is RestTimerRehydration.Expired -> {
                // Rest ended while the process was dead, but recently enough to still matter.
                // This is the alarm-woke-a-dead-process path, so it must alert with sound and
                // vibration, not just a notification — completeOnce owns that, and its
                // id claim means the receiver's own call moments later is a no-op.
                announceScope.launch {
                    RestTimerCompletion.completeOnce(
                        context = appContext,
                        incomingTimerId = outcome.timerId,
                        expectedTimerId = outcome.timerId,
                        deadlineElapsedRealtime = outcome.endsAtElapsedRealtime,
                        sessionId = outcome.sessionId,
                    )
                }
                false
            }
            RestTimerRehydration.None -> {
                persistence?.clear()
                false
            }
        }
    }

    override fun rescheduleCurrent() {
        scheduleAlarmForCurrent()
    }

    override fun refreshAlarmCapability() {
        _exactAlarmAttempt.value = alarms.currentAttempt()
        if (store.current().running) {
            scheduleAlarmForCurrent()
        }
    }

    private fun scheduleAlarmForCurrent() {
        val state = store.current()
        if (!state.running) return
        _exactAlarmAttempt.value = alarms.currentAttempt()
        _lastAlarmSchedule.value = alarms.schedule(
            endsAtElapsedRealtime = state.endsAtElapsedRealtime,
            sessionId = state.sessionId,
            timerId = state.timerId,
        )
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
