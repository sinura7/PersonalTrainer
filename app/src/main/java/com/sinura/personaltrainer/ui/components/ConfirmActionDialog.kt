package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
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
        title = {
            Text(
                title,
                style = InstrumentType.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(body, style = InstrumentType.body, color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (destructive) Haptics.commit(view)
                    onConfirm()
                },
                modifier = Modifier.testTag(ConfirmActionTags.CONFIRM),
            ) {
                Text(
                    confirmLabel,
                    style = InstrumentType.bodyStrong,
                    color = if (destructive) Danger else Volt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
