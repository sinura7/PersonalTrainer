package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.BlockReview
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.DailyProjection
import com.sinura.personaltrainer.domain.DailyProjectionBuilder
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.HistoryMonthGroup
import com.sinura.personaltrainer.domain.HorizonMath
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.groupHistoryByMonth
import com.sinura.personaltrainer.domain.prSummary
import com.sinura.personaltrainer.domain.toHistoryEntry
import com.sinura.personaltrainer.domain.toInsightSession
import com.sinura.personaltrainer.insights.TrainingInsightsSource
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.util.toCivilYearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean

data class HistoryUiState(
    val isLoading: Boolean = true,
    val unavailable: Boolean = false,
    val stale: Boolean = false,
    val summaries: List<SessionSummary> = emptyList(),
    val monthGroups: List<HistoryMonthGroup> = emptyList(),
    val records: List<PrSummaryRow> = emptyList(),
    val calendar: TrainingMonth = TrainingMonth(month = CivilYearMonth(1970, 1)),
    val weekStart: Weekday = Weekday.MONDAY,
    val pastBlocks: List<FinishedBlock> = emptyList(),
    val horizon: AnalyticsHorizon = AnalyticsHorizon.MONTH,
    val horizonTotals: HorizonTotals? = null,
    val today: CivilDate = CivilDate.of(1970, 1, 1),
)

data class FinishedBlock(
    val block: TrainingBlock,
    val review: BlockReview,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val visibleMonth = MutableStateFlow(yearMonthOf(civilToday()))
    private val horizon = MutableStateFlow(AnalyticsHorizon.MONTH)

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val repeating = AtomicBoolean(false)

    private val historyRetry = MutableStateFlow(0)

    private val pastBlockReviews = combine(
        container.preferencesRepository.pastBlocks,
        container.preferencesRepository.weightUnit,
        container.preferencesRepository.bodyweightLog,
    ) { blocks, unit, log -> PastBlockInputs(blocks, unit, log) }
        .distinctUntilChanged { a, b ->
            a.blocks == b.blocks &&
                a.unit == b.unit &&
                a.lastWeighIn == b.lastWeighIn
        }
        .flatMapLatest { inputs ->
            flow {
                val zone = time.defaultZoneId()
                val reviews = inputs.blocks
                    .asReversed()
                    .map { block ->
                        val startMs = time.startOfDayMillis(
                            CivilDate.fromEpochDay(block.startEpochDay),
                            zone,
                        )
                        val endMs = time.startOfDayMillis(
                            CivilDate.fromEpochDay(block.endExclusiveEpochDay),
                            zone,
                        ) - 1
                        val sessions = container.workoutRepository.sessionsBetween(
                            minDateMs = startMs,
                            maxDateMs = endMs,
                        )
                        FinishedBlock(
                            block = block,
                            review = BlockReviewBuilder.build(
                                block = block,
                                sessions = sessions,
                                unit = inputs.unit,
                                time = time,
                                zoneId = zone,
                                bodyweightLog = inputs.bodyweightLog,
                            ),
                        )
                    }
                    .filterNot { it.review.isEmpty }
                emit(reviews)
            }
        }

    private val catalog = historyRetry.flatMapLatest {
        combine(
            combine(
                container.workoutRepository.observeSessionSummariesHealth(),
                container.activityRepository.observeCompletedSummaries(),
                combine(
                    container.workoutRepository.observeFinishedSince(
                        time.nowMillis() - TrainingInsightsSource.WINDOW_MS,
                    ),
                    container.activityRepository.observeCompletedGraphsSince(
                        time.nowMillis() - TrainingInsightsSource.WINDOW_MS,
                    ),
                ) { workouts, activities -> workouts to activities },
            ) { health, activitySummaries, windowed ->
                HistoryReads(health, activitySummaries, windowed.first, windowed.second)
            },
            combine(
                container.preferencesRepository.schedulePreferences,
                pastBlockReviews,
            ) { preferences, reviews -> preferences to reviews },
        ) { reads, settings ->
            val list = historyListFromHealth(reads.health)
            if (list.unavailable) {
                return@combine HistoryCatalog(unavailable = true)
            }
            val allSummaries = list.summaries + reads.activitySummaries
            val preferences = settings.first
            HistoryCatalog(
                unavailable = false,
                stale = list.stale,
                summaries = allSummaries,
                monthGroups = groupHistoryByMonth(allSummaries.map { it.toHistoryEntry() }),
                records = prSummary(
                    reads.windowed + reads.windowedActivities.mapNotNull { it.toInsightSession() },
                ),
                weekStart = preferences.weekStart,
                pastBlocks = settings.second,
                projections = DailyProjectionBuilder.project(allSummaries),
                today = civilToday(),
            )
        }
    }
        .flowOn(container.computeDispatcher)

    val uiState: StateFlow<HistoryUiState> = combine(
        catalog,
        visibleMonth,
        horizon,
    ) { cat, month, selectedHorizon ->
        if (cat.unavailable) {
            return@combine HistoryUiState(isLoading = false, unavailable = true)
        }
        HistoryUiState(
            isLoading = false,
            stale = cat.stale,
            summaries = cat.summaries,
            monthGroups = cat.monthGroups,
            records = cat.records,
            calendar = TrainingCalendarBuilder.buildSummaries(
                month = month.toCivilYearMonth(),
                summaries = cat.summaries,
                weekStart = cat.weekStart,
            ),
            weekStart = cat.weekStart,
            pastBlocks = cat.pastBlocks,
            horizon = selectedHorizon,
            horizonTotals = HorizonMath.totals(
                horizon = selectedHorizon,
                projections = cat.projections,
                today = cat.today,
                weekStart = cat.weekStart,
            ),
            today = cat.today,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        val next = visibleMonth.value.plusMonths(1)
        if (next <= yearMonthOf(civilToday())) visibleMonth.value = next
    }

    fun setHorizon(value: AnalyticsHorizon) {
        horizon.value = value
    }

    fun repeatSession(sessionId: String) {
        if (!repeating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
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
            } finally {
                repeating.set(false)
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

    private data class HistoryReads(
        val health: DataHealth<List<SessionSummary>>,
        val activitySummaries: List<SessionSummary>,
        val windowed: List<com.sinura.personaltrainer.domain.WorkoutSession>,
        val windowedActivities: List<ActivitySession>,
    )

    private data class PastBlockInputs(
        val blocks: List<TrainingBlock>,
        val unit: WeightUnit,
        val bodyweightLog: List<BodyweightEntry>,
    ) {
        val lastWeighIn: Pair<Long, Double>?
            get() = bodyweightLog.maxByOrNull { it.epochDay }?.let { it.epochDay to it.kg }
    }

    private data class HistoryCatalog(
        val unavailable: Boolean = false,
        val stale: Boolean = false,
        val summaries: List<SessionSummary> = emptyList(),
        val monthGroups: List<HistoryMonthGroup> = emptyList(),
        val records: List<PrSummaryRow> = emptyList(),
        val weekStart: Weekday = Weekday.MONDAY,
        val pastBlocks: List<FinishedBlock> = emptyList(),
        val projections: List<DailyProjection> = emptyList(),
        val today: CivilDate = CivilDate.of(1970, 1, 1),
    )

    private fun yearMonthOf(date: CivilDate): YearMonth = YearMonth.of(date.year, date.month)
}

internal data class HistoryListState(
    val unavailable: Boolean,
    val stale: Boolean,
    val summaries: List<SessionSummary>,
)

internal fun historyListFromHealth(
    health: DataHealth<List<SessionSummary>>,
): HistoryListState = when (health) {
    is DataHealth.Unavailable -> HistoryListState(
        unavailable = true,
        stale = false,
        summaries = emptyList(),
    )
    is DataHealth.Available -> HistoryListState(
        unavailable = false,
        stale = false,
        summaries = health.value,
    )
    is DataHealth.Degraded -> HistoryListState(
        unavailable = false,
        stale = true,
        summaries = health.lastValue,
    )
}

private const val TAG = "PT/HistoryViewModel"
