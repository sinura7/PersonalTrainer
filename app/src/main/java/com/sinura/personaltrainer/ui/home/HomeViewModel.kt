package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.UsedReminder
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DayBlockOrder
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.HomeLogged
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MissedWorkPolicy
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.BodyweightCheckIn
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.data.repository.AuxiliaryBlocks
import com.sinura.personaltrainer.data.repository.DayBlocks
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.data.repository.combineHealth
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.StartCardioOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import com.sinura.personaltrainer.workout.StartOccurrenceOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeReadState { WAITING, CURRENT, UNAVAILABLE, STALE }

data class HomeStartState(
    val pending: Boolean = false,
    val error: String? = null,
    val canRetry: Boolean = false,
    /** The original authored occurrence, even after its leftover has moved onto today. */
    val occurrenceId: String? = null,
    /** Only an acknowledged discard makes the old workout unavailable to Resume. */
    val discardedOriginal: Boolean = false,
    /** A rendered action belongs to this admitted attempt, including its failed result. */
    val attemptId: Long = 0L,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val inProgress: WorkoutSession? = null,
    val liveActivity: ActivitySession? = null,
    val routines: List<Routine> = emptyList(),
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
    /** Finished sessions grouped by local epoch day (masthead + day board). */
    val loggedByDay: Map<Long, List<SessionSummary>> = emptyMap(),
    val lighterWeek: Boolean = false,
    val error: String? = null,
    val missedWorkPrompt: Boolean = false,
    val overdueCount: Int = 0,
    /** False until a plan or custom week is accepted. Home stays quiet, not a sheet. */
    val setupComplete: Boolean = true,
    val bodyweightCheckInDue: Boolean = false,
    val latestBodyweightKg: Double? = null,
    val occurrences: List<com.sinura.personaltrainer.domain.ScheduleOccurrence> = emptyList(),
    val rules: List<com.sinura.personaltrainer.domain.ScheduleRule> = emptyList(),
    val weekStartEpochDay: Long = 0L,
    val suggestedExtraEquipment: ExtraEquipment = ExtraEquipment.MIXED,
    val readState: HomeReadState = HomeReadState.WAITING,
    val retryPending: Boolean = false,
    val readProblem: String? = null,
    val bodyweightSavePending: Boolean = false,
    val bodyweightSaveError: String? = null,
    val bodyweightDraftKg: Double? = null,
    val startPending: Boolean = false,
) {
    /** True for a live workout or live cardio — Home's filled Volt hides for both. */
    val sessionLive: Boolean get() = inProgress != null || liveActivity != null
    val mutationEnabled: Boolean get() = readState == HomeReadState.CURRENT && !retryPending && !startPending
}

/** A leftover Skip, held only long enough for the undo host to offer it back. */
data class SkippedDayOffer(
    val occurrenceId: String,
    val previousStatus: OccurrenceStatus,
    val title: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)
    private val bodyweightSave = MutableStateFlow(BodyweightSave())
    private val _startState = MutableStateFlow(HomeStartState())
    val startState: StateFlow<HomeStartState> = _startState.asStateFlow()
    private var startAttemptSerial = 0L
    private var startCommand: StartCommand? = null
    private var blockedStartCommand: StartCommand? = null
    private val resolvedOccurrenceIds = mutableMapOf<String, String>()
    private val _bodyweightSaved = MutableStateFlow(false)
    val bodyweightSaved: StateFlow<Boolean> = _bodyweightSaved.asStateFlow()
    private val undoableSkip = MutableStateFlow<SkippedDayOffer?>(null)
    val skippedDay: StateFlow<SkippedDayOffer?> = undoableSkip.asStateFlow()

    private val readGeneration = MutableStateFlow(0L)
    private val board = MutableStateFlow(HomeUiState())
    private var lastCompleteBoard: HomeUiState? = null
    // Kept on the ViewModel's main dispatcher, before any presentation computation. A stale
    // enabled frame must never admit a queued tap after a required read has failed.
    private var authoritativeReadState = HomeReadState.WAITING
    private var authoritativeRetryPending = false
    private var healthRevision = 0L
    private val terminalReminderRequests = mutableSetOf<String>()

    val uiState: StateFlow<HomeUiState> = combine(
        board,
        actionError,
        combine(bodyweightSave, _startState) { weight, start -> weight to start },
    ) { state, error, fields ->
        state.copy(
            error = error,
            bodyweightSavePending = fields.first.pending,
            bodyweightSaveError = fields.first.error,
            bodyweightDraftKg = fields.first.kg,
            startPending = fields.second.pending,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        // This safety collector belongs to the ViewModel, not its current composition. The
        // last complete board survives a navigation/rotation and a retry never shows a partly
        // refreshed mixture as current. Producers still own sharing and their retry identity.
        viewModelScope.launch {
            readGeneration.flatMapLatest { generation ->
                flow { emitAll(requiredReads(retry = generation != 0L)) }
                    .catch { thrown ->
                        if (thrown is CancellationException) throw thrown
                        AppLog.w(TAG, "Reading Home failed", thrown)
                        emit(DataHealth.Unavailable("Home"))
                    }
                    .map<DataHealth<HomeInputs>, DataHealth<HomeInputs>?> { it }
                    .onStart { emit(null) }
                    .map { generation to it }
            }.map { (generation, health) ->
                val revision = if (generation == readGeneration.value) ++healthRevision else healthRevision
                // collectLatest cancels AND joins the old compute action. Publish the safety
                // refusal before that join: even slow cancellation must not leave an old
                // enabled frame able to start or mutate anything.
                if (generation == readGeneration.value) {
                    when (health) {
                        null -> showReadProblem(waiting = true)
                        is DataHealth.Unavailable, is DataHealth.Degraded -> showReadProblem()
                        is DataHealth.Available -> Unit
                    }
                }
                Triple(generation, revision, health)
            // Keep health publication live even if a healthy replacement has already
            // begun joining a cancelled computation. Only the latest pending work is kept.
            }.buffer(Channel.CONFLATED).collectLatest { (generation, revision, health) ->
                if (generation != readGeneration.value) return@collectLatest
                when (health) {
                    null, is DataHealth.Unavailable, is DataHealth.Degraded -> Unit
                    is DataHealth.Available -> {
                        val rendered = runCatchingCancellable {
                            withContext(container.computeDispatcher) { completeBoard(health.value) }
                        }.getOrElse { thrown ->
                            AppLog.w(TAG, "Preparing Home failed", thrown)
                            if (generation == readGeneration.value && revision == healthRevision) showReadProblem()
                            return@collectLatest
                        }
                        if (generation != readGeneration.value || revision != healthRevision) return@collectLatest
                        lastCompleteBoard = rendered
                        authoritativeReadState = HomeReadState.CURRENT
                        authoritativeRetryPending = false
                        board.value = rendered
                    }
                }
            }
        }
    }

    /** A deliberate retry restarts every required read. Old shared replay cannot satisfy it. */
    fun retryRead(): Boolean {
        if (authoritativeRetryPending || authoritativeReadState == HomeReadState.CURRENT ||
            authoritativeReadState == HomeReadState.WAITING
        ) return false
        authoritativeRetryPending = true
        showReadProblem(waiting = true)
        readGeneration.value += 1L
        return true
    }

    private fun showReadProblem(waiting: Boolean = false) {
        if (!waiting) authoritativeRetryPending = false
        val held = lastCompleteBoard
        authoritativeReadState = when {
            held != null -> HomeReadState.STALE
            waiting && !authoritativeRetryPending -> HomeReadState.WAITING
            else -> HomeReadState.UNAVAILABLE
        }
        board.value = (held ?: HomeUiState()).copy(
            isLoading = authoritativeReadState == HomeReadState.WAITING,
            readState = authoritativeReadState,
            retryPending = authoritativeRetryPending,
            readProblem = when (authoritativeReadState) {
                HomeReadState.WAITING -> null
                HomeReadState.STALE ->
                    "Home could not refresh. Showing the last complete view. Retry before making changes."
                else -> "Home could not be read. Retry before starting or changing anything."
            },
        )
    }

    private fun canMutate(): Boolean =
        authoritativeReadState == HomeReadState.CURRENT && !authoritativeRetryPending && !_startState.value.pending

    private fun requiredReads(retry: Boolean): Flow<DataHealth<HomeInputs>> {
        val live = combineHealth(
            container.workoutRepository.observeInProgressHealth(),
            if (retry) container.activityRepository.retryLiveHealth()
            else container.activityRepository.observeLiveHealth(),
        ) { workout, cardio -> workout to cardio }
        val planner = combineHealth(
            combineHealth(
                container.plannerRepository.observeOccurrencesHealth(),
                container.plannerRepository.observeRulesHealth(),
            ) { occurrences, rules -> occurrences to rules },
            container.plannerRepository.observeDecisionsHealth(),
        ) { pair, decisions -> Triple(pair.first, pair.second, decisions) }
        // Decode one raw snapshot. Combining safe-derived defaults with a separate raw
        // health signal lets those defaults race ahead of a failed-store warning.
        val cadence = combineHealth(
            container.preferencesRepository.observeHomePreferencesHealth(),
            container.preferencesRepository.bodyweightLogHealth,
        ) { preferences, log ->
            preferences.lighterWeekStartEpochDay to HomeCadence(
                preferences.schedulePreferences, preferences.preferredDays, log,
                preferences.bodyweightCheckInWeekday, preferences.onboardingComplete,
                ExtraEquipment.fromPreferences(preferences.coachPreferences.availableEquipment),
            )
        }
        val extras = combineHealth(planner, cadence) { planned, checkIn ->
            (checkIn.first to planned) to checkIn.second
        }
        return combineHealth(
            combineHealth(
                if (retry) container.trainingInsights.retrySharedHealth()
                else container.trainingInsights.observeSharedHealth(),
                live,
            ) { insights, activities -> insights to activities },
            extras,
        ) { main, additional -> HomeInputs(main.first, main.second, additional) }
    }

    private fun completeBoard(input: HomeInputs): HomeUiState {
        val insights = input.insights
        val livePair = input.live
        val extras = input.extras
        val inProgress = livePair.first
        val liveActivity = livePair.second
        val sessionLive = inProgress != null || liveActivity != null
        val lighterStart = extras.first.first
        val occurrences = extras.first.second.first
        val rules = extras.first.second.second
        val decisions = extras.first.second.third
        val cadence = extras.second
        val today = todayEpochDay()
        val weekStart = insights.weekPlan?.weekStartEpochDay
            ?: CivilDate.fromEpochDay(today).previousOrSame(
                insights.weekPlan?.preferences?.weekStart
                    ?: cadence.preferences.weekStart,
            ).epochDay
        val weekOcc = occurrences.filter { it.localEpochDay in weekStart..(weekStart + 6) }
        val overdue = MissedWorkPolicy.overdue(weekOcc, today)
        val decision = decisions.firstOrNull { it.weekStartEpochDay == weekStart }
        return HomeUiState(
            isLoading = false,
            inProgress = inProgress,
            liveActivity = liveActivity,
            routines = insights.routines,
            // The ready-to-progress list is off Home; that section was extra detail the
            // first screen did not owe anyone, and it belongs with the body readout. The
            // card that names the state without listing every lift was previously filtered
            // out as noise beside that list. With the list gone it is the only signal left,
            // so it stands.
            recommendations = insights.recommendations,
            weekPlan = insights.weekPlan,
            loggedEpochDays = insights.summaries.map { it.localEpochDay }.toSet(),
            loggedByDay = HomeLogged.byDay(insights.summaries),
            lighterWeek = LighterWeek.isCurrent(
                lighterStart,
                insights.weekPlan?.weekStartEpochDay,
            ),
            // Not while a session is live: a lifter mid-workout at 18:20 answering
            // "1 planned session was not done" burns the week's one decision on a
            // session they are in the middle of doing.
            missedWorkPrompt = !sessionLive && MissedWorkPolicy.promptNeeded(overdue, decision),
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
            suggestedExtraEquipment = cadence.suggestedExtra,
            readState = HomeReadState.CURRENT,
        )
    }

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
        releaseStartAfterNavigation()
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
        releaseStartAfterNavigation()
    }

    fun onComposerNavigationHandled() {
        _navigateToComposer.value = null
        releaseStartAfterNavigation()
    }

    fun onEditorNavigationHandled() {
        _navigateToEditor.value = null
        releaseStartAfterNavigation()
    }

    private val _reviewOccurrenceId = MutableStateFlow<String?>(null)
    val reviewOccurrenceId: StateFlow<String?> = _reviewOccurrenceId.asStateFlow()
    private val _focusEpochDay = MutableStateFlow<Long?>(null)
    val focusEpochDay: StateFlow<Long?> = _focusEpochDay.asStateFlow()

    fun onReviewOccurrenceHandled() {
        _reviewOccurrenceId.value = null
    }

    fun onFocusEpochDayHandled() {
        _focusEpochDay.value = null
    }

    /**
     * Body tap on a reminder: select the day and open the ADR-018
     * confirm. Starts nothing.
     */
    fun reviewOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            val occurrence = runCatchingCancellable { container.plannerRepository.getOccurrence(occurrenceId) }
                .getOrElse { thrown ->
                    AppLog.w(TAG, "Reading the planned session to review failed", thrown)
                    actionError.value = ReminderCopy.REVIEW_FAILED
                    return@launch
                }
            if (occurrence == null) {
                actionError.value = ReminderCopy.GONE
                return@launch
            }
            val today = todayEpochDay()
            val item = AgendaItem(occurrence, rule = null, routineName = null)
            if (!DailyAgenda.canOpenStart(item, today)) {
                actionError.value = ReminderCopy.GONE
                return@launch
            }
            actionError.value = null
            _focusEpochDay.value = if (MoveToToday.isLeftover(occurrence, today)) {
                today
            } else {
                occurrence.localEpochDay
            }
            _reviewOccurrenceId.value = occurrenceId
        }
    }

    /** Presentation may lag behind this owner; these queries also protect queued callbacks. */
    fun canDismissStart(): Boolean = !_startState.value.pending && !hasQueuedStartNavigation()

    fun canEditStartChoice(): Boolean = canDismissStart() && _blockedByInProgress.value == null

    fun canResumeBlockedStart(): Boolean =
        canDismissStart() && !_startState.value.discardedOriginal && _blockedByInProgress.value != null

    /** An explicit leave abandons this attempt, never a write still in progress. */
    fun dismissStart(expectedAttemptId: Long? = null): Boolean {
        if (!matchesStartAttempt(expectedAttemptId = expectedAttemptId) || !canDismissStart()) return false
        _blockedByInProgress.value = null
        clearStartAttempt()
        return true
    }

    /** Retry owns the same command and any already acknowledged relocation or discard. */
    fun retryStart(expectedAttemptId: Long? = null): Boolean {
        if (!matchesStartAttempt(expectedAttemptId = expectedAttemptId)) return false
        val command = startCommand ?: return false
        if (!_startState.value.canRetry || _startState.value.error == null) return false
        return admitStart(
            command = command,
            allowBlocked = command is StartCommand.Discard,
            explicitRetry = true,
        )
    }

    private fun hasQueuedStartNavigation(): Boolean =
        _navigateToSession.value != null || _navigateToCardio.value != null ||
            _navigateToComposer.value != null || _navigateToEditor.value != null

    private fun matchesStartAttempt(expectedAttemptId: Long?): Boolean =
        expectedAttemptId == null || expectedAttemptId == _startState.value.attemptId

    private fun matchesBlockedStart(expectedBlocked: BlockedStart?, expectedAttemptId: Long?): Boolean =
        matchesStartAttempt(expectedAttemptId = expectedAttemptId) &&
            (expectedBlocked == null || expectedBlocked == _blockedByInProgress.value)

    private fun clearStartAttempt() {
        val ownedError = _startState.value.error
        if (ownedError != null && actionError.value == ownedError) actionError.value = null
        _startState.value.occurrenceId?.let { resolvedOccurrenceIds.remove(it) }
        startCommand = null
        blockedStartCommand = null
        _startState.value = HomeStartState(attemptId = _startState.value.attemptId)
    }

    private fun releaseStartAfterNavigation() {
        if (canDismissStart() && _blockedByInProgress.value == null && _startState.value.error == null) {
            clearStartAttempt()
        }
    }

    private fun admitStart(
        command: StartCommand,
        allowBlocked: Boolean = false,
        explicitRetry: Boolean = false,
    ): Boolean {
        if (!canMutate() || hasQueuedStartNavigation()) return false
        val blocked = _blockedByInProgress.value
        if (blocked != null && !allowBlocked) return false
        if (allowBlocked && (command !is StartCommand.Discard || blocked != command.blocked)) return false
        val retained = startCommand
        if (_startState.value.error != null) {
            // A stale Start callback cannot turn an immediate failure into an implicit retry.
            if (!explicitRetry || retained != command || !_startState.value.canRetry) return false
        }
        val ownedError = _startState.value.error
        if (ownedError != null && actionError.value == ownedError) actionError.value = null
        startCommand = command
        // Reserve before launch: a second callback from the same rendered frame is refused.
        _startState.value = HomeStartState(
            pending = true,
            occurrenceId = command.originalOccurrenceId,
            discardedOriginal = (command as? StartCommand.Discard)?.discarded == true,
            attemptId = ++startAttemptSerial,
        )
        viewModelScope.launch {
            runCatchingCancellable { executeStart(command = command) }
                .onFailure { thrown ->
                    AppLog.w(TAG, "Starting from Home failed", thrown)
                    failStart(message = ReminderCopy.START_FAILED)
                }
            _startState.value = _startState.value.copy(pending = false)
            releaseStartAfterNavigation()
        }
        return true
    }

    private fun failStart(message: String, canRetry: Boolean = true) {
        // The repository's unavailable bucket covers both reads and writes during a start.
        // Home must not diagnose an insertion failure as a failed live-workout read.
        val startError = if (message == DataHealthCopy.START_UNAVAILABLE) ReminderCopy.START_FAILED else message
        actionError.value = startError
        _startState.value = _startState.value.copy(error = startError, canRetry = canRetry)
    }

    private fun markStartBlocked(blocked: BlockedStart) {
        val requested = (startCommand as? StartCommand.Discard)?.requested ?: startCommand
        blockedStartCommand = requested
        startCommand = requested
        _startState.value = _startState.value.copy(error = null, canRetry = false, discardedOriginal = false)
        _blockedByInProgress.value = blocked
    }

    private fun clearBlockedAfterOpen() {
        _blockedByInProgress.value = null
        blockedStartCommand = null
        actionError.value = null
    }

    private suspend fun executeStart(command: StartCommand) {
        when (command) {
            StartCommand.Free -> openWorkout(
                outcome = container.workoutRepository.startFreeWorkoutSafely(),
                blockedDay = freeDay(),
            )
            is StartCommand.Routine -> startRoutineCommand(routineId = command.routineId)
            is StartCommand.Cardio -> startCardioCommand(type = command.type)
            is StartCommand.Aux -> startAuxCommand(packId = command.packId)
            is StartCommand.Suggested -> start(
                day = command.day,
                occurrenceId = PendingOccurrence.plannedOccurrenceId(deps = container, day = command.day),
            )
            is StartCommand.Occurrence -> startOccurrenceCommand(command = command)
            is StartCommand.Discard -> discardAndStartCommand(command = command)
        }
    }

    private suspend fun discardAndStartCommand(command: StartCommand.Discard) {
        if (!command.discarded) {
            val planned = command.requested as? StartCommand.Occurrence
            if (planned != null && plannedStartId(
                    occurrenceId = planned.occurrenceId,
                    reminder = planned.deliveryId?.let { ReminderTap(occurrenceId = planned.occurrenceId, deliveryId = it) },
                ) == null
            ) return
            when (val result = container.discardWorkout(sessionId = command.blocked.sessionId)) {
                DiscardOutcome.Discarded -> {
                    // Only the acknowledgement advances the phase. A failed replacement start
                    // retries that start without deleting the old workout again.
                    startCommand = command.copy(discarded = true)
                    _startState.value = _startState.value.copy(discardedOriginal = true)
                    PendingOccurrence.forgetIfSession(deps = container, sessionId = command.blocked.sessionId)
                }
                is DiscardOutcome.Failed -> {
                    failStart(message = result.message)
                    return
                }
            }
        }
        executeStart(command = command.requested)
    }

    fun startSuggestedDay(day: SuggestedTrainingDay): Boolean {
        // A confirmation owns the day it showed, even when selection or the plan changes.
        // Consult the completed board directly: the presentation flow may still be behind it.
        if (!canMutate() || board.value.weekPlan?.dayOn(day.epochDay) != day) return false
        return admitStart(command = StartCommand.Suggested(day = day))
    }

    /**
     * Empty session the lifter fills in real time. Not today's plan, so the
     * pending occurrence is cleared — finishing this must not mark a Plan
     * row DONE.
     */
    fun startFreeWorkout(): Boolean {
        return admitStart(command = StartCommand.Free)
    }

    fun startRoutine(routineId: String): Boolean {
        return admitStart(command = StartCommand.Routine(routineId = routineId))
    }

    private suspend fun startRoutineCommand(routineId: String) {
        val routine = runCatchingCancellable { container.routineRepository.getById(id = routineId) }
            .getOrElse { thrown ->
                AppLog.w(TAG, "Reading the routine to start failed", thrown)
                failStart(message = ReminderCopy.START_FAILED)
                return
            }
        if (routine == null) {
            failStart(message = "That routine is no longer available.", canRetry = false)
            return
        }
        if (routine.exercises.isEmpty()) {
            failStart(message = SessionOrderCopy.NEED_A_LIFT, canRetry = false)
            return
        }
        openWorkout(
            outcome = container.workoutRepository.startRoutineSafely(routine = routine),
            blockedDay = freeDay(title = routine.name, routineId = routine.id),
        )
    }

    fun startCardio(type: CardioType): Boolean {
        return admitStart(command = StartCommand.Cardio(type = type))
    }

    private suspend fun startCardioCommand(type: CardioType) {
        when (
            val outcome = container.startLiveCardio(
                type = type,
                now = time.captureNow(),
                title = CardioCopy.name(type = type),
            )
        ) {
            is StartCardioOutcome.Open -> {
                PendingOccurrence.forget(deps = container)
                clearBlockedAfterOpen()
                _navigateToCardio.value = outcome.sessionId
            }
            is StartCardioOutcome.Rejected -> failStart(message = outcome.reason)
        }
    }

    fun startAux(packId: String): Boolean {
        return admitStart(command = StartCommand.Aux(packId = packId))
    }

    private suspend fun startAuxCommand(packId: String) {
        val pack = AuxiliaryPacks.byId(id = packId)
        if (pack == null) {
            failStart(message = "That extra is no longer available.", canRetry = false)
            return
        }
        val routine = runCatchingCancellable {
            val routineId = AuxiliaryBlocks.ensureRoutine(
                pack = pack,
                routines = container.routineRepository,
                exercises = container.exerciseRepository,
            )
            container.routineRepository.getById(id = routineId)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Preparing the extra to start failed", thrown)
            failStart(message = ReminderCopy.START_FAILED)
            return
        }
        if (routine == null || routine.exercises.isEmpty()) {
            failStart(message = SessionOrderCopy.NEED_A_LIFT, canRetry = false)
            return
        }
        openWorkout(
            outcome = container.workoutRepository.startRoutineSafely(routine = routine),
            blockedDay = freeDay(title = pack.title, routineId = routine.id),
        )
    }

    private suspend fun openWorkout(
        outcome: StartSessionOutcome,
        blockedDay: SuggestedTrainingDay,
    ) {
        when (outcome) {
            is StartSessionOutcome.Started -> {
                PendingOccurrence.forget(deps = container)
                clearBlockedAfterOpen()
                _navigateToSession.value = outcome.session.id
            }
            is StartSessionOutcome.Blocked ->
                markStartBlocked(
                    blocked = BlockedStart(day = blockedDay, sessionId = outcome.inProgress.id),
                )
            is StartSessionOutcome.Unavailable -> failStart(message = outcome.message)
        }
    }

    private fun freeDay(
        title: String = "Free workout",
        routineId: String? = null,
    ) = SuggestedTrainingDay(
        epochDay = todayEpochDay(),
        dayOfWeek = Weekday.fromEpochDay(todayEpochDay()),
        isRest = false,
        focusKind = SessionFocusKind.FULL_BODY,
        focusTitle = title,
        routineId = routineId,
        routineName = title,
        reason = "",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    fun applyMissedWork(choice: MissedWorkChoice): Boolean {
        if (!canMutate()) return false
        val weekStart = lastCompleteBoard?.weekPlan?.weekStartEpochDay ?: return false
        viewModelScope.launch {
            val now = time.captureNow()
            val nowMinutes = time.wallMinutesOfDay(now.instantMillis, now.zoneId)
            runCatchingCancellable {
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
        return true
    }

    /**
     * A planned session from a reminder's Start ([deliveryId] set) or from Home. The reminder is
     * marked started, and its notification dismissed, only once the start opens: they used to
     * be at the tap, so a start refused because another session was live used the reminder up
     * (audit UI-1). A refused or failed start leaves the reminder as it was, except for a
     * leftover from an earlier day: moving it to today already cancels its reminders.
     */
    fun startOccurrence(occurrenceId: String, deliveryId: String? = null): Boolean {
        return admitStart(command = StartCommand.Occurrence(occurrenceId = occurrenceId, deliveryId = deliveryId))
    }

    private suspend fun startOccurrenceCommand(command: StartCommand.Occurrence) {
        val reminder = command.deliveryId?.let { ReminderTap(occurrenceId = command.occurrenceId, deliveryId = it) }
        val startId = runCatchingCancellable {
            plannedStartId(occurrenceId = command.occurrenceId, reminder = reminder)
        }.getOrElse { thrown ->
            AppLog.w(TAG, "Reading the planned session to start failed", thrown)
            failStart(message = ReminderCopy.START_FAILED)
            return
        } ?: return
        when (val outcome = container.startOccurrence(occurrenceId = startId)) {
            is StartOccurrenceOutcome.OpenWorkout -> {
                PendingOccurrence.bindForSession(
                    deps = container,
                    occurrenceId = outcome.occurrenceId,
                    sessionId = outcome.sessionId,
                )
                markReminderStarted(reminder = reminder)
                clearBlockedAfterOpen()
                _navigateToSession.value = outcome.sessionId
            }
            is StartOccurrenceOutcome.OpenCardio -> {
                PendingOccurrence.forget(deps = container)
                markReminderStarted(reminder = reminder)
                clearBlockedAfterOpen()
                _navigateToCardio.value = outcome.sessionId
            }
            is StartOccurrenceOutcome.OpenComposer -> {
                PendingOccurrence.bind(deps = container, occurrenceId = outcome.occurrenceId)
                markReminderStarted(reminder = reminder)
                clearBlockedAfterOpen()
                _navigateToComposer.value = "mixed"
            }
            is StartOccurrenceOutcome.Blocked -> markStartBlocked(
                blocked = BlockedStart(
                    day = outcome.day,
                    sessionId = outcome.inProgressSessionId,
                    occurrenceId = outcome.occurrenceId,
                    reminder = reminder,
                ),
            )
            is StartOccurrenceOutcome.Failed -> failStart(message = outcome.message)
            StartOccurrenceOutcome.Missing -> reminderGone(reminder = reminder)
        }
    }

    /**
     * A notification already names the intended session. Waiting keeps that handoff alive;
     * a read refusal consumes only this in-app request, without touching its durable delivery
     * or notification. Recovery therefore never turns a refused tap into a delayed start.
     */
    fun dispatchPendingOccurrenceStart(
        occurrenceId: String,
        deliveryId: String? = null,
        requestId: String? = null,
    ): Boolean {
        val token = requestId ?: "legacy:$occurrenceId:$deliveryId"
        if (token in terminalReminderRequests) return true
        if (authoritativeReadState == HomeReadState.WAITING) return false
        terminalReminderRequests += token
        if (!canMutate()) return true
        val requested = StartCommand.Occurrence(occurrenceId = occurrenceId, deliveryId = deliveryId)
        val retained = (startCommand as? StartCommand.Discard)?.requested ?: startCommand
        if (_startState.value.error != null && retained == requested) {
            retryStart()
        } else {
            startOccurrence(occurrenceId = occurrenceId, deliveryId = deliveryId)
        }
        return true
    }

    /**
     * The occurrence to start: [occurrenceId], or today's copy of a leftover from an earlier day.
     * Null when there is nothing to start, already said.
     */
    private suspend fun plannedStartId(occurrenceId: String, reminder: ReminderTap?): String? {
        val resolvedId = resolvedOccurrenceIds[occurrenceId] ?: occurrenceId
        val occurrence = container.plannerRepository.getOccurrence(id = resolvedId)
        val today = todayEpochDay()
        if (occurrence == null || !DailyAgenda.canOpenStart(
                item = AgendaItem(occurrence = occurrence, rule = null, routineName = null),
                todayEpochDay = today,
            )
        ) {
            reminderGone(reminder = reminder)
            return null
        }
        // A relocation acknowledged on another civil day cannot now be bound as today's
        // workout. Leave it available on the board for an explicit new Do it today choice.
        if (resolvedId != occurrenceId && occurrence.localEpochDay != today) {
            failStart(
                message = "The day has changed. Close this start and choose the workout again.",
                canRetry = false,
            )
            return null
        }
        // A deliberate retry uses the copy whose relocation already committed. Without
        // that retained identity, an old MOVED row fails the predicate above.
        if (resolvedId != occurrenceId || !MoveToToday.isLeftover(occurrence = occurrence, todayEpochDay = today)) {
            return resolvedId
        }
        return when (val moved = container.plannerRepository.moveOccurrenceToDay(
            occurrenceId = occurrenceId,
            todayEpochDay = today,
        )) {
            is MoveToToday.Outcome.Relocate -> moved.created.id.also { resolvedOccurrenceIds[occurrenceId] = it }
            is MoveToToday.Outcome.AlreadyThere -> moved.occurrence.id.also { resolvedOccurrenceIds[occurrenceId] = it }
            is MoveToToday.Outcome.Blocked -> {
                failStart(message = moved.message)
                null
            }
            null -> {
                reminderGone(reminder = reminder)
                null
            }
        }
    }

    /**
     * The planned session is no longer there. Its reminder is dismissed, since its Start can
     * never open anything, but not marked started.
     */
    private fun reminderGone(reminder: ReminderTap?) {
        reminder?.let { ReminderNotifications.cancel(context = getApplication(), occurrenceId = it.occurrenceId) }
        failStart(message = ReminderCopy.GONE, canRetry = false)
    }

    /** Starts [day]. The [reminder] that asked for it is used up only if the session opens. */
    private suspend fun start(
        day: SuggestedTrainingDay,
        occurrenceId: String? = null,
        reminder: ReminderTap? = null,
    ) {
        when (val outcome = container.startTrainingDay(day = day)) {
            is StartDayOutcome.Open -> {
                // The binding is armed only now, for exactly this session. Arming
                // before the outcome let a Blocked start leave the intent live, so
                // finishing an unrelated session marked the wrong plan row DONE.
                if (occurrenceId != null) {
                    PendingOccurrence.bindForSession(deps = container, occurrenceId = occurrenceId, sessionId = outcome.sessionId)
                } else {
                    PendingOccurrence.forget(deps = container)
                }
                markReminderStarted(reminder = reminder)
                clearBlockedAfterOpen()
                _navigateToSession.value = outcome.sessionId
            }
            is StartDayOutcome.Blocked ->
                markStartBlocked(
                    blocked = BlockedStart(
                        day = day,
                        sessionId = outcome.inProgressSessionId,
                        occurrenceId = occurrenceId,
                        reminder = reminder,
                    ),
                )
            is StartDayOutcome.Failed -> failStart(message = outcome.message)
            StartDayOutcome.Ignored -> failStart(message = "There is no session to start.", canRetry = false)
        }
    }

    /** The reminder behind a start that has opened, used up ([UsedReminder]). */
    private suspend fun markReminderStarted(reminder: ReminderTap?) {
        reminder ?: return
        UsedReminder.markStarted(
            context = getApplication(),
            deps = container,
            occurrenceId = reminder.occurrenceId,
            deliveryId = reminder.deliveryId,
        )
    }

    fun resumeBlocked(expectedBlocked: BlockedStart? = null, expectedAttemptId: Long? = null) {
        if (!matchesBlockedStart(expectedBlocked = expectedBlocked, expectedAttemptId = expectedAttemptId) ||
            !canResumeBlockedStart()
        ) return
        val blocked = _blockedByInProgress.value ?: return
        _blockedByInProgress.value = null
        clearStartAttempt()
        _navigateToSession.value = blocked.sessionId
    }

    fun discardBlockedAndStart(expectedBlocked: BlockedStart? = null, expectedAttemptId: Long? = null): Boolean {
        if (!matchesBlockedStart(expectedBlocked = expectedBlocked, expectedAttemptId = expectedAttemptId)) return false
        val blocked = _blockedByInProgress.value ?: return false
        if (startCommand is StartCommand.Discard) return retryStart(expectedAttemptId = expectedAttemptId)
        val requested = blockedStartCommand ?: StartCommand.Suggested(day = blocked.day)
        return admitStart(
            command = StartCommand.Discard(blocked = blocked, requested = requested),
            allowBlocked = true,
        )
    }

    fun dismissBlockedStart(expectedBlocked: BlockedStart? = null, expectedAttemptId: Long? = null) {
        if (!matchesBlockedStart(expectedBlocked = expectedBlocked, expectedAttemptId = expectedAttemptId)) return
        dismissStart(expectedAttemptId = expectedAttemptId)
    }

    fun dismissError() {
        if (actionError.value != null && actionError.value == _startState.value.error) {
            dismissStart()
        } else {
            actionError.value = null
        }
    }

    fun skipOccurrence(occurrenceId: String): Boolean {
        if (!canMutate()) return false
        viewModelScope.launch {
            runCatchingCancellable {
                val occurrence = container.plannerRepository.getOccurrence(occurrenceId)
                    ?: return@runCatchingCancellable
                if (!MoveToToday.isLeftover(occurrence, todayEpochDay())) return@runCatchingCancellable
                val previous = container.plannerRepository.skipOccurrence(occurrenceId)
                    ?: return@runCatchingCancellable
                val title = DailyAgenda.forDay(
                    epochDay = occurrence.localEpochDay,
                    occurrences = listOf(occurrence),
                    rules = container.plannerRepository.rules(),
                    routineNames = uiState.value.routines.associate { it.id to it.name },
                ).first().title
                undoableSkip.value = SkippedDayOffer(
                    occurrenceId = occurrenceId,
                    previousStatus = previous,
                    title = title,
                )
            }.onSuccess { actionError.value = null }
                .onFailure { actionError.value = "Could not skip that session. Try again." }
        }
        return true
    }

    fun undoSkipOccurrence(): Boolean {
        if (!canMutate()) return false
        val pending = undoableSkip.value ?: return false
        undoableSkip.value = null
        viewModelScope.launch {
            runCatchingCancellable {
                container.plannerRepository.restoreSkippedOccurrence(
                    occurrenceId = pending.occurrenceId,
                    previousStatus = pending.previousStatus,
                )
            }.onFailure { actionError.value = "Could not restore that session. Try again." }
        }
        return true
    }

    fun onUndoOfferHandled() {
        if (!canMutate()) return
        undoableSkip.value = null
    }

    fun addDaySession(epochDay: Long, add: HomeDayAdd, once: Boolean): Boolean {
        if (!canMutate()) return false
        viewModelScope.launch {
            runCatchingCancellable {
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
        return true
    }

    fun moveDayBlock(items: List<AgendaItem>, occurrenceId: String, delta: Int): Boolean {
        if (!canMutate()) return false
        viewModelScope.launch {
            val from = items.indexOfFirst { it.occurrence.id == occurrenceId }
            val moves = DayBlockOrder.move(items, from, delta)
            if (moves.isEmpty()) return@launch
            runCatchingCancellable {
                container.plannerRepository.applyDayOrder(moves)
            }.onSuccess { actionError.value = null }
                .onFailure { actionError.value = "Could not reorder that session. Try again." }
        }
        return true
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

    private sealed interface StartCommand {
        val originalOccurrenceId: String? get() = null

        data object Free : StartCommand
        data class Routine(val routineId: String) : StartCommand
        data class Cardio(val type: CardioType) : StartCommand
        data class Aux(val packId: String) : StartCommand
        data class Suggested(val day: SuggestedTrainingDay) : StartCommand
        data class Occurrence(val occurrenceId: String, val deliveryId: String?) : StartCommand {
            override val originalOccurrenceId: String get() = occurrenceId
        }
        data class Discard(
            val blocked: BlockedStart,
            val requested: StartCommand,
            val discarded: Boolean = false,
        ) : StartCommand {
            override val originalOccurrenceId: String? get() = requested.originalOccurrenceId
        }
    }

    data class BlockedStart(
        val day: SuggestedTrainingDay,
        val sessionId: String,
        val occurrenceId: String? = null,
        /** The reminder whose Start was refused, still unused; used once this start opens. */
        val reminder: ReminderTap? = null,
    )

    /** A reminder's Start: the occurrence it named, and its delivery. */
    data class ReminderTap(val occurrenceId: String, val deliveryId: String)

    fun recordBodyweight(kg: Double): Boolean {
        if (!canMutate() || !canEditBodyweightDraft()) return false
        if (!kg.isFinite() || kg !in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG) {
            bodyweightSave.value = BodyweightSave(kg = kg, error = "Enter a weight in the allowed range.")
            return false
        }
        // Set/IME and an old enabled frame share this admission latch. The dialog remains
        // open until all of the repository's writes finish, with the authored value intact.
        bodyweightSave.value = BodyweightSave(pending = true, kg = kg)
        _bodyweightSaved.value = false
        val epochDay = todayEpochDay()
        viewModelScope.launch {
            runCatchingCancellable {
                container.preferencesRepository.recordBodyweight(kg, epochDay)
            }.onSuccess {
                // Keep ownership through the UI's close/acknowledge boundary. A callback
                // queued from the old field must not admit another write in that interval.
                _bodyweightSaved.value = true
                bodyweightSave.value = BodyweightSave()
            }.onFailure { thrown ->
                AppLog.w(TAG, "Saving the weigh-in failed", thrown)
                bodyweightSave.value = BodyweightSave(
                    kg = kg,
                    error = "Could not finish saving this weight. Keep it here and try again.",
                )
            }
        }
        return true
    }

    fun onBodyweightSaveHandled() {
        _bodyweightSaved.value = false
    }

    /** The submitted value owns the field until the successful close is acknowledged. */
    fun canEditBodyweightDraft(): Boolean = !bodyweightSave.value.pending && !_bodyweightSaved.value

    /** Explicit dismissal discards only this authored field, and never cancels a live write. */
    fun discardBodyweightDraft(): Boolean {
        if (!canEditBodyweightDraft()) return false
        bodyweightSave.value = BodyweightSave()
        return true
    }

    private data class BodyweightSave(
        val pending: Boolean = false,
        val kg: Double? = null,
        val error: String? = null,
    )

    private data class HomeInputs(
        val insights: TrainingInsights,
        val live: Pair<WorkoutSession?, ActivitySession?>,
        val extras: Pair<
            Pair<Long?, Triple<
                List<com.sinura.personaltrainer.domain.ScheduleOccurrence>,
                List<com.sinura.personaltrainer.domain.ScheduleRule>,
                List<com.sinura.personaltrainer.domain.MissedWorkDecision>,
            >>,
            HomeCadence,
        >,
    )

    private data class HomeCadence(
        val preferences: com.sinura.personaltrainer.domain.SchedulePreferences,
        val preferredDays: Set<Weekday>,
        val bodyweightLog: List<com.sinura.personaltrainer.domain.BodyweightEntry>,
        val checkInWeekday: Weekday?,
        val setupComplete: Boolean,
        val suggestedExtra: ExtraEquipment = ExtraEquipment.MIXED,
    )
}

sealed class HomeDayAdd {
    data class Workout(val routineId: String) : HomeDayAdd()
    data object NewWorkout : HomeDayAdd()
    data class Cardio(val type: CardioType) : HomeDayAdd()
    data class Aux(val packId: String) : HomeDayAdd()
}

private const val TAG = "PT/HomeViewModel"
