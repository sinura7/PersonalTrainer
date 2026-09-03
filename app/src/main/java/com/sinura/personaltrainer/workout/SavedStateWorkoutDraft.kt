package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle

/**
 * Mirrors the staged workout entry into [SavedStateHandle], which is written to the saved
 * instance state and therefore survives the process being killed — the case the in-memory
 * [WorkoutDraftCache] cannot cover.
 *
 * Only unlogged staging lives here. Logged sets are already in Room and remain the source of
 * truth; nothing in this class can change what was actually logged.
 */
class SavedStateWorkoutDraft(private val handle: SavedStateHandle) {
    fun read(sessionId: String): WorkoutDraft? {
        if (sessionId.isBlank()) return null
        val savedSessionId = handle.get<String>(KEY_SESSION_ID) ?: return null
        if (savedSessionId != sessionId) return null
        return WorkoutDraft(
            sessionId = savedSessionId,
            exerciseId = handle.get<String>(KEY_EXERCISE_ID),
            weightKg = handle.get<Double>(KEY_WEIGHT) ?: 0.0,
            reps = handle.get<Int>(KEY_REPS) ?: 1,
            rpe = handle.get<Int>(KEY_RPE),
            isWarmup = handle.get<Boolean>(KEY_WARMUP) ?: false,
            notes = handle.get<String>(KEY_NOTES).orEmpty(),
        )
    }

    fun write(draft: WorkoutDraft, editingSetId: String? = null) {
        handle[KEY_SESSION_ID] = draft.sessionId
        handle[KEY_EXERCISE_ID] = draft.exerciseId
        handle[KEY_WEIGHT] = draft.weightKg
        handle[KEY_REPS] = draft.reps
        handle[KEY_RPE] = draft.rpe
        handle[KEY_WARMUP] = draft.isWarmup
        handle[KEY_NOTES] = draft.notes
        if (editingSetId == null) {
            handle.remove<String>(KEY_EDITING_SET_ID)
        } else {
            handle[KEY_EDITING_SET_ID] = editingSetId
        }
    }

    fun editingSetId(): String? = handle.get<String>(KEY_EDITING_SET_ID)

    fun clear() {
        listOf(
            KEY_SESSION_ID,
            KEY_EXERCISE_ID,
            KEY_WEIGHT,
            KEY_REPS,
            KEY_RPE,
            KEY_WARMUP,
            KEY_NOTES,
            KEY_EDITING_SET_ID,
        )
            .forEach { handle.remove<Any>(it) }
    }

    private companion object {
        // Prefixed so they cannot collide with the route's own "sessionId" navigation argument.
        const val KEY_SESSION_ID = "draft.sessionId"
        const val KEY_EXERCISE_ID = "draft.exerciseId"
        const val KEY_WEIGHT = "draft.weightKg"
        const val KEY_REPS = "draft.reps"
        const val KEY_RPE = "draft.rpe"
        const val KEY_WARMUP = "draft.isWarmup"
        const val KEY_NOTES = "draft.notes"
        const val KEY_EDITING_SET_ID = "draft.editingSetId"
    }
}
