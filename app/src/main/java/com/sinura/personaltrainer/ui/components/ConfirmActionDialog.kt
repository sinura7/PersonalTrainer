package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

@Composable
fun GymDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
) {
    val view = LocalView.current
    val dismiss = dismissLabel
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            // Material's text slot is bounded before the actions. Keep dynamic
            // identity and explanation in the same scroll area so neither can
            // push confirmation/cancellation outside the dialog.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                Text(title, style = InstrumentType.title, color = TextPrimary)
                Text(body, style = InstrumentType.body, color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Haptics.tickLight(view)
                    onConfirm()
                },
                modifier = Modifier.testTag(ConfirmActionTags.CONFIRM),
            ) {
                Text(
                    confirmLabel,
                    style = InstrumentType.bodyStrong,
                    color = if (destructive) Danger else Volt,
                )
            }
        },
        dismissButton = if (dismiss == null) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(
                        dismiss,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
        },
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
) = GymDialog(
    title = title,
    body = body,
    confirmLabel = confirmLabel,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    dismissLabel = dismissLabel,
    destructive = destructive,
)

object ConfirmActionTags {
    const val CONFIRM = "confirm-action-confirm"
}
