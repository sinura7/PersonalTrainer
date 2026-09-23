package com.sinura.personaltrainer.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.RestHonestyRow
import com.sinura.personaltrainer.ui.components.RestNudgeButtons
import com.sinura.personaltrainer.ui.components.RestPresetChips
import com.sinura.personaltrainer.ui.components.RestSweepRing
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn

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
    const val EXACT = "rest-floor-exact"
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
    val context = LocalContext.current
    val notificationsEnabled = rememberRestNotificationsEnabled()
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
            SessionLoadState.FAILED -> {
                EmptyState(
                    scene = EmptyScene.GONE,
                    title = "Workout unavailable",
                    body = "The workout could not be read. The timer has not been stopped. Retry, or close this screen.",
                    actionLabel = "Retry",
                    onAction = viewModel::retrySession,
                    compact = true,
                    modifier = Modifier.padding(padding).padding(Metrics.gutter),
                )
            }
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
                    notificationsEnabled = notificationsEnabled,
                    onOpenNotifications = { openRestNotificationSettings(context) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

/** Pure presentation: native fixtures can pin timer state without real clock jobs. */
@Composable
internal fun RestFloorBody(
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
    notificationsEnabled: Boolean = true,
    onOpenNotifications: () -> Unit = {},
) {
    // Completion is a timer state, not an expiring animation. Closing and reopening
    // this page retains the completed rest until a new timer starts.
    val completed = rest.completedTimerId != null && !rest.running
    val safeRemaining = rest.remainingSeconds.coerceAtLeast(0)
    val urgent = rest.running && safeRemaining <= URGENT_SECONDS
    val accent = when {
        completed -> PrGold
        urgent -> Warn
        rest.running -> RestCyan
        else -> TextSecondary
    }
    val clock = RestTimer.formatClock(if (completed) 0 else if (rest.running) safeRemaining else rest.totalSeconds)
    val label = when {
        completed -> "Rest complete"
        rest.running -> "Rest"
        else -> RestIdleCopy.PLANNED_REST
    }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.6f
    val honesty = RestHonestyCopy.pick(
        persistenceHealthy = rest.persistenceHealthy, restRunning = rest.running,
        notificationsEnabled = notificationsEnabled, batteryHint = rest.batteryHint,
        exactBestEffort = rest.exactAlarmBestEffort, onRestPage = true,
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val showRing = !largeText && maxHeight >= 560.dp
        val ringSize = (maxHeight * 0.48f).coerceIn(180.dp, 320.dp)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Metrics.gutter)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = Metrics.space4),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showRing) {
                        RestSweepRing(
                            remainingSeconds = if (rest.running) safeRemaining else 0,
                            totalSeconds = rest.totalSeconds, accent = accent, clock = clock,
                            kicker = label, running = rest.running || completed, finished = completed,
                            afterWarmup = floor.afterWarmup, ringSize = ringSize,
                            modifier = Modifier.testTag(RestFloorTags.RING), clockTestTag = RestFloorTags.CLOCK,
                        )
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = InstrumentType.bodyStrong, color = accent)
                        Text(clock, modifier = Modifier.testTag(RestFloorTags.CLOCK),
                            style = if (largeText) InstrumentType.numeralLg else InstrumentType.numeralHero,
                            color = TextPrimary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    floor.exerciseName?.let { Text(it, style = InstrumentType.title, color = TextPrimary) }
                    floor.lastSetLine?.let { Text(it, style = InstrumentType.body, color = TextSecondary) }
                    floor.sessionTargetLine?.let {
                        Text(it, modifier = Modifier.testTag(RestFloorTags.NEXT), style = InstrumentType.body, color = TextSecondary)
                    }
                    val plannedClock = RestTimer.formatClock(rest.totalSeconds)
                    Text(
                        RestIdleCopy.planned(plannedClock),
                        // Read without the dot.
                        modifier = Modifier.semantics { contentDescription = RestIdleCopy.plannedSpoken(plannedClock) },
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                }
                if (!rest.running && !completed) RestPresetChips(
                    selectedSeconds = rest.totalSeconds, onSelect = onSelectPreset,
                    onCustom = { showCustom = true },
                )
                honesty?.let { row ->
                    RestHonestyRow(
                        honesty = row, onDismissBatteryHint = onAcknowledgeBattery,
                        onOpenNotifications = onOpenNotifications,
                    )
                }
            }
            // The actions stay within reach; the context above owns any required scrolling.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Metrics.space4)) {
                when {
                    // The dock card's three, in its order and words.
                    rest.running -> RestNudgeButtons(
                        onNudge = onAdjust, onSkip = onSkip,
                        minusTag = RestFloorTags.MINUS, plusTag = RestFloorTags.PLUS, skipTag = RestFloorTags.SKIP,
                        perRow = if (largeText) 2 else 3,
                    )
                    completed -> PrimaryGymButton(
                        text = "Return to workout", onClick = onBackToBar,
                        modifier = Modifier.testTag(RestFloorTags.BACK_TO_BAR),
                    )
                    else -> PrimaryGymButton(
                        text = "Start rest", onClick = onStart,
                        modifier = Modifier.testTag(RestFloorTags.START),
                    )
                }
            }
        }
    }
    if (showCustom) CustomRestDialog(
        title = "Custom rest", confirmLabel = "Set",
        onConfirm = { input ->
            val ok = onCustom(input)
            if (ok) showCustom = false
            ok
        },
        onDismiss = { showCustom = false },
    )
}
