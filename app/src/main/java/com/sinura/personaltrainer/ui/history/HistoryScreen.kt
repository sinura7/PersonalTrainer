package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HorizonProgress
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.PrSummaryRow
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.navigation.LiveBarCopy
import com.sinura.personaltrainer.ui.navigation.LiveBarKind
import com.sinura.personaltrainer.ui.workout.StartSheetOpener
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.util.toYearMonth
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
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
    val unit = LocalWeightUnit.current
    val clock = LocalClockFormat.current
    val today = LocalTodayEpochDay.current
    var selectedDayEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var monthExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        onOpenActiveSession(target)
        viewModel.onNavigationHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "History",
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.display,
                    color = TextPrimary,
                )
                StartSheetOpener(
                    onOpen = onOpenStartSheet,
                    modifier = Modifier.testTag(HistoryTags.START_SHEET),
                )
            }

            when {
                state.isLoading -> {
                    ScreenLoading()
                }
                state.unavailable -> {
                    EmptyState(
                        title = DataHealthCopy.HISTORY_TITLE,
                        body = DataHealthCopy.HISTORY_BODY,
                        actionLabel = DataHealthCopy.RETRY,
                        onAction = viewModel::retryHistory,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Metrics.gutter),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                            bottom = Metrics.space7,
                        ),
                    ) {
                        if (state.stale) {
                            item(key = "stale") {
                                // Degraded pipeline: the list below is the last loaded
                                // read, not fresh. Rendering it indistinguishably from
                                // live data hid the DataHealth signal entirely.
                                Text(
                                    HistoryCopy.STALE_LIST,
                                    modifier = Modifier.padding(bottom = Metrics.space2),
                                    style = InstrumentType.caption,
                                    color = TextSecondary,
                                )
                            }
                        }
                        item(key = "horizon") {
                            HorizonPicker(
                                horizon = state.horizon,
                                totals = state.horizonTotals,
                                progress = state.horizonProgress,
                                onSelect = viewModel::setHorizon,
                                modifier = Modifier.padding(bottom = Metrics.sectionGap),
                            )
                        }
                        item(key = "calendar") {
                            TrainingCalendarCard(
                                month = state.calendar,
                                weekStart = state.weekStart,
                                today = LocalDate.ofEpochDay(today),
                                showMonth = monthExpanded,
                                onToggleMonth = {
                                    val next = !monthExpanded
                                    monthExpanded = next
                                    if (!next) viewModel.showCurrentMonth()
                                },
                                onPreviousMonth = viewModel::showPreviousMonth,
                                onNextMonth = viewModel::showNextMonth,
                                // One session opens straight away; two or more open a sheet.
                                // Opening "the first one" was a coin toss dressed as a default:
                                // nothing on screen said there had been a second.
                                onOpenDay = { day ->
                                    val total = day.sessionIds.size + day.activityIds.size
                                    when {
                                        total == 0 -> Unit
                                        total == 1 && day.sessionIds.size == 1 ->
                                            onOpenSession(day.sessionIds.first())
                                        total == 1 && day.activityIds.size == 1 ->
                                            onOpenActivity(day.activityIds.first())
                                        else -> selectedDayEpoch = day.date.epochDay
                                    }
                                },
                                unit = unit,
                                modifier = Modifier.padding(bottom = Metrics.sectionGap),
                            )
                        }
                        if (state.summaries.isEmpty()) {
                            item(key = "empty-log") {
                                Text(
                                    HistoryCopy.EMPTY_LOG,
                                    style = InstrumentType.body,
                                    color = TextSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(HistoryTags.EMPTY)
                                        .padding(bottom = Metrics.space3),
                                )
                            }
                        } else {
                            state.monthGroups.forEach { group ->
                            // Pinned while its own sessions scroll, so a long log always says
                            // which month you are looking at. A flat list had no landmarks at
                            // all past the first screenful.
                            stickyHeader(key = "month-${group.month}") {
                                Kicker(
                                    DateCopy.monthYear(group.month.toYearMonth()),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Pit)
                                        .padding(top = Metrics.space3, bottom = Metrics.kickerGap),
                                )
                            }
                            itemsIndexed(
                                group.entries,
                                key = { _, entry -> entry.id },
                            ) { index, entry ->
                                Column(
                                    modifier = instrumentAnimateItem()
                                        // Shape computed WITHIN the group, so each month reads
                                        // as its own panel with rounded ends.
                                        .clip(groupedRowShape(index, group.entries.size))
                                        .background(Surface1),
                                ) {
                                    if (index > 0) HairlineDivider()
                                    SessionLogRow(
                                        title = entry.title,
                                        dateLabel = DateCopy.dateTime(entry.sortMillis, clock),
                                        workingSets = entry.workingSets,
                                        work = entry.work,
                                        durationMinutes = entry.durationMinutes,
                                        onClick = {
                                            if (entry.kind == HistoryKind.ACTIVITY) {
                                                onOpenActivity(entry.id)
                                            } else {
                                                onOpenSession(entry.id)
                                            }
                                        },
                                        onRepeat = if (entry.kind == HistoryKind.WORKOUT) {
                                            { viewModel.repeatSession(entry.id) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                            }
                        }
                        if (state.pastBlocks.isNotEmpty()) {
                            item(key = "blocks-header") {
                                GymSectionHeader(
                                    "Blocks",
                                    modifier = Modifier.padding(
                                        top = Metrics.sectionGap,
                                        bottom = Metrics.kickerGap,
                                    ),
                                )
                            }
                            items(state.pastBlocks, key = { it.block.startEpochDay }) { finished ->
                                FinishedBlockCard(finished = finished, unit = unit)
                            }
                        }
                        if (state.records.isNotEmpty()) {
                            item(key = "records-header") {
                                GymSectionHeader(
                                    "Records",
                                    modifier = Modifier.padding(
                                        top = Metrics.sectionGap,
                                        bottom = Metrics.kickerGap,
                                    ),
                                )
                            }
                            item(key = "records") {
                                GroupedList {
                                    state.records.forEachIndexed { index, record ->
                                        if (index > 0) HairlineDivider()
                                        RecordRow(
                                            record = record,
                                            unit = unit,
                                            clock = clock,
                                            onClick = { onOpenExercise(record.exerciseId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        error?.let { message ->
            GymErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                onDismiss = { viewModel.onErrorShown() },
            )
        }
    }

    val dayEpoch = selectedDayEpoch
    if (dayEpoch != null) {
        val daySummaries = state.summaries.filter { it.localEpochDay == dayEpoch }
        if (daySummaries.isEmpty()) {
            selectedDayEpoch = null
        } else {
            DaySessionsSheet(
                summaries = daySummaries,
                dateLabel = DateCopy.weekdayLong(LocalDate.ofEpochDay(dayEpoch)),
                unit = unit,
                clock = clock,
                onOpenSession = { sessionId ->
                    selectedDayEpoch = null
                    onOpenSession(sessionId)
                },
                onOpenActivity = { activityId ->
                    selectedDayEpoch = null
                    onOpenActivity(activityId)
                },
                onDismiss = { selectedDayEpoch = null },
            )
        }
    }

    if (blockedRepeat != null) {
        // Same sentence as StartOptions and session detail. Two explanations of one
        // rule is how a rule stops reading as a rule.
        ConfirmActionDialog(
            title = "Session in progress",
            body = "Finish or discard the current session before starting another.",
            confirmLabel = LiveBarCopy.resumeLabel(LiveBarKind.WORKOUT),
            onConfirm = viewModel::resumeBlockedSession,
            onDismiss = viewModel::dismissBlockedRepeat,
        )
    }
}

/**
 * Day / Week / Month / Year / All. Totals for the selected window.
 *
 * History is a readout. The chips retotal; they do not hide the calendar
 * or the log. One hero numeral (sessions) plus days / sets / min / PRs,
 * and the lift that moved most when the range is long enough to say.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HorizonPicker(
    horizon: AnalyticsHorizon,
    totals: HorizonTotals?,
    onSelect: (AnalyticsHorizon) -> Unit,
    modifier: Modifier = Modifier,
    progress: HorizonProgress? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            AnalyticsHorizon.entries.forEach { entry ->
                InstrumentChip(
                    label = entry.label,
                    selected = horizon == entry,
                    onClick = { onSelect(entry) },
                    modifier = Modifier.testTag(HistoryTags.horizon(entry)),
                )
            }
        }
        Text(
            HistoryCopy.HORIZON_CAPTION,
            style = InstrumentType.caption,
            color = TextTertiary,
        )
        totals?.let { numbers ->
            val title = HistoryCopy.windowTitle(numbers.horizon)
            val spoken = "$title, ${numbers.sessionCount} ${HistoryCopy.sessionsLabel(numbers.sessionCount)}"
            val moved = progress?.movedMost
            GymCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HistoryTags.READOUT)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
            ) {
                Kicker(title)
                Text(
                    numbers.sessionCount.toString(),
                    style = InstrumentType.numeralLg,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    HistoryCopy.sessionsLabel(numbers.sessionCount),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space5),
                ) {
                    MetricCluster(
                        value = numbers.trainedDays.toString(),
                        label = "days",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                    MetricCluster(
                        value = numbers.workingSets.toString(),
                        label = "sets",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                    MetricCluster(
                        value = numbers.activeMinutes.toString(),
                        label = "min",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                    MetricCluster(
                        value = (progress?.recordsBroken ?: 0).toString(),
                        label = "PRs",
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
                if (moved != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HistoryTags.MOVED_MOST),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            HistoryCopy.MOVED_MOST,
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                        Text(
                            moved.exerciseName,
                            modifier = Modifier.weight(1f).padding(start = Metrics.space2),
                            style = InstrumentType.body,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${moved.fromLabel}  →  ${moved.toLabel}",
                            style = InstrumentType.numeralSm,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sessions of one day, when there is more than one of them.
 *
 * A calendar cell can only ever show that *something* happened; two sessions on a Saturday
 * look exactly like one. This is the disambiguation, and it exists so that tapping a day is
 * never a guess about which workout you are about to open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySessionsSheet(
    summaries: List<SessionSummary>,
    dateLabel: String,
    unit: WeightUnit,
    clock: ClockFormat,
    onOpenSession: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface3,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                Kicker(dateLabel)
                Text(
                    "${summaries.size} sessions",
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GroupedList {
                summaries.forEachIndexed { index, summary ->
                    if (index > 0) HairlineDivider()
                    SessionLogRow(
                        title = summary.routineName ?: if (summary.kind == HistoryKind.ACTIVITY) {
                            "Cardio"
                        } else {
                            "Workout"
                        },
                        dateLabel = DateCopy.dateTime(summary.finishedAt ?: summary.date, clock),
                        workingSets = summary.workingSets,
                        work = SetWork(volumeKg = summary.volumeKg, bodyweightReps = 0),
                        durationMinutes = summary.durationMinutes,
                        onClick = {
                            if (summary.kind == HistoryKind.ACTIVITY) {
                                onOpenActivity(summary.id)
                            } else {
                                onOpenSession(summary.id)
                            }
                        },
                        unit = unit,
                    )
                }
            }
        }
    }
}

/**
 * A block you finished, and what it came to.
 *
 * Rebuilt from the sessions it spans rather than read from a stored summary, so editing an old
 * workout corrects the block it belonged to instead of leaving a number that used to be true.
 *
 * The movers are why anyone scrolls this far. Four numbers say how much you did; the movers say
 * what came of it, which is the only part that distinguishes one block from the next.
 */
@Composable
private fun FinishedBlockCard(finished: FinishedBlock, unit: WeightUnit) {
    val review = finished.review
    val span = remember(finished.block) {
        val start = LocalDate.ofEpochDay(finished.block.startEpochDay)
        val end = LocalDate.ofEpochDay(finished.block.endExclusiveEpochDay - 1)
        "${DateCopy.monthYearShort(start)} – ${DateCopy.monthYearShort(end)}"
    }
    GymCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker("${review.weeks} weeks")
            Text(span, style = InstrumentType.caption, color = TextTertiary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space5),
        ) {
            MetricCluster(
                value = review.daysTrained.toString(),
                label = "days",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.workingSets.toString(),
                label = "sets",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            val column = SetCopy.workColumn(review.work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.recordsBroken.toString(),
                label = "PRs",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
        }
        SetCopy.bodyweightLine(review.bodyweight, unit)?.let { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Bodyweight", style = InstrumentType.body, color = TextSecondary)
                Text(line, style = InstrumentType.numeralSm, color = TextSecondary)
            }
        }
        review.movers.forEach { mover ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    mover.exerciseName,
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${mover.fromLabel}  →  ${mover.toLabel}",
                    style = InstrumentType.numeralSm,
                    color = TextSecondary,
                )
            }
        }
    }
}

/**
 * A standing record: which lift, what it was, and when.
 *
 * The kind is spelled out rather than implied by the number, because "120 kg" and "120 kg
 * estimated from a set of five" are different claims and only one of them was ever lifted.
 */
@Composable
private fun RecordRow(
    record: PrSummaryRow,
    unit: WeightUnit,
    clock: ClockFormat,
    onClick: () -> Unit,
) {
    InstrumentRow(
        title = record.exerciseName,
        subtitle = "${record.kind.label} · ${DateCopy.dateTime(record.achievedAt, clock)}",
        onClick = onClick,
    ) {
        MetricCluster(
            value = WeightConverter.formatDisplayNumber(
                WeightConverter.toDisplayValue(record.valueKg, unit),
            ),
            label = unit.suffix,
        )
        MetricCluster(value = record.reps.toString(), label = "reps")
    }
}

/**
 * The sessions read as one grouped panel, but stay individual lazy items.
 *
 * A history is unbounded — every finished session ever, in one query — so pouring the rows
 * into a single `GroupedList` would compose all of them the moment the tab opens. The
 * container is assembled from the rows instead: rounded ends, square middles, a hairline
 * between.
 */
private fun groupedRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(Radius.sm)
    index == 0 -> RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm)
    index == count - 1 -> RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm)
    else -> RectangleShape
}

object HistoryTags {
    const val DAY = "history-horizon-day"
    const val WEEK = "history-horizon-week"
    const val MONTH = "history-horizon-month"
    const val YEAR = "history-horizon-year"
    const val ALL = "history-horizon-all"
    const val READOUT = "history-horizon-readout"
    const val MOVED_MOST = "history-moved-most"
    const val EMPTY = "history-empty-log"
    const val START_SHEET = "history-start-sheet"
    const val CALENDAR_MONTH = "history-calendar-month"

    fun horizon(horizon: AnalyticsHorizon): String = when (horizon) {
        AnalyticsHorizon.DAY -> DAY
        AnalyticsHorizon.WEEK -> WEEK
        AnalyticsHorizon.MONTH -> MONTH
        AnalyticsHorizon.YEAR -> YEAR
        AnalyticsHorizon.ALL_TIME -> ALL
    }
}
