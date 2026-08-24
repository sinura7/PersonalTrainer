package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.SessionMonthGroup
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.groupSessionsByMonth
import com.sinura.personaltrainer.domain.prSummary
import com.sinura.personaltrainer.domain.BlockReview
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.util.toCivilYearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class HistoryUiState(
    val isLoading: Boolean = true,
    val unavailable: Boolean = false,
    val stale: Boolean = false,
    val sessions: List<WorkoutSession> = emptyList(),
    /** The same sessions, grouped for the list. Derived, never a second query. */
    val monthGroups: List<SessionMonthGroup> = emptyList(),
    /** The standing records, newest first — what History could never tell you before. */
    val records: List<PrSummaryRow> = emptyList(),
    val calendar: TrainingMonth = TrainingMonth(month = YearMonth.now().toCivilYearMonth()),
    val weekStart: Weekday = Weekday.MONDAY,
    /** Blocks already finished, newest first. Empty until one has been. */
    val pastBlocks: List<FinishedBlock> = emptyList(),
)

/**
 * A finished block and what it came to.
 *
 * The review is rebuilt from the session history each time rather than stored: the archive
 * keeps two numbers per block, and every session those numbers span is still in the database.
 * A stored summary would be a second source of truth that went stale the moment an old session
 * was edited — and editing an old session is a thing this app deliberately allows.
 */
data class FinishedBlock(
    val block: TrainingBlock,
    val review: BlockReview,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    /**
     * Which month the calendar is showing. Held in the ViewModel rather than the composition so
     * paging back through a year survives rotation and process death.
     */
    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /**
     * One-shot navigation held as state rather than a captured callback: the repeat writes a
     * session row before the destination is known, and a lambda captured into that coroutine
     * belongs to a composition that may already be gone.
     */
    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val historyRetry = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = historyRetry.flatMapLatest {
        combine(
            container.workoutRepository.observeHistoryHealth(),
            combine(
                container.preferencesRepository.schedulePreferences,
                container.preferencesRepository.pastBlocks,
                container.preferencesRepository.weightUnit,
                container.preferencesRepository.bodyweightLog,
            ) { preferences, blocks, unit, log -> Settings(preferences, blocks, unit, log) },
            visibleMonth,
        ) { health, settings, month ->
            val list = historyListFromHealth(health)
            if (list.unavailable) {
                return@combine HistoryUiState(isLoading = false, unavailable = true)
            }
            val sessions = list.sessions
            val preferences = settings.preferences
            HistoryUiState(
                isLoading = false,
                stale = list.stale,
                sessions = sessions,
                monthGroups = groupSessionsByMonth(sessions),
                records = prSummary(sessions),
                calendar = TrainingCalendarBuilder.build(
                    month = month.toCivilYearMonth(),
                    sessions = sessions,
                    weekStart = preferences.weekStart,
                ),
                weekStart = preferences.weekStart,
                pastBlocks = settings.blocks
                    .asReversed()
                    .map { block ->
                        FinishedBlock(
                            block = block,
                            review = BlockReviewBuilder.build(
                                block = block,
                                sessions = sessions,
                                unit = settings.unit,
                                bodyweightLog = settings.bodyweightLog,
                            ),
                        )
                    }
                    .filterNot { it.review.isEmpty },
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        // Never past the current month: nothing is ever logged in the future.
        val next = visibleMonth.value.plusMonths(1)
        if (next <= YearMonth.now()) visibleMonth.value = next
    }

    /**
     * Starts a fresh session shaped like an old one.
     *
     * The outcome is three-way on purpose. A repeat that quietly resumed whatever session
     * happened to be open would be the worst of the three: the lifter taps "Repeat Push Day"
     * and lands in Tuesday's half-finished Legs, with no signal that anything went wrong.
     */
    fun repeatSession(sessionId: String) {
        viewModelScope.launch {
            runCatchingCancellable { container.workoutRepository.repeatSession(sessionId) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is RepeatOutcome.Started -> _navigateToSession.value = outcome.sessionId
                        is RepeatOutcome.Blocked -> _blockedRepeat.value = outcome
                        is RepeatOutcome.Failed -> _error.value = outcome.message
                    }
                }
                .onFailure { thrown ->
                    AppLog.w(TAG, "repeatSession failed", thrown)
                    _error.value = "Could not repeat that workout. Try again."
                }
        }
    }

    fun resumeBlockedSession() {
        val blocked = _blockedRepeat.value ?: return
        _blockedRepeat.value = null
        _navigateToSession.value = blocked.inProgressSessionId
    }

    fun dismissBlockedRepeat() {
        _blockedRepeat.value = null
    }

    fun onNavigationHandled() {
        _navigateToSession.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    fun retryHistory() {
        historyRetry.value += 1
    }

    private data class Settings(
        val preferences: SchedulePreferences,
        val blocks: List<TrainingBlock>,
        val unit: WeightUnit,
        val bodyweightLog: List<BodyweightEntry>,
    )
}

internal data class HistoryListState(
    val unavailable: Boolean,
    val stale: Boolean,
    val sessions: List<WorkoutSession>,
)

/** Empty is a successful list. Unavailable is a first-read fault, never emptiness. */
internal fun historyListFromHealth(
    health: DataHealth<List<WorkoutSession>>,
): HistoryListState = when (health) {
    is DataHealth.Unavailable -> HistoryListState(
        unavailable = true,
        stale = false,
        sessions = emptyList(),
    )
    is DataHealth.Available -> HistoryListState(
        unavailable = false,
        stale = false,
        sessions = health.value,
    )
    is DataHealth.Degraded -> HistoryListState(
        unavailable = false,
        stale = true,
        sessions = health.lastValue,
    )
}

private const val TAG = "PT/HistoryViewModel"
