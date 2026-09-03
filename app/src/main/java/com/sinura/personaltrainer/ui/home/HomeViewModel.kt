package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DayBlockOrder
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.BodyweightCheckIn
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.latest
import com.sinura.personaltrainer.data.repository.AuxiliaryBlocks
import com.sinura.personaltrainer.data.repository.DayBlocks
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import com.sinura.personaltrainer.workout.StartOccurrenceOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val routines: List<Routine> = emptyList(),
    /**
     * The newest finished session from all-time summaries, not the
     * 30-day heat graph. A workout older than the window must still
     * answer "when did I last train".
     */
    val lastSession: SessionSummary? = null,
    val readyToProgress: List<ProgressionHint> = emptyList(),
    val recommendations: List<TrainingRecommendation> = emptyList(),
    val weekPlan: WeeklySchedulePlan? = null,
    /**
     * Days that already hold a finished session.
     *
     * Derived from all-time summaries rather than the heat window: a leftover
     * week card that only knew about the last 30 days would treat older days
     * in the current week as untrained after a travel-week gap.
     */
    val loggedEpochDays: Set<Long> = emptySet(),
    val lighterWeek: Boolean = false,
    val error: String? = null,
    val missedWorkPrompt: Boolean = false,
    val overdueCount: Int = 0,
    /** False until a plan or custom week is accepted. Home shows the get-started sheet. */
    val setupComplete: Boolean = true,
    val bodyweightCheckInDue: Boolean = false,
    val latestBodyweightKg: Double? = null,
    val occurrences: List<com.sinura.personaltrainer.domain.ScheduleOccurrence> = emptyList(),
    val rules: List<com.sinura.personaltrainer.domain.ScheduleRule> = emptyList(),
    val weekStartEpochDay: Long = 0L,
)

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        container.trainingInsights.observeShared(),
        container.workoutRepository.observeInProgress(),
        actionError,
        combine(
            combine(
                container.preferencesRepository.lighterWeekStartEpochDay,
                combine(
                    container.plannerRepository.observeOccurrences(),
                    container.plannerRepository.observeRules(),
                    container.plannerRepository.observeDecisions(),
                ) { occurrences, rules, decisions -> Triple(occurrences, rules, decisions) },
            ) { lighterStart, planner -> lighterStart to planner },
            combine(
                container.preferencesRepository.schedulePreferences,
                container.preferencesRepository.preferredDays,
                container.preferencesRepository.bodyweightLog,
                container.preferencesRepository.bodyweightCheckInWeekday,
                container.preferencesRepository.onboardingComplete,
            ) { preferences, preferredDays, log, checkIn, setupComplete ->
                HomeCadence(preferences, preferredDays, log, checkIn, setupComplete)
            },
        ) { planner, cadence -> planner to cadence },
    ) { insights, inProgress, error, extras ->
        val lighterStart = extras.first.first
        val occurrences = extras.first.second.first
        val rules = extras.first.second.second
        val decisions = extras.first.second.third
        val cadence = extras.second
        val today = todayEpochDay()
        val now = time.captureNow()
        val nowMinutes = time.wallMinutesOfDay(now.instantMillis, now.zoneId)
        val weekStart = insights.weekPlan?.weekStartEpochDay
            ?: CivilDate.fromEpochDay(today).previousOrSame(
                insights.weekPlan?.preferences?.weekStart
                    ?: cadence.preferences.weekStart,
            ).epochDay
        val weekOcc = occurrences.filter { it.localEpochDay in weekStart..(weekStart + 6) }
        val overdue = MissedWorkPolicy.overdue(weekOcc, today, nowMinutes)
        val decision = decisions.firstOrNull { it.weekStartEpochDay == weekStart }
        HomeUiState(
            isLoading = false,
            inProgress = inProgress,
            routines = insights.routines,
            lastSession = insights.summaries.latest(),
            readyToProgress = insights.hints,
            // Home already devotes a section to the ready-to-progress lifts, so the card that
            // only says "some lifts are ready" is noise next to the list naming them.
            recommendations = insights.recommendations.filterNot { rec ->
                rec.id == "progression-ready" && insights.hints.isNotEmpty()
            },
            weekPlan = insights.weekPlan,
            loggedEpochDays = insights.summaries.map { it.localEpochDay }.toSet(),
            lighterWeek = LighterWeek.isCurrent(
                lighterStart,
                insights.weekPlan?.weekStartEpochDay,
            ),
            error = error,
            // Not while a session is live: a lifter mid-workout at 18:20 answering
            // "1 planned session was not done" burns the week's one decision on a
            // session they are in the middle of doing.
            missedWorkPrompt = inProgress == null && MissedWorkPolicy.promptNeeded(overdue, decision),
            overdueCount = overdue.size,
            setupComplete = cadence.setupComplete,
            bodyweightCheckInDue = BodyweightCheckIn.isDueToday(
                todayEpochDay = today,
                preferredDays = cadence.preferredDays,
                weekStart = cadence.preferences.weekStart,
                daysPerWeek = cadence.preferences.trainingDaysPerWeek,
                override = cadence.checkInWeekday,
                log = cadence.bodyweightLog,
            ),
            latestBodyweightKg = cadence.bodyweightLog.lastOrNull()?.kg,
            occurrences = occurrences,
            rules = rules,
            weekStartEpochDay = weekStart,
        )
    }
        // Same reason as Plan: this transform walks every finished session to build the logged
        // set on the first frame after a cold start.
        .flowOn(container.computeDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
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

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    /**
     * The day whose Start was refused because a session is already running.
     *
     * Held so the screen can ask, rather than the app deciding: silently opening whatever is in
     * progress is how tapping Thursday's Pull used to land you in Tuesday's Legs.
     */
    private val _blockedByInProgress = MutableStateFlow<BlockedStart?>(null)
    val blockedByInProgress: StateFlow<BlockedStart?> = _blockedByInProgress.asStateFlow()

    private val _navigateToCardio = MutableStateFlow<String?>(null)
    val navigateToCardio: StateFlow<String?> = _navigateToCardio.asStateFlow()
    private val _navigateToComposer = MutableStateFlow<String?>(null)
    val navigateToComposer: StateFlow<String?> = _navigateToComposer.asStateFlow()
    private val _navigateToEditor = MutableStateFlow<String?>(null)
    val navigateToEditor: StateFlow<String?> = _navigateToEditor.asStateFlow()

    fun onCardioNavigationHandled() {
        _navigateToCardio.value = null
    }

    fun onComposerNavigationHandled() {
        _navigateToComposer.value = null
    }

    fun onEditorNavigationHandled() {
        _navigateToEditor.value = null
    }

    fun startSuggestedDay(day: SuggestedTrainingDay) {
        viewModelScope.launch {
            start(day, PendingOccurrence.plannedOccurrenceId(container, day))
        }
    }

    /**
     * Empty session the lifter fills in real time. Not today's plan, so the
     * pending occurrence is cleared — finishing this must not mark a Plan
     * row DONE.
     */
    fun startFreeWorkout() {
        viewModelScope.launch {
            when (val outcome = container.workoutRepository.startFreeWorkoutSafely()) {
                is StartSessionOutcome.Started -> {
                    // Cleared only on a real start: a Blocked free tap must not unbind
                    // the planned session that is still running.
                    PendingOccurrence.forget(container)
                    actionError.value = null
                    _navigateToSession.value = outcome.session.id
                }
                is StartSessionOutcome.Blocked ->
                    _blockedByInProgress.value = BlockedStart(
                        day = SuggestedTrainingDay(
                            epochDay = todayEpochDay(),
                            dayOfWeek = Weekday.fromEpochDay(todayEpochDay()),
                            isRest = false,
                            focusKind = SessionFocusKind.FULL_BODY,
                            focusTitle = "Free workout",
                            routineId = null,
                            routineName = "Free workout",
                            reason = "",
                            emphasisMuscles = emptyList(),
                            confidence = ScheduleConfidence.HIGH,
                        ),
                        sessionId = outcome.inProgress.id,
                    )
                is StartSessionOutcome.Unavailable ->
                    actionError.value = outcome.message
            }
        }
    }

    fun applyMissedWork(choice: MissedWorkChoice) {
        viewModelScope.launch {
            val weekStart = uiState.value.weekPlan?.weekStartEpochDay ?: return@launch
            val now = time.captureNow()
            val nowMinutes = time.wallMinutesOfDay(now.instantMillis, now.zoneId)
            runCatching {
                container.plannerRepository.applyMissedWork(
                    choice = choice,
                    weekStart = CivilDate.fromEpochDay(weekStart),
                    todayEpochDay = todayEpochDay(),
                    nowMinutesOfDay = nowMinutes,
                    deviceZoneId = now.zoneId,
                    nowMs = now.instantMillis,
                )
            }.onFailure { actionError.value = "Could not save that decision. Try again." }
        }
    }

    fun startOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            val occurrence = container.plannerRepository.getOccurrence(occurrenceId) ?: return@launch
            val today = todayEpochDay()
            val startId = if (MoveToToday.isLeftover(occurrence, today)) {
                when (val moved = container.plannerRepository.moveOccurrenceToDay(occurrenceId, today)) {
                    is MoveToToday.Outcome.Relocate -> moved.created.id
                    is MoveToToday.Outcome.AlreadyThere -> moved.occurrence.id
                    is MoveToToday.Outcome.Blocked -> {
                        actionError.value = moved.message
                        return@launch
                    }
                    null -> return@launch
                }
            } else {
                occurrenceId
            }
            when (val outcome = container.startOccurrence(startId)) {
                is StartOccurrenceOutcome.OpenWorkout -> {
                    PendingOccurrence.bindForSession(
                        container,
                        outcome.occurrenceId,
                        outcome.sessionId,
                    )
                    actionError.value = null
                    _navigateToSession.value = outcome.sessionId
                }
                is StartOccurrenceOutcome.OpenCardio -> {
                    PendingOccurrence.forget(container)
                    actionError.value = null
                    _navigateToCardio.value = outcome.sessionId
                }
                is StartOccurrenceOutcome.OpenComposer -> {
                    PendingOccurrence.bind(container, outcome.occurrenceId)
                    _navigateToComposer.value = "mixed"
                }
                is StartOccurrenceOutcome.Blocked ->
                    _blockedByInProgress.value = BlockedStart(
                        day = outcome.day,
                        sessionId = outcome.inProgressSessionId,
                        occurrenceId = outcome.occurrenceId,
                    )
                is StartOccurrenceOutcome.Failed -> actionError.value = outcome.message
                StartOccurrenceOutcome.Missing -> Unit
            }
        }
    }

    private suspend fun start(day: SuggestedTrainingDay, occurrenceId: String? = null) {
        when (val outcome = container.startTrainingDay(day)) {
            is StartDayOutcome.Open -> {
                // The binding is armed only now, for exactly this session. Arming
                // before the outcome let a Blocked start leave the intent live, so
                // finishing an unrelated session marked the wrong plan row DONE.
                if (occurrenceId != null) {
                    PendingOccurrence.bindForSession(container, occurrenceId, outcome.sessionId)
                } else {
                    PendingOccurrence.forget(container)
                }
                actionError.value = null
                _navigateToSession.value = outcome.sessionId
            }
            is StartDayOutcome.Blocked ->
                _blockedByInProgress.value = BlockedStart(
                    day = day,
                    sessionId = outcome.inProgressSessionId,
                    occurrenceId = occurrenceId,
                )
            is StartDayOutcome.Failed -> actionError.value = outcome.message
            StartDayOutcome.Ignored -> Unit
        }
    }

    fun resumeBlocked() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        _navigateToSession.value = blocked.sessionId
    }

    fun discardBlockedAndStart() {
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        viewModelScope.launch {
            when (val result = container.discardWorkout(blocked.sessionId)) {
                DiscardOutcome.Discarded -> {
                    PendingOccurrence.forgetIfSession(container, blocked.sessionId)
                    start(blocked.day, blocked.occurrenceId)
                }
                is DiscardOutcome.Failed -> actionError.value = result.message
            }
        }
    }

    fun dismissBlockedStart() {
        _blockedByInProgress.value = null
    }

    fun dismissError() {
        actionError.value = null
    }

    fun skipOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            runCatching {
                val occurrence = container.plannerRepository.getOccurrence(occurrenceId)
                    ?: return@runCatching
                if (!MoveToToday.isLeftover(occurrence, todayEpochDay())) return@runCatching
                container.plannerRepository.skipOccurrence(occurrenceId)
            }.onSuccess { actionError.value = null }
                .onFailure { actionError.value = "Could not skip that session. Try again." }
        }
    }

    fun addDaySession(epochDay: Long, add: HomeDayAdd, once: Boolean) {
        viewModelScope.launch {
            runCatching {
                val today = todayEpochDay()
                val nowMinutes = time.wallMinutesOfDay(
                    time.nowMillis(),
                    time.defaultZoneId(),
                )
                when (add) {
                    is HomeDayAdd.Workout -> DayBlocks.addStrength(
                        planner = container.plannerRepository,
                        schedule = container.scheduleRepository,
                        preferences = container.preferencesRepository,
                        epochDay = epochDay,
                        routineId = add.routineId,
                        once = once,
                        todayEpochDay = today,
                        nowMinutes = nowMinutes,
                    )
                    HomeDayAdd.NewWorkout -> {
                        val routineId = DayBlocks.composeWorkout(
                            planner = container.plannerRepository,
                            schedule = container.scheduleRepository,
                            routines = container.routineRepository,
                            preferences = container.preferencesRepository,
                            epochDay = epochDay,
                            once = once,
                            todayEpochDay = today,
                            nowMinutes = nowMinutes,
                        )
                        _navigateToEditor.value = routineId
                    }
                    is HomeDayAdd.Cardio -> DayBlocks.addCardio(
                        planner = container.plannerRepository,
                        preferences = container.preferencesRepository,
                        epochDay = epochDay,
                        type = add.type,
                        once = once,
                        todayEpochDay = today,
                        nowMinutes = nowMinutes,
                    )
                    is HomeDayAdd.Aux -> AuxiliaryBlocks.add(
                        planner = container.plannerRepository,
                        routines = container.routineRepository,
                        exercises = container.exerciseRepository,
                        preferences = container.preferencesRepository,
                        epochDay = epochDay,
                        packId = add.packId,
                        once = once,
                        todayEpochDay = today,
                        nowMinutes = nowMinutes,
                    )
                }
            }.onSuccess { actionError.value = null }
                .onFailure { actionError.value = "Could not add that session. Try again." }
        }
    }

    fun addExtra(epochDay: Long, packId: String) {
        addDaySession(epochDay, HomeDayAdd.Aux(packId), once = true)
    }

    fun moveDayBlock(items: List<AgendaItem>, occurrenceId: String, delta: Int) {
        viewModelScope.launch {
            val from = items.indexOfFirst { it.occurrence.id == occurrenceId }
            val moves = DayBlockOrder.move(items, from, delta)
            if (moves.isEmpty()) return@launch
            runCatching {
                container.plannerRepository.applyDayOrder(moves)
            }.onSuccess { actionError.value = null }
                .onFailure { actionError.value = "Could not reorder that session. Try again." }
        }
    }

    /**
     * Arms the Plan tab to preview a suggested week.
     *
     * The flag is app-scoped rather than a nav argument because the tap and the arrival are
     * separated by a navigation; the Plan tab consumes it once. Nothing is persisted by this —
     * accepting the preview is still the only thing that writes a slot.
     */
    fun requestWeekSuggestion() {
        container.pendingWeekSuggestion.value = true
    }

    /**
     * Arms the Plan tab to replay stored answers on the routines already here.
     *
     * Same idiom as [requestWeekSuggestion]: the tap and the arrival are a navigation
     * apart. Accepting the preview is still the only write.
     */
    fun requestAnswerReplay() {
        container.pendingAnswerReplay.value = true
    }

    data class BlockedStart(
        val day: SuggestedTrainingDay,
        val sessionId: String,
        val occurrenceId: String? = null,
    )

    fun recordBodyweight(kg: Double) {
        viewModelScope.launch {
            container.preferencesRepository.recordBodyweight(kg, todayEpochDay())
        }
    }

    private data class HomeCadence(
        val preferences: com.sinura.personaltrainer.domain.SchedulePreferences,
        val preferredDays: Set<Weekday>,
        val bodyweightLog: List<com.sinura.personaltrainer.domain.BodyweightEntry>,
        val checkInWeekday: Weekday?,
        val setupComplete: Boolean,
    )
}

sealed class HomeDayAdd {
    data class Workout(val routineId: String) : HomeDayAdd()
    data object NewWorkout : HomeDayAdd()
    data class Cardio(val type: CardioType) : HomeDayAdd()
    data class Aux(val packId: String) : HomeDayAdd()
}
