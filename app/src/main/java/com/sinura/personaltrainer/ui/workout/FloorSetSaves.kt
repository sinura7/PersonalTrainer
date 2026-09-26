package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The workout floor's set save ([ActiveWorkoutViewModel]): the one frozen set being written, its
 * two copies (the draft cache for this run, the saved state for when Android stops the app), its
 * phase and words, whether a write or a check is in flight, and the check that settles a save
 * whose outcome is not known.
 *
 * A set is frozen before anything suspends ([freeze]): its id, time and values are fixed then,
 * and Retry writes exactly those, never today's draft or timer. A save whose outcome is unknown
 * (one restored after Android stopped the app, or one whose check failed) is inspected before it
 * is written again ([reconcile]), so a set is never saved twice, nor over a correction made since.
 *
 * What a saved or released set does next is the ViewModel's: the draft, the selection, the edit,
 * the timers, the rest, the receipt, the feedback and the error slot. So this class takes the two
 * repository calls and three callbacks, owns no scope and launches nothing, like
 * [FloorUndoOffers].
 *
 * The frozen set is read back from the cache, then the saved state, when this is built; the
 * ViewModel writes it back to both at the point of its `init` where it always has
 * ([keepRestored]).
 */
internal class FloorSetSaves(
    sessionId: String,
    private val cache: WorkoutDraftCache,
    private val saved: SavedStateWorkoutSave,
    private val write: suspend (WorkoutSetSave) -> WorkoutRepository.SavedWorkoutSet,
    private val inspect: suspend (WorkoutSetSave) -> WorkoutSetSaveResolution,
    /** Whether the workout is loaded and open; a restored set is written again only then. */
    private val sessionFound: () -> Boolean,
    /** The set is in the database: [result] when this save wrote it, null when a check found it. */
    private val onSaved: (command: WorkoutSetSave, result: WorkoutRepository.SavedWorkoutSet?, recovered: Boolean) -> Unit,
    /** Edit let go of a set the database does not hold as it; [conflict] when another row is there. */
    private val onReleased: (command: WorkoutSetSave, conflict: Boolean) -> Unit,
    /** A write was refused. */
    private val onRejected: () -> Unit,
) {
    /** The frozen set this ViewModel was built with, from the cache or else the saved state. */
    val restored: WorkoutSetSave? = cache.pendingSave(sessionId) ?: saved.read(sessionId)

    private val _state = MutableStateFlow(WorkoutSaveState(
        phase = if (restored == null) WorkoutSavePhase.IDLE else WorkoutSavePhase.CHECKING,
        command = restored,
    ))

    /** The frozen set, its phase and its words. */
    val operation: StateFlow<WorkoutSaveState> = _state.asStateFlow()

    private val _inFlight = MutableStateFlow(false)

    /** A write or a check is running. The floor calls it logging. */
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    /** A set is frozen, in whatever phase. Entry and selection stay locked while it is. */
    val pending: Boolean get() = _state.value.pending

    /** Writes the restored set back to both copies and returns it; null when there was none. */
    fun keepRestored(): WorkoutSetSave? {
        val command = restored ?: return null
        cache.putPendingSave(command)
        saved.write(command)
        return command
    }

    /** Freezes [command] in both copies and marks it being written. The caller launches [persist]. */
    fun freeze(command: WorkoutSetSave) {
        // Freeze identity and payload synchronously, before Room or any coroutine suspension.
        cache.putPendingSave(command)
        saved.write(command)
        _state.value = WorkoutSaveState(WorkoutSavePhase.SAVING, command)
        _inFlight.value = true
    }

    /**
     * Retry belongs to the frozen command, never to today's editable draft or timer. The command
     * to [reconcile] with a write, or null when there is none, one is running, or it conflicts.
     */
    fun beginRetry(): WorkoutSetSave? {
        val operation = _state.value
        val command = operation.command ?: return null
        if (operation.busy || operation.phase == WorkoutSavePhase.CONFLICT) return null
        _state.value = operation.copy(phase = WorkoutSavePhase.CHECKING, message = null)
        _inFlight.value = true
        return command
    }

    /**
     * Inspect before releasing a failed operation for editing; an unknown outcome stays owned.
     * The command to [reconcile] with a release, or null when there is none or one is running.
     */
    fun beginEdit(): WorkoutSetSave? {
        val operation = _state.value
        val command = operation.command ?: return null
        if (operation.busy) return null
        _state.value = operation.copy(phase = WorkoutSavePhase.CHECKING, message = null)
        _inFlight.value = true
        return command
    }

    /** Whether [command] is still the frozen set. */
    fun owns(command: WorkoutSetSave): Boolean = _state.value.command == command

    /** Lets go of [command]: the cache's copy always, the saved state and the phase if it is still the frozen set. */
    fun clear(command: WorkoutSetSave) {
        cache.clearPendingSave(command)
        if (_state.value.command == command) {
            saved.clear()
            _state.value = WorkoutSaveState()
        }
    }

    /**
     * Settles [command] against the database. Found: it was saved ([onSaved], recovered). Not
     * there: released for editing, written again ([retryWrite]) while the workout is open, or
     * offered for Retry. Another row there: released without replay, or shown as a conflict.
     */
    suspend fun reconcile(
        command: WorkoutSetSave,
        retryWrite: Boolean,
        releaseUnwritten: Boolean = false,
    ) {
        try {
            when (inspect(command)) {
                WorkoutSetSaveResolution.SAVED -> onSaved(command, null, true)
                WorkoutSetSaveResolution.UNSAVED -> {
                    if (releaseUnwritten) {
                        clear(command)
                        onReleased(command, false)
                    } else if (retryWrite && sessionFound()) {
                        _state.value = WorkoutSaveState(WorkoutSavePhase.SAVING, command)
                        persist(command)
                    } else {
                        _state.value = WorkoutSaveState(
                            WorkoutSavePhase.FAILED, command,
                            "This set has not been saved. Retry to save these values.",
                        )
                    }
                }
                WorkoutSetSaveResolution.CONFLICT -> {
                    if (releaseUnwritten) {
                        // Inspection proved this command is not the stored row. Never replay it.
                        clear(command)
                        onReleased(command, true)
                    } else {
                        _state.value = WorkoutSaveState(
                            WorkoutSavePhase.CONFLICT, command, WorkoutRepository.SetSaveConflict().message,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.w(TAG, "Could not establish the saved set outcome", failure)
            _state.value = WorkoutSaveState(
                WorkoutSavePhase.FAILED, command,
                "Could not confirm whether this set was saved. Retry will check before saving again.",
            )
        } finally {
            _inFlight.value = false
        }
    }

    /** Writes [command]. Refused: says why and keeps it for Retry. Written: [onSaved]. */
    suspend fun persist(command: WorkoutSetSave) {
        val result = try {
            write(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.w(TAG, "Set save did not acknowledge completion", failure)
            _state.value = WorkoutSaveState(
                phase = if (failure is WorkoutRepository.SetSaveConflict) WorkoutSavePhase.CONFLICT else WorkoutSavePhase.FAILED,
                command = command,
                message = failure.message?.takeIf { SetLogRules.isFieldMessage(it) }
                    ?: if (failure is WorkoutRepository.SetSaveConflict) failure.message else LogCommitCopy.WRITE_FAILED,
            )
            onRejected()
            _inFlight.value = false
            return
        }
        // A receipt, timer, or feedback failure after this point cannot create Retry save.
        onSaved(command, result, result.alreadySaved)
        _inFlight.value = false
    }

    private companion object {
        /** The ViewModel's, so the diagnostics read as they did before the move. */
        const val TAG = "PT/ActiveWorkoutVM"
    }
}
