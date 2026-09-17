package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.FloorWeightPresets
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.UnloadedLoad
import com.sinura.personaltrainer.domain.WeightContextAction
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * Weight and reps in one panel.
 *
 * The gym floor (`compact`) stacks them as plate steppers: −step, a
 * labelled numeral, +step. Tap the center to type. Extra, paste, and
 * Home still use the tall wells; those sit side by side until the system
 * font is large enough that a three-digit half-kilo no longer fits, then
 * they stack too.
 *
 * On Extra / paste / Home, nudge with the plates, or tap the number to
 * type when the nudge is too far. The numeral is the field — an underline
 * marks it as tappable so typing is not a hidden gesture.
 *
 * **How many wells appear depends on the lift.** A push-up has no weight to enter, so it gets
 * one well and reps fill the panel: a labelled empty weight box is an invitation to put a
 * number in it, and the number someone would put there is their own bodyweight, which is not
 * what that column means. A weighted pull-up gets two, and the first is labelled "added" —
 * because twenty kilos on a dip belt is not twenty kilos lifted, and the same field on an
 * assisted machine is weight taken *off*.
 */
@Composable
fun SetEntryPanel(
    weightKg: Double,
    reps: Int,
    onWeightKgChange: (Double) -> Unit,
    onRepsAdjust: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: WeightUnit = LocalWeightUnit.current,
    loadClass: LoadClass = LoadClass.LOADED,
    /** Barbell only. A stack or a dumbbell has no Olympic bar to read out. */
    plated: Boolean = false,
    hold: Boolean = false,
    durationSeconds: Int? = null,
    holdRunning: Boolean = false,
    remainingSeconds: Int = 0,
    onSecondsAdjust: (Int) -> Unit = {},
    onSecondsChange: (Int) -> Unit = {},
    compact: Boolean = false,
    loadType: LoadType? = null,
    equipment: EquipmentType? = null,
    movementKey: String? = null,
    plannedKg: Double? = null,
    lastKg: Double? = null,
    suggestedKg: Double? = null,
) {
    if (compact) {
        CompactFloorEntry(
            weightKg = weightKg,
            reps = reps,
            onWeightKgChange = onWeightKgChange,
            onRepsChange = onRepsChange,
            modifier = modifier,
            unit = unit,
            loadClass = loadClass,
            plated = plated,
            hold = hold,
            durationSeconds = durationSeconds,
            holdRunning = holdRunning,
            onSecondsChange = onSecondsChange,
            loadType = loadType,
            equipment = equipment,
            movementKey = movementKey,
            plannedKg = plannedKg,
            lastKg = lastKg,
            suggestedKg = suggestedKg,
        )
        return
    }
    val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    val timeSeconds = if (holdRunning) remainingSeconds else durationSeconds ?: HoldWork.DEFAULT_SECONDS
    val workWell: @Composable (Modifier) -> Unit = { wellModifier ->
        if (hold) {
            TimeStepper(
                value = timeSeconds,
                running = holdRunning,
                onAdjust = onSecondsAdjust,
                onSecondsChange = onSecondsChange,
                modifier = wellModifier,
                compact = compact,
            )
        } else {
            RepsStepper(
                value = reps,
                onAdjust = onRepsAdjust,
                onRepsChange = onRepsChange,
                modifier = wellModifier,
                compact = compact,
            )
        }
    }
    if (stack) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (loadClass.weightMeaning != WeightMeaning.NONE) {
                WeightStepper(
                    valueKg = weightKg,
                    onWeightKgChange = onWeightKgChange,
                    modifier = Modifier.fillMaxWidth(),
                    unit = unit,
                    meaning = loadClass.weightMeaning,
                    plated = plated && loadClass.weightMeaning == WeightMeaning.LIFTED,
                    compact = compact,
                )
            }
            workWell(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (loadClass.weightMeaning != WeightMeaning.NONE) {
                WeightStepper(
                    valueKg = weightKg,
                    onWeightKgChange = onWeightKgChange,
                    modifier = Modifier.weight(1f),
                    unit = unit,
                    meaning = loadClass.weightMeaning,
                    plated = plated && loadClass.weightMeaning == WeightMeaning.LIFTED,
                    compact = compact,
                )
            }
            workWell(
                if (loadClass.weightMeaning != WeightMeaning.NONE) Modifier.weight(1f)
                else Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactFloorEntry(
    weightKg: Double,
    reps: Int,
    onWeightKgChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    modifier: Modifier,
    unit: WeightUnit,
    loadClass: LoadClass,
    plated: Boolean,
    hold: Boolean,
    durationSeconds: Int?,
    holdRunning: Boolean,
    onSecondsChange: (Int) -> Unit,
    loadType: LoadType?,
    equipment: EquipmentType?,
    movementKey: String?,
    plannedKg: Double?,
    lastKg: Double?,
    suggestedKg: Double?,
) {
    val resolvedLoad = loadType ?: when (loadClass) {
        LoadClass.LOADED -> LoadType.EXTERNAL
        LoadClass.BODYWEIGHT -> LoadType.BODYWEIGHT
        LoadClass.BODYWEIGHT_ADDED -> LoadType.BODYWEIGHT_PLUS
        LoadClass.BODYWEIGHT_ASSISTED -> LoadType.ASSISTED
    }
    val showWeight = loadClass.weightMeaning != WeightMeaning.NONE
    val stepShown = IncrementTable.displayStep(resolvedLoad, unit, equipment)
        ?.let { WeightConverter.formatDisplayNumber(it) }
        ?: unit.stepLabel
    var typingWeight by rememberSaveable { mutableStateOf(false) }
    var typingReps by rememberSaveable { mutableStateOf(false) }
    var typingHold by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (showWeight) {
            val displayNumber = WeightConverter.formatDisplayNumber(
                WeightConverter.toDisplayValue(weightKg, unit),
            )
            val meaning = loadClass.weightMeaning
            val plates = if (plated && meaning == WeightMeaning.LIFTED) {
                PlateMath.load(weightKg, unit)?.caption()
            } else {
                null
            }
            val source = FloorWeightPresets.source(
                currentKg = weightKg,
                plannedKg = plannedKg,
                lastKg = lastKg,
                suggestedKg = suggestedKg,
            )
            val actions = FloorWeightPresets.contextActions(
                plannedKg = plannedKg,
                lastKg = lastKg,
            ).filter { WeightConverter.toDisplayValue(it.weightKg, unit) != WeightConverter.toDisplayValue(weightKg, unit) }
            FloorNumeralRow(
                label = meaning.fieldLabel,
                value = displayNumber,
                unit = unit.suffix,
                spoken = SetCopy.weightWellSpoken(meaning, weightKg, unit),
                typeLabel = "Type ${if (meaning == WeightMeaning.LIFTED) "a weight" else meaning.fieldLabel.lowercase()}",
                decrementLabel = "−$stepShown",
                incrementLabel = "+$stepShown",
                onDecrement = {
                    onWeightKgChange(
                        FloorStepper.nextWeightKg(weightKg, unit, -1, resolvedLoad, equipment),
                    )
                },
                onIncrement = {
                    onWeightKgChange(
                        FloorStepper.nextWeightKg(weightKg, unit, 1, resolvedLoad, equipment),
                    )
                },
                onType = { typingWeight = true },
                glyph = TemperIcons.FloorWeight,
                glyphTag = "workout-weight-glyph",
                wellTag = "workout-weight-stepper",
                plateWidth = Metrics.stepperPlateWidth,
                plateHeight = Metrics.stepperWeightHeight,
                sourceLabel = source?.label,
                caption = plates,
                contextActions = actions,
                onContextAction = { onWeightKgChange(it.weightKg) },
                unitForChips = unit,
            )
            if (typingWeight) {
                val keypadClass = when (meaning) {
                    WeightMeaning.ADDED -> LoadClass.BODYWEIGHT_ADDED
                    WeightMeaning.ASSISTANCE -> LoadClass.BODYWEIGHT_ASSISTED
                    WeightMeaning.LIFTED, WeightMeaning.NONE -> LoadClass.LOADED
                }
                NumberEntryDialog(
                    title = meaning.fieldLabel,
                    unitLabel = unit.suffix,
                    initial = displayNumber,
                    decimal = true,
                    helper = SetCopy.weightKeypadHelper(
                        keypadClass,
                        UnloadedLoad.allowsZeroWorkingWeight(resolvedLoad, equipment, movementKey),
                    ),
                    parse = { NumericEntry.parseWeightKg(it, unit) },
                    onConfirm = { onWeightKgChange(it) },
                    onDismiss = { typingWeight = false },
                )
            }
        }
        if (hold) {
            if (!holdRunning) {
                val seconds = durationSeconds ?: HoldWork.DEFAULT_SECONDS
                FloorNumeralRow(
                    label = "Time",
                    value = HoldWork.clock(seconds),
                    unit = null,
                    spoken = "time ${HoldWork.clock(seconds)}",
                    typeLabel = "Type hold seconds",
                    decrementLabel = "−${HoldWork.STEP_SECONDS}s",
                    incrementLabel = "+${HoldWork.STEP_SECONDS}s",
                    onDecrement = { onSecondsChange(FloorStepper.nextHoldSeconds(seconds, -1)) },
                    onIncrement = { onSecondsChange(FloorStepper.nextHoldSeconds(seconds, 1)) },
                    onType = { typingHold = true },
                    glyph = TemperIcons.FloorRepsTime,
                    glyphTag = "workout-reps-time-glyph",
                    wellTag = "workout-hold-stepper",
                    plateWidth = Metrics.stepperPlateWidth,
                    plateHeight = if (showWeight) Metrics.stepperRepsHeight else Metrics.stepperWeightHeight,
                )
                if (typingHold) {
                    NumberEntryDialog(
                        title = "Time",
                        unitLabel = "s",
                        initial = seconds.toString(),
                        decimal = false,
                        helper = "Seconds or mm:ss, 5 to ${HoldWork.MAX_SECONDS}.",
                        parse = { NumericEntry.parseHoldSeconds(it) },
                        onConfirm = { onSecondsChange(it) },
                        onDismiss = { typingHold = false },
                    )
                }
            }
        } else {
            FloorNumeralRow(
                label = "Reps",
                value = reps.toString(),
                unit = "reps",
                spoken = "reps $reps",
                typeLabel = "Type a rep count",
                decrementLabel = "−1",
                incrementLabel = "+1",
                onDecrement = { onRepsChange(FloorStepper.nextReps(reps, -1)) },
                onIncrement = { onRepsChange(FloorStepper.nextReps(reps, 1)) },
                onType = { typingReps = true },
                glyph = TemperIcons.FloorRepsTime,
                glyphTag = "workout-reps-time-glyph",
                wellTag = "workout-reps-stepper",
                plateWidth = Metrics.stepperPlateWidth,
                plateHeight = if (showWeight) Metrics.stepperRepsHeight else Metrics.stepperWeightHeight,
            )
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
        }
    }
}

@Composable
private fun FloorNumeralRow(
    label: String,
    value: String,
    unit: String?,
    spoken: String,
    typeLabel: String,
    decrementLabel: String,
    incrementLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onType: () -> Unit,
    glyph: ImageVector,
    glyphTag: String,
    wellTag: String,
    plateWidth: Dp,
    plateHeight: Dp,
    sourceLabel: String? = null,
    caption: String? = null,
    contextActions: List<WeightContextAction> = emptyList(),
    onContextAction: (WeightContextAction) -> Unit = {},
    unitForChips: WeightUnit = WeightUnit.KG,
) {
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueText = buildAnnotatedString {
        append(value)
        if (unit != null) withStyle(InstrumentType.unit.toSpanStyle()) { append(" $unit") }
    }
    val valueWidth = maxOf(
        measurer.measure(valueText, style = InstrumentType.numeralLg, softWrap = false).size.width,
        measurer.measure(label, style = InstrumentType.caption, softWrap = false).size.width +
            with(density) { (Metrics.icon + Metrics.chevron + Metrics.space2).roundToPx() },
    )
    val adjustmentWidth = maxOf(
        measurer.measure(decrementLabel, style = InstrumentType.bodyStrong, softWrap = false).size.width,
        measurer.measure(incrementLabel, style = InstrumentType.bodyStrong, softWrap = false).size.width,
    )
    val fittingPlateWidth = maxOf(plateWidth, with(density) { adjustmentWidth.toDp() } + Metrics.space2 * 2)
    val editableValue: @Composable (Modifier) -> Unit = { valueModifier ->
        Column(
            modifier = valueModifier
                .heightIn(min = plateHeight)
                .clip(RoundedCornerShape(Radius.xs))
                .background(Surface2)
                .testTag(wellTag)
                .clickable(role = Role.Button, onClick = onType, onClickLabel = typeLabel)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    customActions = listOf(
                        CustomAccessibilityAction("Decrease $label") { onDecrement(); true },
                        CustomAccessibilityAction("Increase $label") { onIncrement(); true },
                        CustomAccessibilityAction(typeLabel) { onType(); true },
                    )
                }
                .padding(horizontal = Metrics.space1, vertical = Metrics.space1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                FloorFieldGlyph(icon = glyph, modifier = Modifier.testTag(glyphTag))
                Text(label, style = InstrumentType.caption, color = TextSecondary)
                Icon(TemperIcons.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(Metrics.chevron))
            }
            // Values and units share a baseline and can grow vertically. No ellipsis:
            // this is the exact payload the user is about to commit.
            Text(valueText, style = InstrumentType.numeralLg, color = TextPrimary, textAlign = TextAlign.Center)
            if (sourceLabel != null) {
                Text(sourceLabel, style = InstrumentType.caption, color = TextTertiary, textAlign = TextAlign.Center)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideSpace = fittingPlateWidth * 2 + Metrics.space2 * 2 + Metrics.space1 * 2
            val valueFits = valueWidth <= with(density) { (maxWidth - sideSpace).roundToPx() }
            if (valueFits) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    StepperButton(label = decrementLabel, onClick = onDecrement, compact = true, plateWidth = fittingPlateWidth, plateHeight = plateHeight)
                    editableValue(Modifier.weight(1f))
                    StepperButton(label = incrementLabel, onClick = onIncrement, compact = true, plateWidth = fittingPlateWidth, plateHeight = plateHeight)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    editableValue(Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StepperButton(label = decrementLabel, onClick = onDecrement, compact = true, plateWidth = fittingPlateWidth, plateHeight = plateHeight)
                        StepperButton(label = incrementLabel, onClick = onIncrement, compact = true, plateWidth = fittingPlateWidth, plateHeight = plateHeight)
                    }
                }
            }
        }
        if (caption != null) {
            Text(
                caption,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (contextActions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                contextActions.forEach { action ->
                    TextButton(
                        onClick = {
                            Haptics.tick(view)
                            onContextAction(action)
                        },
                        modifier = Modifier.heightIn(min = Metrics.touchMin),
                    ) {
                        Text(
                            action.chipLabel(unitForChips),
                            style = InstrumentType.bodyStrong,
                            color = Volt,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeightStepper(
    valueKg: Double,
    onWeightKgChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: WeightUnit = LocalWeightUnit.current,
    /** What this number is a measurement of. See [LoadClass.weightMeaning]. */
    meaning: WeightMeaning = WeightMeaning.LIFTED,
    plated: Boolean = false,
    compact: Boolean = false,
) {
    val displayNumber = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(valueKg, unit))
    // Steppers are for nudging a number, not setting one: 20 kg to 140 kg is 48 taps at the
    // 2.5 kg step. Typing is the escape hatch, and the number itself is the obvious target.
    var typing by rememberSaveable { mutableStateOf(false) }
    val label = meaning.fieldLabel
    val plates = if (plated) PlateMath.load(valueKg, unit)?.caption() else null

    NumeralWell(
        label = label.lowercase(),
        value = displayNumber,
        unit = unit.suffix,
        onType = { typing = true },
        typeLabel = "Type ${if (meaning == WeightMeaning.LIFTED) "a weight" else label.lowercase()}",
        decrementLabel = "−${unit.stepLabel}",
        incrementLabel = "+${unit.stepLabel}",
        onDecrement = { onWeightKgChange(WeightConverter.incrementKg(valueKg, unit, -1)) },
        onIncrement = { onWeightKgChange(WeightConverter.incrementKg(valueKg, unit, 1)) },
        plateCaption = plates,
        spoken = SetCopy.weightWellSpoken(meaning, valueKg, unit),
        modifier = modifier,
        compact = compact,
    )

    if (typing) {
        NumberEntryDialog(
            title = label,
            unitLabel = unit.suffix,
            initial = displayNumber,
            decimal = true,
            helper = SetCopy.weightKeypadHelper(
                when (meaning) {
                    WeightMeaning.ADDED -> LoadClass.BODYWEIGHT_ADDED
                    WeightMeaning.ASSISTANCE -> LoadClass.BODYWEIGHT_ASSISTED
                    WeightMeaning.LIFTED, WeightMeaning.NONE -> LoadClass.LOADED
                },
                allowsZero = meaning != WeightMeaning.LIFTED,
            ),
            parse = { NumericEntry.parseWeightKg(it, unit) },
            onConfirm = { onWeightKgChange(it) },
            onDismiss = { typing = false },
        )
    }
}

@Composable
fun RepsStepper(
    value: Int,
    onAdjust: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var typing by rememberSaveable { mutableStateOf(false) }

    NumeralWell(
        label = "reps",
        value = value.toString(),
        unit = null,
        onType = { typing = true },
        typeLabel = "Type a rep count",
        decrementLabel = "−1",
        incrementLabel = "+1",
        onDecrement = { onAdjust(-1) },
        onIncrement = { onAdjust(1) },
        modifier = modifier,
        compact = compact,
    )

    if (typing) {
        NumberEntryDialog(
            title = "Reps",
            unitLabel = null,
            initial = value.toString(),
            decimal = false,
            helper = "A whole number, 1 to ${NumericEntry.MAX_REPS}.",
            parse = { NumericEntry.parseReps(it) },
            // The number itself, not the distance to it. A typed count used to be sent as
            // `it - value`, a delta measured against the well as it was when the keypad
            // opened; if that well had moved by the time Confirm was pressed, the delta
            // landed somewhere else entirely.
            onConfirm = { onRepsChange(it) },
            onDismiss = { typing = false },
        )
    }
}

@Composable
fun TimeStepper(
    value: Int,
    onAdjust: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    compact: Boolean = false,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val shown = value.coerceAtLeast(0)

    NumeralWell(
        label = if (running) "hold" else "time",
        value = HoldWork.clock(shown),
        unit = null,
        onType = { if (!running) typing = true },
        typeLabel = "Type hold seconds",
        decrementLabel = "−${HoldWork.STEP_SECONDS}s",
        incrementLabel = "+${HoldWork.STEP_SECONDS}s",
        onDecrement = { if (!running) onAdjust(-1) },
        onIncrement = { if (!running) onAdjust(1) },
        modifier = modifier,
        compact = compact,
    )

    if (typing && !running) {
        NumberEntryDialog(
            title = "Time",
            unitLabel = "s",
            initial = shown.toString(),
            decimal = false,
            helper = "Seconds, 5 to ${HoldWork.MAX_SECONDS}. A range like 20–40 uses the first number.",
            parse = { HoldWork.parseRange(it)?.minSeconds },
            onConfirm = { onSecondsChange(it) },
            onDismiss = { typing = false },
        )
    }
}

/** One labelled numeral with a plate on each side. The atom both steppers are built from. */
@Composable
internal fun NumeralWell(
    label: String,
    value: String,
    unit: String?,
    onType: () -> Unit,
    typeLabel: String,
    decrementLabel: String,
    incrementLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    typeHint: String? = null,
    plateCaption: String? = null,
    compact: Boolean = false,
    spoken: String? = null,
) {
    val resolvedSpoken = spoken ?: buildString {
        append(label)
        append(' ')
        append(value)
        if (unit != null) {
            append(' ')
            append(unit)
        }
    }
    if (compact) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Surface1)
                    .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                Kicker(label, asHeading = false)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(typeLabel)
                        .clickable(onClick = onType, onClickLabel = typeLabel)
                        .semantics { contentDescription = resolvedSpoken },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        value,
                        modifier = Modifier.alignByBaseline(),
                        style = InstrumentType.numeralMd,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (unit != null) {
                        Text(
                            unit,
                            modifier = Modifier
                                .alignByBaseline()
                                .padding(start = Metrics.space1),
                            style = InstrumentType.unit,
                            color = TextSecondary,
                        )
                    }
                }
                StepperButton(
                    label = decrementLabel,
                    onClick = onDecrement,
                    compact = true,
                )
                StepperButton(
                    label = incrementLabel,
                    onClick = onIncrement,
                    compact = true,
                )
            }
            if (plateCaption != null) {
                Text(
                    plateCaption,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface1)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .padding(Metrics.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .testTag(typeLabel)
                .clickable(onClick = onType, onClickLabel = typeLabel)
                .semantics { contentDescription = resolvedSpoken }
                .drawBehind {
                    val inset = size.width * 0.18f
                    drawLine(
                        color = HairlineStrong,
                        start = Offset(inset, size.height),
                        end = Offset(size.width - inset, size.height),
                        strokeWidth = Metrics.hairline.toPx(),
                    )
                }
                .padding(vertical = Metrics.space1),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                value,
                modifier = Modifier.alignByBaseline(),
                // numeralLg, not numeralXl: at 56sp a three-digit weight with a half — 102.5,
                // 107.5, every second plate above 100kg — is wider than half the screen once
                // the unit and the well's padding are taken out, and a clipped weight in the
                // entry panel is the worst possible place to lose a digit.
                style = InstrumentType.numeralLg,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit != null) {
                Text(
                    unit,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Metrics.space1),
                    style = InstrumentType.unit,
                    color = TextSecondary,
                )
            }
        }
        if (typeHint != null) {
            Text(
                typeHint,
                style = InstrumentType.caption,
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            StepperButton(label = decrementLabel, onClick = onDecrement, modifier = Modifier.weight(1f))
            StepperButton(label = incrementLabel, onClick = onIncrement, modifier = Modifier.weight(1f))
        }
        if (plateCaption != null) {
            Text(
                plateCaption,
                style = InstrumentType.caption,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
