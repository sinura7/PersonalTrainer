package com.sinura.personaltrainer.ui.workout

import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutWeightCopy
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WorkoutEntryLayoutInstrumentedTest(
    private val width: Int,
    private val height: Int,
    private val font: Float,
    private val scenario: String,
) {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()
    @After fun cleanup() = fixture.close()

    @Test fun entryAndCommitRemainReadableAndReachable() {
        val exerciseId = when (scenario) {
            "bodyweight" -> "ex-push-up"
            "added" -> "ex-pull-up"
            "assisted" -> "ex-assisted-pull-up"
            "hold" -> "ex-plank"
            else -> "ex-barbell-back-squat"
        }
        fixture.seed(exerciseId, longName = scenario == "long")
        GoldenCapture.mountViewport(
            compose = compose, width = width.dp, height = height.dp, fontScale = font,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp,
        ) {
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.KG,
                LocalLayoutDirection provides if (scenario.endsWith("rtl")) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                // Match AppNav's parent Scaffold. On the workout route its bottom
                // Column is empty; it still applies and consumes system insets.
                Scaffold(bottomBar = { Column {} }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm, restNotificationsEnabledOverride = true)
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            fixture.vm.uiState.value.loadState == SessionLoadState.FOUND &&
                fixture.vm.uiState.value.draft.weightKg == fixture.expectedWeightKg && fixture.vm.uiState.value.canLog
        }
        if (scenario.startsWith("warmup")) compose.runOnIdle { fixture.vm.setWarmup(true) }
        if (scenario == "zero") compose.runOnIdle { fixture.vm.setWeight(0.0) }
        if (scenario == "large") compose.runOnIdle { fixture.vm.setWeight(99999.99) }
        if (scenario == "error") compose.runOnIdle { fixture.vm.skipForNow() }
        if (scenario == "latest" || scenario == "undo") {
            compose.runOnIdle { fixture.vm.logSet() }
            compose.waitUntil(15_000) { fixture.vm.uiState.value.session?.sets?.size == 1 && !fixture.vm.uiState.value.logging }
            compose.runOnIdle { fixture.vm.skipRest(); fixture.vm.onLogReceiptShown() }
            if (scenario == "undo") {
                compose.runOnIdle { fixture.vm.deleteSet(fixture.vm.uiState.value.session!!.sets.single().id) }
                compose.waitUntil(15_000) { fixture.vm.uiState.value.session?.sets?.isEmpty() == true && fixture.vm.undoEntries.value.isNotEmpty() }
            }
        }
        compose.waitForIdle()
        SystemClock.sleep(750) // Await the catalog still's first decode, not an animation.
        GoldenCapture.awaitViewport(compose, width, height)
        val rootNode = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
        val density = rootNode.layoutInfo.density.density
        val root = rootNode.boundsInRoot
        assertEquals(width.toFloat(), root.width / density, 1f)
        assertEquals(height.toFloat(), root.height / density, 1f)
        val commit = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val name = "frontend-workout-${width}x$height-font${(font * 10).toInt()}-$scenario-api${Build.VERSION.SDK_INT}"
        val image = GoldenCapture.capture(compose)
        if (Build.VERSION.SDK_INT == 29) GoldenImageAssert.assertMatches(name, image)
        else NativeArtifacts.write(name, image.asAndroidBitmap())
        // Compose rounds dp constraints to physical pixels. Compare the same
        // integer floor; 72 dp at density 2.28125 correctly renders as 164 px.
        val minimumCommitPixels = with(rootNode.layoutInfo.density) { 72.dp.roundToPx() }
        assertTrue("72 dp commit floor: pixels=${commit.height}, expected=$minimumCommitPixels", commit.height >= minimumCommitPixels)
        assertTrue("commit remains in viewport", commit.bottom <= root.bottom + 1)
        val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertTrue("scroll content clears dock", content.bottom <= compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).fetchSemanticsNode().boundsInRoot.top + 1)
        assertTrue("entry retains usable scrolling space", content.height / density >= 48)
        if (width == 360 && height == 800 && font == 1f && scenario == "working") {
            // The baseline profile shows the whole log loop without a scroll: the header's
            // progress line, identity with the set-type toggle, the stats row, both hero
            // numerals and the RPE track.
            compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE).assertIsDisplayed().assertTextEquals("Exercise 1 of 1 · 0 of 12 sets")
            compose.onNodeWithTag(WorkoutTestTags.SET_TYPE).assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.STATS_ROW).assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertIsDisplayed()
        }
        // The companion slot carries the state that needs the room: a failed action's
        // details, or the undo offer for a deleted set.
        if (scenario == "error") compose.onNodeWithTag(WorkoutTestTags.ERROR_DETAILS).assertIsDisplayed()
        if (scenario == "undo") compose.onNodeWithText("Undo").assertIsDisplayed()
        // Essential fields remain reachable even if large text deliberately puts
        // supporting content below the initial viewport.
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.SET_ENTRY))
        val entryTag = when (scenario) {
            "bodyweight" -> WorkoutTestTags.REPS_STEPPER
            "hold" -> WorkoutTestTags.HOLD_STEPPER
            else -> WorkoutTestTags.WEIGHT_STEPPER
        }
        compose.onNodeWithTag(entryTag).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
        if (scenario == "large") {
            // The hero numeral shows the number alone; its unit sits in the column's label.
            val number = WorkoutWeightCopy.number(fixture.vm.uiState.value.draft.weightKg, WeightUnit.KG)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(number) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse("entered value is not clipped", layouts.any { it.hasVisualOverflow })
        }
        if (scenario == "latest") {
            // The saved set is a chip in the set history; once its receipt has been shown it
            // reads as logged, not saved.
            val savedChip = hasTestTag(WorkoutTestTags.setOptions(fixture.vm.uiState.value.session!!.sets.single().id))
            compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(savedChip)
            compose.onNode(savedChip and hasContentDescription(value = "logged", substring = true)).assertIsDisplayed()
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-{3}")
        fun profiles(): List<Array<Any>> = buildList {
            for ((w, h) in listOf(360 to 640, 360 to 800, 412 to 840, 600 to 840, 640 to 360)) {
                for (scale in listOf(1f, 1.6f, 2f)) add(arrayOf(w, h, scale, "working"))
            }
            add(arrayOf(360, 800, 1f, "warmup"))
            add(arrayOf(360, 640, 2f, "warmup"))
            add(arrayOf(360, 640, 2f, "large"))
            for (scenario in listOf("bodyweight", "added", "assisted", "hold", "zero", "long", "error", "undo", "latest")) {
                for (scale in listOf(1f, 2f)) add(arrayOf(360, 800, scale, scenario))
            }
            add(arrayOf(360, 800, 1f, "working-rtl"))
            add(arrayOf(360, 640, 2f, "warmup-rtl"))
        }
    }
}
