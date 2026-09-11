package com.sinura.personaltrainer.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.RestFinishFlash
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestBatteryHintRow
import com.sinura.personaltrainer.ui.components.RestControl
import com.sinura.personaltrainer.ui.components.RestPresetChips
import com.sinura.personaltrainer.ui.components.RestSweepRing
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
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
    const val RING = "rest-floor-ring"
    const val SKIP = "rest-floor-skip"
    const val MINUS = "rest-floor-minus"
    const val PLUS = "rest-floor-plus"
    const val START = "rest-floor-start"
    const val CLOSE = "rest-floor-close"
    const val BACK_TO_BAR = "rest-floor-back"
    const val NEXT = "rest-floor-next"
    const val UNSAVED = "rest-floor-unsaved"
    const val BATTERY = "rest-floor-battery"
}

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
            ScreenHeader(
                title = "",
                onBack = onClose,
                backTag = RestFloorTags.CLOSE,
                backIcon = Icons.Outlined.Close,
                backDescription = "Close rest",
            )
        },
    ) { padding ->
        when (state.loadState) {
            SessionLoadState.LOADING -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            SessionLoadState.MISSING -> {
                EmptyState(
                    scene = EmptyScene.GONE,
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
                    onAcknowledgeBattery = viewModel::acknowledgeRestBatteryHint,
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
    onAcknowledgeBattery: () -> Unit,
    onBackToBar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var justFinished by remember { mutableStateOf(false) }
    var flashedTimerId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rest.completedTimerId) {
        if (RestFinishFlash.shouldFlash(rest.completedTimerId, flashedTimerId)) {
            flashedTimerId = rest.completedTimerId
            justFinished = true
        }
    }
    LaunchedEffect(rest.running) {
        if (rest.running) justFinished = false
    }
    LaunchedEffect(justFinished) {
        if (justFinished) {
            delay(Motion.FINISHED_DWELL_MS)
            justFinished = false
        }
    }

    val safeRemaining = rest.remainingSeconds.coerceAtLeast(0)
    val urgent = rest.running && safeRemaining <= URGENT_SECONDS
    val accent = when {
        justFinished -> PrGold
        urgent -> Warn
        rest.running -> RestCyan
        else -> TextSecondary
    }
    val displaySeconds = if (justFinished) 0 else if (rest.running) safeRemaining else rest.totalSeconds
    val clock = RestTimer.formatClock(displaySeconds)
    val kicker = when {
        justFinished -> "Back to the bar"
        rest.running -> "REST"
        else -> RestIdleCopy.KICKER
    }
    val idleRingSeconds = if (justFinished) 0 else if (rest.running) safeRemaining else 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space4),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            RestSweepRing(
                remainingSeconds = idleRingSeconds,
                totalSeconds = rest.totalSeconds,
                accent = accent,
                clock = clock,
                kicker = kicker,
                running = rest.running || justFinished,
                finished = justFinished,
                afterWarmup = floor.afterWarmup,
                ringSize = LandscapeChrome.ringSizeDp(
                    LocalWindowInfo.current.containerDpSize.height.value.roundToInt(),
                ).dp,
                modifier = Modifier.testTag(RestFloorTags.RING),
                clockTestTag = RestFloorTags.CLOCK,
            )
        }
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
        if (!rest.running && !justFinished && floor.afterWarmup) {
            Text(
                RestIdleCopy.afterWarmupHint(),
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 2,
            )
        }
        // Same honesty as the Settings best-effort notice: the row did not
        // reach disk, so the wakeup is not armed and a process kill ends this
        // rest in silence. One line, not a banner — the countdown still runs.
        if (rest.running && rest.batteryHint) {
            RestBatteryHintRow(
                onDismiss = onAcknowledgeBattery,
                testTag = RestFloorTags.BATTERY,
            )
        }
        if (rest.running && !rest.persistenceHealthy) {
            Text(
                "Rest may not survive leaving the app.",
                modifier = Modifier.testTag(RestFloorTags.UNSAVED),
                style = InstrumentType.caption,
                color = TextSecondary,
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
        PrimaryGymButton(
            text = "Start rest",
            onClick = onStart,
            modifier = Modifier.testTag(RestFloorTags.START),
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
