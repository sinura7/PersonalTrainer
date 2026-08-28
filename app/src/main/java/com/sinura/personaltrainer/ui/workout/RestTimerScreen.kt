package com.sinura.personaltrainer.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestControl
import com.sinura.personaltrainer.ui.components.RestLinearTrack
import com.sinura.personaltrainer.ui.components.RestPresetChips
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn
import kotlinx.coroutines.delay

object RestFloorTags {
    const val ROOT = "rest-floor"
    const val CLOCK = "rest-floor-clock"
    const val SKIP = "rest-floor-skip"
    const val MINUS = "rest-floor-minus"
    const val PLUS = "rest-floor-plus"
    const val START = "rest-floor-start"
    const val CLOSE = "rest-floor-close"
    const val BACK_TO_BAR = "rest-floor-back"
    const val NEXT = "rest-floor-next"
}

private const val FINISHED_DWELL_MS = 3_500L
private const val URGENT_SECONDS = 10

@Composable
fun RestTimerScreen(
    onClose: () -> Unit,
    viewModel: RestTimerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(RestFloorTags.ROOT),
        containerColor = Pit,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pit)
                    .padding(end = Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag(RestFloorTags.CLOSE),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close rest",
                        tint = TextSecondary,
                    )
                }
            }
        },
    ) { padding ->
        when (state.loadState) {
            SessionLoadState.LOADING -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            SessionLoadState.MISSING -> {
                EmptyState(
                    title = "Workout missing",
                    body = "This session was finished, discarded, or replaced by a restore. " +
                        "Nothing was lost from your history.",
                    actionLabel = "Back",
                    onAction = onClose,
                    compact = true,
                    modifier = Modifier
                        .padding(padding)
                        .padding(Metrics.gutter),
                )
            }
            SessionLoadState.FOUND -> {
                RestFloorBody(
                    rest = state.rest,
                    floor = state.floor,
                    onSkip = viewModel::skipRest,
                    onAdjust = viewModel::adjustRest,
                    onSelectPreset = viewModel::selectRestDuration,
                    onCustom = viewModel::selectCustomRest,
                    onStart = viewModel::startSelectedRest,
                    onBackToBar = onClose,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun RestFloorBody(
    rest: RestTimerUiState,
    floor: RestFloorContext,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onCustom: (String) -> Boolean,
    onStart: () -> Unit,
    onBackToBar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var justFinished by remember { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(rest.running) }

    LaunchedEffect(rest.running, rest.remainingSeconds) {
        if (wasRunning && !rest.running && rest.remainingSeconds <= 0) justFinished = true
        if (rest.running) justFinished = false
        wasRunning = rest.running
    }
    LaunchedEffect(justFinished) {
        if (justFinished) {
            delay(FINISHED_DWELL_MS)
            justFinished = false
        }
    }

    val safeRemaining = rest.remainingSeconds.coerceAtLeast(0)
    val urgent = rest.running && safeRemaining <= URGENT_SECONDS
    val accent = when {
        justFinished -> PrGold
        urgent -> Warn
        else -> RestCyan
    }
    val displaySeconds = if (justFinished) 0 else if (rest.running) safeRemaining else rest.totalSeconds
    val clock = RestTimer.formatClock(displaySeconds)
    val kicker = when {
        justFinished -> "Back to the bar"
        rest.running -> "REST"
        else -> "Next rest"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space4),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Kicker(kicker, color = accent)
        RestFloorClock(clock = clock, running = rest.running || justFinished)
        RestLinearTrack(
            remainingSeconds = if (justFinished) 0 else if (rest.running) safeRemaining else rest.totalSeconds,
            totalSeconds = rest.totalSeconds,
            accent = accent,
            finished = justFinished,
        )
        floor.exerciseName?.let { name ->
            Text(
                name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        floor.lastSetLine?.let { line ->
            Text(
                line,
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        floor.sessionTargetLine?.let { line ->
            Text(
                line,
                modifier = Modifier.testTag(RestFloorTags.NEXT),
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            rest.running -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    RestControl(
                        label = "−15s",
                        onClick = { onAdjust(-15) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(RestFloorTags.MINUS),
                    )
                    RestControl(
                        label = "Skip",
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(RestFloorTags.SKIP),
                    )
                    RestControl(
                        label = "+15s",
                        onClick = { onAdjust(15) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(RestFloorTags.PLUS),
                    )
                }
            }
            justFinished -> {
                PrimaryGymButton(
                    text = "Back to the bar",
                    onClick = onBackToBar,
                    modifier = Modifier.testTag(RestFloorTags.BACK_TO_BAR),
                )
            }
            else -> {
                RestFloorIdleControls(
                    totalSeconds = rest.totalSeconds,
                    onPreset = onSelectPreset,
                    onCustom = onCustom,
                    onStart = onStart,
                )
            }
        }
    }
}

/**
 * A clock TalkBack can read on demand. It must not announce every tick.
 */
@Composable
private fun RestFloorClock(
    clock: String,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = if (running) "Rest $clock remaining" else "Next rest $clock"
    Text(
        clock,
        style = InstrumentType.numeralHero,
        color = TextPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .testTag(RestFloorTags.CLOCK)
            .clearAndSetSemantics {
                contentDescription = description
            },
    )
}

@Composable
private fun RestFloorIdleControls(
    totalSeconds: Int,
    onPreset: (Int) -> Unit,
    onCustom: (String) -> Boolean,
    onStart: () -> Unit,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        RestPresetChips(
            selectedSeconds = totalSeconds,
            onSelect = onPreset,
            onCustom = { showCustom = true },
        )
        RestControl(
            label = "Start rest",
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RestFloorTags.START),
        )
    }
    if (showCustom) {
        CustomRestDialog(
            title = "Custom rest",
            confirmLabel = "Set",
            onConfirm = { input ->
                val ok = onCustom(input)
                if (ok) showCustom = false
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}
