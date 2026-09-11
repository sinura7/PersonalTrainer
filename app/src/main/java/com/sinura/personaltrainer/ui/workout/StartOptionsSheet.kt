package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineCardCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.StartOptionsCopy
import com.sinura.personaltrainer.domain.estimatedSessionMinutes
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
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
 * Home confirms today's plan on the board (ADR-018) and does not host
 * this sheet. This is what "everything else" looks like, and it opens
 * over the screen you were on — Body, History, or Plan. The sheet
 * itself still starts today's plan when one exists.
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
            Text(
                "Start a workout",
                modifier = Modifier.semantics { heading() },
                style = InstrumentType.title,
                color = TextPrimary,
            )
            if (state.isLoading) {
                ScreenLoading(Modifier.fillMaxWidth().height(96.dp))
                return@Column
            }
            state.error?.let { message -> GymErrorBanner(message, onDismiss = viewModel::dismissError) }

            if (inProgress != null || liveActivity != null) {
                val kind = if (inProgress != null) LiveBarKind.WORKOUT else LiveBarKind.ACTIVITY
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Session in progress")
                    // They opened Start on purpose. Sending them to dismiss and find the bar
                    // is a treasure hunt; this button is the answer they came for.
                    PrimaryGymButton(
                        text = LiveBarCopy.resumeLabel(kind),
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
                        Text(LiveBarCopy.discard(kind), style = InstrumentType.bodyStrong, color = Danger)
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
                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
                        state.routines.forEach { routine ->
                            RoutineRow(
                                routine = routine,
                                onStart = { viewModel.startRoutine(routine.id) },
                            )
                        }
                    }
                }
            }

            FreeWorkoutAction(onStart = viewModel::startFree)
            LogAndCardioActions(
                onLogPast = {
                    onDismiss()
                    onLogPast()
                },
                onLogMixed = {
                    onDismiss()
                    viewModel.openComposer("mixed")
                },
                onLogCardio = {
                    onDismiss()
                    onLogCardio()
                },
                onStartCardio = viewModel::startCardio,
            )
        }
    }

    if (confirmDiscard && (inProgress != null || liveActivity != null)) {
        val kind = if (inProgress != null) LiveBarKind.WORKOUT else LiveBarKind.ACTIVITY
        val loggedSets = state.inProgressSetCount
        ConfirmActionDialog(
            title = LiveBarCopy.discardTitle(kind),
            body = LiveBarCopy.discardBody(kind, loggedSets),
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
 * A routine as a floor you can walk onto, not a name and a count.
 *
 * S-02: the first three lifts are pictured, and the kit mix sits under
 * them, so a machine day looks like machines. The two numbers on the
 * right are still what decides the tap: how much work this is, and how
 * long it will take. The duration is an estimate and says so — the app
 * knows the planned sets and the planned rest, which is most of a
 * session's length.
 */
@Composable
private fun RoutineRow(routine: Routine, onStart: () -> Unit) {
    // An empty routine cannot be started, so it does not offer a press
    // or a readout either. The old card took the tap and silently did nothing.
    if (routine.exercises.isEmpty()) {
        GymCard {
            Text(routine.name, style = InstrumentType.title, color = TextPrimary)
            Text(
                SessionOrderCopy.NEED_A_LIFT,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        return
    }
    val lifts = remember(routine) {
        SessionOrderCopy.numberedPreview(routine.exercises.map { it.exercise.name })
    }
    val mix = remember(routine) {
        RoutineCardCopy.mix(routine.exercises.map { it.exercise.equipment })
    }
    val stills = remember(routine) {
        routine.exercises.take(RoutineCardCopy.STILL_LIMIT).map { it.exercise }
    }
    val plannedSets = remember(routine) {
        routine.exercises.sumOf { it.targetSets.coerceAtLeast(1) }
    }
    val minutes = remember(routine) { estimatedSessionMinutes(routine) }

    GymCard(onClick = onStart) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                routine.name,
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetricCluster(value = plannedSets.toString(), label = "sets")
            MetricCluster(value = "~$minutes", label = "min")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            stills.forEach { exercise ->
                ExerciseThumb(exercise = exercise)
            }
        }
        mix?.let { kit ->
            Text(
                kit,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            lifts,
            style = InstrumentType.body,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogAndCardioActions(
    onLogPast: () -> Unit,
    onLogMixed: () -> Unit,
    onLogCardio: () -> Unit,
    onStartCardio: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            Kicker("Log")
            GroupedList {
                InstrumentRow(
                    title = "Past workout",
                    subtitle = "Backdated strength. Nothing is written until you save.",
                    onClick = onLogPast,
                )
                HairlineDivider()
                InstrumentRow(
                    title = "Mixed session",
                    subtitle = ActivityDetailCopy.MIXED_CAPTION,
                    onClick = onLogMixed,
                )
                HairlineDivider()
                InstrumentRow(
                    title = "Cardio",
                    subtitle = "Typed time and distance.",
                    onClick = onLogCardio,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            Kicker("Live cardio")
            GroupedList {
                InstrumentRow(
                    title = "Start cardio",
                    subtitle = "Live clock. Survives leaving the app.",
                    onClick = onStartCardio,
                )
            }
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
 * Quiet header control on Body, History, and Plan. Not a Volt — Home
 * owns Start. The sheet this opens still starts today's plan when one
 * exists.
 */
@Composable
fun StartSheetOpener(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onOpen,
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .semantics { contentDescription = StartOptionsCopy.OPEN_SPOKEN },
        contentPadding = PaddingValues(horizontal = Metrics.space2, vertical = 0.dp),
    ) {
        Text(
            StartOptionsCopy.OPEN,
            style = InstrumentType.bodyStrong,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
