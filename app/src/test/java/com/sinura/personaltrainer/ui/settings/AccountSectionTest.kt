package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.domain.AccountAuthCopy
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.domain.SyncCopy
import com.sinura.personaltrainer.domain.SyncStatus
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Account, composed for real: what the owner can read and press while sync is
 * paused and in-app deletion is off, and that both come back intact behind their flags.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class AccountSectionTest {
    @get:Rule val compose = createComposeRule()

    private val session = AccountSession(email = "owner@example.com", userId = "uid-1")

    private fun show(state: AccountUiState) {
        compose.setContent {
            PersonalTrainerTheme {
                AccountSection(
                    state = state,
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _ -> },
                    onSignOut = {},
                    onDeleteAccount = {},
                    onClearError = {},
                )
            }
        }
    }

    private fun sync(active: Boolean, paused: Boolean, pending: Int = 0) = SyncStatus(
        active = active,
        pendingCount = pending,
        lastSuccessAtMs = null,
        lastError = null,
        paused = paused,
    )

    @Test
    fun signedInWhilePausedSaysSoAndOffersNoDeleteButton() {
        show(
            AccountUiState(
                configured = true,
                session = session,
                sync = sync(active = true, paused = true, pending = 4),
                deleteAvailable = false,
            ),
        )

        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC_PAUSED).assertExists()
        compose.onNodeWithText(SyncCopy.PAUSED_BODY).assertExists()
        compose.onNodeWithText(SyncCopy.PAUSED_QUEUE).assertExists()
        compose.onNodeWithText(SyncCopy.SCOPE).assertExists()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_DELETE).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_DELETE_UNAVAILABLE)
            .assertTextEquals(AccountAuthCopy.DELETE_UNAVAILABLE)
        // "4 changes waiting to upload" would promise an upload that is not coming.
        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC).assertDoesNotExist()
        compose.onNodeWithText(AccountAuthCopy.SIGNED_IN_CAPTION).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_SIGN_OUT).assertExists()
    }

    @Test
    fun signedOutWhilePausedStillSaysSoBeforeAnyoneSignsIn() {
        show(
            AccountUiState(
                configured = true,
                session = null,
                sync = sync(active = false, paused = true),
            ),
        )

        compose.onNodeWithTag(SettingsTags.ACCOUNT_ENTRY).assertExists()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC_PAUSED).assertExists()
        // Signed out, nothing is queued, so nothing may be promised as waiting.
        compose.onNodeWithText(SyncCopy.PAUSED_QUEUE).assertDoesNotExist()
    }

    @Test
    fun onceResumedTheStatusLineAndDeleteButtonReturn() {
        show(
            AccountUiState(
                configured = true,
                session = session,
                sync = sync(active = true, paused = false),
                deleteAvailable = true,
            ),
        )

        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC_PAUSED).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC).assertExists()
        compose.onNodeWithText(AccountAuthCopy.SIGNED_IN_CAPTION).assertExists()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_DELETE).assertExists()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_DELETE_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun aBuildWithoutAccountShowsOnlyTheCalmMessage() {
        show(AccountUiState(configured = false, sync = sync(active = false, paused = true)))

        compose.onNodeWithText(AccountAuthCopy.NOT_CONFIGURED).assertExists()
        compose.onNodeWithTag(SettingsTags.ACCOUNT_SYNC_PAUSED).assertDoesNotExist()
    }
}
