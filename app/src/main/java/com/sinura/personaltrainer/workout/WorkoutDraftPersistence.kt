package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Internal bridge keeps the memory/SavedState lane independent of files and Room. */
internal interface WorkoutDraftPersistence {
    /** One owning editor; a newly opened editor retires older callbacks for that session. */
    fun forEntry(): WorkoutDraftPersistence = this
    fun ownsEntry(sessionId: String): Boolean = true
    fun mutationPermit(sessionId: String): WorkoutMutationPermit? = null
    suspend fun open(sessionId: String): WorkoutDraftRestore
    suspend fun checkpoint(entry: WorkoutEntryCheckpoint)
    fun commandIdentity(command: WorkoutSetSave): String
    suspend fun <T> writeSet(command: WorkoutSetSave, write: suspend () -> T, acknowledge: suspend () -> T): T
    suspend fun inspectSet(command: WorkoutSetSave, inspect: suspend () -> WorkoutSetSaveResolution): WorkoutSetSaveResolution
    suspend fun settleSet(command: WorkoutSetSave, entry: WorkoutEntryCheckpoint)
    suspend fun releaseSet(command: WorkoutSetSave)
    suspend fun writeNotes(sessionId: String, notes: String, entry: WorkoutEntryCheckpoint, write: suspend (String, String) -> Unit)
    suspend fun inspectFinish(sessionId: String, notes: String?): String?
    suspend fun <T> finishAtomically(sessionId: String, notes: String?, write: suspend (String) -> T): T
    suspend fun retire(sessionId: String)
    suspend fun discardEntry(sessionId: String)
}

/** Notes null means unhydrated, while an empty string is an intentional clear. */
internal data class WorkoutEntryCheckpoint(
    val sessionId: String,
    val startedAt: Long,
    val authoredRevision: Long,
    val lifts: Map<String, WorkoutDraft>,
    val selectedExerciseId: String?,
    val notes: String?,
    val notesBase: String?,
    val editingOriginal: WorkoutSetSave?,
    val acknowledgedCommand: String? = null,
    val numbers: List<WorkoutNumberDraft> = emptyList(),
    val clocks: WorkoutClockCheckpoint? = null,
    val undo: List<WorkoutUndoCheckpoint> = emptyList(),
    val undoDwellMs: Long = 0,
) {
    override fun toString(): String = "WorkoutEntryCheckpoint(redacted)"
}

internal enum class WorkoutNumberField { WEIGHT, REPS, HOLD_SECONDS, REST_SECONDS }
internal data class WorkoutNumberDraft(
    val exerciseId: String?, val field: WorkoutNumberField, val text: String,
    val weightUnit: WeightUnit?, val selectionStart: Int, val selectionEnd: Int,
    val open: Boolean,
) {
    override fun toString(): String = "WorkoutNumberDraft(redacted)"
}

internal data class WorkoutClockCheckpoint(
    val boot: Long?, val holdExerciseId: String?, val hold: HoldTimerUiState?,
    val stopwatches: Map<String, SetStopwatchUiState>,
) {
    override fun toString(): String = "WorkoutClockCheckpoint(redacted)"
}

internal data class WorkoutUndoCheckpoint(val token: WorkoutUndoToken, val key: String, val message: String) {
    override fun toString(): String = "WorkoutUndoCheckpoint(redacted)"
}
internal sealed interface WorkoutUndoToken {
    data class SetReceipt(val id: String, val exerciseId: String, val number: Int, val completedAt: Long, val values: WorkoutSetValues) : WorkoutUndoToken {
        override fun toString(): String = "DeletedSet(redacted)"
    }
    data class LiftReceipt(
        val id: String, val exerciseId: String, val name: String, val order: Int,
        val sets: Int, val reps: Int, val weightKg: Double?, val rest: Int, val seconds: Int?, val secondsMax: Int?,
    ) : WorkoutUndoToken { override fun toString(): String = "RemovedLift(redacted)" }
}

internal sealed interface WorkoutDraftRestore {
    data class ReadyEntry(
        val entry: WorkoutEntryCheckpoint?, val pending: WorkoutSetSave?,
        val resumed: Boolean, val notesNeedReview: Boolean = false,
        val replaceLocal: Boolean = false,
    ) : WorkoutDraftRestore
    data class OwnerConflict(val message: String) : WorkoutDraftRestore
    data object ReadUnavailable : WorkoutDraftRestore
}

internal class WorkoutCheckpointUnavailable : IllegalStateException("Could not preserve the workout entry. Keep editing or try again.")
internal class WorkoutCheckpointConflict : IllegalStateException("This workout changed. Review saved work before continuing.")
internal class WorkoutFinishRefused(val reason: String) : IllegalStateException(reason)

/** A suspended editor operation keeps the owner that admitted it, across Room dispatch. */
internal class WorkoutMutationPermit(private val allowed: () -> Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<WorkoutMutationPermit>
    fun requireAllowed() { if (!allowed()) throw WorkoutCheckpointConflict() }
}
