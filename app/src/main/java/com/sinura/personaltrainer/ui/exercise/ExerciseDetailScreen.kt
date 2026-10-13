package com.sinura.personaltrainer.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AddToRoutineCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseFloorStatsPresentation
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.PersonalRecord
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.domain.supportingRecordSession
import com.sinura.personaltrainer.ui.components.AddToRoutineSheet
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LabelledTrend
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.GoldContainer
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.workout.ExerciseStatsRow
import com.sinura.personaltrainer.util.JvmTime
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.abs

/**
 * One lift, measured.
 *
 * The two charts here answer different questions and so are drawn differently. Weekly volume
 * is additive — zero is a real floor and the area of a bar means something. An estimated 1RM
 * is a *level*: it moves a few percent at a time, and drawing it as bars from zero against
 * the series' own maximum rendered 100 → 102.5 → 105 kg as three identical rectangles. The
 * one chart that answers "am I getting stronger" could not show that you were, so it now
 * takes the focused-domain line.
 */
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var routinePickerOpen by rememberSaveable { mutableStateOf(false) }
    val unit = LocalWeightUnit.current
    val history = state.history
    // A literal hold has no repetition/tonnage measurement. Do not present a
    // zero-rep record or promise a rep trend for a history made only of holds.
    // Mixed or positive-rep stopwatch history keeps the established measures.
    val holdsOnly = remember(history) {
        history.sessions.isNotEmpty() && history.sessions.all { session ->
            session.sets.isNotEmpty() && session.sets.all { set ->
                set.reps < 1 && (set.durationSeconds ?: 0) > 0
            }
        }
    }
    // The floor's own Best set and Volume for this lift in the workout in progress, once a
    // working set of it is logged there (ADR-030, owner decision of 23 September 2026).
    val sessionInProgress = state.sessionInProgress

    // Sessions that produced an estimate, oldest first, kept alongside their values so the
    // chart's x-axis labels are the dates of the points actually plotted.
    val estimateSessions = remember(history) {
        history.sessions.asReversed().filter { it.estimatedOneRepMaxKg != null }
    }
    // Series carry display values, not kilograms, so a plot and the headline above it can
    // never disagree about what they are measuring.
    val estimates = remember(estimateSessions, unit) {
        estimateSessions.mapNotNull { it.estimatedOneRepMaxKg }
            .map { WeightConverter.toDisplayValue(it, unit) }
    }
    // A lift measured in reps gets a rep trend. Plotting its kilograms would draw a flat line
    // along zero for someone whose pull-ups went from six to fifteen — the app charting the
    // absence of a number instead of the presence of the progress.
    val repsAreTheMeasure = LoadClass.of(state.exercise?.loadType).repsAreTheMeasure
    val weeklySeries = remember(history, unit, repsAreTheMeasure) {
        if (repsAreTheMeasure) {
            history.weeklyTonnage.map { it.bodyweightReps.toDouble() }
        } else {
            history.weeklyTonnage.map { WeightConverter.toDisplayValue(it.volumeKg, unit) }
        }
    }
    val estimateDays = remember(estimateSessions) { estimateSessions.map(::sessionEpochDay) }
    val weekDays = remember(history.weeklyTonnage) { history.weeklyTonnage.map { it.weekStart.epochDay } }
    val today = JvmTime.civilDate(JvmTime.nowMillis()).epochDay
    val currentWeek = weekDays.lastOrNull()?.let { today >= it && today < it + 7L } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        ExerciseDetailHeader(
            name = state.exercise?.name ?: "Exercise",
            exercise = state.exercise,
            onBack = onBack,
        )

        // Outside the branches: adding to a routine is offered from the empty state as well as
        // from the full one, and the confirmation has to land wherever it was pressed.
        state.notice?.let { message ->
            GymStatusBanner(
                message,
                modifier = Modifier.padding(horizontal = Metrics.gutter),
                onDismissed = viewModel::dismissNotice,
            )
        }

        when {
            state.isLoading -> ScreenLoading()

            state.missing -> {
                EmptyState(
                    scene = EmptyScene.GONE,
                    title = "Exercise missing",
                    body = "This lift was deleted from the library. Your logged sets are still in history.",
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.padding(Metrics.gutter),
                )
            }

            !history.hasHistory -> {
                // Reachable only past the isLoading and missing branches above, so the lift is
                // resolved and present here — no null guard needed on the action.
                // The action used to be omitted on the grounds that spending this screen's one
                // filled control on "Back" duplicates the header arrow. That was right about
                // Back and wrong about there being nothing else: the way to get history for a
                // lift is to put it in a routine, which is exactly what this state is missing.
                val nothingLogged: @Composable (Modifier) -> Unit = { emptyModifier ->
                    EmptyState(
                        scene = EmptyScene.LOG,
                        title = "Nothing logged yet",
                        body = "Records and trends appear here once you have finished a session with this lift.",
                        actionLabel = "Add to a routine",
                        onAction = { routinePickerOpen = true },
                        modifier = emptyModifier,
                    )
                }
                if (sessionInProgress == null) {
                    nothingLogged(Modifier.padding(Metrics.gutter))
                } else {
                    // A first session with this lift has no finished history yet, but its sets
                    // today are the floor's, and they stand above the promise of more. A list, so
                    // both stay reachable at large text and in landscape.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
                    ) {
                        item(key = "this-workout") { SessionInProgressCard(sessionInProgress) }
                        item(key = "nothing-logged") { nothingLogged(Modifier) }
                    }
                }
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
                    verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                ) {
                    if (sessionInProgress != null) {
                        item(key = "this-workout") { SessionInProgressCard(sessionInProgress) }
                    }
                    if (!holdsOnly && history.records.isNotEmpty()) {
                        item(key = "records") {
                            RecordsCard(records = history.records, unit = unit, sessions = history.sessions,
                                onOpenSession = onOpenSession, onOpenActivity = onOpenActivity)
                        }
                    }

                    item(key = "all-time-label") {
                        GymSectionHeader("All time", modifier = Modifier.padding(top = SECTION_LEAD))
                    }
                    item(key = "all-time") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                        ) {
                            StatTile(
                                label = "sessions",
                                value = history.sessions.size.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "working sets",
                                value = history.lifetimeWorkingSets.toString(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (holdsOnly) item(key = "hold-measures") {
                        Text("Recorded hold durations are listed by session.",
                            style = InstrumentType.caption, color = TextSecondary)
                    }
                    if (!holdsOnly) item(key = "all-time-volume") {
                        if (repsAreTheMeasure) {
                            StatTile(
                                label = "reps",
                                value = history.lifetimeBodyweightReps.toString(),
                            )
                        } else {
                            StatTile(
                                label = "volume",
                                value = groupedNumber(history.lifetimeVolumeKg, unit),
                                unit = unit.suffix,
                            )
                        }
                    }

                    if (!holdsOnly && !repsAreTheMeasure) item(key = "e1rm") {
                        val prDisplay = history.records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]
                            ?.value
                            ?.let { WeightConverter.toDisplayValue(it, unit) }
                        ExerciseTrendCard(
                            title = "Estimated 1RM",
                            values = estimates,
                            epochDays = estimateDays,
                            pointNoun = "session",
                            // A lift with no estimable set will never grow this chart, so it is
                            // told what would, rather than promised a trend that cannot arrive.
                            hint = if (estimates.isEmpty()) {
                                "Log a loaded set of ${PersonalRecords.MAX_REPS_FOR_ESTIMATE} " +
                                    "reps or fewer to estimate a 1RM."
                            } else {
                                "One more session unlocks your trend."
                            },
                            measure = unit.suffix,
                            line = true,
                            grouped = false,
                            tag = ExerciseDetailTags.ESTIMATES,
                            prValue = prDisplay,
                            modifier = Modifier.padding(top = SECTION_LEAD),
                        )
                    }
                    if (!holdsOnly) item(key = "weekly-volume") {
                        ExerciseTrendCard(
                            title = if (repsAreTheMeasure) "Weekly reps" else "Weekly volume",
                            values = weeklySeries,
                            epochDays = weekDays,
                            pointNoun = "week",
                            hint = "One more training week unlocks your trend.",
                            measure = if (repsAreTheMeasure) "reps" else unit.suffix,
                            line = false,
                            grouped = true,
                            tag = ExerciseDetailTags.WEEKLY,
                            currentWeek = currentWeek,
                        )
                    }

                    item(key = "sessions-label") {
                        GymSectionHeader("Every session", modifier = Modifier.padding(top = SECTION_LEAD))
                    }
                    // Real lazy items, not one GroupedList in a single item: this list is
                    // every session the lift has ever appeared in and grows without bound, and
                    // a lazy item is all-or-nothing — a lift trained twice a week for two
                    // years would compose and measure hundreds of rows on the first frame.
                    // The container is assembled from the rows instead, exactly as History
                    // does it: rounded ends, square middles, a hairline between.
                    itemsIndexed(
                        history.sessions,
                        key = { _, summary -> "${summary.kind}:${summary.sessionId}" },
                    ) { index, summary ->
                        Column(
                            modifier = instrumentAnimateItem()
                                .clip(groupedRowShape(index, history.sessions.size))
                                .background(Surface1),
                        ) {
                            if (index > 0) HairlineDivider()
                            SessionRow(
                                summary = summary,
                                unit = unit,
                                loadClass = LoadClass.of(state.exercise?.loadType),
                                onClick = {
                                    if (summary.kind == HistoryKind.ACTIVITY) {
                                        onOpenActivity(summary.sessionId)
                                    } else {
                                        onOpenSession(summary.sessionId)
                                    }
                                },
                            )
                        }
                    }
                    // After the history, not before it: this screen exists to answer "how is
                    // this lift going", and the answer is what makes the action worth taking.
                    // Without it the screen ended — several hundred lines of records and
                    // trends whose only exits were Back and a session row.
                    item(key = "add-to-routine") {
                        SecondaryGymButton(
                            text = "Add to a routine",
                            onClick = { routinePickerOpen = true },
                            modifier = Modifier
                                .padding(top = SECTION_LEAD)
                                .testTag(ExerciseDetailTags.ADD_TO_ROUTINE),
                            height = Metrics.touchMin,
                        )
                    }
                }
            }
        }
    }

    state.exercise?.let { exercise ->
        if (routinePickerOpen) {
            AddToRoutineSheet(
                exercise = exercise,
                destinations = state.routines.map { membership ->
                    AddToRoutineCopy.destination(membership.routine, membership.alreadyHolds)
                },
                emptyBody = AddToRoutineCopy.EMPTY_DETAIL,
                onSelect = { routineId ->
                    routinePickerOpen = false
                    viewModel.addToRoutine(routineId)
                },
                onDismiss = { routinePickerOpen = false },
            )
        }
    }
}

@Composable
internal fun ExerciseDetailHeader(
    name: String,
    /** Null while loading, or when the lift has been deleted from under this screen. */
    exercise: Exercise?,
    onBack: () -> Unit,
) {
    ScreenHeader(
        title = name,
        onBack = onBack,
        backTag = ExerciseDetailTags.BACK,
        titleMaxLines = 2,
        leading = {
            if (exercise != null) {
                ExerciseThumb(
                    exercise = exercise,
                    modifier = Modifier.padding(end = Metrics.space3),
                    size = ThumbSize.header,
                )
            }
        },
    )
}

/**
 * Every record this lift holds, in one panel and in the gold family.
 *
 * These were three full-width cards, each spending about ninety density-independent pixels on
 * a single number and styled identically to everything else on the screen — so the app's
 * rewards looked exactly like its ordinary readouts. Gold is reserved for records and used
 * nowhere else, and the three numbers now sit in one row where they can be compared.
 */
@Composable
internal fun RecordsCard(
    records: Map<PersonalRecordKind, PersonalRecord>,
    unit: WeightUnit,
    sessions: List<ExerciseSessionSummary>,
    onOpenSession: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val heaviest = records[PersonalRecordKind.WEIGHT]
    // REPS for a bodyweight lift, REPS_AT_WEIGHT for a loaded one — the same slot, because they
    // are the same question asked of two kinds of lift. Only one is ever present.
    val topReps = records[PersonalRecordKind.REPS] ?: records[PersonalRecordKind.REPS_AT_WEIGHT]
    val estimate = records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]

    GymCard(colors = CardDefaults.cardColors(containerColor = GoldContainer)) {
        Kicker("Records", color = PrGold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            heaviest?.let { record ->
                RecordMetric(
                    value = displayNumber(record.weightKg, unit),
                    unit = unit.suffix,
                    label = "heaviest",
                    modifier = Modifier.weight(1f),
                )
            }
            topReps?.let { record ->
                RecordMetric(
                    value = record.reps.toString(),
                    unit = null,
                    label = "top reps",
                    modifier = Modifier.weight(1f),
                )
            }
            estimate?.let { record ->
                RecordMetric(
                    value = displayNumber(record.value, unit),
                    unit = unit.suffix,
                    label = "est 1rm",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HairlineDivider(startIndent = 0.dp)
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            heaviest?.let { record ->
                RecordProvenance(
                    "Heaviest ${record.weightKg.toWeightLabel(unit)} × ${record.reps}",
                    record, supportingRecordSession(record, sessions), onOpenSession, onOpenActivity,
                )
            }
            topReps?.let { record ->
                RecordProvenance(
                    if (record.kind == PersonalRecordKind.REPS) "${record.reps} bodyweight reps"
                    else "${record.reps} reps at ${record.weightKg.toWeightLabel(unit)}",
                    record, supportingRecordSession(record, sessions), onOpenSession, onOpenActivity,
                )
            }
            estimate?.let { record ->
                // The estimate is derived, so show the set it was derived from; a number with no
                // visible working behind it is not something to train off.
                RecordProvenance(
                    "1RM from ${record.weightKg.toWeightLabel(unit)} × ${record.reps}",
                    record, supportingRecordSession(record, sessions), onOpenSession, onOpenActivity,
                )
            }
        }
    }
}

/**
 * Best set and Volume for this lift in the workout in progress: the floor's stats row without
 * the Last cell it keeps, in the floor's words, from the same calculator. At large text these
 * two leave the floor so the entry holds still, and this is where they are read.
 *
 * Best set is the floor's: the lift's standing best, with today's sets in the running, marked
 * `Today` only when one of them beat it. So the heading names the session the card belongs to,
 * the app's own words for it, rather than claiming every number on it for today.
 */
@Composable
private fun SessionInProgressCard(sessionInProgress: SessionInProgressStats) {
    GymCard(modifier = Modifier.testTag(ExerciseDetailTags.THIS_WORKOUT)) {
        Kicker(SESSION_IN_PROGRESS)
        ExerciseStatsRow(
            stats = sessionInProgress.stats,
            unit = sessionInProgress.unit,
            visibility = ExerciseFloorStatsPresentation.RowVisibility.BEST_AND_VOLUME,
            standalone = true,
        )
    }
}

private const val SESSION_IN_PROGRESS = "Session in progress"

@Composable
private fun RecordMetric(
    value: String,
    unit: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    MetricCluster(
        value = value,
        label = label,
        modifier = modifier,
        unit = unit,
        valueStyle = InstrumentType.numeralMd,
        valueColor = PrGold,
        horizontalAlignment = Alignment.Start,
    )
}

@Composable
private fun RecordProvenance(
    detail: String, record: PersonalRecord, source: ExerciseSessionSummary?,
    onOpenSession: (String) -> Unit, onOpenActivity: (String) -> Unit,
) {
    val achieved = source?.let { DateCopy.civilDate(sessionEpochDay(it)) } ?: dateLabel(record.achievedAt)
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            Text("$detail · $achieved", style = InstrumentType.caption, color = TextSecondary)
            Text(source?.sessionName?.takeIf { it.isNotBlank() }?.let { "View $it" }
                ?: if (source != null) "View recorded session" else "Supporting session unavailable",
                style = InstrumentType.caption, color = TextSecondary)
        }
    }
    if (source == null) content() else Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)
            .clickable(role = Role.Button) {
                if (source.kind == HistoryKind.ACTIVITY) onOpenActivity(source.sessionId)
                else onOpenSession(source.sessionId)
            }
            .padding(vertical = Metrics.space2)
            .testTag("record-source-${record.kind.name}"),
    ) { content() }
}

/** Exact readouts lead; only two or more recorded points make a trend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseTrendCard(
    title: String,
    values: List<Double>,
    epochDays: List<Long>,
    pointNoun: String,
    hint: String,
    measure: String,
    line: Boolean,
    grouped: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    prValue: Double? = null,
    currentWeek: Boolean = false,
) {
    require(values.size == epochDays.size)
    var valuesOpen by rememberSaveable { mutableStateOf(false) }
    GymCard(modifier = modifier.testTag(tag)) {
        if (values.isEmpty()) {
            Kicker(title)
            Text(if (line) "No recorded estimate" else "No recorded training week",
                style = InstrumentType.bodyStrong, color = TextSecondary)
            Text(hint, style = InstrumentType.caption, color = TextTertiary)
        } else {
            val latest = values.last()
            if (values.size == 1) {
                Kicker(title)
                // A single real measurement has a value and date, but no fabricated bars.
                Text("${formatSeries(latest, grouped)} $measure", style = InstrumentType.numeralMd,
                    color = TextSecondary)
                Text(pointDate(epochDays.single(), line), style = InstrumentType.caption, color = TextTertiary)
                Text(hint, style = InstrumentType.caption, color = TextTertiary)
            } else {
                LabelledTrend(
                    title = title,
                    values = values,
                    startLabel = axisLabel(epochDays.first()),
                    endLabel = axisLabel(epochDays.last()),
                    headlineValue = formatSeries(latest, grouped),
                    headlineUnit = measure,
                    deltaLabel = seriesDelta(values, measure, grouped),
                    deltaIsGain = latest >= values[values.lastIndex - 1],
                    line = line,
                    contentDescription = trendDescription(title, values, pointNoun, measure, grouped),
                    prValue = prValue,
                )
                Text("Change from the previous recorded $pointNoun", style = InstrumentType.caption,
                    color = TextTertiary)
                Text(
                    if (line) "Recorded sessions · equal spacing · focused scale"
                    else "Training weeks · empty weeks omitted · bars start at 0",
                    style = InstrumentType.caption, color = TextTertiary,
                )
                if (line && prValue != null) Text("Gold dashed line: lifetime best estimate",
                    style = InstrumentType.caption, color = TextTertiary)
            }
            if (currentWeek) Text("Current week so far", style = InstrumentType.caption, color = TextSecondary)
            SecondaryGymButton(
                text = "Values and dates",
                onClick = { valuesOpen = true },
                modifier = Modifier.testTag("$tag-values"),
                height = Metrics.touchMin,
            )
        }
    }
    if (valuesOpen && values.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { valuesOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Surface3,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = LocalWindowInfo.current.containerDpSize.height * SHEET_HEIGHT_SHARE)
                    .padding(horizontal = Metrics.gutter)
                    .testTag("$tag-data"),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Text(title, style = InstrumentType.title, color = TextSecondary)
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).testTag("$tag-data-list"),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space3),
                ) {
                    itemsIndexed(values.indices.reversed().toList()) { _, index ->
                        Column(modifier = Modifier.fillMaxWidth().testTag("$tag-point-$index")) {
                            Text(pointDate(epochDays[index], line), style = InstrumentType.caption,
                                color = TextTertiary)
                            Text("${formatSeries(values[index], grouped)} $measure",
                                style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                }
                SecondaryGymButton(
                    text = "Close",
                    onClick = { valuesOpen = false },
                    modifier = Modifier.padding(bottom = Metrics.space3).testTag("$tag-close"),
                    height = Metrics.touchMin,
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    summary: ExerciseSessionSummary,
    unit: WeightUnit,
    loadClass: LoadClass,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        InstrumentRow(title = dateLabel(atMs = summary.performedAtMs, epochDay = summary.localEpochDay)) {
            MetricCluster(value = summary.workingSets.toString(), label = "sets")
            val column = SetCopy.workColumn(summary.work, unit)
            MetricCluster(value = column.value, label = column.label)
        }
        summary.topSet?.let { top ->
            // Recorded values use the row's full width, without a single-line subtitle cap.
            // Long holds and large text must not turn the saved result into an abbreviation.
            Text(
                (if (top.reps < 1 && (top.durationSeconds ?: 0) > 0) "Recorded hold " else "Top set ") +
                    SetCopy.setLine(
                        weightKg = top.weightKg, reps = top.reps, loadClass = loadClass,
                        unit = unit, durationSeconds = top.durationSeconds,
                    ),
                modifier = Modifier.padding(start = Metrics.space4, end = Metrics.space4, bottom = Metrics.space3),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun dateLabel(atMs: Long, epochDay: Long? = null): String {
    val absolute = remember(atMs, epochDay) {
        epochDay?.let(DateCopy::civilDate) ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atMs))
    }
    val relative = remember(atMs, epochDay) {
        if (epochDay == null) DayLabel.relative(atMs, JvmTime.nowMillis(), JvmTime)
        else when (val days = JvmTime.civilDate(JvmTime.nowMillis()).epochDay - epochDay) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..DayLabel.RELATIVE_DAYS.toLong() -> "$days days ago"
            else -> null
        }
    }
    return relative ?: absolute
}

private fun displayNumber(kg: Double, unit: WeightUnit): String =
    WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit))

private fun groupedNumber(kg: Double, unit: WeightUnit): String =
    com.sinura.personaltrainer.util.QuantityFormat.formatVolumeNumber(kg, unit)

/** Series values are already in display units, so they are formatted rather than converted. */
private fun formatSeries(value: Double, grouped: Boolean): String =
    if (grouped) {
        com.sinura.personaltrainer.util.QuantityFormat.formatGroupedNumber(value)
    } else {
        WeightConverter.formatDisplayNumber(value)
    }

/** Null when the change rounds away: "+0 kg" is noise rather than information. */
private fun seriesDelta(values: List<Double>, measure: String, grouped: Boolean): String? {
    if (values.size < MIN_POINTS_FOR_TREND) return null
    val delta = values.last() - values[values.lastIndex - 1]
    val magnitude = formatSeries(abs(delta), grouped)
    if (magnitude == "0") return null
    return "${if (delta > 0.0) "+" else "−"}$magnitude $measure"
}

/**
 * Both charts are a bare [androidx.compose.foundation.Canvas], which carries no semantics at
 * all — the two numbers on this screen that show progress were completely silent to TalkBack.
 */
private fun trendDescription(
    title: String,
    values: List<Double>,
    pointNoun: String,
    measure: String,
    grouped: Boolean,
): String {
    val noun = if (values.size == 1) pointNoun else "${pointNoun}s"
    val latest = formatSeries(values.lastOrNull() ?: 0.0, grouped)
    val best = formatSeries(values.maxOrNull() ?: 0.0, grouped)
    return "$title, ${values.size} $noun, latest $latest $measure, best $best $measure"
}

private val axisDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun axisLabel(epochDay: Long): String = axisDateFormatter.format(LocalDate.ofEpochDay(epochDay))

private fun sessionEpochDay(summary: ExerciseSessionSummary): Long =
    summary.localEpochDay ?: JvmTime.civilDate(summary.performedAtMs).epochDay

private fun pointDate(epochDay: Long, line: Boolean): String = if (line) DateCopy.civilDate(epochDay)
    else "${DateCopy.civilDate(epochDay)} – ${DateCopy.civilDate(epochDay + 6L)}"

private const val MIN_POINTS_FOR_TREND = 2

/** A section owns the space above it; the list's own arrangement covers the rest. */
private val SECTION_LEAD = Metrics.sectionGap - Metrics.cardGap

private const val SHEET_HEIGHT_SHARE = 0.8f

/**
 * The rounding a row needs to look like part of one grouped container.
 *
 * Same reasoning as History's copy: the sessions list is unbounded, so it has to be real
 * lazy items, which means no single parent can draw the container's corners.
 */
private fun groupedRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(Radius.sm)
    index == 0 -> RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm)
    index == count - 1 -> RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm)
    else -> RectangleShape
}

object ExerciseDetailTags {
    const val BACK = "exercise-detail-back"
    const val ADD_TO_ROUTINE = "exercise-detail-add-to-routine"
    const val THIS_WORKOUT = "exercise-detail-this-workout"
    const val ESTIMATES = "exercise-detail-estimates"
    const val WEEKLY = "exercise-detail-weekly"
}
