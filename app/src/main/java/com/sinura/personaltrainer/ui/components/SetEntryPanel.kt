package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.FloorEntryWheels
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * Weight and reps in one panel.
 *
 * The gym floor (`compact`) stacks them as snap-scroll wheels: a weight
 * wheel, then a reps (or hold-time) wheel. Flick to change. No keyboard.
 * Extra, paste, and Home still use the tall wells; those sit side by side
 * until the system font is large enough that a three-digit half-kilo no
 * longer fits, then they stack too.
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
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (loadClass.weightMeaning != WeightMeaning.NONE) {
            val displays = remember(unit, weightKg) {
                FloorEntryWheels.weightDisplays(unit, weightKg)
            }
            val labels = remember(displays) {
                displays.map { WeightConverter.formatDisplayNumber(it) }
            }
            val page = FloorEntryWheels.weightPage(weightKg, unit)
            val shown = labels.getOrElse(page) { "" }
            val plates = if (plated && loadClass.weightMeaning == WeightMeaning.LIFTED) {
                PlateMath.load(weightKg, unit)?.caption()
            } else {
                null
            }
            FloorSnapRow(
                label = loadClass.weightMeaning.fieldLabel.lowercase(),
                spoken = "${loadClass.weightMeaning.fieldLabel} $shown ${unit.suffix}",
                values = labels,
                selectedIndex = page,
                onSettledIndex = { next ->
                    onWeightKgChange(FloorEntryWheels.weightKgAt(next, unit, weightKg))
                },
                tag = "workout-weight-wheel",
                caption = plates,
                parkKey = unit,
            )
        }
        if (hold) {
            if (!holdRunning) {
                val seconds = durationSeconds ?: HoldWork.DEFAULT_SECONDS
                val values = remember(seconds) { FloorEntryWheels.holdSecondsValues(seconds) }
                val labels = remember(values) { values.map { HoldWork.clock(it) } }
                val page = FloorEntryWheels.holdPage(seconds)
                FloorSnapRow(
                    label = "time",
                    spoken = "time ${labels.getOrElse(page) { "" }}",
                    values = labels,
                    selectedIndex = page,
                    onSettledIndex = { next ->
                        onSecondsChange(FloorEntryWheels.holdSecondsAt(next, seconds))
                    },
                    tag = "workout-hold-wheel",
                    parkKey = "hold",
                )
            }
        } else {
            val values = remember { FloorEntryWheels.repsValues() }
            val labels = remember(values) { values.map { it.toString() } }
            val page = FloorEntryWheels.repsPage(reps)
            FloorSnapRow(
                label = "reps",
                spoken = "reps $reps",
                values = labels,
                selectedIndex = page,
                onSettledIndex = { next -> onRepsChange(FloorEntryWheels.repsAt(next)) },
                tag = "workout-reps-wheel",
                parkKey = "reps",
            )
        }
    }
}

@Composable
private fun FloorSnapRow(
    label: String,
    spoken: String,
    values: List<String>,
    selectedIndex: Int,
    onSettledIndex: (Int) -> Unit,
    tag: String,
    caption: String? = null,
    parkKey: Any? = Unit,
    userScrollEnabled: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.wheelRow * 3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Kicker(label, asHeading = false)
            SnapValueWheel(
                values = values,
                selectedIndex = selectedIndex,
                onSettledIndex = onSettledIndex,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = spoken },
                tag = tag,
                rowHeight = Metrics.wheelRow,
                userScrollEnabled = userScrollEnabled,
                parkKey = parkKey,
            )
        }
        if (caption != null) {
            Text(
                caption,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        modifier = modifier,
        compact = compact,
    )

    if (typing) {
        NumberEntryDialog(
            title = label,
            unitLabel = unit.suffix,
            initial = displayNumber,
            decimal = true,
            helper = SetCopy.weightFieldHint(
                when (meaning) {
                    WeightMeaning.ADDED -> LoadClass.BODYWEIGHT_ADDED
                    WeightMeaning.ASSISTANCE -> LoadClass.BODYWEIGHT_ASSISTED
                    WeightMeaning.LIFTED, WeightMeaning.NONE -> LoadClass.LOADED
                },
            ) ?: "A number, up to two decimals.",
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
) {
    val spoken = buildString {
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
                        .semantics { contentDescription = spoken },
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
