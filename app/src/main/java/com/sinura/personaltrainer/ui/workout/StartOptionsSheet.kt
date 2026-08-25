package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Everything you can start, when today's plan is not what you want.
 *
 * This replaces a full-screen interstitial. Tapping Start on Home used to open a screen whose
 * entire job was to ask what you meant — a navigation, a render, and a second tap before any
 * workout began — and, because it needed to handle the case where a session was already
 * running, it quietly became a third place in the app that offered to resume one.
 *
 * The common case skips it now: Home starts today's plan in one tap
 * (agenda when it exists, leftover slot week otherwise). This is what
 * "everything else" looks like, and it opens over the screen you were on
 * — Body, History, or Plan. The sheet itself starts today's plan when
 * one exists. Home itself does not host this sheet.
 *
 * While a session is live the sheet shows no starts at all. That is not a duplicate of the
 * live session bar: this is a modal surface the user deliberately opened, so it owes them an
 * answer about why nothing here will start, and the answer is the session they are already in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartOptionsSheet(
    onDismiss: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onLogPast: () -> Unit = {},
    onLogCardio: () -> Unit = {},
    onLogMixed: () -> Unit = {},
    onOpenLiveActivity: (String) -> Unit = {},
    viewModel: StartOptionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val navigateToCardio by viewModel.navigateToCardio.collectAsStateWithLifecycle()
    val navigateToComposer by viewModel.navigateToComposer.collectAsStateWithLifecycle()
    val inProgress = state.inProgress
    val liveActivity = state.liveActivity
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        viewModel.onSessionNavigationHandled()
        onDismiss()
        onWorkoutStarted(target)
    }
    LaunchedEffect(navigateToCardio) {
        val target = navigateToCardio ?: return@LaunchedEffect
        viewModel.onCardioNavigationHandled()
        onDismiss()
        onOpenLiveActivity(target)
    }
    LaunchedEffect(navigateToComposer) {
        val mode = navigateToComposer ?: return@LaunchedEffect
        viewModel.onComposerNavigationHandled()
        onDismiss()
        when (mode) {
            "cardio" -> onLogCardio()
            "mixed" -> onLogMixed()
            else -> onLogPast()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text("Start a workout", style = InstrumentType.title, color = TextPrimary)
            state.error?.let { message -> GymErrorBanner(message) }

            if (inProgress != null || liveActivity != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Session in progress")
                    // They opened Start on purpose. Sending them to dismiss and find the bar
                    // is a treasure hunt; this button is the answer they came for.
                    PrimaryGymButton(
                        text = "Go to session",
                        onClick = {
                            onDismiss()
                            if (inProgress != null) {
                                onWorkoutStarted(inProgress.id)
                            } else {
                                onOpenLiveActivity(liveActivity!!.id)
                            }
                        },
                    )
                    Text(
                        "Finish or discard this session before starting another.",
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                    TextButton(
                        onClick = { confirmDiscard = true },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Discard it", style = InstrumentType.bodyStrong, color = Danger)
                    }
                }
                return@Column
            }

            val todayStart = state.todayStart
            if (todayStart != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    Kicker("Today")
                    PrimaryGymButton(
                        text = "Start ${todayStart.title}",
                        onClick = viewModel::startToday,
                    )
                    todayStart.preview?.let { preview ->
                        Text(preview, style = InstrumentType.caption, color = TextTertiary)
                    }
                }
            }

            state.suggestion?.let { lift ->
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    Kicker("Suggested")
                    GroupedList {
                        InstrumentRow(
                            title = lift.name,
                            subtitle = state.suggestionReason,
                            onClick = viewModel::startSuggested,
                        )
                    }
                }
            }

            if (state.routines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                    Kicker("From routine")
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
            }

            FreeWorkoutAction(onStart = viewModel::startFree)
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onLogPast()
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Log past workout", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.openComposer("mixed")
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Log mixed session", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                Text(
                    "Backdated strength, or a mixed day.",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onLogCardio()
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Log cardio", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                Text(
                    "Typed time and distance. No fake lift rows.",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
                TextButton(
                    onClick = viewModel::startCardio,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("Start cardio", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
                Text(
                    "Live clock. Survives leaving the app.",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
        }
    }

    if (confirmDiscard && (inProgress != null || liveActivity != null)) {
        val loggedSets = inProgress?.sets?.size ?: 0
        ConfirmActionDialog(
            title = "Discard this session?",
            body = if (loggedSets > 0) {
                "This deletes the session and its $loggedSets logged " +
                    (if (loggedSets == 1) "set" else "sets") + ". This cannot be undone."
            } else {
                "This deletes the session. This cannot be undone."
            },
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discardInProgress()
            },
            onDismiss = { confirmDiscard = false },
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
            subtitle = SessionOrderCopy.NEED_A_LIFT,
        )
    } else {
        val lifts = remember(routine) {
            SessionOrderCopy.numberedPreview(routine.exercises.map { it.exercise.name })
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
            // Quiet on purpose. Today's plan is the filled start when it is here; a second
            // volt next to it reads as two answers to the same tap.
            Text(SessionOrderCopy.FREE_WORKOUT, style = InstrumentType.bodyStrong, color = TextSecondary)
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


// A rough working minute per set: the rest interval the routine asks for, plus the time the
// set itself takes. An estimate the user can sanity-check, not a promise.
private const val SET_WORK_SECONDS = 40
private const val SECONDS_PER_MINUTE = 60
