package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerSwapDrafts
import com.sinura.personaltrainer.data.draft.PickerSwapHandle
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.data.repository.WorkoutPickerSwap
import com.sinura.personaltrainer.ui.components.OwnedPickerRecovery

import android.app.Application
import com.sinura.personaltrainer.R
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import com.sinura.personaltrainer.workout.WorkoutDraftRestore
import com.sinura.personaltrainer.workout.WorkoutEntryCheckpoint
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.util.PickerWrites
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.testonly.WalkthroughRestCaptureHook
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EffortPrefillPolicy
import com.sinura.personaltrainer.domain.EffortSource
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.FloorTimerCue
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.PendingLiftSwitch
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.LogReceipt
import com.sinura.personaltrainer.domain.LogReceiptCopy
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestPlan
import com.sinura.personaltrainer.domain.RestPlanSource
import com.sinura.personaltrainer.domain.RestPrescription
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionEditRules
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import com.sinura.personaltrainer.workout.SavedStateFloorUndo
import com.sinura.personaltrainer.workout.FloorUndo
import com.sinura.personaltrainer.workout.UndoEntry
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.FinishOutcome
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PT/ActiveWorkoutVM"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_LOAD = "load"
private const val ERR_ADD_LIFT = "addLift"
private const val ERR_REMOVE_LIFT = "removeLift"
private const val ERR_LOG_SET = "logSet"
private const val ERR_EDIT_SET = "editSet"
private const val ERR_DELETE_SET = "deleteSet"
private const val ERR_UNDO_DELETE = "undoDelete"
private const val ERR_UNDO_REMOVE = "undoRemove"
private const val ERR_FINISH = "finish"
private const val ERR_DISCARD = "discard"
private const val ERR_SKIP = "skip"

data class ActiveExerciseDraft(
    val weightKg: Double = 0.0,
    val reps: Int = 5,
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
    val durationSeconds: Int? = null,
)

/** Whether the session row behind this screen has been resolved yet. */
enum class SessionLoadState {
    /** The session Flow has not emitted anything yet. */
    LOADING,

    /** A session row exists and is in [ActiveWorkoutUiState.session]. */
    FOUND,

    /**
     * The Flow emitted null for a real id: the workout was discarded, restored over, or the
     * id came from a stale notification. Terminal — never resolves into FOUND on its own, so
     * the UI must offer a way out instead of spinning forever.
     */
    MISSING,

    /** The row could not be read. Retain the draft and explicitly re-subscribe on Retry. */
    FAILED,
}

/**
 * Why this screen asked to be popped. The two exits land in different places — finishing goes
 * on to the summary, discarding pops back — so the signal carries which one it was, and
 * finishing carries the session the summary is about.
 */
sealed interface WorkoutExit {
    /** Session was written to history. */
    data class Finished(val sessionId: String) : WorkoutExit

    /** Session row was deleted. */
    data object Discarded : WorkoutExit

    /** Leave the current workout running after keeping its draft. */
    data object Kept : WorkoutExit
}

data class ActiveWorkoutUiState(
    val loadState: SessionLoadState = SessionLoadState.LOADING,
    val session: WorkoutSession? = null,
    val selectedExerciseId: String? = null,
    val draft: ActiveExerciseDraft = ActiveExerciseDraft(),
    val hint: ProgressionHint? = null,
    /** What this lift looked like last time, shown beside the inputs. Null on its first ever session. */
    val lastPerformance: ExerciseSessionSummary? = null,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    val notes: String = "",
    val notesSave: NotesSaveState = NotesSaveState(),
    val error: String? = null,
    val pickerProgress: ExercisePickerProgress = ExercisePickerProgress(),
    val finished: Boolean = false,
    val editingSetId: String? = null,
    /**
     * True while a log or edit-save is in flight. The Log button is disabled so a
     * double tap cannot write two sets.
     */
    val logging: Boolean = false,
    val save: WorkoutSaveState = WorkoutSaveState(),
    val mutating: Boolean = false,
    val liftReadiness: LiftEntryReadiness = LiftEntryReadiness.NONE,
    val suggestionUnavailable: Boolean = false,
    val draftDirty: Boolean = false,
    /** True while the picker is exchanging a lift rather than adding one. */
    val swapping: Boolean = false,
    /**
     * Variants of the lift being swapped, pinned above the general list.
     *
     * Swapping is almost always "same movement, different kit" — the bench is taken, the cable
     * station is free. Making the user search for "incline dumbbell" to express that, in a list
     * of 98, is the thing this section removes.
     */
    val swapSiblings: List<Exercise> = emptyList(),
    /** The coach's pick for this session, pinned above the picker's results. */
    val suggestion: Exercise? = null,
    val suggestionReason: String? = null,
    /** Working-set effort in the draft when effort applies; null for warm-ups and holds. */
    val effortValue: Int? = null,
    /** Whether [effortValue] is prefilled, coach-applied, user-picked, or absent (R1 lane B). */
    val effortSource: EffortSource = EffortSource.NONE,
) {
    val isLoading: Boolean get() = loadState == SessionLoadState.LOADING

    val entryLocked: Boolean get() = save.pending || logging || mutating || loadState != SessionLoadState.FOUND

    val canLog: Boolean
        get() = loadState == SessionLoadState.FOUND && session != null &&
            selectedExerciseId != null &&
            !entryLocked &&
            (liftReadiness.allowsCommit() || draftDirty)

    /** True when [canLog] but a working set still needs an effort before Log may act (P2a). */
    val effortMissingForCommit: Boolean
        get() {
            if (!canLog) return false
            val exerciseId = selectedExerciseId ?: return false
            val lift = session?.exercises?.firstOrNull { it.exercise.id == exerciseId } ?: return false
            val hold = HoldWork.isHold(lift.exercise)
            return SetLogRules.validateEffort(rpe = effortValue, isWarmup = draft.isWarmup, isHold = hold) != null
        }

    val logCommitReady: Boolean get() = canLog && !effortMissingForCommit

    /** Explicit chip selection ([EffortSource.USER] or coach Apply [EffortSource.COACH]). */
    val effortChipSelected: Int?
        get() = if (effortSource == EffortSource.USER || effortSource == EffortSource.COACH) {
            effortValue
        } else {
            null
        }

    /** Dotted prefill chip ([EffortSource.PREFILL_LAST]). */
    val effortChipPrefilled: Int?
        get() = if (effortSource == EffortSource.PREFILL_LAST) effortValue else null

    val canFinish: Boolean
        get() = loadState == SessionLoadState.FOUND && session?.sets?.isNotEmpty() == true && !entryLocked

    val showDiscard: Boolean
        get() = loadState == SessionLoadState.FOUND && session != null && session.sets.isEmpty() && !entryLocked

    val showRest: Boolean
        get() {
            val current = session ?: return false
            return if (FloorCompactChrome.emptySessionHidesTimerDock()) {
                current.hasLifts()
            } else {
                true
            }
        }

    val offerSetClock: Boolean
        get() {
            if (!showRest) return false
            val lift = session?.exercises?.firstOrNull { it.exercise.id == selectedExerciseId }
                ?: return false
            return !HoldWork.isHold(lift.exercise)
        }
}

/** A record broken by the set just logged, for the in-workout moment. */
data class PersonalRecordMoment(
    val exerciseName: String,
    val kinds: Set<PersonalRecordKind>,
    val weightKg: Double,
    val reps: Int,
)

private const val REST_IMMERSION_LIFT_KEY = "workout.restImmersionLiftId"

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel internal constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies,
    private val undoTimeout: UndoTimeoutProvider,
    private val pickerContexts: PickerContextDrafts?,
    private val customLiftDrafts: CustomExerciseDrafts?,
    private val swapDrafts: PickerSwapDrafts?,
) : AppViewModel(application, container) {
    @JvmOverloads constructor(application: Application, savedStateHandle: SavedStateHandle,
        container: AppDependencies = application.appContainer(),
        undoTimeout: UndoTimeoutProvider = systemUndoTimeout(application)) :
        this(
            application,
            savedStateHandle,
            container,
            undoTimeout,
            (container as? AppContainer)?.pickerContextDrafts,
            (container as? AppContainer)?.customExerciseDrafts,
            (container as? AppContainer)?.pickerSwapDrafts,
        )
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val draftCache = container.workoutDraftCache
    private val durability = draftCache.persistence?.forEntry()
    private val privateSavedState = savedStateHandle
    private var authoredRevision = maxOf(draftCache.authoredRevision(sessionId), savedStateHandle.get<Long>("workout.private.authorship.v1") ?: 0L)
    private val savedDraft = SavedStateWorkoutDraft(savedStateHandle)
    private val savedEdit = SavedStateWorkoutSave(handle = savedStateHandle, storageKey = "workout.editOriginal.v1")
    private var editingOriginal = draftCache.editingOriginal(sessionId) ?: savedEdit.read(sessionId)
    private val mutating = MutableStateFlow(false)

    /**
     * The set being saved, its copies and its outcome (audit W2d-2). What a saved or released set
     * does next stays here, in [acknowledgeSave] and [releaseSave].
     */
    private val saves = FloorSetSaves(
        sessionId = sessionId,
        cache = draftCache,
        saved = SavedStateWorkoutSave(savedStateHandle),
        write = { command ->
            val port = durability
            if (port == null) container.workoutRepository.saveSet(command)
            else {
                val commit: suspend () -> com.sinura.personaltrainer.data.repository.WorkoutRepository.SavedWorkoutSet = { container.workoutRepository.saveSet(command) }
                val inspectAccepted: suspend () -> com.sinura.personaltrainer.data.repository.WorkoutRepository.SavedWorkoutSet = { container.workoutRepository.acknowledgeSavedSet(command) }
                port.writeSet(command = command, write = commit, acknowledge = inspectAccepted)
            }
        },
        inspect = { command ->
            durability?.inspectSet(command) { container.workoutRepository.inspectSetSave(command) }
                ?: container.workoutRepository.inspectSetSave(command)
        },
        sessionFound = { sessionReader.observations.value.loadState == SessionLoadState.FOUND },
        onSaved = { command, result, recovered ->
            if (durability?.ownsEntry(sessionId) == false) privateDrafts.ownershipLost()
            else acknowledgeSave(command, result, recovered)
        },
        onReleased = { command, conflict -> releaseSave(command, conflict) },
        onRejected = { _logFeedback.tryEmit(LogCommitFeedback.REJECT) },
        beforeRelease = { command ->
            durability?.releaseSet(command)
            // A newer editor can claim ownership while the IO result waits to resume here.
            // Fence shared-cache cleanup as well as the subsequent entry callback.
            if (durability?.ownsEntry(sessionId) == false) {
                privateDrafts.ownershipLost()
                throw com.sinura.personaltrainer.workout.WorkoutCheckpointConflict()
            }
        },
        afterSaved = { command -> privateCheckpoint()?.let { durability?.settleSet(command, it) } },
        onSettlementUnavailable = { privateDrafts.preservationFailed() },
    )

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(ActiveExerciseDraft())
    private val hint = MutableStateFlow<ProgressionHint?>(null)

    /**
     * The load the hint on screen came from, and the settings it was read under (W2e). Null while a
     * lift loads, and after a load that degraded: a lift whose suggestion could not be read is not
     * read again in a live Log; a reopen reads it again.
     */
    private val hintStamp = MutableStateFlow<HintStamp?>(null)
    private val lastPerformance = MutableStateFlow<ExerciseSessionSummary?>(null)

    /**
     * Every finished working set of the selected lift from other sessions, so the floor's
     * Best set is judged against the whole log the way History's records are. Loaded with
     * the prefill and cleared with it; today's rows are added at read time, never here.
     */
    private val priorHistory = MutableStateFlow<List<ExerciseSetRecord>>(emptyList())
    val exerciseHistory: StateFlow<List<ExerciseSetRecord>> = priorHistory.asStateFlow()
    private val effortSource = MutableStateFlow(EffortSource.NONE)
    private val restTotal = MutableStateFlow(
        PlannedRest(
            seconds = RestTimerPreferences.DEFAULT_SECONDS,
            sourceTag = RestPlan(
                seconds = RestTimerPreferences.DEFAULT_SECONDS,
                source = RestPlanSource.DEFAULT,
            ).sourceTag(),
            chosenFor = null,
        ),
    )
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)

    /**
     * The session-exercise row the picker is about to replace, or null when it is adding.
     *
     * Held here rather than passed through the picker so the sheet stays a plain "choose a
     * lift" surface; what happens to the choice is this class's business.
     */
    private val swapTargetItemId = MutableStateFlow<String?>(null)
    private var pendingSwap: PickerSwapHandle? = null
    private var acceptedSwap: WorkoutPickerSwap? = null
    private var swapOutcome: DraftRecoveryOutcome? = null
    private fun pickerOwner() = workoutPickerOwner(sessionId, swapTargetItemId.value)
    internal val pickerRecovery = if (pickerContexts != null && customLiftDrafts != null) {
        OwnedPickerRecovery(
            scope = viewModelScope, contextDrafts = pickerContexts, customDrafts = customLiftDrafts,
            repository = container.exerciseRepository, savedState = savedStateHandle,
            owner = { pickerOwner().also { check(sessionId.isNotBlank()) } },
            stillOwned = { owner -> container.dbMaintenance.withMaintenanceLock { pickerStillOwnedLocked(owner) } },
            ownedAtCommit = ::pickerStillOwnedLocked,
            onQuery = { searchQuery.value = it },
            selectCreated = { exercise -> selectOwnedPickerLift(exercise) },
            onSelected = ::finishPickerSelection,
            selectionBlocked = { pendingSwap?.checkpoint?.pending != null || swapOutcome == DraftRecoveryOutcome.UNAVAILABLE },
            selectionBusy = { pickerWrites.busy },
            discardSelection = ::discardPickerSwap,
            onClearedChild = ::offerCompletedSwapContinuation,
            retryLabel = { when (swapOutcome) {
                DraftRecoveryOutcome.EXACT_SAVED -> "Continue"
                DraftRecoveryOutcome.UNCOMMITTED -> "Retry exact swap"
                DraftRecoveryOutcome.UNAVAILABLE, DraftRecoveryOutcome.CONFLICT -> "Check again"
                null -> "Retry"
            } },
        )
    } else null

    private suspend fun pickerStillOwnedLocked(owner: DraftOwner): Boolean {
        if (container.backupService.restoreInProgress() || owner != pickerOwner() || durability?.ownsEntry(sessionId) == false) return false
        return container.workoutRepository.getSession(sessionId)?.let { current ->
            val target = swapTargetItemId.value
            !current.isFinished && (target == null || current.exercises.any { it.id == target } ||
                acceptedSwap?.takeIf { it.before.id == target }?.let {
                    container.workoutRepository.inspectPickerSwap(it) == DraftRecoveryOutcome.EXACT_SAVED
                } == true)
        } == true
    }

    /**
     * The session's notes, the one-time fill from the row and the two writes, at a typing pause and
     * at Back (audit W2d-3b). Built before `init` restores the words; the draft that carries them,
     * and Finish, stay here.
     */
    private val notesOwner = Any()
    private val sessionNotes = FloorSessionNotes(
        sessionId = sessionId,
        write = { notes ->
            val noteEntry = if (durability == null) null else (privateCheckpoint()?.takeIf { it.notes == notes }
                ?: throw com.sinura.personaltrainer.workout.WorkoutCheckpointConflict())
            draftCache.withNotesWrite(sessionId) {
                check(container.workoutRepository.getSession(sessionId)?.isFinished == false) {
                    "This workout is no longer live."
                }
                val port = durability
                if (port == null) container.workoutRepository.updateSessionNotes(sessionId, notes)
                else port.writeNotes(sessionId, notes, checkNotNull(noteEntry)) { before, intended ->
                    container.workoutRepository.updateLiveNotesIfUnchanged(sessionId, before, intended)
                }
            }
        },
        readStored = { container.workoutRepository.getSession(sessionId)?.notes },
        onUnconfirmed = { pending, busy, notes ->
            if (durability?.ownsEntry(sessionId) == false) draftCache.releaseNotesOwner(sessionId, notesOwner)
            else draftCache.markNotesPending(sessionId = sessionId, owner = notesOwner, pending = pending, busy = busy, notes = notes)
        },
    )
    private val pickerWrites = PickerWrites()
    private val error = ErrorSlot()
    private val finished = MutableStateFlow(false)
    private val _exitRequested = MutableStateFlow<WorkoutExit?>(null)
    val exitRequested: StateFlow<WorkoutExit?> = _exitRequested.asStateFlow()
    private val editingSetId = MutableStateFlow<String?>(null)
    private val liftReadiness = MutableStateFlow(LiftEntryReadiness.NONE)
    private val suggestionUnavailable = MutableStateFlow(false)
    private val draftDirty = MutableStateFlow(false)
    /**
     * The live entry and the snapshot written for an exit share this lock.
     * A fill that resumes off the main thread must not read "nobody has typed" and then
     * store 0 kg over a weight that was typed while that fill was still reading.
     */
    private val entryWrites = Any()

    /**
     * Test seam. Production leaves it null. A test uses it to type a weight after this fill
     * has decided the entry looks untouched and before that decision is applied.
     */
    @VisibleForTesting
    internal var beforeUntouchedSeed: (() -> Unit)? = null

    /** Fires after that same decision has been applied or refused. Production leaves it null. */
    @VisibleForTesting
    internal var afterUntouchedSeed: (() -> Unit)? = null

    /** When true, the next [settleSwapEntry] throws once so tests can drive [retryPersistedSwap]. */
    @VisibleForTesting
    internal var throwOnceOnSettleSwapForTest: Boolean = false
    private var prefillGeneration = 0
    private val wantAnotherSet = MutableStateFlow(false)
    private val tempoTipDismissed = MutableStateFlow(false)
    val tempoCoachDismissed: StateFlow<Boolean> = tempoTipDismissed.asStateFlow()
    private val tempoCoachSheetVisible = MutableStateFlow(false)
    private val showRestFirstCaption = MutableStateFlow(false)
    val restFirstCaptionVisible: StateFlow<Boolean> = showRestFirstCaption.asStateFlow()
    /** Lift that owns the full-screen rest floor; cleared when rest stops. */
    private val restImmersionLiftIdStore = MutableStateFlow(
        privateSavedState.get<String?>(REST_IMMERSION_LIFT_KEY),
    )
    val restImmersionLiftId: StateFlow<String?> = restImmersionLiftIdStore.asStateFlow()

    private fun setRestImmersionLiftId(liftId: String?) {
        restImmersionLiftIdStore.value = liftId
        if (liftId == null) {
            privateSavedState.remove<String?>(REST_IMMERSION_LIFT_KEY)
        } else {
            privateSavedState[REST_IMMERSION_LIFT_KEY] = liftId
        }
    }

    fun closeTempoCoachSheet() {
        tempoCoachSheetVisible.value = false
    }
    private val addASetDismissedExercises = MutableStateFlow<Set<String>>(emptySet())
    private val addASetAcceptedExercises = MutableStateFlow<Set<String>>(emptySet())
    /** Lifters chose Add another set after planned working sets were already in (ADR-004). */
    private val manualExtraAfterLastPlanned = MutableStateFlow<Set<String>>(emptySet())

    private val coachHistory = container.trainingInsights.observeShared(includeWeekPlan = false)
        .map { it.history }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val schedulePreferences = container.preferencesRepository.schedulePreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, SchedulePreferences())

    private val trainingBlock = container.preferencesRepository.trainingBlock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The unit the undo offer and the log receipt name. It rode on [microRec] until W2c. */
    private val weightUnit: StateFlow<WeightUnit> = container.preferencesRepository.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    /**
     * Packet G: cheap destructives form a short LIFO queue of undo offers, newest last, which
     * [FloorUndoOffers] holds and saves. Built after [weightUnit]: it reads the unit and a lift's
     * load type only when an offer is made, so the [session] declared below is never read early.
     */
    private val undo = FloorUndoOffers(
        saved = SavedStateFloorUndo(savedStateHandle),
        undoTimeout = undoTimeout,
        unit = { weightUnit.value },
        loadTypeOf = ::liftLoadType,
        onCheckpoint = ::publishPrivateEntry,
        onOfferDiscarded = ::onRemovedLiftOfferDiscarded,
    )

    /** Every live undo offer, oldest first. The banner shows the last one. */
    val undoEntries: StateFlow<List<UndoEntry>> = undo.entries

    /** How long the current top offer stays readable; extends under TalkBack. */
    val undoDwellMs: StateFlow<Long> = undo.dwellMs

    private val _deleteFeedback = MutableSharedFlow<DeleteFeedback>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deleteFeedback: SharedFlow<DeleteFeedback> = _deleteFeedback.asSharedFlow()

    private var pendingResumeDraft: WorkoutDraft? = null

    /**
     * A set saved and acknowledged whose row the session has not carried yet (W2e). Until it does,
     * no entry follows a new suggestion, whichever lift the set is on and warm-up or not. Cleared
     * when the row arrives, when that set is deleted, and when no lift is selected any more (a
     * removed lift), so a set that leaves before its row never holds a follow back.
     */
    private var savedSetAwaitingRow: String? = null
    /** Blocks a late Room emission from recreating a draft after finish/discard cleared it. */
    private var terminalExit = false

    private val restTimer = container.restTimerController

    /**
     * The hold countdown and the set clock, their tickers, cues and saved state (audit W2d-3a).
     * Built before `init` restores them. The gates, the rest a start stops and the lift switch a
     * running clock holds up stay here.
     */
    private val clocks = FloorWorkClocks(
        saved = SavedStateFloorTimer(savedStateHandle),
        scope = viewModelScope,
        elapsedNow = ::elapsedNow,
        selectedLift = { selectedExerciseId.value },
        holdSoundEnabled = { container.preferencesRepository.restTimerPreferences.first().soundEnabled },
        cancelPendingRest = ::cancelPendingRest,
        onCheckpoint = ::publishPrivateEntry,
    )
    /** The rest preferences as they are now: the hold's GET READY length is read at the tap (P2b). */
    private val restPrefsNow: StateFlow<RestTimerPreferences> = container.preferencesRepository.restTimerPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestTimerPreferences.DEFAULT)
    val holdTimer: StateFlow<HoldTimerUiState> = clocks.hold
    val setStopwatch: StateFlow<SetStopwatchUiState> = clocks.stopwatch
    private val primaryActivation = MutableStateFlow(0L)
    private var lastPrimaryTap: Long? = null
    private var pendingRestJob: Job? = null
    val floorTimerCue: SharedFlow<FloorTimerCue> = clocks.cues
    private val _pendingLiftSwitch = MutableStateFlow<PendingLiftSwitch?>(null)
    val pendingLiftSwitch: StateFlow<PendingLiftSwitch?> = _pendingLiftSwitch.asStateFlow()

    /** The read outcome remains distinct from a successful query with no row. */
    private val sessionReader = WorkoutSessionReader(container.workoutRepository, sessionId, viewModelScope)

    /** The rest commands and the history reads this screen shares with the rest page. */
    private val restCommands = RestCommands(container, viewModelScope, sessionId)
    private val hintLoader = ProgressionHintLoader(container, sessionId)

    /** The unit and the week's mark, as they change. The hint is read under them (W2e). */
    private val hintSettings: StateFlow<HintSettings?> =
        hintLoader.settings().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The session row, hot for this ViewModel's whole life.
     *
     * It used to be a cold flow collected twice — once by the init collector, once by the
     * uiState chain — so the same row was queried twice. Actions also read the session out of
     * `uiState.value`, which is a `WhileSubscribed(5_000)` projection: five seconds after the
     * screen stops being collected it stops updating, so anything acting on it (a notification
     * tap, a resumed screen before its first recomposition) was reading a stale snapshot of a
     * rendered state rather than the data itself.
     */
    private val session: StateFlow<WorkoutSession?> =
        sessionReader.observations.map { it.session }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val privateDrafts = FloorPrivateDrafts(
        persistence = durability, sessionId = sessionId, scope = viewModelScope, onRestored = ::applyPrivateRecovery,
    )
    internal val privateRecovery: StateFlow<WorkoutPrivateState> = privateDrafts.recovery
    private val workoutBoot = com.sinura.personaltrainer.timer.BootSession.count(application)
    private val numberEntries = MutableStateFlow<List<com.sinura.personaltrainer.workout.WorkoutNumberDraft>>(emptyList())
    internal val numberEntry = object : WorkoutNumberEntries {
        override val entries: StateFlow<List<com.sinura.personaltrainer.workout.WorkoutNumberDraft>> = numberEntries.asStateFlow()
        override val exerciseId: String? get() = selectedExerciseId.value
        // Live input keeps its existing per-operation guards; every implementation owns this contract.
        override fun allowsInput(): Boolean = true
        override fun put(value: com.sinura.personaltrainer.workout.WorkoutNumberDraft) {
            if (!canChangeEntry() || value.exerciseId != exerciseId) return
            numberEntries.value = numberEntries.value.filterNot { it.exerciseId == value.exerciseId && it.field == value.field }
                .map { if (value.open) it.copy(open = false) else it } + value
            publishPrivateEntry()
        }
        override fun clear(field: com.sinura.personaltrainer.workout.WorkoutNumberField) {
            if (!canChangeEntry()) return
            numberEntries.value = numberEntries.value.filterNot { it.exerciseId == exerciseId && it.field == field }
            publishPrivateEntry()
        }
    }
    private var clocksRestored = false

    init {
        if (sessionId.isBlank()) {
            error.fail(source = ERR_LOAD, message = "This workout is no longer available.")
        }
        // Survives process death; the in-memory cache does not. See WorkoutDraftRecovery.
        // Packet C: one draft per lift, restored as a map.
        val recovered = WorkoutDraftRecovery.resolveMap(
            sessionId = sessionId,
            inMemory = draftCache.all(sessionId),
            persisted = savedDraft.readAll(sessionId),
        )
        val selectedId = WorkoutDraftRecovery.resolveSelectedId(
            sessionId = sessionId,
            inMemorySelected = draftCache.selectedExerciseId(sessionId),
            persistedSelected = savedDraft.selectedExerciseId(),
            recovered = recovered,
        )
        if (recovered.isNotEmpty()) {
            draftCache.replaceAll(sessionId, recovered, selectedId)
        }
        selectedId?.let { selectedExerciseId.value = it }
        // A selected lift restores its own entry or nothing. It has none when it was picked and
        // the screen was left before its reads finished, and the cache's any-lift fallback then
        // hands back another lift's entry: that lift's numbers, marked as typed when they were
        // typed on it, would show on this one, and its notes are a copy that stopped when that
        // lift was last written (N1).
        // With no lift selected, what either store can still hand back belongs to no lift.
        val selectedDraft = if (selectedId != null) {
            recovered[selectedId]
        } else {
            WorkoutDraftRecovery.resolve(
                sessionId = sessionId,
                inMemory = draftCache.get(sessionId),
                persisted = savedDraft.read(sessionId),
            )
        }
        selectedDraft?.let { cached ->
            pendingResumeDraft = cached
            applyRecoveredDraft(cached)
            liftReadiness.value = LiftEntryReadiness.READY
        }
        // Notes are the session's, never a lift's copy: a lift's entry keeps the words from when
        // it was last written, and one left behind by a swap is restored as the selected lift
        // once the workout has no lifts (N1). The cache's are the freshest while the process
        // lives; saved state's single key is what survives its death, and is read when the
        // cache has none, or an unmarked empty copy. An unmarked empty cache is not trusted: it can be
        // the unfilled field Back stages while the row is still loading, or a real deletion, and
        // the two look alike. In saved state an empty note is one deleted, restored as such so
        // the row does not fill it again; a missing key means nothing was saved, and the row
        // fills the field (N2).
        val cachedNotes = draftCache.sessionNotes(sessionId)
        val restoredNotes = cachedNotes?.takeIf { it.isNotEmpty() || draftCache.hasPendingNotes(sessionId) }
            ?: savedDraft.sessionNotesIfSaved()
        if (restoredNotes != null) sessionNotes.restore(restoredNotes)
        // After a process death the cache is rebuilt above from saved state's entries, which
        // carry no session notes. Staged here, the words saved state brought back outlive this
        // screen even if it is left before it saves anything, as Back while loading leaves it.
        if (!restoredNotes.isNullOrEmpty() && draftCache.sessionNotes(sessionId) == null) {
            draftCache.putSessionNotes(sessionId, restoredNotes)
        }
        savedDraft.editingSetId()?.let { editingSetId.value = it }
        editingOriginal?.let { original ->
            editingSetId.value = original.setId
            savedEdit.write(original)
            draftCache.putEditingOriginal(sessionId, original)
        }
        saves.keepRestored()?.let { command -> restoreSaveDraft(command) }
        privateDrafts.open()
        viewModelScope.launch {
            // The flow is guarded at the repository, but the body below is not — a failure
            // here would otherwise kill the collector and freeze the screen silently.
            runCatchingCancellable {
                combine(sessionReader.observations, privateDrafts.recovery) { read, recovery -> read to recovery }.collect { (read, recovery) ->
                    if (!recovery.canEnter) return@collect
                    if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return@collect }
                    if (read.loadState == SessionLoadState.MISSING) sessionNotes.sessionMissing()
                    val current = read.session ?: return@collect
                    if (!clocksRestored && !current.isFinished) {
                        clocksRestored = true
                        clocks.restore(selectedExerciseId.value)
                        if (clocks.timing && container.restTimerStore.snapshot.value.sessionId == sessionId) restTimer.stop()
                    }
                    // Read before a selection saves the notes. applySelection writes the field to
                    // saved state, and on a workout finished elsewhere the save below returns early,
                    // so an unfilled field saved there would come back after a process death as a
                    // deleted note and be written over the finished one (N2).
                    sessionNotes.sessionRead(current.notes)
                    savedSetAwaitingRow?.let { id -> if (current.sets.any { it.id == id }) savedSetAwaitingRow = null }
                    val resolved = current.resolveSelectedExerciseId(selectedExerciseId.value)
                    if (!saves.pending && resolved != selectedExerciseId.value) {
                        selectedExerciseId.value?.takeIf { id ->
                            read.loadState == SessionLoadState.FOUND && current.exercises.none { it.exercise.id == id }
                        }?.let(::retireRemovedEntry)
                        if (resolved == null) {
                            clearLiftSelection()
                        } else {
                            applySelection(resolved)
                        }
                    }
                    persistDraft()
                }
            }.onFailure { AppLog.e(TAG, "Observing the active session failed", it) }
        }
        // Prefill used to hang off the end of the uiState chain, as `mapLatest { prefill(it) }`.
        // That made it a side effect of rendering, and — worse — it wrote to `restTotal`,
        // `hint` and `draft`, three of that same chain's own combine inputs. Its own write to
        // `restTotal` re-triggered the chain, mapLatest cancelled the suspended progression
        // query, and because the "already prefilled" marker had been set before suspending,
        // the retry was skipped: switching to a lift with a different rest time could silently
        // leave the weight at 0 instead of the suggestion.
        //
        // It is now driven by the selection alone, which is what it actually depends on.
        //
        // Every selection, with no filter ahead: the selection is a StateFlow, so it is already
        // distinct. `filterNotNull().distinctUntilChanged()` read the only lift removed and brought
        // back, by Undo or from the picker (it, then none, then it), as the same lift twice and
        // dropped the second, which left it loading for good with Log off (N2). None cancels the
        // lift's read still running, which isCurrentPrefill would have dropped anyway.
        viewModelScope.launch {
            combine(selectedExerciseId, privateDrafts.recovery.map { it.canEnter }.distinctUntilChanged()) { id, ready -> id to ready }.collectLatest { (exerciseId, ready) ->
                if (!ready || exerciseId == null) return@collectLatest
                runCatchingCancellable { prefill(exerciseId) }
                    .onFailure { AppLog.w(TAG, "Prefilling the next set failed", it) }
            }
        }
        // W2e: a unit or a week's mark that changes while a lift is open reaches its hint, and an
        // entry nobody touched follows it. The stamp names the load the hint came from and what it
        // was read under, so a change that lands while that load is still reading is caught when
        // it ends.
        viewModelScope.launch {
            combine(hintStamp.filterNotNull(), hintSettings.filterNotNull()) { stamp, now -> stamp to now }
                .collectLatest { (stamp, now) ->
                    // Checked here, not by a filter ahead: a change undone before its read returns
                    // cancels that read instead of letting it land and be read back.
                    if (stamp.settings == now) return@collectLatest
                    runCatchingCancellable { refreshHint(stamp, now) }
                        .onFailure { AppLog.w(TAG, "Reading the hint again failed", it) }
                }
        }
        // The typing-pause writer (FloorSessionNotes.writeOnTypingPause), launched after the
        // notes are restored above, so the first words it sees are the restored ones.
        viewModelScope.launch {
            privateDrafts.recovery.map { it.canEnter }.distinctUntilChanged().collectLatest { ready ->
                if (ready) sessionNotes.writeOnTypingPause()
            }
        }
        viewModelScope.launch {
            restCommands.presetEchoes.collect { last ->
                // A pick on the dock comes back here as an echo, and it can land after the
                // owner has moved on. It is already the plan, marked with the lift it was
                // picked on; marked again with whichever lift is selected now, it would stop
                // that lift's own seed. Only a length the dock does not hold yet — one picked
                // on the rest page — is news.
                restTotal.update { plan ->
                    if (plan.seconds == last) {
                        plan
                    } else {
                        PlannedRest(
                            seconds = last,
                            sourceTag = RestPlan(last, RestPlanSource.LAST_PRESET).sourceTag(),
                            chosenFor = selectedExerciseId.value,
                        )
                    }
                }
            }
        }
    }

    /** The dock's rest card. The shared builder is cold; this screen's sharing is its own. */
    val restTimerState: StateFlow<RestTimerUiState> =
        restCommands.restState(restTotal) { it.seconds to it.sourceTag }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RestTimerUiState(),
        )

    val tempoCoachSheetOpen: StateFlow<Boolean> = combine(
        tempoCoachSheetVisible,
        restTimerState,
    ) { visible, rest -> visible && rest.running }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * In-set next load. Recomputed on log, RPE, warmup, lift switch, delete/undo, and edit.
     * Stepper ticks do not change it unless draft RPE is set (preview): the coach is asked again
     * only when what it reads changes ([CoachKey]), so a step, a note or a set of another lift
     * leaves this call as it was (W2c, audit C-2), and when the unit or the week's mark changes
     * (W2e). Plain `map`, no dispatch: [applyMicroRec] and
     * the rest after a log read `.value` at once.
     * Eager: [applyMicroRec] reads this value, not a rendered snapshot.
     */
    private val coachKeyFlow = combine(
        combine(session, selectedExerciseId, draft, hint, wantAnotherSet) {
                current, selected, currentDraft, currentHint, extra ->
            MicroRecCore(current, selected, currentDraft, currentHint, extra)
        }.combine(lastPerformance) { core, last ->
            core.copy(lastPerformance = last)
        },
        combine(
            editingSetId,
            hintSettings.map { it?.lighterWeek == true },
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.coachPreferences,
            effortSource,
        ) { editing, lighter, unit, coach, effort ->
            MicroRecExtras(
                editingSetId = editing,
                lighterWeek = lighter,
                unit = unit,
                coachPrefs = coach,
                draftRpeIntent = effort == EffortSource.USER || effort == EffortSource.COACH,
            )
        },
    ) { core, extras ->
        NextSetInputs(
            session = core.session,
            selectedExerciseId = core.selected,
            draft = core.draft,
            hint = core.hint,
            editingSetId = extras.editingSetId,
            wantAnotherSet = core.wantAnother,
            historySets = core.lastPerformance?.sets.orEmpty(),
            lighterWeek = extras.lighterWeek,
            unit = extras.unit,
            coachPrefs = extras.coachPrefs,
            draftRpeIntent = extras.draftRpeIntent,
        ).coachKey()
    }.distinctUntilChanged()

    val microRec: StateFlow<SetMicroRec?> = coachKeyFlow
        .map { key -> key.rec(nowMs = time.nowMillis(), todayEpochDay = todayEpochDay()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val tempoCoachTip: StateFlow<TempoCoachTip?> = combine(
        combine(
            combine(coachKeyFlow, session, selectedExerciseId, coachHistory) { key, currentSession, selected, history ->
                TempoCoachFramePart1(key, currentSession, selected, history)
            },
            combine(schedulePreferences, trainingBlock) { schedule, block -> schedule to block },
        ) { part, scheduleAndBlock ->
            TempoCoachFrame(
                key = part.key,
                session = part.session,
                selectedExerciseId = part.selectedExerciseId,
                history = part.history,
                schedule = scheduleAndBlock.first,
                block = scheduleAndBlock.second,
            )
        },
        combine(
            combine(hintSettings, tempoTipDismissed, addASetDismissedExercises, addASetAcceptedExercises) {
                    hints, dismissed, quiet, accepted ->
                TempoCoachDismissState(
                    hints = hints,
                    tempoDismissed = dismissed,
                    addASetDismissed = quiet,
                    addASetAccepted = accepted,
                )
            },
            manualExtraAfterLastPlanned,
        ) { dismiss, manualExtra ->
            dismiss to manualExtra
        },
    ) { frame, dismissAndManual ->
        val dismiss = dismissAndManual.first
        val manualExtra = dismissAndManual.second
        val rec = frame.key.rec(nowMs = time.nowMillis(), todayEpochDay = todayEpochDay())
        val liftId = frame.session?.resolveSelectedExerciseId(frame.selectedExerciseId)
        TempoCoachSnapshot(
            microRec = rec,
            coachPrefs = frame.key.prefs,
            unit = dismiss.hints?.unit ?: WeightUnit.KG,
            session = frame.session,
            selectedExerciseId = frame.selectedExerciseId,
            history = frame.history,
            exerciseCatalog = emptyMap(),
            trainingBlock = frame.block,
            schedule = frame.schedule,
            lighterWeek = dismiss.hints?.lighterWeek == true,
            time = time,
            nowMs = time.nowMillis(),
            todayEpochDay = todayEpochDay(),
            addASetDismissed = dismiss.addASetDismissed,
            addASetAccepted = dismiss.addASetAccepted,
            tempoDismissed = dismiss.tempoDismissed,
            manualExtraAfterLastPlanned = liftId != null && liftId in manualExtra,
            inputs = frame.key.inputs,
        ).tip()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    /**
     * The coach's first named lift, as an (exercise, reason) pair, or null when it has nothing
     * specific to say. Read from the shared insights pipeline rather than recomputed here.
     *
     * Declared above [uiState] and not below it because [uiState]'s initializer passes this
     * flow to `combine` as a direct argument, and a property initializer cannot read a property
     * that has not run yet: `variable 'suggestedLift' must be initialized`. Being inside a
     * lambda would have made the forward reference legal; being an argument to one does not.
     *
     * The insights recompute on every logged set, nearly always with the same card on top, so
     * the lift is looked up only when the card's lift or reason changes (W2c). The lift is then
     * watched, not read once: one edited while the Log is open, in the library or by sync from
     * another device, is suggested by its new name and added with its new load's sets, reps and
     * rest; a lift read once kept its old name and load until the card moved (W2c review). The
     * watch asks Room again only when the exercise or muscle-credit tables are written, never
     * on a logged set.
     */
    private val suggestedLift: Flow<Pair<Exercise, String>?> =
        container.trainingInsights.observeShared(includeWeekPlan = false)
            .map { insights ->
                insights.recommendations.firstOrNull { it.actionExerciseId != null }
                    ?.let { card -> card.actionExerciseId!! to card.title }
            }
            .distinctUntilChanged()
            .flatMapLatest { card ->
                val (exerciseId, title) = card ?: return@flatMapLatest flowOf(null)
                container.exerciseRepository.observeById(exerciseId)
                    .map { exercise -> exercise?.let { it to title } }
                    // A first read that fails ends the watch with no value, and uiState's
                    // combine would wait for one forever; the one-shot read gave null. A read
                    // that fails later ends the watch on the lift last read (observeHealth), so
                    // that lift stays suggested but no longer follows edits; the one-shot read's
                    // failure hid the suggestion for the rest of the Log instead. The next card
                    // starts a new watch either way.
                    .onEmpty { emit(null) }
            }
            .distinctUntilChanged()
            .catch { thrown ->
                AppLog.w(TAG, "Reading the suggested lift failed", thrown)
                emit(null)
            }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        // The read, not [session]: one Room emission reached this chain twice, once through
        // each, and the first pass could pair the new read with the old session (W2c).
        sessionReader.observations,
        selectedExerciseId,
        draft,
        hint,
    ) { read, selected, currentDraft, currentHint ->
        WorkoutCore(read, selected, currentDraft, currentHint)
    }.combine(lastPerformance) { core, last ->
        core.copy(lastPerformance = last)
    }.combine(
        combine(
            combine(restTotal, searchQuery, showPicker) { total, query, picker ->
                Triple(total, query, picker)
            },
            combine(
                combine(sessionNotes.text, sessionNotes.saveState) { notes, save -> notes to save },
                error.messages, finished, editingSetId,
            ) { notes, err, done, editing ->
                EditorMeta(notes.first, notes.second, err, done, editing)
            },
        ) { first, second ->
            WorkoutExtras(
                restTotal = first.first.seconds,
                query = first.second,
                showPicker = first.third,
                notes = second.notes,
                notesSave = second.notesSave,
                error = second.error,
                finished = second.finished,
                editingSetId = second.editingSetId,
            )
        },
    ) { core, extras ->
        ActiveWorkoutUiState(
            loadState = core.read.loadState,
            session = core.read.session,
            selectedExerciseId = core.read.session?.resolveSelectedExerciseId(core.selected) ?: core.selected,
            draft = core.draft,
            hint = core.hint,
            lastPerformance = core.lastPerformance,
            searchQuery = extras.query,
            searchResults = emptyList(),
            showExercisePicker = extras.showPicker,
            notes = extras.notes,
            notesSave = extras.notesSave,
            error = extras.error,
            finished = extras.finished,
            editingSetId = extras.editingSetId,
        )
    }.combine(
        combine(
            searchQuery.flatMapLatest { container.exerciseRepository.search(it) },
            container.exerciseRepository.observeLastLogged(),
        ) { results, lastLogged -> results to lastLogged },
    ) { state, (results, lastLogged) ->
        // With an empty query the picker leads with what you have actually been training.
        // A search has already expressed an intent, and re-ranking it by recency would fight
        // what was typed. A closed picker gets no list and no sort: the recency order moves
        // with every logged set. The catalogue itself stays read, so the picker opens on its
        // list and never on an empty "Search the library" (W2c).
        val ordered = when {
            !state.showExercisePicker -> emptyList()
            state.searchQuery.isBlank() -> ExerciseOrdering.pickerOrder(results, lastLogged)
            else -> results
        }
        state.copy(searchResults = ordered)
    }.combine(
        combine(swapTargetItemId, container.exerciseRepository.observeAll()) { target, catalog ->
            target to catalog
        },
    ) { state, (swapTarget, catalog) ->
        val item = state.session?.exercises?.firstOrNull { it.id == swapTarget }
        state.copy(
            swapping = swapTarget != null,
            swapSiblings = item?.let {
                LibraryGrouping.siblings(
                    exercise = it.exercise,
                    catalog = catalog,
                    // Offering a swap to something already in the session is offering to create
                    // a duplicate, which the repository would refuse anyway.
                    exclude = state.session?.exercises.orEmpty().map { row -> row.exercise.id }.toSet(),
                )
            }.orEmpty(),
        )
    }.combine(suggestedLift) { state, suggested ->
        // Never suggest a lift the session already has: the row would offer to add something
        // that is one chip away on screen.
        val alreadyPresent = state.session?.exercises?.any { it.exercise.id == suggested?.first?.id } == true
        state.copy(
            suggestion = suggested?.first?.takeUnless { alreadyPresent },
            suggestionReason = suggested?.second?.takeUnless { alreadyPresent },
        )
    }.combine(combine(mutating, draftCache.finishing) { busy, finishing -> busy || sessionId in finishing }) { state, busy ->
        state.copy(mutating = busy)
    }.combine(saves.operation) { state, operation ->
        state.copy(
            save = operation, error = operation.message ?: state.error,
            selectedExerciseId = operation.command?.exerciseId ?: state.selectedExerciseId,
        )
    }.combine(saves.inFlight) { state, busy ->
        state.copy(logging = busy)
    }.combine(liftReadiness) { state, readiness ->
        state.copy(liftReadiness = readiness)
    }.combine(suggestionUnavailable) { state, missing ->
        state.copy(suggestionUnavailable = missing)
    }.combine(draftDirty) { state, dirty ->
        state.copy(draftDirty = dirty)
    }.combine(pickerWrites.state) { state, progress ->
        state.copy(pickerProgress = progress)
    }.combine(privateDrafts.recovery) { state, recovery ->
        state.copy(
            loadState = if (recovery.phase == WorkoutPrivatePhase.OPENING) SessionLoadState.LOADING else state.loadState,
            mutating = state.mutating || !recovery.canEnter,
            error = recovery.message.takeIf { recovery.phase != WorkoutPrivatePhase.READY } ?: state.error,
        )
    }.combine(effortSource) { state, source ->
        val lift = state.session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
        val hold = lift?.exercise?.let { HoldWork.isHold(it) } == true
        val effortApplies = !state.draft.isWarmup && !hold
        state.copy(
            effortSource = source,
            effortValue = state.draft.rpe.takeIf { effortApplies },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    /**
     * Loads everything shown about a lift, and — unless the user already has work in progress
     * on it — fills the draft with the suggestion.
     *
     * The two halves are separate on purpose. The reference half (last session, progression
     * hint, planned rest) is always safe to load and is exactly what someone resuming a
     * workout or editing a logged set wants to see. The draft half overwrites what they typed,
     * so it is skipped when they already edited, recovered a draft, or are revising a row.
     * Stale answers for a previous lift or an older generation are discarded.
     *
     * It reads the lift's plan from a session row that holds the lift. An Undo, an add or a swap
     * selects the lift before its row arrives, and the row on hand then has no plan for it: the
     * entry was filled with 0 kg and 5 reps (N2). A row found without the lift makes the session
     * collector select another, which cancels this, unless a save is pending; its settling
     * re-selects ([reconcileSelectionAfterSave]).
     */
    private suspend fun prefill(exerciseId: String) {
        val generation = prefillGeneration
        val resume = pendingResumeDraft
        val resumingThisLift = if (resume == null) {
            false
        } else {
            pendingResumeDraft = null
            resume.exerciseId == null || resume.exerciseId == exerciseId
        }
        val keepDraft = resumingThisLift || editingSetId.value != null || saves.pending
        if (!keepDraft) {
            liftReadiness.value = LiftEntryReadiness.RESOLVING
            suggestionUnavailable.value = false
        } else if (liftReadiness.value == LiftEntryReadiness.NONE) {
            liftReadiness.value = LiftEntryReadiness.READY
        }
        lastPerformance.value = null
        priorHistory.value = emptyList()
        hint.value = null
        hintStamp.value = null
        val current = sessionReader.observations.first {
            it.loadState == SessionLoadState.MISSING ||
                (it.loadState == SessionLoadState.FOUND && it.session?.hasLift(exerciseId) == true)
        }.session ?: return
        if (!isCurrentPrefill(exerciseId, generation)) return
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val hold = planned?.exercise?.let { HoldWork.isHold(it) } == true
        val targetReps = planned?.targetReps ?: 5
        try {
            val restPrefs = container.preferencesRepository.restTimerPreferences.first()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val settings = hintSettings.filterNotNull().first()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val progression = hintLoader.progression(exerciseId, planned, settings)
            if (!isCurrentPrefill(exerciseId, generation)) return
            hint.value = progression
            // A lift whose entry was recovered (Back and the live bar, a lift switched back to, a
            // process death): an entry nobody touched takes the suggestion just read (W2e, owner
            // decision of 25 September 2026).
            if (keepDraft) followSuggestion(exerciseId)
            lastPerformance.value = hintLoader.lastPerformance(exerciseId)
            if (!isCurrentPrefill(exerciseId, generation)) return
            priorHistory.value = container.workoutRepository
                .historyBefore(sessionId, listOf(exerciseId))[exerciseId].orEmpty()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val microRec = workoutMicroRec(
                session = current,
                selectedExerciseId = exerciseId,
                draft = draft.value,
                hint = progression,
                editingSetId = editingSetId.value,
                lighterWeek = settings.lighterWeek,
                unit = settings.unit,
                nowMs = time.nowMillis(),
                todayEpochDay = todayEpochDay(),
                historySets = lastPerformance.value?.sets.orEmpty(),
                coachPrefs = container.preferencesRepository.coachPreferences.first(),
            )
            val seededPlan = RestTimer.resolvePlan(
                exerciseRestSeconds = planned?.restSeconds,
                preferences = restPrefs,
                prescribedSeconds = microRec?.restSeconds,
                tempoRepsForLabel = microRec?.nextReps,
            )
            if (!isCurrentPrefill(exerciseId, generation)) return
            // The seed fills in this lift's plan; it never replaces a length picked for this
            // lift while the reads above were running. A pick made on another lift does not
            // count, so switching lifts still brings the new lift's own rest.
            restTotal.update { plan ->
                if (plan.chosenFor == exerciseId) {
                    plan
                } else {
                    PlannedRest(
                        seconds = seededPlan.seconds,
                        sourceTag = seededPlan.sourceTag(),
                        chosenFor = null,
                    )
                }
            }
            // The reads above resume off the main thread. Checking "untouched" and then writing
            // the seed on that same continuation let a typed weight land in the gap and come
            // back as 0 kg, while the reps and effort typed beside it were kept.
            val pausingForTypedWeight = !(keepDraft || draftDirty.value || saves.pending)
            if (pausingForTypedWeight) beforeUntouchedSeed?.invoke()
            val nudge = synchronized(entryWrites) {
                if (!isCurrentPrefill(exerciseId, generation)) return@synchronized false
                if (keepDraft || draftDirty.value || saves.pending || editingSetId.value != null) {
                    liftReadiness.value = LiftEntryReadiness.READY
                    suggestionUnavailable.value = false
                    val wrote = writeDraftLocked()
                    hintStamp.value = HintStamp(exerciseId = exerciseId, generation = generation, settings = settings)
                    wrote
                } else {
                    val lastWeight = progression?.suggestedWeightKg
                        ?: planned?.targetWeightKg
                        ?: 0.0
                    val seededEffort = EffortPrefillPolicy.effortForAutomaticSeed(
                        isWarmup = false,
                        isHold = hold,
                        exerciseId = exerciseId,
                        sessionSets = current.sets,
                        priorWorkingSets = priorHistory.value,
                    )
                    draft.value = ActiveExerciseDraft(
                        weightKg = lastWeight,
                        reps = if (hold) 0 else targetReps.coerceAtLeast(1),
                        rpe = seededEffort,
                        isWarmup = false,
                        durationSeconds = if (hold) HoldWork.countdownSeconds(planned?.targetSeconds) else null,
                    )
                    effortSource.value = if (seededEffort != null) EffortSource.PREFILL_LAST else EffortSource.NONE
                    liftReadiness.value = LiftEntryReadiness.READY
                    suggestionUnavailable.value = false
                    val wrote = writeDraftLocked()
                    // Stamped at the end of the load, not at the hint: a re-read that ran while this load
                    // still read would follow into an entry this load then overwrites with the older hint.
                    hintStamp.value = HintStamp(exerciseId = exerciseId, generation = generation, settings = settings)
                    wrote
                }
            }
            if (nudge) privateDrafts.nudge()
            if (pausingForTypedWeight) afterUntouchedSeed?.invoke()
            return
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Prefilling the next set failed", thrown)
            if (!isCurrentPrefill(exerciseId, generation)) return
            val livePlanned = session.value?.exercises
                ?.firstOrNull { it.exercise.id == exerciseId }
                ?: planned
            val nudge = synchronized(entryWrites) {
                if (!isCurrentPrefill(exerciseId, generation)) return@synchronized false
                if (!saves.pending && !draftDirty.value && editingSetId.value == null &&
                    (!keepDraft || draft.value.weightKg <= 0.0)
                ) {
                    val seededEffort = EffortPrefillPolicy.effortForAutomaticSeed(
                        isWarmup = false,
                        isHold = hold,
                        exerciseId = exerciseId,
                        sessionSets = session.value?.sets.orEmpty(),
                        priorWorkingSets = priorHistory.value,
                    )
                    draft.value = ActiveExerciseDraft(
                        weightKg = livePlanned?.targetWeightKg ?: 0.0,
                        reps = if (hold) 0 else targetReps.coerceAtLeast(1),
                        rpe = seededEffort,
                        isWarmup = false,
                        durationSeconds = if (hold) {
                            HoldWork.countdownSeconds(livePlanned?.targetSeconds)
                        } else {
                            null
                        },
                    )
                    effortSource.value = if (seededEffort != null) EffortSource.PREFILL_LAST else EffortSource.NONE
                    writeDraftLocked()
                } else {
                    false
                }
            }
            if (nudge) privateDrafts.nudge()
            liftReadiness.value = LiftEntryReadiness.DEGRADED
            suggestionUnavailable.value = true
        }
    }

    private fun isCurrentPrefill(exerciseId: String, generation: Int): Boolean =
        selectedExerciseId.value == exerciseId && prefillGeneration == generation

    private fun isHoldLift(exerciseId: String): Boolean {
        val exercise = session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }?.exercise
            ?: return false
        return HoldWork.isHold(exercise)
    }

    private fun automaticEffortForLift(exerciseId: String, isWarmup: Boolean, hold: Boolean): Int? {
        val current = session.value ?: sessionReader.observations.value.session ?: return null
        return EffortPrefillPolicy.effortForAutomaticSeed(
            isWarmup = isWarmup,
            isHold = hold,
            exerciseId = exerciseId,
            sessionSets = current.sets,
            priorWorkingSets = priorHistory.value,
        )
    }

    /** Room may not have emitted the row yet when the save tail seeds the next draft. */
    private fun sessionSetsForEffortSeed(command: WorkoutSetSave): List<SetLog> {
        val live = session.value?.sets.orEmpty()
        if (command.editing || live.any { it.id == command.setId }) return live
        val values = command.values
        val lift = session.value?.exercises?.firstOrNull { it.exercise.id == command.exerciseId }
        return live + SetLog(
            id = command.setId,
            sessionId = sessionId,
            exerciseId = command.exerciseId,
            exerciseName = lift?.exercise?.name.orEmpty(),
            setNumber = 0,
            weightKg = values.weightKg,
            reps = values.reps,
            rpe = values.rpe,
            isWarmup = values.isWarmup,
            completedAt = time.nowMillis(),
            durationSeconds = values.durationSeconds,
        )
    }

    /**
     * Reads the open lift's hint again under [now], the one read a change of the unit or the
     * week's mark makes (W2e); last session, history and the planned rest stay. Dropped when the
     * lift, or its load, is no longer the one [stamp] names. A failure keeps the old hint.
     */
    private suspend fun refreshHint(stamp: HintStamp, now: HintSettings) {
        if (!isCurrentPrefill(stamp.exerciseId, stamp.generation)) return
        val planned = session.value?.exercises?.firstOrNull { it.exercise.id == stamp.exerciseId }
        val progression = hintLoader.progression(stamp.exerciseId, planned, now)
        if (!isCurrentPrefill(stamp.exerciseId, stamp.generation)) return
        hint.value = progression
        hintStamp.value = stamp.copy(settings = now)
        followSuggestion(stamp.exerciseId)
    }

    /**
     * The entry takes the hint's weight when nobody has touched it ([entryFollowsSuggestion]). A
     * follow refused at that moment (a set clock running, a save in flight) is not tried again
     * until the next read of the hint: the next change, or the lift opened again.
     */
    private fun followSuggestion(exerciseId: String) {
        val nudge = synchronized(entryWrites) {
            val suggested = hint.value?.suggestedWeightKg ?: return@synchronized false
            if (!entryFollowsSuggestion(exerciseId)) return@synchronized false
            if (draft.value.weightKg == suggested) return@synchronized false
            draft.value = draft.value.copy(weightKg = suggested)
            writeDraftLocked()
        }
        if (nudge) privateDrafts.nudge()
    }

    /**
     * Whether [exerciseId]'s entry is one nobody touched, which follows a new suggestion (owner
     * decision of 25 September 2026: "untouched pre-filled numbers follow the new suggestion;
     * typed numbers stay"). Not typed or chosen ([draftDirty]: a typed or stepped number, reps,
     * Warm-up, a ramp chip, Last time and Use; not an effort, since P2a made one part of every
     * working set); no working set of the lift logged in this
     * session (after one the entry is the set just done, and dirty is not cleared by a log); not
     * a warm-up (a logged warm-up opened for correction and cancelled leaves its values, untyped);
     * no set open for correction, and none saved as a correction (it counts as typed); no save in
     * flight or held for Retry, which the entry must still show, and no set just saved whose row the
     * session does not carry yet; no set clock running, or stopped with its time not yet logged
     * (the numbers just lifted: Log, Start rest or a correction clears it); and not a hold,
     * whose hint counts reps the coach does not coach (audit DM-1). Such an entry holds only what a
     * load filled in.
     */
    private fun entryFollowsSuggestion(exerciseId: String): Boolean {
        if (!canChangeEntry() || clocks.timing || clocks.stopwatch.value.used) return false
        if (selectedExerciseId.value != exerciseId) return false
        if (draftDirty.value || draft.value.isWarmup || editingSetId.value != null) return false
        val current = session.value ?: return false
        if (savedSetAwaitingRow?.let { id -> current.sets.none { it.id == id } } == true) return false
        val lift = current.exercises.firstOrNull { it.exercise.id == exerciseId } ?: return false
        if (HoldWork.isHold(lift.exercise)) return false
        return current.setsFor(exerciseId).none { !it.isWarmup }
    }

    private fun applySelection(exerciseId: String) {
        if (selectedExerciseId.value == exerciseId) return
        numberEntries.value = numberEntries.value.map { it.copy(open = false) }
        persistDraft()
        editingSetId.value = null
        clearEditingOriginal()
        clocks.saveStopwatchFor(selectedExerciseId.value)
        wantAnotherSet.value = false
        _pendingLiftSwitch.value = null
        clocks.stopHold()
        suggestionUnavailable.value = false
        val stored = liftDraft(exerciseId)
        if (stored != null) {
            pendingResumeDraft = stored
            applyRecoveredDraft(stored)
            liftReadiness.value = LiftEntryReadiness.READY
        } else {
            pendingResumeDraft = null
            draftDirty.value = false
            draft.value = ActiveExerciseDraft()
            effortSource.value = EffortSource.NONE
            liftReadiness.value = LiftEntryReadiness.RESOLVING
        }
        prefillGeneration++
        selectedExerciseId.value = exerciseId
        draftCache.select(sessionId, exerciseId)
        savedDraft.writeSelection(
            sessionId = sessionId,
            exerciseId = exerciseId,
            notes = sessionNotes.text.value,
            editingSetId = editingSetId.value,
        )
        clocks.restoreStopwatchFor(exerciseId, resumeRunning = false)
    }

    private fun pendingRemovedLiftExerciseIds(): Set<String> =
        undo.entries.value.mapNotNull { entry ->
            (entry.token as? FloorUndo.RemovedLift)?.removed?.item?.exerciseId
        }.toSet()

    /**
     * Drops a removed lift's saved set clock when no undo offer names it and it is not live.
     * Call only after the queue has been updated (pop / eviction).
     */
    private fun forgetRemovedLiftStopwatchIfOfferFullyEnded(exerciseId: String) {
        if (session.value?.exercises?.any { it.exercise.id == exerciseId } == true) return
        if (exerciseId in pendingRemovedLiftExerciseIds()) return
        clocks.forgetStopwatch(exerciseId)
    }

    private fun onRemovedLiftOfferDiscarded(token: FloorUndo) {
        if (token is FloorUndo.RemovedLift) {
            forgetRemovedLiftStopwatchIfOfferFullyEnded(token.removed.item.exerciseId)
        }
    }

    private fun liftDraft(exerciseId: String): WorkoutDraft? =
        WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = draftCache.getLift(sessionId, exerciseId),
            persisted = savedDraft.readLift(sessionId, exerciseId),
        )

    private fun applyRecoveredDraft(cached: WorkoutDraft) {
        // The rest page reads the cached entry through the same conversion (W2b-4 review).
        draft.value = cached.entryDraft()
        draftDirty.value = cached.dirty
        wantAnotherSet.value = cached.extraSetRequested
        effortSource.value = cached.effortSource
    }

    private fun clearLiftSelection() {
        clocks.saveStopwatchFor(selectedExerciseId.value)
        clocks.stopHold()
        clocks.bump()
        clocks.resetStopwatchView()
        draftDirty.value = false
        suggestionUnavailable.value = false
        savedSetAwaitingRow = null
        liftReadiness.value = LiftEntryReadiness.NONE
        effortSource.value = EffortSource.NONE
        prefillGeneration++
        selectedExerciseId.value = null
    }

    private fun markDraftDirty() {
        if (draftDirty.value) return
        draftDirty.value = true
    }

    fun selectExercise(exerciseId: String) {
        if (!canChangeEntry()) return
        selectExerciseInternal(exerciseId)
    }

    private fun selectExerciseInternal(exerciseId: String) {
        if (selectedExerciseId.value == exerciseId) return
        if (clocks.timing) {
            _pendingLiftSwitch.value = PendingLiftSwitch(exerciseId)
            return
        }
        applySelection(exerciseId)
        persistDraft()
    }

    fun confirmStopTimingAndSwitch() {
        if (!canChangeEntry()) return
        val pending = _pendingLiftSwitch.value ?: return
        _pendingLiftSwitch.value = null
        stopSetStopwatch()
        applySelection(pending.exerciseId)
        persistDraft()
    }

    fun cancelPendingLiftSwitch() {
        _pendingLiftSwitch.value = null
    }

    fun requestExtraSet(fromUser: Boolean = true) {
        if (!canChangeEntry()) return
        if (fromUser) {
            session.value?.let { current ->
                val liftId = current.resolveSelectedExerciseId(selectedExerciseId.value) ?: return@let
                val lift = current.exercises.firstOrNull { it.exercise.id == liftId } ?: return@let
                val workingLogged = current.setsFor(liftId).count { !it.isWarmup }
                if (lift.targetSets > 0 && workingLogged >= lift.targetSets) {
                    manualExtraAfterLastPlanned.value = manualExtraAfterLastPlanned.value + liftId
                }
            }
        }
        wantAnotherSet.value = true
        persistDraft()
    }

    fun setWeight(weightKg: Double) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite()) return
        editEntry {
            draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
            markDraftDirty()
        }
    }

    /**
     * A rep count chosen outright, rather than nudged to.
     *
     * Typing used to be expressed as a delta — "you asked for 8 and the well showed 5, so
     * add 3" — computed from what the well was showing when the keypad opened. The two can
     * differ by the time Confirm is pressed (a logged set landing, a recommendation taken),
     * and the delta then lands on a different number than it was measured from: type 8, get
     * 11. An absolute value cannot drift, and this is still the one write path, so nothing
     * bypasses the floor or the draft-persist.
     */
    fun setReps(reps: Int) {
        if (!canChangeEntry()) return
        editEntry {
            draft.value = draft.value.copy(reps = reps.coerceAtLeast(1))
            markDraftDirty()
        }
    }

    fun setHoldSeconds(seconds: Int) {
        if (!canChangeEntry()) return
        editEntry {
            draft.value = draft.value.copy(
                durationSeconds = HoldWork.countdownSeconds(seconds),
                reps = 0,
            )
            markDraftDirty()
        }
    }

    /**
     * Starts the in-set work clock for a static hold. Rest still starts
     * after the set is logged, unchanged. Cancels any live rest generation.
     */
    fun startHoldSet() {
        if (!canChangeEntry()) return
        val exerciseId = selectedExerciseId.value ?: return
        val selected = session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }
            ?: return
        if (!HoldWork.isHold(selected.exercise)) return
        if (clocks.hold.value.running) return
        val total = HoldWork.countdownSeconds(
            draft.value.durationSeconds ?: selected.targetSeconds,
        )
        restTimer.stop()
        clocks.startHold(exerciseId = exerciseId, totalSeconds = total, leadInSeconds = restPrefsNow.value.leadInSeconds)
    }

    /** Ends GET READY before the hold clock runs; no hold starts and nothing is logged (P2b). */
    fun cancelLeadIn() {
        if (!clocks.hold.value.gettingReady) return
        clocks.stopHold()
    }

    /**
     * Manual count-up for a strength set. Cancels a pending rest alarm
     * (Packet E). Holds keep [startHoldSet].
     */
    fun startSetStopwatch() {
        if (!canChangeEntry()) return
        val exerciseId = selectedExerciseId.value ?: return
        val selected = session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }
            ?: return
        if (HoldWork.isHold(selected.exercise)) return
        if (clocks.stopwatch.value.running) return
        restTimer.stop()
        clocks.startStopwatch(exerciseId = exerciseId)
    }

    fun stopSetStopwatch() {
        clocks.stopStopwatch()
    }

    private fun elapsedNow(): Long = time.elapsedRealtimeMillis()

    private fun cancelPendingRest() {
        pendingRestJob?.cancel()
        pendingRestJob = null
    }

    /**
     * Record how hard that set was. It does not touch the load or the reps.
     *
     * Choosing an RPE used to fill the wells from the recommendation it unlocks, which meant
     * a weight and a rep count the lifter had already typed were replaced by a number they
     * had not asked for — the app editing their entry in the act of being told about it. The
     * recommendation still appears the moment the RPE is set; it sits above the Log button
     * with its own **Use**, and only that tap moves it into the wells.
     */
    fun setRpe(rpe: Int?) {
        if (!canChangeEntry()) return
        if (draft.value.isWarmup) return
        editEntry {
            val current = draft.value
            draft.value = current.copy(rpe = rpe)
            effortSource.value = if (rpe == null) EffortSource.NONE else EffortSource.USER
        }
        // An effort does not make the entry "typed" (P2a, 29 September 2026). Every working
        // set now carries one, so counting it would stop every entry after a logged set from
        // following a unit switch or a lighter week (W2e), and a follow moves only the weight,
        // which an effort says nothing about. The effort itself is cleared by the log.
    }

    fun setWarmup(isWarmup: Boolean) {
        if (!canChangeEntry()) return
        editEntry {
            val current = draft.value
            draft.value = current.copy(
                isWarmup = isWarmup,
                rpe = if (isWarmup) null else current.rpe,
            )
            if (isWarmup) {
                effortSource.value = EffortSource.NONE
            } else {
                val liftId = selectedExerciseId.value
                if (liftId != null && !isHoldLift(liftId)) {
                    val seeded = automaticEffortForLift(
                        exerciseId = liftId,
                        isWarmup = false,
                        hold = false,
                    )
                    if (seeded != null) {
                        draft.value = draft.value.copy(rpe = seeded)
                        effortSource.value = EffortSource.PREFILL_LAST
                    }
                }
            }
            markDraftDirty()
        }
    }

    /**
     * Packet D: one ramp chip writes the lighter weight and Warm-up.
     * It does not log. RPE is cleared so a working-set effort cannot
     * sit on a warm-up draft.
     */
    fun applyWarmupRamp(weightKg: Double) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite() || weightKg <= 0.0) return
        editEntry {
            draft.value = draft.value.copy(
                weightKg = weightKg,
                isWarmup = true,
                rpe = null,
            )
            effortSource.value = EffortSource.NONE
            markDraftDirty()
        }
    }

    fun setNotes(value: String) {
        if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return }
        if (!privateDrafts.recovery.value.canEnter || terminalExit || _exitRequested.value != null || mutating.value || finished.value ||
            sessionReader.observations.value.session?.isFinished == true
        ) return
        val nudge = synchronized(entryWrites) {
            var wrote = false
            draftCache.editLiveNotes(sessionId) {
                sessionNotes.edit(value)
                wrote = writeDraftLocked()
            }
            wrote
        }
        if (nudge) privateDrafts.nudge()
        // The database write is not launched here. See FloorSessionNotes.writeOnTypingPause, launched in init.
    }

    fun canDismissPicker(): Boolean = !pickerWrites.busy

    fun setPickerVisible(visible: Boolean) {
        if (visible && !canChangeEntry()) return
        if (!visible && pickerWrites.busy) return
        if (visible && swapDrafts != null) { openWorkoutPicker(null); return }
        if (!visible) swapTargetItemId.value = null
        showPicker.value = visible
        if (!visible && pickerRecovery == null) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun retryPicker() = pickerWrites.retry()
    fun dismissPickerError() { pickerWrites.dismissError(); error.clearFrom(ERR_ADD_LIFT) }

    fun addExercise(exercise: Exercise) = pickExercise(exercise, swapTargetItemId.value)

    private fun pickExercise(exercise: Exercise, swapTarget: String?, created: Boolean = false) {
        if (!canChangeEntry()) return
        val ticket = pickerWrites.begin(exercise.id)
        launchEntryMutation(source = ERR_ADD_LIFT) {
            try {
                if (swapTarget != null && swapDrafts != null) {
                    check(selectPersistedSwap(exercise, swapTarget))
                } else addExerciseInternal(exercise, swapTarget)
                pickerWrites.succeed(ticket, created)
                if (swapTarget != null && swapDrafts != null) completeSwapPicker()
            } catch (thrown: CancellationException) {
                pickerWrites.cancel(ticket)
                throw thrown
            } catch (thrown: Exception) {
                val message = thrown.message?.takeIf {
                    SetLogRules.isUserMessage(it) || SessionEditRules.isUserMessage(it)
                } ?: if (swapTarget != null && swapDrafts != null) swapRecoveryMessage() else
                    if (swapTarget != null) "Could not swap that lift. Try again." else SessionOrderCopy.ADD_LIFT_FAILED
                error.fail(ERR_ADD_LIFT, message)
                pickerWrites.fail(ticket, message) {
                    if (swapTarget != null && swapDrafts != null && pendingSwap?.checkpoint?.pending != null) retryPersistedSwap()
                    else pickExercise(exercise, swapTarget, created)
                }
            }
        }
    }

    fun createAndAddExercise(name: String, muscleGroup: String, loadType: LoadType = LoadType.EXTERNAL) =
        createPickerExercise(name, muscleGroup, loadType, swapTargetItemId.value)

    private fun createPickerExercise(name: String, muscleGroup: String, loadType: LoadType, swapTarget: String?) {
        if (!canChangeEntry()) return
        val ticket = pickerWrites.begin("create", creating = true)
        if (name.isBlank()) {
            error.fail(ERR_ADD_LIFT, SessionOrderCopy.LIFT_NAME_REQUIRED)
            pickerWrites.fail(ticket, SessionOrderCopy.LIFT_NAME_REQUIRED)
            return
        }
        launchEntryMutation(source = ERR_ADD_LIFT) {
            var created: Exercise? = null
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup, loadType = loadType)) {
                    is SaveExerciseResult.DuplicateName -> {
                        error.fail(ERR_ADD_LIFT, DUPLICATE_NAME_MESSAGE)
                        pickerWrites.fail(ticket, DUPLICATE_NAME_MESSAGE, existing = result.existing)
                    }
                    is SaveExerciseResult.MissingMuscle -> {
                        error.fail(ERR_ADD_LIFT, MuscleGroups.MISSING_MESSAGE)
                        pickerWrites.fail(ticket, MuscleGroups.MISSING_MESSAGE)
                    }
                    is SaveExerciseResult.Saved -> {
                        created = result.exercise
                        addExerciseInternal(result.exercise, swapTarget)
                        pickerWrites.succeed(ticket, created = true)
                    }
                }
            } catch (thrown: CancellationException) {
                pickerWrites.cancel(ticket)
                throw thrown
            } catch (thrown: Exception) {
                val saved = created
                val message = if (saved != null) "Lift saved to Library. Could not select it here. Retry to select it." else SessionOrderCopy.CREATE_LIFT_FAILED
                error.fail(ERR_ADD_LIFT, message)
                pickerWrites.fail(ticket, message) {
                    if (saved != null) pickExercise(saved, swapTarget, created = true)
                    else createPickerExercise(name, muscleGroup, loadType, swapTarget)
                }
            }
        }
    }

    fun requestSwap() {
        if (!canChangeEntry()) return
        val selectedId = selectedExerciseId.value ?: return
        val item = session.value?.exercises?.firstOrNull { it.exercise.id == selectedId } ?: return
        if (swapDrafts != null) { openWorkoutPicker(item.id); return }
        swapTargetItemId.value = item.id
        searchQuery.value = ""
        showPicker.value = true
    }

    fun removeSelectedLift() {
        if (!canChangeEntry()) return
        val started = error.mark()
        val selectedId = selectedExerciseId.value ?: return
        val item = session.value?.exercises?.firstOrNull { it.exercise.id == selectedId } ?: return
        launchEntryMutation(source = ERR_REMOVE_LIFT) {
            try {
                val removed = undo.serialize {
                    container.workoutRepository.removeExerciseFromSession(
                        sessionId = sessionId,
                        itemId = item.id,
                    ).also { undo.push(FloorUndo.RemovedLift(it)) }
                }
                _deleteFeedback.tryEmit(DeleteFeedback.REMOVED)
                draftCache.removeLift(sessionId, selectedId)
                numberEntries.value = numberEntries.value.filterNot { it.exerciseId == selectedId }
                savedDraft.removeLift(selectedId)
                // Room can select and prefill a survivor before this mutation resumes.
                // Clear only the removed selection; keep that newer entry ready.
                if (selectedExerciseId.value == selectedId) clearLiftSelection()
                error.clearFrom(source = ERR_REMOVE_LIFT, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "removeSelectedLift failed", thrown)
                error.fail(
                    source = ERR_REMOVE_LIFT,
                    message = thrown.message?.takeIf {
                        SetLogRules.isUserMessage(it) || SessionEditRules.isUserMessage(it)
                    } ?: "Could not remove that lift. Try again.",
                )
            }
        }
    }

    /**
     * Packet G: park the current lift and move to the next unfinished one.
     *
     * Nothing is deleted and the plan is untouched — the lift stays in the switcher with its
     * draft, so coming back later resumes exactly where it left. Distinct from Remove, which
     * takes the lift out of the session and offers it back through undo.
     */
    fun skipForNow() {
        if (!canChangeEntry()) return
        val started = error.mark()
        val current = session.value ?: return
        val selectedId = selectedExerciseId.value ?: return
        val next = WorkoutAdvance.nextUnfinishedExerciseId(current, selectedId)
        if (next == null) {
            error.fail(source = ERR_SKIP, message = CurrentLiftCopy.SKIP_NOWHERE)
            return
        }
        error.clearFrom(source = ERR_SKIP, before = started)
        // The stopwatch-running confirm path is the same one a manual switch takes.
        selectExercise(next)
    }

    private suspend fun addExerciseInternal(exercise: Exercise, swapTarget: String?, closePicker: Boolean = true) {
        val started = error.mark()
        check(sessionId.isNotBlank()) { "This workout is no longer available." }
        val replaced = swapTarget?.let { target -> container.workoutRepository.getSession(sessionId)?.exercises?.firstOrNull { it.id == target }?.exercise?.id }
        if (swapTarget != null) {
            container.workoutRepository.swapExerciseInSession(sessionId, swapTarget, exercise)
            clocks.forgetStopwatch(exercise.id)
            if (swapTargetItemId.value == swapTarget) swapTargetItemId.value = null
        } else {
            val current = container.workoutRepository.getSession(sessionId)
            if (current?.exercises?.none { it.exercise.id == exercise.id } != false) {
                clocks.forgetStopwatch(exercise.id)
                val goal = container.preferencesRepository.coachPreferences.first().goal
                val defaults = AddDefaults.forExercise(exercise, goal = goal)
                container.workoutRepository.addExerciseToSession(
                    sessionId = sessionId, exercise = exercise, targetSets = defaults.sets,
                    targetReps = defaults.reps, targetWeightKg = null, restSeconds = defaults.restSeconds,
                    targetSeconds = defaults.seconds, targetSecondsMax = defaults.secondsMax,
                )
            }
        }
        selectExerciseInternal(exercise.id)
        if (replaced != null) {
            draftCache.removeLift(sessionId, replaced)
            savedDraft.removeLift(replaced)
            numberEntries.value = numberEntries.value.filterNot { it.exerciseId == replaced }
            clocks.forgetStopwatch(replaced)
            persistDraft()
        }
        if (closePicker) showPicker.value = false
        if (pickerRecovery == null) searchQuery.value = ""
        error.clearFrom(source = ERR_ADD_LIFT, before = started)
    }

    private suspend fun selectOwnedPickerLift(exercise: Exercise): Boolean {
        if (!canChangeEntry()) return false
        val target = swapTargetItemId.value
        val permit = durability?.mutationPermit(sessionId)
        val ticket = if (target != null && swapDrafts != null) pickerWrites.begin("selecting-created-swap") else null
        mutating.value = true
        return try {
            val select: suspend () -> Boolean = {
                if (target != null && swapDrafts != null) selectPersistedSwap(exercise, target)
                else {
                    addExerciseInternal(exercise = exercise, swapTarget = target, closePicker = false)
                    container.workoutRepository.getSession(sessionId)?.exercises?.any { it.exercise.id == exercise.id } == true
                }
            }
            val confirmed = if (permit == null) select() else kotlinx.coroutines.withContext(permit) { select() }
            if (ticket != null) {
                if (confirmed) pickerWrites.succeed(ticket)
                else pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap)
            }
            confirmed
        } catch (cancelled: CancellationException) {
            if (ticket != null) pickerWrites.cancel(ticket)
            throw cancelled
        } catch (failure: Exception) {
            if (ticket != null) {
                swapOutcome = DraftRecoveryOutcome.UNAVAILABLE
                pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap)
            }
            throw failure
        } finally { mutating.value = false }
    }

    private fun openWorkoutPicker(requestedTarget: String?) {
        if (!canChangeEntry()) return
        val service = swapDrafts ?: return
        pickerWrites.clearFailures()
        val ticket = pickerWrites.begin("checking-swap")
        swapTargetItemId.value = requestedTarget
        launchEntryMutation(source = ERR_ADD_LIFT) {
            try {
                pendingSwap = service.openPending(sessionId)
                acceptedSwap = null
                swapOutcome = null
                pendingSwap?.let { handle ->
                    swapTargetItemId.value = handle.itemId
                    swapOutcome = service.inspect(handle)
                    if (swapOutcome == DraftRecoveryOutcome.EXACT_SAVED) acceptedSwap = handle.checkpoint.pending?.value
                }
                showPicker.value = true
                if (pendingSwap == null) pickerWrites.succeed(ticket)
                else pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap)
            } catch (cancelled: CancellationException) { pickerWrites.cancel(ticket); throw cancelled }
            catch (_: Exception) {
                swapOutcome = DraftRecoveryOutcome.UNAVAILABLE
                showPicker.value = true
                pickerWrites.fail(ticket, swapRecoveryMessage()) { openWorkoutPicker(requestedTarget) }
            }
        }
    }

    private suspend fun selectPersistedSwap(exercise: Exercise, target: String): Boolean {
        val service = checkNotNull(swapDrafts)
        val current = pendingSwap?.takeIf { it.checkpoint.pending != null }
            ?: service.open(sessionId, target).also { pendingSwap = it }
        check(current.itemId == target)
        if (current.checkpoint.pending != null) {
            check(current.checkpoint.pending!!.value.after.exerciseId == exercise.id)
            swapOutcome = service.inspect(current)
            if (swapOutcome != DraftRecoveryOutcome.EXACT_SAVED) return false
        } else {
            service.admit(current, sessionId, target, exercise)
            try { service.execute(current) { durability?.ownsEntry(sessionId) != false && swapTargetItemId.value == target } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { swapOutcome = DraftRecoveryOutcome.UNAVAILABLE; throw IllegalStateException(swapRecoveryMessage()) }
            swapOutcome = service.inspect(current)
        }
        check(swapOutcome == DraftRecoveryOutcome.EXACT_SAVED)
        val command = checkNotNull(current.checkpoint.pending).value
        acceptedSwap = command
        settleSwapEntry(command)
        return true
    }

    private suspend fun settleSwapEntry(command: WorkoutPickerSwap) {
        if (throwOnceOnSettleSwapForTest) {
            throwOnceOnSettleSwapForTest = false
            throw IllegalStateException("Synthetic settle failure for test")
        }
        clocks.forgetStopwatch(command.after.exerciseId)
        clocks.restoreStopwatchFor(command.after.exerciseId, resumeRunning = false)
        selectExerciseInternal(command.after.exerciseId)
        val replaced = command.before.exerciseId
        draftCache.removeLift(sessionId, replaced)
        savedDraft.removeLift(replaced)
        numberEntries.value = numberEntries.value.filterNot { it.exerciseId == replaced }
        clocks.forgetStopwatch(replaced)
        persistDraft()
    }

    private fun retryPersistedSwap() {
        if (!canChangeEntry()) return
        val service = swapDrafts ?: return
        val allowRetry = swapOutcome == DraftRecoveryOutcome.UNCOMMITTED
        val ticket = pickerWrites.begin("recovering-swap")
        launchEntryMutation(source = ERR_ADD_LIFT) {
            try {
                val current = checkNotNull(pendingSwap ?: service.openPending(sessionId).also { pendingSwap = it })
                swapTargetItemId.value = current.itemId
                swapOutcome = service.inspect(current)
                if (swapOutcome == DraftRecoveryOutcome.UNCOMMITTED && allowRetry) {
                    service.retry(current)
                    service.execute(current) { durability?.ownsEntry(sessionId) != false && swapTargetItemId.value == current.itemId }
                    swapOutcome = service.inspect(current)
                }
                if (swapOutcome != DraftRecoveryOutcome.EXACT_SAVED) {
                    pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap)
                    return@launchEntryMutation
                }
                acceptedSwap = checkNotNull(current.checkpoint.pending).value
                settleSwapEntry(checkNotNull(acceptedSwap))
                pickerWrites.succeed(ticket)
                completeSwapPicker()
            } catch (cancelled: CancellationException) { pickerWrites.cancel(ticket); throw cancelled }
            catch (_: Exception) { pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap) }
        }
    }

    private fun finishPickerSelection() {
        if (mutating.value) {
            viewModelScope.launch { mutating.first { !it }; finishPickerSelection() }
            return
        }
        val current = pendingSwap
        if (current == null) { setPickerVisible(false); return }
        if (!canChangeEntry()) return
        val ticket = pickerWrites.begin("confirming-swap")
        launchEntryMutation(source = ERR_ADD_LIFT) {
            try {
                val service = checkNotNull(swapDrafts)
                val command = checkNotNull(acceptedSwap)
                check(container.workoutRepository.inspectPickerSwap(command) == DraftRecoveryOutcome.EXACT_SAVED)
                if (current.checkpoint.pending != null) service.finish(current) else service.openPending(sessionId)
                pendingSwap = null; acceptedSwap = null; swapOutcome = null
                pickerWrites.succeed(ticket)
                setPickerVisible(false)
            } catch (cancelled: CancellationException) { pickerWrites.cancel(ticket); throw cancelled }
            catch (_: Exception) {
                swapOutcome = DraftRecoveryOutcome.EXACT_SAVED
                pickerWrites.fail(ticket, "Replacement saved. Confirmation was not completed; continue to check it.", retry = ::finishPickerSelection)
            }
        }
    }

    private fun offerCompletedSwapContinuation() {
        if (acceptedSwap == null || pendingSwap == null || pickerWrites.busy) return
        swapOutcome = DraftRecoveryOutcome.EXACT_SAVED
        val ticket = pickerWrites.begin("settled-swap-child")
        pickerWrites.fail(ticket, swapRecoveryMessage(), retry = ::retryPersistedSwap)
    }

    private fun completeSwapPicker() {
        viewModelScope.launch {
            mutating.first { !it }
            val recovery = pickerRecovery ?: return@launch
            if (recovery.create.status.value.phase == com.sinura.personaltrainer.ui.components.PickerCreatePhase.CLOSED &&
                recovery.view.fields.value.customDraftId == acceptedSwap?.after?.exerciseId) {
                val opening = recovery.resumeCreate()
                if (opening == null) { offerCompletedSwapContinuation(); return@launch }
                opening.join()
                if (opening.isCancelled) { offerCompletedSwapContinuation(); return@launch }
            }
            if (recovery.create.status.value.phase == com.sinura.personaltrainer.ui.components.PickerCreatePhase.SAVED)
                recovery.useSaved()
            else if (recovery.view.fields.value.customDraftId != acceptedSwap?.after?.exerciseId)
                recovery.view.leave(::finishPickerSelection)
        }
    }

    private fun discardPickerSwap(onDiscarded: () -> Unit) {
        val target = pendingSwap?.itemId ?: swapTargetItemId.value
        if (swapDrafts == null) { onDiscarded(); return }
        if (!canChangeEntry()) return
        val ticket = pickerWrites.begin("resetting-swap")
        launchEntryMutation(source = ERR_ADD_LIFT) {
            try {
                swapDrafts.reset(sessionId, target)
                pendingSwap = null; acceptedSwap = null; swapOutcome = null
                pickerWrites.clearFailures()
                pickerWrites.succeed(ticket)
                onDiscarded()
                if (target != null && container.workoutRepository.getSession(sessionId)?.exercises?.none { it.id == target } == true) setPickerVisible(false)
            } catch (cancelled: CancellationException) { pickerWrites.cancel(ticket); throw cancelled }
            catch (_: Exception) { pickerWrites.fail(ticket, "Picker reset was not confirmed. Saved workout rows are kept.") { discardPickerSwap(onDiscarded) } }
        }
    }

    private fun swapRecoveryMessage(): String = when (swapOutcome) {
        DraftRecoveryOutcome.EXACT_SAVED -> "Replacement saved. Continue to return to your workout."
        DraftRecoveryOutcome.UNCOMMITTED -> "Replacement was not saved. Retry the exact kept swap."
        DraftRecoveryOutcome.CONFLICT -> "This workout or lift changed. Check the saved workout or reset this picker."
        DraftRecoveryOutcome.UNAVAILABLE, null -> "Swap was not confirmed. Check what saved before trying again."
    }

    /**
     * A record just broken, held until the screen has shown it.
     *
     * One-shot state rather than a callback, for the same reason navigation is: the set is
     * written before the answer is known, and a lambda captured into that coroutine belongs to
     * a composition that may not exist by the time the query returns.
     */
    private val _personalRecord = MutableStateFlow<PersonalRecordMoment?>(null)
    val personalRecord: StateFlow<PersonalRecordMoment?> = _personalRecord.asStateFlow()

    /** True while the lifter asked to log past the prescription. Cleared on log, Next, or switch. */
    val extraSetRequested: StateFlow<Boolean> = wantAnotherSet.asStateFlow()

    private val _logReceipt = MutableStateFlow<LogReceipt?>(null)
    val logReceipt: StateFlow<LogReceipt?> = _logReceipt.asStateFlow()

    fun onLogReceiptShown() {
        _logReceipt.value = null
    }

    private val _logFeedback = MutableSharedFlow<LogCommitFeedback>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val logFeedback: SharedFlow<LogCommitFeedback> = _logFeedback.asSharedFlow()

    fun onPersonalRecordShown() {
        _personalRecord.value = null
    }

    init {
        viewModelScope.launch {
            combine(sessionReader.observations, privateDrafts.recovery) { read, recovery -> read to recovery }.collect { (read, recovery) ->
                if (!recovery.canEnter || read.loadState == SessionLoadState.LOADING) return@collect
                val operation = saves.operation.value
                if (operation.phase == WorkoutSavePhase.CHECKING && !saves.inFlight.value) {
                    operation.command?.let { saves.reconcile(it, retryWrite = false) }
                }
            }
        }
    }

    private fun canChangeEntry(): Boolean {
        if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return false }
        return privateDrafts.recovery.value.canEnter && !terminalExit && !draftCache.liveEntryLocked(sessionId) && _exitRequested.value == null && !saves.inFlight.value && !mutating.value && !saves.pending &&
            sessionReader.observations.value.loadState == SessionLoadState.FOUND &&
            sessionReader.observations.value.session?.isFinished == false
    }

    private fun launchEntryMutation(source: String, block: suspend () -> Unit) {
        if (!canChangeEntry()) return
        val permit = durability?.mutationPermit(sessionId)
        mutating.value = true
        viewModelScope.launch {
            try {
                if (permit == null) block() else kotlinx.coroutines.withContext(permit) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AppLog.w(TAG, "Workout operation failed: $source", failure)
                error.fail(source = source, message = "Could not update this workout. Your saved sets are kept.")
            } finally {
                mutating.value = false
            }
        }
    }

    private fun clearEditingOriginal() {
        editingOriginal = null
        savedEdit.clear()
        draftCache.putEditingOriginal(sessionId, null)
    }

    val primaryAction: StateFlow<WorkoutPrimaryAction> = combine(
        uiState,
        wantAnotherSet,
        combine(clocks.hold, clocks.stopwatch, clocks.generation) { hold, stopwatch, generation ->
            Triple(hold, stopwatch, generation)
        },
        primaryActivation,
        restTimerState,
    ) { state, extra, timing, activation, rest ->
        val session = state.session
        val lift = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
        val workingLogged = session?.sets?.count {
            it.exerciseId == state.selectedExerciseId && !it.isWarmup
        } ?: 0
        val nextSet = workingLogged + 1
        val immersionLiftId = restImmersionLiftIdStore.value
        val onRestLift = rest.running && state.selectedExerciseId == immersionLiftId
        val startNowLabel = nextSet.takeIf { onRestLift && lift != null }?.let { ordinal ->
            getApplication<Application>().getString(R.string.rest_start_now, ordinal)
        }
        WorkoutPrimaryActions.derive(
            state.copy(mutating = state.mutating || !privateDrafts.recovery.value.canCommit && !state.save.pending),
            extra,
            timing.first,
            timing.second,
            timing.third,
            activation,
            restRunning = rest.running,
            restImmersionLiftId = immersionLiftId,
            startSetNowLabel = startNowLabel,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, currentPrimaryAction())

    private fun currentPrimaryAction(): WorkoutPrimaryAction {
        val read = sessionReader.observations.value
        val draftValue = draft.value
        val selected = selectedExerciseId.value
        val lift = read.session?.exercises?.firstOrNull { it.exercise.id == selected }
        val hold = lift?.exercise?.let { HoldWork.isHold(it) } == true
        val effortApplies = !draftValue.isWarmup && !hold
        val source = effortSource.value
        val rest = restTimerState.value
        val immersionLiftId = restImmersionLiftIdStore.value
        val workingLogged = read.session?.sets?.count {
            it.exerciseId == selected && !it.isWarmup
        } ?: 0
        val onRestLift = rest.running && selected == immersionLiftId
        val startNowLabel = (workingLogged + 1).takeIf { onRestLift && lift != null }?.let { ordinal ->
            getApplication<Application>().getString(R.string.rest_start_now, ordinal)
        }
        return WorkoutPrimaryActions.derive(
            state = ActiveWorkoutUiState(
                loadState = read.loadState, session = read.session,
                selectedExerciseId = selected, draft = draftValue,
                editingSetId = editingSetId.value, logging = saves.inFlight.value,
                mutating = mutating.value || !privateDrafts.recovery.value.canCommit && !saves.pending, save = saves.operation.value,
                liftReadiness = liftReadiness.value, draftDirty = draftDirty.value,
                effortSource = source,
                effortValue = draftValue.rpe.takeIf { effortApplies },
            ),
            extraSet = wantAnotherSet.value, hold = clocks.hold.value, stopwatch = clocks.stopwatch.value,
            timedGeneration = clocks.generation.value, activation = primaryActivation.value,
            restRunning = rest.running,
            restImmersionLiftId = immersionLiftId,
            startSetNowLabel = startNowLabel,
        )
    }

    /** Validate the rendered action; a second Log tap cannot become Next after the save. */
    fun performPrimary(action: WorkoutPrimaryAction): Boolean {
        if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return false }
        val current = currentPrimaryAction()
        if (!current.enabled || action.identity != current.identity) return false
        val now = elapsedNow()
        val previous = lastPrimaryTap
        if (previous != null && now >= previous &&
            now - previous < android.view.ViewConfiguration.getDoubleTapTimeout()
        ) return false
        lastPrimaryTap = now
        primaryActivation.value += 1
        when (action.kind) {
            WorkoutPrimaryKind.ADD_EXERCISE -> setPickerVisible(true)
            WorkoutPrimaryKind.START_HOLD -> startHoldSet()
            WorkoutPrimaryKind.LOG_SET, WorkoutPrimaryKind.LOG_WARMUP, WorkoutPrimaryKind.LOG_HOLD, WorkoutPrimaryKind.SAVE_CHANGES ->
                logSetWithDuration(action.durationSeconds, freezeDisplayedDuration = true)
            WorkoutPrimaryKind.NEXT_EXERCISE -> action.identity.nextExerciseId?.let(::selectExercise)
            WorkoutPrimaryKind.RETRY_SAVE -> retrySave()
            WorkoutPrimaryKind.FINISH, WorkoutPrimaryKind.REVIEW_SAVE -> Unit // The screen opens its confirmation/details.
            WorkoutPrimaryKind.START_SET_NOW -> skipRest()
            WorkoutPrimaryKind.UNAVAILABLE, WorkoutPrimaryKind.CHECKING, WorkoutPrimaryKind.SAVING, WorkoutPrimaryKind.UPDATING, WorkoutPrimaryKind.GET_READY -> return false
        }
        return true
    }

    /** No production caller: the floor logs through [performPrimary]. Tests drive a plain log. */
    @VisibleForTesting
    internal fun logSet() = logSetWithDuration(displayedDurationSeconds = null, freezeDisplayedDuration = false)

    private fun logSetWithDuration(displayedDurationSeconds: Int?, freezeDisplayedDuration: Boolean) {
        if (!privateDrafts.recovery.value.canCommit) return
        if (!canChangeEntry()) return
        val exerciseId = selectedExerciseId.value ?: return
        if (!(liftReadiness.value.allowsCommit() || draftDirty.value)) return
        val current = draft.value
        val selectedLift = session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId } ?: return
        val hold = HoldWork.isHold(selectedLift.exercise)
        val holdState = clocks.hold.value
        val editingId = editingSetId.value
        if (hold && editingId == null && holdState.totalSeconds == 0 && !holdState.running) {
            startHoldSet()
            return
        }
        // GET READY: the hold has not been held yet, so there is nothing to log (P2b). Cancel is
        // the bar's; the commit is disabled.
        if (hold && editingId == null && holdState.gettingReady) return
        val original = editingOriginal?.takeIf { it.setId == editingId && it.exerciseId == exerciseId }
        if (editingId != null && original == null) {
            error.fail(source = ERR_LOG_SET, message = "That saved set is no longer available. Cancel editing to continue.")
            return
        }
        val duration = if (freezeDisplayedDuration) displayedDurationSeconds else FloorTimerSurface.durationToLog(
            hold = hold,
            holdElapsedSeconds = holdState.elapsedSeconds,
            holdTotalSeconds = holdState.totalSeconds,
            holdRemainingSeconds = holdState.remainingSeconds,
            holdDraftSeconds = current.durationSeconds ?: selectedLift.targetSeconds,
            stopwatch = clocks.stopwatch.value,
            existingDurationSeconds = current.durationSeconds.takeUnless { hold },
            holdGettingReady = holdState.gettingReady,
        )
        val values = WorkoutSetValues(
            weightKg = current.weightKg,
            reps = if (hold) 0 else current.reps,
            rpe = current.rpe,
            isWarmup = current.isWarmup,
            durationSeconds = duration,
        )
        val invalid = SetLogRules.validate(
            weightKg = values.weightKg, reps = values.reps, isWarmup = values.isWarmup,
            loadType = selectedLift.exercise.loadType, durationSeconds = values.durationSeconds,
            isHold = hold, equipment = selectedLift.exercise.equipment,
            movementKey = selectedLift.exercise.movementKey,
        )
        if (invalid != null) {
            error.fail(source = ERR_LOG_SET, message = invalid)
            _logFeedback.tryEmit(LogCommitFeedback.REJECT)
            return
        }
        // A working set logs only with its effort (P2a, owner decision of 29 September 2026).
        // Here, on the one path every Log, warm-up log, hold log and Save changes takes, so
        // the floor and the test seam refuse alike; History's corrections check the same rule.
        // Log is disabled until effort is chosen; this guard catches tests and stray calls.
        if (SetLogRules.validateEffort(rpe = values.rpe, isWarmup = values.isWarmup, isHold = hold) != null) return
        val command = WorkoutSetSave(
            sessionId = sessionId, exerciseId = exerciseId,
            setId = original?.setId ?: IdFactory.Uuid.newId(),
            completedAt = original?.completedAt ?: time.nowMillis(),
            values = values, original = original?.values,
        )
        saves.freeze(command)
        viewModelScope.launch { saves.persist(command) }
    }

    /** Retry belongs to the frozen command, never to today's editable draft or timer. */
    fun retrySave() {
        val command = saves.beginRetry() ?: return
        viewModelScope.launch { saves.reconcile(command, retryWrite = true) }
    }

    /** Inspect before releasing a failed operation for editing; an unknown outcome stays owned. */
    fun editFailedSave() {
        val command = saves.beginEdit() ?: return
        viewModelScope.launch { saves.reconcile(command, retryWrite = false, releaseUnwritten = true) }
    }

    /**
     * Edit let go of a set the database does not hold as it ([FloorSetSaves]); its copies are
     * already cleared. After a conflict the edit is let go too; after an unwritten set, Log's
     * earlier refusal is cleared.
     */
    private fun releaseSave(command: WorkoutSetSave, conflict: Boolean) {
        if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return }
        if (conflict) {
            editingSetId.value = null
            clearEditingOriginal()
            val read = sessionReader.observations.value
            if (read.loadState == SessionLoadState.FOUND &&
                read.session?.exercises?.none { it.exercise.id == command.exerciseId } == true) {
                retireRemovedEntry(command.exerciseId)
            }
            reconcileSelectionAfterSave()
            persistDraft()
        } else {
            error.clearFrom(source = ERR_LOG_SET, before = error.mark())
            reconcileSelectionAfterSave()
        }
    }

    /** Retire only an owner proven absent by a successful session read, after save inspection. */
    private fun retireRemovedEntry(exerciseId: String) {
        // Clearing selection first saves/stops its clock. Remove those copies afterwards so
        // applySelection cannot persist the released command's draft back into the checkpoint.
        // Do not forget the lift's saved set clock here: Remove keeps it for Undo (TH6 / Q4).
        if (selectedExerciseId.value == exerciseId) clearLiftSelection()
        draftCache.removeLift(sessionId, exerciseId)
        savedDraft.removeLift(exerciseId)
        numberEntries.value = numberEntries.value.filterNot { it.exerciseId == exerciseId }
        if (pendingResumeDraft?.exerciseId == exerciseId) pendingResumeDraft = null
    }

    private fun restoreSaveDraft(command: WorkoutSetSave) {
        val current = sessionReader.observations.value.session
        if (current != null && current.exercises.none { it.exercise.id == command.exerciseId }) {
            reconcileSelectionAfterSave()
            return
        }
        applySelection(command.exerciseId)
        editingSetId.value = command.setId.takeIf { command.editing }
        val values = command.values
        draft.value = ActiveExerciseDraft(
            weightKg = values.weightKg, reps = values.reps, rpe = values.rpe,
            isWarmup = values.isWarmup, durationSeconds = values.durationSeconds,
        )
        val cached = liftDraft(command.exerciseId)
        effortSource.value = when {
            cached != null && cached.rpe == values.rpe -> cached.effortSource
            values.isWarmup || values.rpe == null -> EffortSource.NONE
            else -> EffortSource.USER
        }
        draftDirty.value = true
        liftReadiness.value = LiftEntryReadiness.READY
    }

    private fun reconcileSelectionAfterSave() {
        val current = sessionReader.observations.value.session ?: return
        val resolved = current.resolveSelectedExerciseId(selectedExerciseId.value)
        if (resolved != selectedExerciseId.value) {
            if (resolved == null) clearLiftSelection() else applySelection(resolved)
        }
    }

    private fun acknowledgeSave(
        command: WorkoutSetSave,
        result: WorkoutRepository.SavedWorkoutSet?,
        recovered: Boolean,
    ) {
        if (!saves.owns(command)) return
        // Entry/selection were locked while this command was outstanding.
        saves.clear(command)
        if (session.value?.sets?.none { it.id == command.setId } != false) savedSetAwaitingRow = command.setId
        try {
            editingSetId.value = null
            clearEditingOriginal()
            wantAnotherSet.value = false
            tempoTipDismissed.value = false
            // A correction saved is the lifter's number (W2e): a warm-up's, saved unchanged, would
            // otherwise read as an untouched entry once Warm-up is cleared here.
            if (command.editing) draftDirty.value = true
            val hold = session.value?.exercises
                ?.firstOrNull { it.exercise.id == command.exerciseId }
                ?.exercise
                ?.let { HoldWork.isHold(it) } == true
            val seededEffort = EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = hold,
                exerciseId = command.exerciseId,
                sessionSets = sessionSetsForEffortSeed(command),
                priorWorkingSets = priorHistory.value,
            )
            draft.value = draft.value.copy(isWarmup = false, rpe = seededEffort)
            effortSource.value = if (seededEffort != null) EffortSource.PREFILL_LAST else EffortSource.NONE
            error.clearFrom(source = ERR_LOG_SET, before = error.mark())
            persistDraft()
            clocks.stopHold()
            clocks.discardStopwatch()
            reconcileSelectionAfterSave()
            if (recovered || result == null) return
            val values = command.values
            val lift = session.value?.exercises?.firstOrNull { it.exercise.id == command.exerciseId }
            emitLogReceipt(
                setId = command.setId, weightKg = values.weightKg, reps = values.reps,
                rpe = values.rpe, isWarmup = values.isWarmup, durationSeconds = values.durationSeconds,
                loadType = lift?.exercise?.loadType,
                warmupAfter = result.warmupOrdinal, workingAfter = result.workingOrdinal,
                targetSets = result.targetSets,
            )
            _logFeedback.tryEmit(LogCommitFeedback.SUCCESS)
            if (result.records.isNotEmpty()) {
                _personalRecord.value = PersonalRecordMoment(
                    exerciseName = lift?.exercise?.name.orEmpty(), kinds = result.records,
                    weightKg = values.weightKg, reps = values.reps,
                )
            }
            if (command.editing) return
            val complete = !values.isWarmup && WorkoutAdvance.liftComplete(
                result.workingOrdinal, result.targetSets, wantAnother = false,
            )
            cancelPendingRest()
            val startRest = RestTimer.shouldStartAfterLog(values.isWarmup, result.workingOrdinal, result.targetSets) ||
                RestTimer.shouldStartAfterExtra(values.isWarmup, result.workingOrdinal, result.targetSets)
            if (startRest) {
                scheduleRestAfterReceipt(RestPrescription.seconds(
                    reasonCode = microRec.value?.reasonCode ?: SetMicroRecCalculator.QUALITY,
                    loadType = lift?.exercise?.loadType, reps = values.reps,
                ))
            } else if (complete) {
                restTimer.stop()
            }
        } catch (failure: Exception) {
            AppLog.w(TAG, "Set saved; subsequent workout feedback failed", failure)
            error.fail(source = ERR_LOAD, message = "Set saved. Timer or feedback could not update.")
        }
    }

    fun dismissError() {
        error.dismiss()
    }

    fun retrySession() {
        if (mutating.value || notesExitPending || saves.inFlight.value) return
        sessionReader.retry()
        privateDrafts.open()
    }

    /**
     * Open a saved set for correction, with its original values read from the stored row.
     *
     * The original travels with the save, and [WorkoutRepository.saveSet] refuses an edit
     * whose original does not match the row. Taking it from [session] instead — a Flow's
     * cached copy — meant a second correction of the same set, opened before the first had
     * been published back, carried values one revision old and was rejected as a conflict it
     * was not. An edit changes no set count, so there was nothing for the screen to wait on
     * either. The row is read here for the same reason the check reads it.
     *
     * Running as an entry mutation is what makes that a rule rather than a shorter window:
     * [logSetWithDuration] cannot issue a command while [mutating] is raised, so no save can
     * overtake this read and send the stale original anyway.
     */
    fun editSet(setId: String) {
        launchEntryMutation(source = ERR_EDIT_SET) {
            val original = container.workoutRepository.editableSet(sessionId, setId)
                ?: return@launchEntryMutation
            val values = original.values
            // editingSetId is set before the lift below, and prefill refuses to run while it
            // is, so selecting the set's lift cannot overwrite the values being edited.
            editingSetId.value = original.setId
            editingOriginal = original.also { saved ->
                savedEdit.write(saved)
                draftCache.putEditingOriginal(sessionId, saved)
            }
            clocks.stopHold()
            clocks.discardStopwatch()
            selectedExerciseId.value = original.exerciseId
            val hold = session.value?.exercises
                ?.firstOrNull { it.exercise.id == original.exerciseId }
                ?.let { HoldWork.isHold(it.exercise) } == true
            draft.value = ActiveExerciseDraft(
                weightKg = values.weightKg,
                reps = if (hold) 0 else values.reps,
                rpe = values.rpe,
                isWarmup = values.isWarmup,
                durationSeconds = values.durationSeconds,
            )
            persistDraft()
        }
    }

    fun cancelEdit() {
        if (!canChangeEntry()) return
        editingSetId.value = null
        clearEditingOriginal()
        persistDraft()
    }

    /**
     * Deletes immediately and offers the reversal, instead of asking first.
     *
     * The confirm dialog this replaces charged a tap to every delete — including the common
     * one, a set logged against the wrong lift ten seconds ago — to protect against a delete
     * nobody makes by accident on a row that only the latest set even exposes. The undo
     * charges nothing until you were actually wrong.
     *
     * Packet G: the token stacks. A second delete reveals its own offer without expiring the
     * first. The write and the push run inside the entry lock ([launchEntryMutation]), which keeps
     * them apart from every other delete, removal and undo, so the offer always names exactly
     * what landed; they also take the undo lock ([FloorUndoOffers.serialize]), kept on purpose.
     * Expiry pops without either lock. Never starts or restarts rest.
     */
    fun deleteSet(setId: String) {
        if (!canChangeEntry()) return
        val started = error.mark()
        launchEntryMutation(source = ERR_DELETE_SET) {
            val current = session.value
            val deleted = current?.sets?.firstOrNull { it.id == setId }
            val wasLatest = deleted != null &&
                current.sets.maxByOrNull { it.completedAt }?.id == setId
            if (editingSetId.value == setId) {
                editingSetId.value = null
                clearEditingOriginal()
            }
            try {
                val removed = undo.serialize {
                    val snapshot = container.workoutRepository.deleteSet(setId)
                    if (snapshot != null) undo.push(FloorUndo.DeletedSet(snapshot))
                    if (wasLatest) {
                        cancelPendingRest()
                        restTimer.stop()
                    }
                    snapshot
                }
                if (savedSetAwaitingRow == setId) savedSetAwaitingRow = null
                if (removed != null) _deleteFeedback.tryEmit(DeleteFeedback.DELETED)
                error.clearFrom(source = ERR_DELETE_SET, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "deleteSet failed", thrown)
                error.fail(
                    source = ERR_DELETE_SET,
                    message = "Could not delete that set. Try again.",
                )
            }
        }
    }

    /**
     * Puts the set back exactly as it was, and deliberately does NOT restart the rest timer:
     * the rest that followed that set has already been taken, and re-arming a countdown
     * minutes later would be the app inventing a state the lifter is not in.
     *
     * Packet G: undoes the top token when it is a deleted set, revealing whatever is
     * underneath. A failed restore keeps the offer rather than expiring it.
     */
    fun undoDeleteSet() {
        if (!canChangeEntry()) return
        val started = error.mark()
        launchEntryMutation(source = ERR_UNDO_DELETE) {
            undo.serialize {
                val top = undo.top as? FloorUndo.DeletedSet
                    ?: return@serialize null
                container.workoutRepository.restoreSet(top.deleted)
                undo.pop()
            } ?: return@launchEntryMutation
            _deleteFeedback.tryEmit(DeleteFeedback.UNDO)
            error.clearFrom(source = ERR_UNDO_DELETE, before = started)
        }
    }

    fun undoRemoveLift() {
        if (!canChangeEntry()) return
        val started = error.mark()
        launchEntryMutation(source = ERR_UNDO_REMOVE) {
            val pending = undo.serialize {
                val top = undo.top as? FloorUndo.RemovedLift
                    ?: return@serialize null
                container.workoutRepository.restoreExerciseToSession(removed = top.removed)
                undo.pop()
                top
            } ?: return@launchEntryMutation
            try {
                applySelection(pending.removed.item.exerciseId)
                persistDraft()
                _deleteFeedback.tryEmit(DeleteFeedback.UNDO)
                error.clearFrom(source = ERR_UNDO_REMOVE, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "restoreExerciseToSession failed", thrown)
                error.fail(
                    source = ERR_UNDO_REMOVE,
                    message = "Could not restore that lift. Try again.",
                )
            }
        }
    }

    /** The banner's Undo: reverses whatever the top offer names. */
    fun undoTopOffer() {
        when (undo.top) {
            is FloorUndo.DeletedSet -> undoDeleteSet()
            is FloorUndo.RemovedLift -> undoRemoveLift()
            null -> Unit
        }
    }

    /**
     * The banner timed out. The top offer expires silently — no saved cue, no rest change —
     * and the next offer underneath is revealed with a fresh dwell.
     */
    fun onUndoOfferExpired() {
        val entry = undo.pop() ?: return
        when (val token = entry.token) {
            is FloorUndo.RemovedLift -> forgetRemovedLiftStopwatchIfOfferFullyEnded(token.removed.item.exerciseId)
            else -> Unit
        }
    }

    private fun liftLoadType(exerciseId: String): LoadType? =
        session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }?.exercise?.loadType

    fun skipRest() {
        cancelPendingRest()
        restTimer.stop()
        tempoCoachSheetVisible.value = false
        setRestImmersionLiftId(null)
    }

    /** Idle: change the planned rest. Running: the same gateway ±15. */
    fun nudgeRest(deltaSeconds: Int) {
        if (restTimer.snapshot.value.running) {
            restTimer.adjust(deltaSeconds)
        } else {
            selectRestDuration(RestTimer.nudgeSeconds(restTotal.value.seconds, deltaSeconds))
        }
    }

    /** Names the next rest. Does not start the clock. */
    fun selectRestDuration(seconds: Int) {
        restTotal.value = PlannedRest(
            seconds = seconds,
            sourceTag = RestPlan(seconds, RestPlanSource.LAST_PRESET).sourceTag(),
            chosenFor = selectedExerciseId.value,
        )
        restCommands.rememberPick(seconds)
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        if (!canChangeEntry()) return
        clocks.bump()
        clocks.stopHold()
        clocks.discardStopwatch()
        val planned = restTotal.value
        setRestImmersionLiftId(selectedExerciseId.value)
        restCommands.startPlanned(planned.seconds, planned.sourceTag)
    }

    fun acknowledgeRestBatteryHint() {
        restCommands.acknowledgeBatteryHint()
    }

    /** Fills the wells from one working set of the last session. Does not log. */
    fun applyLastTimeSet(weightKg: Double, reps: Int) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite()) return
        editEntry {
            draft.value = draft.value.copy(
                weightKg = weightKg.coerceAtLeast(0.0),
                reps = reps.coerceAtLeast(1),
            )
            markDraftDirty()
        }
    }

    /**
     * Copies the in-set next load into the draft only. Does not [logSet].
     * After a log, today's hold stays until the lifter taps Use.
     */
    fun applyMicroRec() {
        if (!canChangeEntry()) return
        val rec = microRec.value ?: return
        applyCoachRec(rec)
    }

    fun applyTempoCoachTip(tip: TempoCoachTip) {
        if (!canChangeEntry()) return
        tempoCoachSheetVisible.value = false
        when (tip) {
            is TempoCoachTip.NextSet -> applyMicroRec()
            is TempoCoachTip.AddASet -> {
                wantAnotherSet.value = true
                applyCoachRec(tip.seedRec, force = true)
                selectedExerciseId.value?.let { liftId ->
                    addASetAcceptedExercises.value = addASetAcceptedExercises.value + liftId
                }
            }
        }
        tempoTipDismissed.value = true
    }

    fun dismissTempoCoachTip(tip: TempoCoachTip) {
        if (!canChangeEntry()) return
        when (tip) {
            is TempoCoachTip.AddASet -> selectedExerciseId.value?.let { liftId ->
                addASetDismissedExercises.value = addASetDismissedExercises.value + liftId
            }
            is TempoCoachTip.NextSet -> tempoTipDismissed.value = true
        }
    }

    private fun applyCoachRec(rec: SetMicroRec, force: Boolean = false) {
        if (!force && (!rec.showApply || rec.previewOnly)) return
        val current = session.value
        val liftId = current?.resolveSelectedExerciseId(selectedExerciseId.value)
        val lift = current?.exercises?.firstOrNull { it.exercise.id == liftId }
        if (lift != null && HoldWork.isHold(lift.exercise)) return
        editEntry {
            val current = draft.value
            val coachEffort = EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(
                isWarmup = current.isWarmup,
                isHold = false,
                suggestedRpe = rec.nextRpe,
            )
            draft.value = current.copy(
                weightKg = rec.nextWeightKg.coerceAtLeast(0.0),
                reps = rec.nextReps.coerceAtLeast(1),
                rpe = coachEffort ?: current.rpe,
            )
            if (coachEffort != null) effortSource.value = EffortSource.COACH
            markDraftDirty()
        }
    }

    /**
     * Set when this screen should be popped. Held as state for the same reason as forward
     * navigation: a callback captured into a coroutine is bound to a NavController that may
     * no longer exist by the time the database work finishes.
     *
     * Pops ack BEFORE navigating (forward navigations ack after) — a duplicate pop would eat
     * an extra screen, which is worse than the vanishingly narrow window it guards against.
     */
    fun onExitHandled() {
        _exitRequested.value = null
    }

    /**
     * Dispatches to [FinishWorkout], which owns the timer-stop / write / draft-clear order.
     * This screen keeps only what is its own: the error channel, the finished flag and the
     * one-shot exit event.
     */
    fun finishWorkout() {
        if (!canChangeEntry()) return
        if (saves.inFlight.value) return
        cancelPendingRest()
        val started = error.mark()
        launchEntryMutation(source = ERR_FINISH) {
            when (val outcome = sessionNotes.finishWithLatest { notes -> container.finishWorkout(sessionId, notes) }) {
                is FinishOutcome.Finished -> {
                    PendingOccurrence.complete(container, outcome.sessionId)
                    error.clearFrom(source = ERR_FINISH, before = started)
                    // The use case clears the process-wide cache; the SavedStateHandle mirror
                    // is scoped to this nav entry and unreachable from anywhere else, so it
                    // stays this ViewModel's job.
                    terminalExit = true
                    // The use case cleared this before returning. Clear once more after the
                    // terminal flag so an in-flight pre-finish Room emission cannot recreate
                    // the cache in the narrow gap between those two operations.
                    draftCache.clear(sessionId)
                    savedDraft.clear()
                    savedEdit.clear()
                    finished.value = true
                    _exitRequested.value = WorkoutExit.Finished(outcome.sessionId)
                }

                FinishOutcome.NothingLogged ->
                    error.fail(
                        source = ERR_FINISH,
                        message = "Log at least one set before finishing.",
                    )

                // Two different answers (UX23): a row that is not there cannot be retried
                // into existence, and a write that failed left every logged set where it was.
                // Both are the finish action refusing, so both are ERR_FINISH: the only thing
                // that answers either of them is a finish that lands, and that one success
                // must clear either one.
                FinishOutcome.SessionMissing ->
                    error.fail(
                        source = ERR_FINISH,
                        message = DataHealthCopy.FINISH_NOT_FOUND,
                    )

                FinishOutcome.NotesPending ->
                    error.fail(source = ERR_FINISH, message = EndWorkoutCopy.BAR_NOTES_PENDING)

                FinishOutcome.InProgress ->
                    error.fail(source = ERR_FINISH, message = EndWorkoutCopy.FINISHING)

                is FinishOutcome.Failed ->
                    error.fail(
                        source = ERR_FINISH,
                        message = DataHealthCopy.FINISH_FAILED,
                    )
            }
        }
    }

    fun discardWorkout() {
        if (!canChangeEntry()) return
        cancelPendingRest()
        val started = error.mark()
        launchEntryMutation(source = ERR_DISCARD) {
            when (container.discardWorkout(sessionId)) {
                DiscardOutcome.Discarded -> {
                    PendingOccurrence.forgetIfSession(container, sessionId)
                    error.clearFrom(source = ERR_DISCARD, before = started)
                    terminalExit = true
                    draftCache.clear(sessionId)
                    savedDraft.clear()
                    savedEdit.clear()
                    _exitRequested.value = WorkoutExit.Discarded
                }

                is DiscardOutcome.Failed ->
                    error.fail(
                        source = ERR_DISCARD,
                        message = "Could not discard this workout. Try again.",
                    )
            }
        }
    }

    private fun emitLogReceipt(
        setId: String,
        weightKg: Double,
        reps: Int,
        rpe: Int?,
        isWarmup: Boolean,
        durationSeconds: Int?,
        loadType: LoadType?,
        warmupAfter: Int,
        workingAfter: Int,
        targetSets: Int,
    ) {
        val loadClass = LoadClass.of(loadType)
        val ordinal = LogReceiptCopy.ordinal(
            isWarmup = isWarmup,
            warmupAfter = warmupAfter,
            workingAfter = workingAfter,
            targetSets = targetSets,
        )
        val payload = LogReceiptCopy.payload(
            weightKg = weightKg,
            reps = reps,
            loadClass = loadClass,
            unit = weightUnit.value,
            rpe = rpe,
            durationSeconds = durationSeconds,
        )
        _logReceipt.value = LogReceipt(
            setId = setId,
            line = LogReceiptCopy.line(ordinal, payload),
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            isWarmup = isWarmup,
        )
    }

    private fun scheduleRestAfterReceipt(prescribedSeconds: Int) {
        cancelPendingRest()
        pendingRestJob = viewModelScope.launch {
            delay(Motion.ROW_SETTLE_MS.toLong() + WalkthroughRestCaptureHook.extraDelayAfterSettleMs())
            if (canChangeEntry()) startRestAfterSet(prescribedSeconds)
        }
    }

    private fun startRestAfterSet(prescribedSeconds: Int? = null) {
        val planned = session.value?.exercises
            ?.firstOrNull { it.exercise.id == selectedExerciseId.value }
        val restPrefs = restPrefsNow.value
        val tempoReps = microRec.value?.nextReps
        val plan = RestTimer.resolvePlan(
            exerciseRestSeconds = planned?.restSeconds,
            preferences = restPrefs,
            prescribedSeconds = prescribedSeconds,
            tempoRepsForLabel = tempoReps,
        )
        restTotal.update {
            it.copy(seconds = plan.seconds, sourceTag = plan.sourceTag())
        }
        val seconds = plan.seconds
        setRestImmersionLiftId(selectedExerciseId.value)
        restTimer.start(seconds, sessionId, plan.sourceTag())
        viewModelScope.launch {
            container.preferencesRepository.markRestAlarmEligible()
            container.preferencesRepository.recordRestFloorCaptionForSession(sessionId)
            val sessions = container.preferencesRepository.restFloorCaptionSessions.first()
            showRestFirstCaption.value = sessions <= 3
            maybeOpenTempoCoachSheet()
        }
    }

    private fun maybeOpenTempoCoachSheet() {
        val tip = tempoCoachTip.value as? TempoCoachTip.NextSet ?: return
        if (!tip.rec.showApply || tip.rec.previewOnly) return
        val unit = weightUnit.value
        if (tip.rec.isApplied(
                weightKg = draft.value.weightKg,
                reps = draft.value.reps,
                rpe = draft.value.rpe,
                unit = unit,
            )
        ) {
            return
        }
        tempoCoachSheetVisible.value = true
    }

    /** Flushes the debounce tail: leaving must not drop the words typed in the last 400 ms. */
    fun persistDraftForExit() {
        if (draftCache.liveEntryLocked(sessionId)) return
        persistDraft()
        viewModelScope.launch { sessionNotes.writeNow() }
    }

    fun retryNotesSave() {
        if (draftCache.liveEntryLocked(sessionId)) return
        if (sessionNotes.beginRetry()) viewModelScope.launch { sessionNotes.retryNow() }
    }

    private val _notesExitBlocked = MutableStateFlow(false)
    val notesExitBlocked: StateFlow<Boolean> = _notesExitBlocked.asStateFlow()
    private var notesExitPending = false

    fun requestNotesExit(leaveWithDraft: Boolean = false) {
        if (terminalExit || _exitRequested.value != null || mutating.value || notesExitPending ||
            sessionId in draftCache.finishing.value
        ) return
        // A stale route can reopen a confirmed finished row. It has no live notes to flush,
        // and the completed cache marker must keep rejecting edits without trapping Back.
        if (sessionReader.observations.value.session?.isFinished == true || draftCache.liveEntryLocked(sessionId)) {
            _notesExitBlocked.value = false
            _exitRequested.value = WorkoutExit.Kept
            return
        }
        if (leaveWithDraft && sessionNotes.saveState.value.busy) return
        _notesExitBlocked.value = false
        notesExitPending = true
        // One exit intent owns the controls until its write settles. A later Finish/discard
        // cannot race the Kept navigation and cancel a queued terminal operation.
        mutating.value = true
        viewModelScope.launch {
            try {
                val recovery = privateDrafts.awaitOpened()
                if (!recovery.canEnter) {
                    // No fields were unlocked. Leave the unread checkpoint intact for a later attempt.
                    _exitRequested.value = WorkoutExit.Kept
                    return@launch
                }
                sessionReader.observations.value.session?.let { sessionNotes.sessionRead(it.notes) }
                val (nudge, checkpoint) = synchronized(entryWrites) {
                    writeDraftLocked() to privateCheckpoint()
                }
                if (nudge) privateDrafts.nudge()
                val preserved = durability == null || checkpoint != null && privateDrafts.flush(checkpoint)
                if (preserved && (leaveWithDraft || sessionNotes.writeNow())) _exitRequested.value = WorkoutExit.Kept
                else _notesExitBlocked.value = true
            } finally {
                notesExitPending = false
                mutating.value = false
            }
        }
    }

    fun keepEditingNotes() {
        _notesExitBlocked.value = false
    }

    private fun applyPrivateRecovery(restored: WorkoutDraftRestore.ReadyEntry) {
        if (restored.replaceLocal) primaryActivation.value = primaryActivation.value + 1
        val checkpoint = restored.entry
        if (restored.replaceLocal && checkpoint == null) {
            draftCache.clear(sessionId); savedDraft.clear(); savedEdit.clear()
            editingOriginal = null; editingSetId.value = null; selectedExerciseId.value = null
            numberEntries.value = emptyList(); sessionNotes.resetUnhydrated(); authoredRevision = 0
            saves.operation.value.command?.let(saves::clear)
        }
        if (checkpoint != null && (restored.replaceLocal || checkpoint.authoredRevision >= authoredRevision)) {
            authoredRevision = checkpoint.authoredRevision
            privateSavedState["workout.private.authorship.v1"] = authoredRevision
            draftCache.replaceAll(sessionId, checkpoint.lifts, checkpoint.selectedExerciseId)
            draftCache.rememberAuthoredRevision(sessionId, authoredRevision)
            savedDraft.clear()
            checkpoint.lifts.values.forEach { savedDraft.write(it, checkpoint.editingOriginal?.setId) }
            savedDraft.writeSelection(
                sessionId = sessionId, exerciseId = checkpoint.selectedExerciseId,
                notes = checkpoint.notes.orEmpty(), editingSetId = checkpoint.editingOriginal?.setId,
            )
            if (checkpoint.notes == null) {
                savedDraft.forgetUnhydratedNotes()
                sessionNotes.resetUnhydrated()
            } else {
                draftCache.putSessionNotes(sessionId, checkpoint.notes)
                sessionNotes.restore(value = checkpoint.notes, needsInspection = restored.notesNeedReview)
            }
            selectedExerciseId.value = checkpoint.selectedExerciseId
            checkpoint.selectedExerciseId?.let { checkpoint.lifts[it] }?.let { value ->
                pendingResumeDraft = value
                applyRecoveredDraft(value)
                liftReadiness.value = LiftEntryReadiness.READY
            }
            editingOriginal = checkpoint.editingOriginal
            checkpoint.clocks?.let { clocks.seed(it, workoutBoot) }
            numberEntries.value = checkpoint.numbers
            undo.restoreCheckpoint(sessionId, checkpoint.undo, checkpoint.undoDwellMs)
            draftCache.putEditingOriginal(sessionId, editingOriginal)
            editingSetId.value = editingOriginal?.setId
            editingOriginal?.let(savedEdit::write) ?: savedEdit.clear()
            val memoryPending = saves.operation.value.command
            if (memoryPending != null && checkpoint.acknowledgedCommand == durability?.commandIdentity(memoryPending)) {
                saves.clear(memoryPending)
            }
        } else if (restored.notesNeedReview && sessionNotes.textKnown) {
            sessionNotes.restore(value = sessionNotes.text.value, needsInspection = true)
        }
        restored.pending?.let { command ->
            saves.restorePrivate(command)
            if (command.editing && editingOriginal == null) {
                val original = command.copy(values = checkNotNull(command.original))
                editingOriginal = original
                draftCache.putEditingOriginal(sessionId, original)
                savedEdit.write(original)
            }
            restoreSaveDraft(command)
        }
    }

    private fun privateCheckpoint(): WorkoutEntryCheckpoint? {
        val current = sessionReader.observations.value.session ?: return null
        if (terminalExit || current.isFinished) return null
        return WorkoutEntryCheckpoint(
            sessionId = sessionId, startedAt = current.startedAt, authoredRevision = authoredRevision,
            lifts = draftCache.all(sessionId), selectedExerciseId = selectedExerciseId.value,
            notes = sessionNotes.text.value.takeIf { sessionNotes.textKnown }, notesBase = current.notes.trim(),
            editingOriginal = editingOriginal,
            clocks = clocks.checkpoint(workoutBoot).let { value ->
                val pendingRemoved = pendingRemovedLiftExerciseIds()
                value.copy(
                    stopwatches = value.stopwatches.filterKeys { id ->
                        current.exercises.any { it.exercise.id == id } || id in pendingRemoved
                    },
                )
            },
            numbers = numberEntries.value,
            undo = undo.checkpoint(), undoDwellMs = undo.dwellMs.value,
        )
    }

    /**
     * A keypad, set clock, or undo offer checkpoints without going through [editEntry].
     * Remembering the snapshot has to wake the writer too. Bumping the authorship number
     * and leaving the file behind makes the next open ignore that file.
     */
    private fun publishPrivateEntry() {
        val nudge = synchronized(entryWrites) { stagePrivateEntry() }
        if (nudge) privateDrafts.nudge()
    }

    /** Caller holds [entryWrites]. Returns whether the writer should be kicked after the lock. */
    private fun stagePrivateEntry(): Boolean {
        if (durability == null || !privateDrafts.recovery.value.canEnter) return false
        authoredRevision = Math.addExact(authoredRevision, 1L)
        privateSavedState["workout.private.authorship.v1"] = authoredRevision
        draftCache.rememberAuthoredRevision(sessionId, authoredRevision)
        val checkpoint = privateCheckpoint() ?: return false
        privateDrafts.accept(checkpoint)
        return true
    }

    internal fun keepPrivateEntry(): Boolean {
        if (privateDrafts.recovery.value.phase != WorkoutPrivatePhase.PRESERVATION_FAILED || !canChangeEntry()) return false
        val checkpoint = privateCheckpoint() ?: return false
        mutating.value = true
        viewModelScope.launch {
            try { privateDrafts.flush(checkpoint) }
            finally { mutating.value = false }
        }
        return true
    }

    internal fun discardConflictingEntry() {
        if (privateDrafts.recovery.value.phase !in setOf(WorkoutPrivatePhase.CONFLICT, WorkoutPrivatePhase.READ_FAILED) || mutating.value || saves.inFlight.value) return
        mutating.value = true
        viewModelScope.launch {
            try {
                durability?.discardEntry(sessionId)
                saves.operation.value.command?.let(saves::clear)
                draftCache.clear(sessionId)
                savedDraft.clear(); savedEdit.clear(); editingOriginal = null; editingSetId.value = null
                sessionNotes.resetUnhydrated(); selectedExerciseId.value = null; pendingResumeDraft = null
                numberEntries.value = emptyList(); clocks.stopHold(); clocks.discardStopwatch()
                authoredRevision = 0; draftDirty.value = false; liftReadiness.value = LiftEntryReadiness.NONE
                privateDrafts.reopenAfterDiscard()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { privateDrafts.discardFailed() }
            finally { mutating.value = false }
        }
    }

    private fun editEntry(mutate: () -> Unit) {
        val nudge = synchronized(entryWrites) {
            mutate()
            writeDraftLocked()
        }
        if (nudge) privateDrafts.nudge()
    }

    private fun persistDraft() {
        val nudge = synchronized(entryWrites) { writeDraftLocked() }
        if (nudge) privateDrafts.nudge()
    }

    /** Caller holds [entryWrites]. Weight, reps and effort are copied from one entry object. */
    private fun writeDraftLocked(): Boolean {
        if (sessionId.isBlank() || terminalExit || finished.value || session.value?.isFinished == true) return false
        if (durability?.ownsEntry(sessionId) == false) { privateDrafts.ownershipLost(); return false }
        val exerciseId = selectedExerciseId.value
        val fields = draft.value
        val dirty = draftDirty.value
        val canSeed = dirty ||
            liftReadiness.value.allowsCommit() ||
            editingSetId.value != null ||
            (exerciseId != null && draftCache.getLift(sessionId, exerciseId) != null)
        if (!canSeed) {
            // No entry to carry the notes yet, so they are staged on their own: a lift still
            // loading, or no lift selected at all, must not lose what was typed to an exit that
            // does not flush.
            draftCache.select(sessionId, exerciseId)
            draftCache.putSessionNotes(sessionId, sessionNotes.text.value)
            savedDraft.writeSelection(
                sessionId = sessionId,
                exerciseId = exerciseId,
                notes = sessionNotes.text.value,
                editingSetId = editingSetId.value,
            )
            return stagePrivateEntry()
        }
        val current = WorkoutDraft(
            sessionId = sessionId,
            exerciseId = exerciseId,
            weightKg = fields.weightKg,
            reps = fields.reps,
            rpe = fields.rpe,
            isWarmup = fields.isWarmup,
            notes = sessionNotes.text.value,
            durationSeconds = fields.durationSeconds,
            dirty = dirty,
            extraSetRequested = wantAnotherSet.value,
            effortSource = effortSource.value,
        )
        // The entry, and with it the session's notes (WorkoutDraftCache.sessionNotes).
        draftCache.put(current)
        // Written through to saved state so the numbers dialed in before a rest survive the
        // process being killed while the phone sits in a pocket. Packet C upserts this
        // lift without dropping the others.
        savedDraft.write(current, editingSetId.value)
        return stagePrivateEntry()
    }

    private data class WorkoutCore(
        val read: WorkoutSessionRead,
        val selected: String?,
        val draft: ActiveExerciseDraft,
        val hint: ProgressionHint?,
        val lastPerformance: ExerciseSessionSummary? = null,
    )

    private data class MicroRecCore(
        val session: WorkoutSession?,
        val selected: String?,
        val draft: ActiveExerciseDraft,
        val hint: ProgressionHint?,
        val wantAnother: Boolean,
        val lastPerformance: ExerciseSessionSummary? = null,
    )

    private data class MicroRecExtras(
        val editingSetId: String?,
        val lighterWeek: Boolean,
        val unit: WeightUnit,
        val coachPrefs: CoachPreferences,
        val draftRpeIntent: Boolean,
    )

    /** Which load the hint on screen came from, by [prefillGeneration], and what it was read under (W2e). */
    private data class HintStamp(val exerciseId: String, val generation: Int, val settings: HintSettings)

    /**
     * The rest the dock will start. [chosenFor] is the lift someone picked it on — on the dock,
     * or on the rest page through the shared last preset — and that lift's late prefill seed
     * leaves it alone. Null when it is a seed or the default.
     */
    private data class PlannedRest(val seconds: Int, val sourceTag: String, val chosenFor: String?)

    private data class WorkoutExtras(
        val restTotal: Int,
        val query: String,
        val showPicker: Boolean,
        val notes: String,
        val notesSave: NotesSaveState,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )

    private data class EditorMeta(
        val notes: String,
        val notesSave: NotesSaveState,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )

    private data class TempoCoachFramePart1(
        val key: CoachKey,
        val session: WorkoutSession?,
        val selectedExerciseId: String?,
        val history: List<WorkoutSession>,
    )

    private data class TempoCoachFrame(
        val key: CoachKey,
        val session: WorkoutSession?,
        val selectedExerciseId: String?,
        val history: List<WorkoutSession>,
        val schedule: SchedulePreferences,
        val block: TrainingBlock?,
    )

    private data class TempoCoachDismissState(
        val hints: HintSettings?,
        val tempoDismissed: Boolean,
        val addASetDismissed: Set<String>,
        val addASetAccepted: Set<String>,
    )
}
