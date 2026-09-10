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
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.LibraryGrouping
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SessionEditRules
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetLogRules
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.FinishOutcome
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/ActiveWorkoutVM"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_LOAD = "load"
private const val ERR_ADD_LIFT = "addLift"
private const val ERR_REMOVE_LIFT = "removeLift"
private const val ERR_LOG_SET = "logSet"
private const val ERR_DELETE_SET = "deleteSet"
private const val ERR_UNDO_DELETE = "undoDelete"
private const val ERR_FINISH = "finish"
private const val ERR_DISCARD = "discard"

/** A typing pause, not a keystroke, is what commits notes to the database. */
private const val NOTES_WRITE_DEBOUNCE_MS = 400L

data class ActiveExerciseDraft(
    val weightKg: Double = 0.0,
    val reps: Int = 5,
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
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
}

/** A record broken by the set just logged, for the in-workout moment. */
/**
 * A lift finished its prescribed sets and another is waiting.
 *
 * [finishedName] is what the screen names as done; [nextExerciseId] is where it goes if the
 * offer is not refused.
 */
data class PendingAdvance(
    val finishedExerciseId: String,
    val finishedName: String,
    val nextExerciseId: String,
    val nextName: String,
)

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
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val draftCache = container.workoutDraftCache
    private val savedDraft = SavedStateWorkoutDraft(savedStateHandle)

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
    private val wantAnotherSet = MutableStateFlow(false)
    private var cachedWeightUnit = WeightUnit.KG

    /**
     * The last deleted set, held only long enough for the snackbar to offer it back. One-shot
     * state rather than a captured callback, so an Activity recreation mid-offer cannot leave
     * an Undo button wired to a dead composition.
     */
    private val undoableDelete = MutableStateFlow<WorkoutRepository.DeletedSet?>(null)

    /** What the database already holds, so a re-seed or a no-op edit does not re-write it. */
    private var lastPersistedNotes: String? = null
    private var notesHydrated = false
    private var pendingResumeDraft: WorkoutDraft? = null
    /** Blocks a late Room emission from recreating a draft after finish/discard cleared it. */
    private var terminalExit = false

    /**
     * Tapping the lift that is already selected must still refill the draft from the
     * suggestion. [selectedExerciseId] cannot carry that: a StateFlow conflates a write of the
     * value it already holds into no emission at all.
     */
    private val reselections = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val restTimer = container.restTimerController

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
        val recovered = WorkoutDraftRecovery.resolve(
            sessionId = sessionId,
            inMemory = draftCache.get(sessionId),
            persisted = savedDraft.read(sessionId),
        )
        recovered?.let { cached ->
            pendingResumeDraft = cached
            selectedExerciseId.value = cached.exerciseId
            draft.value = ActiveExerciseDraft(
                weightKg = cached.weightKg,
                reps = cached.reps.coerceAtLeast(1),
                rpe = cached.rpe,
                isWarmup = cached.isWarmup,
            )
            notes.value = cached.notes
        }
        savedDraft.editingSetId()?.let { editingSetId.value = it }
        viewModelScope.launch {
            // The flow is guarded at the repository, but the body below is not — a failure
            // here would otherwise kill the collector and freeze the screen silently.
            runCatchingCancellable {
                session.collect { current ->
                    if (current == null) return@collect
                    val resolved = current.resolveSelectedExerciseId(selectedExerciseId.value)
                    if (resolved != selectedExerciseId.value) {
                        selectedExerciseId.value = resolved
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
            merge(
                selectedExerciseId.filterNotNull().distinctUntilChanged(),
                reselections,
            ).collectLatest { exerciseId ->
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RestTimerUiState(),
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
     * so it is skipped in both those cases.
     */
    private suspend fun prefill(exerciseId: String) {
        // Decided before the first suspension point, so a cancelled prefill cannot half-consume
        // it: a recovered draft is the user's own unfinished entry, but a draft recovered for
        // some other lift is simply spent.
        val resume = pendingResumeDraft
        val resumingThisLift = if (resume == null) {
            false
        } else {
            pendingResumeDraft = null
            resume.exerciseId == null || resume.exerciseId == exerciseId
        }
        val keepDraft = resumingThisLift || editingSetId.value != null
        // Cleared before the query so the previous lift's numbers never sit under the new
        // lift's name; a stale "last time" is worse than none.
        lastPerformance.value = null
        hint.value = null
        // Wait for the row rather than giving up: a selection restored from a saved draft can
        // arrive before the query answers, and bailing there left that lift with no suggestion
        // at all. sessionResolved bounds the wait — once the query has answered, null is final.
        val current = session.value ?: run {
            sessionResolved.first { it }
            session.value ?: return
        }
        val planned = current.exercises.firstOrNull { it.exercise.id == exerciseId }
        val targetReps = planned?.targetReps ?: 5
        val restPrefs = container.preferencesRepository.restTimerPreferences.first()
        restTotal.value = RestTimer.secondsToStart(planned?.restSeconds, restPrefs)
        val schedule = container.preferencesRepository.schedulePreferences.first()
        val thisWeek = LighterWeek.weekStartEpochDay(
            civilToday(),
            schedule.weekStart,
        )
        val lighter = LighterWeek.isCurrent(
            container.preferencesRepository.lighterWeekStartEpochDay.first(),
            thisWeek,
        )
        lighterWeek.value = lighter
        val progression = container.workoutRepository.progressionFor(
            exerciseId = exerciseId,
            exerciseName = planned?.exercise?.name ?: "",
            targetReps = targetReps,
            excludeSessionId = sessionId,
            loadType = planned?.exercise?.loadType,
            // Read once here rather than collected: the hint is computed at the moment a lift
            // opens, and a unit change mid-set recomputes it on the next open anyway.
            unit = container.preferencesRepository.weightUnit.first(),
            lighterWeek = lighter,
        )
        hint.value = progression
        lastPerformance.value = container.workoutRepository.lastPerformance(exerciseId, sessionId)
        if (keepDraft) return
        val lastWeight = progression?.suggestedWeightKg
            ?: planned?.targetWeightKg
            ?: 0.0
        draft.value = ActiveExerciseDraft(
            weightKg = lastWeight,
            reps = targetReps.coerceAtLeast(1),
            rpe = null,
            isWarmup = false,
        )
        persistDraft()
    }

    fun selectExercise(exerciseId: String) {
        // Any deliberate move settles a standing offer: the lifter has already answered it by
        // choosing, and leaving it armed would move them again a beat later.
        _pendingAdvance.value = null
        wantAnotherSet.value = false
        if (selectedExerciseId.value == exerciseId) {
            reselections.tryEmit(exerciseId)
        } else {
            selectedExerciseId.value = exerciseId
        }
        persistDraft()
    }

    fun advanceToNextLift(exerciseId: String) {
        wantAnotherSet.value = false
        selectExercise(exerciseId)
    }

    fun requestExtraSet() {
        wantAnotherSet.value = true
        persistDraft()
    }

    fun adjustWeight(deltaKg: Double) {
        val next = if (deltaKg.isFinite()) draft.value.weightKg + deltaKg else draft.value.weightKg
        draft.value = draft.value.copy(weightKg = next.coerceAtLeast(0.0))
        persistDraft()
    }

    fun setWeight(weightKg: Double) {
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(weightKg = weightKg.coerceAtLeast(0.0))
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
        persistDraft()
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
        draft.value = draft.value.copy(rpe = rpe)
        persistDraft()
    }

    fun setWarmup(isWarmup: Boolean) {
        draft.value = draft.value.copy(isWarmup = isWarmup)
        persistDraft()
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

    fun createAndAddExercise(name: String, muscleGroup: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                error.fail(source = ERR_ADD_LIFT, message = "Give that lift a name.")
                return@launch
            }
            try {
                when (val result = container.exerciseRepository.createCustom(name, muscleGroup)) {
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
                container.workoutRepository.removeExerciseFromSession(sessionId, item.id)
                // Let the session's own rule pick what to show next rather than guessing here.
                selectedExerciseId.value = null
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
            val defaults = AddDefaults.forExercise(exercise)
            container.workoutRepository.addExerciseToSession(
                sessionId = sessionId,
                exercise = exercise,
                targetSets = defaults.sets,
                targetReps = defaults.reps,
                targetWeightKg = null,
                restSeconds = defaults.restSeconds,
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

    /** What the Undo snackbar is offering, or null when there is nothing to put back. */
    val deletedSet: StateFlow<WorkoutRepository.DeletedSet?> = undoableDelete.asStateFlow()

    /** True while the lifter asked to log past the prescription. Cleared on log, Next, or switch. */
    val extraSetRequested: StateFlow<Boolean> = wantAnotherSet.asStateFlow()

    /**
     * The lift the loop is about to move to, and the one it just finished, or null when it is
     * staying put.
     *
     * Offered rather than taken: the screen shows it for a beat with a way out, because the
     * prescription is a plan and not a rule. A fourth set on a three-set lift is ordinary, and
     * a loop that jumps the moment the third lands puts the lifter on the wrong card with a
     * bar in their hands.
     */
    private val _pendingAdvance = MutableStateFlow<PendingAdvance?>(null)
    val pendingAdvance: StateFlow<PendingAdvance?> = _pendingAdvance.asStateFlow()

    fun onPersonalRecordShown() {
        _personalRecord.value = null
    }

    fun logSet() {
        val started = error.mark()
        if (logging.value) return
        val exerciseId = selectedExerciseId.value
        if (exerciseId == null) {
            error.fail(source = ERR_LOG_SET, message = "Add a lift before logging a set.")
            return
        }
        val current = draft.value
        // The same rule the repository enforces, run early so the refusal lands on the field
        // the user is looking at rather than as a thrown error after the tap.
        val loadType = session.value?.exercises
            ?.firstOrNull { it.exercise.id == exerciseId }
            ?.exercise
            ?.loadType
        val invalid = SetLogRules.validate(
            weightKg = current.weightKg,
            reps = current.reps,
            isWarmup = current.isWarmup,
            loadType = loadType,
        )
        if (invalid != null) {
            error.fail(source = ERR_LOG_SET, message = invalid)
            return
        }
        logging.value = true
        viewModelScope.launch {
            try {
                val editingId = editingSetId.value
                if (editingId != null) {
                    container.workoutRepository.updateSet(
                        setId = editingId,
                        weightKg = current.weightKg,
                        reps = current.reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                    )
                    editingSetId.value = null
                } else {
                    // Snapshot the count before the insert. The session Flow may publish the
                    // new row as soon as Room commits; reading it after logSet() and then
                    // adding one double-counted the set under a fast collector. A final
                    // prescribed set could therefore look like target + 1 and start a rest.
                    val previousWorking = session.value
                        ?.sets
                        ?.count { it.exerciseId == exerciseId && !it.isWarmup }
                        ?: 0
                    val targetSets = session.value
                        ?.exercises
                        ?.firstOrNull { it.exercise.id == exerciseId }
                        ?.targetSets
                        ?: 0
                    val logged = container.workoutRepository.logSet(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        weightKg = current.weightKg,
                        reps = current.reps,
                        rpe = current.rpe,
                        isWarmup = current.isWarmup,
                    )
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
                    val workingAfter = previousWorking + if (current.isWarmup) 0 else 1
                    wantAnotherSet.value = false
                    // The set that meets the target is the one that offers the move. Warm-ups
                    // never do, and neither does a lift with no prescription to meet: both
                    // would march the loop off a lift the session is not done with. The offer
                    // is only made once per crossing, because `workingAfter == targetSets` is
                    // false for the extra sets that follow.
                    if (!current.isWarmup && targetSets > 0 && workingAfter == targetSets) {
                        val after = session.value
                        val nextId = after?.nextUnfinishedExerciseAfter(exerciseId)
                        val nameOf = { id: String ->
                            after?.exercises
                                ?.firstOrNull { it.exercise.id == id }
                                ?.exercise
                                ?.name
                                .orEmpty()
                        }
                        _pendingAdvance.value = nextId?.let { next ->
                            PendingAdvance(
                                finishedExerciseId = exerciseId,
                                finishedName = nameOf(exerciseId),
                                nextExerciseId = next,
                                nextName = nameOf(next),
                            )
                        }
                    }
                    if (
                        RestTimer.shouldStartAfterLog(
                            isWarmup = current.isWarmup,
                            workingSetsAfterLog = workingAfter,
                            targetSets = targetSets,
                        ) ||
                        RestTimer.shouldStartAfterExtra(
                            isWarmup = current.isWarmup,
                            workingSetsAfterLog = workingAfter,
                            targetSets = targetSets,
                        )
                    ) {
                        startRestAfterSet()
                    }
                }
                error.clearFrom(source = ERR_LOG_SET, before = started)
                // Clear the two per-set flags on whatever is in the wells NOW — not on the
                // snapshot taken at the tap. Room's write is tens of milliseconds and a finger
                // is faster: a weight nudged or a rep count typed for the next set, in the
                // moment between the tap and the row landing, used to be taken back by this
                // line. The lifter saw the number they had just chosen revert to the one they
                // had already logged, and only sometimes, which is what made it so hard to
                // pin down. The set that was written is `current`; the wells belong to the
                // next one.
                draft.value = draft.value.copy(isWarmup = false, rpe = null)
                persistDraft()
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                error.fail(
                    source = ERR_LOG_SET,
                    message = thrown.message?.takeIf { message ->
                        SetLogRules.isUserMessage(message)
                    } ?: "Could not save that set. Try again.",
                )
            } finally {
                logging.value = false
            }
        }
    }

    /** Take the offer: move the loop to the waiting lift. */
    fun advanceNow() {
        val pending = _pendingAdvance.value ?: return
        _pendingAdvance.value = null
        selectedExerciseId.value = pending.nextExerciseId
        wantAnotherSet.value = false
        persistDraft()
    }

    /**
     * Refuse the offer and stay on the lift that just finished, ready for another set. Without
     * arming [wantAnotherSet] the entry wells would be closed on a lift already at target, so
     * refusing would leave nothing to do but refuse again.
     */
    fun stayOnCurrentExercise() {
        if (_pendingAdvance.value == null) return
        _pendingAdvance.value = null
        wantAnotherSet.value = true
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
        selectedExerciseId.value = set.exerciseId
        draft.value = ActiveExerciseDraft(
            weightKg = set.weightKg,
            reps = set.reps,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
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
                val removed = container.workoutRepository.deleteSet(setId)
                if (wasLatest) {
                    restTimer.stop()
                }
                undoableDelete.value = removed
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
     */
    fun undoDeleteSet() {
        val started = error.mark()
        val pending = undoableDelete.value ?: return
        undoableDelete.value = null
        viewModelScope.launch {
            try {
                container.workoutRepository.restoreSet(pending)
                error.clearFrom(source = ERR_UNDO_DELETE, before = started)
            } catch (thrown: CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "restoreSet failed", thrown)
                error.fail(
                    source = ERR_UNDO_DELETE,
                    message = "Could not restore that set. Try again.",
                )
            }
        }
    }

    /** The snackbar was dismissed or timed out; the offer expires with it. */
    fun onUndoOfferHandled() {
        undoableDelete.value = null
    }

    fun skipRest() {
        restTimer.stop()
    }

    fun adjustRest(deltaSeconds: Int) {
        restTimer.adjust(deltaSeconds)
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

    fun applySuggestedWeight() {
        val suggested = hint.value?.suggestedWeightKg ?: return
        draft.value = draft.value.copy(weightKg = suggested)
    }

    /** Fills the wells from one working set of the last session. Does not log. */
    fun applyLastTimeSet(weightKg: Double, reps: Int) {
        if (!weightKg.isFinite()) return
        draft.value = draft.value.copy(
            weightKg = weightKg.coerceAtLeast(0.0),
            reps = reps.coerceAtLeast(1),
        )
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

    private fun startRestAfterSet() {
        val seconds = restTotal.value.coerceIn(
            RestTimerPreferences.MIN_SECONDS,
            RestTimerPreferences.MAX_SECONDS,
        )
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
        val current = WorkoutDraft(
            sessionId = sessionId,
            exerciseId = selectedExerciseId.value,
            weightKg = draft.value.weightKg,
            reps = draft.value.reps,
            rpe = draft.value.rpe,
            isWarmup = draft.value.isWarmup,
            notes = notes.value,
        )
        draftCache.put(current)
        // Written through to saved state so the numbers dialed in before a rest survive the
        // process being killed while the phone sits in a pocket.
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
