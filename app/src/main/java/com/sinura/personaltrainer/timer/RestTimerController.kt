package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerController(
    context: Context,
    private val store: RestTimerStore,
    private val persistence: RestTimerStatePersistence? = null,
    private val alarms: RestTimerAlarmScheduler = RestTimerAlarmScheduler(context.applicationContext),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RestTimerGateway {
    private val appContext = context.applicationContext
    private val _lastAlarmSchedule = MutableStateFlow(AlarmScheduleResult.FAILED)
    private val _exactAlarmAttempt = MutableStateFlow(alarms.currentAttempt())
    private val _lastCompletedTimerId = MutableStateFlow<String?>(null)
    override val lastCompletedTimerId: StateFlow<String?> = _lastCompletedTimerId.asStateFlow()
    override val lastAlarmSchedule: StateFlow<AlarmScheduleResult> = _lastAlarmSchedule.asStateFlow()
    override val exactAlarmAttempt: StateFlow<ExactAlarmAttempt> = _exactAlarmAttempt.asStateFlow()

    /** Application-lifetime; persist/arm and late-rest announce. */
    private val announceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    /** Serialises persist-then-arm. A later halt bumps [persistSeq]. */
    private val persistLock = Mutex()
    private var persistJob: Job? = null
    private val persistSeq = AtomicInteger(0)
    override val snapshot: StateFlow<RestTimerSnapshot> = store.snapshot

    /**
     * One poll for every collector. Remaining seconds are an Int, so waking on
     * the next whole-second boundary is enough; 5 Hz per Live-bar / workout /
     * rest-page collector was three clocks for the same number.
     */
    override val remainingSeconds: Flow<Int> = snapshot.flatMapLatest { state ->
        if (!state.running) {
            flowOf(0)
        } else {
            flow {
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val left = state.remainingSeconds(now)
                    emit(left)
                    if (left <= 0) break
                    val leftMs = state.endsAtElapsedRealtime - now
                    val nextBoundaryMs = ((leftMs - 1L) / 1000L) * 1000L
                    delay((leftMs - nextBoundaryMs).coerceAtLeast(1L))
                }
            }
        }
    }.distinctUntilChanged()
        .shareIn(
            scope = announceScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0,
            ),
            replay = 1,
        )

    override val runningSessionId: Flow<String?> = snapshot.map { snap ->
        snap.sessionId.takeIf { snap.running }
    }.distinctUntilChanged()

    override fun start(totalSeconds: Int, sessionId: String?) {
        _lastCompletedTimerId.value = null
        store.start(totalSeconds, sessionId, SystemClock.elapsedRealtime())
        persistThenArm(syncService = true)
    }

    override fun adjust(deltaSeconds: Int) {
        store.adjust(deltaSeconds, SystemClock.elapsedRealtime())
        if (store.current().running) {
            persistThenArm(syncService = true)
        } else {
            alarms.cancel()
            persistThenArm(syncService = false)
            dispatch(RestTimerService.ACTION_STOP)
        }
    }

    override fun markCompleted(timerId: String) {
        if (timerId.isBlank()) return
        _lastCompletedTimerId.value = timerId
    }

    override fun stopIfCurrent(timerId: String, fromService: Boolean): Boolean {
        val current = store.current()
        if (current.running && current.timerId != timerId) return false
        halt(fromService)
        return true
    }

    override fun completeIfCurrent(timerId: String, fromService: Boolean): Boolean {
        val current = store.current()
        if (current.running && current.timerId != timerId) return false
        // Publish before clearing running. The lock glance collects those
        // two flows separately; stop-then-mark looks like a skip for one
        // frame and dismisses the "Back to the bar" surface.
        markCompleted(timerId)
        halt(fromService)
        return true
    }

    override fun stop(fromService: Boolean) {
        _lastCompletedTimerId.value = null
        halt(fromService)
    }

    private fun halt(fromService: Boolean) {
        val wasRunning = store.current().running
        store.clear()
        // Drop the wakeup immediately so a cancelled rest cannot fire
        // while the IO job is still clearing the row.
        alarms.cancel()
        persistThenArm(syncService = false)
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
            nowBootCount = BootSession.count(appContext),
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
                persistThenArm(syncService = true)
                true
            }
            is RestTimerRehydration.Expired -> {
                // Rest ended while the process was dead. Keep the disk row until
                // completeOnce claims it, so a late alarm still has something to
                // match. Cue inside the grace window; silent "Rest done" beyond it.
                announceScope.launch {
                    RestTimerCompletion.completeOnce(
                        context = appContext,
                        incomingTimerId = outcome.timerId,
                        expectedTimerId = outcome.timerId,
                        deadlineElapsedRealtime = outcome.endsAtElapsedRealtime,
                        sessionId = outcome.sessionId,
                        playCue = outcome.playCue,
                    )
                }
                false
            }
            RestTimerRehydration.None -> {
                persistThenArm(syncService = false)
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

    /**
     * Publish already happened on the caller. Persist the row, then arm.
     * The receiver treats a missing disk row as already completed, so the
     * alarm must not be scheduled first. A later [halt] bumps [persistSeq]
     * so an in-flight start skips the whole persist, not just the alarm.
     */
    private fun persistThenArm(syncService: Boolean) {
        val seq = persistSeq.incrementAndGet()
        val snap = store.current()
        persistJob?.cancel()
        persistJob = ioScope.launch {
            persistLock.withLock {
                if (seq != persistSeq.get()) return@withLock
                if (snap.running) {
                    persistence?.save(
                        RestTimerRehydrator.toPersisted(
                            endsAtElapsedRealtime = snap.endsAtElapsedRealtime,
                            totalSeconds = snap.totalSeconds,
                            sessionId = snap.sessionId,
                            timerId = snap.timerId,
                        ),
                    )
                    if (seq != persistSeq.get()) return@withLock
                    scheduleAlarmForCurrent()
                    if (syncService) dispatch(RestTimerService.ACTION_SYNC)
                } else {
                    persistence?.clear()
                }
            }
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
        } catch (error: Exception) {
            AppLog.w(TAG, "startForegroundService failed", error)
            try {
                appContext.startService(intent)
            } catch (fallback: Exception) {
                AppLog.w(TAG, "startService failed", fallback)
            }
        }
    }

    private companion object {
        const val TAG = "PT/RestTimer"
    }
}
