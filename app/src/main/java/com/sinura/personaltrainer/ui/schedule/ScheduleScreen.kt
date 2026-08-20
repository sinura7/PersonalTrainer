package com.sinura.personaltrainer.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        onWorkoutStarted(id)
        viewModel.onSessionNavigationHandled()
    }
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    // Setup is a visit-once job. It used to occupy the top third of the screen on every
    // visit, so the week — the thing this screen exists to show — opened below the fold.
    var tuning by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScheduleHeader(
            tuning = tuning,
            onBack = onBack,
            onToggleTune = { tuning = !tuning },
            onRegenerate = viewModel::regenerate,
        )
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.weight(1f))
            }
            state.plan == null -> {
                // The error banner belongs here too, not only in the branch that has a plan:
                // a planner failure lands in exactly this branch, and used to render nowhere.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { err -> GymErrorBanner(err) }
                    // Also here, not only in the branch that has a plan: the Tune button sits
                    // above this `when`, so without it tapping Tune with no plan flipped the
                    // label to "Done" and revealed nothing — and setting the split is exactly
                    // what you want before generating a first week.
                    if (tuning) {
                        GymCard {
                            PreferenceBlock(
                                preferences = state.preferences,
                                onDays = viewModel::setTrainingDays,
                                onSplit = viewModel::setSplit,
                                onWeekStart = viewModel::setWeekStart,
                            )
                        }
                    }
                    EmptyState(
                        title = "No week plan yet",
                        body = "Generate a week, or log a workout so suggestions can follow your training.",
                        actionLabel = "Generate week",
                        onAction = viewModel::regenerate,
                    )
                }
            }
            else -> {
                val plan = state.plan!!
                val inProgress = state.inProgress
                // One filled Start on the whole screen, on today or on the next training day.
                // Every other training day starts from its own row.
                val leadDay = plan.dayOn(today)?.takeUnless { it.isRest }
                    ?: plan.nextTrainingOnOrAfter(today)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space2,
                        bottom = Metrics.space8,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    if (tuning) {
                        item(key = "tune") {
                            GymCard {
                                PreferenceBlock(
                                    preferences = state.preferences,
                                    onDays = viewModel::setTrainingDays,
                                    onSplit = viewModel::setSplit,
                                    onWeekStart = viewModel::setWeekStart,
                                )
                            }
                        }
                    }
                    state.error?.let { err ->
                        item(key = "error") { GymErrorBanner(err) }
                    }
                    if (inProgress != null) {
                        item(key = "resume") {
                            PrimaryGymButton(
                                text = "Resume ${inProgress.routineName ?: "workout"}",
                                onClick = { onWorkoutStarted(inProgress.id) },
                            )
                        }
                    }
                    item(key = "week") {
                        GroupedList {
                            plan.days.forEachIndexed { index, day ->
                                if (index > 0) HairlineDivider(startIndent = 0.dp)
                                // A session already running owns every start surface in the
                                // app, so no day offers one while it exists.
                                val startable = inProgress == null && !day.isRest
                                ScheduleDayRow(
                                    day = day,
                                    isToday = day.epochDay == today,
                                    logged = day.epochDay in state.loggedEpochDays,
                                    leadStart = startable && day.epochDay == leadDay?.epochDay,
                                    onStart = if (startable) ({ viewModel.startDay(day) }) else null,
                                    onOpenRoutine = day.routineId?.let { id -> { onOpenRoutine(id) } },
                                )
                            }
                        }
                    }
                    item(key = "summary") {
                        Text(
                            if (plan.thinHistory) {
                                "Starter week from your split. It follows neglected muscles after a few logged sessions."
                            } else {
                                plan.summary
                            },
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A plain header on the window colour, with both of its actions named.
 *
 * Regenerating the week was a bare Refresh glyph, which on a screen full of generated
 * content is indistinguishable from a reload — nothing said it would rewrite the plan.
 */
@Composable
private fun ScheduleHeader(
    tuning: Boolean,
    onBack: () -> Unit,
    onToggleTune: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space2, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = TextSecondary)
        }
        Text(
            "This week",
            modifier = Modifier.weight(1f),
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onToggleTune) {
            Text(
                if (tuning) "Done" else "Tune",
                style = InstrumentType.bodyStrong,
                color = if (tuning) Volt else TextSecondary,
            )
        }
        TextButton(onClick = onRegenerate) {
            Text("New week", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    }
}

@Composable
fun PreferenceBlock(
    preferences: SchedulePreferences,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space5)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Training days", compact = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                items((SchedulePreferences.MIN_DAYS..SchedulePreferences.MAX_DAYS).toList()) { days ->
                    InstrumentChip(
                        label = "$days",
                        selected = preferences.trainingDaysPerWeek == days,
                        onClick = { onDays(days) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Split", compact = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                items(SplitStyle.entries) { style ->
                    InstrumentChip(
                        label = style.displayName,
                        selected = preferences.splitStyle == style,
                        onClick = { onSplit(style) },
                    )
                }
            }
            Text(
                preferences.splitStyle.blurb,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader("Week starts", compact = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    InstrumentChip(
                        label = if (day == DayOfWeek.MONDAY) "Monday" else "Sunday",
                        selected = preferences.weekStart == day,
                        onClick = { onWeekStart(day) },
                    )
                }
            }
        }
    }
}

/**
 * One day of the week, with calendar geometry.
 *
 * The date used to be prose in the row's own body — "Mon · Aug 18 · Today · Logged" — so
 * seven days had no shared left edge to read down, and every training day mounted its own
 * full-width filled button. Here the rail is fixed, the numeral is tabular, today wears a
 * volt rule, and a rest day is quiet by colour rather than by fading the whole row: alpha
 * on a container dims its text along with it, which is how rest days ended up below the
 * contrast floor.
 */
@Composable
private fun ScheduleDayRow(
    day: SuggestedTrainingDay,
    isToday: Boolean,
    logged: Boolean,
    leadStart: Boolean,
    onStart: (() -> Unit)?,
    onOpenRoutine: (() -> Unit)?,
) {
    val dayOfMonth = remember(day.epochDay) {
        LocalDate.ofEpochDay(day.epochDay).dayOfMonth.toString()
    }
    val rowStart = onStart?.takeIf { !leadStart }
    val titleColor = if (day.isRest) TextTertiary else TextPrimary
    val railColor = if (day.isRest) TextTertiary else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .then(
                if (rowStart != null) {
                    Modifier.clickable(onClick = rowStart, onClickLabel = "Start ${day.focusTitle}")
                } else {
                    Modifier
                },
            )
            .padding(end = Metrics.space4, top = Metrics.space3, bottom = Metrics.space3),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(TODAY_RAIL_WIDTH)
                .height(TODAY_RAIL_HEIGHT)
                // The calendar marks today with a hairline ring, not the accent. One encoding.
                    .background(if (isToday) HairlineStrong else Color.Transparent),
        )
        Column(
            modifier = Modifier.width(DATE_RAIL_WIDTH),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Kicker(day.dayOfWeek.shortLabel(), color = if (isToday) Volt else railColor)
            Text(
                dayOfMonth,
                style = InstrumentType.numeralMd,
                color = titleColor,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                if (day.isRest) "Rest" else day.focusTitle,
                style = InstrumentType.title,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                day.routineName ?: day.reason,
                style = InstrumentType.caption,
                color = if (day.isRest) TextTertiary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (leadStart && onStart != null) {
                PrimaryGymButton(
                    text = when {
                        logged -> "Train again"
                        day.routineName != null -> "Start ${day.routineName}"
                        else -> "Start ${day.focusTitle}"
                    },
                    onClick = onStart,
                )
            }
            // Outside the lead-day guard: this opens the day's routine, which every planned
            // day has, and nesting it under the single Start button made six of the seven
            // days unable to reach their own routine at all.
            if (onOpenRoutine != null && day.routineName != null) {
                TextButton(onClick = onOpenRoutine, contentPadding = PaddingValues(0.dp)) {
                    Text("Open routine", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
        if (logged) {
            Kicker("Logged", color = TextTertiary)
        }
    }
}

/**
 * Today's line on Home: what the plan says, and the one button that acts on it.
 *
 * Home used to stack a filled Start/Resume above this card and a second Start below it, all
 * three offering to begin the same session. The card owns the decision now, so the screen
 * has exactly one filled control.
 */
@Composable
fun ThisWeekHomeCard(
    day: SuggestedTrainingDay?,
    nextDay: SuggestedTrainingDay?,
    thinHistory: Boolean,
    loggedToday: Boolean,
    inProgress: Boolean,
    onOpenSchedule: () -> Unit,
    onPrimary: () -> Unit,
) {
    val trainingToday = day?.takeUnless { it.isRest }
    val kicker = when {
        inProgress -> "In progress"
        trainingToday != null -> "Today"
        nextDay != null -> "Next · ${nextDay.dayOfWeek.shortLabel()}"
        else -> "This week"
    }
    val headline = when {
        trainingToday != null -> trainingToday.routineName ?: trainingToday.focusTitle
        nextDay != null -> nextDay.routineName ?: nextDay.focusTitle
        day != null -> "Rest day"
        else -> "No week plan yet"
    }
    val action = when {
        inProgress -> "Resume workout"
        trainingToday != null && loggedToday -> "Train again"
        trainingToday != null -> "Start this session"
        else -> "Start a workout"
    }

    GymCard(onClick = onOpenSchedule) {
        // The button below is already volt; the accent is spent on the live state only.
        Kicker(kicker, color = if (inProgress) Volt else TextSecondary)
        Text(
            headline,
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (thinHistory) {
            Text(
                "Starter week — tightens after a few sessions.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        PrimaryGymButton(
            text = action,
            onClick = onPrimary,
            modifier = Modifier.padding(top = Metrics.space1),
        )
    }
}

fun todayEpochDay(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

private val TODAY_RAIL_WIDTH = 3.dp
private val TODAY_RAIL_HEIGHT = 44.dp
private val DATE_RAIL_WIDTH = 40.dp
