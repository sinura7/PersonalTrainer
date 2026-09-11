package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.DraftStore

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

    /** One session's slice of this cache, so finish can [DraftStore.clear] on an accepted save. */
    fun storeFor(sessionId: String): DraftStore<WorkoutDraft> =
        object : DraftStore<WorkoutDraft> {
            override fun read(): WorkoutDraft? = get(sessionId)
            override fun write(value: WorkoutDraft) = put(value)
            override fun clear() = this@WorkoutDraftCache.clear(sessionId)
        }
}
