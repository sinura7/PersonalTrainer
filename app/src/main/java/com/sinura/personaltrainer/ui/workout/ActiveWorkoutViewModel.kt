package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.ui.library.DUPLICATE_NAME_MESSAGE
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.FloorTimerCue
import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.PendingLiftSwitch
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.SetStopwatchWork
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.LogCommitFeedback
import com.sinura.personaltrainer.domain.LogReceipt
import com.sinura.personaltrainer.domain.LogReceiptCopy
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestPrescription
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SessionEditRules
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.CurrentLiftCopy
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
private const val TIMED_TICK_MS = 250L

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

/** A typing pause, not a keystroke, is what commits notes to the database. */
private const val NOTES_WRITE_DEBOUNCE_MS = 400L

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
    val error: String? = null,
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
) {
    val isLoading: Boolean get() = loadState == SessionLoadState.LOADING

    val entryLocked: Boolean get() = save.pending || logging || mutating || loadState != SessionLoadState.FOUND

    val canLog: Boolean
        get() = loadState == SessionLoadState.FOUND && session != null &&
            selectedExerciseId != null &&
            !entryLocked &&
            (liftReadiness.allowsCommit() || draftDirty)

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

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
    private val undoTimeout: UndoTimeoutProvider = systemUndoTimeout(application),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val draftCache = container.workoutDraftCache
    private val savedDraft = SavedStateWorkoutDraft(savedStateHandle)
    private val savedTimer = SavedStateFloorTimer(savedStateHandle)
    private val savedSave = SavedStateWorkoutSave(savedStateHandle)
    private val savedEdit = SavedStateWorkoutSave(handle = savedStateHandle, storageKey = "workout.editOriginal.v1")
    private var editingOriginal = draftCache.editingOriginal(sessionId) ?: savedEdit.read(sessionId)
    private val mutating = MutableStateFlow(false)
    private val restoredSave = draftCache.pendingSave(sessionId) ?: savedSave.read(sessionId)
    private val saveOperation = MutableStateFlow(WorkoutSaveState(
        phase = if (restoredSave == null) WorkoutSavePhase.IDLE else WorkoutSavePhase.CHECKING,
        command = restoredSave,
    ))

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(ActiveExerciseDraft())
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val lastPerformance = MutableStateFlow<ExerciseSessionSummary?>(null)

    /**
     * Every finished working set of the selected lift from other sessions, so the floor's
     * Best set is judged against the whole log the way History's records are. Loaded with
     * the prefill and cleared with it; today's rows are added at read time, never here.
     */
    private val priorHistory = MutableStateFlow<List<ExerciseSetRecord>>(emptyList())
    val exerciseHistory: StateFlow<List<ExerciseSetRecord>> = priorHistory.asStateFlow()
    private val lighterWeek = MutableStateFlow(false)
    private val restTotal = MutableStateFlow(PlannedRest(seconds = 90, chosenFor = null))
    private val searchQuery = MutableStateFlow("")
    private val showPicker = MutableStateFlow(false)

    /**
     * The session-exercise row the picker is about to replace, or null when it is adding.
     *
     * Held here rather than passed through the picker so the sheet stays a plain "choose a
     * lift" surface; what happens to the choice is this class's business.
     */
    private val swapTargetItemId = MutableStateFlow<String?>(null)
    private val notes = MutableStateFlow("")
    private val error = ErrorSlot()
    private val finished = MutableStateFlow(false)
    private val editingSetId = MutableStateFlow<String?>(null)
    private val logging = MutableStateFlow(false)
    private val liftReadiness = MutableStateFlow(LiftEntryReadiness.NONE)
    private val suggestionUnavailable = MutableStateFlow(false)
    private val draftDirty = MutableStateFlow(false)
    private var prefillGeneration = 0
    private val wantAnotherSet = MutableStateFlow(false)

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

    /** What the database already holds, so a re-seed or a no-op edit does not re-write it. */
    private var lastPersistedNotes: String? = null
    private var notesHydrated = false
    private var pendingResumeDraft: WorkoutDraft? = null
    /** Blocks a late Room emission from recreating a draft after finish/discard cleared it. */
    private var terminalExit = false

    private val restTimer = container.restTimerController
    private val _holdTimer = MutableStateFlow(HoldTimerUiState())
    val holdTimer: StateFlow<HoldTimerUiState> = _holdTimer.asStateFlow()
    private var holdJob: Job? = null
    private val _setStopwatch = MutableStateFlow(SetStopwatchUiState())
    val setStopwatch: StateFlow<SetStopwatchUiState> = _setStopwatch.asStateFlow()
    private var setStopwatchJob: Job? = null
    private var timedGeneration = 0
    private val timedActionGeneration = MutableStateFlow(0)
    private val primaryActivation = MutableStateFlow(0L)
    private var lastPrimaryTap: Long? = null
    private var pendingRestJob: Job? = null
    private val _floorTimerCue = MutableSharedFlow<FloorTimerCue>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val floorTimerCue: SharedFlow<FloorTimerCue> = _floorTimerCue.asSharedFlow()
    private val _pendingLiftSwitch = MutableStateFlow<PendingLiftSwitch?>(null)
    val pendingLiftSwitch: StateFlow<PendingLiftSwitch?> = _pendingLiftSwitch.asStateFlow()

    /** The read outcome remains distinct from a successful query with no row. */
    private val sessionReader = WorkoutSessionReader(container.workoutRepository, sessionId, viewModelScope)

    /** The rest commands and the history reads this screen shares with the rest page. */
    private val restCommands = RestCommands(container, viewModelScope, sessionId)
    private val hintLoader = ProgressionHintLoader(container, sessionId)

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
        val selectedDraft = selectedId?.let { recovered[it] }
            ?: WorkoutDraftRecovery.resolve(
                sessionId = sessionId,
                inMemory = draftCache.get(sessionId),
                persisted = savedDraft.read(sessionId),
            )
        selectedDraft?.let { cached ->
            pendingResumeDraft = cached
            applyRecoveredDraft(cached)
            notes.value = cached.notes.ifEmpty { savedDraft.sessionNotes() }
            liftReadiness.value = LiftEntryReadiness.READY
        } ?: run {
            val savedNotes = savedDraft.sessionNotes()
            if (savedNotes.isNotEmpty()) notes.value = savedNotes
        }
        savedDraft.editingSetId()?.let { editingSetId.value = it }
        editingOriginal?.let { original ->
            editingSetId.value = original.setId
            savedEdit.write(original)
            draftCache.putEditingOriginal(sessionId, original)
        }
        restoredSave?.let { command ->
            draftCache.putPendingSave(command)
            savedSave.write(command)
            restoreSaveDraft(command)
        }
        restoreTimedWork(selectedExerciseId.value)
        viewModelScope.launch {
            // The flow is guarded at the repository, but the body below is not — a failure
            // here would otherwise kill the collector and freeze the screen silently.
            runCatchingCancellable {
                session.collect { current ->
                    if (current == null) return@collect
                    val resolved = current.resolveSelectedExerciseId(selectedExerciseId.value)
                    if (!saveOperation.value.pending && resolved != selectedExerciseId.value) {
                        if (resolved == null) {
                            clearLiftSelection()
                        } else {
                            applySelection(resolved)
                        }
                    }
                    lastPersistedNotes = current.notes
                    // Hydrate once. "Field is empty" cannot tell not-yet-seeded from
                    // deliberately-cleared, and re-seeding on a later emission restored
                    // notes the user had just deleted mid-debounce.
                    if (!notesHydrated) {
                        notesHydrated = true
                        if (notes.value.isEmpty() && current.notes.isNotEmpty()) {
                            notes.value = current.notes
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
        viewModelScope.launch {
            selectedExerciseId.filterNotNull().distinctUntilChanged()
                .collectLatest { exerciseId ->
                    runCatchingCancellable { prefill(exerciseId) }
                        .onFailure { AppLog.w(TAG, "Prefilling the next set failed", it) }
                }
        }
        // Notes used to launch an independent write per keystroke. Room's writes are not
        // ordered against each other, so a shorter earlier string could land after a longer
        // later one and the user's last characters would silently disappear on the next read
        // — while a paragraph of notes cost a database write per character.
        //
        // collectLatest cancels the pending delay on every keystroke, so only a typing pause
        // writes; and because it awaits the previous block's cancellation before starting the
        // next, the writes it does perform are strictly ordered. NonCancellable means a write
        // that has already begun finishes rather than being torn in half by the next keystroke.
        viewModelScope.launch {
            notes.collectLatest { value ->
                delay(NOTES_WRITE_DEBOUNCE_MS)
                writeNotes(value)
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
                        PlannedRest(seconds = last, chosenFor = selectedExerciseId.value)
                    }
                }
            }
        }
    }

    private suspend fun writeNotes(value: String) {
        if (sessionId.isBlank()) return
        // Null means the session row has not been read yet, so what is on disk is unknown.
        // Writing here would push the empty initial value over real notes whenever the first
        // query took longer than the debounce — exactly the case on a cold start.
        val known = lastPersistedNotes ?: return
        if (value == known) return
        withContext(NonCancellable) {
            runCatchingCancellable {
                container.workoutRepository.updateSessionNotes(sessionId, value)
                lastPersistedNotes = value
            }.onFailure { thrown ->
                AppLog.w(TAG, "Writing the session notes failed", thrown)
                // Notes stay in the draft cache, and the next keystroke retries.
            }
        }
    }

    /** The dock's rest card. The shared builder is cold; this screen's sharing is its own. */
    val restTimerState: StateFlow<RestTimerUiState> =
        restCommands.restState(restTotal) { it.seconds }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RestTimerUiState(),
        )

    /**
     * In-set next load. Recomputed on log, RPE, warmup, lift switch, delete/undo, and edit.
     * Stepper ticks do not change it unless draft RPE is set (preview): the coach is asked again
     * only when what it reads changes ([CoachKey]), so a step, a note or a set of another lift
     * leaves this call as it was (W2c, audit C-2). Plain `map`, no dispatch: [applyMicroRec] and
     * the rest after a log read `.value` at once.
     * Eager: [applyMicroRec] reads this value, not a rendered snapshot.
     */
    val microRec: StateFlow<SetMicroRec?> = combine(
        combine(session, selectedExerciseId, draft, hint, wantAnotherSet) {
                current, selected, currentDraft, currentHint, extra ->
            MicroRecCore(current, selected, currentDraft, currentHint, extra)
        }.combine(lastPerformance) { core, last ->
            core.copy(lastPerformance = last)
        },
        combine(
            editingSetId,
            lighterWeek,
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.coachPreferences,
        ) { editing, lighter, unit, coach ->
            MicroRecExtras(editingSetId = editing, lighterWeek = lighter, unit = unit, coachPrefs = coach)
        },
    ) { core, extras ->
        // The rest page asks the same question from what this screen leaves in the draft
        // cache (W2b-4); every input is named, so neither side can drop one.
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
            // The goal set in Settings reaches the floor's coach (audit C-1); it was DEFAULT.
            coachPrefs = extras.coachPrefs,
        ).coachKey()
    }.distinctUntilChanged()
        .map { key -> key.rec(nowMs = time.nowMillis(), todayEpochDay = todayEpochDay()) }
        .stateIn(
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
            combine(notes, error.messages, finished, editingSetId) { sessionNotes, err, done, editing ->
                EditorMeta(sessionNotes, err, done, editing)
            },
        ) { first, second ->
            WorkoutExtras(
                restTotal = first.first.seconds,
                query = first.second,
                showPicker = first.third,
                notes = second.notes,
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
    }.combine(mutating) { state, busy ->
        state.copy(mutating = busy)
    }.combine(saveOperation) { state, operation ->
        state.copy(
            save = operation, error = operation.message ?: state.error,
            selectedExerciseId = operation.command?.exerciseId ?: state.selectedExerciseId,
        )
    }.combine(logging) { state, busy ->
        state.copy(logging = busy)
    }.combine(liftReadiness) { state, readiness ->
        state.copy(liftReadiness = readiness)
    }.combine(suggestionUnavailable) { state, missing ->
        state.copy(suggestionUnavailable = missing)
    }.combine(draftDirty) { state, dirty ->
        state.copy(draftDirty = dirty)
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
        val keepDraft = resumingThisLift || editingSetId.value != null || saveOperation.value.pending
        if (!keepDraft) {
            liftReadiness.value = LiftEntryReadiness.RESOLVING
            suggestionUnavailable.value = false
        } else if (liftReadiness.value == LiftEntryReadiness.NONE) {
            liftReadiness.value = LiftEntryReadiness.READY
        }
        lastPerformance.value = null
        priorHistory.value = emptyList()
        hint.value = null
        val current = sessionReader.observations.first {
            it.loadState == SessionLoadState.FOUND || it.loadState == SessionLoadState.MISSING
        }.session ?: return
        if (!isCurrentPrefill(exerciseId, generation)) return
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val hold = planned?.exercise?.let { HoldWork.isHold(it) } == true
        val targetReps = planned?.targetReps ?: 5
        try {
            val restPrefs = container.preferencesRepository.restTimerPreferences.first()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val thisWeek = hintLoader.thisWeekStart()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val lighter = hintLoader.isLighterWeek(thisWeek)
            if (!isCurrentPrefill(exerciseId, generation)) return
            lighterWeek.value = lighter
            val progression = hintLoader.progression(exerciseId, planned, lighter)
            if (!isCurrentPrefill(exerciseId, generation)) return
            hint.value = progression
            lastPerformance.value = hintLoader.lastPerformance(exerciseId)
            if (!isCurrentPrefill(exerciseId, generation)) return
            priorHistory.value = container.workoutRepository
                .historyBefore(sessionId, listOf(exerciseId))[exerciseId].orEmpty()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val seededRest = RestTimer.secondsToStart(
                planned?.restSeconds,
                restPrefs,
                prescribedSeconds = workoutMicroRec(
                    session = current,
                    selectedExerciseId = exerciseId,
                    draft = draft.value,
                    hint = progression,
                    editingSetId = editingSetId.value,
                    lighterWeek = lighter,
                    unit = container.preferencesRepository.weightUnit.first(),
                    nowMs = time.nowMillis(),
                    todayEpochDay = todayEpochDay(),
                    historySets = lastPerformance.value?.sets.orEmpty(),
                    coachPrefs = container.preferencesRepository.coachPreferences.first(),
                )?.restSeconds,
            )
            if (!isCurrentPrefill(exerciseId, generation)) return
            // The seed fills in this lift's plan; it never replaces a length picked for this
            // lift while the reads above were running. A pick made on another lift does not
            // count, so switching lifts still brings the new lift's own rest.
            restTotal.update { plan ->
                if (plan.chosenFor == exerciseId) plan else PlannedRest(seconds = seededRest, chosenFor = null)
            }
            if (keepDraft || draftDirty.value || saveOperation.value.pending) {
                liftReadiness.value = LiftEntryReadiness.READY
                suggestionUnavailable.value = false
                persistDraft()
                return
            }
            val lastWeight = progression?.suggestedWeightKg
                ?: planned?.targetWeightKg
                ?: 0.0
            draft.value = ActiveExerciseDraft(
                weightKg = lastWeight,
                reps = if (hold) 0 else targetReps.coerceAtLeast(1),
                rpe = null,
                isWarmup = false,
                durationSeconds = if (hold) HoldWork.countdownSeconds(planned?.targetSeconds) else null,
            )
            liftReadiness.value = LiftEntryReadiness.READY
            suggestionUnavailable.value = false
            persistDraft()
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Prefilling the next set failed", thrown)
            if (!isCurrentPrefill(exerciseId, generation)) return
            val livePlanned = session.value?.exercises
                ?.firstOrNull { it.exercise.id == exerciseId }
                ?: planned
            if (!saveOperation.value.pending && !draftDirty.value && (!keepDraft || draft.value.weightKg <= 0.0)) {
                draft.value = ActiveExerciseDraft(
                    weightKg = livePlanned?.targetWeightKg ?: 0.0,
                    reps = if (hold) 0 else targetReps.coerceAtLeast(1),
                    rpe = null,
                    isWarmup = false,
                    durationSeconds = if (hold) {
                        HoldWork.countdownSeconds(livePlanned?.targetSeconds)
                    } else {
                        null
                    },
                )
                persistDraft()
            }
            liftReadiness.value = LiftEntryReadiness.DEGRADED
            suggestionUnavailable.value = true
        }
    }

    private fun isCurrentPrefill(exerciseId: String, generation: Int): Boolean =
        selectedExerciseId.value == exerciseId && prefillGeneration == generation

    private fun applySelection(exerciseId: String) {
        if (selectedExerciseId.value == exerciseId) return
        persistDraft()
        editingSetId.value = null
        clearEditingOriginal()
        persistStopwatchFor(selectedExerciseId.value)
        wantAnotherSet.value = false
        _pendingLiftSwitch.value = null
        stopHoldTimer()
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
            liftReadiness.value = LiftEntryReadiness.RESOLVING
        }
        prefillGeneration++
        selectedExerciseId.value = exerciseId
        draftCache.select(sessionId, exerciseId)
        savedDraft.writeSelection(
            sessionId = sessionId,
            exerciseId = exerciseId,
            notes = notes.value,
            editingSetId = editingSetId.value,
        )
        restoreStopwatchFor(exerciseId, resumeRunning = false)
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
    }

    private fun clearLiftSelection() {
        persistStopwatchFor(selectedExerciseId.value)
        stopHoldTimer()
        bumpTimedGeneration()
        _setStopwatch.value = SetStopwatchUiState()
        draftDirty.value = false
        suggestionUnavailable.value = false
        liftReadiness.value = LiftEntryReadiness.NONE
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
        if (_setStopwatch.value.running || _holdTimer.value.running) {
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

    fun requestExtraSet() {
        if (!canChangeEntry()) return
        wantAnotherSet.value = true
        persistDraft()
    }

    fun setWeight(weightKg: Double) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
        markDraftDirty()
        persistDraft()
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
        draft.value = draft.value.copy(reps = reps.coerceAtLeast(1))
        markDraftDirty()
        persistDraft()
    }

    fun setHoldSeconds(seconds: Int) {
        if (!canChangeEntry()) return
        draft.value = draft.value.copy(
            durationSeconds = HoldWork.countdownSeconds(seconds),
            reps = 0,
        )
        markDraftDirty()
        persistDraft()
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
        if (_holdTimer.value.running) return
        val total = HoldWork.countdownSeconds(
            draft.value.durationSeconds ?: selected.targetSeconds,
        )
        restTimer.stop()
        persistStopwatchFor(exerciseId)
        clearSetStopwatch()
        bumpTimedGeneration()
        val gen = timedGeneration
        val now = elapsedNow()
        val deadline = HoldWork.deadlineElapsedRealtime(now, total)
        _holdTimer.value = HoldTimerUiState(
            running = true,
            remainingSeconds = total,
            totalSeconds = total,
            elapsedSeconds = 0,
            startElapsedRealtime = now,
            deadlineElapsedRealtime = deadline,
            targetReached = false,
        )
        persistHold()
        _floorTimerCue.tryEmit(FloorTimerCue.HoldStarted)
        holdJob = viewModelScope.launch { runHoldTicker(gen) }
    }

    private suspend fun runHoldTicker(generation: Int) {
        while (generation == timedGeneration) {
            val hold = _holdTimer.value
            if (!hold.running) return
            val now = elapsedNow()
            if (now < hold.startElapsedRealtime) {
                stopHoldTimer()
                return
            }
            val remaining = HoldWork.remainingFromDeadline(
                deadlineElapsedRealtime = hold.deadlineElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val elapsed = HoldWork.elapsedFromRealtime(
                startElapsedRealtime = hold.startElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            if (remaining <= 0) {
                _holdTimer.value = hold.copy(
                    running = false,
                    remainingSeconds = 0,
                    elapsedSeconds = hold.totalSeconds,
                    targetReached = true,
                )
                persistHold()
                val sound = container.preferencesRepository.restTimerPreferences.first().soundEnabled
                _floorTimerCue.tryEmit(FloorTimerCue.HoldTarget(soundEnabled = sound))
                return
            }
            _holdTimer.value = hold.copy(
                remainingSeconds = remaining,
                elapsedSeconds = elapsed,
            )
            delay(TIMED_TICK_MS)
        }
    }

    private fun stopHoldTimer() {
        bumpHoldJob()
        _holdTimer.value = HoldTimerUiState()
        savedTimer.clearHold()
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
        if (_setStopwatch.value.running) return
        restTimer.stop()
        stopHoldTimer()
        bumpTimedGeneration()
        val gen = timedGeneration
        val already = _setStopwatch.value.elapsedSeconds.coerceAtLeast(0)
        val now = elapsedNow()
        _setStopwatch.value = SetStopwatchUiState(
            running = true,
            elapsedSeconds = already,
            used = true,
            startElapsedRealtime = now,
            frozenElapsedSeconds = already,
            exerciseId = exerciseId,
        )
        persistStopwatchFor(exerciseId)
        _floorTimerCue.tryEmit(FloorTimerCue.StopwatchStarted)
        setStopwatchJob = viewModelScope.launch { runStopwatchTicker(gen) }
    }

    private suspend fun runStopwatchTicker(generation: Int) {
        while (generation == timedGeneration) {
            val watch = _setStopwatch.value
            if (!watch.running) return
            val now = elapsedNow()
            val elapsed = SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = watch.startElapsedRealtime,
                frozenElapsedSeconds = watch.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
            _setStopwatch.value = watch.copy(elapsedSeconds = elapsed)
            if (elapsed >= HoldWork.MAX_SECONDS) {
                stopSetStopwatch()
                return
            }
            delay(TIMED_TICK_MS)
        }
    }

    fun stopSetStopwatch() {
        val current = _setStopwatch.value
        if (!current.used && !current.running) return
        bumpStopwatchJob()
        val now = elapsedNow()
        val elapsed = if (current.running) {
            SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = current.startElapsedRealtime,
                frozenElapsedSeconds = current.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
        } else {
            current.elapsedSeconds
        }
        _setStopwatch.value = current.copy(
            running = false,
            elapsedSeconds = elapsed,
            frozenElapsedSeconds = elapsed,
            startElapsedRealtime = 0L,
        )
        persistStopwatchFor(selectedExerciseId.value)
        if (current.running) {
            _floorTimerCue.tryEmit(FloorTimerCue.StopwatchStopped)
        }
    }

    private fun clearSetStopwatch() {
        bumpStopwatchJob()
        val id = _setStopwatch.value.exerciseId ?: selectedExerciseId.value
        _setStopwatch.value = SetStopwatchUiState()
        id?.let { savedTimer.clearStopwatch(it) }
    }

    private fun elapsedNow(): Long = time.elapsedRealtimeMillis()

    private fun bumpTimedGeneration() {
        timedGeneration++
        timedActionGeneration.value = timedGeneration
        holdJob?.cancel()
        holdJob = null
        setStopwatchJob?.cancel()
        setStopwatchJob = null
        cancelPendingRest()
    }

    private fun cancelPendingRest() {
        pendingRestJob?.cancel()
        pendingRestJob = null
    }

    private fun bumpHoldJob() {
        holdJob?.cancel()
        holdJob = null
    }

    private fun bumpStopwatchJob() {
        setStopwatchJob?.cancel()
        setStopwatchJob = null
    }

    private fun persistHold() {
        savedTimer.writeHold(selectedExerciseId.value, _holdTimer.value)
    }

    private fun persistStopwatchFor(exerciseId: String?) {
        if (exerciseId.isNullOrBlank()) return
        val watch = _setStopwatch.value
        if (!watch.used && !watch.running) {
            savedTimer.clearStopwatch(exerciseId)
            return
        }
        savedTimer.writeStopwatch(exerciseId, watch.copy(exerciseId = exerciseId))
    }

    private fun restoreTimedWork(exerciseId: String?) {
        val now = elapsedNow()
        val hold = savedTimer.readHold(exerciseId, now)
        if (hold != null) {
            val remaining = HoldWork.remainingFromDeadline(
                deadlineElapsedRealtime = hold.deadlineElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val elapsed = HoldWork.elapsedFromRealtime(
                startElapsedRealtime = hold.startElapsedRealtime,
                nowElapsedRealtime = now,
                totalSeconds = hold.totalSeconds,
            )
            val reached = hold.targetReached || remaining <= 0
            _holdTimer.value = hold.copy(
                running = hold.running && !reached,
                remainingSeconds = if (reached) 0 else remaining,
                elapsedSeconds = if (reached) hold.totalSeconds else elapsed,
                targetReached = reached,
            )
            if (_holdTimer.value.running) {
                bumpTimedGeneration()
                val gen = timedGeneration
                holdJob = viewModelScope.launch { runHoldTicker(gen) }
            }
        }
        if (exerciseId != null) {
            restoreStopwatchFor(
                exerciseId = exerciseId,
                resumeRunning = !_holdTimer.value.running,
            )
        }
        if (_holdTimer.value.running || _setStopwatch.value.running) {
            restTimer.stop()
        }
    }

    private fun restoreStopwatchFor(exerciseId: String, resumeRunning: Boolean) {
        bumpStopwatchJob()
        val now = elapsedNow()
        val stored = savedTimer.readStopwatch(exerciseId, now)
        if (stored == null || !stored.used) {
            _setStopwatch.value = SetStopwatchUiState(exerciseId = exerciseId)
            return
        }
        val live = if (stored.running && resumeRunning) {
            SetStopwatchWork.elapsedFromRealtime(
                startElapsedRealtime = stored.startElapsedRealtime,
                frozenElapsedSeconds = stored.frozenElapsedSeconds,
                nowElapsedRealtime = now,
            )
        } else {
            stored.elapsedSeconds.coerceAtLeast(stored.frozenElapsedSeconds)
        }
        val running = stored.running && resumeRunning && live < HoldWork.MAX_SECONDS
        _setStopwatch.value = stored.copy(
            running = running,
            elapsedSeconds = live,
            frozenElapsedSeconds = if (running) stored.frozenElapsedSeconds else live,
            startElapsedRealtime = if (running) stored.startElapsedRealtime else 0L,
            exerciseId = exerciseId,
        )
        if (running) {
            bumpTimedGeneration()
            val gen = timedGeneration
            setStopwatchJob = viewModelScope.launch { runStopwatchTicker(gen) }
        }
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
        val current = draft.value
        draft.value = current.copy(rpe = rpe)
        markDraftDirty()
        persistDraft()
    }

    fun setWarmup(isWarmup: Boolean) {
        if (!canChangeEntry()) return
        val current = draft.value
        draft.value = current.copy(
            isWarmup = isWarmup,
            rpe = if (isWarmup) null else current.rpe,
        )
        markDraftDirty()
        persistDraft()
    }

    /**
     * Packet D: one ramp chip writes the lighter weight and Warm-up.
     * It does not log. RPE is cleared so a working-set effort cannot
     * sit on a warm-up draft.
     */
    fun applyWarmupRamp(weightKg: Double) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite() || weightKg <= 0.0) return
        draft.value = draft.value.copy(
            weightKg = weightKg,
            isWarmup = true,
            rpe = null,
        )
        markDraftDirty()
        persistDraft()
    }

    fun setNotes(value: String) {
        notes.value = value
        persistDraft()
        // The database write is not launched here. See the debounce collector in init.
    }

    fun setPickerVisible(visible: Boolean) {
        if (visible && !canChangeEntry()) return
        if (!visible) swapTargetItemId.value = null
        showPicker.value = visible
        if (!visible) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun addExercise(exercise: Exercise) {
        if (!canChangeEntry()) return
        launchEntryMutation(source = ERR_ADD_LIFT) {
            addExerciseInternal(exercise)
        }
    }

    fun createAndAddExercise(
        name: String,
        muscleGroup: String,
        loadType: LoadType = LoadType.EXTERNAL,
    ) {
        if (!canChangeEntry()) return
        launchEntryMutation(source = ERR_ADD_LIFT) {
            if (name.isBlank()) {
                error.fail(source = ERR_ADD_LIFT, message = "Give that lift a name.")
                return@launchEntryMutation
            }
            try {
                when (val result = container.exerciseRepository.createCustom(
                    name,
                    muscleGroup,
                    loadType = loadType,
                )) {
                    is SaveExerciseResult.DuplicateName ->
                        error.fail(source = ERR_ADD_LIFT, message = DUPLICATE_NAME_MESSAGE)
                    is SaveExerciseResult.MissingMuscle ->
                        error.fail(source = ERR_ADD_LIFT, message = MuscleGroups.MISSING_MESSAGE)
                    is SaveExerciseResult.Saved -> addExerciseInternal(result.exercise)
                }
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "createAndAddExercise failed", thrown)
                error.fail(source = ERR_ADD_LIFT, message = SessionOrderCopy.CREATE_LIFT_FAILED)
            }
        }
    }

    fun requestSwap() {
        if (!canChangeEntry()) return
        val selectedId = selectedExerciseId.value ?: return
        val item = session.value?.exercises?.firstOrNull { it.exercise.id == selectedId } ?: return
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
                savedDraft.removeLift(selectedId)
                clearLiftSelection()
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

    private suspend fun addExerciseInternal(exercise: Exercise) {
        val started = error.mark()
        if (sessionId.isBlank()) {
            error.fail(source = ERR_ADD_LIFT, message = "This workout is no longer available.")
            return
        }
        swapTargetItemId.value?.let { itemId ->
            swapTargetItemId.value = null
            try {
                container.workoutRepository.swapExerciseInSession(sessionId, itemId, exercise)
                selectExerciseInternal(exercise.id)
                showPicker.value = false
                error.clearFrom(source = ERR_ADD_LIFT, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "swapExerciseInSession failed", thrown)
                error.fail(
                    source = ERR_ADD_LIFT,
                    message = thrown.message?.takeIf {
                        SetLogRules.isUserMessage(it) || SessionEditRules.isUserMessage(it)
                    } ?: "Could not swap that lift. Try again.",
                )
            }
            return
        }
        val alreadyAdded = session.value?.exercises?.any { it.exercise.id == exercise.id } == true
        if (alreadyAdded) {
            selectExerciseInternal(exercise.id)
            showPicker.value = false
            error.clearFrom(source = ERR_ADD_LIFT, before = started)
            return
        }
        try {
            val goal = container.preferencesRepository.coachPreferences.first().goal
            val defaults = AddDefaults.forExercise(exercise, goal = goal)
            container.workoutRepository.addExerciseToSession(
                sessionId = sessionId,
                exercise = exercise,
                targetSets = defaults.sets,
                targetReps = defaults.reps,
                targetWeightKg = null,
                restSeconds = defaults.restSeconds,
                targetSeconds = defaults.seconds,
                targetSecondsMax = defaults.secondsMax,
            )
            selectExerciseInternal(exercise.id)
            showPicker.value = false
            error.clearFrom(source = ERR_ADD_LIFT, before = started)
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "addExerciseInternal failed", thrown)
            error.fail(source = ERR_ADD_LIFT, message = "Could not add that lift. Try again.")
        }
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
        restoredSave?.let { command ->
            viewModelScope.launch {
                sessionReader.observations.first { it.loadState != SessionLoadState.LOADING }
                reconcileSave(command, retryWrite = false)
            }
        }
    }

    private fun canChangeEntry(): Boolean =
        !terminalExit && !logging.value && !mutating.value && !saveOperation.value.pending &&
            sessionReader.observations.value.loadState == SessionLoadState.FOUND &&
            sessionReader.observations.value.session?.isFinished == false

    private fun launchEntryMutation(source: String, block: suspend () -> Unit) {
        if (!canChangeEntry()) return
        mutating.value = true
        viewModelScope.launch {
            try {
                block()
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
        combine(_holdTimer, _setStopwatch, timedActionGeneration) { hold, stopwatch, generation ->
            Triple(hold, stopwatch, generation)
        },
        primaryActivation,
    ) { state, extra, timing, activation ->
        WorkoutPrimaryActions.derive(state, extra, timing.first, timing.second, timing.third, activation)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, currentPrimaryAction())

    private fun currentPrimaryAction(): WorkoutPrimaryAction {
        val read = sessionReader.observations.value
        return WorkoutPrimaryActions.derive(
            state = ActiveWorkoutUiState(
                loadState = read.loadState, session = read.session,
                selectedExerciseId = selectedExerciseId.value, draft = draft.value,
                editingSetId = editingSetId.value, logging = logging.value,
                mutating = mutating.value, save = saveOperation.value,
                liftReadiness = liftReadiness.value, draftDirty = draftDirty.value,
            ),
            extraSet = wantAnotherSet.value, hold = _holdTimer.value, stopwatch = _setStopwatch.value,
            timedGeneration = timedActionGeneration.value, activation = primaryActivation.value,
        )
    }

    /** Validate the rendered action; a second Log tap cannot become Next after the save. */
    fun performPrimary(action: WorkoutPrimaryAction): Boolean {
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
            WorkoutPrimaryKind.UNAVAILABLE, WorkoutPrimaryKind.CHECKING, WorkoutPrimaryKind.SAVING, WorkoutPrimaryKind.UPDATING -> return false
        }
        return true
    }

    /** No production caller: the floor logs through [performPrimary]. Tests drive a plain log. */
    @VisibleForTesting
    internal fun logSet() = logSetWithDuration(displayedDurationSeconds = null, freezeDisplayedDuration = false)

    private fun logSetWithDuration(displayedDurationSeconds: Int?, freezeDisplayedDuration: Boolean) {
        if (!canChangeEntry()) return
        val exerciseId = selectedExerciseId.value ?: return
        if (!(liftReadiness.value.allowsCommit() || draftDirty.value)) return
        val current = draft.value
        val selectedLift = session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId } ?: return
        val hold = HoldWork.isHold(selectedLift.exercise)
        val holdState = _holdTimer.value
        val editingId = editingSetId.value
        if (hold && editingId == null && holdState.totalSeconds == 0 && !holdState.running) {
            startHoldSet()
            return
        }
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
            stopwatch = _setStopwatch.value,
            existingDurationSeconds = current.durationSeconds.takeUnless { hold },
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
        val command = WorkoutSetSave(
            sessionId = sessionId, exerciseId = exerciseId,
            setId = original?.setId ?: IdFactory.Uuid.newId(),
            completedAt = original?.completedAt ?: time.nowMillis(),
            values = values, original = original?.values,
        )
        // Freeze identity and payload synchronously, before Room or any coroutine suspension.
        draftCache.putPendingSave(command)
        savedSave.write(command)
        saveOperation.value = WorkoutSaveState(WorkoutSavePhase.SAVING, command)
        logging.value = true
        viewModelScope.launch { persistSet(command) }
    }

    /** Retry belongs to the frozen command, never to today's editable draft or timer. */
    fun retrySave() {
        val operation = saveOperation.value
        val command = operation.command ?: return
        if (operation.busy || operation.phase == WorkoutSavePhase.CONFLICT) return
        saveOperation.value = operation.copy(phase = WorkoutSavePhase.CHECKING, message = null)
        logging.value = true
        viewModelScope.launch { reconcileSave(command, retryWrite = true) }
    }

    /** Inspect before releasing a failed operation for editing; an unknown outcome stays owned. */
    fun editFailedSave() {
        val operation = saveOperation.value
        val command = operation.command ?: return
        if (operation.busy) return
        saveOperation.value = operation.copy(phase = WorkoutSavePhase.CHECKING, message = null)
        logging.value = true
        viewModelScope.launch { reconcileSave(command, retryWrite = false, releaseUnwritten = true) }
    }

    private suspend fun reconcileSave(
        command: WorkoutSetSave,
        retryWrite: Boolean,
        releaseUnwritten: Boolean = false,
    ) {
        try {
            when (container.workoutRepository.inspectSetSave(command)) {
                WorkoutSetSaveResolution.SAVED -> acknowledgeSave(command, result = null, recovered = true)
                WorkoutSetSaveResolution.UNSAVED -> {
                    if (releaseUnwritten) {
                        clearSave(command)
                        error.clearFrom(source = ERR_LOG_SET, before = error.mark())
                        reconcileSelectionAfterSave()
                    } else if (retryWrite && sessionReader.observations.value.loadState == SessionLoadState.FOUND) {
                        saveOperation.value = WorkoutSaveState(WorkoutSavePhase.SAVING, command)
                        persistSet(command)
                    } else {
                        saveOperation.value = WorkoutSaveState(
                            WorkoutSavePhase.FAILED, command,
                            "This set has not been saved. Retry to save these values.",
                        )
                    }
                }
                WorkoutSetSaveResolution.CONFLICT -> {
                    if (releaseUnwritten) {
                        // Inspection proved this command is not the stored row. Never replay it.
                        clearSave(command)
                        editingSetId.value = null
                        clearEditingOriginal()
                        reconcileSelectionAfterSave()
                        persistDraft()
                    } else {
                        saveOperation.value = WorkoutSaveState(
                            WorkoutSavePhase.CONFLICT, command, WorkoutRepository.SetSaveConflict().message,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.w(TAG, "Could not establish the saved set outcome", failure)
            saveOperation.value = WorkoutSaveState(
                WorkoutSavePhase.FAILED, command,
                "Could not confirm whether this set was saved. Retry will check before saving again.",
            )
        } finally {
            logging.value = false
        }
    }

    private suspend fun persistSet(command: WorkoutSetSave) {
        val result = try {
            container.workoutRepository.saveSet(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.w(TAG, "Set save did not acknowledge completion", failure)
            saveOperation.value = WorkoutSaveState(
                phase = if (failure is WorkoutRepository.SetSaveConflict) WorkoutSavePhase.CONFLICT else WorkoutSavePhase.FAILED,
                command = command,
                message = failure.message?.takeIf { SetLogRules.isFieldMessage(it) }
                    ?: if (failure is WorkoutRepository.SetSaveConflict) failure.message else LogCommitCopy.WRITE_FAILED,
            )
            _logFeedback.tryEmit(LogCommitFeedback.REJECT)
            logging.value = false
            return
        }
        // A receipt, timer, or feedback failure after this point cannot create Retry save.
        acknowledgeSave(command, result, recovered = result.alreadySaved)
        logging.value = false
    }

    private fun restoreSaveDraft(command: WorkoutSetSave) {
        selectedExerciseId.value = command.exerciseId
        editingSetId.value = command.setId.takeIf { command.editing }
        val values = command.values
        draft.value = ActiveExerciseDraft(
            weightKg = values.weightKg, reps = values.reps, rpe = values.rpe,
            isWarmup = values.isWarmup, durationSeconds = values.durationSeconds,
        )
        draftDirty.value = true
        liftReadiness.value = LiftEntryReadiness.READY
    }

    private fun clearSave(command: WorkoutSetSave) {
        draftCache.clearPendingSave(command)
        if (saveOperation.value.command == command) {
            savedSave.clear()
            saveOperation.value = WorkoutSaveState()
        }
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
        if (saveOperation.value.command != command) return
        // Entry/selection were locked while this command was outstanding.
        clearSave(command)
        try {
            editingSetId.value = null
            clearEditingOriginal()
            wantAnotherSet.value = false
            draft.value = draft.value.copy(isWarmup = false, rpe = null)
            error.clearFrom(source = ERR_LOG_SET, before = error.mark())
            persistDraft()
            stopHoldTimer()
            clearSetStopwatch()
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
        sessionReader.retry()
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
            stopHoldTimer()
            clearSetStopwatch()
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
        undo.pop()
    }

    private fun liftLoadType(exerciseId: String): LoadType? =
        session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }?.exercise?.loadType

    fun skipRest() {
        cancelPendingRest()
        restTimer.stop()
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
        restTotal.value = PlannedRest(seconds = seconds, chosenFor = selectedExerciseId.value)
        restCommands.rememberPick(seconds)
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        if (!canChangeEntry()) return
        bumpTimedGeneration()
        stopHoldTimer()
        clearSetStopwatch()
        restCommands.startPlanned(restTotal.value.seconds)
    }

    fun acknowledgeRestBatteryHint() {
        restCommands.acknowledgeBatteryHint()
    }

    /** Fills the wells from one working set of the last session. Does not log. */
    fun applyLastTimeSet(weightKg: Double, reps: Int) {
        if (!canChangeEntry()) return
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(
            weightKg = weightKg.coerceAtLeast(0.0),
            reps = reps.coerceAtLeast(1),
        )
        markDraftDirty()
        persistDraft()
    }

    /**
     * Copies the in-set next load into the draft only. Does not [logSet].
     * After a log, today's hold stays until the lifter taps Use.
     */
    fun applyMicroRec() {
        if (!canChangeEntry()) return
        val rec = microRec.value ?: return
        if (!rec.showApply || rec.previewOnly) return
        draft.value = draft.value.copy(
            weightKg = rec.nextWeightKg.coerceAtLeast(0.0),
            reps = rec.nextReps.coerceAtLeast(1),
            rpe = rec.nextRpe,
        )
        markDraftDirty()
        persistDraft()
    }

    /**
     * Set when this screen should be popped. Held as state for the same reason as forward
     * navigation: a callback captured into a coroutine is bound to a NavController that may
     * no longer exist by the time the database work finishes.
     *
     * Pops ack BEFORE navigating (forward navigations ack after) — a duplicate pop would eat
     * an extra screen, which is worse than the vanishingly narrow window it guards against.
     */
    private val _exitRequested = MutableStateFlow<WorkoutExit?>(null)
    val exitRequested: StateFlow<WorkoutExit?> = _exitRequested.asStateFlow()

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
        if (logging.value) return
        cancelPendingRest()
        val started = error.mark()
        launchEntryMutation(source = ERR_FINISH) {
            when (val outcome = container.finishWorkout(sessionId, notes.value)) {
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
            delay(Motion.ROW_SETTLE_MS.toLong())
            if (canChangeEntry()) startRestAfterSet(prescribedSeconds)
        }
    }

    private fun startRestAfterSet(prescribedSeconds: Int? = null) {
        val planned = session.value?.exercises
            ?.firstOrNull { it.exercise.id == selectedExerciseId.value }
        val seconds = RestTimer.secondsToStart(
            planned?.restSeconds,
            RestTimerPreferences(
                defaultRestSeconds = restTotal.value.seconds.coerceIn(
                    RestTimerPreferences.MIN_SECONDS,
                    RestTimerPreferences.MAX_SECONDS,
                ),
            ),
            prescribedSeconds = prescribedSeconds,
        )
        restTotal.update { it.copy(seconds = seconds) }
        restTimer.start(seconds, sessionId)
        viewModelScope.launch {
            container.preferencesRepository.markRestAlarmEligible()
        }
    }

    /** Flushes the debounce tail: leaving must not drop the words typed in the last 400 ms. */
    fun persistDraftForExit() {
        persistDraft()
        viewModelScope.launch { writeNotes(notes.value) }
    }

    private fun persistDraft() {
        if (sessionId.isBlank() || terminalExit || finished.value || session.value?.isFinished == true) return
        val exerciseId = selectedExerciseId.value
        val canSeed = draftDirty.value ||
            liftReadiness.value.allowsCommit() ||
            editingSetId.value != null ||
            (exerciseId != null && draftCache.getLift(sessionId, exerciseId) != null)
        if (!canSeed) {
            draftCache.select(sessionId, exerciseId)
            savedDraft.writeSelection(
                sessionId = sessionId,
                exerciseId = exerciseId,
                notes = notes.value,
                editingSetId = editingSetId.value,
            )
            return
        }
        val current = WorkoutDraft(
            sessionId = sessionId,
            exerciseId = exerciseId,
            weightKg = draft.value.weightKg,
            reps = draft.value.reps,
            rpe = draft.value.rpe,
            isWarmup = draft.value.isWarmup,
            notes = notes.value,
            durationSeconds = draft.value.durationSeconds,
            dirty = draftDirty.value,
            extraSetRequested = wantAnotherSet.value,
        )
        draftCache.put(current)
        // Written through to saved state so the numbers dialed in before a rest survive the
        // process being killed while the phone sits in a pocket. Packet C upserts this
        // lift without dropping the others.
        savedDraft.write(current, editingSetId.value)
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
    )

    /**
     * The rest the dock will start. [chosenFor] is the lift someone picked it on — on the dock,
     * or on the rest page through the shared last preset — and that lift's late prefill seed
     * leaves it alone. Null when it is a seed or the default.
     */
    private data class PlannedRest(val seconds: Int, val chosenFor: String?)

    private data class WorkoutExtras(
        val restTotal: Int,
        val query: String,
        val showPicker: Boolean,
        val notes: String,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )

    private data class EditorMeta(
        val notes: String,
        val error: String?,
        val finished: Boolean,
        val editingSetId: String?,
    )
}
