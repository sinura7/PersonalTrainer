package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags

@Composable
fun TempoLine(
    message: String,
    applied: Boolean,
    onWhy: () -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val narrow = LocalWindowInfo.current.containerDpSize.width <= 360.dp
    val stackActions = fontScale >= 2f && narrow
    val maxLines = if (fontScale >= 1.3f) 3 else 2
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Box(
                Modifier
                    .size(Metrics.space6)
                    .clip(Radius.full)
                    .background(VoltContainer),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text(
                    stringResource(R.string.tempo_label),
                    style = InstrumentType.kicker,
                    color = Volt,
                    modifier = Modifier.testTag(WorkoutTestTags.TEMPO_SUGGESTION_LABEL),
                )
                Text(
                    text = message,
                    style = InstrumentType.body,
                    color = TextPrimary,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (applied) {
                    Text(
                        text = stringResource(R.string.tempo_applied),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            maxItemsInEachRow = if (stackActions) 1 else 3,
        ) {
            if (applied) {
                QuietButton(
                    text = stringResource(R.string.tempo_undo),
                    onClick = onUndo,
                    enabled = enabled,
                    modifier = Modifier.testTag("workout-tempo-undo"),
                )
            } else {
                QuietButton(
                    text = stringResource(R.string.tempo_apply),
                    onClick = onApply,
                    enabled = enabled,
                    modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_APPLY),
                )
                QuietButton(
                    text = stringResource(R.string.tempo_why),
                    onClick = onWhy,
                    enabled = enabled,
                    modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_WHY),
                )
            }
            onDismiss?.let {
                QuietButton(
                    text = stringResource(R.string.tempo_dismiss),
                    onClick = it,
                    enabled = enabled,
                    modifier = Modifier.testTag(WorkoutTestTags.TEMPO_COACH_DISMISS),
                )
            }
        }
    }
}
