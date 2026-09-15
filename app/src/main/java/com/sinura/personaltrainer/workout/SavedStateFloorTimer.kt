package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.SetStopwatchUiState

/**
 * Packet E: hold and stopwatch timestamps in [SavedStateHandle].
 * No Room table. Rest deadlines stay on the existing rest store.
 */
class SavedStateFloorTimer(private val handle: SavedStateHandle) {
    fun writeHold(exerciseId: String?, hold: HoldTimerUiState) {
        if (exerciseId.isNullOrBlank() || (!hold.running && hold.totalSeconds <= 0)) {
            clearHold()
            return
        }
        handle[KEY_HOLD_EXERCISE] = exerciseId
        handle[KEY_HOLD_RUNNING] = hold.running
        handle[KEY_HOLD_START] = hold.startElapsedRealtime
        handle[KEY_HOLD_DEADLINE] = hold.deadlineElapsedRealtime
        handle[KEY_HOLD_TOTAL] = hold.totalSeconds
        handle[KEY_HOLD_TARGET] = hold.targetReached
    }

    fun readHold(sessionExerciseId: String?, nowElapsedRealtime: Long): HoldTimerUiState? {
        val storedId = handle.get<String>(KEY_HOLD_EXERCISE) ?: return null
        if (sessionExerciseId != null && storedId != sessionExerciseId) return null
        val start = handle.get<Long>(KEY_HOLD_START) ?: return null
        val deadline = handle.get<Long>(KEY_HOLD_DEADLINE) ?: return null
        val total = handle.get<Int>(KEY_HOLD_TOTAL) ?: return null
        if (total <= 0) return null
        if (nowElapsedRealtime < start) {
            clearHold()
            return null
        }
        val running = handle.get<Boolean>(KEY_HOLD_RUNNING) ?: false
        val target = handle.get<Boolean>(KEY_HOLD_TARGET) ?: false
        return HoldTimerUiState(
            running = running && !target,
            remainingSeconds = 0,
            totalSeconds = total,
            elapsedSeconds = 0,
            startElapsedRealtime = start,
            deadlineElapsedRealtime = deadline,
            targetReached = target,
        )
    }

    fun writeStopwatch(exerciseId: String, watch: SetStopwatchUiState) {
        if (exerciseId.isBlank()) return
        val ids = ArrayList(stopwatchIds())
        if (exerciseId !in ids) ids.add(exerciseId)
        handle[KEY_SW_IDS] = ids
        handle[swKey(exerciseId, FIELD_RUNNING)] = watch.running
        handle[swKey(exerciseId, FIELD_START)] = watch.startElapsedRealtime
        handle[swKey(exerciseId, FIELD_FROZEN)] = watch.frozenElapsedSeconds
        handle[swKey(exerciseId, FIELD_ELAPSED)] = watch.elapsedSeconds
        handle[swKey(exerciseId, FIELD_USED)] = watch.used
    }

    fun readStopwatch(exerciseId: String, nowElapsedRealtime: Long): SetStopwatchUiState? {
        if (exerciseId.isBlank()) return null
        val used = handle.get<Boolean>(swKey(exerciseId, FIELD_USED)) ?: return null
        val start = handle.get<Long>(swKey(exerciseId, FIELD_START)) ?: 0L
        val running = handle.get<Boolean>(swKey(exerciseId, FIELD_RUNNING)) ?: false
        val frozen = handle.get<Int>(swKey(exerciseId, FIELD_FROZEN)) ?: 0
        val elapsed = handle.get<Int>(swKey(exerciseId, FIELD_ELAPSED)) ?: frozen
        if (running && nowElapsedRealtime < start) {
            return SetStopwatchUiState(
                running = false,
                elapsedSeconds = elapsed.coerceAtLeast(frozen),
                used = used,
                startElapsedRealtime = 0L,
                frozenElapsedSeconds = elapsed.coerceAtLeast(frozen),
                exerciseId = exerciseId,
            )
        }
        return SetStopwatchUiState(
            running = running && used,
            elapsedSeconds = elapsed,
            used = used,
            startElapsedRealtime = start,
            frozenElapsedSeconds = frozen,
            exerciseId = exerciseId,
        )
    }

    fun clearHold() {
        listOf(
            KEY_HOLD_EXERCISE,
            KEY_HOLD_RUNNING,
            KEY_HOLD_START,
            KEY_HOLD_DEADLINE,
            KEY_HOLD_TOTAL,
            KEY_HOLD_TARGET,
        ).forEach { handle.remove<Any>(it) }
    }

    fun clearStopwatch(exerciseId: String) {
        listOf(FIELD_RUNNING, FIELD_START, FIELD_FROZEN, FIELD_ELAPSED, FIELD_USED)
            .forEach { handle.remove<Any>(swKey(exerciseId, it)) }
        val ids = ArrayList(stopwatchIds())
        if (ids.remove(exerciseId)) {
            if (ids.isEmpty()) handle.remove<Any>(KEY_SW_IDS) else handle[KEY_SW_IDS] = ids
        }
    }

    fun clearAll() {
        stopwatchIds().forEach { clearStopwatch(it) }
        clearHold()
    }

    private fun stopwatchIds(): List<String> =
        handle.get<ArrayList<String>>(KEY_SW_IDS).orEmpty()

    private companion object {
        const val KEY_HOLD_EXERCISE = "timer.hold.exerciseId"
        const val KEY_HOLD_RUNNING = "timer.hold.running"
        const val KEY_HOLD_START = "timer.hold.startMs"
        const val KEY_HOLD_DEADLINE = "timer.hold.deadlineMs"
        const val KEY_HOLD_TOTAL = "timer.hold.total"
        const val KEY_HOLD_TARGET = "timer.hold.targetReached"
        const val KEY_SW_IDS = "timer.sw.ids"
        const val FIELD_RUNNING = "running"
        const val FIELD_START = "startMs"
        const val FIELD_FROZEN = "frozen"
        const val FIELD_ELAPSED = "elapsed"
        const val FIELD_USED = "used"

        fun swKey(exerciseId: String, field: String): String = "timer.sw.$exerciseId.$field"
    }
}
