package com.sinura.personaltrainer.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.Motion

private val RAIL_WIDTH = 3.dp
private val RAIL_HEIGHT = 24.dp

/** Long enough to read a failure line; the bar is small and the error must not pin forever. */
private const val ACTION_ERROR_DWELL_MS = 6_000L

object LiveSessionBarTestTags {
    const val ROOT = "live-session-bar"
}

/**
 * The one live-session surface in the app.
 *
 * Docked above the tab bar whenever a session is in progress, on every screen except the
 * workout itself and its summary. Stays visible behind [com.sinura.personaltrainer.ui.workout
 * .StartOptionsSheet] — that sheet offers “Go to session”, not a second Resume. While the
 * bar is visible no other surface may offer to resume, finish or discard — the app used to
 * answer "where is my workout" in three different places, each with its own quirks, and
 * none of them present once the user had navigated away.
 *
 * @param applyNavInsets true on routes where the tab bar is hidden, so the bar owns the
 *   gesture-navigation inset instead of sitting under it.
 */
@Composable
fun LiveSessionBar(
    state: LiveSessionBarUiState,
    applyNavInsets: Boolean,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    actionError: String? = null,
    onActionErrorShown: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // The error must not be sticky: without this it stayed on the bar until
    // some later action happened to succeed — the pinned-banner pattern the
    // Home screen already had fixed.
    if (actionError != null) {
        LaunchedEffect(actionError) {
            kotlinx.coroutines.delay(ACTION_ERROR_DWELL_MS)
            onActionErrorShown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pit)
            .then(if (applyNavInsets) Modifier.navigationBarsPadding() else Modifier),
    ) {
        HairlineDivider(startIndent = 0.dp)
        // A confirmed finish/discard that failed must say so here — the dialog is
        // gone and the bar staying put is otherwise indistinguishable from a lag.
        if (actionError != null) {
            Text(
                actionError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter, vertical = Metrics.space1),
                style = InstrumentType.caption,
                color = Warn,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.rowMin)
                .padding(horizontal = Metrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            // The accent's whole meaning is live/act/now; this is the one place on a tab
            // screen that is literally live.
            Box(
                modifier = Modifier
                    .width(RAIL_WIDTH)
                    .height(RAIL_HEIGHT)
                    .clip(RoundedCornerShape(RAIL_WIDTH))
                    .background(if (state.stale) Warn else Volt),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = LiveBarCopy.resumeLabel(state.kind), onClick = onResume)
                    .testTag(LiveSessionBarTestTags.ROOT),
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.stale) {
                    Kicker("Left open · ${state.staleHours}h", color = Warn)
                } else {
                    Kicker(LiveBarCopy.IN_PROGRESS, color = Volt)
                }
                Text(
                    state.title,
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                state.elapsedLabel,
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.restRunning) {
                Text(
                    RestTimer.formatClock(state.restRemainingSeconds),
                    style = InstrumentType.numeralSm,
                    color = RestCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (LiveBarCopy.showsSets(state.kind)) {
                MetricCluster(value = state.workingSets.toString(), label = LiveBarCopy.SETS)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = LiveBarCopy.actionsDescription(state.kind),
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (state.canFinish) {
                        DropdownMenuItem(
                            text = { Text(LiveBarCopy.finish(state.kind), style = InstrumentType.body) },
                            onClick = {
                                menuOpen = false
                                onFinish()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(LiveBarCopy.discard(state.kind), style = InstrumentType.body) },
                        onClick = {
                            menuOpen = false
                            confirmDiscard = true
                        },
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = LiveBarCopy.discardTitle(state.kind),
            body = LiveBarCopy.discardBody(state.kind, state.totalSets),
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onDiscard()
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }
}

/**
 * Collects the ticking live-bar state in its own composition. The nav
 * root only reads [LiveSessionBarViewModel.hasLiveSession], so a 1 Hz
 * elapsed label cannot rebuild the NavHost.
 */
@Composable
fun LiveSessionBarHost(
    viewModel: LiveSessionBarViewModel,
    visible: Boolean,
    showBottomBar: Boolean,
    barMs: Int,
    onResume: (LiveSessionBarUiState) -> Unit,
) {
    val liveSession by viewModel.uiState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(barMs, easing = Motion.Standard),
        ) { it },
        exit = slideOutVertically(
            animationSpec = tween(barMs, easing = Motion.Exit),
        ) { it },
    ) {
        liveSession?.let { live ->
            LiveSessionBar(
                state = live,
                applyNavInsets = !showBottomBar,
                onResume = { onResume(live) },
                onFinish = viewModel::finishFromBar,
                onDiscard = viewModel::discardFromBar,
                actionError = actionError,
                onActionErrorShown = viewModel::onActionErrorShown,
            )
        }
    }
}
