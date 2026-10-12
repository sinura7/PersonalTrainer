package com.sinura.personaltrainer.ui.library

import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.CustomLiftDraft
import com.sinura.personaltrainer.data.draft.CustomLiftHandle
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class LiftEditorPhase { CLOSED, OPENING, EDITING, CHECKPOINT_FAILED, SAVING, CHECKING, UNCERTAIN, DISCARD_UNCERTAIN, RETRYABLE, CONFLICT, READ_FAILED }
internal data class LiftEditorStatus(val phase: LiftEditorPhase = LiftEditorPhase.CLOSED, val message: String? = null) {
    val busy: Boolean get() = phase in setOf(LiftEditorPhase.OPENING, LiftEditorPhase.SAVING, LiftEditorPhase.CHECKING)
    val editable: Boolean get() = phase == LiftEditorPhase.EDITING || phase == LiftEditorPhase.CHECKPOINT_FAILED
}

/** Main-owned admission is synchronous; serial jobs flush the latest fields before orderly close. */
internal class LiftEditorRecovery(
    private val scope: CoroutineScope,
    private val drafts: CustomExerciseDrafts,
    private val onFields: (ExerciseEditorDraft?) -> Unit,
    private val onSaved: (String) -> Unit,
    private val currentWriteGeneration: () -> Long = { 0L },
    private val canWrite: (Long) -> Boolean = { true },
) {
    private val mutableStatus = MutableStateFlow(LiftEditorStatus())
    val status: StateFlow<LiftEditorStatus> = mutableStatus
    private val lock = Mutex()
    private var handle: CustomLiftHandle? = null
    private var fields: ExerciseEditorDraft? = null
    private var openIdentity: String? = null
    private var openMuscle: String = ""
    private var ownerToken = 0L

    fun open(initial: ExerciseEditorDraft) {
        if (status.value.busy) return
        val token = ++ownerToken
        openIdentity = initial.id
        openMuscle = initial.muscleGroup
        fields = initial
        onFields(initial)
        handle = null
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.OPENING, "Opening draft…")
        scope.launch { lock.withLock {
            if (token != ownerToken) return@withLock
            try {
                val opened = drafts.open(openIdentity, openMuscle)
                handle = opened
                fields = opened.authored.toEditor()
                onFields(fields)
                when {
                    opened.ownerConflict -> conflict()
                    opened.checkpoint.pending != null -> recover(opened)
                    else -> mutableStatus.value = LiftEditorStatus(LiftEditorPhase.EDITING, if (opened.resumed) "Draft resumed" else null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableStatus.value = LiftEditorStatus(LiftEditorPhase.READ_FAILED, "Draft could not be read. Check again; nothing was discarded.") }
        } }
    }

    fun update(next: ExerciseEditorDraft) {
        if (!status.value.editable || next.id != fields?.id) return
        fields = next
        onFields(next)
        val token = ownerToken
        scope.launch { lock.withLock {
            if (token != ownerToken || !status.value.editable || status.value.phase == LiftEditorPhase.CHECKPOINT_FAILED) return@withLock
            val current = handle ?: return@withLock
            try { drafts.checkpoint(current, next.toStored(current)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == ownerToken) checkpointFailed() }
        } }
    }

    fun close() {
        if (status.value.busy) return
        if (status.value.phase == LiftEditorPhase.DISCARD_UNCERTAIN) { discard(); return }
        val current = handle
        if (current == null) { ownerToken++; onFields(null); mutableStatus.value = LiftEditorStatus(); return }
        val latest = fields ?: return
        val editable = status.value.editable
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CHECKING, "Keeping draft…")
        scope.launch { lock.withLock {
            try {
                if (editable) {
                    drafts.refreshCheckpoint(current)
                    drafts.checkpoint(current, latest.toStored(current))
                } else drafts.keepBeforeClose(current, latest.toStored(current))
                ownerToken++
                onFields(null)
                mutableStatus.value = LiftEditorStatus()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (editable) checkpointFailed() else uncertain() }
        } }
    }

    fun save() {
        if (status.value.phase != LiftEditorPhase.EDITING && status.value.phase != LiftEditorPhase.RETRYABLE) return
        val current = handle ?: return
        val latest = fields ?: return
        val retry = status.value.phase == LiftEditorPhase.RETRYABLE
        val generation = currentWriteGeneration()
        val permit: () -> Boolean = { canWrite(generation) }
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.SAVING, "Saving lift…")
        scope.launch { lock.withLock {
            try {
                if (retry) drafts.retry(current) else drafts.admit(current, latest.toStored(current))
                when (drafts.execute(current, permit)) {
                    is SaveExerciseResult.Saved -> recover(current)
                    is SaveExerciseResult.DuplicateName -> {
                        if (drafts.inspect(current) == DraftRecoveryOutcome.UNCOMMITTED) {
                            drafts.editInstead(current)
                            mutableStatus.value = LiftEditorStatus(LiftEditorPhase.EDITING, DUPLICATE_NAME_MESSAGE)
                        } else conflict()
                    }
                    is SaveExerciseResult.MissingMuscle, null -> conflict()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { uncertain() }
        } }
    }

    fun check() {
        if (status.value.busy) return
        if (status.value.phase == LiftEditorPhase.DISCARD_UNCERTAIN) { discard(); return }
        if (status.value.phase == LiftEditorPhase.READ_FAILED) { fields?.let(::open); return }
        val current = handle ?: return
        val checkpointOnly = status.value.phase == LiftEditorPhase.CHECKPOINT_FAILED
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CHECKING, "Checking…")
        scope.launch { lock.withLock {
            try {
                if (checkpointOnly) {
                    drafts.refreshCheckpoint(current)
                    drafts.checkpoint(current, checkNotNull(fields).toStored(current))
                    mutableStatus.value = LiftEditorStatus(LiftEditorPhase.EDITING, "Draft kept")
                } else recover(current)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (checkpointOnly) checkpointFailed() else uncertain() }
        } }
    }

    fun editInstead() {
        if (status.value.phase != LiftEditorPhase.RETRYABLE) return
        val current = handle ?: return
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CHECKING, "Opening fields…")
        scope.launch { lock.withLock {
            try { drafts.editInstead(current); mutableStatus.value = LiftEditorStatus(LiftEditorPhase.EDITING, "Not saved; edit your draft") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { uncertain() }
        } }
    }

    fun discard() {
        if (status.value.busy) return
        val current = handle ?: return
        mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CHECKING, "Discarding draft…")
        scope.launch { lock.withLock {
            try {
                drafts.discard(current)
                ownerToken++
                fields = null
                handle = null
                onFields(null)
                mutableStatus.value = LiftEditorStatus()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableStatus.value = LiftEditorStatus(LiftEditorPhase.DISCARD_UNCERTAIN, "Discard not confirmed. Check before leaving.") }
        } }
    }

    private suspend fun recover(current: CustomLiftHandle) {
        when (drafts.inspect(current)) {
            DraftRecoveryOutcome.EXACT_SAVED -> {
                val name = current.authored.name.trim()
                drafts.finish(current)
                fields = null
                handle = null
                onFields(null)
                mutableStatus.value = LiftEditorStatus()
                onSaved(name)
            }
            DraftRecoveryOutcome.UNCOMMITTED -> mutableStatus.value = if (current.checkpoint.pending == null) {
                LiftEditorStatus(LiftEditorPhase.EDITING, "Not saved. Your draft is kept.")
            } else LiftEditorStatus(LiftEditorPhase.RETRYABLE, "Not saved yet. Retry keeps these exact values.")
            DraftRecoveryOutcome.CONFLICT -> conflict()
            DraftRecoveryOutcome.UNAVAILABLE -> uncertain()
        }
    }

    private fun uncertain() { mutableStatus.value = LiftEditorStatus(LiftEditorPhase.UNCERTAIN, "Save not confirmed. Check what saved before trying again.") }
    private fun conflict() { mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CONFLICT, "This lift or draft changed. Your fields are retained; review the current lift before saving.") }
    private fun checkpointFailed() { mutableStatus.value = LiftEditorStatus(LiftEditorPhase.CHECKPOINT_FAILED, "Draft not confirmed on this device. Check again before closing.") }
    private fun CustomLiftDraft.toEditor(): ExerciseEditorDraft = ExerciseEditorDraft(id = editId, name = name, muscleGroup = muscle, notes = notes, loadType = loadType)
    private fun ExerciseEditorDraft.toStored(current: CustomLiftHandle): CustomLiftDraft = current.authored.copy(name = name, muscle = muscleGroup, notes = notes, loadType = loadType)
}
