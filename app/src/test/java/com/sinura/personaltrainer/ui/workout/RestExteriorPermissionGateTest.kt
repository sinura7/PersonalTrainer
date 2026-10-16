package com.sinura.personaltrainer.ui.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.RestExteriorPermissionCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/**
 * Rest-exterior permissions are asked on the workout floor when rest is relevant, with Later as
 * a real answer — not as a root-level block on every resume (UX blueprint P-5).
 */
@RunWith(RobolectricTestRunner::class)
class RestExteriorPermissionGateTest {
    @get:Rule val compose = createComposeRule()

    private var asked by mutableStateOf<Boolean?>(false)
    private var active by mutableStateOf(false)
    private var screen by mutableIntStateOf(0)
    private var marks = 0

    private fun showGate(provided: Boolean = true) {
        compose.setContent {
            val ask = RestExteriorAsk(asked = asked, markAsked = { marks++; asked = true })
            if (provided) {
                CompositionLocalProvider(LocalRestExteriorAsk provides ask) {
                    key(screen) { RestExteriorPermissionGate(active = active) }
                }
            } else {
                key(screen) { RestExteriorPermissionGate(active = active) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun laterDismissesAndMarksAskedWithoutBlockingWhenInactive() {
        active = true
        showGate()
        if (ShadowDialog.getLatestDialog() != null) {
            compose.onNodeWithText(RestExteriorPermissionCopy.LATER).performClick()
            assertEquals(1, marks)
            active = false
            compose.waitForIdle()
            compose.onNodeWithText(RestExteriorPermissionCopy.BLOCKING_TITLE).assertDoesNotExist()
        }
    }

    @Test
    fun staysDownWhenShellSaysAlreadyAsked() {
        asked = true
        active = true
        showGate()
        compose.onNodeWithText(RestExteriorPermissionCopy.BLOCKING_TITLE).assertDoesNotExist()
    }

    @Test
    fun defaultCompositionLocalCountsAsAsked() {
        asked = null
        active = true
        showGate(provided = false)
        compose.onNodeWithText(RestExteriorPermissionCopy.BLOCKING_TITLE).assertDoesNotExist()
    }
}
