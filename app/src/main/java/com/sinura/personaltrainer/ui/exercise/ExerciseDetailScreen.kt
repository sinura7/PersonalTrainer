package com.sinura.personaltrainer.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.PersonalRecord
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.PersonalRecords
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GroupedList
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
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.util.toLocalDate
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
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
    viewModel: ExerciseDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var routinePickerOpen by rememberSaveable { mutableStateOf(false) }
    val unit = LocalWeightUnit.current
    val history = state.history

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
                EmptyState(
                    title = "Nothing logged yet",
                    body = "Records and trends appear here once you have finished a session with this lift.",
                    actionLabel = "Add to a routine",
                    onAction = { routinePickerOpen = true },
                    modifier = Modifier.padding(Metrics.gutter),
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
                    verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                ) {
                    if (history.records.isNotEmpty()) {
                        item(key = "records") { RecordsCard(records = history.records, unit = unit) }
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
                    item(key = "all-time-volume") {
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

                    item(key = "e1rm") {
                        val prDisplay = history.records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]
                            ?.value
                            ?.let { WeightConverter.toDisplayValue(it, unit) }
                        TrendCard(
                            title = "Estimated 1RM",
                            values = estimates,
                            startLabel = estimateSessions.firstOrNull()
                                ?.let { axisLabel(it.performedAtMs) }.orEmpty(),
                            endLabel = estimateSessions.lastOrNull()
                                ?.let { axisLabel(it.performedAtMs) }.orEmpty(),
                            pointNoun = "session",
                            // A lift with no estimable set will never grow this chart, so it is
                            // told what would, rather than promised a trend that cannot arrive.
                            hint = if (estimates.isEmpty()) {
                                "Log a loaded set of ${PersonalRecords.MAX_REPS_FOR_ESTIMATE} " +
                                    "reps or fewer to estimate a 1RM."
                            } else {
                                "One more session unlocks your trend."
                            },
                            unit = unit,
                            line = true,
                            grouped = false,
                            prValue = prDisplay,
                            modifier = Modifier.padding(top = SECTION_LEAD),
                        )
                    }
                    item(key = "weekly-volume") {
                        TrendCard(
                            title = if (repsAreTheMeasure) "Weekly reps" else "Weekly volume",
                            values = weeklySeries,
                            startLabel = history.weeklyTonnage.firstOrNull()
                                ?.let { axisLabel(it.weekStart) }.orEmpty(),
                            endLabel = history.weeklyTonnage.lastOrNull()
                                ?.let { axisLabel(it.weekStart) }.orEmpty(),
                            pointNoun = "week",
                            hint = "One more training week unlocks your trend.",
                            unit = unit,
                            line = false,
                            grouped = true,
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
                        key = { _, summary -> summary.sessionId },
                    ) { index, summary ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .clip(groupedRowShape(index, history.sessions.size))
                                .background(Surface1),
                        ) {
                            if (index > 0) HairlineDivider()
                            SessionRow(
                                summary = summary,
                                unit = unit,
                                loadClass = LoadClass.of(state.exercise?.loadType),
                                onClick = { onOpenSession(summary.sessionId) },
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

    if (routinePickerOpen) {
        RoutinePickerSheet(
            exerciseName = state.exercise?.name.orEmpty(),
            landing = state.exercise?.let { AddDefaults.landingCopy(it) },
            routines = state.routines,
            onDismiss = { routinePickerOpen = false },
            onPick = { routineId ->
                routinePickerOpen = false
                viewModel.addToRoutine(routineId)
            },
        )
    }
}

/**
 * Which routine to put this lift in.
 *
 * Routines that already hold it are shown, not hidden, and are not pressable. Hiding them
 * would answer a question the user did not ask — "why is my push day missing from this list?"
 * — and the repository's add is a silent no-op on a duplicate, so a pressable row would take
 * the tap and do nothing at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePickerSheet(
    exerciseName: String,
    landing: String?,
    routines: List<RoutineMembership>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text("Add $exerciseName to", style = InstrumentType.title, color = TextPrimary)
            if (routines.isEmpty()) {
                Text(
                    "You have no routines yet. Build one in Plan and this lift can go straight into it.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                return@Column
            }
            GroupedList {
                routines.forEachIndexed { index, membership ->
                    if (index > 0) HairlineDivider()
                    InstrumentRow(
                        title = membership.routine.name,
                        subtitle = if (membership.alreadyHolds) {
                            "Already in this routine"
                        } else {
                            liftCountLabel(membership.routine.exercises.size)
                        },
                        onClick = if (membership.alreadyHolds) {
                            null
                        } else {
                            { onPick(membership.routine.id) }
                        },
                    )
                }
            }
            Text(
                landing ?: "Sets, reps and rest start from this lift's defaults. Change them in the routine.",
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        }
    }
}

private fun liftCountLabel(count: Int): String =
    if (count == 1) "1 lift" else "$count lifts"

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
private fun RecordsCard(
    records: Map<PersonalRecordKind, PersonalRecord>,
    unit: WeightUnit,
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
                    record.achievedAt,
                )
            }
            topReps?.let { record ->
                RecordProvenance(
                    "${record.reps} reps at ${record.weightKg.toWeightLabel(unit)}",
                    record.achievedAt,
                )
            }
            estimate?.let { record ->
                // The estimate is derived, so show the set it was derived from; a number with no
                // visible working behind it is not something to train off.
                RecordProvenance(
                    "1RM from ${record.weightKg.toWeightLabel(unit)} × ${record.reps}",
                    record.achievedAt,
                )
            }
        }
    }
}

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
private fun RecordProvenance(detail: String, achievedAt: Long) {
    val achieved = dateLabel(achievedAt)
    Text(
        "$detail · $achieved",
        style = InstrumentType.caption,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A trend, annotated, and never absent.
 *
 * Below two points both charts used to disappear, so a lifter one session in had no way to
 * know a trend was coming; the card now stands with ghost content and says what would fill
 * it. When there is data, the current value leads and the change against the previous point
 * sits beside it — a down week in the quiet colour, never in red.
 */
@Composable
private fun TrendCard(
    title: String,
    values: List<Double>,
    startLabel: String,
    endLabel: String,
    pointNoun: String,
    hint: String,
    unit: WeightUnit,
    line: Boolean,
    grouped: Boolean,
    modifier: Modifier = Modifier,
    prValue: Double? = null,
) {
    GymCard(modifier = modifier) {
        if (values.size < MIN_POINTS_FOR_TREND) {
            Kicker(title)
            GhostTrend()
            Text(hint, style = InstrumentType.caption, color = TextTertiary)
        } else {
            val latest = values.last()
            val previous = values[values.lastIndex - 1]
            LabelledTrend(
                title = title,
                values = values,
                startLabel = startLabel,
                endLabel = endLabel,
                headlineValue = formatSeries(latest, grouped),
                headlineUnit = unit.suffix,
                deltaLabel = seriesDelta(values, unit, grouped),
                deltaIsGain = latest >= previous,
                line = line,
                contentDescription = trendDescription(title, values, pointNoun, unit, grouped),
                prValue = prValue,
            )
        }
    }
}

/** The shape of a chart that does not exist yet. Decorative, so it carries no semantics. */
@Composable
private fun GhostTrend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GHOST_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalAlignment = Alignment.Bottom,
    ) {
        GHOST_FRACTIONS.forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(Hairline),
            )
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
    InstrumentRow(
        title = dateLabel(summary.performedAtMs),
        subtitle = summary.topSet?.let { top ->
            "Top set " + SetCopy.setLine(top.weightKg, top.reps, loadClass, unit)
        },
        onClick = onClick,
    ) {
        MetricCluster(value = summary.workingSets.toString(), label = "sets")
        val column = SetCopy.workColumn(summary.work, unit)
        MetricCluster(value = column.value, label = column.label)
    }
}

@Composable
private fun dateLabel(atMs: Long): String {
    val absolute = remember(atMs) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atMs)) }
    val relative = remember(atMs) { DayLabel.relative(atMs, System.currentTimeMillis()) }
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
private fun seriesDelta(values: List<Double>, unit: WeightUnit, grouped: Boolean): String? {
    if (values.size < MIN_POINTS_FOR_TREND) return null
    val delta = values.last() - values[values.lastIndex - 1]
    val magnitude = formatSeries(abs(delta), grouped)
    if (magnitude == "0") return null
    return "${if (delta > 0.0) "+" else "−"}$magnitude ${unit.suffix}"
}

/**
 * Both charts are a bare [androidx.compose.foundation.Canvas], which carries no semantics at
 * all — the two numbers on this screen that show progress were completely silent to TalkBack.
 */
private fun trendDescription(
    title: String,
    values: List<Double>,
    pointNoun: String,
    unit: WeightUnit,
    grouped: Boolean,
): String {
    val noun = if (values.size == 1) pointNoun else "${pointNoun}s"
    val latest = formatSeries(values.lastOrNull() ?: 0.0, grouped)
    val best = formatSeries(values.maxOrNull() ?: 0.0, grouped)
    return "$title, ${values.size} $noun, latest $latest ${unit.suffix}, best $best ${unit.suffix}"
}

private val axisDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun axisLabel(weekStart: com.sinura.personaltrainer.domain.CivilDate): String =
    axisDateFormatter.format(weekStart.toLocalDate())

private fun axisLabel(atMs: Long): String =
    axisDateFormatter.format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDate())

private const val MIN_POINTS_FOR_TREND = 2

/** A section owns the space above it; the list's own arrangement covers the rest. */
private val SECTION_LEAD = Metrics.sectionGap - Metrics.cardGap

private val GHOST_HEIGHT = 72.dp
private val GHOST_FRACTIONS = listOf(0.34f, 0.5f, 0.42f, 0.66f, 0.55f, 0.8f)

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
}
