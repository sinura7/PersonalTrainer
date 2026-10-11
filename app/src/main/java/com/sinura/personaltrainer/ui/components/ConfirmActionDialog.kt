package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextDisabled
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
    confirmEnabled: Boolean = true,
    recoveryContent: (@Composable () -> Unit)? = null,
    dismissEnabled: Boolean = true,
    canDismiss: () -> Boolean = { dismissEnabled },
    canConfirm: () -> Boolean = { confirmEnabled },
    contextContent: (@Composable () -> Unit)? = null,
    actionKey: Any? = null,
) {
    val view = LocalView.current
    val dismiss = dismissLabel
    val mayDismiss by rememberUpdatedState(canDismiss)
    val mayConfirm by rememberUpdatedState(canConfirm)
    AlertDialog(
        onDismissRequest = { if (mayDismiss()) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled),
        text = {
            // Material's text slot is bounded before the actions. Keep dynamic
            // identity and explanation in the same scroll area so neither can
            // push confirmation/cancellation outside the dialog.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                Text(title, style = InstrumentType.title, color = TextPrimary)
                contextContent?.invoke()
                Text(body, style = InstrumentType.body, color = TextSecondary)
                recoveryContent?.invoke()
            }
        },
        confirmButton = {
            key(actionKey) {
                TextButton(
                    enabled = confirmEnabled,
                    onClick = {
                        if (mayConfirm()) {
                            Haptics.tickLight(view)
                            onConfirm()
                        }
                    },
                    modifier = Modifier
                        .sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                        .testTag(ConfirmActionTags.CONFIRM),
                ) {
                    Text(
                        confirmLabel,
                        style = InstrumentType.bodyStrong,
                        color = when {
                            !confirmEnabled -> TextDisabled
                            destructive -> Danger
                            else -> Volt
                        },
                    )
                }
            }
        },
        dismissButton = if (dismiss == null) {
            null
        } else {
            {
                key(actionKey) {
                    TextButton(
                        onClick = { if (mayDismiss()) onDismiss() },
                        enabled = dismissEnabled,
                        modifier = Modifier
                            .sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                            .testTag(ConfirmActionTags.CANCEL),
                    ) {
                        Text(
                            dismiss,
                            style = InstrumentType.bodyStrong,
                            color = if (dismissEnabled) TextSecondary else TextDisabled,
                        )
                    }
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
    confirmEnabled: Boolean = true,
    recoveryContent: (@Composable () -> Unit)? = null,
    dismissEnabled: Boolean = true,
    canDismiss: () -> Boolean = { dismissEnabled },
    canConfirm: () -> Boolean = { confirmEnabled },
    contextContent: (@Composable () -> Unit)? = null,
    actionKey: Any? = null,
) = GymDialog(
    title = title,
    body = body,
    confirmLabel = confirmLabel,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    dismissLabel = dismissLabel,
    destructive = destructive,
    confirmEnabled = confirmEnabled,
    recoveryContent = recoveryContent,
    dismissEnabled = dismissEnabled,
    canDismiss = canDismiss,
    canConfirm = canConfirm,
    contextContent = contextContent,
    actionKey = actionKey,
)

object ConfirmActionTags {
    const val CONFIRM = "confirm-action-confirm"
    const val CANCEL = "confirm-action-cancel"
}
