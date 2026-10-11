package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/** A separate, illustrated session with its action beside the name and a rule between sessions. */
@Composable
fun DayBlock(
    title: String,
    lines: DayBlockCopy.Lines,
    exercises: List<Exercise>,
    action: String?,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val surface = if (onOpen != null) {
        modifier.clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
    } else modifier
    Column(modifier = surface.fillMaxWidth().padding(vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        DayBlockHead(
            title = title, lines = lines, exercises = exercises,
            trailing = action?.let { label ->
                {
                    Text(text = label, modifier = Modifier.heightIn(min = Metrics.touchMin).padding(top = Metrics.space3),
                        style = InstrumentType.bodyStrong, color = if (enabled) Volt else TextDisabled)
                }
            },
        )
        lines.status?.let { status ->
            Text(text = status, style = InstrumentType.caption, color = TextSecondary)
        }
        controls?.invoke(this)
        HairlineDivider(startIndent = 0.dp)
    }
}

/**
 * The head of a block — title, still-and-name rows, meta — emitted into
 * the session column. Shared with the empty-agenda fallback
 * so both today-surfaces draw a session the same way.
 *
 * Done and moved blocks go quiet in ink. The stills stay as they are: a
 * still is the lift's identity, not the day's state (ADR-022).
 */
@Composable
fun DayBlockHead(
    title: String,
    lines: DayBlockCopy.Lines,
    exercises: List<Exercise>,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = if (lines.settled) TextSecondary else TextPrimary
    val meta = if (lines.settled) TextTertiary else TextSecondary
    if (LocalDensity.current.fontScale >= 1.6f && trailing != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(text = title, style = InstrumentType.title, color = ink)
            trailing()
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            Text(text = title, modifier = Modifier.weight(1f), style = InstrumentType.title, color = ink)
            trailing?.invoke()
        }
    }
    if (lines.names.isNotEmpty()) {
        SessionLiftRows(
            labels = lines.names,
            exercises = exercises,
            nameColor = ink,
            extraColor = meta,
        )
    }
    lines.meta?.let { line ->
        Text(
            text = line,
            style = InstrumentType.caption,
            color = meta,
        )
    }
}

/**
 * One pictured lift per row: still on the left, number and name to the
 * right. Stills are one width, digits are tabular, so 1 / 2 / 3 / 4 read
 * as a list. A 4-up still strip duplicated those names. Remainder `+N`
 * is its own last line.
 */
@Composable
private fun SessionLiftRows(
    labels: List<String>,
    exercises: List<Exercise>,
    nameColor: Color,
    extraColor: Color,
) {
    val extra = labels.lastOrNull()?.takeIf { DayBlockCopy.isExtra(it) }
    val rows = if (extra != null) labels.dropLast(1) else labels
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        rows.forEachIndexed { index, label ->
            SessionLiftRow(
                exercise = exercises.getOrNull(index),
                label = label,
                color = nameColor,
            )
        }
        extra?.let { line ->
            Text(
                text = line,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ThumbSize.row + Metrics.space2),
                style = InstrumentType.body,
                color = extraColor,
            )
        }
    }
}

@Composable
private fun SessionLiftRow(
    exercise: Exercise?,
    label: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (exercise != null) {
            ExerciseThumb(exercise = exercise)
        } else {
            Spacer(Modifier.size(ThumbSize.row))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = InstrumentType.body,
            color = color,
        )
    }
}
