package com.sinura.personaltrainer.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.recoverWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.concurrent.atomic.AtomicBoolean
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
    private val _persistenceHealthy = MutableStateFlow(true)
    override val lastCompletedTimerId: StateFlow<String?> = _lastCompletedTimerId.asStateFlow()
    override val lastAlarmSchedule: StateFlow<AlarmScheduleResult> = _lastAlarmSchedule.asStateFlow()
    override val exactAlarmAttempt: StateFlow<ExactAlarmAttempt> = _exactAlarmAttempt.asStateFlow()
    override val persistenceHealthy: StateFlow<Boolean> = _persistenceHealthy.asStateFlow()

    /** Application-lifetime; persist/arm and late-rest announce. */
    private val announceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // A SupervisorJob alone hands an escaped throwable to the default uncaught
    // handler, and this scope runs disk writes on the owner's phone. Nothing
    // here is worth the process; log it and keep the in-memory countdown.
    private val ioScope = CoroutineScope(
        SupervisorJob() + ioDispatcher +
            CoroutineExceptionHandler { _, error -> AppLog.e(TAG, "Rest timer IO failed", error) },
    )
    /** Serialises persist-then-arm. A later call bumps [persistSeq]; an earlier job then does nothing. */
    private val persistLock = Mutex()
    private val persistSeq = AtomicInteger(0)
    /**
     * A SYNC a call asked for that no job has sent yet. Whichever job next writes a running rest
     * sends it; that is after the call published its rest, and the service reads the live rest.
     */
    private val syncOwed = AtomicBoolean(false)

    /**
     * Test seams: run on the calling thread just before and just after a persist call takes its
     * number, the points where a call on another thread can overtake it (ADR-012 decision 1).
     * Null in production.
     */
    @Volatile
    internal var beforePersistNumberTaken: (() -> Unit)? = null

    @Volatile
    internal var afterPersistNumberTaken: (() -> Unit)? = null

    /**
     * Test seam: runs on the calling thread inside [skipIfShown], after the rest on screen is
     * found running and before it is cleared, where a finish on another thread can land. Null in
     * production.
     */
    @Volatile
    internal var betweenSkipReadAndClear: (() -> Unit)? = null
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
        // Choose from what this adjust did, not from a second read of the store: by then
        // another thread may have started, stopped or completed a timer this call knows nothing
        // about. The disk write and the alarm still take the store's latest state; the last
        // caller's job wins.
        when (val adjustment = store.adjust(deltaSeconds, SystemClock.elapsedRealtime())) {
            is RestAdjustment.Running -> persistThenArm(syncService = true)
            is RestAdjustment.Ended -> {
                alarms.cancel()
                persistThenArm(syncService = false)
                dispatch(RestTimerService.ACTION_STOP, timerId = adjustment.timerId)
            }
            // Whoever emptied the store already cancelled the alarm, cleared the row, and the
            // service stops on the snapshot. A STOP from here would run stop() in the service
            // and wipe the "rest done" a completion just published: a finished rest would
            // look skipped, and the lock glance would close.
            RestAdjustment.Idle -> Unit
        }
    }

    override fun markCompleted(timerId: String) {
        if (timerId.isBlank()) return
        _lastCompletedTimerId.value = timerId
    }

    override fun stopIfCurrent(timerId: String, fromService: Boolean): Boolean {
        // One compare-and-set, not a check and then a clear: a +15 landing in between
        // would have minted a replacement that the clear then wiped.
        val cleared = store.clearIfCurrent(timerId) ?: return false
        afterHalt(wasRunning = cleared.running, fromService = fromService, timerId = cleared.timerId)
        return true
    }

    /**
     * Ends the rest a surface showed as [timerId] (the notification card, the lock glance, the
     * rest page), or the ±15 of it that has replaced it since: to the owner a ±15 is the same
     * rest under a new id. A newer rest (the next set's) keeps running, and a rest that finished
     * first keeps its "rest done". Nothing running is left alone: whoever emptied the store
     * already dropped the wakeup and the row.
     */
    override fun skipIfShown(timerId: String, fromService: Boolean): Boolean {
        while (true) {
            val current = store.current()
            if (!current.running || !store.isSameRest(timerId, current.timerId)) return false
            betweenSkipReadAndClear?.invoke()
            // A ±15 or a start that landed after the read replaced it: look again.
            val cleared = store.clearIfCurrent(current.timerId) ?: continue
            // A finish or a stop that landed after the read emptied the store first. Nothing of
            // ours is left to end, and from the app a halt here would take the done card with it.
            if (!cleared.running) return false
            afterHalt(wasRunning = true, fromService = fromService, timerId = cleared.timerId)
            return true
        }
    }

    override fun completeIfCurrent(timerId: String, fromService: Boolean): Boolean {
        val current = store.current()
        if (current.running && current.timerId != timerId) return false
        // An empty store that held this rest was emptied by a Skip, a stop or a -15 to zero, or
        // by a finish that already announced it: an alarm already on its way for it is not a
        // finish. A process started after death has held nothing, so a rest that ran out while
        // it was dead still completes.
        if (!current.running && store.hasHeld(timerId)) return false
        // Publish before clearing running. The lock glance collects those
        // two flows separately; stop-then-mark looks like a skip for one
        // frame and dismisses the "Back to the bar" surface.
        markCompleted(timerId)
        val cleared = store.clearIfCurrent(timerId)
        if (cleared == null || (!cleared.running && store.hasHeld(timerId))) {
            // After the check, a +15 replaced this timer, or a Skip emptied the store: nothing
            // finished. Take the completion back, unless something newer has been published.
            _lastCompletedTimerId.compareAndSet(timerId, null)
            return false
        }
        afterHalt(wasRunning = cleared.running, fromService = fromService, timerId = cleared.timerId)
        return true
    }

    override fun stop(fromService: Boolean) {
        _lastCompletedTimerId.value = null
        halt(fromService)
    }

    private fun halt(fromService: Boolean) {
        val was = store.current()
        store.clear()
        afterHalt(was.running, fromService, was.timerId)
    }

    private fun afterHalt(wasRunning: Boolean, fromService: Boolean, timerId: String) {
        // Drop the wakeup immediately so a cancelled rest cannot fire
        // while the IO job is still clearing the row.
        alarms.cancel()
        persistThenArm(syncService = false)
        if (!fromService) {
            if (wasRunning) {
                dispatch(RestTimerService.ACTION_STOP, timerId = timerId)
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
        // A row for a rest this process held, and no longer runs, is left by a Skip, a stop, a
        // -15 to zero or a finish whose row clear has not landed yet, or failed. Bring nothing
        // back and announce nothing; clear the row again. A process started after death has
        // held nothing.
        val staleTimerId = when (outcome) {
            is RestTimerRehydration.Running -> outcome.timerId
            is RestTimerRehydration.Expired -> outcome.timerId
            RestTimerRehydration.None -> null
        }
        if (staleTimerId != null && store.hasHeld(staleTimerId)) {
            persistThenArm(syncService = false)
            return false
        }
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

    /**
     * An early delivery re-arms through the same queue as every write: the row first, then the
     * wakeup. Arming straight from the store could set a wakeup for a rest whose row is not on
     * disk yet, or never landed; after a process kill that wakeup finds no row and ends the rest
     * in silence (audit RT-4, RT-5).
     */
    override fun rescheduleCurrent() {
        if (!store.current().running) return
        persistThenArm(syncService = false)
    }

    /**
     * A resume, or a change to the exact-alarm grant, re-arms the running rest the same way,
     * row first. Arming at once while a start's job was still queued set the wakeup before the
     * row (audit RT-4); a row that never landed gets another try before anything is armed.
     */
    override fun refreshAlarmCapability() {
        _exactAlarmAttempt.value = alarms.currentAttempt()
        if (!store.current().running) return
        persistThenArm(syncService = false)
    }

    /**
     * Publish already happened on the caller. Persist the row, then arm.
     * The receiver treats a missing disk row as already completed, so the
     * alarm must not be scheduled first — and is not scheduled at all when
     * the row did not commit. Any later call bumps [persistSeq] so an
     * in-flight start skips the whole persist, not just the alarm.
     *
     * No call cancels another's job (ADR-012 decision 1). Calls come from more than one thread
     * (a finish off the main thread, the next rest's start on it), and a cancel could land on a
     * newer call's job: that rest would count down with no row and no wakeup. An older job sees
     * the newer number and does nothing instead. A SYNC is owed until a job that writes a
     * running rest sends it, so a rest started as the last one finished still reaches the
     * service when the finish's job is the newest.
     */
    private fun persistThenArm(syncService: Boolean) {
        // Owed before the number is taken: a newer call can run its job before this call has a
        // job at all, and that job must find the SYNC owed.
        if (syncService) syncOwed.set(true)
        val seq = takeNumber()
        // Read after the number: a call that has not taken one yet published its rest first and
        // will take a newer number, so the newest job always reads the newest rest.
        val snap = store.current()
        ioScope.launch {
            persistLock.withLock {
                if (seq != persistSeq.get()) return@withLock
                if (snap.running) {
                    val saved = saveRow(snap)
                    if (seq != persistSeq.get()) return@withLock
                    if (saved) {
                        armIfLive(snap)
                    } else {
                        // An alarm whose row is missing fires into "already
                        // completed" after a process kill: worse than no
                        // alarm, because it looks armed. An older alarm from
                        // a row that did land is left alone; it still ends
                        // that earlier deadline. The foreground service is
                        // synced regardless so the countdown runs while the
                        // process lives.
                        _lastAlarmSchedule.value = AlarmScheduleResult.FAILED
                        AppLog.w(TAG, "Rest row did not commit; wakeup not armed")
                    }
                    if (syncOwed.getAndSet(false)) dispatch(RestTimerService.ACTION_SYNC)
                } else {
                    clearRow()
                }
            }
        }
    }

    /** The number and its seams, as one step: nothing may sit between the seams and the number. */
    private fun takeNumber(): Int {
        beforePersistNumberTaken?.invoke()
        return persistSeq.incrementAndGet().also { afterPersistNumberTaken?.invoke() }
    }

    /** True when the row is on disk, or there is no disk to write. */
    private fun saveRow(snap: RestTimerSnapshot): Boolean {
        val target = persistence ?: return true
        val saved = recoverWith(TAG, "Rest row save", false) {
            target.save(
                RestTimerRehydrator.toPersisted(
                    endsAtElapsedRealtime = snap.endsAtElapsedRealtime,
                    totalSeconds = snap.totalSeconds,
                    sessionId = snap.sessionId,
                    timerId = snap.timerId,
                ),
            )
        }
        _persistenceHealthy.value = saved
        return saved
    }

    /**
     * One retry, then say so: a row that outlives its rest rehydrates as an
     * expired rest after the next process death and posts a silent
     * "Rest done" for a rest the user already skipped.
     */
    private fun clearRow() {
        val target = persistence ?: return
        var cleared = recoverWith(TAG, "Rest row clear", false) { target.clear() }
        if (!cleared) {
            AppLog.w(TAG, "Rest row did not clear; retrying once")
            cleared = recoverWith(TAG, "Rest row clear retry", false) { target.clear() }
        }
        if (!cleared) {
            AppLog.w(TAG, "Rest row still on disk; a stale rest may rehydrate after process death")
        }
        _persistenceHealthy.value = cleared
    }

    /**
     * Arms the rest whose row this job just wrote, and only while it is still the running rest
     * (every start and every ±15 publishes a new id). Reading the store again instead armed
     * whatever ran now: a rest published after this job's number was taken, but before its own
     * was, got a wakeup over the older rest's row, and a kill then left a wakeup that finds
     * another rest's row and stays silent (audit RT-4). That newer rest's own job, which comes
     * next, writes its row and arms it. A rest ended in that moment is not armed at all: its
     * halt has already dropped the wakeup.
     */
    private fun armIfLive(saved: RestTimerSnapshot) {
        val live = store.current()
        if (!live.running || live.timerId != saved.timerId) return
        _exactAlarmAttempt.value = alarms.currentAttempt()
        _lastAlarmSchedule.value = alarms.schedule(
            endsAtElapsedRealtime = saved.endsAtElapsedRealtime,
            sessionId = saved.sessionId,
            timerId = saved.timerId,
        )
    }

    /**
     * [timerId] names the rest a STOP is for, so the service can tell it from a rest started
     * after the STOP was sent (ADR-012 decision 1).
     */
    private fun dispatch(action: String, timerId: String? = null) {
        val intent = Intent(appContext, RestTimerService::class.java).setAction(action)
        timerId?.let { intent.putExtra(RestTimerService.EXTRA_TIMER_ID, it) }
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
