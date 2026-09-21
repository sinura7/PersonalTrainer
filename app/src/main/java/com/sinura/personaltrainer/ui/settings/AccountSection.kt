package com.sinura.personaltrainer.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.ui.theme.instrumentTween

private enum class AccountSignInStep {
    Entry,
    Credentials,
}

@Composable
internal fun AccountSection(
    state: AccountUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onClearError: () -> Unit,
    onLeaveAccount: () -> Unit = {},
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

        var signedOutStep by remember { mutableStateOf(AccountSignInStep.Entry) }
        LaunchedEffect(state.signedIn) {
            if (!state.signedIn) {
                signedOutStep = AccountSignInStep.Entry
            }
        }

        if (state.signedIn) {
            SignedInAccountBody(
                email = state.session!!.email,
                busy = state.busy == AccountBusyKind.SIGN_OUT,
                onSignOut = onSignOut,
            )
        } else {
            Crossfade(
                targetState = signedOutStep,
                animationSpec = instrumentTween(Motion.BASE),
                label = "account-sign-in-step",
            ) { current ->
                when (current) {
                    AccountSignInStep.Entry -> AccountEntryBody(
                        onOpenAccount = { signedOutStep = AccountSignInStep.Credentials },
                        onNotNow = onLeaveAccount,
                    )
                    AccountSignInStep.Credentials -> SignedOutCredentialsBody(
                        busyKind = state.busy,
                        onSignIn = onSignIn,
                        onSignUp = onSignUp,
                        onClearError = onClearError,
                        onBackToEntry = {
                            onClearError()
                            signedOutStep = AccountSignInStep.Entry
                        },
                    )
                }
            }
        }

        state.error?.let { message ->
            Text(
                message,
                style = InstrumentType.caption,
                color = Danger,
                modifier = Modifier.testTag(SettingsTags.ACCOUNT_ERROR),
            )
        }

        if (state.signedIn) {
            Text(
                AccountAuthCopy.SIGNED_IN_CAPTION,
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        } else if (
            state.busy == null &&
            signedOutStep == AccountSignInStep.Credentials
        ) {
            Text(
                AccountAuthCopy.SIGNED_OUT_CAPTION,
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        }

        if (state.signedIn && state.sync.active) {
            val syncLine = AccountAuthCopy.syncStatusLine(
                pending = state.sync.pendingCount,
                lastSuccessAtMs = state.sync.lastSuccessAtMs,
                lastError = state.sync.lastError,
            )
            Text(
                syncLine,
                style = InstrumentType.caption,
                color = when {
                    state.sync.lastError != null -> Danger
                    state.sync.pendingCount > 0 -> Warn
                    else -> TextTertiary
                },
                modifier = Modifier.testTag(SettingsTags.ACCOUNT_SYNC),
            )
        }
    }
}

@Composable
private fun AccountEntryBody(
    onOpenAccount: () -> Unit,
    onNotNow: () -> Unit,
) {
    val view = LocalView.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealed = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTags.ACCOUNT_ENTRY),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Metrics.space8 * 5)
                .clip(RoundedCornerShape(Radius.lg))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(VoltDim, Pit, Pit),
                    ),
                )
                .padding(Metrics.gutter),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Kicker(
                    text = AccountAuthCopy.ENTRY_KICKER,
                    color = if (revealed) Volt else TextTertiary,
                )
                Text(
                    AccountAuthCopy.ENTRY_HEADLINE,
                    style = InstrumentType.display,
                    color = if (revealed) TextPrimary else TextTertiary,
                )
                Text(
                    AccountAuthCopy.ENTRY_BLURB,
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
        }

        PrimaryGymButton(
            text = AccountAuthCopy.ENTRY_OPEN,
            onClick = {
                Haptics.tick(view)
                onOpenAccount()
            },
            modifier = Modifier.testTag(SettingsTags.ACCOUNT_ENTRY_OPEN),
        )
        SecondaryGymButton(
            text = AccountAuthCopy.ENTRY_NOT_NOW,
            onClick = onNotNow,
            modifier = Modifier.testTag(SettingsTags.ACCOUNT_ENTRY_DISMISS),
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
private fun SignedOutCredentialsBody(
    busyKind: AccountBusyKind?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String) -> Unit,
    onClearError: () -> Unit,
    onBackToEntry: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val busy = busyKind != null

    Column(
        modifier = Modifier.testTag(SettingsTags.ACCOUNT_CREDENTIALS),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        SecondaryGymButton(
            text = AccountAuthCopy.CREDENTIALS_BACK,
            onClick = onBackToEntry,
            enabled = !busy,
            modifier = Modifier.testTag(SettingsTags.ACCOUNT_CREDENTIALS_BACK),
        )
        GymSectionHeader(title = AccountAuthCopy.CREDENTIALS_TITLE, compact = true)
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
}
