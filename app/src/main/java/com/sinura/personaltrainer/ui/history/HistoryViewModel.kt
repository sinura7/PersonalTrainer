package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
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
import com.sinura.personaltrainer.domain.HistoryPeriodMath
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.HorizonMath
import com.sinura.personaltrainer.domain.HorizonProgress
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.domain.RecordsCalculator
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TrainingCalendarBuilder
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingMonth
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.groupHistoryByMonth
import com.sinura.personaltrainer.domain.toHistoryEntry
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.ErrorSlot
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val recordsLoading: Boolean = true,
    val recordsUnavailable: Boolean = false,
    val recordsStale: Boolean = false,
    val calendar: TrainingMonth = TrainingMonth(month = CivilYearMonth(1970, 1)),
    val weekStart: Weekday = Weekday.MONDAY,
    val pastBlocks: List<FinishedBlock> = emptyList(),
    val blocksLoading: Boolean = true,
    val blocksUnavailable: Boolean = false,
    val blocksStale: Boolean = false,
    val horizon: AnalyticsHorizon = AnalyticsHorizon.MONTH,
    val selection: HistoryPeriodSelection = HistoryPeriodSelection(AnalyticsHorizon.MONTH, 0L, true),
    val periodRange: HistoryPeriodRange? = null,
    val canGoNext: Boolean = false,
    val progressLoading: Boolean = false,
    val progressFailed: Boolean = false,
    /** The same unit that keyed the period's computed progress. */
    val unit: WeightUnit = WeightUnit.KG,
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
    private val savedStateHandle: SavedStateHandle?,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    /** Legacy callers keep in-memory selection; only an owner-provided handle persists it. */
    constructor(application: Application, container: AppDependencies) :
        this(application, null, container)

    constructor(application: Application) :
        this(application, null, application.appContainer())

    private val initialToday = civilToday()
    private val today = MutableStateFlow(initialToday)
    private val periodSelection = MutableStateFlow(restoredSelection(initialToday))

    init {
        saveSelection(periodSelection.value)
    }

    private val _navigateToSession = MutableStateFlow<String?>(null)
    val navigateToSession: StateFlow<String?> = _navigateToSession.asStateFlow()

    private val _blockedRepeat = MutableStateFlow<RepeatOutcome.Blocked?>(null)
    val blockedRepeat: StateFlow<RepeatOutcome.Blocked?> = _blockedRepeat.asStateFlow()

    private val errors = ErrorSlot()
    val error: StateFlow<String?> = errors.messages
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

    /** The reviews last built, shown while a later read fails (the sidecar rule). */
    @Volatile
    private var lastPastBlocks: CachedBlockReviews? = null

    /**
     * Every read here is guarded. The blocks, the weigh-ins and the full log each threw
     * through the catalog and closed the app (audit UI-17); a failed one now marks the page
     * behind. When the full log fails, the section keeps what it last showed for the blocks
     * it still has; an unread block list retains a previously verified review as stale.
     */
    private val pastBlockInputs = historyRetry.flatMapLatest { attempt ->
        combine(
            container.preferencesRepository.pastBlocksHealth,
            container.preferencesRepository.weightUnit,
            container.preferencesRepository.bodyweightLogHealth,
            revision,
        ) { blocks, unit, log, finishedWork ->
            PastBlockInputs(
                blocks = blocks.presentValue().orEmpty(),
                unit = unit,
                bodyweightLog = log.presentValue().orEmpty(),
                revision = finishedWork,
                attempt = attempt,
                readsBehind = blocks !is DataHealth.Available || log !is DataHealth.Available,
                blocksUnavailable = blocks is DataHealth.Unavailable,
                weightsUnavailable = log is DataHealth.Unavailable,
            )
        }
    }
        .distinctUntilChanged()
        .flowOn(container.computeDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val pastBlockReviews = pastBlockInputs
        .flatMapLatest { inputs ->
            flow {
                emit(PastBlockRead(inputs = inputs, status = BlockReadStatus.LOADING))
                // Unknown is not a successful empty list. Keep a prior review while required
                // inputs cannot be read, and do not invent the weigh-ins for a new review.
                if (inputs.blocksUnavailable || (inputs.blocks.isNotEmpty() && inputs.weightsUnavailable)) {
                    val retained = retainedBlockReviews(inputs)
                    emit(PastBlockRead(
                        inputs = inputs, status = BlockReadStatus.FAILED,
                        value = retained.orEmpty(),
                        stale = true,
                        unavailable = retained == null,
                    ))
                    return@flow
                }
                // With no finished block there is nothing to review, so the whole log is
                // not read at all.
                val reviews = if (inputs.blocks.isEmpty()) {
                    Result.success(emptyList<FinishedBlock>())
                } else {
                    runCatchingCancellable {
                        val items = container.completedTrainingRepository.all()
                        inputs.blocks
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
                    }
                }
                // A cancelled noncancellable DAO return must not overwrite the fallback cache.
                currentCoroutineContext().ensureActive()
                reviews
                    .onSuccess { built -> lastPastBlocks = CachedBlockReviews(inputs.unit, built) }
                    .onFailure { thrown -> AppLog.e(TAG, "Reading past blocks failed", thrown) }
                val retained = if (reviews.isFailure) retainedBlockReviews(inputs) else null
                emit(
                    PastBlockRead(
                        inputs = inputs,
                        status = if (reviews.isFailure) BlockReadStatus.FAILED else BlockReadStatus.READY,
                        // Only reviews of blocks still in the list: a restore can replace them
                        // while this screen lives.
                        value = reviews.getOrElse {
                            retained.orEmpty()
                        },
                        stale = inputs.readsBehind || reviews.isFailure,
                        unavailable = reviews.isFailure && retained == null,
                    ),
                )
            }
        }
        .flowOn(container.computeDispatcher)

    /** Hide a prior result even in the interval before the new read publishes Loading. */
    private val currentPastBlockRead = combine(pastBlockInputs, pastBlockReviews) { inputs, read ->
        read.takeIf { it.inputs == inputs } ?: PastBlockRead(inputs, BlockReadStatus.LOADING)
    }

    private fun retainedBlockReviews(inputs: PastBlockInputs): List<FinishedBlock>? {
        val cached = lastPastBlocks?.takeIf { it.unit == inputs.unit } ?: return null
        return cached.value.filter { shown -> inputs.blocksUnavailable || shown.block in inputs.blocks }
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
                currentPastBlockRead,
            ) { preferences, reviews -> preferences to reviews },
        ) { reads, recordReads, settings ->
            val list = historyListFromHealth(reads.health)
            val activities = sidecarFromHealth(reads.activitySummaries)
            val workoutRecords = sidecarFromHealth(recordReads.workouts)
            val activityRecords = sidecarFromHealth(recordReads.activities)
            val allSummaries = list.summaries + activities.value
            val preferences = settings.first
            val pastBlocks = settings.second
            HistoryCatalog(
                unavailable = list.unavailable,
                stale = list.stale || activities.stale || workoutRecords.stale ||
                    activityRecords.stale || pastBlocks.stale,
                summaries = allSummaries,
                records = RecordsCalculator.standing(workoutRecords.value + activityRecords.value),
                recordsUnavailable = recordReads.workouts is DataHealth.Unavailable ||
                    recordReads.activities is DataHealth.Unavailable,
                recordsStale = recordReads.workouts is DataHealth.Degraded ||
                    recordReads.activities is DataHealth.Degraded,
                weekStart = preferences.weekStart,
                blockRead = pastBlocks,
                revision = reads.revision,
            )
        }
    }
        .flowOn(container.computeDispatcher)
        // Two collectors (uiState and progress) used to mean two copies of every
        // upstream subscription and two assemblies of the same catalog per change. Shared
        // once within the ViewModel; WhileSubscribed matches uiState so nothing runs while
        // the screen is off, and replay hands a late collector the current catalog.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** One request owns both the visible period and the identity of its full-log read. */
    private val periodRequest = combine(
        catalog,
        periodSelection,
        today,
        container.preferencesRepository.weightUnit,
        historyRetry,
    ) { cat, selection, currentToday, unit, attempt ->
        val range = if (cat.unavailable) null else HistoryPeriodMath.resolve(
            selection = selection,
            today = currentToday,
            weekStart = cat.weekStart,
            earliestEpochDay = cat.summaries.minOfOrNull { it.localEpochDay },
        )
        val scoped = if (range == null) emptyList() else cat.summaries.filter { it.localEpochDay in range }
        PeriodRequest(
            catalog = cat,
            selection = selection,
            today = currentToday,
            range = range,
            summaries = scoped,
            projections = DailyProjectionBuilder.project(scoped),
            unit = unit,
            attempt = attempt,
            key = range?.let { HorizonProgressKey(it, unit, cat.revision, attempt) },
        )
    }
        .flowOn(container.computeDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val horizonProgress = periodRequest
        .map { it.key }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            flow {
                if (key == null) {
                    emit(HorizonRead(key = null, status = ProgressStatus.UNAVAILABLE))
                    return@flow
                }
                emit(HorizonRead(key = key, status = ProgressStatus.LOADING))
                // As for past blocks: a failed read hides the readout and marks the page
                // behind instead of closing the app (audit UI-17).
                val progress = runCatchingCancellable {
                    BlockReviewBuilder.overRange(
                        startEpochDay = key.range.startEpochDay,
                        endExclusiveEpochDay = key.range.endExclusiveEpochDay,
                        items = container.completedTrainingRepository.all(),
                        unit = key.unit,
                    )
                }
                progress.onFailure { thrown -> AppLog.e(TAG, "Reading the period's history failed", thrown) }
                emit(HorizonRead(
                    key = key,
                    status = if (progress.isFailure) ProgressStatus.FAILED else ProgressStatus.READY,
                    progress = progress.getOrNull(),
                ))
            }
        }
        .flowOn(container.computeDispatcher)

    val uiState: StateFlow<HistoryUiState> = combine(
        periodRequest,
        horizonProgress,
    ) { request, progress ->
        val cat = request.catalog
        val selection = request.selection
        val anchor = if (selection.followToday) request.today.epochDay
            else minOf(selection.anchorEpochDay, request.today.epochDay)
        val month = CivilYearMonth.from(CivilDate.fromEpochDay(anchor))
        // Preferences and the catalog can publish before the block-input flow catches up.
        // Only reviews formatted and computed for this visible request may be exposed.
        val blocks = cat.blockRead?.takeIf { read ->
            read.inputs.unit == request.unit && read.inputs.revision == cat.revision &&
                read.inputs.attempt == request.attempt
        }
        val blocksLoading = blocks == null || blocks.status == BlockReadStatus.LOADING
        if (cat.unavailable) {
            return@combine HistoryUiState(
                isLoading = false, unavailable = true, selection = selection,
                horizon = selection.horizon, today = request.today, unit = request.unit,
                calendar = TrainingMonth(month),
                stale = cat.stale,
                records = cat.records, recordsLoading = false,
                recordsUnavailable = cat.recordsUnavailable, recordsStale = cat.recordsStale,
                pastBlocks = blocks?.value.orEmpty(), blocksLoading = blocksLoading,
                blocksUnavailable = blocks?.unavailable == true, blocksStale = blocks?.stale == true,
            )
        }
        // A new request can arrive before flatMapLatest publishes Loading. Never attach
        // the previous period/unit/revision/retry result during that interval.
        val matching = progress.takeIf { it.key == request.key }
        val range = checkNotNull(request.range)
        val failed = matching?.status == ProgressStatus.FAILED
        HistoryUiState(
            isLoading = false,
            stale = cat.stale || failed,
            summaries = request.summaries,
            monthGroups = groupHistoryByMonth(request.summaries.map { it.toHistoryEntry() }),
            records = cat.records,
            recordsLoading = false,
            recordsUnavailable = cat.recordsUnavailable,
            recordsStale = cat.recordsStale,
            calendar = TrainingCalendarBuilder.buildSummaries(
                month = month,
                summaries = cat.summaries,
                weekStart = cat.weekStart,
                range = range,
            ),
            weekStart = cat.weekStart,
            pastBlocks = blocks?.value.orEmpty(),
            blocksLoading = blocksLoading,
            blocksUnavailable = blocks?.unavailable == true,
            blocksStale = blocks?.stale == true,
            horizon = selection.horizon,
            selection = selection,
            periodRange = range,
            canGoNext = HistoryPeriodMath.next(selection, request.today, cat.weekStart) != null,
            progressLoading = matching == null || matching.status == ProgressStatus.LOADING,
            progressFailed = failed,
            unit = request.unit,
            horizonTotals = HorizonMath.totals(
                horizon = selection.horizon,
                projections = request.projections,
                range = range,
            ),
            horizonProgress = matching?.progress.takeIf { matching?.status == ProgressStatus.READY },
            today = request.today,
        )
    }
        .flowOn(container.computeDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(
                selection = periodSelection.value,
                horizon = periodSelection.value.horizon,
                today = initialToday,
                calendar = TrainingMonth(CivilYearMonth.from(CivilDate.fromEpochDay(
                    if (periodSelection.value.followToday) initialToday.epochDay
                    else minOf(periodSelection.value.anchorEpochDay, initialToday.epochDay),
                ))),
            ),
        )

    fun updateToday(epochDay: Long) {
        if (!supportedDay(epochDay)) return
        today.value = CivilDate.fromEpochDay(epochDay)
    }

    fun selectDay(epochDay: Long) {
        if (!supportedDay(epochDay) || epochDay > today.value.epochDay) return
        setSelection(HistoryPeriodSelection(AnalyticsHorizon.DAY, epochDay, false))
    }

    fun selectMonth(month: CivilYearMonth) {
        val anchor = month.atDay(1).epochDay
        if (!supportedDay(anchor) || anchor > today.value.epochDay) return
        setSelection(HistoryPeriodSelection(AnalyticsHorizon.MONTH, anchor, false))
    }

    fun showPreviousPeriod() {
        HistoryPeriodMath.previous(periodSelection.value, today.value, uiState.value.weekStart)?.let(::setSelection)
    }

    fun showNextPeriod() {
        HistoryPeriodMath.next(periodSelection.value, today.value, uiState.value.weekStart)?.let(::setSelection)
    }

    fun showCurrentPeriod() {
        setSelection(HistoryPeriodMath.current(periodSelection.value, today.value))
    }

    fun setHorizon(value: AnalyticsHorizon) {
        setSelection(periodSelection.value.copy(horizon = value))
    }

    // Existing callers still page the same selection; there is no independent month cursor.
    fun showPreviousMonth() = showPreviousPeriod()
    fun showNextMonth() = showNextPeriod()
    fun showCurrentMonth() = showCurrentPeriod()

    private fun setSelection(selection: HistoryPeriodSelection) {
        saveSelection(selection)
        periodSelection.value = selection
    }

    private fun saveSelection(selection: HistoryPeriodSelection) {
        val handle = savedStateHandle ?: return
        handle[SAVED_HORIZON] = selection.horizon.name
        handle[SAVED_ANCHOR] = selection.anchorEpochDay
        handle[SAVED_FOLLOW_TODAY] = selection.followToday
    }

    private fun restoredSelection(currentToday: CivilDate): HistoryPeriodSelection {
        val handle = savedStateHandle
        val restored = runCatching {
            val name = handle?.get<Any?>(SAVED_HORIZON) as? String ?: return@runCatching null
            val anchor = handle.get<Any?>(SAVED_ANCHOR) as? Long ?: return@runCatching null
            val follow = handle.get<Any?>(SAVED_FOLLOW_TODAY) as? Boolean ?: return@runCatching null
            HistoryPeriodSelection(AnalyticsHorizon.valueOf(name), anchor, follow)
        }.getOrNull()
        return restored ?: HistoryPeriodSelection(AnalyticsHorizon.MONTH, currentToday.epochDay, true)
    }

    private fun supportedDay(epochDay: Long): Boolean =
        epochDay in MIN_CIVIL_EPOCH_DAY..MAX_CIVIL_EPOCH_DAY

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
        val range: HistoryPeriodRange,
        val unit: WeightUnit,
        val revision: String,
        /** Retry reads the period again, even when nothing it keys on moved. */
        val attempt: Int,
    )

    private data class HistoryReads(
        val health: DataHealth<List<SessionSummary>>,
        val activitySummaries: DataHealth<List<SessionSummary>>,
        val revision: String,
    )

    private enum class ProgressStatus { LOADING, READY, FAILED, UNAVAILABLE }

    /** Every stage carries the full request identity, including Loading and failure. */
    private data class HorizonRead(
        val key: HorizonProgressKey?,
        val status: ProgressStatus,
        val progress: HorizonProgress? = null,
    )

    private data class PeriodRequest(
        val catalog: HistoryCatalog,
        val selection: HistoryPeriodSelection,
        val today: CivilDate,
        val range: HistoryPeriodRange?,
        val summaries: List<SessionSummary>,
        val projections: List<DailyProjection>,
        val unit: WeightUnit,
        val attempt: Int,
        val key: HorizonProgressKey?,
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
        val attempt: Int,
        /** The blocks or the weigh-ins came from a failed read: what they last had, or none. */
        val readsBehind: Boolean,
        val blocksUnavailable: Boolean,
        val weightsUnavailable: Boolean,
    )

    private enum class BlockReadStatus { LOADING, READY, FAILED }

    private data class CachedBlockReviews(val unit: WeightUnit, val value: List<FinishedBlock>)

    private data class PastBlockRead(
        val inputs: PastBlockInputs,
        val status: BlockReadStatus,
        val value: List<FinishedBlock> = emptyList(),
        val stale: Boolean = false,
        val unavailable: Boolean = false,
    )

    private data class HistoryCatalog(
        val unavailable: Boolean = false,
        val stale: Boolean = false,
        val summaries: List<SessionSummary> = emptyList(),
        val records: List<PrSummaryRow> = emptyList(),
        val recordsUnavailable: Boolean = false,
        val recordsStale: Boolean = false,
        val weekStart: Weekday = Weekday.MONDAY,
        val blockRead: PastBlockRead? = null,
        val revision: String = "",
    )

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
private const val SAVED_HORIZON = "history.period.horizon"
private const val SAVED_ANCHOR = "history.period.anchor"
private const val SAVED_FOLLOW_TODAY = "history.period.follow-today"
private const val MIN_CIVIL_EPOCH_DAY = -365243219162L
private const val MAX_CIVIL_EPOCH_DAY = 365241780471L
