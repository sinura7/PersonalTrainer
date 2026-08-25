package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

object CompactLiftCopy {
    const val TARGET_WEIGHT = "Target weight"
}

object CompactLiftTags {
    const val ROW = "compact-lift-row"
    const val NAME = "compact-lift-name"
    const val TARGET_WEIGHT = "compact-lift-target-weight"
}

/**
 * One lift as a horizontal row you can stack, not a card that eats the screen.
 *
 * Identity owns the first line. Sets × reps and reorder sit on a second line
 * so a long name at 360 dp is still readable (FND-016). Expanded, the load
 * field is labeled Target weight with the unit as a suffix, not the label.
 */
@Composable
fun CompactLiftRow(
    exercise: Exercise,
    sets: Int,
    reps: Int,
    restSeconds: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSwap: (() -> Unit)?,
    onStageTargets: (Int?, Int?, Int?, Double?) -> Unit,
    onCommitTargets: () -> Unit,
    modifier: Modifier = Modifier,
    rowKey: String = exercise.id,
    targetWeightKg: Double? = null,
) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, Hairline, shape)
            .testTag(CompactLiftTags.ROW),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.rowMin)
                .clickable(onClick = onToggle)
                .padding(start = Metrics.space3, end = Metrics.space3, top = Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            ExerciseThumb(exercise = exercise)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    modifier = Modifier.testTag(CompactLiftTags.NAME),
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    exercise.muscleGroup,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Metrics.space3, end = Metrics.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                "$sets × $reps",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) TextSecondary else TextTertiary,
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) TextSecondary else TextTertiary,
                )
            }
        }
        if (expanded) {
            CompactTargetFields(
                rowKey = rowKey,
                sets = sets,
                reps = reps,
                restSeconds = restSeconds,
                targetWeightKg = targetWeightKg,
                onStageTargets = onStageTargets,
                onCommitTargets = onCommitTargets,
                onRemove = onRemove,
                onSwap = onSwap,
            )
        }
    }
}

@Composable
internal fun CompactTargetFields(
    rowKey: String,
    sets: Int,
    reps: Int,
    restSeconds: Int,
    targetWeightKg: Double?,
    onStageTargets: (Int?, Int?, Int?, Double?) -> Unit,
    onCommitTargets: () -> Unit,
    onRemove: () -> Unit,
    onSwap: (() -> Unit)?,
) {
    val unit = LocalWeightUnit.current
    var setsText by rememberSaveable(rowKey) { mutableStateOf(sets.toString()) }
    var repsText by rememberSaveable(rowKey) { mutableStateOf(reps.toString()) }
    var restText by rememberSaveable(rowKey) { mutableStateOf(restSeconds.toString()) }
    var weightText by rememberSaveable(rowKey) {
        mutableStateOf(
            targetWeightKg?.let { kg ->
                WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit))
            }.orEmpty(),
        )
    }
    val stage = {
        val kg = weightText.toDoubleOrNull()?.let { display ->
            WeightConverter.toKg(display, unit)
        }?.takeIf { it > 0.0 }
        onStageTargets(setsText.toIntOrNull(), repsText.toIntOrNull(), restText.toIntOrNull(), kg)
    }
    Column(
        modifier = Modifier.padding(start = Metrics.space3, end = Metrics.space3, bottom = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Text(
            RestTimer.formatClock(restSeconds),
            style = InstrumentType.caption,
            color = TextTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            MiniNumberField("Sets", setsText, Modifier.weight(1f), onCommitTargets) {
                setsText = it.filter(Char::isDigit)
                stage()
            }
            MiniNumberField("Reps", repsText, Modifier.weight(1f), onCommitTargets) {
                repsText = it.filter(Char::isDigit)
                stage()
            }
        }
        MiniNumberField("Rest s", restText, Modifier.fillMaxWidth(), onCommitTargets) {
            restText = it.filter(Char::isDigit)
            stage()
        }
        MiniNumberField(
            label = CompactLiftCopy.TARGET_WEIGHT,
            value = weightText,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CompactLiftTags.TARGET_WEIGHT),
            onFocusLost = onCommitTargets,
            allowDecimal = true,
            suffix = unit.suffix,
        ) {
            weightText = it.filter { ch -> ch.isDigit() || ch == '.' }
            stage()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            TextButton(onClick = onRemove) {
                Text("Remove", style = InstrumentType.bodyStrong, color = Danger)
            }
            if (onSwap != null) {
                TextButton(onClick = onSwap) {
                    Text("Swap", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MiniNumberField(
    label: String,
    value: String,
    modifier: Modifier,
    onFocusLost: () -> Unit,
    allowDecimal: Boolean = false,
    suffix: String? = null,
    onValueChange: (String) -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = InstrumentType.caption) },
        modifier = modifier.onFocusChanged { focus ->
            if (hadFocus && !focus.isFocused) onFocusLost()
            hadFocus = focus.isFocused
        },
        singleLine = true,
        textStyle = InstrumentType.numeralSm,
        suffix = suffix?.let { unit ->
            { Text(unit, style = InstrumentType.unit, color = TextSecondary) }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}
