package com.sinura.personaltrainer.workout

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues

/** Task-restored recovery, separate from the editable draft. This is not a crash journal. */
class SavedStateWorkoutSave(
    private val handle: SavedStateHandle,
    private val storageKey: String = "workout.pendingSave.v1",
) {
    fun write(command: WorkoutSetSave) {
        handle[storageKey] = Bundle().apply {
            putString("session", command.sessionId)
            putString("exercise", command.exerciseId)
            putString("set", command.setId)
            putLong("completed", command.completedAt)
            putBundle("values", encode(command.values))
            command.original?.let { putBundle("original", encode(it)) }
        }
    }

    fun read(sessionId: String): WorkoutSetSave? {
        val bundle = handle.get<Bundle>(storageKey) ?: return null
        if (bundle.getString("session") != sessionId) return null
        return WorkoutSetSave(
            sessionId = sessionId,
            exerciseId = bundle.getString("exercise") ?: return null,
            setId = bundle.getString("set") ?: return null,
            completedAt = bundle.getLong("completed"),
            values = bundle.getBundle("values")?.let(::decode) ?: return null,
            original = bundle.getBundle("original")?.let(::decode),
        )
    }

    fun clear() { handle.remove<Bundle>(storageKey) }

    private fun encode(values: WorkoutSetValues) = Bundle().apply {
        putDouble("weight", values.weightKg)
        putInt("reps", values.reps)
        putBoolean("warmup", values.isWarmup)
        values.rpe?.let { putInt("rpe", it) }
        values.durationSeconds?.let { putInt("duration", it) }
    }

    private fun decode(bundle: Bundle) = WorkoutSetValues(
        weightKg = bundle.getDouble("weight"),
        reps = bundle.getInt("reps"),
        rpe = if (bundle.containsKey("rpe")) bundle.getInt("rpe") else null,
        isWarmup = bundle.getBoolean("warmup"),
        durationSeconds = if (bundle.containsKey("duration")) bundle.getInt("duration") else null,
    )

}
