package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.DraftStore
import com.sinura.personaltrainer.domain.WorkoutSetSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val extraSetRequested: Boolean = false,
)

/**
 * Process-scoped staged entry. Packet C keeps one draft per lift so
 * switching never resets the other lift's numbers. Survives rotation;
 * process death is [SavedStateWorkoutDraft].
 *
 * Notes belong to the session, and are kept once per session here as [SavedStateWorkoutDraft]
 * keeps them: [sessionNotes]. A lift's [WorkoutDraft.notes] is a copy taken when that lift was
 * last written, so a lift that was not selected while the notes grew keeps older words. A
 * restore reads [sessionNotes], never a lift's copy (N1).
 */
class WorkoutDraftCache {
    private val lock = Any()
    private val sessions = mutableMapOf<String, SessionDrafts>()
    // These locks outlive cleared drafts: an older writer must never acquire a different
    // lock for the same session after Finish removes its cache entry.
    private val notesWriteLocks = mutableMapOf<String, Mutex>()
    private val completedSessions = mutableSetOf<String>()
    private val _finishing = MutableStateFlow<Set<String>>(emptySet())
    val finishing: StateFlow<Set<String>> = _finishing.asStateFlow()

    internal fun beginFinish(sessionId: String): Boolean = synchronized(lock) {
        if (sessionId in _finishing.value) false else {
            _finishing.value += sessionId
            true
        }
    }

    internal fun endFinish(sessionId: String) {
        synchronized(lock) { _finishing.value -= sessionId }
    }

    internal fun finishConfirmed(sessionId: String) {
        synchronized(lock) {
            completedSessions += sessionId
            sessions.remove(sessionId)
        }
    }

    fun liveEntryLocked(sessionId: String): Boolean = synchronized(lock) {
        sessionId in _finishing.value || sessionId in completedSessions
    }

    /** Editing and reserving Finish share one synchronous decision, before any suspension. */
    internal fun editLiveNotes(sessionId: String, edit: () -> Unit) {
        synchronized(lock) {
            if (sessionId !in _finishing.value && sessionId !in completedSessions) edit()
        }
    }

    /** History may edit finished notes, but cannot start a write during Finish's reservation. */
    internal fun editSessionNotes(sessionId: String, edit: () -> Unit) {
        synchronized(lock) { if (sessionId !in _finishing.value) edit() }
    }

    internal suspend fun <T> withNotesWrite(sessionId: String, write: suspend () -> T): T {
        val writeLock = synchronized(lock) { notesWriteLocks.getOrPut(sessionId) { Mutex() } }
        return writeLock.withLock { write() }
    }

    fun editingOriginal(sessionId: String): WorkoutSetSave? = synchronized(lock) {
        sessions[sessionId]?.editingOriginal
    }

    fun putEditingOriginal(sessionId: String, value: WorkoutSetSave?) {
        synchronized(lock) { sessions.getOrPut(sessionId) { SessionDrafts() }.editingOriginal = value }
    }

    fun pendingSave(sessionId: String): WorkoutSetSave? = synchronized(lock) {
        sessions[sessionId]?.pendingSave
    }

    fun putPendingSave(command: WorkoutSetSave) {
        synchronized(lock) {
            sessions.getOrPut(command.sessionId) { SessionDrafts() }.pendingSave = command
        }
    }

    fun clearPendingSave(command: WorkoutSetSave) {
        synchronized(lock) {
            sessions[command.sessionId]?.let { if (it.pendingSave == command) it.pendingSave = null }
        }
    }

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

    /** The session's notes as last staged in this process, or null when none were. */
    fun sessionNotes(sessionId: String): String? = synchronized(lock) {
        sessions[sessionId]?.notes
    }

    /** Stages the session's notes with no lift's entry: a lift still loading, or none selected. */
    fun putSessionNotes(sessionId: String, notes: String) {
        synchronized(lock) {
            if (sessionId !in completedSessions) sessions.getOrPut(sessionId) { SessionDrafts() }.notes = notes
        }
    }

    /** Live notes owners retain their barrier until their write is confirmed or Finish clears it. */
    fun markNotesPending(sessionId: String, owner: Any, pending: Boolean, busy: Boolean, notes: String) {
        synchronized(lock) {
            if (sessionId in completedSessions) return
            if (pending) sessions.getOrPut(sessionId) { SessionDrafts() }.notesOwners[owner] = busy
            else sessions[sessionId]?.let { session ->
                session.notesOwners.remove(owner)
                // A reopened editor can confirm the preserved draft after an older owner
                // was removed. Retire its settled protection, but keep every live writer.
                if (session.notes?.trim() == notes.trim()) {
                    session.notesOwners.entries.removeAll { !it.value }
                }
            }
        }
    }

    fun hasPendingNotes(sessionId: String): Boolean = synchronized(lock) {
        sessions[sessionId]?.notesOwners?.isNotEmpty() == true
    }

    /** Stages a lift's entry, selects it, and takes its notes as the session's. */
    fun put(value: WorkoutDraft) {
        synchronized(lock) {
            if (value.sessionId in completedSessions) return
            val session = sessions.getOrPut(value.sessionId) { SessionDrafts() }
            val key = value.exerciseId.orEmpty()
            session.lifts[key] = value
            session.selectedExerciseId = value.exerciseId
            session.notes = value.notes
        }
    }

    fun replaceAll(
        sessionId: String,
        lifts: Map<String, WorkoutDraft>,
        selectedExerciseId: String?,
    ) {
        synchronized(lock) {
            if (sessionId in completedSessions) return
            val session = SessionDrafts()
            session.pendingSave = sessions[sessionId]?.pendingSave
            session.editingOriginal = sessions[sessionId]?.editingOriginal
            session.notes = sessions[sessionId]?.notes
            session.notesOwners.putAll(sessions[sessionId]?.notesOwners.orEmpty())
            session.selectedExerciseId = selectedExerciseId
            lifts.forEach { (id, draft) ->
                if (id.isNotEmpty()) session.lifts[id] = draft.copy(sessionId = sessionId)
            }
            sessions[sessionId] = session
        }
    }

    fun select(sessionId: String, exerciseId: String?) {
        synchronized(lock) {
            if (sessionId in completedSessions) return
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
            completedSessions.clear()
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
        var pendingSave: WorkoutSetSave? = null
        var editingOriginal: WorkoutSetSave? = null
        var notes: String? = null
        val notesOwners = mutableMapOf<Any, Boolean>()
        var selectedExerciseId: String? = null
        val lifts: MutableMap<String, WorkoutDraft> = mutableMapOf()
    }
}
