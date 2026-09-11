package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
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
 * Weight and reps, side by side, in one panel.
 *
 * These were two stacked full-width rows with 108x96dp labelled buttons on either side of
 * each: about three hundred vertical density-independent pixels spent on two numbers that
 * are always read together, which pushed the set list — the record of what you have
 * actually done — off the bottom of the screen. Side by side they fit in roughly a third of
 * that, and the two values a lifter is deciding between sit in one glance.
 *
 * Nudge with the plates, or tap the number to type when the nudge is too far. The numeral
 * is the field — an underline marks it as tappable so typing is not a hidden gesture.
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
) {
    val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
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
                )
            }
            RepsStepper(
                value = reps,
                onAdjust = onRepsAdjust,
                onRepsChange = onRepsChange,
                modifier = Modifier.fillMaxWidth(),
            )
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
                )
            }
            RepsStepper(
                value = reps,
                onAdjust = onRepsAdjust,
                onRepsChange = onRepsChange,
                modifier = Modifier.weight(1f),
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
) {
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
