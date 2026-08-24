package com.sinura.personaltrainer.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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

private val BAR_HEIGHT = 56.dp
private val RAIL_WIDTH = 3.dp
private val RAIL_HEIGHT = 24.dp

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
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pit)
            .then(if (applyNavInsets) Modifier.navigationBarsPadding() else Modifier),
    ) {
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .testTag(LiveSessionBarTestTags.ROOT)
                .clickable(onClickLabel = "Back to the workout", onClick = onResume)
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
            Column(modifier = Modifier.weight(1f)) {
                if (state.stale) {
                    Kicker("Left open · ${state.staleHours}h", color = Warn)
                } else {
                    Kicker("In progress", color = Volt)
                }
                Text(
                    state.title,
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
            Text(
                state.elapsedLabel,
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            if (state.restRunning) {
                Text(
                    RestTimer.formatClock(state.restRemainingSeconds),
                    style = InstrumentType.numeralSm,
                    color = RestCyan,
                )
            }
            MetricCluster(value = state.workingSets.toString(), label = "sets")
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Workout actions",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (state.canFinish) {
                        DropdownMenuItem(
                            text = { Text("Finish workout", style = InstrumentType.body) },
                            onClick = {
                                menuOpen = false
                                onFinish()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Discard workout…", style = InstrumentType.body) },
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
            title = "Discard this workout?",
            body = if (state.totalSets == 0) {
                "This deletes the session. This cannot be undone."
            } else {
                "This deletes the session and its ${state.totalSets} logged sets. " +
                    "This cannot be undone."
            },
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
