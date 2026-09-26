package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The workout-facing rest timer contract.
 *
 * ViewModels and use cases need state plus five commands; they do not need
 * AlarmManager, a foreground Service, or an Android Context. Keeping that
 * boundary explicit lets behavior tests use a deterministic in-memory timer
 * while [RestTimerController] remains the Android implementation.
 */
interface RestTimerGateway {
    val snapshot: StateFlow<RestTimerSnapshot>
    val remainingSeconds: Flow<Int>
    val runningSessionId: Flow<String?>
    val lastAlarmSchedule: StateFlow<AlarmScheduleResult>
    val exactAlarmAttempt: StateFlow<ExactAlarmAttempt>
    val lastCompletedTimerId: StateFlow<String?>

    /**
     * False once the running rest has no row on disk (its save came back
     * false, or threw, and no earlier save of that same rest landed) or a
     * clear failed; true again after the next commit that landed. A rewrite
     * that fails while the rest's earlier row stands keeps it true. While
     * false, the wakeup is not armed and the rest only lives as long as the
     * process does. Fakes have no disk, so the default is always healthy.
     */
    val persistenceHealthy: StateFlow<Boolean>
        get() = ALWAYS_HEALTHY

    fun start(totalSeconds: Int, sessionId: String?)
    fun adjust(deltaSeconds: Int)
    fun stop(fromService: Boolean = false)

    /** Completion, not skip. Keys the gold flash. */
    fun markCompleted(timerId: String) {}

    /**
     * Stops only if the live timer still is [timerId]. Completion claims a
     * timer id and must not wipe a NEWER timer the user minted (+15s) between
     * the claim and the stop. Default keeps fakes simple; the production
     * controller really checks — as one compare-and-set on the store
     * ([RestTimerStore.clearIfCurrent]), because a check followed by a clear
     * lets a +15 from another thread land in between and be wiped.
     */
    fun stopIfCurrent(timerId: String, fromService: Boolean = false): Boolean {
        stop(fromService)
        return true
    }

    /**
     * Skip for the rest a surface showed as [timerId]: ends that rest, or the ±15 of it that
     * has replaced it since (to the owner a ±15 is the same rest under a new id). A newer rest
     * (the next set's) keeps running, a rest that finished first keeps its "rest done", and
     * nothing running is left alone. True when it ended a rest.
     *
     * The notification's, the lock glance's and the rest page's Skip come here; the dock's
     * Skip still [stop]s whatever runs (ADR-012, W2b-3). No default: whether a ±15 is the same
     * rest is the store's memory, and a fake that guessed would test nothing.
     */
    fun skipIfShown(timerId: String, fromService: Boolean = false): Boolean

    /**
     * Successful completion: publish [timerId] then halt. A skip goes through
     * [stop] or [skipIfShown] and must not leave a completion id, or the lock
     * glance shows "Back to the bar".
     */
    fun completeIfCurrent(timerId: String, fromService: Boolean = false): Boolean {
        if (!stopIfCurrent(timerId, fromService)) return false
        markCompleted(timerId)
        return true
    }

    fun rehydrate(): Boolean

    /** Current-but-early delivery asks the live rest to be scheduled again. */
    fun rescheduleCurrent() {}

    /**
     * Re-read the exact-alarm grant. A live rest is armed again so a grant
     * or revoke that happened in Settings takes effect without restarting.
     */
    fun refreshAlarmCapability() {}

    companion object {
        private val ALWAYS_HEALTHY: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    }
}
