package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.UndoKind

/**
 * Packet G: the undo queue survives process death.
 *
 * Mirrors the [UndoEntry] stack into [SavedStateHandle] as primitives — operation type, ids
 * and columns, plus the captured receipt line — so a force-stop during the undo window still
 * offers the reversal on return. No database schema: logged sets are already in Room and
 * remain the source of truth; this only re-holds the tokens that name them.
 *
 * The dwell in force when the process died is restored alongside, so the offer does not come
 * back shorter than it was promised.
 */
class SavedStateFloorUndo(private val handle: SavedStateHandle) {
    fun read(): List<UndoEntry> {
        val count = handle.get<Int>(KEY_COUNT) ?: return emptyList()
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { readAt(it) }
    }

    fun readDwellMs(): Long? = handle.get<Long>(KEY_DWELL_MS)

    fun write(entries: List<UndoEntry>, dwellMs: Long) {
        val previous = handle.get<Int>(KEY_COUNT) ?: 0
        handle[KEY_COUNT] = entries.size
        handle[KEY_DWELL_MS] = dwellMs
        entries.forEachIndexed { index, entry -> writeAt(index, entry) }
        // Shrinking the queue must not leave a stale token readable past the new end.
        for (index in entries.size until previous) clearAt(index)
    }

    fun clear() {
        val count = handle.get<Int>(KEY_COUNT) ?: 0
        for (index in 0 until count) clearAt(index)
        handle.remove<Int>(KEY_COUNT)
        handle.remove<Long>(KEY_DWELL_MS)
    }

    private fun readAt(index: Int): UndoEntry? {
        val prefix = "$KEY_ITEM.$index"
        val type = handle.get<String>("$prefix.$FIELD_TYPE") ?: return null
        val message = handle.get<String>("$prefix.$FIELD_MESSAGE") ?: return null
        val token: FloorUndo = when (type) {
            TYPE_SET -> FloorUndo.DeletedSet(
                WorkoutRepository.DeletedSet(
                    setId = handle.get<String>("$prefix.$FIELD_SET_ID").orEmpty(),
                    sessionId = handle.get<String>("$prefix.$FIELD_SESSION_ID").orEmpty(),
                    exerciseId = handle.get<String>("$prefix.$FIELD_EXERCISE_ID").orEmpty(),
                    setNumber = handle.get<Int>("$prefix.$FIELD_SET_NUMBER") ?: 0,
                    weightKg = handle.get<Double>("$prefix.$FIELD_WEIGHT") ?: 0.0,
                    reps = handle.get<Int>("$prefix.$FIELD_REPS") ?: 0,
                    rpe = handle.get<Int>("$prefix.$FIELD_RPE"),
                    isWarmup = handle.get<Boolean>("$prefix.$FIELD_WARMUP") ?: false,
                    completedAt = handle.get<Long>("$prefix.$FIELD_COMPLETED_AT") ?: 0L,
                    durationSeconds = handle.get<Int>("$prefix.$FIELD_DURATION"),
                ),
            )

            TYPE_LIFT -> FloorUndo.RemovedLift(
                WorkoutRepository.RemovedLift(
                    item = SessionExerciseEntity(
                        id = handle.get<String>("$prefix.$FIELD_ITEM_ID").orEmpty(),
                        sessionId = handle.get<String>("$prefix.$FIELD_SESSION_ID").orEmpty(),
                        exerciseId = handle.get<String>("$prefix.$FIELD_EXERCISE_ID").orEmpty(),
                        sortOrder = handle.get<Int>("$prefix.$FIELD_SORT_ORDER") ?: 0,
                        targetSets = handle.get<Int>("$prefix.$FIELD_TARGET_SETS") ?: 0,
                        targetReps = handle.get<Int>("$prefix.$FIELD_TARGET_REPS") ?: 0,
                        targetWeightKg = handle.get<Double>("$prefix.$FIELD_TARGET_WEIGHT"),
                        restSeconds = handle.get<Int>("$prefix.$FIELD_REST_SECONDS") ?: 0,
                        targetSeconds = handle.get<Int>("$prefix.$FIELD_TARGET_SECONDS"),
                        targetSecondsMax = handle.get<Int>("$prefix.$FIELD_TARGET_SECONDS_MAX"),
                    ),
                    name = handle.get<String>("$prefix.$FIELD_NAME").orEmpty(),
                ),
            )

            else -> return null
        }
        val kind = when (token) {
            is FloorUndo.DeletedSet -> UndoKind.DELETED_SET
            is FloorUndo.RemovedLift -> UndoKind.REMOVED_LIFT
        }
        return UndoEntry(
            token = token,
            offer = UndoOffer(
                key = "undo-restored-$index-$type",
                message = message,
                kind = kind,
            ),
        )
    }

    private fun writeAt(index: Int, entry: UndoEntry) {
        val prefix = "$KEY_ITEM.$index"
        val token = entry.token
        handle["$prefix.$FIELD_MESSAGE"] = entry.offer.message
        when (token) {
            is FloorUndo.DeletedSet -> {
                handle["$prefix.$FIELD_TYPE"] = TYPE_SET
                val deleted = token.deleted
                handle["$prefix.$FIELD_SET_ID"] = deleted.setId
                handle["$prefix.$FIELD_SESSION_ID"] = deleted.sessionId
                handle["$prefix.$FIELD_EXERCISE_ID"] = deleted.exerciseId
                handle["$prefix.$FIELD_SET_NUMBER"] = deleted.setNumber
                handle["$prefix.$FIELD_WEIGHT"] = deleted.weightKg
                handle["$prefix.$FIELD_REPS"] = deleted.reps
                putOrRemove(prefix, FIELD_RPE, deleted.rpe)
                handle["$prefix.$FIELD_WARMUP"] = deleted.isWarmup
                handle["$prefix.$FIELD_COMPLETED_AT"] = deleted.completedAt
                putOrRemove(prefix, FIELD_DURATION, deleted.durationSeconds)
            }

            is FloorUndo.RemovedLift -> {
                handle["$prefix.$FIELD_TYPE"] = TYPE_LIFT
                val item = token.removed.item
                handle["$prefix.$FIELD_ITEM_ID"] = item.id
                handle["$prefix.$FIELD_SESSION_ID"] = item.sessionId
                handle["$prefix.$FIELD_EXERCISE_ID"] = item.exerciseId
                handle["$prefix.$FIELD_SORT_ORDER"] = item.sortOrder
                handle["$prefix.$FIELD_TARGET_SETS"] = item.targetSets
                handle["$prefix.$FIELD_TARGET_REPS"] = item.targetReps
                putOrRemove(prefix, FIELD_TARGET_WEIGHT, item.targetWeightKg)
                handle["$prefix.$FIELD_REST_SECONDS"] = item.restSeconds
                putOrRemove(prefix, FIELD_TARGET_SECONDS, item.targetSeconds)
                putOrRemove(prefix, FIELD_TARGET_SECONDS_MAX, item.targetSecondsMax)
                handle["$prefix.$FIELD_NAME"] = token.removed.name
            }
        }
    }

    private fun <T> putOrRemove(prefix: String, field: String, value: T?) {
        val key = "$prefix.$field"
        if (value == null) {
            handle.remove<Any>(key)
        } else {
            handle[key] = value
        }
    }

    private fun clearAt(index: Int) {
        val prefix = "$KEY_ITEM.$index"
        listOf(
            FIELD_TYPE,
            FIELD_MESSAGE,
            FIELD_SET_ID,
            FIELD_SESSION_ID,
            FIELD_EXERCISE_ID,
            FIELD_SET_NUMBER,
            FIELD_WEIGHT,
            FIELD_REPS,
            FIELD_RPE,
            FIELD_WARMUP,
            FIELD_COMPLETED_AT,
            FIELD_DURATION,
            FIELD_ITEM_ID,
            FIELD_SORT_ORDER,
            FIELD_TARGET_SETS,
            FIELD_TARGET_REPS,
            FIELD_TARGET_WEIGHT,
            FIELD_REST_SECONDS,
            FIELD_TARGET_SECONDS,
            FIELD_TARGET_SECONDS_MAX,
            FIELD_NAME,
        ).forEach { handle.remove<Any>("$prefix.$it") }
    }

    private companion object {
        const val KEY_COUNT = "floorUndo.count"
        const val KEY_DWELL_MS = "floorUndo.dwellMs"
        const val KEY_ITEM = "floorUndo.item"
        const val TYPE_SET = "set"
        const val TYPE_LIFT = "lift"
        const val FIELD_TYPE = "type"
        const val FIELD_MESSAGE = "message"
        const val FIELD_SET_ID = "setId"
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_EXERCISE_ID = "exerciseId"
        const val FIELD_SET_NUMBER = "setNumber"
        const val FIELD_WEIGHT = "weightKg"
        const val FIELD_REPS = "reps"
        const val FIELD_RPE = "rpe"
        const val FIELD_WARMUP = "isWarmup"
        const val FIELD_COMPLETED_AT = "completedAt"
        const val FIELD_DURATION = "durationSeconds"
        const val FIELD_ITEM_ID = "itemId"
        const val FIELD_SORT_ORDER = "sortOrder"
        const val FIELD_TARGET_SETS = "targetSets"
        const val FIELD_TARGET_REPS = "targetReps"
        const val FIELD_TARGET_WEIGHT = "targetWeightKg"
        const val FIELD_REST_SECONDS = "restSeconds"
        const val FIELD_TARGET_SECONDS = "targetSeconds"
        const val FIELD_TARGET_SECONDS_MAX = "targetSecondsMax"
        const val FIELD_NAME = "name"
    }
}
