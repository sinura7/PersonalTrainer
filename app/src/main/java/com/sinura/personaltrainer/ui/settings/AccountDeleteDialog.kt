package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
internal fun DeleteAccountDialog(
    accountEmail: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    var typed by remember(accountEmail) { mutableStateOf("") }
    val emailMatches = typed.trim().equals(accountEmail.trim(), ignoreCase = true)
    var showMismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Metrics.space4),
            ) {
                Text(
                    AccountAuthCopy.DELETE_ACCOUNT_TITLE,
                    style = InstrumentType.title,
                    color = TextPrimary,
                )
                Text(
                    AccountAuthCopy.DELETE_ACCOUNT_BODY,
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                Text(
                    AccountAuthCopy.DELETE_ACCOUNT_TYPE_EMAIL,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = {
                        typed = it
                        showMismatch = false
                    },
                    label = { Text(AccountAuthCopy.EMAIL) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (emailMatches && !busy) {
                                Haptics.tickLight(view)
                                onConfirm()
                            } else {
                                showMismatch = true
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTags.ACCOUNT_DELETE_EMAIL_FIELD),
                )
                if (showMismatch && !emailMatches) {
                    Text(
                        AccountAuthCopy.DELETE_ACCOUNT_EMAIL_MISMATCH,
                        style = InstrumentType.caption,
                        color = Danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!emailMatches) {
                        showMismatch = true
                        return@TextButton
                    }
                    Haptics.tickLight(view)
                    onConfirm()
                },
                enabled = !busy && emailMatches,
                modifier = Modifier.testTag(SettingsTags.ACCOUNT_DELETE_CONFIRM),
            ) {
                Text(
                    if (busy) AccountAuthCopy.BUSY_DELETE_ACCOUNT else AccountAuthCopy.DELETE_ACCOUNT_CONFIRM,
                    style = InstrumentType.bodyStrong,
                    color = Danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(
                    "Cancel",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        },
    )
}
