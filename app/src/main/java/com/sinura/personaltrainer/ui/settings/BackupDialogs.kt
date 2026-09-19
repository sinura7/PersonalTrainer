package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.ui.components.imeAction
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Shows the stored backup password once, after the lock screen has said yes.
 *
 * Deliberately plain: no copy button, because the clipboard on Android is readable by other
 * apps and persists after this dialog is gone. Read it and write it down.
 */
@Composable
internal fun RevealedPasswordDialog(
    password: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Backup password",
                style = InstrumentType.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(
                    text = password,
                    style = InstrumentType.title,
                    color = Volt,
                )
                Text(
                    text = "This opens every backup Temper has written to Drive, on any " +
                        "phone. Keep it somewhere that is not this phone.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    style = InstrumentType.bodyStrong,
                    color = Volt,
                )
            }
        },
    )
}

@Composable
internal fun ProtectBackupDialog(
    drive: Boolean,
    onConfirm: (String, String) -> Boolean,
    /** Null hides the plaintext escape. Arming automatic backup must not offer one. */
    onAdvanced: (() -> Unit)?,
    onDismiss: () -> Unit,
    arming: Boolean = false,
) {
    // remember, not rememberSaveable: a saveable field serializes the plaintext
    // password into the Activity's saved-state Bundle, which the OS persists
    // across process death — exactly the copy the ViewModel wipes elsewhere.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf<String?>(null) }
    val passwordChain = NumericEntry.PASSWORD_CHAIN
    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (arming) {
                    "Remember your backup password?"
                } else if (drive) {
                    "Protect this Drive backup?"
                } else {
                    "Protect this backup?"
                },
                style = InstrumentType.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(
                    if (arming) {
                        "A backup taken while you are not looking still has to be encrypted, " +
                            "so this password is kept on this phone, sealed by Android's " +
                            "keystore. The copies in Drive stay encrypted either way.\n\n" +
                            "Save it somewhere outside this phone now. Temper will not ask " +
                            "for it again, and without it every backup in Drive stays sealed " +
                            "for good — including on a new phone."
                    } else {
                        "The file opens on another phone only with this password. " +
                            "It is not stored on this device."
                    },
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        invalid = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = passwordChain[0].imeAction(),
                    ),
                    keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocus() }),
                    textStyle = InstrumentType.body,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        invalid = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(confirmFocus),
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = passwordChain[1].imeAction(),
                    ),
                    textStyle = InstrumentType.body,
                )
                invalid?.let { Text(it, style = InstrumentType.caption, color = Danger) }
                onAdvanced?.let { advanced ->
                    TextButton(onClick = advanced) {
                        Text(
                            if (drive) {
                                "Upload without a password"
                            } else {
                                "Export without a password"
                            },
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val reason = BackupEnvelope.validateNewPassword(password, confirm)
                    if (reason != null) {
                        invalid = reason
                    } else {
                        onConfirm(password, confirm)
                    }
                },
            ) {
                Text(
                    if (arming) {
                        "Turn on automatic backup"
                    } else if (drive) {
                        "Upload protected backup"
                    } else {
                        "Save protected file"
                    },
                    style = InstrumentType.bodyStrong,
                    color = Volt,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}

@Composable
internal fun UnlockBackupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // remember, not rememberSaveable — see ProtectBackupDialog.
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This backup is protected", style = InstrumentType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(
                    "Enter the password chosen when this file was saved.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = NumericEntry.UNLOCK_PASSWORD.imeAction(),
                    ),
                    textStyle = InstrumentType.body,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text("Open backup", style = InstrumentType.bodyStrong, color = Volt)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        },
    )
}
