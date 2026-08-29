package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    fun start(totalSeconds: Int, sessionId: String?)
    fun adjust(deltaSeconds: Int)
    fun stop(fromService: Boolean = false)

    /**
     * Stops only if the live timer still is [timerId]. Completion claims a
     * timer id and must not wipe a NEWER timer the user minted (+15s) between
     * the claim and the stop. Default keeps fakes simple; the production
     * controller really checks.
     */
    fun stopIfCurrent(timerId: String, fromService: Boolean = false): Boolean {
        stop(fromService)
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
}
