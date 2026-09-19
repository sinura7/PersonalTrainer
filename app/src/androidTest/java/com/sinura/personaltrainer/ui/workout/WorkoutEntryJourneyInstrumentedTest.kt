package com.sinura.personaltrainer.ui.workout

import android.os.Build
import android.graphics.BitmapFactory
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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
                val large = landscape || description.methodName.startsWith("numericEntry") || description.methodName.startsWith("savedSetsSheet") || description.methodName.startsWith("switcher")
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
        SystemClock.sleep(android.view.ViewConfiguration.getDoubleTapTimeout().toLong() + 20)
    }

    /** The full Edit/Delete sheet shares the per-set tag with the strip's chips; address the sheet's row. */
    private fun sheetOptionsFor(setId: String) =
        hasTestTag(WorkoutTestTags.setOptions(setId)) and hasAnyAncestor(hasTestTag(WorkoutTestTags.SAVED_SETS_SHEET))

    /** The floor is a lazy list: an item below the fold is not composed until scrolled to. */
    private fun scrollContentTo(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    /** The saved-sets sheet is lazy too. */
    private fun sheetRowFor(setId: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("workout-saved-sets-list").performScrollToNode(sheetOptionsFor(setId))
        return compose.onNode(sheetOptionsFor(setId))
    }

    private fun captureWindow(state: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // UiAutomation observes the OS compositor, which can lag Compose idle
        // when a separate sheet/dialog/IME window has just attached.
        SystemClock.sleep(750)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Older platform UiAutomation can return null after a real display-size
        // override. Shell screencap still captures the same OS windows/IME; a
        // missing or invalid PNG must fail, never silently omit visual evidence.
        val bitmap = automation.takeScreenshot() ?: automation.executeShellCommand("screencap -p").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use(BitmapFactory::decodeStream)
        }
        checkNotNull(bitmap) { "Both native-window screenshot paths failed for $state" }
        NativeArtifacts.write("frontend-workout-window-$state-api${Build.VERSION.SDK_INT}", bitmap)
        bitmap.recycle()
    }

    @Test fun eightSavesKeepEntryAndCommitPositionsAndPersistExactlyTheirPayloads() {
        mount()
        scrollContentTo(WorkoutTestTags.WEIGHT_STEPPER)
        val before = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot.top
        val buttonBottom = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom
        repeat(8) { index ->
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
            awaitSets(index + 1)
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsDisplayed()
            assertEquals("ordinary logging must retain the entry position", before,
                compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals("the saved chip must not move commit", buttonBottom,
                compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom, 1f)
        }
        val saved = savedSets()
        assertEquals(8, saved.map { it.id }.distinct().size)
        saved.forEach { assertEquals(60.0, it.weightKg, 0.01); assertEquals(8, it.reps); assertFalse(it.isWarmup) }
        // The receipt is the just-saved chip in the set history: it reads as saved while the
        // receipt is live and as logged once the receipt has been shown.
        val receipt = checkNotNull(fixture.vm.logReceipt.value) { "the eighth save must leave a live receipt" }
        val savedChip = hasTestTag(WorkoutTestTags.setChip(receipt.setId))
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(savedChip)
        compose.onNode(savedChip and hasContentDescription(value = "saved", substring = true)).assertIsDisplayed()
        compose.runOnIdle { fixture.vm.onLogReceiptShown() }
        compose.onNode(savedChip and hasContentDescription(value = "logged", substring = true)).assertIsDisplayed()
        assertEquals(buttonBottom, compose.onNodeWithTag(WorkoutTestTags.LOG_SET).fetchSemanticsNode().boundsInRoot.bottom, 1f)
    }

    @Test fun numericEntrySelectsExistingValueValidatesCancelsAndAppliesAbsoluteDecimal() {
        mount(fontScale = 2f)
        scrollContentTo(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        val field = compose.onNodeWithTag(NumberEntryTags.FIELD)
        field.assertIsDisplayed()
        assertEquals(TextRange(0, 2), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        field.performTextReplacement("85,5")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(60.0, fixture.vm.uiState.value.draft.weightKg, 0.01)
        scrollContentTo(WorkoutTestTags.WEIGHT_STEPPER).performClick()
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
        scrollContentTo(WorkoutTestTags.WARMUP_CHIP).performClick()
        // A warm-up has no RPE track, only the reason it is blank.
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.RPE_WARMUP_REASON))
        compose.onNodeWithTag(WorkoutTestTags.RPE_WARMUP_REASON).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertDoesNotExist()
        scrollContentTo("workout-warmup-preset-0").performClick()
        assertTrue(savedSets().isEmpty())
        assertTrue(fixture.vm.uiState.value.draft.isWarmup)
        val warmupWeight = fixture.vm.uiState.value.draft.weightKg
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        assertTrue(savedSets().single().isWarmup)
        assertEquals(warmupWeight, savedSets().single().weightKg, 0.01)
        assertFalse(fixture.vm.uiState.value.draft.isWarmup)
        scrollContentTo(WorkoutTestTags.WORKING_CHIP).assertIsSelected()
        scrollContentTo(WorkoutTestTags.rpeChoice(9)).performClick()
        assertEquals(9, fixture.vm.uiState.value.draft.rpe)
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).performClick()
        assertNull(fixture.vm.uiState.value.draft.rpe)
        scrollContentTo(WorkoutTestTags.rpeChoice(8)).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(2)
        assertNull(fixture.vm.uiState.value.draft.rpe)
        assertEquals(8, savedSets().single { !it.isWarmup }.rpe)
        scrollContentTo(WorkoutTestTags.RPE_HELPER).performClick()
        compose.onNodeWithText("Effort (RPE)").assertIsDisplayed()
        captureWindow("rpe-help")
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun savedSetsSheetEditsTheChosenRowDeletesAndUndoesWithoutAnotherInsert() {
        mount(fontScale = 2f)
        repeat(2) { compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick(); awaitSets(it + 1) }
        val original = savedSets().first()
        scrollContentTo(WorkoutTestTags.VIEW_SETS).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        captureWindow("saved-sets-font20")
        sheetRowFor(original.id).performClick()
        compose.onNodeWithText("Edit set").performClick()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.editingSetId == original.id }
        // Cancel edit stands in the dock's companion slot while a saved set is being
        // revised, and returns to entry without writing.
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.editingSetId == null }
        assertEquals(60.0, savedSets().first { it.id == original.id }.weightKg, 0.01)
        scrollContentTo(WorkoutTestTags.VIEW_SETS).performClick()
        sheetRowFor(original.id).performClick()
        compose.onNodeWithText("Edit set").performClick()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.editingSetId == original.id }
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsDisplayed().performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("70")
        compose.onNodeWithText("Set").performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(15_000) { savedSets().first { it.id == original.id }.weightKg == 70.0 && !fixture.vm.uiState.value.logging }
        assertEquals(2, savedSets().size)
        scrollContentTo(WorkoutTestTags.VIEW_SETS).performClick()
        sheetRowFor(original.id).performClick()
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
        // Denied alerts put the honesty row in the companion slot; the compact clock beside it
        // is the way into every timer control.
        compose.onNodeWithTag(WorkoutTestTags.NOTIF_RECOVERY).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithTag("workout-rest-sheet-plus").performScrollTo().performClick()
        compose.onNodeWithText("Start rest").performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.vm.restTimerState.value.running }
        compose.runOnIdle { fixture.vm.skipRest() }
        compose.waitUntil(5_000) { !fixture.vm.restTimerState.value.running }
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        scrollContentTo(WorkoutTestTags.SHEET_START_SET_CLOCK).performClick()
        compose.waitUntil(5_000) { fixture.vm.setStopwatch.value.running }
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithText("Stop timing").performClick()
        compose.waitUntil(5_000) { !fixture.vm.setStopwatch.value.running }
        assertTrue(savedSets().isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitSets(1)
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed()
    }

    @Test fun switcherNamesProgressAndRequiresConfirmationToInterruptTimingAtLargeText() {
        mount(fontScale = 2f, longName = true)
        val original = checkNotNull(fixture.vm.uiState.value.selectedExerciseId)
        val next = fixture.addNextExercise()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.session?.exercises?.size == 2 }
        compose.runOnIdle { fixture.vm.startSetStopwatch() }
        compose.waitUntil(5_000) { fixture.vm.setStopwatch.value.running }
        // The exercise identity is the way into the switcher.
        scrollContentTo(WorkoutTestTags.liftCard(original)).performClick()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(original)).assertIsDisplayed()
        captureWindow("switcher-font20")
        compose.onNodeWithTag("workout-switcher-list").performScrollToNode(hasTestTag(WorkoutTestTags.liftSwitcherRow(next.id)))
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(next.id)).assertIsDisplayed().performClick()
        compose.onNodeWithText(com.sinura.personaltrainer.domain.SetStopwatchCopy.SWITCH_TITLE).assertIsDisplayed()
        assertEquals(original, fixture.vm.uiState.value.selectedExerciseId)
        captureWindow("switch-timing-confirm-font20")
        compose.onNodeWithText(com.sinura.personaltrainer.domain.SetStopwatchCopy.SWITCH_CONFIRM).performClick()
        compose.waitUntil(15_000) { fixture.vm.uiState.value.selectedExerciseId == next.id }
        assertFalse(fixture.vm.setStopwatch.value.running)
        assertTrue(savedSets().isEmpty())
    }

    @Test fun longNameIdleLandscapeKeepsTimerAccessAndCustomEntryUsesOneOverlay() {
        mount(fontScale = 2f)
        val planned = fixture.vm.restTimerState.value.totalSeconds
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed().performClick()
        scrollContentTo(WorkoutTestTags.SHEET_START_SET_CLOCK).assertIsDisplayed()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertDoesNotExist()
        compose.onNodeWithText("Custom rest").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("180")
        compose.waitUntil(10_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
                val bounds = android.graphics.Rect()
                it.getBoundsInScreen(bounds)
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && bounds.height() > 200
            }
        }
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
        captureWindow("custom-rest-landscape-font20")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertIsDisplayed()
        assertEquals(planned, fixture.vm.restTimerState.value.totalSeconds)
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("2:15")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { fixture.vm.restTimerState.value.totalSeconds == 135 }
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertDoesNotExist()
        assertFalse(fixture.vm.restTimerState.value.running)
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).performClick()
        compose.onNodeWithText("Start rest").performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.vm.restTimerState.value.running }
        assertTrue(savedSets().isEmpty())
    }

    @Test fun longNameSavedSheetKeepsEveryRowAndExtraSetReachableInLandscapeAtSystemFontTwo() {
        mount(fontScale = 2f, targetSets = 2, longName = true, savedCount = 10)
        val bounds = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode().boundsInRoot
        assertTrue("Actual app window is landscape", bounds.width > bounds.height)
        val sets = savedSets()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.VIEW_SETS))
        scrollContentTo(WorkoutTestTags.VIEW_SETS).performClick()
        captureWindow("long-sheet-landscape-font20")
        val list = compose.onNodeWithTag("workout-saved-sets-list")
        for (set in listOf(sets.first(), sets.last())) {
            list.performScrollToNode(hasTestTag(WorkoutTestTags.setOptions(set.id)))
            compose.onNode(sheetOptionsFor(set.id)).assertIsDisplayed().performClick()
            compose.onNodeWithText("Edit set").assertIsDisplayed()
            compose.onNodeWithText("Delete set").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
        }
        list.performScrollToNode(hasText("Add another set"))
        val anotherInSheet = hasText("Add another set") and hasAnyAncestor(hasTestTag(WorkoutTestTags.SAVED_SETS_SHEET))
        compose.onNode(anotherInSheet).assertIsDisplayed()
        captureWindow("long-sheet-last-row-landscape-font20")
        compose.onNode(anotherInSheet).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        assertEquals(10, savedSets().size)
    }
}
