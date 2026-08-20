package com.sinura.personaltrainer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.sinura.personaltrainer.domain.NumericEntry
import androidx.compose.runtime.withFrameNanos
import com.sinura.personaltrainer.util.runCatchingCancellable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay

val GymNumericStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 0.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            if (compact) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else {
                PrimaryGymButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun ScreenLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@Composable
fun WeightStepper(
    valueKg: Double,
    onWeightKgChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: WeightUnit = LocalWeightUnit.current,
) {
    val displayNumber = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(valueKg, unit))
    // Steppers are for nudging a number, not setting one: 20 kg to 140 kg is 48 taps at the
    // 2.5 kg step. Typing is the escape hatch, and the number itself is the obvious target.
    var typing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton(
            label = "−${unit.stepLabel}",
            onClick = { onWeightKgChange(WeightConverter.incrementKg(valueKg, unit, -1)) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    onClick = { typing = true },
                    onClickLabel = "Type a weight",
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "WEIGHT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                displayNumber,
                style = GymNumericStyle.copy(fontSize = 52.sp, lineHeight = 56.sp),
            )
            Text(
                unit.suffix,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepperButton(
            label = "+${unit.stepLabel}",
            onClick = { onWeightKgChange(WeightConverter.incrementKg(valueKg, unit, 1)) },
        )
    }

    if (typing) {
        NumberEntryDialog(
            title = "Weight",
            unitLabel = unit.suffix,
            initial = displayNumber,
            decimal = true,
            helper = "A number, up to two decimals.",
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton(label = "−1", onClick = { onAdjust(-1) })
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    onClick = { typing = true },
                    onClickLabel = "Type a rep count",
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "REPS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value.toString(),
                style = GymNumericStyle.copy(fontSize = 52.sp, lineHeight = 56.sp),
            )
        }
        StepperButton(label = "+1", onClick = { onAdjust(1) })
    }

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

/**
 * One typed number, confirmed explicitly.
 *
 * Confirm stays disabled until the text parses, so there is no path where a fumbled entry
 * silently commits the old value or a wrong one — the button simply will not fire. The field
 * opens fully selected, because the first thing anyone does here is replace the number.
 */
@Composable
private fun <T> NumberEntryDialog(
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
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = text.text.isNotBlank() && parsed == null,
                // Says why "Set" is greyed out. A disabled button with no reason beside it is
                // just a dead end.
                supportingText = { Text(helper) },
                suffix = suffixSlot,
                textStyle = GymNumericStyle.copy(fontSize = 32.sp),
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
                        onConfirm(it)
                        onDismiss()
                    }
                },
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun StepperButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 108.dp, height = 96.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RestTimerBar(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onCustom: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var justFinished by remember { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running, remainingSeconds) {
        if (wasRunning && !running && remainingSeconds <= 0) {
            justFinished = true
        }
        if (running) {
            justFinished = false
        }
        wasRunning = running
    }
    LaunchedEffect(justFinished) {
        if (justFinished) {
            delay(3_500)
            justFinished = false
        }
    }
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val progress = if (!running || totalSeconds <= 0) {
        0f
    } else {
        (safeRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }
    val infinite = rememberInfiniteTransition(label = "rest-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rest-pulse-alpha",
    )
    val pulseAlpha = if (running) pulse else 1f
    val colors = when {
        running -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        justFinished -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = colors,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (running || justFinished) 16.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (running || justFinished) 12.dp else 8.dp),
        ) {
            if (running) {
                Text(
                    "REST",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    RestTimer.formatClock(safeRemaining),
                    style = GymNumericStyle.copy(fontSize = 56.sp, lineHeight = 60.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .alpha(pulseAlpha),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onAdjust(-15) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) { Text("−15s") }
                    Button(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) { Text("Skip") }
                    OutlinedButton(
                        onClick = { onAdjust(15) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) { Text("+15s") }
                }
            } else if (justFinished) {
                Text(
                    "REST DONE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    RestTimer.formatClock(0),
                    style = GymNumericStyle.copy(fontSize = 48.sp, lineHeight = 52.sp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "Back to the bar.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "REST",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        RestTimer.formatClock(totalSeconds.coerceAtLeast(0)),
                        style = GymNumericStyle.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    )
                }
                Text(
                    "Starts after a working set.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RestPresetChips(
                selectedSeconds = totalSeconds,
                onSelect = onPreset,
                onCustom = { showCustom = true },
            )
        }
    }
    if (showCustom) {
        CustomRestDialog(
            title = "Custom rest",
            confirmLabel = "Start",
            onConfirm = { input ->
                val ok = onCustom(input)
                if (ok) showCustom = false
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(RestTimer.PRESETS_SECONDS) { seconds ->
            FilterChip(
                selected = selectedSeconds == seconds,
                onClick = { onSelect(seconds) },
                label = {
                    Text(
                        RestTimer.formatClock(seconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        }
        item {
            FilterChip(
                selected = customSelected,
                onClick = onCustom,
                label = {
                    Text(
                        if (customSelected) RestTimer.formatClock(selectedSeconds ?: 0) else "Custom",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Seconds (90) or mm:ss (1:30). 15 seconds to 30 minutes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                )
                if (invalid) {
                    Text("Use 90 or 1:30.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { invalid = !onConfirm(input) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PrimaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 64.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
