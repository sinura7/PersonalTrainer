package com.sinura.personaltrainer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.instrumentTween
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.delay

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    actionTag: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        if (!compact) {
            TemperMark(size = TemperMarkSize)
        }
        Text(
            title,
            style = if (compact) InstrumentType.title else InstrumentType.display,
            color = TextPrimary,
        )
        Text(body, style = InstrumentType.body, color = TextSecondary)
        if (actionLabel != null && onAction != null) {
            val actionModifier =
                if (actionTag != null) Modifier.testTag(actionTag) else Modifier
            if (compact) {
                TextButton(
                    onClick = onAction,
                    modifier = actionModifier,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(actionLabel, style = InstrumentType.bodyStrong, color = Volt)
                }
            } else {
                PrimaryGymButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = actionModifier,
                )
            }
        }
    }
}

/**
 * A spinner that only appears if the wait is real.
 *
 * Every screen in this app reads from a local database, where a query resolves in single
 * digit milliseconds — so an unconditional spinner exists just long enough to flash for a
 * frame or two on every single navigation, which is worse than showing nothing at all.
 * Below the threshold the screen simply stays empty and the content arrives.
 */
@Composable
fun ScreenLoading(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        visible = true
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) {
            CircularProgressIndicator(color = Volt, strokeWidth = SPINNER_STROKE)
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = InstrumentType.title) },
        text = { Text(body, style = InstrumentType.body, color = TextSecondary) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (destructive) Haptics.commit(view)
                    onConfirm()
                },
            ) {
                Text(
                    confirmLabel,
                    style = InstrumentType.bodyStrong,
                    color = if (destructive) Danger else Volt,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Set entry
// ---------------------------------------------------------------------------

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
 * is the field — a hint and an underline sit under it so typing is not a hidden gesture.
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
        typeHint = "Tap the number to type",
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
        typeHint = "Tap the number to type",
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
            // The caller only knows how to nudge, so a typed target becomes the delta that
            // reaches it. Keeping one write path means the draft-persist and validation that
            // hang off onAdjust cannot be bypassed by typing.
            onConfirm = { onAdjust(it - value) },
            onDismiss = { typing = false },
        )
    }
}

/** One labelled numeral with a plate on each side. The atom both steppers are built from. */
@Composable
private fun NumeralWell(
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
            )
        }
    }
}

/**
 * A plate. Press and hold to repeat, with a detent under the thumb for every step.
 *
 * The code that this replaces carried a comment conceding the cost of its own design —
 * "20 kg to 140 kg is 48 taps at the 2.5 kg step" — and then made all 48 taps identical
 * and silent. A physical weight selector clicks per detent and accelerates when held; this
 * one now does both, which turns the weakest part of the app's strongest control into
 * something that feels like equipment.
 */
@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The repeat loop must call the CURRENT lambda, not the one that existed when the press
    // began. LaunchedEffect is remember(key) { ... }, so while `pressed` stays true the
    // running coroutine keeps its original block — and the weight plate's lambda closes over
    // the current weight to compute an absolute target. Captured, every repeat would
    // recompute the same number from the pre-press value: the weight would move one step and
    // then sit still while the haptics kept firing.
    val currentOnClick by rememberUpdatedState(onClick)
    // A press that turned into a hold has already delivered its steps. Compose calls
    // onClick on release, which would otherwise add one more on top of the repeat run.
    var repeatedThisPress by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        // Cleared here rather than on release: a press that is cancelled instead of clicked
        // — dragged off the plate, or stolen by a parent scroll — never reaches the click
        // handler, and a flag left set would silently swallow the next genuine tap.
        repeatedThisPress = false
        delay(HOLD_BEFORE_REPEAT_MS)
        repeatedThisPress = true
        var repeats = 0
        while (true) {
            currentOnClick()
            Haptics.tickLight(view)
            repeats++
            delay(if (repeats >= REPEATS_BEFORE_FAST) FAST_REPEAT_MS else REPEAT_MS)
        }
    }

    val background by animateColorAsState(
        targetValue = if (pressed) SurfacePressed else Surface2,
        animationSpec = instrumentTween(Motion.TAP),
        label = "stepper-press",
    )

    Box(
        modifier = modifier
            .heightIn(min = Metrics.commit)
            .clip(RoundedCornerShape(Radius.sm))
            .background(background)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (repeatedThisPress) {
                        repeatedThisPress = false
                    } else {
                        onClick()
                        Haptics.tick(view)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            style = InstrumentType.numeralMd,
            color = TextPrimary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One typed number, confirmed explicitly.
 *
 * Confirm stays disabled until the text parses, so there is no path where a fumbled entry
 * silently commits the old value or a wrong one — the button simply will not fire. The field
 * opens fully selected, because the first thing anyone does here is replace the number.
 */
@Composable
fun <T> NumberEntryDialog(
    title: String,
    unitLabel: String?,
    initial: String,
    decimal: Boolean,
    helper: String,
    parse: (String) -> T?,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(initial, selection = TextRange(0, initial.length)),
        )
    }
    val parsed = parse(text.text)
    val suffixSlot: (@Composable () -> Unit)? = unitLabel?.let { label -> { Text(label) } }
    val focus = remember { FocusRequester() }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        // The dialog's window attaches a frame after this composes, and requesting focus
        // before the node exists throws. Wait one frame, and treat it as best effort even
        // then: the keyboard opening by itself is a convenience, and losing that race must
        // not take the app down mid-set.
        withFrameNanos { }
        runCatchingCancellable { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = InstrumentType.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = text.text.isNotBlank() && parsed == null,
                // Says why "Set" is greyed out. A disabled button with no reason beside it is
                // just a dead end.
                supportingText = { Text(helper, style = InstrumentType.caption) },
                suffix = suffixSlot,
                textStyle = InstrumentType.numeralMd,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        parsed?.let {
                            onConfirm(it)
                            onDismiss()
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    parsed?.let {
                        Haptics.tick(view)
                        onConfirm(it)
                        onDismiss()
                    }
                },
            ) { Text("Set", style = InstrumentType.bodyStrong, color = Volt) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Rest timer
// ---------------------------------------------------------------------------

/**
 * The rest clock, as a ring.
 *
 * This was a full-width card with a 12dp linear progress bar whose only animation was an
 * alpha blink — identical at two minutes and at three seconds, so it communicated nothing
 * while looking like a rendering fault — and whose fill stepped once per second, because
 * the progress came straight from an integer count with nothing smoothing it. Underneath
 * all that, the duration chips stayed mounted through the whole countdown, so two rows of
 * controls competed beneath the one number that mattered and a mistap silently restarted
 * the timer.
 *
 * Here the sweep interpolates between ticks so it moves continuously, urgency is carried by
 * a colour change and a tick in the last ten seconds, and while the clock is running the
 * only controls on screen are the three that make sense then: less, skip, more.
 */
/**
 * The rest clock, pinned.
 *
 * This is the single most important structural change on the workout screen. The clock used
 * to be an ordinary item in the scrolling list, so the moment a lifter scrolled down to
 * check the sets they had just logged — which is exactly what people do while resting — the
 * number they were waiting on left the screen. Here it sits outside the scroll entirely and
 * cannot be lost.
 *
 * Idle is still this dock: the chips set how long the next rest will be. They do not start
 * it. Logging a working set starts it. Start rest is for between lifts.
 */
@Composable
fun RestDock(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onCustom: (String) -> Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var justFinished by remember { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(running) }
    val view = LocalView.current

    LaunchedEffect(running, remainingSeconds) {
        if (wasRunning && !running && remainingSeconds <= 0) justFinished = true
        if (running) justFinished = false
        wasRunning = running
    }
    LaunchedEffect(justFinished) {
        if (justFinished) {
            delay(FINISHED_DWELL_MS)
            justFinished = false
        }
    }

    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val urgent = running && safeRemaining <= URGENT_SECONDS

    // One tick per second through the final stretch, so the end of the rest can be felt with
    // the phone face-down on a bench.
    LaunchedEffect(urgent, safeRemaining) {
        if (urgent && safeRemaining > 0) Haptics.tick(view)
    }

    if (!running && !justFinished) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Surface1)
                .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        ) {
            RestIdleRow(
                totalSeconds = totalSeconds,
                onPreset = onSelectPreset,
                onCustom = onCustom,
                onStart = onStart,
            )
        }
        HairlineDivider(startIndent = 0.dp)
        return
    }

    // Cyan, which the palette defines as "recovery and rest-day identity". Not gold — that
    // means a record broke and nothing else — and not volt, because Home's rest strip already
    // labels a running timer in cyan and one timer should not change colour with the screen
    // you happen to be looking at.
    val accent = if (urgent) Warn else RestCyan

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RestRing(
                remainingSeconds = if (justFinished) 0 else safeRemaining,
                totalSeconds = totalSeconds,
                accent = accent,
                showClock = false,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Kicker(if (justFinished) "Back to the bar" else "Rest", color = accent)
                Text(
                    RestTimer.formatClock(if (justFinished) 0 else safeRemaining),
                    style = InstrumentType.numeralXl,
                    color = TextPrimary,
                    maxLines = 2,
                )
            }
        }
        if (running) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                RestControl("−15s", onClick = { onAdjust(-15) }, modifier = Modifier.weight(1f))
                RestControl("Skip", onClick = onSkip, modifier = Modifier.weight(1f), emphasised = true)
                RestControl("+15s", onClick = { onAdjust(15) }, modifier = Modifier.weight(1f))
            }
        }
    }
    HairlineDivider(startIndent = 0.dp)
}

/**
 * Choosing how long the next rest will be — an idle-state job, and only an idle-state job.
 *
 * These chips used to stay mounted underneath the running clock, so a countdown was shown
 * with two competing rows of controls beneath it and a mistap silently restarted the timer.
 * They also used to start the clock. That made picking 1:30 feel like rest had begun before
 * the set was logged. Now they only name the next rest; Start rest, or logging a working
 * set, is what starts it.
 */
@Composable
fun RestIdleRow(
    totalSeconds: Int,
    onPreset: (Int) -> Unit,
    onCustom: (String) -> Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker("Next rest")
            Text(
                RestTimer.formatClock(totalSeconds.coerceAtLeast(0)),
                style = InstrumentType.numeralMd,
                color = TextSecondary,
            )
        }
        RestPresetChips(
            selectedSeconds = totalSeconds,
            onSelect = onPreset,
            onCustom = { showCustom = true },
        )
        RestControl(
            label = "Start rest",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            emphasised = true,
        )
    }

    if (showCustom) {
        CustomRestDialog(
            title = "Custom rest",
            confirmLabel = "Set",
            onConfirm = { input ->
                val ok = onCustom(input)
                if (ok) showCustom = false
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun RestRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    accent: Color,
    showClock: Boolean = true,
) {
    val target = if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Linear over exactly one tick, so the sweep glides between whole seconds instead of
    // stepping once a second like a form refreshing.
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "rest-sweep",
    )
    val sweepColor by animateColorAsState(
        targetValue = accent,
        animationSpec = instrumentTween(Motion.BASE),
        label = "rest-accent",
    )
    val clock = RestTimer.formatClock(remainingSeconds)

    Box(
        modifier = Modifier
            .size(if (showClock) RING_SIZE else RING_SIZE_COMPACT)
            .semantics { contentDescription = "Rest, $clock remaining" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = HairlineStrong,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // A wide, faint pass under the sweep stands in for a blur: it reads as a glow
            // on a near-black field and costs nothing on a low-end GPU.
            drawArc(
                color = sweepColor.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 2.4f, cap = StrokeCap.Round),
            )
            drawArc(
                color = sweepColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (showClock) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Kicker("Rest")
                Text(clock, style = InstrumentType.numeralXl, color = TextPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RestControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .heightIn(min = Metrics.control)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (emphasised) Volt else Surface2)
            .then(
                if (emphasised) Modifier else Modifier.border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.sm)),
            )
            .clickable {
                Haptics.tick(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Metrics.space2, vertical = Metrics.space2),
            style = InstrumentType.bodyStrong,
            color = if (emphasised) Pit else TextPrimary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RestPresetChips(
    selectedSeconds: Int?,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customSelected = selectedSeconds != null && selectedSeconds !in RestTimer.PRESETS_SECONDS
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        items(RestTimer.PRESETS_SECONDS) { seconds ->
            InstrumentChip(
                label = RestTimer.formatClock(seconds),
                selected = selectedSeconds == seconds,
                onClick = { onSelect(seconds) },
            )
        }
        item {
            InstrumentChip(
                label = if (customSelected) RestTimer.formatClock(selectedSeconds ?: 0) else "Custom",
                selected = customSelected,
                onClick = onCustom,
            )
        }
    }
}

/**
 * A chip in the app's own language rather than Material's.
 *
 * The stock filter chip draws its selected state from `secondaryContainer`, which is one of
 * the roles the old theme never mapped — so every selected chip in the product was baseline
 * lavender. Even mapped, its tonal fill and 8dp corner belong to a different design system
 * than this one.
 */
@Composable
fun InstrumentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A small glyph before the label. Defaults to nothing, so every existing chip in the app
     * is untouched — the in-workout lift switcher is the only caller that fills it.
     */
    leading: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    // VoltDim, not solid Volt: the palette declares this token as "selected chips, active
    // tracks" and nothing was using it, while every selected chip in the app burned a full
    // accent fill. A screen can hold a dozen chips; the accent is meant to appear once or
    // twice.
    val background by animateColorAsState(
        targetValue = if (selected) VoltDim else Surface2,
        animationSpec = instrumentTween(Motion.TAP),
        label = "chip-fill",
    )
    Box(
        modifier = modifier
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.xs))
            .background(background)
            .border(
                Metrics.hairline,
                if (selected) Volt else Hairline,
                RoundedCornerShape(Radius.xs),
            )
            // selectable, not clickable: this replaced FilterChip everywhere in the app, and
            // FilterChip published a Selected semantics property that a screen reader reads
            // out. With a plain clickable the selected state exists only as a colour swap,
            // which TalkBack cannot see at all.
            .selectable(
                selected = selected,
                onClick = {
                    Haptics.tick(view)
                    onClick()
                },
            )
            .padding(horizontal = Metrics.space4),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            leading?.invoke()
            Text(
                label,
                style = InstrumentType.bodyStrong,
                // Volt ink on the dim fill: Pit ink was only legible against a solid accent.
                color = if (selected) Volt else TextSecondary,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun CustomRestDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = InstrumentType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(
                    "Seconds (90) or mm:ss (1:30). 15 seconds to 30 minutes.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rest") },
                    placeholder = { Text("1:30") },
                    singleLine = true,
                    isError = invalid,
                    textStyle = InstrumentType.numeralMd,
                )
                if (invalid) {
                    Text("Use 90 or 1:30.", style = InstrumentType.caption, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ok = onConfirm(input)
                    invalid = !ok
                    if (!ok) Haptics.reject(view)
                },
            ) { Text(confirmLabel, style = InstrumentType.bodyStrong, color = Volt) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/**
 * The one loud control on a screen.
 *
 * [hapticFeedback] exists so the log-set button can opt out and fire the heavier commit
 * pattern itself, instead of buzzing twice for one press.
 */
@Composable
fun PrimaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Metrics.control,
    hapticFeedback: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            if (hapticFeedback) Haptics.tickLight(view)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Volt,
            contentColor = Pit,
            disabledContainerColor = Surface2,
            disabledContentColor = TextSecondary,
        ),
    ) {
        Text(
            text,
            style = InstrumentType.title,
            color = if (enabled) Pit else TextSecondary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Metrics.control,
    /** Lets a destructive alternative wear [Danger] as ink without becoming a solid red slab. */
    contentColor: Color = TextPrimary,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled) {
                Haptics.tickLight(view)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = Metrics.space3, vertical = Metrics.space2),
            style = InstrumentType.title,
            color = if (enabled) contentColor else TextSecondary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * How the session went, in words, folded away until it is wanted.
 *
 * Collapsed by default because a text field mounted permanently is a text field the thumb
 * finds by accident mid-set; the toggle's own label carries whether anything is written, so
 * nothing has to be opened to check.
 *
 * Shared by the live workout and by session detail rather than duplicated, so a note written
 * during a session and a note added to it a week later are the same affordance with the same
 * words. The write behind [onChange] is debounced by each caller's ViewModel.
 */
enum class NotesKind { SESSION, PROGRAM }

@Composable
fun NotesBlock(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    kind: NotesKind = NotesKind.SESSION,
) {
    Column(modifier = modifier) {
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
            )
            Text(
                notesToggleLabel(kind = kind, notes = notes, expanded = expanded),
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.padding(start = Metrics.space2),
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = notes,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 2,
            )
        }
    }
}

fun notesToggleLabel(kind: NotesKind, notes: String, expanded: Boolean): String = when {
    expanded -> "Hide notes"
    kind == NotesKind.PROGRAM && notes.isBlank() -> "Add notes"
    kind == NotesKind.PROGRAM -> "Notes"
    notes.isBlank() -> "Session notes"
    else -> "Session notes · saved"
}

private val SPINNER_STROKE = 3.dp
private const val SPINNER_DELAY_MS = 250L
private const val HOLD_BEFORE_REPEAT_MS = 400L
private const val REPEAT_MS = 150L
private const val FAST_REPEAT_MS = 60L
private const val REPEATS_BEFORE_FAST = 8
private const val FINISHED_DWELL_MS = 3_500L
private const val URGENT_SECONDS = 10
private val RING_SIZE = 200.dp
private val RING_SIZE_COMPACT = 88.dp
private val RING_STROKE = 10.dp
