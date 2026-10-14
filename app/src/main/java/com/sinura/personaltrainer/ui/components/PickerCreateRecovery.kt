package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.CustomLiftDraft
import com.sinura.personaltrainer.data.draft.CustomLiftHandle
import com.sinura.personaltrainer.data.draft.CustomLiftResume
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class PickerCreatePhase {
    CLOSED, OPENING, EDITING, KEEP_FAILED, SAVING, CHECKING, UNCERTAIN, RETRYABLE, SAVED, SELECTING, CONFLICT, READ_FAILED,
}
internal data class PickerCreateStatus(
    val phase: PickerCreatePhase = PickerCreatePhase.CLOSED,
    val fields: CustomLiftDraft? = null,
    val message: String? = null,
) {
    val busy: Boolean get() = phase in setOf(PickerCreatePhase.OPENING, PickerCreatePhase.SAVING,
        PickerCreatePhase.CHECKING, PickerCreatePhase.SELECTING)
    val editable: Boolean get() = phase in setOf(PickerCreatePhase.EDITING, PickerCreatePhase.KEEP_FAILED)
}

/** Creation admission and parent selection are separate. Reopening never replays either write. */
internal class PickerCreateRecovery(
    private val scope: CoroutineScope,
    private val drafts: CustomExerciseDrafts,
    private val repository: ExerciseRepository,
    private val stillOwned: suspend (DraftOwner) -> Boolean,
    private val ownedAtCommit: suspend (DraftOwner) -> Boolean,
) {
    private val mutableStatus = MutableStateFlow(PickerCreateStatus())
    val status: StateFlow<PickerCreateStatus> = mutableStatus
    private val serial = Mutex()
    private var owner: DraftOwner? = null
    private var handle: CustomLiftHandle? = null
    private var revision = 0L
    private var retryOpen: (() -> Unit)? = null
    private var visit = 0L
    private var opening: Job? = null

    fun closeVisit() {
        visit++
        opening?.cancel()
        opening = null
        revision++
        owner = null
        handle = null
        retryOpen = null
        mutableStatus.value = PickerCreateStatus()
    }

    fun open(requested: DraftOwner, name: String, muscle: String, onReady: (String) -> Unit,
        expectedId: String? = null, onCleared: () -> Unit = {}): Job? {
        if (status.value.busy) return null
        retryOpen = { open(requested, name, muscle, onReady, expectedId, onCleared) }
        val token = ++visit
        owner = requested
        handle = null
        mutableStatus.value = PickerCreateStatus(phase = PickerCreatePhase.OPENING)
        return scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                check(stillOwned(requested))
                val resumed = expectedId?.let { drafts.resumeForPicker(requested, it) }
                if (token != visit) return@withLock
                if (resumed == CustomLiftResume.AlreadyCleared) {
                    handle = null
                    mutableStatus.value = PickerCreateStatus()
                    onCleared()
                    return@withLock
                }
                val opened = (resumed as? CustomLiftResume.Existing)?.handle ?: drafts.openForPicker(requested, muscle)
                if (token != visit) return@withLock
                handle = opened
                if (!opened.resumed && opened.checkpoint.pending == null) {
                    drafts.checkpoint(opened, opened.authored.copy(name = name))
                }
                if (token != visit) return@withLock
                mutableStatus.value = PickerCreateStatus(phase = if (opened.checkpoint.pending != null)
                    PickerCreatePhase.CHECKING else PickerCreatePhase.EDITING,
                    fields = opened.authored, message = if (opened.checkpoint.pending != null) "Checking what saved…"
                    else if (opened.resumed) "Draft resumed" else null)
                onReady(opened.authored.createId)
                if (opened.ownerConflict) conflict()
                else if (opened.checkpoint.pending != null) inspect(opened, token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) {
                fail(PickerCreatePhase.READ_FAILED, "Custom lift could not be read. Check again; your draft remains kept.")
            } }
        } }.also { opening = it }
    }

    fun update(name: String, muscle: String, loadType: LoadType, notes: String) {
        if (!status.value.editable) return
        val current = handle ?: return
        val fields = checkNotNull(status.value.fields).copy(name = name, muscle = muscle, loadType = loadType, notes = notes)
        mutableStatus.value = status.value.copy(fields = fields)
        val admitted = ++revision
        val token = visit
        scope.launch { serial.withLock {
            if (token != visit || admitted != revision || !status.value.editable) return@withLock
            try {
                drafts.keepBeforeClose(current, fields)
                if (token == visit && admitted == revision) mutableStatus.value = status.value.copy(phase = PickerCreatePhase.EDITING, message = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit && admitted == revision) fail(PickerCreatePhase.KEEP_FAILED, "Draft was not kept. Retry before leaving.") }
        } }
    }

    fun save(onSaved: () -> Unit = {}) {
        if (status.value.phase !in setOf(PickerCreatePhase.EDITING, PickerCreatePhase.RETRYABLE)) return
        val current = handle ?: return
        val fields = checkNotNull(status.value.fields)
        if (fields.name.isBlank() || MuscleGroups.resolved(fields.muscle) == null) return
        val retry = status.value.phase == PickerCreatePhase.RETRYABLE
        val requested = checkNotNull(owner)
        val token = visit
        revision++
        mutableStatus.value = status.value.copy(phase = PickerCreatePhase.SAVING, message = "Saving lift…")
        scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                if (!stillOwned(requested)) { conflict(); return@withLock }
                if (retry) drafts.retry(current) else {
                    drafts.refreshCheckpoint(current)
                    drafts.admit(current, fields)
                }
                if (token != visit) return@withLock
                val ownerCheck: suspend () -> Boolean = { ownedAtCommit(requested) }
                val permit: () -> Boolean = { token == visit }
                val result = drafts.executeForPicker(handle = current, ownerChecked = ownerCheck, canCommit = permit)
                if (token != visit) return@withLock
                when (result) {
                    is SaveExerciseResult.Saved -> {
                        inspect(current, token)
                        if (token == visit && status.value.phase == PickerCreatePhase.SAVED) onSaved()
                    }
                    is SaveExerciseResult.DuplicateName -> {
                        if (drafts.inspect(current) == DraftRecoveryOutcome.UNCOMMITTED) {
                            drafts.editInstead(current)
                            fail(PickerCreatePhase.EDITING, "A lift already uses this name. Choose it in the list or change the name.")
                        } else conflict()
                    }
                    else -> conflict()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) uncertain() }
        } }
    }

    fun check() {
        if (status.value.busy) return
        if (status.value.phase == PickerCreatePhase.READ_FAILED) { retryOpen?.invoke(); return }
        val current = handle ?: return
        val token = visit
        val requested = checkNotNull(owner)
        val checkpointOnly = status.value.phase == PickerCreatePhase.KEEP_FAILED
        mutableStatus.value = status.value.copy(phase = PickerCreatePhase.CHECKING, message = "Checking…")
        scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                if (!stillOwned(requested)) { conflict(); return@withLock }
                if (checkpointOnly) {
                    drafts.refreshCheckpoint(current)
                    drafts.keepBeforeClose(current, checkNotNull(status.value.fields))
                    if (token != visit) return@withLock
                    mutableStatus.value = status.value.copy(phase = PickerCreatePhase.EDITING, message = "Draft kept")
                } else inspect(current, token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) {
                if (checkpointOnly) fail(PickerCreatePhase.KEEP_FAILED, "Draft was not kept. Retry before leaving.") else uncertain()
            } }
        } }
    }

    /** Caller proves the intended parent result. A failed handoff retains the accepted Create. */
    fun useSaved(select: suspend (Exercise) -> Boolean, onComplete: () -> Unit) {
        if (status.value.phase != PickerCreatePhase.SAVED) return
        val current = handle ?: return
        val token = visit
        val requested = checkNotNull(owner)
        mutableStatus.value = status.value.copy(phase = PickerCreatePhase.SELECTING, message = "Selecting lift…")
        scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                if (!stillOwned(requested)) { conflict(); return@withLock }
                val intended = checkNotNull(current.checkpoint.pending).value.intended
                if (repository.customDraftRecord(intended.row.id) != intended) {
                    conflict()
                    return@withLock
                }
                val exercise = checkNotNull(repository.getById(intended.row.id))
                if (token != visit) return@withLock
                check(select(exercise))
                if (token != visit) return@withLock
                check(drafts.inspect(current) == DraftRecoveryOutcome.EXACT_SAVED)
                drafts.finish(current)
                if (token != visit) return@withLock
                handle = null
                mutableStatus.value = PickerCreateStatus()
                onComplete()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) fail(PickerCreatePhase.SAVED, "Lift is saved in Library. Selection was not confirmed. Check the list or try selecting it again.") }
        } }
    }

    fun keep(onKept: () -> Unit) {
        if (status.value.busy) return
        val current = handle
        if (current == null) { onKept(); return }
        val fields = checkNotNull(status.value.fields)
        val token = visit
        val phase = status.value.phase
        revision++
        mutableStatus.value = status.value.copy(phase = PickerCreatePhase.CHECKING, message = "Keeping draft…")
        scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                drafts.keepBeforeClose(current, fields)
                if (token != visit) return@withLock
                mutableStatus.value = status.value.copy(phase = phase, message = null)
                onKept()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) fail(if (phase in setOf(PickerCreatePhase.EDITING, PickerCreatePhase.KEEP_FAILED))
                PickerCreatePhase.KEEP_FAILED else phase, "Draft was not kept. Retry before leaving.") }
        } }
    }

    /** Explicit discard clears this owner's private command, including unreadable drafts, never its saved lift. */
    fun discard(requested: DraftOwner, onDiscarded: () -> Unit) {
        if (status.value.busy || owner?.let { it != requested } == true) return
        val token = ++visit
        revision++
        val previous = status.value
        mutableStatus.value = previous.copy(phase = PickerCreatePhase.CHECKING, message = "Discarding draft…")
        scope.launch { serial.withLock {
            if (token != visit) return@withLock
            try {
                drafts.discardForPicker(requested)
                if (token != visit) return@withLock
                handle = null
                owner = null
                retryOpen = null
                mutableStatus.value = PickerCreateStatus()
                onDiscarded()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == visit) mutableStatus.value = previous.copy(
                message = "Draft discard was not confirmed. Fields remain here; try discarding again.") }
        } }
    }

    private suspend fun inspect(current: CustomLiftHandle, token: Long) {
        val outcome = drafts.inspect(current)
        if (token != visit) return
        when (outcome) {
            DraftRecoveryOutcome.EXACT_SAVED -> fail(PickerCreatePhase.SAVED, "Lift saved in Library. Use it here to continue.")
            DraftRecoveryOutcome.UNCOMMITTED -> fail(if (current.checkpoint.pending == null)
                PickerCreatePhase.EDITING else PickerCreatePhase.RETRYABLE, "Not saved yet. Your exact draft is kept.")
            DraftRecoveryOutcome.CONFLICT -> conflict()
            DraftRecoveryOutcome.UNAVAILABLE -> uncertain()
        }
    }
    private fun fail(phase: PickerCreatePhase, message: String) { mutableStatus.value = status.value.copy(phase = phase, message = message) }
    private fun conflict() { fail(PickerCreatePhase.CONFLICT, "This lift or its owner changed. Fields remain kept; check the current data before saving.") }
    private fun uncertain() { fail(PickerCreatePhase.UNCERTAIN, "Save was not confirmed. Check what saved before trying again.") }
}
