package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.progress.dispatchRecommendation
import com.sinura.personaltrainer.ui.schedule.ThisWeekHomeCard
import com.sinura.personaltrainer.ui.schedule.todayEpochDay
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenLibraryMuscle: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        onResumeWorkout(id)
        viewModel.onSessionNavigationHandled()
    }
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val inProgress = state.inProgress

    if (state.isLoading) {
        ScreenLoading()
        return
    }

    val today = todayEpochDay()
    val plan = state.weekPlan
    val todayDay = plan?.dayOn(today)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Metrics.gutter,
            end = Metrics.gutter,
            top = Metrics.space4,
            bottom = Metrics.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                HomeMasthead(
                    epochDay = today,
                    headline = todayHeadline(day = todayDay, inProgress = inProgress != null),
                    onOpenSettings = onOpenSettings,
                )
                HomeStatRow(
                    lastSession = state.recentSessions.firstOrNull(),
                    todayEpoch = today,
                    unit = unit,
                )
            }
        }
        state.error?.let { message ->
            item {
                GymErrorBanner(message)
            }
        }
        item {
            // The hero carries Home's only filled button, and it never says Resume: while a
            // session is live the LiveSessionBar is the only surface that returns to it.
            // Home used to answer "where is my workout" three ways — this card, the rest
            // strip, and the hero's own relabelling — none of which existed off this screen.
            ThisWeekHomeCard(
                day = todayDay,
                nextDay = plan?.nextTrainingOnOrAfter(today),
                thinHistory = plan?.thinHistory == true,
                loggedToday = state.recentSessions.any { session ->
                    todayEpochDay(session.date) == today
                },
                // Always false: the card can no longer render its In-progress/Resume branch.
                inProgress = false,
                onOpenSchedule = onOpenSchedule,
                onPrimary = {
                    val target = todayDay?.takeUnless { it.isRest }
                    when {
                        // The start screen states the block explicitly rather than silently
                        // resuming; the bar is how you get back to a running session.
                        inProgress != null -> onStartWorkout()
                        target != null -> viewModel.startSuggestedDay(target)
                        else -> onStartWorkout()
                    }
                },
            )
        }
        item {
            TrainingBalanceCard(
                hasWork = state.heatSnapshot?.hasAnyWorkingSets == true,
                recommendations = state.recommendations.take(1),
                onOpenProgress = onOpenProgress,
                onRecommendation = { rec ->
                    dispatchRecommendation(
                        recommendation = rec,
                        onOpenLibrary = onOpenLibraryMuscle,
                        onStartWorkout = onStartWorkout,
                        onOpenRoutines = onOpenRoutines,
                        onOpenProgress = onOpenProgress,
                    )
                },
                snapshotHighlights = state.heatSnapshot,
            )
        }
        if (state.readyToProgress.isNotEmpty()) {
            item {
                ReadyToProgressSection(
                    hints = state.readyToProgress,
                    unit = unit,
                    onStartWorkout = onStartWorkout,
                )
            }
        }
        item {
            RecentSection(
                sessions = state.recentSessions,
                dateFormat = dateFormat,
                onOpenHistory = onOpenHistory,
                onOpenSession = onOpenSession,
            )
        }
    }
}

/**
 * The date, then the one thing the user opened the app to find out.
 *
 * What this replaces led with the app's own name under a greeting, with the unit preference
 * as a third line and a settings entry built as an [IconButton] nested inside a clickable
 * column with its own caption — two overlapping targets around one 24dp glyph. A product's
 * face states today's answer; its name is on the launcher icon.
 */
@Composable
private fun HomeMasthead(
    epochDay: Long,
    headline: String,
    onOpenSettings: () -> Unit,
) {
    val dateLine = remember(epochDay) {
        DateTimeFormatter.ofPattern(DATE_LINE_PATTERN).format(LocalDate.ofEpochDay(epochDay))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Kicker(dateLine)
            Text(
                headline,
                style = InstrumentType.display,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}

/**
 * Two numerals above the fold.
 *
 * Home had none: a fitness tracker whose first screen was a menu of links, where the largest
 * type on the page was the app's own name. These are the last session's working volume and
 * how long ago it was — both exact from the state Home already holds, unlike a rolling
 * weekly total, which would have to be estimated from the three sessions it receives.
 */
@Composable
private fun HomeStatRow(
    lastSession: WorkoutSession?,
    todayEpoch: Long,
    unit: WeightUnit,
) {
    val volume = lastSession?.let { session ->
        WeightConverter.formatGroupedNumber(
            WeightConverter.toDisplayValue(session.workingVolumeKg(), unit),
        )
    }
    val daysSince = lastSession?.let { session ->
        (todayEpoch - todayEpochDay(session.date)).coerceAtLeast(0L).toString()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap),
    ) {
        StatTile(
            label = "Last session",
            value = volume ?: NO_VALUE,
            unit = if (volume != null) unit.suffix else null,
            valueColor = if (volume != null) TextPrimary else TextTertiary,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Days since",
            value = daysSince ?: NO_VALUE,
            valueColor = if (daysSince != null) TextPrimary else TextTertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadyToProgressSection(
    hints: List<ProgressionHint>,
    unit: WeightUnit,
    onStartWorkout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        GymSectionHeader("Ready to progress")
        GroupedList {
            hints.forEachIndexed { index, hint ->
                if (index > 0) HairlineDivider()
                InstrumentRow(
                    title = hint.exerciseName,
                    subtitle = "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}",
                    onClick = onStartWorkout,
                ) {
                    MetricCluster(
                        value = WeightConverter.formatDisplayNumber(
                            WeightConverter.toDisplayValue(hint.suggestedWeightKg, unit),
                        ),
                        label = "target",
                        unit = unit.suffix,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSection(
    sessions: List<WorkoutSession>,
    dateFormat: DateFormat,
    onOpenHistory: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        GymSectionHeader(
            title = "Recent",
            // Kept, unlike the other section actions: the rows below open one session each,
            // the action opens the whole history — two destinations, not one twice.
            actionLabel = if (sessions.isNotEmpty()) "History" else null,
            onAction = if (sessions.isNotEmpty()) onOpenHistory else null,
        )
        if (sessions.isEmpty()) {
            EmptyState(
                title = "No sessions yet",
                body = "Finish a workout and it lands here.",
                compact = true,
            )
        } else {
            GroupedList {
                sessions.forEachIndexed { index, session ->
                    if (index > 0) HairlineDivider()
                    SessionLogRow(
                        title = session.routineName ?: "Workout",
                        dateLabel = dateFormat.format(Date(session.date)),
                        workingSets = session.sets.count { !it.isWarmup },
                        volumeKg = session.workingVolumeKg(),
                        durationMinutes = session.durationMinutes,
                        onClick = { onOpenSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingBalanceCard(
    hasWork: Boolean,
    recommendations: List<TrainingRecommendation>,
    onOpenProgress: () -> Unit,
    onRecommendation: (TrainingRecommendation) -> Unit,
    snapshotHighlights: BodyHeatSnapshot?,
) {
    val highlights = listOf(
        CanonicalMuscle.CHEST,
        CanonicalMuscle.BACK,
        CanonicalMuscle.SHOULDERS,
        CanonicalMuscle.QUADRICEPS,
        CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.CORE,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        // No header action: the card underneath already navigates to the body map, and a
        // section header that repeats its own card's tap is one affordance drawn twice.
        GymSectionHeader("Training")
        GymCard(onClick = onOpenProgress) {
            if (!hasWork) {
                Text(
                    "Finish a few sessions to see which muscles are loaded.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            } else {
                Kicker(snapshotHighlights?.window?.label ?: "Last 7 days")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    highlights.forEach { muscle ->
                        val load = snapshotHighlights?.load(muscle)
                        MuscleHeatTile(
                            label = muscle.shortLabel,
                            workingSets = load?.workingSets ?: 0,
                            heat = load?.heat ?: 0.0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        recommendations.firstOrNull()?.let { rec ->
            GymCard(onClick = { onRecommendation(rec) }) {
                Text(
                    rec.title,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rec.reason,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One muscle's share of the last window: a heat bar, its working-set count, its name.
 *
 * These were six 16dp dots with nothing but a label — the app's only instrument moment,
 * drawn at the size of a chart legend and carrying no number at all. The fill comes from
 * [heatColor], the same ramp as the body map and the calendar.
 */
@Composable
private fun MuscleHeatTile(
    label: String,
    workingSets: Int,
    heat: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEAT_TILE_HEIGHT)
                .clip(RoundedCornerShape(Radius.xs))
                .background(heatColor(heat)),
        )
        Text(
            workingSets.toString(),
            style = InstrumentType.numeralSm,
            color = if (workingSets > 0) TextPrimary else TextTertiary,
            maxLines = 1,
        )
        Kicker(label, color = TextTertiary)
    }
}

/** Today's answer, in the fewest words that still name the session. */
private fun todayHeadline(day: SuggestedTrainingDay?, inProgress: Boolean): String = when {
    inProgress -> "Workout in progress"
    day == null -> "Ready to train"
    day.isRest -> "Rest day"
    day.focusKind == SessionFocusKind.RECOVERY -> "Recovery day"
    else -> "${day.focusKind.label.lowercase().replaceFirstChar { it.titlecase() }} day"
}

// The separator is quoted: everything outside quotes in a pattern is a format field.
private const val DATE_LINE_PATTERN = "EEEE '·' d MMM"
private const val NO_VALUE = "—"
private val HEAT_TILE_HEIGHT = 28.dp
