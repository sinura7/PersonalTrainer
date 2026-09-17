package com.sinura.personaltrainer.ui.workout

import android.os.Build
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.FileInputStream

/** Real-window input/sheet journeys over the real Room repository and workout VM. */
@RunWith(AndroidJUnit4::class)
class WorkoutEntryJourneyInstrumentedTest {
    private val compose = createComposeRule()
    private val nativeWindowRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                check(Build.HARDWARE in setOf("ranchu", "goldfish"))
                val previousScale = shell("settings get system font_scale").trim()
                val previousKeyboard = shell("settings get secure show_ime_with_hard_keyboard").trim()
                val previousSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val previousFlags = automation.serviceInfo.flags
                val landscape = description.methodName.startsWith("longName")
                val large = landscape || description.methodName.startsWith("numericEntry") || description.methodName.startsWith("savedSetsSheet")
                try {
                    shell("settings put system font_scale ${if (large) "2.0" else "1.0"}")
                    awaitSystemFont(if (large) 2f else 1f)
                    if (landscape) {
                        // The launcher itself locks portrait. Set the emulator's real
                        // window shape before Activity creation; assert the app below.
                        shell("wm size 1920x1080")
                    }
                    shell("settings put secure show_ime_with_hard_keyboard 1")
                    automation.serviceInfo = automation.serviceInfo.apply {
                        flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    }
                    base.evaluate()
                } finally {
                    automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
                    if (landscape) {
                        shell("wm size ${previousSize ?: "reset"}")
                    }
                    if (previousKeyboard in setOf("0", "1")) shell("settings put secure show_ime_with_hard_keyboard $previousKeyboard")
                    else shell("settings delete secure show_ime_with_hard_keyboard")
                    if (previousScale.toFloatOrNull() != null) shell("settings put system font_scale $previousScale")
                    else shell("settings delete system font_scale")
                    awaitSystemFont(previousScale.toFloatOrNull() ?: 1f)
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(nativeWindowRule).around(compose)
    private val fixture = WorkoutEntryFixture()
    @After fun cleanup() = fixture.close()

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe -> FileInputStream(pipe.fileDescriptor).use { String(it.readBytes()) } }

    private fun awaitSystemFont(expected: Float) {
        val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (resources.configuration.fontScale != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertEquals(expected, resources.configuration.fontScale, 0.01f)
    }

    private fun mount(fontScale: Float = 1f, notifications: Boolean = true, targetSets: Int = 12, longName: Boolean = false, savedCount: Int = 0) {
        fixture.seed(targetSets = targetSets, longName = longName)
        if (savedCount > 0) runBlocking(Dispatchers.IO) {
            val exercise = checkNotNull(fixture.container.workoutRepository.getSession(fixture.sessionId)).exercises.single().exercise
            repeat(savedCount) {
                fixture.container.workoutRepository.logSet(sessionId = fixture.sessionId, exerciseId = exercise.id,
                    weightKg = 60.0, reps = 8, rpe = null, isWarmup = false)
            }
        }
        GoldenCapture.mountDevice(compose, fontScale = fontScale) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                Scaffold(bottomBar = { Column {} }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm, restNotificationsEnabledOverride = notifications)
                    }
                }
            }
        }
        compose.waitUntil(15_000) { fixture.vm.uiState.value.draft.weightKg == 60.0 && fixture.vm.uiState.value.session?.sets?.size == savedCount }
    }

    private fun savedSets() = runBlocking(Dispatchers.IO) {
        checkNotNull(fixture.container.workoutRepository.getSession(fixture.sessionId)).sets
    }

    private fun awaitSets(count: Int) {
        compose.waitUntil(15_000) { !fixture.vm.uiState.value.logging && savedSets().size == count }
        compose.waitForIdle()
    }

    private fun captureWindow(state: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // UiAutomation observes the OS compositor, which can lag Compose idle
        // when a separate sheet/dialog/IME window has just attached.
        SystemClock.sleep(750)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        NativeArtifacts.write("frontend-workout-window-$state-api${Build.VERSION.SDK_INT}", bitmap)
        bitmap.recycle()
    }

    @Test fun eightSavesKeepEntryAndCommitPositionsAndPersistExactlyTheirPayloads() {
        mount()
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performScrollTo()
        val before = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot.top
        val buttonBottom = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom
        repeat(8) { index ->
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
            awaitSets(index + 1)
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsDisplayed()
            assertEquals("ordinary logging must retain the entry position", before,
                compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals("receipt must not move commit", buttonBottom,
                compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom, 1f)
        }
        val saved = savedSets()
        assertEquals(8, saved.map { it.id }.distinct().size)
        saved.forEach { assertEquals(60.0, it.weightKg, 0.01); assertEquals(8, it.reps); assertFalse(it.isWarmup) }
        compose.runOnIdle { fixture.vm.onLogReceiptShown() }
        assertEquals(buttonBottom, compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom, 1f)
    }

    @Test fun numericEntrySelectsExistingValueValidatesCancelsAndAppliesAbsoluteDecimal() {
        mount(fontScale = 2f)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performScrollTo().performClick()
        val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
        field.assertIsDisplayed()
        assertEquals(TextRange(0, 2), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        field.performTextReplacement("85,5")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(60.0, fixture.vm.uiState.value.draft.weightKg, 0.01)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performScrollTo().performClick()
        field.performTextReplacement("-5")
        compose.onNodeWithText("Set").assertIsNotEnabled()
        compose.waitUntil(10_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
                val bounds = android.graphics.Rect()
                it.getBoundsInScreen(bounds)
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && bounds.height() > 200
            }
        }
        captureWindow("numeric-invalid-font20")
        field.performTextReplacement("85,5")
        compose.onNodeWithText("Set").performClick()
        compose.waitUntil(5_000) { fixture.vm.uiState.value.draft.weightKg == 85.5 }
        assertTrue(savedSets().isEmpty())
    }

    @Test fun warmupPresetOnlyChangesDraftAndSavingReturnsToWorkingWithClearEffort() {
        mount()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).performScrollTo().performClick()
        compose.onNodeWithTag("workout-warmup-preset-0").performScrollTo().performClick()
        assertTrue(savedSets().isEmpty())
        assertTrue(fixture.vm.uiState.value.draft.isWarmup)
        val warmupWeight = fixture.vm.uiState.value.draft.weightKg
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        assertTrue(savedSets().single().isWarmup)
        assertEquals(warmupWeight, savedSets().single().weightKg, 0.01)
        assertFalse(fixture.vm.uiState.value.draft.isWarmup)
        compose.onNodeWithTag("workout-working-choice").performScrollTo().assertIsSelected()
        compose.onNodeWithText("9").performScrollTo().performClick()
        assertEquals(9, fixture.vm.uiState.value.draft.rpe)
        compose.onNodeWithTag("workout-clear-rpe").performClick()
        assertNull(fixture.vm.uiState.value.draft.rpe)
        compose.onNodeWithText("8").performScrollTo().performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(2)
        assertNull(fixture.vm.uiState.value.draft.rpe)
        assertEquals(8, savedSets().single { !it.isWarmup }.rpe)
        compose.onNodeWithTag(WorkoutTestTags.RPE_HELPER).performScrollTo().performClick()
        compose.onNodeWithText("Effort (RPE)").assertIsDisplayed()
        captureWindow("rpe-help")
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun savedSetsSheetEditsTheChosenRowDeletesAndUndoesWithoutAnotherInsert() {
        mount(fontScale = 2f)
        repeat(2) { compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick(); awaitSets(it + 1) }
        val original = savedSets().first()
        compose.onNodeWithTag("workout-view-sets").performScrollTo().performClick()
        compose.onNodeWithTag("workout-saved-sets-sheet").assertIsDisplayed()
        captureWindow("saved-sets-font20")
        compose.onNodeWithTag(WorkoutTestTags.setOptions(original.id)).performScrollTo().performClick()
        compose.onNodeWithText("Edit set").performClick()
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsDisplayed().performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("70")
        compose.onNodeWithText("Set").performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(15_000) { savedSets().first { it.id == original.id }.weightKg == 70.0 && !fixture.vm.uiState.value.logging }
        assertEquals(2, savedSets().size)
        compose.onNodeWithTag("workout-view-sets").performScrollTo().performClick()
        compose.onNodeWithTag(WorkoutTestTags.setOptions(original.id)).performScrollTo().performClick()
        compose.onNodeWithText("Delete set").performClick()
        awaitSets(1)
        compose.onNodeWithText("Undo").performClick()
        awaitSets(2)
        val restored = savedSets().first { it.id == original.id }
        assertEquals(original.completedAt, restored.completedAt)
        assertEquals(70.0, restored.weightKg, 0.01)
    }

    @Test fun deniedNotificationsRetainIdleTimingAndCompletedExerciseActions() {
        mount(notifications = false, targetSets = 1)
        compose.onNodeWithTag("workout-companion-clock").performClick()
        compose.onNodeWithTag("workout-rest-sheet-plus").performScrollTo().performClick()
        compose.onNodeWithText("Start rest").performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.vm.restTimerState.value.running }
        compose.runOnIdle { fixture.vm.skipRest() }
        compose.waitUntil(5_000) { !fixture.vm.restTimerState.value.running }
        compose.onNodeWithTag("workout-companion-clock").performClick()
        compose.onNodeWithTag("workout-sheet-start-set-clock").performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.vm.setStopwatch.value.running }
        compose.onNodeWithTag("workout-companion-clock").performClick()
        compose.onNodeWithText("Stop timing").performClick()
        compose.waitUntil(5_000) { !fixture.vm.setStopwatch.value.running }
        assertTrue(savedSets().isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed()
    }

    @Test fun longNameSavedSheetKeepsEveryRowAndExtraSetReachableInLandscapeAtSystemFontTwo() {
        mount(fontScale = 2f, targetSets = 2, longName = true, savedCount = 10)
        val bounds = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode().boundsInRoot
        assertTrue("Actual app window is landscape", bounds.width > bounds.height)
        val sets = savedSets()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag("workout-view-sets"))
        compose.onNodeWithTag("workout-view-sets").performScrollTo().performClick()
        captureWindow("long-sheet-landscape-font20")
        val list = compose.onNodeWithTag("workout-saved-sets-list")
        for (set in listOf(sets.first(), sets.last())) {
            list.performScrollToNode(hasTestTag(WorkoutTestTags.setOptions(set.id)))
            compose.onNodeWithTag(WorkoutTestTags.setOptions(set.id)).assertIsDisplayed().performClick()
            compose.onNodeWithText("Edit set").assertIsDisplayed()
            compose.onNodeWithText("Delete set").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
        }
        list.performScrollToNode(androidx.compose.ui.test.hasText("Add another set"))
        compose.onNodeWithText("Add another set").assertIsDisplayed()
        captureWindow("long-sheet-last-row-landscape-font20")
        compose.onNodeWithText("Add another set").performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        assertEquals(10, savedSets().size)
    }
}
