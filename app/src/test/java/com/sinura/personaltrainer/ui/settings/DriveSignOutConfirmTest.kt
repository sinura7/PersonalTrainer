package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Signing out of Drive forgets automatic backup's saved password, and every backup it wrote
 * opens only with that password. With automatic backup on, the Signed in row asks first; with
 * it off there is nothing to lose, and the row signs out straight away as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class DriveSignOutConfirmTest {
    @get:Rule val compose = createComposeRule()
    private var signOuts = 0

    private fun show(autoBackupEnabled: Boolean) {
        val state = BackupUiState(accountEmail = "owner@example.com", autoBackupEnabled = autoBackupEnabled)
        compose.setContent {
            PersonalTrainerTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupRestoreSection(
                        state = state,
                        clock = ClockFormat.TWELVE,
                        onSignIn = {},
                        onSignOut = { signOuts++ },
                        onCreateBackup = {},
                        onAutoBackupChange = {},
                        onShowBackupPassword = {},
                        onRefresh = {},
                        onRestore = {},
                        onExportFile = {},
                        onExportPlaintext = {},
                        onImportFile = {},
                        onExportSafety = {},
                        onRestoreSafety = {},
                        onDeleteSafety = {},
                        onDismissError = {},
                        onFinishRestore = {},
                        onDismissRestoreNote = {},
                    )
                }
            }
        }
    }

    @Test
    fun withAutomaticBackupOnSigningOutAsksFirstAndCancelChangesNothing() {
        show(autoBackupEnabled = true)
        compose.onNodeWithText("Signed in").performClick()
        compose.onNodeWithText(DriveSignOutCopy.TITLE).assertIsDisplayed()
        compose.onNodeWithText(DriveSignOutCopy.BODY).assertIsDisplayed()
        assertEquals(0, signOuts)

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText(DriveSignOutCopy.TITLE).assertDoesNotExist()
        assertEquals(0, signOuts)
    }

    @Test
    fun withAutomaticBackupOnConfirmingSignsOutOnce() {
        show(autoBackupEnabled = true)
        compose.onNodeWithText("Signed in").performClick()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.onNodeWithText(DriveSignOutCopy.TITLE).assertDoesNotExist()
        assertEquals(1, signOuts)
    }

    @Test
    fun withAutomaticBackupOffSigningOutAsksNothing() {
        show(autoBackupEnabled = false)
        compose.onNodeWithText("Signed in").performClick()
        compose.onNodeWithText(DriveSignOutCopy.TITLE).assertDoesNotExist()
        assertEquals(1, signOuts)
    }
}
