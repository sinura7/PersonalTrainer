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
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
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
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

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
    const val HINT = "session-lift-hint"
    fun card(id: String) = "session-lift-card-$id"
}

object SessionLiftCopy {
    const val MOVE_EARLIER = "Earlier"
    const val MOVE_LATER = "Later"
}

/**
 * The session as a numbered strip of cards, not a stack of full-width rows.
 *
 * Every face on a card has a job: order, identity, work, rest, and load when
 * it exists. Tap opens the same target fields [CompactLiftRow] uses, so the
 * 360 dp identity test on that row still has a home.
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
        if (selected == null) {
            Text(
                SessionOrderCopy.TAP_TO_SET,
                modifier = Modifier.testTag(SessionLiftTags.HINT),
                style = InstrumentType.body,
                color = TextSecondary,
            )
        } else {
            SessionLiftEditor(
                item = selected,
                number = selectedIndex + 1,
                total = lifts.size,
                canMoveEarlier = selectedIndex > 0,
                canMoveLater = selectedIndex in 0 until lifts.lastIndex,
                canSwap = canSwap(selected.id),
                onMoveEarlier = { onMoveEarlier(selected.id) },
                onMoveLater = { onMoveLater(selected.id) },
                onRemove = { onRemove(selected.id) },
                onSwap = { onSwap(selected.id) },
                onStageTargets = { sets, reps, rest, kg ->
                    onStageTargets(selected.id, sets, reps, rest, kg)
                },
                onCommitTargets = { onCommitTargets(selected.id) },
            )
        }
    }
}

@Composable
private fun SessionLiftEditor(
    item: SessionLiftItem,
    number: Int,
    total: Int,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canSwap: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onStageTargets: (Int?, Int?, Int?, Double?) -> Unit,
    onCommitTargets: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface2)
            .border(Metrics.emphasisBorder, Volt, shape)
            .testTag(SessionLiftTags.EDITOR),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Metrics.space3,
                end = Metrics.space3,
                top = Metrics.space3,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Kicker(SessionOrderCopy.liftIndex(number, total))
            Text(
                item.exercise.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.exercise.muscleGroup,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CompactTargetFields(
            rowKey = item.id,
            sets = item.sets,
            reps = item.reps,
            restSeconds = item.restSeconds,
            targetWeightKg = item.targetWeightKg,
            onStageTargets = onStageTargets,
            onCommitTargets = onCommitTargets,
            onRemove = onRemove,
            onSwap = if (canSwap) onSwap else null,
        )
        Kicker(
            SessionOrderCopy.ORDER,
            modifier = Modifier.padding(horizontal = Metrics.space3),
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
                onClick = onMoveEarlier,
                enabled = canMoveEarlier,
            ) {
                Text(
                    SessionLiftCopy.MOVE_EARLIER,
                    style = InstrumentType.bodyStrong,
                    color = if (canMoveEarlier) TextSecondary else TextTertiary,
                )
            }
            TextButton(
                onClick = onMoveLater,
                enabled = canMoveLater,
            ) {
                Text(
                    SessionLiftCopy.MOVE_LATER,
                    style = InstrumentType.bodyStrong,
                    color = if (canMoveLater) TextSecondary else TextTertiary,
                )
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
    val unit = LocalWeightUnit.current
    val restClock = RestTimer.formatClock(item.restSeconds)
    val loadKg = item.targetWeightKg
    val loadDisplay = loadKg?.let { kg -> WeightConverter.formatLabel(kg, unit) }
    val spoken = SessionOrderCopy.cardSpoken(
        number = number,
        name = item.exercise.name,
        muscleGroup = item.exercise.muscleGroup,
        sets = item.sets,
        reps = item.reps,
        restClock = restClock,
        load = loadDisplay,
    )
    val shape = RoundedCornerShape(Radius.sm)
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
                contentDescription = spoken
            }
            .clickable(onClick = onClick)
            .testTag(SessionLiftTags.card(item.id))
            .padding(Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CartBadge(number = number, selected = selected)
            ExerciseThumb(
                exercise = item.exercise,
                size = ThumbSize.header,
            )
        }
        Text(
            item.exercise.name,
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.exercise.muscleGroup,
            style = InstrumentType.caption,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            MetricCluster(
                value = "${item.sets} × ${item.reps}",
                label = SessionOrderCopy.WORK,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = restClock,
                label = SessionOrderCopy.REST,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
        }
        if (loadKg != null) {
            MetricCluster(
                value = WeightConverter.formatDisplayNumber(
                    WeightConverter.toDisplayValue(loadKg, unit),
                ),
                label = SessionOrderCopy.LOAD,
                unit = unit.suffix,
                horizontalAlignment = Alignment.Start,
            )
        }
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
            .size(Metrics.space6)
            .clip(CircleShape)
            .background(if (selected) Volt else Surface1)
            .border(Metrics.hairline, if (selected) Volt else Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = if (selected) Pit else TextPrimary,
        )
    }
}

private val CARD_WIDTH = 156.dp
