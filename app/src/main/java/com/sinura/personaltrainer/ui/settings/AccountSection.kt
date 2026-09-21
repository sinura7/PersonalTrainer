package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextTertiary

@Composable
internal fun AccountSection(
    state: AccountUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag(SettingsTags.ACCOUNT)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        if (!state.configured) {
            Text(
                AccountAuthCopy.NOT_CONFIGURED,
                style = InstrumentType.body,
                color = TextTertiary,
            )
            return
        }

        if (state.signedIn) {
            SignedInAccountBody(
                email = state.session!!.email,
                busy = state.busy == AccountBusyKind.SIGN_OUT,
                onSignOut = onSignOut,
            )
        } else {
            SignedOutAccountBody(
                busyKind = state.busy,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onClearError = onClearError,
            )
        }

        state.error?.let { message ->
            Text(
                message,
                style = InstrumentType.caption,
                color = Danger,
                modifier = Modifier.testTag(SettingsTags.ACCOUNT_ERROR),
            )
        }

        Text(
            if (state.signedIn) AccountAuthCopy.SIGNED_IN_CAPTION else AccountAuthCopy.SIGNED_OUT_CAPTION,
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}

@Composable
private fun SignedInAccountBody(
    email: String,
    busy: Boolean,
    onSignOut: () -> Unit,
) {
    GymSectionHeader(title = "Signed in", compact = true)
    Text(
        email,
        style = InstrumentType.bodyStrong,
        modifier = Modifier.testTag(SettingsTags.ACCOUNT_EMAIL),
    )
    SecondaryGymButton(
        text = if (busy) AccountAuthCopy.BUSY_SIGN_OUT else AccountAuthCopy.SIGN_OUT,
        onClick = onSignOut,
        enabled = !busy,
        modifier = Modifier.testTag(SettingsTags.ACCOUNT_SIGN_OUT),
    )
}

@Composable
private fun SignedOutAccountBody(
    busyKind: AccountBusyKind?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onClearError: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val busy = busyKind != null

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(AccountAuthCopy.EMAIL) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.ACCOUNT_EMAIL_FIELD),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(AccountAuthCopy.PASSWORD) },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!busy && email.isNotBlank() && password.isNotBlank()) {
                        onClearError()
                        onSignIn(email, password)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.ACCOUNT_PASSWORD_FIELD),
        )
    }

    PrimaryGymButton(
        text = when (busyKind) {
            AccountBusyKind.SIGN_IN -> AccountAuthCopy.BUSY_SIGN_IN
            AccountBusyKind.SIGN_UP -> AccountAuthCopy.BUSY_SIGN_UP
            AccountBusyKind.SIGN_OUT, null -> AccountAuthCopy.SIGN_IN
        },
        onClick = {
            onClearError()
            onSignIn(email, password)
        },
        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.testTag(SettingsTags.ACCOUNT_SIGN_IN),
    )
    SecondaryGymButton(
        text = when (busyKind) {
            AccountBusyKind.SIGN_UP -> AccountAuthCopy.BUSY_SIGN_UP
            else -> AccountAuthCopy.CREATE_ACCOUNT
        },
        onClick = {
            onClearError()
            onSignUp(email, password)
        },
        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.testTag(SettingsTags.ACCOUNT_SIGN_UP),
    )
}
