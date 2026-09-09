package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.domain.AnalyticsHorizon
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
import com.sinura.personaltrainer.domain.HorizonProgress
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.groupHistoryByMonth
import com.sinura.personaltrainer.domain.standingRecords
import com.sinura.personaltrainer.domain.toHistoryEntry
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.ErrorSlot
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean

/** [ErrorSlot] families: a success may clear only its own family's refusal. */
private const val ERR_REPEAT = "repeat"

data class HistoryUiState(
    val isLoading: Boolean = true,
    val unavailable: Boolean = false,
    val stale: Boolean = false,
    val summaries: List<SessionSummary> = emptyList(),
    val monthGroups: List<HistoryMonthGroup> = emptyList(),
    /**
     * Lifetime standing bests, one per lift, newest first. Independent of [horizon]: the
     * chips retotal the period and the readout's PRs count is period-scoped, but a best is
     * a claim about the whole log. This used to be built from the 32-day insight window, so
     * a stronger lift older than a month vanished and a weaker recent set wore the label.
     */
    val records: List<PrSummaryRow> = emptyList(),
    val calendar: TrainingMonth = TrainingMonth(month = CivilYearMonth(1970, 1)),
    val weekStart: Weekday = Weekday.MONDAY,
    val pastBlocks: List<FinishedBlock> = emptyList(),
    val horizon: AnalyticsHorizon = AnalyticsHorizon.MONTH,
    val horizonTotals: HorizonTotals? = null,
    val horizonProgress: HorizonProgress? = null,
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

    private val errors = ErrorSlot()
    val error: StateFlow<String?> =
        errors.messages.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val repeating = AtomicBoolean(false)

    private val historyRetry = MutableStateFlow(0)

    /**
     * The invalidation contract for every derived read below. Both keys used to guess at
     * content identity — the horizon readout from list size and newest id, the block reviews
     * from the last weigh-in — and neither moved when a finished set was edited, deleted or
     * restored, so the PRs and mover stayed stale until the horizon was switched.
     *
     * Includes activity completions: a backdated strength day used to move the list and
     * Records without recomputing the readout, because the token was strength-store only.
     */
    private val revision = container.completedTrainingRepository.observeRevision()

    private val pastBlockReviews = combine(
        container.preferencesRepository.pastBlocks,
        container.preferencesRepository.weightUnit,
        container.preferencesRepository.bodyweightLog,
        revision,
    ) { blocks, unit, log, finishedWork -> PastBlockInputs(blocks, unit, log, finishedWork) }
        .distinctUntilChanged()
        .flatMapLatest { inputs ->
            flow {
                val items = container.completedTrainingRepository.all()
                val reviews = inputs.blocks
                    .asReversed()
                    .map { block ->
                        FinishedBlock(
                            block = block,
                            review = BlockReviewBuilder.build(
                                block = block,
                                items = items,
                                unit = inputs.unit,
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
                container.activityRepository.observeCompletedSummariesHealth(),
                revision,
            ) { health, activities, finishedWork -> HistoryReads(health, activities, finishedWork) },
            combine(
                container.workoutRepository.observeRecordSetsHealth(),
                container.activityRepository.observeRecordSetsHealth(),
            ) { workouts, activities -> RecordReads(workouts, activities) },
            combine(
                container.preferencesRepository.schedulePreferences,
                pastBlockReviews,
            ) { preferences, reviews -> preferences to reviews },
        ) { reads, recordReads, settings ->
            val list = historyListFromHealth(reads.health)
            if (list.unavailable) {
                return@combine HistoryCatalog(unavailable = true)
            }
            val activities = sidecarFromHealth(reads.activitySummaries)
            val workoutRecords = sidecarFromHealth(recordReads.workouts)
            val activityRecords = sidecarFromHealth(recordReads.activities)
            val allSummaries = list.summaries + activities.value
            val preferences = settings.first
            HistoryCatalog(
                unavailable = false,
                stale = list.stale || activities.stale || workoutRecords.stale || activityRecords.stale,
                summaries = allSummaries,
                monthGroups = groupHistoryByMonth(allSummaries.map { it.toHistoryEntry() }),
                records = standingRecords(workoutRecords.value + activityRecords.value),
                weekStart = preferences.weekStart,
                pastBlocks = settings.second,
                projections = DailyProjectionBuilder.project(allSummaries),
                today = civilToday(),
                revision = reads.revision,
            )
        }
    }
        .flowOn(container.computeDispatcher)
        // Two collectors (uiState and horizonProgress) used to mean two copies of every
        // upstream subscription and two assemblies of the same catalog per change. Shared
        // once within the ViewModel; WhileSubscribed matches uiState so nothing runs while
        // the screen is off, and replay hands a late collector the current catalog.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val horizonProgress = combine(
        catalog,
        horizon,
        container.preferencesRepository.weightUnit,
    ) { cat, selectedHorizon, unit ->
        if (cat.unavailable) {
            null
        } else {
            val earliest = cat.summaries.minOfOrNull { it.localEpochDay }
            val (start, end) = HorizonMath.range(
                selectedHorizon,
                cat.today,
                cat.weekStart,
                earliest,
            )
            HorizonProgressKey(
                startEpochDay = start,
                endEpochDay = end,
                unit = unit,
                revision = cat.revision,
            )
        }
    }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            flow {
                if (key == null) {
                    emit(null)
                    return@flow
                }
                val items = container.completedTrainingRepository.all()
                emit(
                    BlockReviewBuilder.overRange(
                        startEpochDay = key.startEpochDay,
                        endExclusiveEpochDay = key.endEpochDay + 1,
                        items = items,
                        unit = key.unit,
                    ),
                )
            }
        }
        .flowOn(container.computeDispatcher)

    val uiState: StateFlow<HistoryUiState> = combine(
        catalog,
        visibleMonth,
        horizon,
        horizonProgress,
    ) { cat, month, selectedHorizon, progress ->
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
            horizonProgress = progress,
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

    fun showCurrentMonth() {
        visibleMonth.value = yearMonthOf(civilToday())
    }

    fun setHorizon(value: AnalyticsHorizon) {
        horizon.value = value
    }

    fun repeatSession(sessionId: String) {
        if (!repeating.compareAndSet(false, true)) return
        val started = errors.mark()
        viewModelScope.launch {
            try {
                runCatchingCancellable { container.workoutRepository.repeatSession(sessionId) }
                    .onSuccess { outcome ->
                        when (outcome) {
                            is RepeatOutcome.Started -> {
                                errors.clearFrom(source = ERR_REPEAT, before = started)
                                _navigateToSession.value = outcome.sessionId
                            }
                            is RepeatOutcome.Blocked -> {
                                errors.clearFrom(source = ERR_REPEAT, before = started)
                                _blockedRepeat.value = outcome
                            }
                            is RepeatOutcome.Failed ->
                                errors.fail(source = ERR_REPEAT, message = outcome.message)
                        }
                    }
                    .onFailure { thrown ->
                        AppLog.w(TAG, "repeatSession failed", thrown)
                        errors.fail(
                            source = ERR_REPEAT,
                            message = "Could not repeat that workout. Try again.",
                        )
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
        errors.dismiss()
    }

    fun retryHistory() {
        historyRetry.value += 1
    }

    /**
     * What decides whether the horizon readout is recomputed. The revision replaces the old
     * session count and newest id: those never moved for an edit, and a longer key that
     * still ignored content would have been the same bug with more fields.
     */
    private data class HorizonProgressKey(
        val startEpochDay: Long,
        val endEpochDay: Long,
        val unit: WeightUnit,
        val revision: String,
    )

    private data class HistoryReads(
        val health: DataHealth<List<SessionSummary>>,
        val activitySummaries: DataHealth<List<SessionSummary>>,
        val revision: String,
    )

    private data class RecordReads(
        val workouts: DataHealth<List<RecordSet>>,
        val activities: DataHealth<List<RecordSet>>,
    )

    /**
     * Compared whole, not by proxy. The log is small (one row per weigh-in) and any entry in
     * it can change what a block's opening or closing weight was.
     */
    private data class PastBlockInputs(
        val blocks: List<TrainingBlock>,
        val unit: WeightUnit,
        val bodyweightLog: List<BodyweightEntry>,
        val revision: String,
    )

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
        val revision: String = "",
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

/**
 * A read that rides alongside the workout list: activity summaries, record sets.
 *
 * These never make the screen unavailable on their own — the workout list decides that —
 * but a failed one must not read as "no activities" or "no records" either. It contributes
 * what it last had (or nothing) and marks the screen stale, so the caption says the page may
 * be behind instead of the section quietly vanishing.
 */
internal data class HistorySidecar<T>(
    val value: List<T>,
    val stale: Boolean,
)

internal fun <T> sidecarFromHealth(health: DataHealth<List<T>>): HistorySidecar<T> =
    HistorySidecar(
        value = health.presentValue().orEmpty(),
        stale = health !is DataHealth.Available,
    )

private const val TAG = "PT/HistoryViewModel"
