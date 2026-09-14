package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * One session on Home's day board.
 *
 * ADR-021 §1 made the row the control: tap a planned session to start it,
 * with a trailing Start in Volt ink rather than a second filled button. The
 * row was an [com.sinura.personaltrainer.ui.components.InstrumentRow] — a
 * title, a one-line order and that trailing word — which is how a settings
 * list looks, not a session. This is the same control drawn as what it is:
 * a bordered block with each lift as a still beside its number and name,
 * the count and the estimate, and Start (or Do it today) on the foot where
 * the row's trailing word was.
 *
 * Separate blocks on purpose. A day's sessions are independent — cardio,
 * then the main session, then a pack, each finished on its own
 * ([com.sinura.personaltrainer.domain.SessionOrderCopy.AGENDA_SEPARATE]) —
 * so the grouped-list rule for "ten instances of one thing" does not
 * apply: two sessions are two things.
 *
 * Same test tag and the same tap as the row, so the instrumented pass and
 * the confirm behind it are untouched. [action] and [onOpen] travel
 * together: the block is tappable exactly when it names a Start, and then
 * it reads as a button, as the row did.
 *
 * [controls] are the block's own — Skip, Up / Down — drawn inside its
 * border under the foot, so a control between two blocks never has to be
 * guessed to belong to the one above it.
 */
@Composable
fun DayBlock(
    title: String,
    lines: DayBlockCopy.Lines,
    exercises: List<Exercise>,
    action: String?,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val status = lines.status
    val surface = if (onOpen != null) modifier.semantics { role = Role.Button } else modifier
    GymCard(modifier = surface, onClick = onOpen) {
        DayBlockHead(title = title, lines = lines, exercises = exercises)
        if (status != null || action != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                if (status != null) {
                    Text(
                        status,
                        modifier = Modifier.weight(1f),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (action != null) {
                    Text(
                        action,
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        controls?.invoke(this)
    }
}

/**
 * The head of a block — title, still-and-name rows, meta — emitted into
 * the enclosing card's column. Shared with the empty-agenda leftover card
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
) {
    val ink = if (lines.settled) TextSecondary else TextPrimary
    val meta = if (lines.settled) TextTertiary else TextSecondary
    Text(
        title,
        style = InstrumentType.title,
        color = ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (lines.names.isNotEmpty()) {
        SessionLiftRows(
            labels = lines.names,
            exercises = exercises,
            nameColor = ink,
            indexColor = meta,
        )
    }
    lines.meta?.let { line ->
        Text(
            line,
            style = InstrumentType.caption,
            color = meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One pictured lift per row: still on the left, number then name to the
 * right, names sharing a column so 1 / 2 / 3 / 4 read as a list. A 4-up
 * still strip duplicated those names. Remainder `+N` is its own last line.
 */
@Composable
private fun SessionLiftRows(
    labels: List<String>,
    exercises: List<Exercise>,
    nameColor: Color,
    indexColor: Color,
) {
    val extra = labels.lastOrNull()?.takeIf { DayBlockCopy.isExtra(it) }
    val rows = if (extra != null) labels.dropLast(1) else labels
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        rows.forEachIndexed { index, label ->
            val number = index + 1
            val name = label.substringAfter(' ', missingDelimiterValue = label)
            SessionLiftRow(
                exercise = exercises.getOrNull(index),
                number = number,
                name = name,
                spoken = label,
                nameColor = nameColor,
                indexColor = indexColor,
            )
        }
        extra?.let { line ->
            Text(
                line,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ThumbSize.row + Metrics.space2)
                    .clearAndSetSemantics { text = AnnotatedString(line) },
                style = InstrumentType.body,
                color = indexColor,
            )
        }
    }
}

@Composable
private fun SessionLiftRow(
    exercise: Exercise?,
    number: Int,
    name: String,
    spoken: String,
    nameColor: Color,
    indexColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { text = AnnotatedString(spoken) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (exercise != null) {
            ExerciseThumb(exercise = exercise)
        } else {
            Spacer(Modifier.size(ThumbSize.row))
        }
        Text(
            number.toString(),
            modifier = Modifier.width(INDEX_WIDTH),
            style = InstrumentType.caption,
            color = indexColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = InstrumentType.body,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fixed so 1 and 8 leave the names on one vertical edge. */
private val INDEX_WIDTH = Metrics.space4
