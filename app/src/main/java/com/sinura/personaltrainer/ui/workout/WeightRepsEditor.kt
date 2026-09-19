package com.sinura.personaltrainer.ui.workout

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.FloorWeightPresets
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.UnloadedLoad
import com.sinura.personaltrainer.domain.WarmupSet
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutWeightCopy
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentPreset
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.components.StepperButton
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.instrumentTween

/**
 * The two numbers about to be logged, as the loudest things on the floor.
 *
 * Weight on the left, reps (or hold time) on the right, split by a hairline. The numeral
 * is the hero and the field: tap it to type, or nudge it with the round plates, which
 * repeat under a held thumb ([StepperButton]). The plates sit beside the numeral when
 * the column can hold a four-digit value with them, and drop beneath it when it cannot,
 * decided from a fixed sample rather than the live value so nothing jumps as digits
 * come and go. Large system text stacks the two columns.
 *
 * A bodyweight lift has no weight column at all; a hold shows its time where reps
 * would be. Increments come from [IncrementTable] through [FloorStepper], the same
 * rule every other weight field obeys.
 */
@Composable
internal fun WeightRepsEditor(
    enabled: Boolean,
    weightKg: Double,
    reps: Int,
    unit: WeightUnit,
    loadClass: LoadClass,
    loadType: LoadType,
    equipment: EquipmentType?,
    movementKey: String?,
    plated: Boolean,
    hold: Boolean,
    holdSeconds: Int?,
    holdRunning: Boolean,
    holdRemainingSeconds: Int,
    sourceLabel: String?,
    onWeightKgChange: (Double) -> Unit,
    plannedKg: Double? = null,
    lastKg: Double? = null,
    onRepsChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meaning = loadClass.weightMeaning
    val showWeight = meaning != WeightMeaning.NONE
    val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    var typingWeight by rememberSaveable { mutableStateOf(false) }
    var typingReps by rememberSaveable { mutableStateOf(false) }
    var typingHold by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            typingWeight = false
            typingReps = false
            typingHold = false
        }
    }
    val stepShown = IncrementTable.displayStep(loadType, unit, equipment)
        ?.let { WeightConverter.formatDisplayNumber(it) }
        ?: unit.stepLabel
    val weightNumber = WorkoutWeightCopy.number(weightKg, unit)
    val plates = if (plated && meaning == WeightMeaning.LIFTED) PlateMath.load(weightKg, unit)?.caption() else null
    val weightField = meaning.fieldLabel.lowercase()
    // Plan / Last as one-tap fills, only while the entry holds something else.
    val quickFills = FloorWeightPresets.quickFills(currentKg = weightKg, plannedKg = plannedKg, lastKg = lastKg)
    val weightColumn: @Composable (Modifier, Dp) -> Unit = { columnModifier, columnWidth ->
        HeroNumeral(
            modifier = columnModifier,
            availableWidth = columnWidth,
            enabled = enabled,
            below = quickFills.takeIf { it.isNotEmpty() }?.let { fills ->
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        fills.forEach { fill ->
                            InstrumentPreset(
                                label = fill.chipLabel(unit),
                                onClick = { onWeightKgChange(fill.weightKg) },
                                modifier = Modifier.weight(1f).testTag(WorkoutTestTags.weightPreset(fill.source)),
                                enabled = enabled,
                                compact = true,
                            )
                        }
                    }
                }
            },
            label = "${meaning.fieldLabel} (${unit.suffix})",
            value = weightNumber,
            sample = WEIGHT_SAMPLE,
            spoken = SetCopy.weightWellSpoken(meaning = meaning, weightKg = weightKg, unit = unit, entryPrecision = true),
            typeLabel = "Type ${if (meaning == WeightMeaning.LIFTED) "a weight" else weightField}",
            decrementSpoken = "Decrease $weightField by $stepShown ${unit.suffix}",
            incrementSpoken = "Increase $weightField by $stepShown ${unit.suffix}",
            onDecrement = {
                onWeightKgChange(FloorStepper.nextWeightKg(weightKg, unit, -1, loadType, equipment))
            },
            onIncrement = {
                onWeightKgChange(FloorStepper.nextWeightKg(weightKg, unit, 1, loadType, equipment))
            },
            onType = { typingWeight = true },
            caption = plates ?: sourceLabel,
            tag = WorkoutTestTags.WEIGHT_STEPPER,
        )
    }
    val holdShown = if (holdRunning) holdRemainingSeconds else holdSeconds ?: HoldWork.DEFAULT_SECONDS
    val workColumn: @Composable (Modifier, Dp) -> Unit = { columnModifier, columnWidth ->
        if (hold) {
            val seconds = holdShown.coerceAtLeast(0)
            HeroNumeral(
                modifier = columnModifier,
                availableWidth = columnWidth,
                enabled = enabled && !holdRunning,
                label = if (holdRunning) HoldWork.HOLD_KICKER else "Time",
                value = HoldWork.clock(seconds),
                sample = TIME_SAMPLE,
                spoken = if (holdRunning) "Hold, ${HoldWork.clock(seconds)} remaining" else "Time ${HoldWork.clock(seconds)}",
                typeLabel = "Type hold seconds",
                decrementSpoken = "Decrease time by ${HoldWork.STEP_SECONDS} seconds",
                incrementSpoken = "Increase time by ${HoldWork.STEP_SECONDS} seconds",
                onDecrement = { onSecondsChange(FloorStepper.nextHoldSeconds(seconds, -1)) },
                onIncrement = { onSecondsChange(FloorStepper.nextHoldSeconds(seconds, 1)) },
                onType = { typingHold = true },
                caption = null,
                tag = WorkoutTestTags.HOLD_STEPPER,
            )
        } else {
            HeroNumeral(
                modifier = columnModifier,
                availableWidth = columnWidth,
                enabled = enabled,
                label = "Reps",
                value = reps.toString(),
                sample = REPS_SAMPLE,
                spoken = "Reps $reps",
                typeLabel = "Type a rep count",
                decrementSpoken = "Decrease reps by 1",
                incrementSpoken = "Increase reps by 1",
                onDecrement = { onRepsChange(FloorStepper.nextReps(reps, -1)) },
                onIncrement = { onRepsChange(FloorStepper.nextReps(reps, 1)) },
                onType = { typingReps = true },
                caption = null,
                tag = WorkoutTestTags.REPS_STEPPER,
            )
        }
    }
    // The one constraints read on the editor: each numeral learns its column width from
    // here, so nothing beneath asks a lazy parent for intrinsic sizes.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (showWeight && !stack) {
            val columnWidth = maxWidth / 2 - Metrics.space2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // The hairline between the numerals, inset from the row's ends.
                        val inset = Metrics.space3.toPx()
                        val x = size.width / 2f
                        drawLine(
                            color = Hairline,
                            start = Offset(x, inset),
                            end = Offset(x, size.height - inset),
                            strokeWidth = Metrics.hairline.toPx(),
                        )
                    }
                    .testTag(WorkoutTestTags.SET_ENTRY),
            ) {
                weightColumn(Modifier.weight(1f).padding(end = Metrics.space2), columnWidth)
                workColumn(Modifier.weight(1f).padding(start = Metrics.space2), columnWidth)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkoutTestTags.SET_ENTRY),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                if (showWeight) {
                    weightColumn(Modifier.fillMaxWidth(), maxWidth)
                    HairlineDivider(startIndent = Metrics.space7)
                }
                workColumn(Modifier.fillMaxWidth(), maxWidth)
            }
        }
    }
    if (typingWeight) {
        val keypadClass = when (meaning) {
            WeightMeaning.ADDED -> LoadClass.BODYWEIGHT_ADDED
            WeightMeaning.ASSISTANCE -> LoadClass.BODYWEIGHT_ASSISTED
            WeightMeaning.LIFTED, WeightMeaning.NONE -> LoadClass.LOADED
        }
        NumberEntryDialog(
            title = meaning.fieldLabel,
            unitLabel = unit.suffix,
            initial = weightNumber,
            decimal = true,
            helper = SetCopy.weightKeypadHelper(
                keypadClass,
                UnloadedLoad.allowsZeroWorkingWeight(loadType, equipment, movementKey),
            ),
            parse = { NumericEntry.parseWeightKg(it, unit) },
            appliedValueLabel = { WeightConverter.formatLabel(it, unit) },
            onConfirm = { onWeightKgChange(it) },
            onDismiss = { typingWeight = false },
        )
    }
    if (typingReps) {
        NumberEntryDialog(
            title = "Reps",
            unitLabel = null,
            initial = reps.toString(),
            decimal = false,
            helper = "A whole number, 1 to ${NumericEntry.MAX_REPS}.",
            parse = { NumericEntry.parseReps(it) },
            onConfirm = { onRepsChange(it) },
            onDismiss = { typingReps = false },
        )
    }
    if (typingHold) {
        NumberEntryDialog(
            title = "Time",
            unitLabel = "s",
            initial = holdShown.coerceAtLeast(0).toString(),
            decimal = false,
            helper = "Seconds or mm:ss, 5 to ${HoldWork.MAX_SECONDS}.",
            parse = { NumericEntry.parseHoldSeconds(it) },
            onConfirm = { onSecondsChange(it) },
            onDismiss = { typingHold = false },
        )
    }
}

/**
 * One labelled hero numeral with a round plate on each side, or beneath it when the
 * column is too narrow for the widest value it may have to show.
 */
@Composable
private fun HeroNumeral(
    modifier: Modifier,
    availableWidth: Dp,
    enabled: Boolean,
    label: String,
    value: String,
    sample: String,
    spoken: String,
    typeLabel: String,
    decrementSpoken: String,
    incrementSpoken: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onType: () -> Unit,
    caption: String?,
    tag: String,
    below: (@Composable () -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(modifier = modifier) {
        val sampleWidth = with(density) {
            measurer.measure(sample, style = InstrumentType.numeralXl, softWrap = false).size.width.toDp()
        }
        val inline = sampleWidth + (Metrics.stepperRound + Metrics.space2) * 2 <= availableWidth
        val numeral: @Composable (Modifier) -> Unit = { numeralModifier ->
            Box(
                modifier = numeralModifier
                    .heightIn(min = Metrics.stepperRound)
                    .clip(RoundedCornerShape(Radius.sm))
                    .testTag(tag)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onType, onClickLabel = typeLabel)
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        customActions = if (!enabled) emptyList() else listOf(
                            CustomAccessibilityAction(decrementSpoken) { onDecrement(); true },
                            CustomAccessibilityAction(incrementSpoken) { onIncrement(); true },
                            CustomAccessibilityAction(typeLabel) { onType(); true },
                        )
                    }
                    .padding(horizontal = Metrics.space1),
                contentAlignment = Alignment.Center,
            ) {
                // A changed value fades in over one tap's worth of motion (nothing under
                // reduced motion). No ellipsis: this is the exact payload about to be committed.
                Crossfade(
                    targetState = value,
                    animationSpec = instrumentTween(Motion.TAP),
                    label = "hero-numeral",
                ) { shown ->
                    Text(
                        shown,
                        style = InstrumentType.numeralXl,
                        color = if (enabled) TextPrimary else TextDisabled,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Kicker(text = label, color = TextSecondary, asHeading = false)
            if (inline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundPlate(label = "−", spoken = decrementSpoken, enabled = enabled, onClick = onDecrement)
                    numeral(Modifier.weight(1f))
                    RoundPlate(label = "+", spoken = incrementSpoken, enabled = enabled, onClick = onIncrement)
                }
            } else {
                numeral(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space4, Alignment.CenterHorizontally),
                ) {
                    RoundPlate(label = "−", spoken = decrementSpoken, enabled = enabled, onClick = onDecrement)
                    RoundPlate(label = "+", spoken = incrementSpoken, enabled = enabled, onClick = onIncrement)
                }
            }
            if (caption != null) {
                Text(
                    caption,
                    modifier = Modifier.fillMaxWidth(),
                    style = InstrumentType.caption,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            below?.invoke()
        }
    }
}

@Composable
private fun RoundPlate(
    label: String,
    spoken: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    StepperButton(
        label = label,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = spoken },
        enabled = enabled,
        plateWidth = Metrics.stepperRound,
        plateHeight = Metrics.stepperRound,
        shape = Radius.full,
        textStyle = InstrumentType.numeralMd,
    )
}

/**
 * Warm-up ladder off the working weight, as value-application presets:
 * `Use 40 lbs · 40%`. Sets the draft; never logs.
 */
@Composable
internal fun WarmupRampRow(
    enabled: Boolean,
    ramp: List<WarmupSet>,
    emphasisIndex: Int,
    unit: WeightUnit,
    onApplyRamp: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ramp.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = ramp.maxOf {
        measurer.measure("Use ${it.weightKg.toWeightLabel(unit)}", style = InstrumentType.bodyStrong, softWrap = false).size.width
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minimum = with(density) { widest.toDp() } + Metrics.space3 * 2
        val columns = ((maxWidth + Metrics.space2) / (minimum + Metrics.space2)).toInt().coerceIn(1, ramp.size)
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.WARMUP_RAMP),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            ramp.forEachIndexed { index, step ->
                InstrumentPreset(
                    enabled = enabled,
                    label = "Use ${step.weightKg.toWeightLabel(unit)}",
                    compact = true,
                    supporting = "${step.percent}%" + if (index == emphasisIndex) " · Suggested" else "",
                    onClick = { onApplyRamp(step.weightKg) },
                    modifier = Modifier.weight(1f).testTag("workout-warmup-preset-$index"),
                )
            }
        }
    }
}

/** Widest values a column must hold without moving its plates: four digits with a half, three reps, a long hold. */
private const val WEIGHT_SAMPLE = "888.8"
private const val REPS_SAMPLE = "888"
private const val TIME_SAMPLE = "88:88"
