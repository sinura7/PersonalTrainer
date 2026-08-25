package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TodaySheetStart
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.workout.StartDayOutcome
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OwnedLiftResolver
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.workout.DiscardOutcome
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PT/StartOptionsVM"
private const val START_BLOCKED_MESSAGE = "A workout is already in progress."

data class StartOptionsUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val liveActivity: ActivitySession? = null,
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
 * today's plan) skips it entirely and everything else is one tap deeper rather than one screen.
 */
class StartOptionsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val error = MutableStateFlow<String?>(null)

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

    val uiState: StateFlow<StartOptionsUiState> = combine(
        combine(
            container.workoutRepository.observeInProgress(),
            container.activityRepository.observeLive(),
        ) { workout, activity -> workout to activity },
        combine(
            container.routineRepository.observeAll(),
            suggestedLift,
            error,
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
            routines = routines,
            suggestion = extras.second?.first,
            suggestionReason = extras.second?.second,
            error = extras.third,
            todayStart = HomeToday.sheetStart(agenda, leftover, routines),
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

    fun openComposer(mode: String) {
        _navigateToComposer.value = mode
    }

    fun startCardio() {
        viewModelScope.launch {
            try {
                val now = JvmTime.captureNow()
                val block = CardioBlock(
                    id = IdFactory.Uuid.newId(),
                    sortOrder = 0,
                    type = CardioType.RUN,
                    indoor = false,
                    elapsedSeconds = 0L,
                    movingSeconds = 0L,
                    distanceMeters = null,
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                )
                when (val write = container.startLiveActivity("Cardio", listOf(block), now)) {
                    is com.sinura.personaltrainer.domain.ActivityWrite.Accepted -> {
                        val nowElapsed = android.os.SystemClock.elapsedRealtime()
                        val nowWall = System.currentTimeMillis()
                        container.cardioTimerPersistence.save(
                            PersistedCardioTimer(
                                sessionId = write.session.id,
                                startedAtElapsedRealtime = nowElapsed,
                                startedAtWallClockMillis = nowWall,
                                bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
                            ),
                        )
                        error.value = null
                        _navigateToCardio.value = write.session.id
                    }
                    is com.sinura.personaltrainer.domain.ActivityWrite.Rejected -> {
                        error.value = write.reason
                    }
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startCardio failed", thrown)
                error.value = "Could not start cardio. Try again."
            }
        }
    }

    fun startRoutine(routineId: String) {
        viewModelScope.launch {
            PendingOccurrence.forget(container)
            val routine = container.routineRepository.getById(routineId)
            if (routine == null) {
                error.value = "That routine is no longer available."
                return@launch
            }
            if (routine.exercises.isEmpty()) {
                error.value = SessionOrderCopy.NEED_A_LIFT
                return@launch
            }
            try {
                handleStart(container.workoutRepository.startRoutineSafely(routine))
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startRoutine failed", thrown)
                error.value = "Could not start that routine. Try again."
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
        val exercise = uiState.value.suggestion ?: return
        viewModelScope.launch {
            PendingOccurrence.forget(container)
            try {
                val focus = OwnedLiftResolver.primaryMuscleOf(exercise)?.displayName
                val outcome = container.workoutRepository.startFreeWorkoutSafely(focusTitle = focus)
                if (outcome is StartSessionOutcome.Blocked) {
                    error.value = START_BLOCKED_MESSAGE
                    return@launch
                }
                if (outcome is StartSessionOutcome.Unavailable) {
                    error.value = outcome.message
                    return@launch
                }
                val session = (outcome as StartSessionOutcome.Started).session
                val defaults = AddDefaults.forExercise(exercise)
                container.workoutRepository.addExerciseToSession(
                    sessionId = session.id,
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
                error.value = null
                _navigateToSession.value = session.id
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startSuggested failed", thrown)
                error.value = "Could not start that session. Try again."
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
        val liveWorkout = uiState.value.inProgress
        val liveActivity = uiState.value.liveActivity
        viewModelScope.launch {
            when {
                liveWorkout != null -> when (val result = container.discardWorkout(liveWorkout.id)) {
                    DiscardOutcome.Discarded -> error.value = null
                    is DiscardOutcome.Failed -> error.value = result.message
                }
                liveActivity != null -> {
                    container.discardActivity(liveActivity.id)
                    container.cardioTimerPersistence.clear()
                    error.value = null
                }
            }
        }
    }

    fun startFree() {
        viewModelScope.launch {
            PendingOccurrence.forget(container)
            try {
                handleStart(container.workoutRepository.startFreeWorkoutSafely())
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startFree failed", thrown)
                error.value = "Could not start a free workout. Try again."
            }
        }
    }

    /**
     * Today's plan from this sheet — occurrence when the agenda owns
     * the day, leftover slot week otherwise. Same binding Home uses.
     */
    fun startToday() {
        val start = uiState.value.todayStart ?: return
        viewModelScope.launch {
            try {
                val occurrenceId = start.occurrenceId
                if (occurrenceId != null) {
                    startOccurrence(occurrenceId)
                    return@launch
                }
                val day = start.leftover ?: return@launch
                PendingOccurrence.bindForPlannedDay(container, day)
                when (val outcome = container.startTrainingDay(day)) {
                    is StartDayOutcome.Open -> {
                        error.value = null
                        _navigateToSession.value = outcome.sessionId
                    }
                    is StartDayOutcome.Blocked -> error.value = START_BLOCKED_MESSAGE
                    is StartDayOutcome.Failed -> error.value = outcome.message
                    StartDayOutcome.Ignored -> Unit
                }
            } catch (thrown: kotlinx.coroutines.CancellationException) {
                throw thrown
            } catch (thrown: Exception) {
                AppLog.w(TAG, "startToday failed", thrown)
                error.value = "Could not start today's session. Try again."
            }
        }
    }

    private suspend fun startOccurrence(occurrenceId: String) {
        val occurrence = container.plannerRepository.getOccurrence(occurrenceId) ?: return
        val rule = container.plannerRepository.getRule(occurrence.ruleId)
        when (rule?.modality ?: ScheduleModality.STRENGTH) {
            ScheduleModality.CARDIO -> {
                PendingOccurrence.forget(container)
                val now = JvmTime.captureNow()
                val block = CardioBlock(
                    id = IdFactory.Uuid.newId(),
                    sortOrder = 0,
                    type = CardioType.RUN,
                    indoor = false,
                    elapsedSeconds = 0L,
                    movingSeconds = 0L,
                    distanceMeters = null,
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                )
                when (val write = container.startLiveActivity("Cardio", listOf(block), now, occurrence.id)) {
                    is ActivityWrite.Accepted -> {
                        val nowElapsed = android.os.SystemClock.elapsedRealtime()
                        val nowWall = System.currentTimeMillis()
                        container.cardioTimerPersistence.save(
                            PersistedCardioTimer(
                                sessionId = write.session.id,
                                startedAtElapsedRealtime = nowElapsed,
                                startedAtWallClockMillis = nowWall,
                                bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
                            ),
                        )
                        error.value = null
                        _navigateToCardio.value = write.session.id
                    }
                    is ActivityWrite.Rejected -> error.value = write.reason
                }
            }
            ScheduleModality.MIXED -> {
                PendingOccurrence.bind(container, occurrence.id)
                _navigateToComposer.value = "mixed"
            }
            ScheduleModality.STRENGTH -> {
                PendingOccurrence.bind(container, occurrence.id)
                val item = AgendaItem(occurrence, rule)
                val day = SuggestedTrainingDay(
                    epochDay = occurrence.localEpochDay,
                    dayOfWeek = Weekday.fromEpochDay(occurrence.localEpochDay),
                    isRest = false,
                    focusKind = rule?.focusKind ?: SessionFocusKind.FULL_BODY,
                    focusTitle = item.title,
                    routineId = rule?.routineId,
                    routineName = rule?.routineId?.let { id ->
                        uiState.value.routines.firstOrNull { it.id == id }?.name
                    },
                    reason = "",
                    emphasisMuscles = emptyList(),
                    confidence = ScheduleConfidence.HIGH,
                    slotId = null,
                )
                when (val outcome = container.startTrainingDay(day)) {
                    is StartDayOutcome.Open -> {
                        error.value = null
                        _navigateToSession.value = outcome.sessionId
                    }
                    is StartDayOutcome.Blocked -> error.value = START_BLOCKED_MESSAGE
                    is StartDayOutcome.Failed -> error.value = outcome.message
                    StartDayOutcome.Ignored -> Unit
                }
            }
        }
    }

    private fun handleStart(outcome: StartSessionOutcome) {
        when (outcome) {
            is StartSessionOutcome.Started -> {
                error.value = null
                _navigateToSession.value = outcome.session.id
            }
            is StartSessionOutcome.Blocked -> {
                error.value = START_BLOCKED_MESSAGE
            }
            is StartSessionOutcome.Unavailable -> {
                error.value = outcome.message
            }
        }
    }
}
