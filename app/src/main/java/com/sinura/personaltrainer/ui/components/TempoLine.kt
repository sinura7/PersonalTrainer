package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags

@Composable
fun TempoLine(
    message: String,
    applied: Boolean,
    onWhy: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val maxLines = if (fontScale >= 1.3f) 3 else 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm))
            .background(Surface2)
            .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
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
                color = com.sinura.personaltrainer.ui.theme.TextPrimary,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
            )
            if (applied) {
                Text(
                    text = stringResource(R.string.tempo_applied),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
        QuietButton(
            text = if (applied) stringResource(R.string.tempo_undo) else stringResource(R.string.tempo_why),
            onClick = if (applied) onUndo else onWhy,
            enabled = enabled,
            modifier = Modifier.testTag(if (applied) "workout-tempo-undo" else "workout-micro-rec-why"),
        )
    }
}
