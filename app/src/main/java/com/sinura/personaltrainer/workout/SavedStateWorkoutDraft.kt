package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.DraftStore

/**
 * Mirrors staged workout entry into [SavedStateHandle], which is written to the saved
 * instance state and therefore survives the process being killed — the case the in-memory
 * [WorkoutDraftCache] cannot cover.
 *
 * Packet C stores a map keyed by lift id. Only unlogged staging lives here. Logged sets
 * are already in Room and remain the source of truth.
 */
class SavedStateWorkoutDraft(private val handle: SavedStateHandle) : DraftStore<WorkoutDraft> {
    override fun read(): WorkoutDraft? {
        val id = handle.get<String>(KEY_SESSION_ID) ?: return null
        return read(sessionId = id)
    }

    fun read(sessionId: String): WorkoutDraft? {
        if (sessionId.isBlank()) return null
        val savedSessionId = handle.get<String>(KEY_SESSION_ID) ?: return null
        if (savedSessionId != sessionId) return null
        val ids = liftIds()
        if (ids.isNotEmpty()) {
            val selected = selectedExerciseId() ?: return null
            return readLiftFields(sessionId, selected)?.copy(notes = sessionNotes())
        }
        return readLegacy(sessionId)
    }

    fun selectedExerciseId(): String? = handle.get<String>(KEY_SELECTED_EXERCISE_ID)
        ?: handle.get<String>(KEY_EXERCISE_ID)

    fun sessionNotes(): String = handle.get<String>(KEY_NOTES).orEmpty()

    fun readAll(sessionId: String): Map<String, WorkoutDraft> {
        if (sessionId.isBlank()) return emptyMap()
        val savedSessionId = handle.get<String>(KEY_SESSION_ID) ?: return emptyMap()
        if (savedSessionId != sessionId) return emptyMap()
        val ids = liftIds()
        if (ids.isNotEmpty()) {
            return ids.mapNotNull { id ->
                readLiftFields(sessionId, id)?.let { id to it }
            }.toMap()
        }
        val legacy = readLegacy(sessionId) ?: return emptyMap()
        val id = legacy.exerciseId ?: return emptyMap()
        return mapOf(id to legacy)
    }

    fun readLift(sessionId: String, exerciseId: String): WorkoutDraft? {
        if (sessionId.isBlank() || exerciseId.isBlank()) return null
        val savedSessionId = handle.get<String>(KEY_SESSION_ID) ?: return null
        if (savedSessionId != sessionId) return null
        readLiftFields(sessionId, exerciseId)?.let { return it }
        val ids = liftIds()
        if (ids.isNotEmpty()) return null
        return readLegacy(sessionId)?.takeIf { it.exerciseId == exerciseId }
    }

    override fun write(value: WorkoutDraft) {
        write(draft = value, editingSetId = editingSetId())
    }

    fun write(draft: WorkoutDraft, editingSetId: String? = null) {
        handle[KEY_SESSION_ID] = draft.sessionId
        handle[KEY_SELECTED_EXERCISE_ID] = draft.exerciseId
        handle[KEY_NOTES] = draft.notes
        if (editingSetId == null) {
            handle.remove<String>(KEY_EDITING_SET_ID)
        } else {
            handle[KEY_EDITING_SET_ID] = editingSetId
        }
        val id = draft.exerciseId
        if (!id.isNullOrBlank()) {
            upsertLift(draft)
        }
        writeLegacy(draft)
    }

    fun writeSelection(
        sessionId: String,
        exerciseId: String?,
        notes: String,
        editingSetId: String? = editingSetId(),
    ) {
        handle[KEY_SESSION_ID] = sessionId
        handle[KEY_SELECTED_EXERCISE_ID] = exerciseId
        handle[KEY_NOTES] = notes
        if (editingSetId == null) {
            handle.remove<String>(KEY_EDITING_SET_ID)
        } else {
            handle[KEY_EDITING_SET_ID] = editingSetId
        }
    }

    fun editingSetId(): String? = handle.get<String>(KEY_EDITING_SET_ID)

    fun removeLift(exerciseId: String) {
        if (exerciseId.isBlank()) return
        val ids = ArrayList(liftIds())
        if (!ids.remove(exerciseId)) return
        if (ids.isEmpty()) {
            handle.remove<Any>(KEY_LIFT_IDS)
        } else {
            handle[KEY_LIFT_IDS] = ids
        }
        removeLiftKeys(exerciseId)
        if (selectedExerciseId() == exerciseId) {
            val next = ids.firstOrNull()
            handle[KEY_SELECTED_EXERCISE_ID] = next
            handle[KEY_EXERCISE_ID] = next
        }
    }

    override fun clear() {
        liftIds().forEach { removeLiftKeys(it) }
        listOf(
            KEY_SESSION_ID,
            KEY_SELECTED_EXERCISE_ID,
            KEY_LIFT_IDS,
            KEY_EXERCISE_ID,
            KEY_WEIGHT,
            KEY_REPS,
            KEY_RPE,
            KEY_WARMUP,
            KEY_NOTES,
            KEY_DURATION,
            KEY_DIRTY,
            KEY_EDITING_SET_ID,
        ).forEach { handle.remove<Any>(it) }
    }

    private fun upsertLift(draft: WorkoutDraft) {
        val id = draft.exerciseId ?: return
        val ids = ArrayList(liftIds())
        if (id !in ids) ids.add(id)
        handle[KEY_LIFT_IDS] = ids
        handle[liftKey(id, FIELD_WEIGHT)] = draft.weightKg
        handle[liftKey(id, FIELD_REPS)] = draft.reps
        handle[liftKey(id, FIELD_RPE)] = draft.rpe
        handle[liftKey(id, FIELD_WARMUP)] = draft.isWarmup
        handle[liftKey(id, FIELD_DURATION)] = draft.durationSeconds
        handle[liftKey(id, FIELD_DIRTY)] = draft.dirty
        handle[liftKey(id, FIELD_EXTRA)] = draft.extraSetRequested
    }

    private fun readLiftFields(sessionId: String, exerciseId: String): WorkoutDraft? {
        val weight = handle.get<Double>(liftKey(exerciseId, FIELD_WEIGHT)) ?: return null
        return WorkoutDraft(
            sessionId = sessionId,
            exerciseId = exerciseId,
            weightKg = weight,
            reps = handle.get<Int>(liftKey(exerciseId, FIELD_REPS)) ?: 1,
            rpe = handle.get<Int>(liftKey(exerciseId, FIELD_RPE)),
            isWarmup = handle.get<Boolean>(liftKey(exerciseId, FIELD_WARMUP)) ?: false,
            notes = sessionNotes(),
            durationSeconds = handle.get<Int>(liftKey(exerciseId, FIELD_DURATION)),
            dirty = handle.get<Boolean>(liftKey(exerciseId, FIELD_DIRTY)) ?: false,
            extraSetRequested = handle.get<Boolean>(liftKey(exerciseId, FIELD_EXTRA)) ?: false,
        )
    }

    private fun readLegacy(sessionId: String): WorkoutDraft? {
        val savedSessionId = handle.get<String>(KEY_SESSION_ID) ?: return null
        if (savedSessionId != sessionId) return null
        if (handle.get<Double>(KEY_WEIGHT) == null && handle.get<Int>(KEY_REPS) == null) {
            return null
        }
        return WorkoutDraft(
            sessionId = savedSessionId,
            exerciseId = handle.get<String>(KEY_EXERCISE_ID),
            weightKg = handle.get<Double>(KEY_WEIGHT) ?: 0.0,
            reps = handle.get<Int>(KEY_REPS) ?: 1,
            rpe = handle.get<Int>(KEY_RPE),
            isWarmup = handle.get<Boolean>(KEY_WARMUP) ?: false,
            notes = handle.get<String>(KEY_NOTES).orEmpty(),
            durationSeconds = handle.get<Int>(KEY_DURATION),
            dirty = handle.get<Boolean>(KEY_DIRTY) ?: false,
        )
    }

    private fun writeLegacy(draft: WorkoutDraft) {
        handle[KEY_EXERCISE_ID] = draft.exerciseId
        handle[KEY_WEIGHT] = draft.weightKg
        handle[KEY_REPS] = draft.reps
        handle[KEY_RPE] = draft.rpe
        handle[KEY_WARMUP] = draft.isWarmup
        handle[KEY_DURATION] = draft.durationSeconds
        handle[KEY_DIRTY] = draft.dirty
    }

    private fun liftIds(): List<String> {
        val stored = handle.get<ArrayList<String>>(KEY_LIFT_IDS)
        if (stored != null) return stored
        val legacy = handle.get<String>(KEY_EXERCISE_ID)
        return if (legacy.isNullOrBlank()) emptyList() else listOf(legacy)
    }

    private fun removeLiftKeys(exerciseId: String) {
        listOf(FIELD_WEIGHT, FIELD_REPS, FIELD_RPE, FIELD_WARMUP, FIELD_DURATION, FIELD_DIRTY, FIELD_EXTRA)
            .forEach { handle.remove<Any>(liftKey(exerciseId, it)) }
    }

    private companion object {
        // Prefixed so they cannot collide with the route's own "sessionId" navigation argument.
        const val KEY_SESSION_ID = "draft.sessionId"
        const val KEY_SELECTED_EXERCISE_ID = "draft.selectedExerciseId"
        const val KEY_LIFT_IDS = "draft.liftIds"
        const val KEY_EXERCISE_ID = "draft.exerciseId"
        const val KEY_WEIGHT = "draft.weightKg"
        const val KEY_REPS = "draft.reps"
        const val KEY_RPE = "draft.rpe"
        const val KEY_WARMUP = "draft.isWarmup"
        const val KEY_NOTES = "draft.notes"
        const val KEY_DURATION = "draft.durationSeconds"
        const val KEY_DIRTY = "draft.dirty"
        const val KEY_EDITING_SET_ID = "draft.editingSetId"
        const val FIELD_WEIGHT = "weightKg"
        const val FIELD_REPS = "reps"
        const val FIELD_RPE = "rpe"
        const val FIELD_WARMUP = "isWarmup"
        const val FIELD_DURATION = "durationSeconds"
        const val FIELD_DIRTY = "dirty"
        const val FIELD_EXTRA = "extraSetRequested"

        fun liftKey(exerciseId: String, field: String): String = "draft.lift.$exerciseId.$field"
    }
}
