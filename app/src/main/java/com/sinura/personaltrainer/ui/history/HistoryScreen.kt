package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HistoryPeriodMath
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.HorizonProgress
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChoiceChip
import com.sinura.personaltrainer.ui.components.InstrumentChoiceGroup
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.rememberWeekStripState
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.workout.StartSheetOpener
import com.sinura.personaltrainer.util.toYearMonth
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenActiveSession: (String) -> Unit,
    onOpenActivity: (String) -> Unit = {},
    onOpenStartSheet: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val blockedRepeat by viewModel.blockedRepeat.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val unit = state.unit
    val today = LocalTodayEpochDay.current
    // Own exploration before a required-read retry can temporarily remove the calendar.
    val calendarState = rememberWeekStripState()
    var secondary by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDay = HistoryPeriodMath.anchor(
        selection = state.selection, today = state.today, weekStart = state.weekStart,
        completedEpochDays = state.completedEpochDays,
    ).epochDay

    LaunchedEffect(today) { viewModel.updateToday(today) }
    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        onOpenActiveSession(target)
        viewModel.onNavigationHandled()
    }
    Box(Modifier.fillMaxSize().background(Pit)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(HistoryTags.LIST),
            contentPadding = PaddingValues(
                start = Metrics.gutter, end = Metrics.gutter,
                top = Metrics.space3, bottom = Metrics.space7,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            item(key = "heading") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Text("History", style = InstrumentType.display, color = TextPrimary,
                        modifier = Modifier.semantics { heading() })
                    StartSheetOpener(onOpen = onOpenStartSheet,
                        modifier = Modifier.testTag(HistoryTags.START_SHEET))
                }
            }
            when {
                state.isLoading -> item(key = "loading") { ScreenLoading() }
                state.unavailable -> item(key = "unavailable") {
                    EmptyState(
                        scene = EmptyScene.RETRY,
                        title = DataHealthCopy.HISTORY_TITLE, body = DataHealthCopy.HISTORY_BODY,
                        actionLabel = DataHealthCopy.RETRY, onAction = viewModel::retryHistory,
                        compact = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    if (state.stale) item(key = "stale") {
                        HistoryRetryNotice(viewModel::retryHistory, HistoryTags.STALE_RETRY)
                    }
                    item(key = "horizon") {
                        HorizonPicker(
                            horizon = state.horizon, totals = state.horizonTotals,
                            progress = state.horizonProgress, onSelect = viewModel::setHorizon,
                            range = state.periodRange, progressLoading = state.progressLoading,
                            progressFailed = state.progressFailed, onRetry = viewModel::retryHistory,
                        )
                    }
                    item(key = "period-controls") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                        ) {
                            if (state.horizon != AnalyticsHorizon.ALL_TIME) {
                                QuietButton(text = "Previous", onClick = viewModel::showPreviousPeriod,
                                    modifier = Modifier.testTag(HistoryTags.PREVIOUS))
                                QuietButton(text = "Next", onClick = viewModel::showNextPeriod, enabled = state.canGoNext,
                                    modifier = Modifier.testTag(HistoryTags.NEXT))
                            }
                            QuietButton(text = "Current", onClick = viewModel::showCurrentPeriod,
                                modifier = Modifier.testTag(HistoryTags.CURRENT))
                        }
                    }
                    item(key = "lifetime-actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                            SecondaryGymButton(text = HistoryCopy.LIFETIME_RECORDS, onClick = { secondary = RECORDS },
                                modifier = Modifier.testTag(HistoryTags.LIFETIME_RECORDS))
                            SecondaryGymButton(text = HistoryCopy.LIFETIME_BLOCKS, onClick = { secondary = BLOCKS },
                                modifier = Modifier.testTag(HistoryTags.LIFETIME_BLOCKS))
                        }
                    }
                    when (state.horizon) {
                        AnalyticsHorizon.DAY, AnalyticsHorizon.WEEK, AnalyticsHorizon.MONTH ->
                            item(key = "calendar") {
                                TrainingCalendarCard(
                                    month = state.calendar, weekStart = state.weekStart,
                                    today = LocalDate.ofEpochDay(state.today.epochDay),
                                    selectedEpochDay = selectedDay, horizon = state.horizon,
                                    onSelectDay = viewModel::selectDay, unit = unit,
                                    stripState = calendarState,
                                    completedEpochDays = state.completedEpochDays,
                                )
                            }
                        AnalyticsHorizon.YEAR -> item(key = "year-months") {
                            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                                val year = LocalDate.ofEpochDay(selectedDay).year
                                for (number in 1..12) {
                                    val month = CivilYearMonth(year, number)
                                    val count = state.summaries.count {
                                        val day = LocalDate.ofEpochDay(it.localEpochDay)
                                        day.year == year && day.monthValue == number
                                    }
                                    SecondaryGymButton(
                                        text = "${DateCopy.monthYear(month.toYearMonth())} · $count ${HistoryCopy.sessionsLabel(count)}",
                                        onClick = { viewModel.selectMonth(month) },
                                        enabled = HistoryPeriodMath.canSelectMonth(
                                            month = month, today = state.today,
                                            completedEpochDays = state.completedEpochDays,
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag(HistoryTags.month(month)),
                                    )
                                }
                            }
                        }
                        AnalyticsHorizon.ALL_TIME -> Unit
                    }
                    if (state.summaries.isEmpty()) item(key = "empty-period") {
                        EmptyState(
                            scene = EmptyScene.LOG, title = HistoryCopy.EMPTY_PERIOD_TITLE,
                            body = HistoryCopy.EMPTY_PERIOD, compact = true,
                            modifier = Modifier.fillMaxWidth().testTag(HistoryTags.EMPTY),
                        )
                    }
                    state.monthGroups.forEach { group ->
                        stickyHeader(key = "month-${group.month}") {
                            Kicker(
                                text = DateCopy.monthYear(group.month.toYearMonth()),
                                modifier = Modifier.fillMaxWidth().background(Pit)
                                    .testTag(HistoryTags.monthHeader(group.month))
                                    .padding(top = Metrics.space3, bottom = Metrics.kickerGap),
                            )
                        }
                        itemsIndexed(group.entries, key = { _, entry -> "${entry.kind}:${entry.id}" }) { index, entry ->
                            Column(
                                modifier = instrumentAnimateItem()
                                    .testTag(HistoryTags.row(entry.kind, entry.id))
                                    .clip(groupedRowShape(index, group.entries.size)).background(Surface1),
                            ) {
                                if (index > 0) HairlineDivider()
                                SessionLogRow(
                                    title = entry.title, dateLabel = DateCopy.civilDate(entry.localEpochDay),
                                    workingSets = entry.workingSets, work = entry.work,
                                    durationMinutes = entry.durationMinutes, stills = entry.stills,
                                    onClick = {
                                        if (entry.kind == HistoryKind.ACTIVITY) onOpenActivity(entry.id)
                                        else onOpenSession(entry.id)
                                    },
                                    onRepeat = if (entry.kind == HistoryKind.WORKOUT)
                                        { { viewModel.repeatSession(entry.id) } } else null,
                                    reflowContent = true,
                                    durationLabel = HistoryCopy.activeDuration(entry.durationMinutes),
                                    unit = unit,
                                )
                            }
                        }
                    }
                }
            }
        }
        error?.let { message ->
            GymErrorBanner(message = message, onDismiss = viewModel::onErrorShown,
                modifier = Modifier.align(Alignment.BottomCenter).padding(Metrics.gutter))
        }
    }
    secondary?.let { selected ->
        val loading = if (selected == RECORDS) state.recordsLoading else state.blocksLoading
        val unavailable = if (selected == RECORDS) state.recordsUnavailable else state.blocksUnavailable
        val stale = if (selected == RECORDS) state.recordsStale else state.blocksStale
        val successfulEmpty = !loading && !unavailable && !stale
        HistoryLifetimeSheet(
            title = if (selected == RECORDS) HistoryCopy.LIFETIME_RECORDS else HistoryCopy.LIFETIME_BLOCKS,
            loading = loading, unavailable = unavailable, stale = stale,
            onRetry = viewModel::retryHistory, onDismiss = { secondary = null },
        ) {
            if (selected == RECORDS) {
                if (successfulEmpty && state.records.isEmpty()) item(key = "empty-records") {
                    Text("No lifetime records yet.", style = InstrumentType.body, color = TextSecondary)
                }
                items(state.records, key = { "${it.exerciseId}:${it.kind}" }) { record ->
                    RecordRow(record = record, unit = unit) {
                        secondary = null
                        onOpenExercise(record.exerciseId)
                    }
                }
            } else {
                if (successfulEmpty && state.pastBlocks.isEmpty()) item(key = "empty-blocks") {
                    Text("No completed blocks yet.", style = InstrumentType.body, color = TextSecondary)
                }
                items(state.pastBlocks, key = { it.block.startEpochDay }) { FinishedBlockCard(it, unit) }
            }
        }
    }
    if (blockedRepeat != null) {
        ConfirmActionDialog(
            title = "Session in progress",
            body = "Finish or discard the current session before starting another.",
            confirmLabel = LiveBarCopy.resumeLabel(LiveBarKind.WORKOUT),
            onConfirm = viewModel::resumeBlockedSession, onDismiss = viewModel::dismissBlockedRepeat,
        )
    }
}

@Composable
private fun HistoryRetryNotice(onRetry: () -> Unit, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Text(HistoryCopy.STALE_LIST, style = InstrumentType.caption, color = TextSecondary)
        QuietButton(text = DataHealthCopy.RETRY, onClick = onRetry, plain = true, modifier = Modifier.testTag(tag))
    }
}

/** Values share the selected range; pending and failed progress never become computed zero. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HorizonPicker(
    horizon: AnalyticsHorizon,
    totals: HorizonTotals?,
    onSelect: (AnalyticsHorizon) -> Unit,
    modifier: Modifier = Modifier,
    progress: HorizonProgress? = null,
    range: HistoryPeriodRange? = null,
    progressLoading: Boolean = false,
    progressFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        InstrumentChoiceGroup {
            AnalyticsHorizon.entries.forEach { entry ->
                InstrumentChoiceChip(label = entry.label, selected = horizon == entry, onClick = { onSelect(entry) },
                    modifier = Modifier.testTag(HistoryTags.horizon(entry)))
            }
        }
        Text(HistoryCopy.HORIZON_CAPTION, style = InstrumentType.caption, color = TextTertiary)
        totals?.let { numbers ->
            val title = range?.let(DateCopy::periodRange) ?: HistoryCopy.windowTitle(horizon)
            val spoken = buildList {
                add(title)
                add("${numbers.sessionCount} ${HistoryCopy.sessionsLabel(numbers.sessionCount)}")
                add("${numbers.trainedDays} days")
                add("${numbers.workingSets} sets")
                add(HistoryCopy.activeDuration(numbers.activeMinutes))
                if (!progressLoading && !progressFailed && progress != null) add("${progress.recordsBroken} PRs")
                if (progressFailed) add(HistoryCopy.PROGRESS_FAILED)
                else if (progressLoading || progress == null) add(HistoryCopy.PROGRESS_LOADING)
            }.joinToString(", ")
            GymCard(modifier = Modifier.fillMaxWidth().testTag(HistoryTags.READOUT)
                .semantics { contentDescription = spoken }) {
                Text(title, style = InstrumentType.title, color = TextPrimary,
                    modifier = Modifier.testTag(HistoryTags.RANGE_TITLE).semantics { heading() })
                Text(numbers.sessionCount.toString(), style = InstrumentType.numeralLg, color = TextPrimary)
                Text(HistoryCopy.sessionsLabel(numbers.sessionCount), style = InstrumentType.caption, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    HistoryMetric(numbers.trainedDays.toString(), "days")
                    HistoryMetric(numbers.workingSets.toString(), "sets")
                    HistoryMetric(HistoryCopy.activeDuration(numbers.activeMinutes), "active time")
                    if (!progressLoading && !progressFailed && progress != null)
                        HistoryMetric(progress.recordsBroken.toString(), "PRs")
                }
                when {
                    progressFailed -> Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                        Text(HistoryCopy.PROGRESS_FAILED, style = InstrumentType.body, color = TextSecondary,
                            modifier = Modifier.testTag(HistoryTags.PROGRESS_FAILED))
                        QuietButton(text = DataHealthCopy.RETRY, onClick = onRetry, plain = true,
                            modifier = Modifier.testTag(HistoryTags.PROGRESS_RETRY))
                    }
                    progressLoading || progress == null ->
                        Text(HistoryCopy.PROGRESS_LOADING, style = InstrumentType.body, color = TextSecondary,
                            modifier = Modifier.testTag(HistoryTags.PROGRESS_LOADING))
                    else -> progress.movedMost?.let { moved ->
                        Column(Modifier.fillMaxWidth().testTag(HistoryTags.MOVED_MOST),
                            verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                            Text(HistoryCopy.MOVED_MOST, style = InstrumentType.caption, color = TextTertiary)
                            Text(moved.exerciseName, style = InstrumentType.body, color = TextSecondary)
                            Text("${moved.fromLabel} → ${moved.toLabel}", style = InstrumentType.numeralSm, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Text(value, style = InstrumentType.numeralSm, color = TextSecondary)
        Text(label, style = InstrumentType.caption, color = TextTertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryLifetimeSheet(
    title: String,
    loading: Boolean,
    unavailable: Boolean,
    stale: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag(HistoryTags.SECONDARY_LIST),
            contentPadding = PaddingValues(horizontal = Metrics.gutter, vertical = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            item(key = "title") {
                Text(title, style = InstrumentType.title, color = TextPrimary,
                    modifier = Modifier.semantics { heading() })
            }
            item(key = "close") {
                QuietButton(text = "Close", onClick = onDismiss, modifier = Modifier.testTag(HistoryTags.SECONDARY_CLOSE))
            }
            if (loading) item(key = "loading") {
                Text("Loading $title…", style = InstrumentType.body, color = TextSecondary,
                    modifier = Modifier.testTag(HistoryTags.SECONDARY_LOADING))
            }
            if (unavailable || stale) item(key = "health") {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    Text(if (unavailable) "$title could not be loaded." else "$title is behind. Retry to refresh.",
                        style = InstrumentType.body, color = TextSecondary,
                        modifier = Modifier.testTag(HistoryTags.SECONDARY_FAILED))
                    QuietButton(text = DataHealthCopy.RETRY, onClick = onRetry,
                        modifier = Modifier.testTag(HistoryTags.SECONDARY_RETRY))
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishedBlockCard(finished: FinishedBlock, unit: WeightUnit) {
    val review = finished.review
    val span = "${DateCopy.civilDate(finished.block.startEpochDay)} – ${DateCopy.civilDate(finished.block.endExclusiveEpochDay - 1)}"
    GymCard {
        Text("${review.weeks} weeks · $span", style = InstrumentType.title, color = TextPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            HistoryMetric(review.daysTrained.toString(), "days")
            HistoryMetric(review.workingSets.toString(), "sets")
            val work = SetCopy.workColumn(review.work, unit)
            HistoryMetric(work.value, work.label)
            HistoryMetric(review.recordsBroken.toString(), "PRs")
        }
        SetCopy.bodyweightLine(review.bodyweight, unit)?.let { line ->
            Text("Bodyweight · $line", style = InstrumentType.body, color = TextSecondary)
        }
        review.movers.forEach { mover ->
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Text(mover.exerciseName, style = InstrumentType.body, color = TextSecondary)
                Text("${mover.fromLabel} → ${mover.toLabel}", style = InstrumentType.numeralSm, color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordRow(record: PrSummaryRow, unit: WeightUnit, onClick: () -> Unit) {
    val clock = LocalClockFormat.current
    Column(
        Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.sm)).background(Surface1)
            .clickable(role = Role.Button, onClick = onClick).padding(Metrics.space4),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Text(record.exerciseName, style = InstrumentType.bodyStrong, color = TextPrimary)
        Text("${record.kind.label} · ${DateCopy.dateTime(record.achievedAt, clock)}",
            style = InstrumentType.caption, color = TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            HistoryMetric(WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(record.valueKg, unit)), unit.suffix)
            HistoryMetric(record.reps.toString(), "reps")
        }
    }
}

private fun groupedRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(Radius.sm)
    index == 0 -> RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm)
    index == count - 1 -> RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm)
    else -> RectangleShape
}

private const val RECORDS = "records"
private const val BLOCKS = "blocks"

object HistoryTags {
    const val LIST = "history-period-list"
    const val STALE_RETRY = "history-stale-retry"
    const val DAY = "history-horizon-day"
    const val WEEK = "history-horizon-week"
    const val MONTH = "history-horizon-month"
    const val YEAR = "history-horizon-year"
    const val ALL = "history-horizon-all"
    const val READOUT = "history-horizon-readout"
    const val RANGE_TITLE = "history-period-range"
    const val MOVED_MOST = "history-moved-most"
    const val EMPTY = "history-empty-log"
    const val START_SHEET = "history-start-sheet"
    const val PREVIOUS = "history-period-previous"
    const val NEXT = "history-period-next"
    const val CURRENT = "history-period-current"
    const val CALENDAR = "history-period-calendar"
    const val CALENDAR_SCROLL = "history-calendar-scroll"
    const val LIFETIME_RECORDS = "history-lifetime-records"
    const val LIFETIME_BLOCKS = "history-lifetime-blocks"
    const val SECONDARY_LIST = "history-secondary-list"
    const val SECONDARY_CLOSE = "history-secondary-close"
    const val SECONDARY_RETRY = "history-secondary-retry"
    const val SECONDARY_LOADING = "history-secondary-loading"
    const val SECONDARY_FAILED = "history-secondary-failed"
    const val PROGRESS_LOADING = "history-progress-loading"
    const val PROGRESS_FAILED = "history-progress-failed"
    const val PROGRESS_RETRY = "history-progress-retry"
    const val MONTH_HEADER_PREFIX = "history-month-header-"

    fun row(kind: HistoryKind, id: String) = "history-row-$kind-$id"
    fun monthHeader(month: CivilYearMonth) = "$MONTH_HEADER_PREFIX$month"
    fun month(month: CivilYearMonth) = "history-year-month-$month"
    fun day(epochDay: Long) = "history-day-$epochDay"
    fun date(epochDay: Long) = "history-date-$epochDay"
    fun weekday(column: Int) = "history-weekday-$column"
    fun horizon(horizon: AnalyticsHorizon): String = when (horizon) {
        AnalyticsHorizon.DAY -> DAY
        AnalyticsHorizon.WEEK -> WEEK
        AnalyticsHorizon.MONTH -> MONTH
        AnalyticsHorizon.YEAR -> YEAR
        AnalyticsHorizon.ALL_TIME -> ALL
    }
}
