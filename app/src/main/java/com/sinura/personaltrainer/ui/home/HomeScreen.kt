package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.MastheadCopy
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.featuredSession
import com.sinura.personaltrainer.domain.nextSessionReason
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ResumeOrDiscardDialog
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.StartOptionsSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onResumeWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLogActivity: (String) -> Unit = {},
    onOpenLiveCardio: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        onResumeWorkout(id)
        viewModel.onSessionNavigationHandled()
    }
    val blocked by viewModel.blockedByInProgress.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val inProgress = state.inProgress
    // Home owns the sheet's visibility now that there is no interstitial to navigate to.
    var startOptionsOpen by rememberSaveable { mutableStateOf(false) }

    // Starting a planned day while another session is live is a question, not something the
    // app answers on the user's behalf. Composed before the loading return so it survives a
    // recomposition that briefly reports loading.
    if (blocked != null) {
        ResumeOrDiscardDialog(
            onResume = viewModel::resumeBlocked,
            onDiscardAndStart = viewModel::discardBlockedAndStart,
            onDismiss = viewModel::dismissBlockedStart,
        )
    }

    if (state.isLoading) {
        ScreenLoading()
        return
    }

    val today = todayEpochDay()
    val plan = state.weekPlan
    val todayDay = plan?.dayOn(today)
    // Read from the full logged-day set, not the three-session stat feed: a fourth session
    // today would otherwise push today's own entry out of the window the masthead reads.
    val loggedToday = today in state.loggedEpochDays
    // "Nothing is planned" and "today is a planned rest day" are different sentences, and the
    // derivation cannot tell them apart on its own — an unpinned day and a rest day are the
    // same object.
    val hasPlan = plan?.days?.any { !it.isRest } == true
    val liftCount = todayDay?.routineId?.let { routineId ->
        state.routines.firstOrNull { it.id == routineId }?.exercises?.size
    }
    val nextDay = plan?.nextTrainingOnOrAfter(today)
    // The session Home is actually talking about. One derivation, shared by the card's headline
    // and by the lift list underneath it — see featuredSession.
    val featured = featuredSession(today = todayDay, next = nextDay)

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
                    // The masthead describes the DAY, never the session. The live bar owns
                    // live, and a masthead that switched to narrating the workout would be a
                    // second answer to "where is my workout" on the screen that had three.
                    headline = MastheadCopy.headline(
                        day = todayDay,
                        loggedToday = loggedToday,
                        liftCount = liftCount,
                        hasPlan = hasPlan,
                    ),
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
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                // The hero carries Home's only filled button, and it never says Resume: while
                // a session is live the LiveSessionBar is the only surface that returns to it.
                // The lifts and the reason describe `featured` — the same day the card's own
                // headline names — because they are derived from one shared rule rather than
                // from two that agree until one of them changes.
                ThisWeekCard(
                    day = todayDay,
                    nextDay = nextDay,
                    loggedToday = loggedToday,
                    sessionLive = inProgress != null,
                    hasRoutines = state.routines.isNotEmpty(),
                    lifts = featured?.routineId
                        ?.let { id -> state.routines.firstOrNull { it.id == id } }
                        ?.exercises.orEmpty()
                        .take(LIFTS_PREVIEWED)
                        .map { it.exercise.name },
                    reason = nextSessionReason(featured, state.recommendations),
                    onSuggestWeek = {
                        viewModel.requestWeekSuggestion()
                        onOpenPlan()
                    },
                    onReplayAnswers = {
                        viewModel.requestAnswerReplay()
                        onOpenPlan()
                    },
                    onPrimary = {
                        val target = todayDay?.takeUnless { it.isRest }
                        // One tap starts today's plan. Everything else — a rest day, an empty
                        // week, a session already running — is a question, and the sheet is
                        // where questions get asked.
                        when {
                            inProgress != null || target == null -> startOptionsOpen = true
                            else -> viewModel.startSuggestedDay(target)
                        }
                    },
                )
                // The card body no longer navigates. A whole-card tap that went to the plan,
                // with a filled Start inside it, was a mis-tap trap on the most-pressed
                // control in the app.
                //
                // The block reads as part of that link rather than as its own row: "Week 3 of
                // 12" is where this week sits, and where it sits is what the link goes to see.
                LinkRow(
                    label = "This week",
                    trailing = state.block?.let { block ->
                        if (block.isCompleteOn(today)) {
                            "Block complete"
                        } else {
                            "Week ${block.displayWeekOn(today)} of ${block.weeks}"
                        }
                    },
                    onClick = onOpenPlan,
                )
            }
        }
        if (plan != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    // The same composable Plan renders, fed from the same derived week — not a
                    // Home-only variant that agrees by convention until one of them changes.
                    WeekStrip(
                        days = plan.days,
                        proposals = emptyMap(),
                        loggedEpochDays = state.loggedEpochDays,
                        today = today,
                        onOpenDay = { onOpenPlan() },
                    )
                    if (state.lighterWeek) {
                        Text(
                            LighterWeek.CAPTION,
                            style = InstrumentType.caption,
                            color = Volt,
                        )
                    }
                }
            }
        }
        if (state.readyToProgress.isNotEmpty()) {
            item {
                ReadyToProgressSection(
                    hints = state.readyToProgress,
                    unit = unit,
                    onOpenExercise = onOpenExercise,
                )
            }
        }
        item {
            LinkRow(label = "Training calendar", onClick = onOpenHistory)
        }
    }

    if (startOptionsOpen) {
        StartOptionsSheet(
            onDismiss = { startOptionsOpen = false },
            onWorkoutStarted = onResumeWorkout,
            todayDay = todayDay,
            onStartToday = todayDay?.takeUnless { it.isRest }?.let { target ->
                { viewModel.startSuggestedDay(target) }
            },
            onLogPast = { onLogActivity("strength") },
            onLogCardio = { onLogActivity("cardio") },
            onLogMixed = { onLogActivity("mixed") },
            onOpenLiveActivity = onOpenLiveCardio,
        )
    }
}

/**
 * A tertiary row that goes somewhere.
 *
 * Deliberately quiet: these are the two places Home hands off to another tab, and neither
 * competes with the hero for the eye.
 */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit, trailing: String? = null) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text("$label  \u203a", style = InstrumentType.bodyStrong, color = TextSecondary)
        if (trailing != null) {
            Text(
                "  ·  $trailing",
                style = InstrumentType.caption,
                color = TextTertiary,
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
                maxLines = LogLoopScale.headlineLines(LocalDensity.current.fontScale),
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
    val column = remember(lastSession, unit) {
        lastSession?.let { session -> SetCopy.workColumn(session.work(), unit) }
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
            value = column?.value ?: NO_VALUE,
            unit = column?.label,
            valueColor = if (column != null) TextPrimary else TextTertiary,
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
    onOpenExercise: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        GymSectionHeader("Ready to progress")
        GroupedList {
            hints.forEachIndexed { index, hint ->
                if (index > 0) HairlineDivider()
                InstrumentRow(
                    title = hint.exerciseName,
                    subtitle = "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}",
                    // The lift, not a start screen. A row that names a lift and opens a menu
                    // was asking the user to find it again themselves.
                    onClick = { onOpenExercise(hint.exerciseId) },
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

// The separator is quoted: everything outside quotes in a pattern is a format field.
private const val DATE_LINE_PATTERN = "EEEE '·' d MMM"
private const val NO_VALUE = "—"
private const val LIFTS_PREVIEWED = 3
