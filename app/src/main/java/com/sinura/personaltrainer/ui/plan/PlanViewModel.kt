package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ExistingLayoutMatcher
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineGenerator
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.BlockReview
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.workout.DiscardOutcome
import com.sinura.personaltrainer.workout.StartDayOutcome
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PT/PlanVM"

data class PlanUiState(
    val isLoading: Boolean = true,
    val week: WeeklySchedulePlan? = null,
    val routines: List<Routine> = emptyList(),
    val preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
    val inProgress: WorkoutSession? = null,
    val loggedEpochDays: Set<Long> = emptySet(),
    /** A previewed week. Nothing here is stored until it is accepted. */
    val proposals: List<SuggestedTrainingDay> = emptyList(),
    /** The block this week belongs to, or null when the lifter is not in one. */
    val block: TrainingBlock? = null,
    /**
     * What the block came to, present only once it is over.
     *
     * Null while the block is running — not an empty review. Totting up twelve weeks that are
     * still going would invite reading a mid-block number as a result.
     */
    val blockReview: BlockReview? = null,
    /** True when this calendar week is the marked lighter week. */
    val lighterWeek: Boolean = false,
    val error: String? = null,
)

/**
 * The Plan tab: the week you decided on, and the routines it is built from.
 *
 * The distinction this whole screen turns on is between the week that is *stored* — pinned
 * slots, in [PlanUiState.week] — and a week that is merely *proposed*, in
 * [PlanUiState.proposals]. Before this, there was only one kind, and it was regenerated behind
 * your back on every emission: what looked like a plan was a suggestion that reshuffled
 * whenever you logged anything. Here a suggestion is visibly a suggestion until you accept it,
 * and accepting it is the only thing in the class that writes a slot.
 */
class PlanViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val actionError = MutableStateFlow<String?>(null)
    private val proposals = MutableStateFlow<List<SuggestedTrainingDay>>(emptyList())

    private val insights: StateFlow<TrainingInsights?> = container.trainingInsights.observeShared()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val uiState: StateFlow<PlanUiState> = combine(
        insights,
        container.workoutRepository.observeInProgress(),
        combine(
            container.preferencesRepository.schedulePreferences,
            container.preferencesRepository.trainingBlock,
            container.preferencesRepository.weightUnit,
            combine(
                container.preferencesRepository.bodyweightLog,
                container.preferencesRepository.lighterWeekStartEpochDay,
            ) { log, lighterStart -> log to lighterStart },
        ) { preferences, block, unit, logAndLighter ->
            SettingsAndBlock(preferences, block, unit, logAndLighter.first, logAndLighter.second)
        },
        proposals,
        actionError,
    ) { current, inProgress, settings, previewed, error ->
        if (current == null) return@combine PlanUiState()
        val zone = ZoneId.systemDefault()
        PlanUiState(
            isLoading = false,
            week = current.weekPlan,
            routines = current.routines,
            preferences = settings.preferences,
            inProgress = inProgress,
            loggedEpochDays = current.history
                .filter { it.isFinished }
                .map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().toEpochDay() }
                .toSet(),
            proposals = previewed,
            block = settings.block,
            blockReview = settings.block
                ?.takeIf { it.isCompleteOn(todayEpochDay()) }
                ?.let { finished ->
                    BlockReviewBuilder.build(
                        block = finished,
                        sessions = current.history,
                        unit = settings.unit,
                        zone = zone,
                        bodyweightLog = settings.bodyweightLog,
                    )
                },
            lighterWeek = LighterWeek.isCurrent(
                settings.lighterWeekStart,
                current.weekPlan?.weekStartEpochDay,
            ),
            error = error ?: when {
                current.failed(InsightFailure.PLAN) ->
                    "Couldn’t read this week’s plan. Your pins are safe — try again."
                else -> null
            },
        )
    }
        // Off the main thread. This transform walks every finished session to build the logged
        // set, and once a block completes it walks every set of it again for the review — with
        // a records check per set, which compares against everything before it. That is not
        // main-thread work, and it was on the main thread before the review made it obvious.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlanUiState(),
        )

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedByInProgress = MutableStateFlow<BlockedStart?>(null)
    val blockedByInProgress: StateFlow<BlockedStart?> = _blockedByInProgress.asStateFlow()

    init {
        // Home's empty hero can ask for a preview before this screen exists. The flag is
        // consumed exactly once — written back to false BEFORE the work runs, so an Activity
        // recreated in between does not re-arm it — and it stores nothing on its own.
        viewModelScope.launch {
            container.pendingWeekSuggestion.collect { pending ->
                if (!pending) return@collect
                container.pendingWeekSuggestion.value = false
                suggestFills()
            }
        }
        viewModelScope.launch {
            container.pendingAnswerReplay.collect { pending ->
                if (!pending) return@collect
                container.pendingAnswerReplay.value = false
                replayStoredAnswers()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pinning
    // -----------------------------------------------------------------------

    fun pinRoutine(epochDay: Long, routineId: String) {
        write("Could not pin that routine. Try again.") {
            container.scheduleRepository.pin(
                routineId = routineId,
                focusKind = null,
                anchorDay = dayOfWeekFor(epochDay),
            )
        }
    }

    fun pinFocus(epochDay: Long, kind: SessionFocusKind) {
        write("Could not pin that focus. Try again.") {
            container.scheduleRepository.pin(
                routineId = null,
                focusKind = kind,
                anchorDay = dayOfWeekFor(epochDay),
            )
        }
    }

    /** No confirm: unpinning loses nothing that re-pinning cannot put back in two taps. */
    fun unpin(slotId: String) {
        write("Could not unpin that day. Try again.") {
            container.scheduleRepository.unpin(slotId)
        }
    }

    fun swapRoutine(slotId: String, routineId: String) {
        write("Could not swap that day's routine. Try again.") {
            container.scheduleRepository.swapRoutine(slotId, routineId)
        }
    }

    // -----------------------------------------------------------------------
    // Suggestions
    // -----------------------------------------------------------------------

    /**
     * Builds a preview and shows it. Deliberately writes nothing — the old "New week" button
     * rewrote the plan on the spot, which is why nobody could tell a plan from a redraw.
     */
    fun suggestFills() {
        viewModelScope.launch {
            val current = insights.value ?: insights.first { it != null } ?: return@launch
            val snapshot = current.snapshot
            if (snapshot == null) {
                actionError.value = "Couldn’t read your recent training, so there is nothing to suggest from."
                return@launch
            }
            val preferences = container.preferencesRepository.schedulePreferences.first()
            val emphasis = container.preferencesRepository.coachPreferences.first().emphasis
            val slots = runCatchingCancellable { container.scheduleRepository.slots() }
                .getOrElse { thrown ->
                    AppLog.w(TAG, "Reading the pinned slots failed", thrown)
                    emptyList()
                }
            val plan = withContext(Dispatchers.Default) {
                WeeklySchedulePlanner.plan(
                    preferences = preferences,
                    snapshot = snapshot,
                    recommendations = current.recommendations,
                    routines = current.routines,
                    recentSessions = current.history,
                    nowMs = System.currentTimeMillis(),
                    pinnedSlots = slots,
                    emphasis = emphasis,
                )
            }
            // Only the days the planner invented. Echoed pins carry a slotId and are already
            // on the strip; re-drawing them as suggestions would offer to pin them twice.
            proposals.value = plan.days.filter { it.slotId == null && !it.isRest }
            actionError.value = null
        }
    }

    fun acceptFills() {
        val previewed = proposals.value
        if (previewed.isEmpty()) return
        proposals.value = emptyList()
        write("Could not save that week. Try again.") {
            container.scheduleRepository.acceptFills(previewed)
        }
    }

    fun dismissFills() {
        proposals.value = emptyList()
    }

    /**
     * Rebuilds the setup layout on the routines already on the phone.
     *
     * Writes nothing. Matching is name-then-kind; [acceptFills] is still the only pin. A
     * Settings rebuild is the wrong tool here — it would create a second copy of every
     * session they already train.
     */
    fun replayStoredAnswers() {
        viewModelScope.launch {
            val current = insights.value ?: insights.first { it != null } ?: return@launch
            val week = current.weekPlan
            if (week == null) {
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            val answers = runCatchingCancellable {
                container.preferencesRepository.storedOnboardingAnswers()
            }.getOrElse { thrown ->
                AppLog.w(TAG, "Reading stored answers failed", thrown)
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            val catalog = runCatchingCancellable {
                container.exerciseRepository.observeAll().first()
            }.getOrElse { thrown ->
                AppLog.w(TAG, "Reading the catalog for replay failed", thrown)
                emptyList()
            }
            val matched = withContext(Dispatchers.Default) {
                val blueprint = RoutineGenerator.generate(
                    answers = answers,
                    catalog = catalog,
                    weekStart = week.preferences.weekStart,
                )
                ExistingLayoutMatcher.match(
                    blueprint = blueprint,
                    existing = current.routines,
                    weekStartEpochDay = week.weekStartEpochDay,
                )
            }
            if (matched.isEmpty()) {
                actionError.value = WeekTwoCopy.MATCH_FAILED
                return@launch
            }
            proposals.value = matched
            actionError.value = null
        }
    }

    // -----------------------------------------------------------------------
    // Starting
    // -----------------------------------------------------------------------

    fun startDay(day: SuggestedTrainingDay) {
        viewModelScope.launch { start(day) }
    }

    private suspend fun start(day: SuggestedTrainingDay) {
        when (val outcome = container.startTrainingDay(day)) {
            is StartDayOutcome.Open -> {
                actionError.value = null
                _navigateToSession.value = outcome.sessionId
            }
            is StartDayOutcome.Blocked ->
                _blockedByInProgress.value = BlockedStart(day, outcome.inProgressSessionId)
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
                DiscardOutcome.Discarded -> start(blocked.day)
                is DiscardOutcome.Failed -> actionError.value = result.message
            }
        }
    }

    fun dismissBlockedStart() {
        _blockedByInProgress.value = null
    }

    fun onSessionNavigationHandled() {
        _navigateToSession.value = null
    }

    // -----------------------------------------------------------------------
    // Routines and tuning
    // -----------------------------------------------------------------------

    fun deleteRoutine(routineId: String) {
        write("Could not delete that routine. Try again.") {
            container.routineRepository.delete(routineId)
        }
    }

    fun setTrainingDays(days: Int) {
        viewModelScope.launch { container.preferencesRepository.setTrainingDaysPerWeek(days) }
    }

    fun setSplit(style: SplitStyle) {
        viewModelScope.launch { container.preferencesRepository.setSplitStyle(style) }
    }

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch { container.preferencesRepository.setWeekStart(day) }
    }

    fun setLighterWeek(enabled: Boolean) {
        viewModelScope.launch {
            val start = insights.value?.weekPlan?.weekStartEpochDay
                ?: uiState.value.week?.weekStartEpochDay
                ?: return@launch
            container.preferencesRepository.setLighterWeekStartEpochDay(
                if (enabled) start else null,
            )
        }
    }

    fun onErrorShown() {
        actionError.value = null
    }

    private fun write(failureMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatchingCancellable { block() }
                .onSuccess { actionError.value = null }
                .onFailure { thrown ->
                    AppLog.w(TAG, failureMessage, thrown)
                    actionError.value = failureMessage
                }
        }
    }

    private fun dayOfWeekFor(epochDay: Long): DayOfWeek = LocalDate.ofEpochDay(epochDay).dayOfWeek

    /**
     * Begin the next twelve weeks from the top of this week.
     *
     * Nothing is deleted and nothing is regenerated: the routines, the pinned week and every
     * logged session stay exactly as they are. A block is a horizon, not a container, so
     * starting a new one moves the marker and leaves the training alone.
     */
    fun startNextBlock() {
        write("Couldn't start a new block. Try again.") {
            val weekStart = container.preferencesRepository.schedulePreferences.first().weekStart
            // beginBlock keeps the block being replaced when it was finished. It always is
            // here — this is only reachable from the completed state — but the rule lives in
            // one place rather than being asserted at each caller.
            container.preferencesRepository.beginBlock(
                next = TrainingBlock.startingIn(today = LocalDate.now(), weekStart = weekStart),
                todayEpochDay = todayEpochDay(),
            )
        }
    }

    /** The day whose Start was refused, held so the screen can ask instead of the app deciding. */
    data class BlockedStart(val day: SuggestedTrainingDay, val sessionId: String)

    private data class SettingsAndBlock(
        val preferences: SchedulePreferences,
        val block: TrainingBlock?,
        val unit: WeightUnit,
        val bodyweightLog: List<BodyweightEntry>,
        val lighterWeekStart: Long?,
    )
}
