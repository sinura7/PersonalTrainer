package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.RestNudgeButtons
import com.sinura.personaltrainer.ui.components.RestRing
import com.sinura.personaltrainer.ui.components.RingSize
import com.sinura.personaltrainer.ui.components.UpNextCard
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn

@Composable
internal fun WorkoutRestPanel(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    completed: Boolean,
    upNextKicker: String,
    upNextTarget: String,
    upNextLoading: Boolean,
    loggedLine: String?,
    showFirstRestCaption: Boolean,
    onNudge: (Int) -> Unit,
    onSkip: () -> Unit,
    onEditUpNext: () -> Unit,
    onUndoLastSet: () -> Unit,
    onOpenRest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val urgent = running && remainingSeconds in 1..3
    LaunchedEffect(urgent) {
        if (urgent) Haptics.warn(view)
    }
    LaunchedEffect(completed) {
        if (completed) Haptics.holdDone(view)
    }
    val accent = when {
        completed -> Volt
        urgent -> Warn
        else -> RestCyan
    }
    val clock = RestTimer.formatClock(if (completed) 0 else remainingSeconds.coerceAtLeast(0))
    val totalClock = RestTimer.formatClock(totalSeconds.coerceAtLeast(0))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.REST_FLOOR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        RestRing(
            remainingMs = remainingSeconds.coerceAtLeast(0) * 1000L,
            totalMs = totalSeconds.coerceAtLeast(1) * 1000L,
            modifier = Modifier
                .testTag(WorkoutTestTags.REST_FLOOR_RING)
                .clickable(enabled = running, role = Role.Button, onClick = onOpenRest),
            size = RingSize.Large,
            accent = accent,
            running = running,
            completed = completed,
            totalLabel = stringResource(R.string.rest_of_total, totalClock),
        )
        if (showFirstRestCaption && running) {
            Text(
                stringResource(R.string.rest_first_caption),
                style = InstrumentType.caption,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Metrics.gutter),
            )
        }
        if (running) {
            RestNudgeButtons(
                onNudge = onNudge,
                onSkip = onSkip,
                minusTag = WorkoutTestTags.REST_MINUS,
                plusTag = WorkoutTestTags.REST_PLUS,
                skipTag = WorkoutTestTags.REST_SKIP,
                modifier = Modifier.padding(horizontal = Metrics.gutter),
            )
        }
        UpNextCard(
            kicker = upNextKicker,
            target = upNextTarget,
            onEdit = onEditUpNext,
            loading = upNextLoading,
            modifier = Modifier.padding(horizontal = Metrics.gutter),
        )
        loggedLine?.let { line ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                Text(
                    line,
                    style = InstrumentType.body,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                GhostButton(
                    label = stringResource(R.string.rest_undo_last),
                    onClick = onUndoLastSet,
                    modifier = Modifier.testTag(WorkoutTestTags.REST_UNDO_LAST),
                )
            }
        }
    }
}
