package com.sinura.personaltrainer.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.LiveSessionRules
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.LeaveCardioDialog
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.imeAction
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveCardioScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    viewModel: LiveCardioViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finishedId by viewModel.finishedId.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(finishedId) {
        val id = finishedId ?: return@LaunchedEffect
        viewModel.onFinishedHandled()
        onFinished(id)
    }

    BackHandler(enabled = !state.missing) { confirmLeave = true }

    if (confirmLeave) {
        LeaveCardioDialog(
            onLeaveRunning = {
                confirmLeave = false
                onExit()
            },
            onStay = { confirmLeave = false },
            onDiscardInstead = {
                confirmLeave = false
                confirmDiscard = true
            },
            onDismiss = { confirmLeave = false },
        )
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = CardioCopy.DISCARD_TITLE,
            body = CardioCopy.DISCARD_BODY,
            confirmLabel = CardioCopy.DISCARD_CONFIRM,
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    Scaffold(
        bottomBar = {
            if (!state.missing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HairlineDivider(startIndent = 0.dp)
                    CardioActionDock(
                        finishing = state.finishing,
                        onFinish = viewModel::finish,
                        onLeaveRunning = onExit,
                        onDiscard = { confirmDiscard = true },
                    )
                }
            }
        },
    ) { padding ->
        if (state.missing) {
            EmptyState(
                title = "No live cardio",
                body = "That session is gone. Start a new one from Home.",
                actionLabel = "Back",
                onAction = onExit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Metrics.gutter),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter, vertical = Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(state.session?.title ?: "Cardio", style = InstrumentType.title, color = TextPrimary)
            ElapsedReadout(elapsedSeconds = state.elapsedSeconds)
            Text(CardioCopy.CLOCK_CAPTION, style = InstrumentType.caption, color = TextSecondary)
            state.error?.let { GymErrorBanner(it, onDismiss = viewModel::dismissError) }
            Kicker(CardioCopy.TYPE)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                CardioType.entries.forEach { option ->
                    InstrumentChip(
                        label = CardioCopy.name(option),
                        selected = option == state.type,
                        onClick = { viewModel.setType(option) },
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                InstrumentChip(
                    label = CardioCopy.INDOOR,
                    selected = state.indoor,
                    onClick = { viewModel.setIndoor(true) },
                )
                InstrumentChip(
                    label = CardioCopy.OUTDOOR,
                    selected = !state.indoor,
                    onClick = { viewModel.setIndoor(false) },
                )
            }
            OutlinedTextField(
                value = state.distanceKm,
                onValueChange = { viewModel.setDistanceKm(NumericEntry.filterDecimal(it)) },
                label = { Text(CardioCopy.DISTANCE_LABEL) },
                singleLine = true,
                textStyle = InstrumentType.numeralMd,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = NumericEntry.LIVE_CARDIO_DISTANCE.imeAction(),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Finish is the one Volt — the log-loop analog of Log. Leave running and Discard are
 * real stacked controls, not footnotes. Scaffold's bottomBar draws edge-to-edge and
 * this route hides the tab bar, so [PinnedDock] owns the system-nav inset.
 */
@Composable
internal fun CardioActionDock(
    finishing: Boolean,
    onFinish: () -> Unit,
    onLeaveRunning: () -> Unit,
    onDiscard: () -> Unit,
) {
    PinnedDock(
        volt = {
            PrimaryGymButton(
                text = if (finishing) CardioCopy.FINISHING else CardioCopy.FINISH,
                onClick = onFinish,
                modifier = Modifier.testTag(CardioTags.FINISH),
                enabled = !finishing,
                height = Metrics.commit,
            )
        },
        secondary = {
            SecondaryGymButton(
                text = CardioCopy.LEAVE_RUNNING,
                onClick = onLeaveRunning,
                modifier = Modifier.testTag(CardioTags.LEAVE),
            )
        },
        tertiary = {
            SecondaryGymButton(
                text = CardioCopy.DISCARD,
                onClick = onDiscard,
                modifier = Modifier.testTag(CardioTags.DISCARD),
                contentColor = Danger,
            )
        },
    )
}

/**
 * A clock TalkBack can read on demand. It must not announce every tick.
 */
@Composable
internal fun ElapsedReadout(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val formatted = LiveSessionRules.formatElapsed(elapsedSeconds)
    Text(
        formatted,
        style = InstrumentType.display,
        color = TextPrimary,
        modifier = modifier
            .testTag(CardioTags.ELAPSED)
            .clearAndSetSemantics {
                contentDescription = "Elapsed $formatted"
            },
    )
}

object CardioTags {
    const val ELAPSED = "live-cardio-elapsed"
    const val FINISH = "live-cardio-finish"
    const val LEAVE = "live-cardio-leave"
    const val DISCARD = "live-cardio-discard"
}
