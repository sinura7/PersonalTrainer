package com.sinura.personaltrainer.data.draft

import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.workout.WorkoutCheckpointConflict
import com.sinura.personaltrainer.workout.WorkoutCheckpointUnavailable
import com.sinura.personaltrainer.workout.WorkoutDraftPersistence
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One live workout, private typed fields and separate immutable set/notes commands. */
internal class DurableWorkoutDrafts(
    private val store: DurableDraftStore,
    private val repository: WorkoutRepository,
    private val maintenance: DbMaintenance,
    private val restoreOpen: () -> Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkoutDraftPersistence {
    private val serial = Mutex()
    private val handles = mutableMapOf<String, Handle>()
    private class Reservation(val identity: String) { @Volatile var businessIssued = false }
    private class AccessFamily {
        val active = ConcurrentHashMap<String, String>()
        val reservations = ConcurrentHashMap<String, Reservation>()
    }
    private var family = AccessFamily()
    private val permits = ConcurrentHashMap<String, String>()
    /** Called under maintenance, including an opening that cannot reconstruct its fields. */
    private fun claimEntry(sessionId: String) {
        val permit = UUID.randomUUID().toString()
        permits[sessionId] = permit
        family.active[sessionId] = permit
    }
    override fun ownsEntry(sessionId: String): Boolean = permits[sessionId]?.let { family.active[sessionId] == it } == true
    internal fun invalidateEntries() { family.active.clear() }
    override fun mutationPermit(sessionId: String): com.sinura.personaltrainer.workout.WorkoutMutationPermit {
        val permit = permits[sessionId]
        return com.sinura.personaltrainer.workout.WorkoutMutationPermit { permit != null && family.active[sessionId] == permit }
    }
    override fun forEntry(): WorkoutDraftPersistence = DurableWorkoutDrafts(
        store = store, repository = repository, maintenance = maintenance,
        restoreOpen = restoreOpen, dispatcher = dispatcher,
    ).also { it.family = family }
    private fun requireActive(sessionId: String): String {
        val permit = permits[sessionId] ?: throw WorkoutCheckpointUnavailable()
        if (family.active[sessionId] != permit) throw WorkoutCheckpointConflict()
        return permit
    }
    private suspend fun <T> owned(sessionId: String, block: suspend () -> T): T {
        val permit = requireActive(sessionId)
        return ordered {
            if (family.active[sessionId] != permit) throw WorkoutCheckpointConflict()
            block()
        }
    }
    override fun commandIdentity(command: WorkoutSetSave): String = digest(WorkoutSetCheckpointCodec.encodeDraft(command).toString())
    private class Handle(
        val entryBinding: DraftBinding<WorkoutEntryCheckpoint, Unit>,
        var entry: DraftCheckpoint<WorkoutEntryCheckpoint, Unit>,
        val setBinding: DraftBinding<WorkoutSetSave, WorkoutSetSave>,
        var set: DraftCheckpoint<WorkoutSetSave, WorkoutSetSave>,
        val notesBinding: DraftBinding<WorkoutNotesCommand, WorkoutNotesCommand>,
        var notes: DraftCheckpoint<WorkoutNotesCommand, WorkoutNotesCommand>,
        var notesBase: String,
        var acknowledgedInProcess: String? = null,
        var retired: Boolean = false,
    )

    private suspend fun <T> ordered(block: suspend () -> T): T = withContext(dispatcher) {
        serial.withLock { maintenance.withMaintenanceLock {
            kotlin.coroutines.coroutineContext[com.sinura.personaltrainer.workout.WorkoutMutationPermit]?.requireAllowed()
            check(!restoreOpen()); block()
        } }
    }

    override suspend fun open(sessionId: String): WorkoutDraftRestore = try {
        ordered {
            claimEntry(sessionId)
            handles.remove(sessionId)?.retired = true
            val handle = load(sessionId)
            val current = repository.getSession(sessionId) ?: return@ordered WorkoutDraftRestore.OwnerConflict("The workout is no longer available.")
            if (current.isFinished) return@ordered WorkoutDraftRestore.OwnerConflict("The workout has finished. Review its saved sets in History.")
            if (!validOwner(handle.entry.draft, current)) return@ordered WorkoutDraftRestore.OwnerConflict("The workout or a staged lift changed.")
            if (handle.entry.needsReconciliation) {
                handle.entry = changed(handle.entryBinding.reconcileOwner(handle.entry.version, handle.entry.currentGeneration))
                // An external generation cannot authorize an old destructive reversal.
                handle.entry.draft?.takeIf { it.undo.isNotEmpty() }?.let { raw ->
                    handle.entry = changed(handle.entryBinding.checkpoint(handle.entry.version, raw.copy(undo = emptyList())))
                }
            }
            val command = handle.set.pending?.value
            if (command != null && (command.sessionId != sessionId || handle.set.draft != command ||
                    handle.set.pending?.identity != commandIdentity(command))) throw WorkoutCheckpointUnavailable()
            val notes = handle.notes.pending?.value
            if (notes != null && (notes.sessionId != sessionId || handle.notes.draft != notes)) throw WorkoutCheckpointUnavailable()
            handle.entry.draft?.editingOriginal?.let { original ->
                val saved = repository.editableSet(sessionId, original.setId)
                val admittedResult = command != null && repository.inspectSetSave(command) == WorkoutSetSaveResolution.SAVED
                if (!admittedResult && (saved == null || saved.values != original.values ||
                        saved.exerciseId != original.exerciseId || saved.completedAt != original.completedAt)) {
                    return@ordered WorkoutDraftRestore.OwnerConflict("The saved set changed. Review it before continuing the correction.")
                }
            }
            val marker = handle.entry.draft?.acknowledgedCommand
            val accepted = handle.set.pending?.phase == DraftCommandPhase.ACCEPTED && marker == handle.set.pending?.identity
            // A completed UI acknowledgement cannot be replayed over later authored fields.
            if (accepted) handle.set = changed(handle.setBinding.discard(handle.set.version, handle.set.currentGeneration))
            handles[sessionId] = handle
            WorkoutDraftRestore.ReadyEntry(
                entry = handle.entry.draft, pending = handle.set.pending?.value,
                resumed = handle.entry.draft != null || command != null,
                notesNeedReview = notes != null || handle.entry.draft?.let { entry ->
                    entry.notes != null && current.notes.trim() != entry.notesBase && current.notes.trim() != entry.notes.trim()
                } == true,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: WorkoutCheckpointConflict) {
        WorkoutDraftRestore.OwnerConflict("This workout changed. Review its saved work before continuing.")
    } catch (_: Exception) {
        WorkoutDraftRestore.ReadUnavailable
    }

    override suspend fun checkpoint(entry: WorkoutEntryCheckpoint) = owned(entry.sessionId) { checkpointEntry(entry) }

    private suspend fun checkpointEntry(entry: WorkoutEntryCheckpoint) {
        val handle = handle(entry.sessionId)
        val current = repository.getSession(entry.sessionId) ?: throw WorkoutCheckpointConflict()
        if (handle.retired || current.isFinished || !validOwner(entry, current)) throw WorkoutCheckpointConflict()
        handle.entry = read(handle.entryBinding)
        if (handle.entry.needsReconciliation) handle.entry = changed(handle.entryBinding.reconcileOwner(handle.entry.version, handle.entry.currentGeneration))
        if (entry.authoredRevision < (handle.entry.draft?.authoredRevision ?: 0)) return
        val marker = handle.acknowledgedInProcess ?: handle.entry.draft?.acknowledgedCommand
        val snapshot = entry.copy(notesBase = handle.notesBase, acknowledgedCommand = marker)
        handle.entry = changed(handle.entryBinding.checkpoint(handle.entry.version, snapshot))
    }

    override suspend fun <T> writeSet(command: WorkoutSetSave, write: suspend () -> T, acknowledge: suspend () -> T): T {
        requireActive(command.sessionId)
        val identity = commandIdentity(command)
        val reservation = checkNotNull(family.reservations.compute(command.sessionId) { _, old ->
            old?.takeIf { it.identity == identity } ?: Reservation(identity)
        })
        return owned(command.sessionId) {
        val handle = handle(command.sessionId)
        if (handle.retired) throw WorkoutCheckpointConflict()
        val current = repository.getSession(command.sessionId) ?: throw WorkoutCheckpointConflict()
        if (current.isFinished || !validOwner(handle.entry.draft, current)) throw WorkoutCheckpointConflict()
        refreshSet(handle)
        val old = handle.set.pending
        if (old != null && old.value != command) {
            if (old.phase != DraftCommandPhase.ACCEPTED || handle.entry.draft?.acknowledgedCommand != old.identity) {
                throw WorkoutCheckpointConflict()
            }
            handle.set = changed(handle.setBinding.clear(handle.set.version))
        }
        if (handle.set.pending == null) {
            if (handle.set.needsReconciliation) handle.set = changed(handle.setBinding.reconcileOwner(handle.set.version, handle.set.currentGeneration))
            handle.set = changed(handle.setBinding.beginSave(
                expected = handle.set.version, draft = command,
                commandIdentity = commandIdentity(command), command = command,
            ))
        } else {
            when (inspectSetLocked(handle, command)) {
                WorkoutSetSaveResolution.SAVED -> return@owned acknowledge()
                WorkoutSetSaveResolution.CONFLICT -> throw WorkoutCheckpointConflict()
                WorkoutSetSaveResolution.UNSAVED -> {
                    val pending = checkNotNull(handle.set.pending)
                    handle.set = changed(handle.setBinding.retry(handle.set.version, pending.identity))
                }
            }
        }
        refreshSet(handle)
        if (handle.set.needsReconciliation || handle.set.pending?.phase != DraftCommandPhase.PENDING ||
            handle.set.pending?.value != command || restoreOpen()
        ) throw WorkoutCheckpointConflict()
        reservation.businessIssued = true
        val result = write()
        val pending = checkNotNull(handle.set.pending)
        handle.set = changed(handle.setBinding.inspect(handle.set.version, pending.identity, DraftRecoveryOutcome.EXACT_SAVED))
        result
        }
    }

    override suspend fun inspectSet(
        command: WorkoutSetSave, inspect: suspend () -> WorkoutSetSaveResolution,
    ): WorkoutSetSaveResolution = owned(command.sessionId) {
        // The repository inspection is the authority; the callback is retained for the same
        // execution contract in injected graphs. The private generation further fences absence.
        val handle = handle(command.sessionId)
        refreshSet(handle)
        val observed = inspect()
        recordSetInspection(handle, command, observed)
    }

    private suspend fun inspectSetLocked(handle: Handle, command: WorkoutSetSave): WorkoutSetSaveResolution =
        recordSetInspection(handle, command, repository.inspectSetSave(command))

    private suspend fun recordSetInspection(
        handle: Handle, command: WorkoutSetSave, observed: WorkoutSetSaveResolution,
    ): WorkoutSetSaveResolution {
        refreshSet(handle)
        if (handle.set.pending == null) {
            if (observed == WorkoutSetSaveResolution.SAVED) return observed
            val reservation = family.reservations[command.sessionId]
            if (observed != WorkoutSetSaveResolution.UNSAVED || reservation?.identity != commandIdentity(command) || reservation.businessIssued) return WorkoutSetSaveResolution.CONFLICT
            val current = repository.getSession(command.sessionId) ?: return WorkoutSetSaveResolution.CONFLICT
            if (current.isFinished || !validOwner(handle.entry.draft, current)) return WorkoutSetSaveResolution.CONFLICT
            if (handle.set.needsReconciliation) handle.set = changed(handle.setBinding.reconcileOwner(handle.set.version, handle.set.currentGeneration))
            handle.set = changed(handle.setBinding.beginSave(
                expected = handle.set.version, draft = command, commandIdentity = commandIdentity(command), command = command,
            ))
        }
        val pending = checkNotNull(handle.set.pending)
        if (pending.value != command || handle.set.draft != command) throw WorkoutCheckpointUnavailable()
        if (pending.phase == DraftCommandPhase.ACCEPTED) {
            if (handle.set.needsReconciliation && observed != WorkoutSetSaveResolution.SAVED) return WorkoutSetSaveResolution.CONFLICT
            return WorkoutSetSaveResolution.SAVED
        }
        if (observed == WorkoutSetSaveResolution.UNSAVED && handle.set.needsReconciliation) return WorkoutSetSaveResolution.CONFLICT
        if (observed == WorkoutSetSaveResolution.CONFLICT) return observed
        if (handle.set.needsReconciliation) {
            handle.set = changed(handle.setBinding.reconcileOwner(handle.set.version, handle.set.currentGeneration))
        }
        val outcome = if (observed == WorkoutSetSaveResolution.SAVED) DraftRecoveryOutcome.EXACT_SAVED else DraftRecoveryOutcome.UNCOMMITTED
        handle.set = changed(handle.setBinding.inspect(handle.set.version, pending.identity, outcome))
        return observed
    }

    override suspend fun settleSet(command: WorkoutSetSave, entry: WorkoutEntryCheckpoint) = owned(command.sessionId) {
        val handle = handle(command.sessionId)
        refreshSet(handle)
        val pending = handle.set.pending ?: return@owned
        if (pending.value != command || command.sessionId != entry.sessionId) throw WorkoutCheckpointConflict()
        if (pending.phase != DraftCommandPhase.ACCEPTED && inspectSetLocked(handle, command) != WorkoutSetSaveResolution.SAVED) {
            throw WorkoutCheckpointConflict()
        }
        handle.acknowledgedInProcess = pending.identity
        val snapshot = entry.copy(acknowledgedCommand = pending.identity, notesBase = handle.notesBase)
        handle.entry = changed(handle.entryBinding.checkpoint(handle.entry.version, snapshot))
        // The durable acknowledgement marker comes first. A lost cleanup acknowledgement
        // therefore cannot reset a newer draft or authorize a second business write.
        handle.set = changed(handle.setBinding.clear(handle.set.version))
        family.reservations.remove(command.sessionId)
    }

    override suspend fun releaseSet(command: WorkoutSetSave) = owned(command.sessionId) {
        val handle = handle(command.sessionId)
        refreshSet(handle)
        val pending = handle.set.pending ?: return@owned
        if (pending.value != command) throw WorkoutCheckpointConflict()
        val observed = repository.inspectSetSave(command)
        if (observed == WorkoutSetSaveResolution.SAVED || pending.phase == DraftCommandPhase.ACCEPTED) throw WorkoutCheckpointConflict()
        if (observed == WorkoutSetSaveResolution.UNSAVED && !handle.set.needsReconciliation) {
            recordSetInspection(handle, command, observed)
            handle.set = changed(handle.setBinding.resumeEditing(handle.set.version, pending.identity))
        } else if (observed == WorkoutSetSaveResolution.CONFLICT || observed == WorkoutSetSaveResolution.UNSAVED && handle.set.needsReconciliation) {
            // Releasing the failed command never recreates a missing owner or writes a row.
            handle.set = changed(handle.setBinding.discard(handle.set.version, handle.set.currentGeneration))
        } else throw WorkoutCheckpointConflict()
        family.reservations.remove(command.sessionId)
    }

    override suspend fun writeNotes(sessionId: String, notes: String, entry: WorkoutEntryCheckpoint, write: suspend (String, String) -> Unit) = owned(sessionId) {
        if (entry.sessionId != sessionId || entry.notes != notes) throw WorkoutCheckpointConflict()
        checkpointEntry(entry)
        val handle = handle(sessionId)
        if (handle.retired) throw WorkoutCheckpointConflict()
        var current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
        if (current.isFinished) throw WorkoutCheckpointConflict()
        handle.notes = read(handle.notesBinding)
        handle.notes.pending?.let { old ->
            val command = old.value
            if (command.sessionId != sessionId || handle.notes.draft != command) throw WorkoutCheckpointUnavailable()
            val saved = old.phase == DraftCommandPhase.ACCEPTED || current.notes.trim() == command.intended
            if (!saved && (current.notes.trim() != command.before || handle.notes.needsReconciliation)) throw WorkoutCheckpointConflict()
            if (handle.notes.needsReconciliation) handle.notes = changed(handle.notesBinding.reconcileOwner(handle.notes.version, handle.notes.currentGeneration))
            if (!saved) {
                handle.notes = changed(handle.notesBinding.inspect(handle.notes.version, old.identity, DraftRecoveryOutcome.UNCOMMITTED))
                handle.notes = changed(handle.notesBinding.retry(handle.notes.version, old.identity))
                write(command.before, command.intended)
                current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
                if (current.notes.trim() != command.intended) throw WorkoutCheckpointConflict()
            }
            if (handle.notes.pending?.phase != DraftCommandPhase.ACCEPTED) {
                handle.notes = changed(handle.notesBinding.inspect(handle.notes.version, old.identity, DraftRecoveryOutcome.EXACT_SAVED))
            }
            preserveNotesBase(handle, command.intended)
            handle.notes = changed(handle.notesBinding.clear(handle.notes.version))
        }
        val intended = notes.trim()
        if (current.notes.trim() == intended) { preserveNotesBase(handle, intended); return@owned }
        if (current.notes.trim() != handle.notesBase) throw WorkoutCheckpointConflict()
        if (handle.notes.needsReconciliation) handle.notes = changed(handle.notesBinding.reconcileOwner(handle.notes.version, handle.notes.currentGeneration))
        val command = WorkoutNotesCommand(sessionId = sessionId, before = handle.notesBase, intended = intended)
        handle.notes = changed(handle.notesBinding.beginSave(
            expected = handle.notes.version, draft = command,
            commandIdentity = UUID.randomUUID().toString(), command = command,
        ))
        // Recheck after the admitted file write before touching the row.
        current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
        handle.notes = read(handle.notesBinding)
        if (current.isFinished || current.notes.trim() != command.before || handle.notes.needsReconciliation || restoreOpen()) {
            throw WorkoutCheckpointConflict()
        }
        write(command.before, intended)
        current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
        if (current.notes.trim() != intended) throw WorkoutCheckpointConflict()
        val pending = checkNotNull(handle.notes.pending)
        handle.notes = changed(handle.notesBinding.inspect(handle.notes.version, pending.identity, DraftRecoveryOutcome.EXACT_SAVED))
        preserveNotesBase(handle, intended)
        handle.notes = changed(handle.notesBinding.clear(handle.notes.version))
    }

    private suspend fun preserveNotesBase(handle: Handle, intended: String) {
        handle.notesBase = intended
        handle.entry = read(handle.entryBinding)
        val raw = handle.entry.draft ?: return
        if (handle.entry.needsReconciliation) {
            val current = repository.getSession(raw.sessionId) ?: throw WorkoutCheckpointConflict()
            if (!validOwner(raw, current)) throw WorkoutCheckpointConflict()
            handle.entry = changed(handle.entryBinding.reconcileOwner(handle.entry.version, handle.entry.currentGeneration))
        }
        handle.entry = changed(handle.entryBinding.checkpoint(handle.entry.version, raw.copy(notesBase = intended)))
    }

    override suspend fun inspectFinish(sessionId: String, notes: String?): String? = ordered { inspectFinishLocked(sessionId, notes) }

    private suspend fun inspectFinishLocked(sessionId: String, notes: String?): String? {
        val handle = handles[sessionId] ?: load(sessionId)
        val current = repository.getSession(sessionId) ?: return "This workout is no longer available."
        if (current.isFinished) return null
        if (!validOwner(handle.entry.draft, current)) return "Return to the workout to review its restored draft."
        val raw = handle.entry.draft
        if (notes == null && raw?.editingOriginal != null) return "Return to the workout to save or cancel its unfinished correction."
        if (raw?.notes != null && current.notes.trim() != raw.notesBase && current.notes.trim() != raw.notes.trim()) return "Return to the workout to review its changed notes before finishing."
        handle.set = read(handle.setBinding)
        handle.set.pending?.let { pending ->
            if (pending.phase != DraftCommandPhase.ACCEPTED && repository.inspectSetSave(pending.value) != WorkoutSetSaveResolution.SAVED) {
                return "Return to the workout to resolve the pending set before finishing."
            }
        }
        handle.notes = read(handle.notesBinding)
        if (handle.notes.pending != null || notes == null && handle.entry.draft?.notes?.trim()?.let { it != current.notes.trim() } == true) {
            return "Return to the workout to keep or review its pending notes before finishing."
        }
        return null
    }

    override suspend fun <T> finishAtomically(sessionId: String, notes: String?, write: suspend (String) -> T): T = ordered {
        inspectFinishLocked(sessionId, notes)?.let { throw com.sinura.personaltrainer.workout.WorkoutFinishRefused(it) }
        val current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
        if (current.isFinished || current.sets.isEmpty()) throw WorkoutCheckpointConflict()
        val result = write(notes ?: current.notes)
        // The finish is acknowledged. Cleanup failure cannot turn it into a failed/replayed write.
        try { retireOwned(sessionId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { handles[sessionId]?.retired = true }
        result
    }

    override suspend fun retire(sessionId: String) = ordered { retireOwned(sessionId) }

    private suspend fun retireOwned(sessionId: String) {
        family.active.remove(sessionId)
        val handle = handles[sessionId] ?: load(sessionId)
        val current = repository.getSession(sessionId)
        if (current?.isFinished == false) throw WorkoutCheckpointConflict()
        handle.retired = true
        handle.entry = read(handle.entryBinding)
        handle.set = read(handle.setBinding)
        handle.notes = read(handle.notesBinding)
        handle.entry = changed(handle.entryBinding.discard(handle.entry.version, handle.entry.currentGeneration))
        handle.set = changed(handle.setBinding.discard(handle.set.version, handle.set.currentGeneration))
        handle.notes = changed(handle.notesBinding.discard(handle.notes.version, handle.notes.currentGeneration))
        family.reservations.remove(sessionId)
        handles.remove(sessionId)
        Unit
    }

    override suspend fun discardEntry(sessionId: String) = owned(sessionId) {
        // Retire previously queued operations. A failed clear keeps a new private-only
        // claim for Retry, without a reconstructed handle that could submit old fields.
        claimEntry(sessionId)
        handles.remove(sessionId)?.retired = true
        family.reservations.remove(sessionId)
        // Explicitly abandon only private unfinished fields/commands. Saved rows remain intact.
        val owner = digest(sessionId)
        suspend fun <D, C> clear(binding: DraftBinding<D, C>) {
            when (val read = binding.read()) {
                is DraftRead.PresentCheckpoint -> changed(binding.discard(read.checkpoint.version, read.checkpoint.currentGeneration))
                is DraftRead.UnreadableCheckpoint -> changed(binding.discardUnreadable())
            }
        }
        clear(store.bind(DraftOwner(DraftForm.WORKOUT, "entry:$owner"), WorkoutCheckpointCodec))
        clear(store.bind(DraftOwner(DraftForm.WORKOUT, "set:$owner"), WorkoutSetCheckpointCodec))
        clear(store.bind(DraftOwner(DraftForm.WORKOUT, "notes:$owner"), WorkoutNotesCheckpointCodec))
        family.active.remove(sessionId)
        Unit
    }

    private fun validOwner(entry: WorkoutEntryCheckpoint?, current: WorkoutSession): Boolean {
        if (entry == null) return true
        val ids = current.exercises.map { it.exercise.id }.toSet()
        return entry.sessionId == current.id && entry.startedAt == current.startedAt &&
            entry.lifts.all { (id, draft) -> id in ids && draft.exerciseId == id && draft.sessionId == current.id } &&
            (entry.selectedExerciseId == null || entry.selectedExerciseId in ids) &&
            entry.numbers.all { it.exerciseId == null || it.exerciseId in ids } &&
            (entry.clocks?.holdExerciseId == null || entry.clocks.holdExerciseId in ids) &&
            entry.clocks?.stopwatches.orEmpty().keys.all { it in ids }
    }

    private suspend fun load(sessionId: String): Handle {
        val owner = digest(sessionId)
        val entryBinding = store.bind(DraftOwner(DraftForm.WORKOUT, "entry:$owner"), WorkoutCheckpointCodec)
        val setBinding = store.bind(DraftOwner(DraftForm.WORKOUT, "set:$owner"), WorkoutSetCheckpointCodec)
        val notesBinding = store.bind(DraftOwner(DraftForm.WORKOUT, "notes:$owner"), WorkoutNotesCheckpointCodec)
        val entry = read(entryBinding)
        val current = repository.getSession(sessionId) ?: throw WorkoutCheckpointConflict()
        return Handle(
            entryBinding = entryBinding, entry = entry, setBinding = setBinding, set = read(setBinding),
            notesBinding = notesBinding, notes = read(notesBinding),
            notesBase = entry.draft?.notesBase ?: current.notes.trim(),
        )
    }
    private fun handle(sessionId: String): Handle = handles[sessionId] ?: throw WorkoutCheckpointUnavailable()
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private suspend fun refreshSet(handle: Handle) { handle.set = read(handle.setBinding) }
    private suspend fun <D, C> read(binding: DraftBinding<D, C>): DraftCheckpoint<D, C> = when (val result = binding.read()) {
        is DraftRead.PresentCheckpoint -> result.checkpoint
        is DraftRead.UnreadableCheckpoint -> throw WorkoutCheckpointUnavailable()
    }
    private fun <D, C> changed(result: DraftChange<D, C>): DraftCheckpoint<D, C> = when (result) {
        is DraftChange.Written -> result.checkpoint
        is DraftChange.Rejected -> throw WorkoutCheckpointConflict()
        is DraftChange.Unavailable -> throw WorkoutCheckpointUnavailable()
    }
}
