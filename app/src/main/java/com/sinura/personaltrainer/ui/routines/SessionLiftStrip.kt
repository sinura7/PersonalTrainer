package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

data class SessionLiftItem(
    val id: String,
    val exercise: Exercise,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val targetWeightKg: Double? = null,
)

object SessionLiftTags {
    const val STRIP = "session-lift-strip"
    const val EDITOR = "session-lift-editor"
    fun card(id: String) = "session-lift-card-$id"
}

object SessionLiftCopy {
    const val MOVE_EARLIER = "Earlier"
    const val MOVE_LATER = "Later"
}

/**
 * The session as a numbered strip of cards, not a stack of full-width rows.
 *
 * Order is the session: 1, 2, 3 left to right. Tap a card and the sets, reps,
 * rest and load open under the strip — the same fields [CompactLiftRow] uses,
 * so the 360 dp identity test on that row still has a home.
 */
@Composable
fun SessionLiftStrip(
    lifts: List<SessionLiftItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onMoveEarlier: (String) -> Unit,
    onMoveLater: (String) -> Unit,
    onRemove: (String) -> Unit,
    onStageTargets: (String, Int?, Int?, Int?, Double?) -> Unit,
    onCommitTargets: (String) -> Unit,
    modifier: Modifier = Modifier,
    canSwap: (String) -> Boolean = { false },
    onSwap: (String) -> Unit = {},
) {
    val selected = lifts.firstOrNull { it.id == selectedId }
    val selectedIndex = lifts.indexOfFirst { it.id == selectedId }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SessionLiftTags.STRIP),
            horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap),
            contentPadding = PaddingValues(vertical = Metrics.space1),
        ) {
            itemsIndexed(lifts, key = { _, item -> item.id }) { index, item ->
                SessionLiftCard(
                    item = item,
                    number = index + 1,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (selected != null) {
            val shape = RoundedCornerShape(Radius.sm)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Surface2)
                    .border(Metrics.emphasisBorder, Volt, shape)
                    .testTag(SessionLiftTags.EDITOR),
            ) {
                Text(
                    selected.exercise.name,
                    modifier = Modifier.padding(
                        start = Metrics.space3,
                        end = Metrics.space3,
                        top = Metrics.space3,
                    ),
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CompactTargetFields(
                    rowKey = selected.id,
                    sets = selected.sets,
                    reps = selected.reps,
                    restSeconds = selected.restSeconds,
                    targetWeightKg = selected.targetWeightKg,
                    onStageTargets = { sets, reps, rest, kg ->
                        onStageTargets(selected.id, sets, reps, rest, kg)
                    },
                    onCommitTargets = { onCommitTargets(selected.id) },
                    onRemove = { onRemove(selected.id) },
                    onSwap = if (canSwap(selected.id)) {
                        { onSwap(selected.id) }
                    } else {
                        null
                    },
                )
                Row(
                    modifier = Modifier.padding(
                        start = Metrics.space2,
                        end = Metrics.space2,
                        bottom = Metrics.space2,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    TextButton(
                        onClick = { onMoveEarlier(selected.id) },
                        enabled = selectedIndex > 0,
                    ) {
                        Text(
                            SessionLiftCopy.MOVE_EARLIER,
                            style = InstrumentType.bodyStrong,
                            color = if (selectedIndex > 0) TextSecondary else TextTertiary,
                        )
                    }
                    TextButton(
                        onClick = { onMoveLater(selected.id) },
                        enabled = selectedIndex in 0 until lifts.lastIndex,
                    ) {
                        Text(
                            SessionLiftCopy.MOVE_LATER,
                            style = InstrumentType.bodyStrong,
                            color = if (selectedIndex in 0 until lifts.lastIndex) {
                                TextSecondary
                            } else {
                                TextTertiary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLiftCard(
    item: SessionLiftItem,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.sm)
    val label = "$number. ${item.exercise.name}"
        Column(
            modifier = modifier
                .width(CARD_WIDTH)
                .heightIn(min = Metrics.rowMin)
                .clip(shape)
                .background(if (selected) VoltDim else Surface2)
                .border(
                    if (selected) Metrics.emphasisBorder else Metrics.hairline,
                    if (selected) Volt else Hairline,
                    shape,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                }
                .clickable(onClick = onClick)
                .testTag(SessionLiftTags.card(item.id))
                .padding(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CartBadge(number = number, selected = selected)
            ExerciseThumb(
                exercise = item.exercise,
                size = ThumbSize.header,
            )
            Text(
                item.exercise.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${item.sets} × ${item.reps}",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
        }
}

@Composable
internal fun CartBadge(
    number: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Metrics.space5)
            .clip(CircleShape)
            .background(if (selected) Volt else Surface1)
            .border(Metrics.hairline, if (selected) Volt else Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = if (selected) Pit else TextSecondary,
        )
    }
}

private val CARD_WIDTH = 140.dp
