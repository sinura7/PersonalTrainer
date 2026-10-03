package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.domain.CapabilityState
import com.sinura.personaltrainer.domain.PermissionsCopy
import com.sinura.personaltrainer.domain.PhoneCapability
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi", sdk = [35])
class PermissionsSectionRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fiveCapabilityRowsWithChipsAndFixButtons() {
        val snapshot = PhoneCapabilitySnapshot(
            sdkInt = 35,
            notificationsEnabled = false,
            restDoneChannelEnabled = true,
            postNotificationsGranted = false,
            canScheduleExactAlarms = false,
            canUseFullScreenIntent = false,
            batteryUnrestricted = false,
            hasVibrator = true,
        )
        compose.setContent {
            PersonalTrainerTheme {
                SettingsSubpage(
                    title = PermissionsCopy.SETTINGS_ROW_SUBTITLE,
                    onBack = {},
                ) {
                    PermissionsSection(snapshot = snapshot)
                }
            }
        }
        compose.onNodeWithTag(SettingsTags.PERMISSIONS).assertIsDisplayed()
        PhoneCapability.entries.forEach { capability ->
            compose.onNodeWithTag(SettingsTags.permissionRow(capability))
                .performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithText(PermissionsCopy.title(capability)).assertIsDisplayed()
        }
        assertTrue(
            compose.onAllNodesWithTag(SettingsTags.permissionChip(CapabilityState.MISSING))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        PhoneCapability.entries.filter { it != PhoneCapability.VIBRATION }.forEach { capability ->
            compose.onNodeWithTag(SettingsTags.permissionFix(capability))
                .performScrollTo()
                .assertIsDisplayed()
        }
        compose.onNodeWithText(PermissionsCopy.INSTALL_TIME_LINE)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
