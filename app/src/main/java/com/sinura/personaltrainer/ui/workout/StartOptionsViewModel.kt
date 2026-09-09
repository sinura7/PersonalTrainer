package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.TodaySheetStart
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.workout.StartCardioOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import com.sinura.personaltrainer.workout.StartOccurrenceOutcome
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OwnedLiftResolver
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.util.ErrorSlot
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/StartOptionsVM"

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_START = "start"
private const val ERR_DISCARD = "discard"
private const val START_BLOCKED_MESSAGE = "A workout is already in progress."

data class StartOptionsUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val liveActivity: ActivitySession? = null,
    /** Logged sets in [inProgress] — the summary row's own list is always empty. */
    val inProgressSetCount: Int = 0,
    val routines: List<Routine> = emptyList(),
    /** The coach's named lift, offered as a one-tap start. Null when it has nothing specific. */
    val suggestion: Exercise? = null,
    val suggestionReason: String? = null,
    val error: String? = null,
    /** Today's plan, when Body / History / Plan open this sheet. Null on rest or empty. */
    val todayStart: TodaySheetStart? = null,
)

/**
 * The start spine, now behind a sheet instead of a screen.
 *
 * Starting a workout used to cost a full-screen interstitial: you tapped Start on Home, a
 * screen appeared, you tapped again. It also became a THIRD place that offered to resume a
 * live session, alongside Home's hero and the notification. The logic here is unchanged — what
 * changed is that it opens over the screen you were already on, so the common case (start
 * today's plan) confirms on Home and everything else is one tap deeper rather than one screen.
 */
class StartOptionsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val error = ErrorSlot()

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StartOptionsUiState> = combine(
        combine(
            container.workoutRepository.observeInProgress(),
            container.activityRepository.observeLive(),
            // The discard confirm must state the real logged-set count: the summary
            // row's sets list is deliberately empty, and "This deletes the session."
            // in front of 25 logged sets was a misleading destructive confirm.
            container.workoutRepository.observeInProgress()
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(0)
                    } else {
                        container.workoutRepository.observeSessionActivity(session.id)
                            .map { it.totalSets }
                    }
                },
        ) { workout, activity, setCount -> Triple(workout, activity, setCount) },
        combine(
            container.routineRepository.observeAll(),
            suggestedLift,
            error.messages,
        ) { routines, suggested, err -> Triple(routines, suggested, err) },
        combine(
            container.trainingInsights.observeShared(),
            container.plannerRepository.observeOccurrences(),
            container.plannerRepository.observeRules(),
        ) { insights, occurrences, rules -> Triple(insights, occurrences, rules) },
    ) { live, extras, planner ->
        val routines = extras.first
        val today = todayEpochDay()
        val leftover = planner.first.weekPlan?.dayOn(today)
        val agenda = DailyAgenda.forDay(
            today,
            planner.second,
            planner.third,
            routines.associate { it.id to it.name },
        )
        StartOptionsUiState(
            isLoading = false,
            inProgress = live.first,
            liveActivity = live.second,
            inProgressSetCount = live.third,
            routines = routines,
            suggestion = extras.second?.first,
            suggestionReason = extras.second?.second,
            error = extras.third,
            todayStart = HomeToday.sheetStart(
                agenda,
                leftover?.takeIf {
                    WeekBoard.leftoverBelongsOn(today, leftover, planner.second, planner.third)
                },
                routines,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartOptionsUiState(),
    )

    /**
     * The session to open, held as state rather than passed as a callback.
     *
     * A navigation lambda captured into a viewModelScope coroutine closes over the
     * composition's NavController; if the Activity is recreated between the tap and the
     * database write completing, that controller is dead and the navigation is simply lost —
     * the workout starts but the screen never moves. A StateFlow survives recreation and is
     * re-read by the new composition.
     */
    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()
    private val _navigateToCardio = MutableStateFlow<String?>(null)
    val navigateToCardio: StateFlow<String?> = _navigateToCardio.asStateFlow()
    private val _navigateToComposer = MutableStateFlow<String?>(null)
    val navigateToComposer: StateFlow<String?> = _navigateToComposer.asStateFlow()

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    fun onCardioNavigationHandled() {
        _navigateToCardio.value = null
    }

    fun onComposerNavigationHandled() {
        _navigateToComposer.value = null
    }

    fun dismissError() {
        error.dismiss()
    }

    fun openComposer(mode: String) {
        _navigateToComposer.value = mode
    }

    fun startCardio() {
        val started = error.mark()
        viewModelScope.launch {
            try {
                when (
                    val outcome = container.startLiveCardio(
                        type = CardioType.RUN,
                        now = time.captureNow(),
                        title = "Cardio",
                    )
                ) {
                    is StartCardioOutcome.Open -> {
                        error.clearFrom(source = ERR_START, before = started)
                        _navigateToCardio.value = outcome.sessionId
                    }
                    is StartCardioOutcome.Rejected ->
                        error.fail(source = ERR_START, message = outcome.reason)
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startCardio failed", thrown)
                error.fail(source = ERR_START, message = "Could not start cardio. Try again.")
            }
        }
    }

    fun startRoutine(routineId: String) {
        viewModelScope.launch {
            val routine = container.routineRepository.getById(routineId)
            if (routine == null) {
                error.fail(source = ERR_START, message = "That routine is no longer available.")
                return@launch
            }
            if (routine.exercises.isEmpty()) {
                error.fail(source = ERR_START, message = SessionOrderCopy.NEED_A_LIFT)
                return@launch
            }
            try {
                handleStart(container.workoutRepository.startRoutineSafely(routine))
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startRoutine failed", thrown)
                error.fail(source = ERR_START, message = "Could not start that routine. Try again.")
            }
        }
    }

    /**
     * Starts a free session with the suggested lift already in it.
     *
     * The suggestion is only worth pinning here if acting on it is one tap: a row that opened
     * a picker so you could find the lift it had just named would be a worse version of
     * scrolling the routines list.
     */
    fun startSuggested() {
        val started = error.mark()
        val exercise = uiState.value.suggestion ?: return
        viewModelScope.launch {
            try {
                val focus = OwnedLiftResolver.primaryMuscleOf(exercise)?.displayName
                val outcome = container.workoutRepository.startFreeWorkoutSafely(focusTitle = focus)
                if (outcome is StartSessionOutcome.Blocked) {
                    error.fail(source = ERR_START, message = START_BLOCKED_MESSAGE)
                    return@launch
                }
                if (outcome is StartSessionOutcome.Unavailable) {
                    error.fail(source = ERR_START, message = outcome.message)
                    return@launch
                }
                val session = (outcome as StartSessionOutcome.Started).session
                PendingOccurrence.forget(container)
                val defaults = AddDefaults.forExercise(exercise)
                container.workoutRepository.addExerciseToSession(
                    sessionId = session.id,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
                error.clearFrom(source = ERR_START, before = started)
                _navigateToSession.value = session.id
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startSuggested failed", thrown)
                error.fail(source = ERR_START, message = "Could not start that session. Try again.")
            }
        }
    }

    /**
     * Discards the live session from inside the sheet.
     *
     * Routed through the shared use case so the rest timer stops and the draft is cleared —
     * the sheet is not allowed its own idea of what discarding means.
     */
    fun discardInProgress() {
        val started = error.mark()
        val liveWorkout = uiState.value.inProgress
        val liveActivity = uiState.value.liveActivity
        viewModelScope.launch {
            when {
                liveWorkout != null -> when (val result = container.discardWorkout(liveWorkout.id)) {
                    DiscardOutcome.Discarded -> {
                        PendingOccurrence.forgetIfSession(container, liveWorkout.id)
                        error.clearFrom(source = ERR_DISCARD, before = started)
                    }
                    is DiscardOutcome.Failed ->
                        error.fail(source = ERR_DISCARD, message = result.message)
                }
                liveActivity != null -> {
                    // Guarded like the live bar: an unhandled throw here took
                    // the process down, and clearing the timer on failure
                    // wiped the baseline of a session that still exists.
                    try {
                        container.discardActivity(liveActivity.id)
                        container.cardioTimerPersistence.clear()
                        error.clearFrom(source = ERR_DISCARD, before = started)
                    } catch (thrown: kotlinx.coroutines.CancellationException) {
                        throw thrown
                    } catch (thrown: Exception) {
                        AppLog.w(TAG, "Discarding live cardio from the sheet failed", thrown)
                        error.fail(
                            source = ERR_DISCARD,
                            message = "Could not discard this session. Try again.",
                        )
                    }
                }
            }
        }
    }

    fun startFree() {
        viewModelScope.launch {
            try {
                handleStart(container.workoutRepository.startFreeWorkoutSafely())
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startFree failed", thrown)
                error.fail(
                    source = ERR_START,
                    message = "Could not start a free workout. Try again.",
                )
            }
        }
    }

    /**
     * Today's plan from this sheet — occurrence when the agenda owns
     * the day, leftover slot week otherwise. Same binding Home uses.
     */
    fun startToday() {
        val started = error.mark()
        val start = uiState.value.todayStart ?: return
        viewModelScope.launch {
            try {
                val occurrenceId = start.occurrenceId
                if (occurrenceId != null) {
                    startOccurrence(occurrenceId)
                    return@launch
                }
                val day = start.leftover ?: return@launch
                val plannedId = PendingOccurrence.plannedOccurrenceId(container, day)
                when (val outcome = container.startTrainingDay(day)) {
                    is StartDayOutcome.Open -> {
                        if (plannedId != null) {
                            PendingOccurrence.bindForSession(container, plannedId, outcome.sessionId)
                        } else {
                            PendingOccurrence.forget(container)
                        }
                        error.clearFrom(source = ERR_START, before = started)
                        _navigateToSession.value = outcome.sessionId
                    }
                    is StartDayOutcome.Blocked ->
                        error.fail(source = ERR_START, message = START_BLOCKED_MESSAGE)
                    is StartDayOutcome.Failed ->
                        error.fail(source = ERR_START, message = outcome.message)
                    StartDayOutcome.Ignored -> Unit
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startToday failed", thrown)
                error.fail(
                    source = ERR_START,
                    message = "Could not start today's session. Try again.",
                )
            }
        }
    }

    private suspend fun startOccurrence(occurrenceId: String) {
        val started = error.mark()
        when (val outcome = container.startOccurrence(occurrenceId)) {
            is StartOccurrenceOutcome.OpenWorkout -> {
                PendingOccurrence.bindForSession(
                    container,
                    outcome.occurrenceId,
                    outcome.sessionId,
                )
                error.clearFrom(source = ERR_START, before = started)
                _navigateToSession.value = outcome.sessionId
            }
            is StartOccurrenceOutcome.OpenCardio -> {
                PendingOccurrence.forget(container)
                error.clearFrom(source = ERR_START, before = started)
                _navigateToCardio.value = outcome.sessionId
            }
            is StartOccurrenceOutcome.OpenComposer -> {
                PendingOccurrence.bind(container, outcome.occurrenceId)
                _navigateToComposer.value = "mixed"
            }
            is StartOccurrenceOutcome.Blocked ->
                error.fail(source = ERR_START, message = START_BLOCKED_MESSAGE)
            is StartOccurrenceOutcome.Failed ->
                error.fail(source = ERR_START, message = outcome.message)
            StartOccurrenceOutcome.Missing -> Unit
        }
    }

    private suspend fun handleStart(outcome: StartSessionOutcome) {
        val started = error.mark()
        when (outcome) {
            is StartSessionOutcome.Started -> {
                PendingOccurrence.forget(container)
                error.clearFrom(source = ERR_START, before = started)
                _navigateToSession.value = outcome.session.id
            }
            is StartSessionOutcome.Blocked -> {
                error.fail(source = ERR_START, message = START_BLOCKED_MESSAGE)
            }
            is StartSessionOutcome.Unavailable -> {
                error.fail(source = ERR_START, message = outcome.message)
            }
        }
    }
}
