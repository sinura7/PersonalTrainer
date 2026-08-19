package com.sinura.personaltrainer.workout

data class WorkoutDraft(
    val sessionId: String,
    val exerciseId: String?,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val notes: String,
)

class WorkoutDraftCache {
    @Volatile
    private var draft: WorkoutDraft? = null

    fun get(sessionId: String): WorkoutDraft? =
        draft?.takeIf { it.sessionId == sessionId }

    fun put(value: WorkoutDraft) {
        draft = value
    }

    fun clear(sessionId: String) {
        if (draft?.sessionId == sessionId) {
            draft = null
        }
    }

    /**
     * Drops the draft whatever session it belongs to. Used by restore, which deletes every
     * session row — a draft left behind would point at a workout that no longer exists.
     */
    fun clearAll() {
        draft = null
    }
}
