package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
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
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.LighterWeek
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
import com.sinura.personaltrainer.domain.UndoDwell
import com.sinura.personaltrainer.domain.UndoQueue
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.workout.SavedStateFloorTimer
import com.sinura.personaltrainer.workout.SavedStateFloorUndo
import com.sinura.personaltrainer.workout.FloorUndo
import com.sinura.personaltrainer.workout.UndoEntry
import com.sinura.personaltrainer.workout.toOffer
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "PT/ActiveWorkoutVM"
private const val TIMED_TICK_MS = 250L

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_LOAD = "load"
private const val ERR_ADD_LIFT = "addLift"
private const val ERR_REMOVE_LIFT = "removeLift"
private const val ERR_LOG_SET = "logSet"
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

    val canLog: Boolean
        get() = session != null &&
            selectedExerciseId != null &&
            !logging &&
            (liftReadiness.allowsCommit() || draftDirty)

    val canFinish: Boolean
        get() = session?.sets?.isNotEmpty() == true && !logging

    val showDiscard: Boolean
        get() = session != null && session.sets.isEmpty() && !logging

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

/**
 * A lift finished its prescribed sets. Standing dock choice — not a timed
 * auto-move. [nextExerciseId] is the next unfinished lift, or null when
 * Finish workout is the Volt. Cleared only by Next / Another / Finish,
 * logging, editing, or switching lifts.
 */
data class PendingAdvance(
    val finishedExerciseId: String,
    val finishedName: String,
    val nextExerciseId: String?,
    val nextName: String,
)

/** A record broken by the set just logged, for the in-workout moment. */
data class PersonalRecordMoment(
    val exerciseName: String,
    val kinds: Set<PersonalRecordKind>,
    val weightKg: Double,
    val reps: Int,
)

data class RestTimerUiState(
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 90,
    val running: Boolean = false,
    val completedTimerId: String? = null,
    /** False while the rest row is not on disk; the floor says so in one line. */
    val persistenceHealthy: Boolean = true,
    /** First rest in-app: unrestricted battery, or Samsung kills the clock. */
    val batteryHint: Boolean = false,
    /** Exact alarm denied: rest page says best-effort, never "precise". */
    val exactAlarmBestEffort: Boolean = false,
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
    private val savedUndo = SavedStateFloorUndo(savedStateHandle)

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(ActiveExerciseDraft())
    private val hint = MutableStateFlow<ProgressionHint?>(null)
    private val lastPerformance = MutableStateFlow<ExerciseSessionSummary?>(null)
    private val lighterWeek = MutableStateFlow(false)
    private val restTotal = MutableStateFlow(90)
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
    private val _pendingAdvance = MutableStateFlow<PendingAdvance?>(null)
    private var cachedWeightUnit = WeightUnit.KG

    /**
     * Packet G: cheap destructives form a short LIFO queue, newest last.
     *
     * The floor used to hold one deleted set xor one removed lift, so a second delete silently
     * expired the first offer. Undoing (or timing out) the top token now reveals the next one
     * underneath. One-shot state rather than captured callbacks, so an Activity recreation
     * mid-offer cannot leave an Undo button wired to a dead composition.
     */
    private val _undoEntries = MutableStateFlow<List<UndoEntry>>(emptyList())
    private var undoSequence = 0L
    private val _undoDwellMs = MutableStateFlow(
        savedUndo.readDwellMs() ?: UndoDwell.dwellMs(Motion.STATUS_DWELL_MS, null),
    )

    /** Every live undo offer, oldest first. The banner shows the last one. */
    val undoEntries: StateFlow<List<UndoEntry>> = _undoEntries.asStateFlow()

    /** How long the current top offer stays readable; extends under TalkBack. */
    val undoDwellMs: StateFlow<Long> = _undoDwellMs.asStateFlow()

    /** What the banner offers, or null when there is nothing to put back. */
    val deletedSet: StateFlow<WorkoutRepository.DeletedSet?> =
        _undoEntries.map { entries ->
            (entries.lastOrNull()?.token as? FloorUndo.DeletedSet)?.deleted
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A lift just taken out of the plan, held only for the same undo host. */
    val removedLift: StateFlow<WorkoutRepository.RemovedLift?> =
        _undoEntries.map { entries ->
            (entries.lastOrNull()?.token as? FloorUndo.RemovedLift)?.removed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _deleteFeedback = MutableSharedFlow<DeleteFeedback>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deleteFeedback: SharedFlow<DeleteFeedback> = _deleteFeedback.asSharedFlow()

    /**
     * Packet G: cheap mutations serialize on one mutex.
     *
     * A delete landing while an undo restores (or two deletes racing) must not interleave the
     * repository write and the queue push — the offer has to name exactly what was written.
     */
    private val undoMutex = Mutex()

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
    private var pendingRestJob: Job? = null
    private val _floorTimerCue = MutableSharedFlow<FloorTimerCue>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val floorTimerCue: SharedFlow<FloorTimerCue> = _floorTimerCue.asSharedFlow()
    private val _pendingLiftSwitch = MutableStateFlow<PendingLiftSwitch?>(null)
    val pendingLiftSwitch: StateFlow<PendingLiftSwitch?> = _pendingLiftSwitch.asStateFlow()

    /**
     * Flips true the first time the session query emits — including when it emits null.
     * "Session is null" alone cannot distinguish "still loading" from "gone", which is how a
     * discarded workout used to leave the screen on a spinner with no exit.
     */
    private val sessionResolved = MutableStateFlow(false)

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
        container.workoutRepository.observeSession(sessionId)
            .onEach { sessionResolved.value = true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (sessionId.isBlank()) {
            error.fail(source = ERR_LOAD, message = "This workout is no longer available.")
            // Nothing will ever emit for a blank id, so resolve immediately rather than spin.
            sessionResolved.value = true
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
        // Packet G: the undo queue survives process death; the dwell promised with it does too.
        val restoredUndo = savedUndo.read()
        _undoEntries.value = restoredUndo
        undoSequence = restoredUndo.size.toLong()
        if (savedUndo.readDwellMs() == null) refreshUndoDwell()
        restoreTimedWork(selectedExerciseId.value)
        viewModelScope.launch {
            // The flow is guarded at the repository, but the body below is not — a failure
            // here would otherwise kill the collector and freeze the screen silently.
            runCatchingCancellable {
                session.collect { current ->
                    if (current == null) return@collect
                    val resolved = current.resolveSelectedExerciseId(selectedExerciseId.value)
                    if (resolved != selectedExerciseId.value) {
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
            container.preferencesRepository.restTimerPreferences
                .map { it.lastPresetSeconds }
                .distinctUntilChanged()
                .drop(1)
                .collect { last ->
                    if (last != null && !restTimer.snapshot.value.running) {
                        restTotal.value = last
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

    val restTimerState: StateFlow<RestTimerUiState> = combine(
        combine(
            restTimer.remainingSeconds,
            restTimer.snapshot,
            restTotal,
            restTimer.lastCompletedTimerId,
            restTimer.persistenceHealthy,
        ) { remaining, snapshot, planned, completedId, healthy ->
            RestTimerUiState(
                remainingSeconds = remaining,
                totalSeconds = if (snapshot.running) snapshot.totalSeconds else planned,
                running = snapshot.running,
                completedTimerId = completedId,
                persistenceHealthy = healthy,
            )
        },
        container.preferencesRepository.restBatteryHintShown,
        restTimer.exactAlarmAttempt,
    ) { rest, shown, attempt ->
        rest.copy(
            batteryHint = rest.running && !shown,
            exactAlarmBestEffort = attempt == ExactAlarmAttempt.BEST_EFFORT,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestTimerUiState(),
    )

    /**
     * Packet D: first-use RPE helper. Hidden after a permanent dismiss
     * in DataStore — not a Room row.
     */
    val rpeHelperVisible: StateFlow<Boolean> =
        container.preferencesRepository.rpeHelperDismissed
            .map { dismissed -> !dismissed }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

    /**
     * In-set next load. Recomputed on log, RPE, warmup, lift switch, delete/undo, and edit.
     * Stepper ticks do not change it unless draft RPE is set (preview).
     * Eager: [applyMicroRec] reads this value, not a rendered snapshot.
     */
    val microRec: StateFlow<SetMicroRec?> = combine(
        combine(session, selectedExerciseId, draft, hint, wantAnotherSet) {
                current, selected, currentDraft, currentHint, extra ->
            MicroRecCore(current, selected, currentDraft, currentHint, extra)
        }.combine(lastPerformance) { core, last ->
            core.copy(lastPerformance = last)
        },
        combine(editingSetId, lighterWeek, container.preferencesRepository.weightUnit) { editing, lighter, unit ->
            Triple(editing, lighter, unit)
        },
    ) { core, extras ->
        cachedWeightUnit = extras.third
        workoutMicroRec(
            session = core.session,
            selectedExerciseId = core.selected,
            draft = core.draft,
            hint = core.hint,
            editingSetId = extras.first,
            lighterWeek = extras.second,
            unit = extras.third,
            wantAnotherSet = core.wantAnother,
            nowMs = time.nowMillis(),
            todayEpochDay = todayEpochDay(),
            historySets = core.lastPerformance?.sets.orEmpty(),
        )
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
     */
    private val suggestedLift: Flow<Pair<Exercise, String>?> =
        container.trainingInsights.observeShared(includeWeekPlan = false)
            .map { insights ->
                val card = insights.recommendations.firstOrNull { it.actionExerciseId != null }
                    ?: return@map null
                val exercise = container.exerciseRepository.getById(card.actionExerciseId!!)
                    ?: return@map null
                exercise to card.title
            }
            .catch { thrown ->
                AppLog.w(TAG, "Reading the suggested lift failed", thrown)
                emit(null)
            }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        session,
        selectedExerciseId,
        draft,
        hint,
    ) { current, selected, currentDraft, currentHint ->
        WorkoutCore(current, selected, currentDraft, currentHint)
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
                restTotal = first.first,
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
            // Overwritten below once sessionResolved is known; see the combine on that flow.
            loadState = SessionLoadState.LOADING,
            session = core.session,
            selectedExerciseId = core.session?.resolveSelectedExerciseId(core.selected) ?: core.selected,
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
    }.combine(sessionResolved) { state, resolved ->
        state.copy(
            loadState = when {
                !resolved && sessionId.isNotBlank() -> SessionLoadState.LOADING
                state.session != null -> SessionLoadState.FOUND
                else -> SessionLoadState.MISSING
            },
        )
    }.combine(
        combine(
            searchQuery.flatMapLatest { container.exerciseRepository.search(it) },
            container.exerciseRepository.observeLastLogged(),
        ) { results, lastLogged -> results to lastLogged },
    ) { state, (results, lastLogged) ->
        // With an empty query the picker leads with what you have actually been training.
        // A search has already expressed an intent, and re-ranking it by recency would fight
        // what was typed.
        val ordered = if (state.searchQuery.isBlank()) {
            ExerciseOrdering.pickerOrder(results, lastLogged)
        } else {
            results
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
        val keepDraft = resumingThisLift || editingSetId.value != null
        if (!keepDraft) {
            liftReadiness.value = LiftEntryReadiness.RESOLVING
            suggestionUnavailable.value = false
        } else if (liftReadiness.value == LiftEntryReadiness.NONE) {
            liftReadiness.value = LiftEntryReadiness.READY
        }
        lastPerformance.value = null
        hint.value = null
        val current = session.value ?: run {
            sessionResolved.first { it }
            session.value ?: return
        }
        if (!isCurrentPrefill(exerciseId, generation)) return
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val hold = planned?.exercise?.let { HoldWork.isHold(it) } == true
        val targetReps = planned?.targetReps ?: 5
        try {
            val restPrefs = container.preferencesRepository.restTimerPreferences.first()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val schedule = container.preferencesRepository.schedulePreferences.first()
            if (!isCurrentPrefill(exerciseId, generation)) return
            val thisWeek = LighterWeek.weekStartEpochDay(
                civilToday(),
                schedule.weekStart,
            )
            val lighter = LighterWeek.isCurrent(
                container.preferencesRepository.lighterWeekStartEpochDay.first(),
                thisWeek,
            )
            if (!isCurrentPrefill(exerciseId, generation)) return
            lighterWeek.value = lighter
            val progression = container.workoutRepository.progressionFor(
                exerciseId = exerciseId,
                exerciseName = planned?.exercise?.name ?: "",
                targetReps = targetReps,
                excludeSessionId = sessionId,
                loadType = planned?.exercise?.loadType,
                unit = container.preferencesRepository.weightUnit.first(),
                lighterWeek = lighter,
                equipment = planned?.exercise?.equipment,
            )
            if (!isCurrentPrefill(exerciseId, generation)) return
            hint.value = progression
            lastPerformance.value = container.workoutRepository.lastPerformance(exerciseId, sessionId)
            if (!isCurrentPrefill(exerciseId, generation)) return
            restTotal.value = RestTimer.secondsToStart(
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
                )?.restSeconds,
            )
            if (!isCurrentPrefill(exerciseId, generation)) return
            if (keepDraft || draftDirty.value) {
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
            if (!draftDirty.value && (!keepDraft || draft.value.weightKg <= 0.0)) {
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
        persistStopwatchFor(selectedExerciseId.value)
        _pendingAdvance.value = null
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
        draft.value = ActiveExerciseDraft(
            weightKg = cached.weightKg,
            reps = if (cached.durationSeconds != null) {
                cached.reps.coerceAtLeast(0)
            } else {
                cached.reps.coerceAtLeast(1)
            },
            rpe = cached.rpe,
            isWarmup = cached.isWarmup,
            durationSeconds = cached.durationSeconds,
        )
        draftDirty.value = cached.dirty
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
        if (selectedExerciseId.value == exerciseId) return
        if (_setStopwatch.value.running) {
            _pendingLiftSwitch.value = PendingLiftSwitch(exerciseId)
            return
        }
        applySelection(exerciseId)
        persistDraft()
    }

    fun confirmStopTimingAndSwitch() {
        val pending = _pendingLiftSwitch.value ?: return
        _pendingLiftSwitch.value = null
        stopSetStopwatch()
        applySelection(pending.exerciseId)
        persistDraft()
    }

    fun cancelPendingLiftSwitch() {
        _pendingLiftSwitch.value = null
    }

    fun advanceToNextLift(exerciseId: String) {
        wantAnotherSet.value = false
        selectExercise(exerciseId)
    }

    fun requestExtraSet() {
        _pendingAdvance.value = null
        wantAnotherSet.value = true
        persistDraft()
    }

    fun adjustWeight(deltaKg: Double) {
        val next = if (deltaKg.isFinite()) draft.value.weightKg + deltaKg else draft.value.weightKg
        draft.value = draft.value.copy(weightKg = next.coerceAtLeast(0.0))
        markDraftDirty()
        persistDraft()
    }

    fun setWeight(weightKg: Double) {
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
        markDraftDirty()
        persistDraft()
    }

    fun adjustReps(delta: Int) {
        setReps(draft.value.reps + delta)
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
        draft.value = draft.value.copy(reps = reps.coerceAtLeast(1))
        markDraftDirty()
        persistDraft()
    }

    fun adjustHoldSeconds(direction: Int) {
        val current = draft.value.durationSeconds ?: HoldWork.DEFAULT_SECONDS
        setHoldSeconds(HoldWork.nextSeconds(current, direction))
    }

    fun setHoldSeconds(seconds: Int) {
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
        if (draft.value.isWarmup) return
        val current = draft.value
        draft.value = current.copy(rpe = rpe)
        markDraftDirty()
        persistDraft()
    }

    fun setWarmup(isWarmup: Boolean) {
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
        if (!weightKg.isFinite() || weightKg <= 0.0) return
        draft.value = draft.value.copy(
            weightKg = weightKg,
            isWarmup = true,
            rpe = null,
        )
        markDraftDirty()
        persistDraft()
    }

    fun dismissRpeHelper() {
        viewModelScope.launch {
            container.preferencesRepository.dismissRpeHelper()
        }
    }

    fun setNotes(value: String) {
        notes.value = value
        persistDraft()
        // The database write is not launched here. See the debounce collector in init.
    }

    fun setPickerVisible(visible: Boolean) {
        if (!visible) swapTargetItemId.value = null
        showPicker.value = visible
        if (!visible) searchQuery.value = ""
    }

    fun onSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            addExerciseInternal(exercise)
        }
    }

    fun createAndAddExercise(
        name: String,
        muscleGroup: String,
        loadType: LoadType = LoadType.EXTERNAL,
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                error.fail(source = ERR_ADD_LIFT, message = "Give that lift a name.")
                return@launch
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
        val selectedId = selectedExerciseId.value ?: return
        val item = session.value?.exercises?.firstOrNull { it.exercise.id == selectedId } ?: return
        swapTargetItemId.value = item.id
        searchQuery.value = ""
        showPicker.value = true
    }

    fun removeSelectedLift() {
        val started = error.mark()
        val selectedId = selectedExerciseId.value ?: return
        val item = session.value?.exercises?.firstOrNull { it.exercise.id == selectedId } ?: return
        viewModelScope.launch {
            try {
                val removed = undoMutex.withLock {
                    container.workoutRepository.removeExerciseFromSession(
                        sessionId = sessionId,
                        itemId = item.id,
                    ).also { pushUndo(FloorUndo.RemovedLift(it)) }
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
                selectExercise(exercise.id)
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
            selectExercise(exercise.id)
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
            selectExercise(exercise.id)
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

    /**
     * The lift the loop can move to, and the one it just finished, or null when it is
     * staying put.
     *
     * Standing dock choice (Next lift / Another set), not a timed auto-move. The
     * prescription is a plan and not a rule: a fourth set on a three-set lift is ordinary,
     * and a loop that jumps the moment the third lands puts the lifter on the wrong card
     * with a bar in their hands.
     */
    val pendingAdvance: StateFlow<PendingAdvance?> = _pendingAdvance.asStateFlow()

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

    fun logSet() {
        if (logging.value) return
        val started = error.mark()
        val exerciseId = selectedExerciseId.value
        if (exerciseId == null) {
            error.fail(source = ERR_LOG_SET, message = "Add a lift before logging a set.")
            _logFeedback.tryEmit(LogCommitFeedback.REJECT)
            return
        }
        val ready = liftReadiness.value.allowsCommit() || draftDirty.value
        if (!ready) return
        logging.value = true
        val current = draft.value
        val selectedLift = session.value?.exercises
            ?.firstOrNull { it.exercise.id == exerciseId }
        val hold = selectedLift?.let { HoldWork.isHold(it.exercise) } == true
        val holdState = _holdTimer.value
        val editingId = editingSetId.value
        // Freeze the edited row's position before suspending for persistence. Total
        // counts describe the latest row, not an earlier row being corrected.
        val precedingSets = session.value?.setsFor(exerciseId).orEmpty()
            .takeWhile { it.id != editingId }
        if (hold && editingId == null && holdState.totalSeconds == 0 && !holdState.running) {
            logging.value = false
            startHoldSet()
            return
        }
        val duration = FloorTimerSurface.durationToLog(
            hold = hold,
            holdElapsedSeconds = holdState.elapsedSeconds,
            holdTotalSeconds = holdState.totalSeconds,
            holdRemainingSeconds = holdState.remainingSeconds,
            holdDraftSeconds = current.durationSeconds ?: selectedLift?.targetSeconds,
            stopwatch = _setStopwatch.value,
            existingDurationSeconds = current.durationSeconds.takeUnless { hold },
        )
        val reps = if (hold) 0 else current.reps
        val loadType = selectedLift?.exercise?.loadType
        val invalid = SetLogRules.validate(
            weightKg = current.weightKg,
            reps = reps,
            isWarmup = current.isWarmup,
            loadType = loadType,
            durationSeconds = duration,
            isHold = hold,
            equipment = selectedLift?.exercise?.equipment,
            movementKey = selectedLift?.exercise?.movementKey,
        )
        if (invalid != null) {
            logging.value = false
            error.fail(source = ERR_LOG_SET, message = invalid)
            _logFeedback.tryEmit(LogCommitFeedback.REJECT)
            return
        }
        viewModelScope.launch {
            try {
                if (editingId != null) {
                    container.workoutRepository.updateSet(
                        setId = editingId,
                        weightKg = current.weightKg,
                        reps = reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                        durationSeconds = duration,
                    )
                    editingSetId.value = null
                    stopHoldTimer()
                    clearSetStopwatch()
                    emitLogReceipt(
                        setId = editingId,
                        weightKg = current.weightKg,
                        reps = reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                        durationSeconds = duration,
                        loadType = loadType,
                        warmupAfter = precedingSets.count { it.isWarmup } + if (current.isWarmup) 1 else 0,
                        workingAfter = precedingSets.count { !it.isWarmup } + if (current.isWarmup) 0 else 1,
                        targetSets = selectedLift?.targetSets ?: 0,
                    )
                    _logFeedback.tryEmit(LogCommitFeedback.SUCCESS)
                } else {
                    val previousWorking = session.value
                        ?.sets
                        ?.count { it.exerciseId == exerciseId && !it.isWarmup }
                        ?: 0
                    val previousWarmup = session.value
                        ?.sets
                        ?.count { it.exerciseId == exerciseId && it.isWarmup }
                        ?: 0
                    val targetSets = selectedLift?.targetSets ?: 0
                    val logged = container.workoutRepository.logSet(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        weightKg = current.weightKg,
                        reps = reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                        durationSeconds = duration,
                    )
                    stopHoldTimer()
                    clearSetStopwatch()
                    val workingAfter = previousWorking + if (current.isWarmup) 0 else 1
                    val warmupAfter = previousWarmup + if (current.isWarmup) 1 else 0
                    emitLogReceipt(
                        setId = logged.setId,
                        weightKg = current.weightKg,
                        reps = reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                        durationSeconds = duration,
                        loadType = loadType,
                        warmupAfter = warmupAfter,
                        workingAfter = workingAfter,
                        targetSets = targetSets,
                    )
                    _logFeedback.tryEmit(LogCommitFeedback.SUCCESS)
                    if (logged.records.isNotEmpty()) {
                        _personalRecord.value = PersonalRecordMoment(
                            exerciseName = session.value
                                ?.exercises
                                ?.firstOrNull { it.exercise.id == exerciseId }
                                ?.exercise
                                ?.name
                                .orEmpty(),
                            kinds = logged.records,
                            weightKg = current.weightKg,
                            reps = current.reps,
                        )
                    }
                    wantAnotherSet.value = false
                    _pendingAdvance.value = null
                    val liftComplete = !current.isWarmup &&
                        WorkoutAdvance.liftComplete(workingAfter, targetSets, wantAnother = false)
                    if (liftComplete) {
                        val after = session.value
                        val nextId = WorkoutAdvance.nextUnfinishedExerciseId(after, exerciseId)
                        val nameOf = { id: String ->
                            after?.exercises
                                ?.firstOrNull { it.exercise.id == id }
                                ?.exercise
                                ?.name
                                .orEmpty()
                        }
                        _pendingAdvance.value = PendingAdvance(
                            finishedExerciseId = exerciseId,
                            finishedName = nameOf(exerciseId),
                            nextExerciseId = nextId,
                            nextName = nextId?.let(nameOf).orEmpty(),
                        )
                    }
                    cancelPendingRest()
                    val startRest = RestTimer.shouldStartAfterLog(
                        isWarmup = current.isWarmup,
                        workingSetsAfterLog = workingAfter,
                        targetSets = targetSets,
                    ) || RestTimer.shouldStartAfterExtra(
                        isWarmup = current.isWarmup,
                        workingSetsAfterLog = workingAfter,
                        targetSets = targetSets,
                    )
                    if (startRest) {
                        scheduleRestAfterReceipt(
                            prescribedSeconds = RestPrescription.seconds(
                                reasonCode = microRec.value?.reasonCode
                                    ?: SetMicroRecCalculator.QUALITY,
                                loadType = loadType,
                                reps = reps,
                            ),
                        )
                    } else if (liftComplete) {
                        restTimer.stop()
                    }
                }
                error.clearFrom(source = ERR_LOG_SET, before = started)
                draft.value = draft.value.copy(isWarmup = false, rpe = null)
                persistDraft()
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                _logFeedback.tryEmit(LogCommitFeedback.REJECT)
                error.fail(
                    source = ERR_LOG_SET,
                    message = thrown.message?.takeIf { message ->
                        SetLogRules.isFieldMessage(message)
                    } ?: LogCommitCopy.WRITE_FAILED,
                )
            } finally {
                logging.value = false
            }
        }
    }

    /** Take the offer: move the loop to the waiting unfinished lift. */
    fun advanceNow() {
        val current = session.value ?: return
        val selected = selectedExerciseId.value
        val next = _pendingAdvance.value?.nextExerciseId
            ?: WorkoutAdvance.nextUnfinishedExerciseId(current, selected)
            ?: return
        _pendingAdvance.value = null
        applySelection(next)
        wantAnotherSet.value = false
        persistDraft()
    }

    /**
     * Refuse the offer and stay on the lift that just finished, ready for another set.
     */
    fun stayOnCurrentExercise() {
        requestExtraSet()
    }

    fun dismissError() {
        error.dismiss()
    }

    fun editSet(setId: String) {
        val set = session.value?.sets?.firstOrNull { it.id == setId } ?: return
        // editingSetId is set below and prefill refuses to run while it is, so selecting the
        // set's lift here cannot overwrite the values being edited.
        editingSetId.value = set.id
        // Revising a set is not moving on from it.
        _pendingAdvance.value = null
        stopHoldTimer()
        clearSetStopwatch()
        selectedExerciseId.value = set.exerciseId
        val hold = session.value?.exercises
            ?.firstOrNull { it.exercise.id == set.exerciseId }
            ?.let { HoldWork.isHold(it.exercise) } == true
        draft.value = ActiveExerciseDraft(
            weightKg = set.weightKg,
            reps = if (hold) 0 else set.reps,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
            durationSeconds = set.durationSeconds,
        )
        persistDraft()
    }

    fun cancelEdit() {
        editingSetId.value = null
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
     * first, and the write plus the push serialize on [undoMutex] so the offer always names
     * exactly what landed. Never starts or restarts rest.
     */
    fun deleteSet(setId: String) {
        val started = error.mark()
        viewModelScope.launch {
            val current = session.value
            val deleted = current?.sets?.firstOrNull { it.id == setId }
            val wasLatest = deleted != null &&
                current.sets.maxByOrNull { it.completedAt }?.id == setId
            if (editingSetId.value == setId) {
                editingSetId.value = null
            }
            try {
                val removed = undoMutex.withLock {
                    val snapshot = container.workoutRepository.deleteSet(setId)
                    if (snapshot != null) pushUndo(FloorUndo.DeletedSet(snapshot))
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
        val started = error.mark()
        viewModelScope.launch {
            undoMutex.withLock {
                val top = _undoEntries.value.lastOrNull()?.token as? FloorUndo.DeletedSet
                    ?: return@withLock null
                container.workoutRepository.restoreSet(top.deleted)
                popUndo()
            } ?: return@launch
            _deleteFeedback.tryEmit(DeleteFeedback.UNDO)
            error.clearFrom(source = ERR_UNDO_DELETE, before = started)
        }
    }

    fun undoRemoveLift() {
        val started = error.mark()
        viewModelScope.launch {
            val pending = undoMutex.withLock {
                val top = _undoEntries.value.lastOrNull()?.token as? FloorUndo.RemovedLift
                    ?: return@withLock null
                container.workoutRepository.restoreExerciseToSession(removed = top.removed)
                popUndo()
                top
            } ?: return@launch
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
        when (_undoEntries.value.lastOrNull()?.token) {
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
        popUndo()
    }

    private fun liftLoadType(exerciseId: String): LoadType? =
        session.value?.exercises?.firstOrNull { it.exercise.id == exerciseId }?.exercise?.loadType

    private fun pushUndo(token: FloorUndo) {
        val offer = token.toOffer(
            sequence = undoSequence++,
            unit = cachedWeightUnit,
            loadTypeOf = ::liftLoadType,
        )
        _undoEntries.value = UndoQueue.push(_undoEntries.value, UndoEntry(token, offer))
        refreshUndoDwell()
        persistUndo()
    }

    private fun popUndo(): UndoEntry? {
        val entries = _undoEntries.value
        if (entries.isEmpty()) return null
        val top = entries.last()
        _undoEntries.value = UndoQueue.pop(entries)
        persistUndo()
        return top
    }

    private fun persistUndo() {
        savedUndo.write(_undoEntries.value, _undoDwellMs.value)
    }

    private fun refreshUndoDwell() {
        _undoDwellMs.value = UndoDwell.dwellMs(
            Motion.STATUS_DWELL_MS,
            undoTimeout.recommendedTimeoutMs(Motion.STATUS_DWELL_MS.toInt()),
        )
    }

    fun skipRest() {
        cancelPendingRest()
        restTimer.stop()
    }

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
    }

    /** Idle: change the planned rest. Running: the same gateway ±15. */
    fun nudgeRest(deltaSeconds: Int) {
        if (restTimer.snapshot.value.running) {
            restTimer.adjust(deltaSeconds)
        } else {
            selectRestDuration(RestTimer.nudgeSeconds(restTotal.value, deltaSeconds))
        }
    }

    /** Names the next rest. Does not start the clock. */
    fun selectRestDuration(seconds: Int) {
        restTotal.value = seconds
        viewModelScope.launch {
            container.preferencesRepository.setLastRestPresetSeconds(seconds)
        }
    }

    fun selectCustomRest(input: String): Boolean {
        val seconds = RestTimer.parseCustom(input) ?: return false
        selectRestDuration(seconds)
        return true
    }

    fun startSelectedRest() {
        bumpTimedGeneration()
        stopHoldTimer()
        clearSetStopwatch()
        val seconds = restTotal.value.coerceIn(
            RestTimerPreferences.MIN_SECONDS,
            RestTimerPreferences.MAX_SECONDS,
        )
        viewModelScope.launch {
            container.preferencesRepository.setLastRestPresetSeconds(seconds)
            container.preferencesRepository.markRestAlarmEligible()
            restTimer.start(seconds, sessionId)
        }
    }

    /**
     * Dock primary while idle: go on to the next lift. Does not start rest
     * (G-05). Start (the small control) is rest only.
     */
    fun startNextLift() {
        val current = session.value ?: return
        val advance = WorkoutAdvance.forSelection(
            session = current,
            selectedExerciseId = selectedExerciseId.value,
            wantAnother = wantAnotherSet.value,
            editing = editingSetId.value != null,
        )
        val next = advance.nextExerciseId ?: return
        if (advance.showNext) {
            advanceToNextLift(next)
        }
    }

    fun acknowledgeRestBatteryHint() {
        viewModelScope.launch {
            container.preferencesRepository.markRestBatteryHintShown()
        }
    }

    /**
     * Copies the progression's suggested load into the weight well. Does not log.
     *
     * `persistDraft()` is not optional here, and its absence was a real loss: every other
     * mutator on this class mirrors the draft, and nothing else re-persists on its own — the
     * session collector only persists on a Room emission, and tapping a chip changes no row.
     * Nudge the well down to 70 kg, change your mind and tap Use to take the suggested 82.5,
     * then pocket the phone for the rest; if Android reclaims the process, recovery hands back
     * the 70 that was mirrored and the tap is gone.
     */
    fun applySuggestedWeight() {
        val suggested = hint.value?.suggestedWeightKg ?: return
        draft.value = draft.value.copy(weightKg = suggested)
        markDraftDirty()
        persistDraft()
    }

    /** Fills the wells from one working set of the last session. Does not log. */
    fun applyLastTimeSet(weightKg: Double, reps: Int) {
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
        if (logging.value) return
        val started = error.mark()
        viewModelScope.launch {
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
        val started = error.mark()
        viewModelScope.launch {
            when (container.discardWorkout(sessionId)) {
                DiscardOutcome.Discarded -> {
                    PendingOccurrence.forgetIfSession(container, sessionId)
                    error.clearFrom(source = ERR_DISCARD, before = started)
                    terminalExit = true
                    draftCache.clear(sessionId)
                    savedDraft.clear()
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
            unit = cachedWeightUnit,
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
            startRestAfterSet(prescribedSeconds)
        }
    }

    private fun startRestAfterSet(prescribedSeconds: Int? = null) {
        val planned = session.value?.exercises
            ?.firstOrNull { it.exercise.id == selectedExerciseId.value }
        val seconds = RestTimer.secondsToStart(
            planned?.restSeconds,
            RestTimerPreferences(
                defaultRestSeconds = restTotal.value.coerceIn(
                    RestTimerPreferences.MIN_SECONDS,
                    RestTimerPreferences.MAX_SECONDS,
                ),
            ),
            prescribedSeconds = prescribedSeconds,
        )
        restTotal.value = seconds
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
        )
        draftCache.put(current)
        // Written through to saved state so the numbers dialed in before a rest survive the
        // process being killed while the phone sits in a pocket. Packet C upserts this
        // lift without dropping the others.
        savedDraft.write(current, editingSetId.value)
    }

    private data class WorkoutCore(
        val session: WorkoutSession?,
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
