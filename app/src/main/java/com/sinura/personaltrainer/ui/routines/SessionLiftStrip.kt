package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
 * The session as a vertical stack of full-width cards.
 *
 * Each lift occupies one horizontal row. The next sits under it. Tap expands
 * that card for sets, reps, rest and load — not a sideways strip you have to
 * hunt through.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val selectedIndex = lifts.indexOfFirst { it.id == selectedId }
    val firstRequester = remember { BringIntoViewRequester() }
    val lastRequester = remember { BringIntoViewRequester() }
    var seenCount by remember { mutableIntStateOf(0) }
    var seenFirstId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lifts.firstOrNull()?.id, lifts.size) {
        val firstId = lifts.firstOrNull()?.id
        when (nextStripScroll(seenFirstId, seenCount, firstId, lifts.size)) {
            StripScrollTarget.START -> firstRequester.bringIntoView()
            StripScrollTarget.LAST -> lastRequester.bringIntoView()
            null -> Unit
        }
        seenCount = lifts.size
        seenFirstId = firstId
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SessionLiftTags.STRIP),
        verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
    ) {
        lifts.forEachIndexed { index, item ->
            val requester = when (index) {
                0 -> firstRequester
                lifts.lastIndex -> lastRequester
                else -> null
            }
            SessionLiftCard(
                item = item,
                number = index + 1,
                total = lifts.size,
                selected = item.id == selectedId,
                onClick = { onSelect(item.id) },
                canMoveEarlier = index > 0,
                canMoveLater = index < lifts.lastIndex,
                canSwap = canSwap(item.id),
                onMoveEarlier = { onMoveEarlier(item.id) },
                onMoveLater = { onMoveLater(item.id) },
                onRemove = { onRemove(item.id) },
                onSwap = { onSwap(item.id) },
                onStageTargets = { sets, reps, rest, kg ->
                    onStageTargets(item.id, sets, reps, rest, kg)
                },
                onCommitTargets = { onCommitTargets(item.id) },
                modifier = Modifier.then(
                    if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier,
                ),
            )
        }
        if (lifts.isNotEmpty() && selectedIndex < 0) {
            Text(
                SessionOrderCopy.TAP_TO_SET,
                modifier = Modifier.testTag(SessionLiftTags.HINT),
                style = InstrumentType.body,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SessionLiftCard(
    item: SessionLiftItem,
    number: Int,
    total: Int,
    selected: Boolean,
    onClick: () -> Unit,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canSwap: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onStageTargets: (Int?, Int?, Int?, Double?) -> Unit,
    onCommitTargets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val restClock = RestTimer.formatClock(item.restSeconds)
    val loadKg = item.targetWeightKg?.takeIf { it > 0.0 }
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
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clip(shape)
            .background(if (selected) VoltDim else Surface2)
            .border(
                if (selected) Metrics.emphasisBorder else Metrics.hairline,
                if (selected) Volt else Hairline,
                shape,
            ),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(SessionLiftTags.card(item.id))
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    this.selected = selected
                }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.exercise.name,
                        style = InstrumentType.title,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.exercise.muscleGroup.isNotBlank()) {
                        Text(
                            item.exercise.muscleGroup,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
                if (loadKg != null) {
                    MetricCluster(
                        value = WeightConverter.formatDisplayNumber(
                            WeightConverter.toDisplayValue(loadKg, unit),
                        ),
                        label = SessionOrderCopy.LOAD,
                        unit = unit.suffix,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
            }
        }
        if (selected) {
            SessionLiftEditor(
                item = item,
                number = number,
                total = total,
                canMoveEarlier = canMoveEarlier,
                canMoveLater = canMoveLater,
                canSwap = canSwap,
                onMoveEarlier = onMoveEarlier,
                onMoveLater = onMoveLater,
                onRemove = onRemove,
                onSwap = onSwap,
                onStageTargets = onStageTargets,
                onCommitTargets = onCommitTargets,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SessionLiftTags.EDITOR),
    ) {
        Kicker(
            SessionOrderCopy.liftIndex(number, total),
            modifier = Modifier.padding(horizontal = Metrics.space3),
        )
        key("${item.id}:${item.exercise.id}") {
            CompactTargetFields(
                rowKey = "${item.id}:${item.exercise.id}",
                sets = item.sets,
                reps = item.reps,
                restSeconds = item.restSeconds,
                targetWeightKg = item.targetWeightKg,
                onStageTargets = onStageTargets,
                onCommitTargets = onCommitTargets,
                onRemove = onRemove,
                onSwap = if (canSwap) onSwap else null,
            )
        }
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
internal fun CartBadge(
    number: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeShape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .heightIn(min = Metrics.space6)
            .widthIn(min = Metrics.space6)
            .clip(badgeShape)
            .background(if (selected) Volt else Surface1)
            .border(Metrics.hairline, if (selected) Volt else Hairline, badgeShape)
            .padding(horizontal = Metrics.space1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = if (selected) Pit else TextPrimary,
        )
    }
}

internal enum class StripScrollTarget { START, LAST }

internal fun nextStripScroll(
    seenFirstId: String?,
    seenCount: Int,
    firstId: String?,
    size: Int,
): StripScrollTarget? = when {
    seenFirstId != null && firstId != seenFirstId && size > 0 -> StripScrollTarget.START
    size > seenCount && size > 0 -> StripScrollTarget.LAST
    else -> null
}
