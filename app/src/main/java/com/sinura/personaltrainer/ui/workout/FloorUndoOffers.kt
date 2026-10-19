package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.UndoDwell
import com.sinura.personaltrainer.domain.UndoQueue
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.FloorUndo
import com.sinura.personaltrainer.workout.SavedStateFloorUndo
import com.sinura.personaltrainer.workout.UndoEntry
import com.sinura.personaltrainer.workout.toOffer
import com.sinura.personaltrainer.workout.WorkoutUndoCheckpoint
import com.sinura.personaltrainer.workout.WorkoutUndoToken
import com.sinura.personaltrainer.workout.UndoOffer
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The workout floor's undo offers ([ActiveWorkoutViewModel]): the queue, its saved-state mirror
 * and the dwell the banner holds the top offer for.
 *
 * Packet G: cheap destructives form a short LIFO queue, newest last. The floor used to hold one
 * deleted set xor one removed lift, so a second delete silently expired the first offer. Undoing
 * (or timing out) the top token now reveals the next one underneath. One-shot state rather than
 * captured callbacks, so an Activity recreation mid-offer cannot leave an Undo button wired to a
 * dead composition.
 *
 * This class only holds the offers. What a delete, a removal or an undo does is the ViewModel's:
 * its entry lock, its error slot, the rest timer, the edit and the draft. So it takes no
 * container, scope, gate or error slot, and it launches nothing. It reads the unit and a lift's
 * load type when an offer is made, not when it is built, since the ViewModel builds it before
 * the session it reads them from.
 *
 * The queue and its dwell come back from the saved state when it is built.
 */
internal class FloorUndoOffers(
    private val saved: SavedStateFloorUndo,
    private val undoTimeout: UndoTimeoutProvider,
    private val unit: () -> WeightUnit,
    private val loadTypeOf: (String) -> LoadType?,
    private val onCheckpoint: () -> Unit = {},
    /** Oldest offer dropped when the queue is full ([UndoQueue.MAX_DEPTH]). */
    private val onOfferDiscarded: (FloorUndo) -> Unit = {},
) {
    private val _entries = MutableStateFlow<List<UndoEntry>>(emptyList())
    private var sequence = 0L
    private val _dwellMs = MutableStateFlow(
        saved.readDwellMs() ?: UndoDwell.dwellMs(Motion.STATUS_DWELL_MS, null),
    )

    /** Every live undo offer, oldest first. The banner shows the last one. */
    val entries: StateFlow<List<UndoEntry>> = _entries.asStateFlow()

    /** How long the current top offer stays readable; extends under TalkBack. */
    val dwellMs: StateFlow<Long> = _dwellMs.asStateFlow()

    /**
     * The undo lock. Its four holders are the ViewModel's set delete, lift removal and their two
     * undos; each takes it around its repository write and the push or pop that goes with it, so
     * an offer names exactly what was written. The set delete also stops the rest timer inside it
     * when the set it deleted was the latest.
     *
     * It is not what keeps those four apart. Each runs inside the ViewModel's entry lock
     * (`launchEntryMutation`), which refuses a second one until the first has finished, and that
     * is after this lock is let go; so this lock is never waited on. It is kept on purpose:
     * removing it is a decision of its own. Expiry ([pop] from `onUndoOfferExpired`) takes
     * neither lock.
     */
    private val lock = Mutex()

    init {
        // Packet G: the undo queue survives process death; the dwell promised with it does too.
        val restored = saved.read()
        _entries.value = restored
        sequence = restored.size.toLong()
        if (saved.readDwellMs() == null) refreshDwell()
    }

    /** The newest offer's token, or null when nothing is offered. */
    val top: FloorUndo?
        get() = _entries.value.lastOrNull()?.token

    /** Runs [block] under the undo lock. */
    suspend fun <T> serialize(block: suspend () -> T): T = lock.withLock { block() }

    /** Offers [token] on top of the queue, with a fresh dwell, and saves the queue. */
    fun push(token: FloorUndo) {
        val current = _entries.value
        val evicted = if (current.size >= UndoQueue.MAX_DEPTH) current.first().token else null
        val offer = token.toOffer(
            sequence = sequence++,
            unit = unit(),
            loadTypeOf = loadTypeOf,
        )
        _entries.value = UndoQueue.push(current, UndoEntry(token, offer))
        if (evicted != null) onOfferDiscarded(evicted)
        refreshDwell()
        persist()
    }

    /** Takes the newest offer off the queue and saves the rest; null, and nothing saved, when empty. */
    fun pop(): UndoEntry? {
        val current = _entries.value
        if (current.isEmpty()) return null
        val newest = current.last()
        _entries.value = UndoQueue.pop(current)
        persist()
        return newest
    }

    private fun persist() {
        saved.write(_entries.value, _dwellMs.value)
        onCheckpoint()
    }

    fun checkpoint(): List<WorkoutUndoCheckpoint> = _entries.value.map { entry ->
        val token = when (val current = entry.token) {
            is FloorUndo.DeletedSet -> current.deleted.let { row -> WorkoutUndoToken.SetReceipt(
                id = row.setId, exerciseId = row.exerciseId, number = row.setNumber, completedAt = row.completedAt,
                values = WorkoutSetValues(weightKg = row.weightKg, reps = row.reps, rpe = row.rpe, isWarmup = row.isWarmup, durationSeconds = row.durationSeconds),
            ) }
            is FloorUndo.RemovedLift -> current.removed.let { row -> WorkoutUndoToken.LiftReceipt(
                id = row.item.id, exerciseId = row.item.exerciseId, name = row.name, order = row.item.sortOrder,
                sets = row.item.targetSets, reps = row.item.targetReps, weightKg = row.item.targetWeightKg,
                rest = row.item.restSeconds, seconds = row.item.targetSeconds, secondsMax = row.item.targetSecondsMax,
            ) }
        }
        WorkoutUndoCheckpoint(token = token, key = entry.offer.key, message = entry.offer.message)
    }

    fun restoreCheckpoint(sessionId: String, values: List<WorkoutUndoCheckpoint>, dwell: Long) {
        _entries.value = values.map { value ->
            val token = when (val current = value.token) {
                is WorkoutUndoToken.SetReceipt -> FloorUndo.DeletedSet(WorkoutRepository.DeletedSet(
                    setId = current.id, sessionId = sessionId, exerciseId = current.exerciseId, setNumber = current.number,
                    weightKg = current.values.weightKg, reps = current.values.reps, rpe = current.values.rpe,
                    isWarmup = current.values.isWarmup, completedAt = current.completedAt, durationSeconds = current.values.durationSeconds,
                ))
                is WorkoutUndoToken.LiftReceipt -> FloorUndo.RemovedLift(WorkoutRepository.RemovedLift(
                    item = SessionExerciseEntity(id = current.id, sessionId = sessionId, exerciseId = current.exerciseId, sortOrder = current.order,
                        targetSets = current.sets, targetReps = current.reps, targetWeightKg = current.weightKg, restSeconds = current.rest,
                        targetSeconds = current.seconds, targetSecondsMax = current.secondsMax), name = current.name,
                ))
            }
            UndoEntry(token = token, offer = UndoOffer(key = value.key, message = value.message,
                kind = if (token is FloorUndo.DeletedSet) UndoKind.DELETED_SET else UndoKind.REMOVED_LIFT))
        }
        sequence = _entries.value.size.toLong()
        if (dwell > 0) _dwellMs.value = dwell else refreshDwell()
        saved.write(_entries.value, _dwellMs.value)
    }

    private fun refreshDwell() {
        _dwellMs.value = UndoDwell.dwellMs(
            Motion.STATUS_DWELL_MS,
            undoTimeout.recommendedTimeoutMs(Motion.STATUS_DWELL_MS.toInt()),
        )
    }
}
