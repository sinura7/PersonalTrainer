package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GymCard
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
 * a bordered block with the session's first four stills, the order under
 * them, the count and the estimate, and Start (or Do it today) on the foot
 * where the row's trailing word was.
 *
 * Separate blocks on purpose. A day's sessions are independent — cardio,
 * then the main session, then a pack, each finished on its own
 * ([com.sinura.personaltrainer.domain.SessionOrderCopy.AGENDA_SEPARATE]) —
 * so the grouped-list rule for "ten instances of one thing" does not
 * apply: two sessions are two things.
 *
 * Same test tag and the same tap as the row, so the instrumented pass and
 * the confirm behind it are untouched. [action] and [onOpen] travel
 * together: the block is tappable exactly when it names a Start.
 */
@Composable
fun DayBlock(
    title: String,
    lines: DayBlockCopy.Lines,
    exercises: List<Exercise>,
    action: String?,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val status = lines.status
    GymCard(modifier = modifier, onClick = onOpen) {
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
    }
}

/**
 * The head of a block — title, stills, order, meta — emitted into the
 * enclosing card's column. Shared with the empty-agenda leftover card so
 * both today-surfaces draw a session the same way.
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
    if (exercises.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            exercises.take(DayBlockCopy.STILL_LIMIT).forEach { exercise ->
                ExerciseThumb(exercise = exercise)
            }
        }
    }
    lines.names?.let { names ->
        Text(
            names,
            style = InstrumentType.body,
            color = meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
