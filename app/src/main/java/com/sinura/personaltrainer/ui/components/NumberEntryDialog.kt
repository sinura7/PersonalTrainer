package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.withFrameNanos
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.util.runCatchingCancellable

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
                    .focusRequester(focus)
                    .testTag(NumberEntryTags.FIELD),
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
            ) { Text("Set", style = InstrumentType.bodyStrong, color = if (parsed != null) Volt else TextDisabled) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

object NumberEntryTags {
    const val FIELD = "number-entry-field"
}

/**
 * The rule a box or a form broke, under the thing that broke it.
 *
 * A polite live region, because the old banner these lines replace was one: a screen reader
 * that hears nothing when Save re-enables has been told the save worked. Same caption style
 * and ink everywhere so a complaint reads the same on every screen.
 */
@Composable
fun FieldComplaint(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = InstrumentType.caption,
        color = Danger,
    )
}

/**
 * Marks a text field as holding text that broke [message]'s rule, so assistive tech reads the
 * rule instead of Material's generic "Invalid input". No-op while there is no complaint.
 */
fun Modifier.fieldError(message: String?): Modifier =
    // The property is set directly rather than through the `error(...)` extension: importing a
    // function named `error` would shadow the stdlib `error()` for every checker that reads
    // imports by name, and the property is what the extension sets anyway.
    if (message == null) this else semantics { this[SemanticsProperties.Error] = message }

fun NumericEntry.Ime.imeAction(): ImeAction = when (this) {
    NumericEntry.Ime.NEXT -> ImeAction.Next
    NumericEntry.Ime.DONE -> ImeAction.Done
}
