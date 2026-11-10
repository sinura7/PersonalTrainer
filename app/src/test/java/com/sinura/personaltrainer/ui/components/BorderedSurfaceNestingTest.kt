package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h400dp-xhdpi")
class BorderedSurfaceNestingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun panelInsidePanelLogsNestingWarningOnce() {
        ShadowLog.setupLogging()
        compose.setContent {
            PersonalTrainerTheme {
                Panel {
                    Panel { }
                }
            }
        }
        compose.waitForIdle()
        val warnings = ShadowLog.getLogsForTag("TemperRefresh").count { it.type == Log.WARN }
        assertEquals(1, warnings)
    }
}
