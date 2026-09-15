package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.UndoHostCopy
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit

/**
 * Packet G: one token per cheap destructive, newest last.
 *
 * The floor used to hold a single deleted set xor a single removed lift — a second delete
 * silently expired the first offer. Tokens now form a short LIFO queue
 * ([com.sinura.personaltrainer.domain.UndoQueue]); the banner shows the top offer and
 * undoing (or timing out) reveals the next one underneath.
 */
sealed interface FloorUndo {
    data class DeletedSet(val deleted: WorkoutRepository.DeletedSet) : FloorUndo

    data class RemovedLift(val removed: WorkoutRepository.RemovedLift) : FloorUndo

    val kind: UndoKind
        get() = when (this) {
            is DeletedSet -> UndoKind.DELETED_SET
            is RemovedLift -> UndoKind.REMOVED_LIFT
        }
}

/** What the undo banner shows for the top token. The key restarts the dwell per offer. */
data class UndoOffer(
    val key: String,
    val message: String,
    val kind: UndoKind,
)

/** One queued token plus the receipt line captured when it landed. */
data class UndoEntry(
    val token: FloorUndo,
    val offer: UndoOffer,
)

fun FloorUndo.toOffer(
    sequence: Long,
    unit: WeightUnit,
    loadTypeOf: (String) -> LoadType?,
): UndoOffer =
    when (this) {
        is FloorUndo.DeletedSet -> {
            val line = SetCopy.setLine(
                deleted.weightKg,
                deleted.reps,
                LoadClass.of(loadTypeOf(deleted.exerciseId)),
                unit,
                durationSeconds = deleted.durationSeconds,
            )
            UndoOffer(
                key = "undo-$sequence-set-${deleted.setId}",
                message = UndoHostCopy.setDeleted(line),
                kind = UndoKind.DELETED_SET,
            )
        }
        is FloorUndo.RemovedLift -> UndoOffer(
            key = "undo-$sequence-lift-${removed.item.id}",
            message = UndoHostCopy.liftRemoved(removed.name),
            kind = UndoKind.REMOVED_LIFT,
        )
    }
