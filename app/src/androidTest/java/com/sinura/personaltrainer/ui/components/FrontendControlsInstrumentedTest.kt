package com.sinura.personaltrainer.ui.components

import android.os.Build
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.preview.ComponentSection
import com.sinura.personaltrainer.ui.preview.ComponentStateGallery
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class FrontendControlsInstrumentedTest {
    private val compose = createComposeRule()
    // Configure the OS before Compose's Activity launches; changing font_scale
    // afterwards recreates that Activity and discards its test-owned composition.
    private val nativeFontRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                if (description.methodName != "overlaysDismissBeforeReturningToTheirParentAtLargeText") {
                    base.evaluate()
                    return
                }
                check(Build.HARDWARE in setOf("ranchu", "goldfish"))
                val previous = shell("settings get system font_scale").trim()
                try {
                    shell("settings put system font_scale 2.0")
                    awaitNativeFontScale(2f)
                    base.evaluate()
                } finally {
                    if (previous.toFloatOrNull() != null) shell("settings put system font_scale $previous")
                    else shell("settings delete system font_scale")
                    awaitNativeFontScale(previous.toFloatOrNull() ?: 1f)
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(nativeFontRule).around(compose)
    private var previousKeyboardSetting = "null"
    private var changedKeyboardPreference = false
    private var previousAccessibilityFlags = 0

    @Before fun allowNativeKeyboardWithEmulatorHardwareKeys() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Native-window fixtures require an emulator." }
        previousKeyboardSetting = shell("settings get secure show_ime_with_hard_keyboard").trim()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        changedKeyboardPreference = true
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        previousAccessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
    }

    @After fun restoreKeyboardPreference() {
        if (!changedKeyboardPreference) return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = previousAccessibilityFlags
        automation.serviceInfo = info
        if (previousKeyboardSetting in setOf("0", "1")) {
            shell("settings put secure show_ime_with_hard_keyboard $previousKeyboardSetting")
        } else {
            shell("settings delete secure show_ime_with_hard_keyboard")
        }
    }

    @Test fun selectionToggleAndPresetExposeDifferentContracts() {
        var applied = 0
        GoldenCapture.mountViewport(compose = compose, width = 360.dp, height = 800.dp) {
            var warmup by remember { mutableStateOf(false) }
            var enabled by remember { mutableStateOf(false) }
            Column {
                InstrumentChoiceGroup {
                    InstrumentChoiceChip(label = "Working", selected = !warmup, onClick = { warmup = false })
                    InstrumentChoiceChip(label = "Warm-up", selected = warmup, onClick = { warmup = true })
                }
                InstrumentToggleChip(label = "Reminder", checked = enabled, onCheckedChange = { enabled = it })
                InstrumentPreset(label = "Use 15 lbs", onClick = { applied++ })
                InstrumentSuggestion(text = "15 lbs")
            }
        }
        compose.onNodeWithText("Working").assertIsSelected()
        compose.onNodeWithText("Warm-up").performClick().assertIsSelected()
        compose.onNodeWithText("Working").assertIsNotSelected()
        compose.onNodeWithText("Warm-up").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("Reminder").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithText("Reminder").performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNodeWithText("Use 15 lbs").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .performClick()
        compose.onNodeWithText("Suggested · 15 lbs").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun numericEntryValidatesConfirmsAbsoluteValueAndCancelsWithoutChangingDraft() {
        GoldenCapture.mountDevice(compose = compose) {
            ComponentStateGallery(section = ComponentSection.ENTRY)
        }
        compose.onNodeWithText("Edit reps").performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("invalid")
        compose.onNodeWithText("Set").assertIsNotEnabled()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("12")
        // 30 s, as WorkoutEntryJourneyInstrumentedTest: the CI emulator's first keyboard can be slow.
        compose.waitUntil("the keyboard's window is up", 30_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
                val bounds = android.graphics.Rect()
                it.getBoundsInScreen(bounds)
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && bounds.height() > 200
            }
        }
        captureWindow("frontend-number-entry-valid")
        compose.onNodeWithText("Set").performClick()
        compose.onNodeWithText("Log set · 135 lbs × 12").assertIsDisplayed()
        compose.onNodeWithText("Edit reps").performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("99")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Log set · 135 lbs × 12").assertIsDisplayed()
    }

    @Test fun gallerySelectionAppliesOnlyADraft() {
        GoldenCapture.mountViewport(compose = compose, width = 360.dp, height = 800.dp) {
            ComponentStateGallery(section = ComponentSection.SELECTION)
        }
        compose.onNodeWithText("Use 15 lbs").performClick()
        compose.onNodeWithText("Draft: 15 lbs · nothing logged").assertIsDisplayed()
        compose.onNodeWithText("Warm-up").assertIsSelected()
    }

    @Test fun galleryReadyAction() = assertActionSample("Ready")
    @Test fun galleryPressedAction() = assertActionSample("Pressed")
    @Test fun galleryFocusedAction() = assertActionSample("Focused")
    @Test fun gallerySavingAction() = assertActionSample("Saving")
    @Test fun galleryDisabledAction() = assertActionSample("Disabled")

    private fun assertActionSample(sample: String) {
        GoldenCapture.mountViewport(compose = compose, width = 360.dp, height = 800.dp) {
            ComponentStateGallery(section = ComponentSection.ACTIONS)
        }
        compose.onNodeWithText(sample).performScrollTo().performClick()
        if (sample == "Focused") compose.onNodeWithText("Log set · 135 lbs × 8").assertIsFocused()
        if (sample == "Saving" || sample == "Disabled") {
            compose.onNodeWithText(if (sample == "Saving") "Saving…" else "Log set · 135 lbs × 8")
                .assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    if (sample == "Saving") "Saving the sample set" else "Sample action unavailable",
                ))
        }
    }

    @Test fun enlargedLabelsRemainAvailableInGallery() {
        GoldenCapture.mountViewport(compose = compose, width = 360.dp, height = 640.dp, fontScale = 2f) {
            ComponentStateGallery(section = ComponentSection.SELECTION)
        }
        compose.onNodeWithText("Use 15 lbs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Independent switch").performScrollTo().assertIsDisplayed()
    }

    @Test fun overlaysDismissBeforeReturningToTheirParentAtLargeText() {
        GoldenCapture.mountDevice(compose = compose, fontScale = 2f) {
            ComponentStateGallery(section = ComponentSection.OVERLAYS)
        }
        compose.onNodeWithText("Open rest controls").performScrollTo().performClick()
        compose.onNodeWithTag("workout-rest-duration-sheet").assertIsDisplayed()
        captureWindow("frontend-rest-sheet-font20")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("workout-rest-duration-sheet").assertDoesNotExist()
        compose.onNodeWithText("Open confirmation").performScrollTo().performClick()
        compose.onNodeWithText("Discard this sample?").assertIsDisplayed()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed()
        captureWindow("frontend-confirmation-font20")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard this sample?").assertDoesNotExist()
        compose.onNodeWithText("Open numeric entry").performScrollTo().performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).assertIsDisplayed()
        compose.onNodeWithTag("workout-rest-duration-sheet").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Open long-name confirmation").performScrollTo().performClick()
        compose.onNodeWithText("Discard sample").assertIsDisplayed()
        compose.onNodeWithText("This is the complete warning. Only sample data would be discarded.")
            .performScrollTo().assertIsDisplayed()
        captureWindow("frontend-long-confirmation-font20")
        compose.onNodeWithText("Cancel").performTouchInput { click() }
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertDoesNotExist()
    }

    private fun captureWindow(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(200, 5_000)
        // Compose's virtual clock and accessibility idle precede SurfaceFlinger's
        // presented frame for separate dialog/IME surfaces on software rendering.
        android.os.SystemClock.sleep(750)
        instrumentation.waitForIdleSync()
        println("NATIVE_WINDOW $name " + instrumentation.uiAutomation.windows.joinToString { window ->
            val bounds = android.graphics.Rect()
            window.getBoundsInScreen(bounds)
            "type=${window.type} bounds=$bounds title=${window.title}"
        })
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { NativeArtifacts.write(name, bitmap) } finally { bitmap.recycle() }
    }

    private fun shell(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }

    private fun awaitNativeFontScale(expected: Float) {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (resources.configuration.fontScale != expected && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(25)
        }
        assertEquals(expected, resources.configuration.fontScale, 0.01f)
    }
}
