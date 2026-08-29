package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.BodyweightCheckIn
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.latest
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import kotlinx.coroutines.Dispatchers
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
    val heatSnapshot: BodyHeatSnapshot? = null,
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
    /** The block this week belongs to, or null when the lifter is not in one. */
    val block: TrainingBlock? = null,
    val lighterWeek: Boolean = false,
    val error: String? = null,
    val agenda: List<com.sinura.personaltrainer.domain.AgendaItem> = emptyList(),
    val missedWorkPrompt: Boolean = false,
    val overdueCount: Int = 0,
    val twoADayEpochDays: Set<Long> = emptySet(),
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
                container.preferencesRepository.trainingBlock,
                container.preferencesRepository.lighterWeekStartEpochDay,
                combine(
                    container.plannerRepository.observeOccurrences(),
                    container.plannerRepository.observeRules(),
                    container.plannerRepository.observeDecisions(),
                ) { occurrences, rules, decisions -> Triple(occurrences, rules, decisions) },
            ) { block, lighterStart, planner -> Triple(block, lighterStart, planner) },
            combine(
                container.preferencesRepository.schedulePreferences,
                container.preferencesRepository.preferredDays,
                container.preferencesRepository.bodyweightLog,
                container.preferencesRepository.bodyweightCheckInWeekday,
                container.preferencesRepository.onboardingComplete,
            ) { preferences, preferredDays, log, checkIn, setupComplete ->
                HomeCadence(preferences, preferredDays, log, checkIn, setupComplete)
            },
        ) { plannerBlock, cadence -> plannerBlock to cadence },
    ) { insights, inProgress, error, extras ->
        val block = extras.first.first
        val lighterStart = extras.first.second
        val occurrences = extras.first.third.first
        val rules = extras.first.third.second
        val decisions = extras.first.third.third
        val cadence = extras.second
        val today = todayEpochDay()
        val now = JvmTime.captureNow()
        val nowMinutes = JvmTime.wallMinutesOfDay(now.instantMillis, now.zoneId)
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
            heatSnapshot = insights.snapshot,
            // Home already devotes a section to the ready-to-progress lifts, so the card that
            // only says "some lifts are ready" is noise next to the list naming them.
            recommendations = insights.recommendations.filterNot { rec ->
                rec.id == "progression-ready" && insights.hints.isNotEmpty()
            },
            weekPlan = insights.weekPlan,
            loggedEpochDays = insights.summaries.map { it.localEpochDay }.toSet(),
            block = block,
            lighterWeek = LighterWeek.isCurrent(
                lighterStart,
                insights.weekPlan?.weekStartEpochDay,
            ),
            error = error,
            agenda = DailyAgenda.forDay(
                today,
                occurrences,
                rules,
                insights.routines.associate { it.id to it.name },
            ),
            // Not while a session is live: a lifter mid-workout at 18:20 answering
            // "1 planned session was not done" burns the week's one decision on a
            // session they are in the middle of doing.
            missedWorkPrompt = inProgress == null && MissedWorkPolicy.promptNeeded(overdue, decision),
            overdueCount = overdue.size,
            twoADayEpochDays = DailyAgenda.twoADayEpochDays(weekOcc),
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
        .flowOn(Dispatchers.Default)
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

    fun onCardioNavigationHandled() {
        _navigateToCardio.value = null
    }

    fun onComposerNavigationHandled() {
        _navigateToComposer.value = null
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
            val now = JvmTime.captureNow()
            val nowMinutes = JvmTime.wallMinutesOfDay(now.instantMillis, now.zoneId)
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
            val toStart = container.plannerRepository.getOccurrence(startId) ?: return@launch
            val rule = container.plannerRepository.getRule(toStart.ruleId)
            when (rule?.modality ?: ScheduleModality.STRENGTH) {
                ScheduleModality.CARDIO -> {
                    val now = JvmTime.captureNow()
                    val type = ScheduleKind.cardioTypeOrRun(rule?.templateId)
                    val block = CardioBlock(
                        id = IdFactory.Uuid.newId(),
                        sortOrder = 0,
                        type = type,
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
                    when (val write = container.startLiveActivity(CardioCopy.name(type), listOf(block), now, toStart.id)) {
                        is ActivityWrite.Accepted -> {
                            PendingOccurrence.forget(container)
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
                            actionError.value = null
                            _navigateToCardio.value = write.session.id
                        }
                        is ActivityWrite.Rejected -> actionError.value = write.reason
                    }
                }
                ScheduleModality.MIXED -> {
                    PendingOccurrence.bind(container, toStart.id)
                    _navigateToComposer.value = "mixed"
                }
                ScheduleModality.STRENGTH -> {
                    val item = AgendaItem(toStart, rule)
                    start(
                        SuggestedTrainingDay(
                            epochDay = toStart.localEpochDay,
                            dayOfWeek = Weekday.fromEpochDay(toStart.localEpochDay),
                            isRest = false,
                            focusKind = rule?.focusKind ?: SessionFocusKind.FULL_BODY,
                            focusTitle = item.title,
                            routineId = rule?.routineId,
                            routineName = rule?.routineId?.let { id ->
                                uiState.value.routines.firstOrNull { it.id == id }?.name
                            },
                            reason = "Planned.",
                            emphasisMuscles = emptyList(),
                            confidence = ScheduleConfidence.HIGH,
                            slotId = null,
                        ),
                        occurrenceId = toStart.id,
                    )
                }
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
