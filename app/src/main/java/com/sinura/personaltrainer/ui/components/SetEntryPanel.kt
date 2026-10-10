package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
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
 * Weight and recorded work in one panel: history's set sheet. Two tall wells sit side by side until
 * the system font is large enough that a three-digit half-kilo no longer fits, then they
 * stack. (The gym floor draws its own hero numerals, WeightRepsEditor; W2a removed this
 * panel's unused compact path.)
 *
 * Nudge with the plates, or tap the number to
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
    /** Only an original saved time-only result supplies this; stopwatch strength keeps reps. */
    durationSeconds: Int? = null,
    onDurationSecondsChange: (Int) -> Unit = {},
    /** Accepted saved counts use the whole-rep rule; new entries keep the fumble guard. */
    capturedReps: Boolean = false,
) {
    val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    val workWell: @Composable (Modifier) -> Unit = { wellModifier ->
        if (durationSeconds != null) {
            TimeStepper(value = durationSeconds, onChange = onDurationSecondsChange, modifier = wellModifier)
        } else {
            RepsStepper(
                value = reps,
                onAdjust = onRepsAdjust,
                onRepsChange = onRepsChange,
                modifier = wellModifier,
                capturedReps = capturedReps,
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
        spoken = SetCopy.weightWellSpoken(meaning, valueKg, unit),
        modifier = modifier,
        fitNumeral = true,
        stepperTextStyle = InstrumentType.bodyStrong,
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
    capturedReps: Boolean = false,
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
        fitNumeral = true,
        stepperTextStyle = InstrumentType.bodyStrong,
    )

    if (typing) {
        NumberEntryDialog(
            title = "Reps",
            unitLabel = null,
            initial = value.toString(),
            decimal = false,
            helper = if (capturedReps) NumericEntry.REPS_WHOLE_RULE else "A whole number, 1 to ${NumericEntry.MAX_REPS}.",
            parse = {
                if (capturedReps) NumericEntry.typedWhole(it, min = 1, rule = NumericEntry.REPS_WHOLE_RULE).valueOrNull
                else NumericEntry.parseReps(it)
            },
            // The number itself, not the distance to it. A typed count used to be sent as
            // `it - value`, a delta measured against the well as it was when the keypad
            // opened; if that well had moved by the time Confirm was pressed, the delta
            // landed somewhere else entirely.
            onConfirm = { onRepsChange(it) },
            onDismiss = { typing = false },
        )
    }
}

/** Captured seconds have no live-target range cap; an untouched original is never clamped. */
@Composable
private fun TimeStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var typing by rememberSaveable { mutableStateOf(false) }
    NumeralWell(
        label = "time",
        value = value.toString(),
        unit = "s",
        onType = { typing = true },
        typeLabel = "Type a duration",
        decrementLabel = "−5",
        incrementLabel = "+5",
        onDecrement = { onChange((value.toLong() - 5L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()) },
        onIncrement = { onChange((value.toLong() + 5L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()) },
        modifier = modifier,
        spoken = "Time $value seconds",
        fitNumeral = true,
        stepperTextStyle = InstrumentType.bodyStrong,
    )
    if (typing) {
        NumberEntryDialog(
            title = "Time",
            unitLabel = "s",
            initial = value.toString(),
            decimal = false,
            helper = CAPTURED_DURATION_RULE,
            parse = { NumericEntry.typedWhole(it, min = 1, rule = CAPTURED_DURATION_RULE).valueOrNull },
            onConfirm = onChange,
            onDismiss = { typing = false },
        )
    }
}

private const val CAPTURED_DURATION_RULE = "Enter time as whole seconds, at least 1."

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
    spoken: String? = null,
    /** History's actual values fit with their complete unit; routine target wells retain their layout. */
    fitNumeral: Boolean = false,
    stepperTextStyle: TextStyle? = null,
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface1)
            // A recessed well keeps the quiet edge: the bright SectionEdge is for panels
            // that stand up off the floor, and a rim on a well would read as raised.
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .padding(Metrics.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker(label)
        val fieldModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .testTag(typeLabel)
            .clickable(onClick = onType, onClickLabel = typeLabel, role = if (fitNumeral) Role.Button else null)
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
            .padding(vertical = Metrics.space1)
        val valueLine: @Composable (TextStyle, Modifier) -> Unit = { style, lineModifier ->
            CompositionLocalProvider(LocalLayoutDirection provides if (fitNumeral) LayoutDirection.Ltr else LocalLayoutDirection.current) {
                Row(
                    modifier = lineModifier,
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        value,
                        modifier = Modifier.alignByBaseline(),
                        style = style,
                        color = TextPrimary,
                        maxLines = if (fitNumeral) 1 else 2,
                        softWrap = !fitNumeral,
                        overflow = if (fitNumeral) TextOverflow.Visible else TextOverflow.Ellipsis,
                    )
                    if (unit != null) {
                        Text(
                            unit,
                            modifier = Modifier.alignByBaseline().padding(start = Metrics.space1),
                            style = InstrumentType.unit,
                            color = TextSecondary,
                            maxLines = if (fitNumeral) 1 else Int.MAX_VALUE,
                            softWrap = !fitNumeral,
                        )
                    }
                }
            }
        }
        if (fitNumeral) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            BoxWithConstraints(
                modifier = fieldModifier.heightIn(min = Metrics.touchMin),
                contentAlignment = Alignment.Center,
            ) {
                // Reserve the real unit and gap before fitting, including large system text.
                val unitWidth = unit?.let {
                    measurer.measure(it, style = InstrumentType.unit, softWrap = false).size.width
                } ?: 0
                val room = with(density) {
                    maxWidth.roundToPx() - unitWidth - (if (unit != null) Metrics.space1.roundToPx() else 0) - 1
                }
                val style = LogLoopScale.fittedNumeral(
                    value, InstrumentType.numeralLg.copy(textDirection = TextDirection.Ltr), room,
                ) { text, candidate -> measurer.measure(text, style = candidate, softWrap = false).size.width }
                valueLine(style, Modifier.fillMaxWidth())
            }
        } else {
            valueLine(InstrumentType.numeralLg, fieldModifier)
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
            StepperButton(label = decrementLabel, onClick = onDecrement, modifier = Modifier.weight(1f), textStyle = stepperTextStyle)
            StepperButton(label = incrementLabel, onClick = onIncrement, modifier = Modifier.weight(1f), textStyle = stepperTextStyle)
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
