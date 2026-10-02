package com.sinura.personaltrainer.ui.permissions

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.LaunchPermissionCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LaunchPermissionsHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun batteryStepOpensPerAppBatteryIntent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, false)

        var asked = false
        compose.activity.setContent {
            PersonalTrainerTheme {
                LaunchPermissionsHost(alreadyAsked = false, onAsked = { asked = true })
            }
        }
        compose.waitForIdle()
        assertTrue(asked)

        compose.onNodeWithText(LaunchPermissionCopy.BATTERY_TITLE).assertExists()
        shadowOf(app).clearNextStartedActivities()
        compose.onNodeWithText(LaunchPermissionCopy.CONTINUE).performClick()
        compose.waitForIdle()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, started.action)
        assertEquals("package:${app.packageName}", started.data.toString())
    }
}
