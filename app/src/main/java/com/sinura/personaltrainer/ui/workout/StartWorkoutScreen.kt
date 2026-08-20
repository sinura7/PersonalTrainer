package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Choosing what to train, with enough of each routine visible to choose from.
 *
 * The hierarchy here used to be inverted: "Free workout" — the fallback, the thing you pick
 * when you have no plan — was a full-width filled button above everything, while the routine
 * the lifter actually came for sat in a flat card below a heading, described only as
 * "N exercises". Routines now lead as rich rows carrying their lifts and an estimated
 * duration; free workout is a text action at the bottom, except when there are no routines
 * at all, where it is genuinely the only way forward and earns the button back.
 */
@Composable
fun StartWorkoutScreen(
    onBack: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    viewModel: StartWorkoutViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Forward navigation: navigate, then ack. Every target uses launchSingleTop, so a
    // re-fired effect is absorbed rather than stacking a duplicate screen.
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        onWorkoutStarted(id)
        viewModel.onSessionNavigationHandled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        StartWorkoutHeader(onBack = onBack)

        if (state.isLoading) {
            ScreenLoading()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Metrics.gutter,
                    end = Metrics.gutter,
                    top = Metrics.space2,
                    bottom = Metrics.space7,
                ),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                state.error?.let { error ->
                    item(key = "error") { GymErrorBanner(error) }
                }

                state.inProgress?.let { session ->
                    item(key = "resume") {
                        ResumeBlock(
                            routineName = session.routineName,
                            onResume = { onWorkoutStarted(session.id) },
                        )
                    }
                }

                if (state.inProgress == null) {
                    if (state.routines.isEmpty()) {
                        item(key = "no-routines") {
                            EmptyState(
                                title = "No routines yet",
                                body = "Start a free workout and add lifts as you go, or build a routine first.",
                                actionLabel = "Free workout",
                                onAction = viewModel::startFree,
                            )
                        }
                    } else {
                        item(key = "routines-label") { GymSectionHeader("From routine") }
                        item(key = "routines") {
                            GroupedList {
                                state.routines.forEachIndexed { index, routine ->
                                    if (index > 0) HairlineDivider()
                                    RoutineRow(
                                        routine = routine,
                                        onStart = { viewModel.startRoutine(routine.id) },
                                    )
                                }
                            }
                        }
                        item(key = "free") {
                            FreeWorkoutAction(
                                onStart = viewModel::startFree,
                                modifier = Modifier.padding(top = Metrics.space3),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartWorkoutHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space4, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
            )
        }
        Text(
            "Start workout",
            modifier = Modifier.weight(1f),
            style = InstrumentType.display,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResumeBlock(routineName: String?, onResume: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        Kicker("Session in progress")
        PrimaryGymButton(
            text = "Resume ${routineName ?: "workout"}",
            onClick = onResume,
        )
        Text(
            "Finish or discard the current session before starting another.",
            style = InstrumentType.body,
            color = TextSecondary,
        )
    }
}

/**
 * A routine as something you can choose between, rather than a name and a count.
 *
 * The two numbers on the right are what actually decides the tap in a gym: how much work
 * this is, and how long it will take. The duration is an estimate and says so — the app
 * knows the planned sets and the planned rest, which is most of a session's length.
 */
@Composable
private fun RoutineRow(routine: Routine, onStart: () -> Unit) {
    // An empty routine cannot be started, so it does not offer a press or a readout either.
    // The old card took the tap and silently did nothing.
    if (routine.exercises.isEmpty()) {
        InstrumentRow(
            title = routine.name,
            subtitle = "Add at least one lift before starting",
        )
    } else {
        val lifts = remember(routine) {
            routine.exercises.take(LIFTS_PREVIEWED).joinToString(" · ") { it.exercise.name }
        }
        val plannedSets = remember(routine) {
            routine.exercises.sumOf { it.targetSets.coerceAtLeast(1) }
        }
        val minutes = remember(routine) { estimatedMinutes(routine) }

        InstrumentRow(
            title = routine.name,
            subtitle = lifts,
            onClick = onStart,
        ) {
            MetricCluster(value = plannedSets.toString(), label = "sets")
            MetricCluster(value = "~$minutes", label = "min")
        }
    }
}

@Composable
private fun FreeWorkoutAction(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        TextButton(onClick = onStart, contentPadding = PaddingValues(0.dp)) {
            Text("Free workout", style = InstrumentType.bodyStrong, color = Volt)
        }
        Text(
            "No plan. Add lifts as you go.",
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}

/**
 * Rest is most of a strength session, so planned sets times planned rest is close enough to
 * be useful. [SET_WORK_SECONDS] covers the set itself and the walk to the rack; without it a
 * 5×5 reads as about half the time it really takes.
 */
private fun estimatedMinutes(routine: Routine): Int {
    val seconds = routine.exercises.sumOf { item ->
        item.targetSets.coerceAtLeast(1) * (item.restSeconds.coerceAtLeast(0) + SET_WORK_SECONDS)
    }
    return ((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).coerceAtLeast(1)
}

private const val LIFTS_PREVIEWED = 3
private const val SET_WORK_SECONDS = 40
private const val SECONDS_PER_MINUTE = 60
