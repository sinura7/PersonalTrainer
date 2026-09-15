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
    val durationSeconds: Int? = null,
    val dirty: Boolean = false,
)

/**
 * Process-scoped staged entry. Packet C keeps one draft per lift so
 * switching never resets the other lift's numbers. Survives rotation;
 * process death is [SavedStateWorkoutDraft].
 */
class WorkoutDraftCache {
    private val lock = Any()
    private val sessions = mutableMapOf<String, SessionDrafts>()

    fun get(sessionId: String): WorkoutDraft? = synchronized(lock) {
        val session = sessions[sessionId] ?: return null
        val key = session.selectedExerciseId.orEmpty()
        return session.lifts[key] ?: session.lifts.values.firstOrNull()
    }

    fun getLift(sessionId: String, exerciseId: String): WorkoutDraft? = synchronized(lock) {
        sessions[sessionId]?.lifts?.get(exerciseId)
    }

    fun all(sessionId: String): Map<String, WorkoutDraft> = synchronized(lock) {
        val session = sessions[sessionId] ?: return emptyMap()
        session.lifts.filterKeys { it.isNotEmpty() }
    }

    fun selectedExerciseId(sessionId: String): String? = synchronized(lock) {
        sessions[sessionId]?.selectedExerciseId
    }

    fun put(value: WorkoutDraft) {
        synchronized(lock) {
            val session = sessions.getOrPut(value.sessionId) { SessionDrafts() }
            val key = value.exerciseId.orEmpty()
            session.lifts[key] = value
            session.selectedExerciseId = value.exerciseId
        }
    }

    fun replaceAll(
        sessionId: String,
        lifts: Map<String, WorkoutDraft>,
        selectedExerciseId: String?,
    ) {
        synchronized(lock) {
            val session = SessionDrafts()
            session.selectedExerciseId = selectedExerciseId
            lifts.forEach { (id, draft) ->
                if (id.isNotEmpty()) session.lifts[id] = draft.copy(sessionId = sessionId)
            }
            sessions[sessionId] = session
        }
    }

    fun select(sessionId: String, exerciseId: String?) {
        synchronized(lock) {
            val session = sessions.getOrPut(sessionId) { SessionDrafts() }
            session.selectedExerciseId = exerciseId
        }
    }

    fun removeLift(sessionId: String, exerciseId: String) {
        synchronized(lock) {
            val session = sessions[sessionId] ?: return
            session.lifts.remove(exerciseId)
            if (session.selectedExerciseId == exerciseId) {
                session.selectedExerciseId = session.lifts.keys.firstOrNull { it.isNotEmpty() }
            }
        }
    }

    fun clear(sessionId: String) {
        synchronized(lock) {
            sessions.remove(sessionId)
        }
    }

    /**
     * Drops every session's drafts. Used by restore, which deletes every
     * session row — a draft left behind would point at a workout that no longer exists.
     */
    fun clearAll() {
        synchronized(lock) {
            sessions.clear()
        }
    }

    /** One session's slice of this cache, so finish can [DraftStore.clear] on an accepted save. */
    fun storeFor(sessionId: String): DraftStore<WorkoutDraft> =
        object : DraftStore<WorkoutDraft> {
            override fun read(): WorkoutDraft? = get(sessionId)
            override fun write(value: WorkoutDraft) = put(value)
            override fun clear() = this@WorkoutDraftCache.clear(sessionId)
        }

    private class SessionDrafts {
        var selectedExerciseId: String? = null
        val lifts: MutableMap<String, WorkoutDraft> = mutableMapOf()
    }
}
