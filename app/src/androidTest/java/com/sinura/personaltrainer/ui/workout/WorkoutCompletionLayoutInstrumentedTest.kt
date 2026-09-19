package com.sinura.personaltrainer.ui.workout

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Actual workout route, real Room data; completed sets are seeded without clock jobs. */
@RunWith(Parameterized::class)
class WorkoutCompletionLayoutInstrumentedTest(
    private val width: Int, private val height: Int, private val font: Float, private val scenario: String,
) {
    @get:Rule val compose = createComposeRule()
    private val fixture = WorkoutEntryFixture()
    @After fun cleanup() = fixture.close()

    @Test fun primaryActionAndRecoveryRemainReachable() {
        fixture.seed(targetSets = 1)
        if (scenario == "next" || scenario == "removed-owner") fixture.addNextExercise(longName = true, targetSets = 3)
        val repo = fixture.container.workoutRepository
        val sessionId = fixture.sessionId
        var setId: String? = null
        runBlocking(Dispatchers.IO) {
            val session = checkNotNull(repo.getSession(sessionId))
            val first = session.exercises.first()
            if (scenario.startsWith("removed")) {
                val command = WorkoutSetSave(sessionId, first.exercise.id, "removed-pending", 1_700_000_000_000,
                    WorkoutSetValues(60.0, 8, null, false, null))
                val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
                SavedStateWorkoutSave(handle).write(command)
                repo.removeExerciseFromSession(sessionId, first.id)
                fixture.recreate(handle)
            } else {
                val result = repo.logSet(sessionId, first.exercise.id, 60.0, 8, null, false)
                setId = result.setId
            }
        }
        GoldenCapture.mountViewport(
            compose = compose, width = width.dp, height = height.dp, fontScale = font,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp,
        ) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                Scaffold(bottomBar = { Column {} }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = fixture.vm,
                            restNotificationsEnabledOverride = scenario != "edit-denied")
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            val action = fixture.vm.primaryAction.value
            action.enabled && action.kind == when {
                scenario.startsWith("removed") -> WorkoutPrimaryKind.REVIEW_SAVE
                scenario == "next" -> WorkoutPrimaryKind.NEXT_EXERCISE
                else -> WorkoutPrimaryKind.FINISH
            }
        }
        if (scenario == "edit-denied") {
            compose.runOnIdle { fixture.vm.editSet(checkNotNull(setId)) }
            compose.waitUntil(15_000) { fixture.vm.primaryAction.value.kind == WorkoutPrimaryKind.SAVE_CHANGES }
        }
        if (scenario == "extra") {
            compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).performClick()
            compose.waitUntil(15_000) { fixture.vm.primaryAction.value.kind == WorkoutPrimaryKind.LOG_SET }
        }
        SystemClock.sleep(750)
        GoldenCapture.awaitViewport(compose, width, height)
        val tag = when (scenario) {
            "next" -> WorkoutTestTags.NEXT
            "finish" -> WorkoutTestTags.DOCK_FINISH
            else -> WorkoutTestTags.LOG_SET
        }
        // The commit says its verb on the first line and, when the tap writes a set, the
        // payload on the second.
        val action = fixture.vm.primaryAction.value
        // The commit keeps its short verb in every orientation; the next lift's name is its
        // capped supporting line, checked below.
        val verb = action.verb(includeNextName = false)
        val setPayload = action.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED)
        val button = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().assertTextContains(verb).fetchSemanticsNode()
        val root = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
        val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertEquals(width.toFloat(), root.boundsInRoot.width / root.layoutInfo.density.density, 1f)
        assertEquals(height.toFloat(), root.boundsInRoot.height / root.layoutInfo.density.density, 1f)
        assertTrue(button.boundsInRoot.bottom <= root.boundsInRoot.bottom + 1)
        assertTrue("Scrollable content must retain a full touch target", content.height / root.layoutInfo.density.density >= 48)
        fun layoutsOf(text: String): List<TextLayoutResult> {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(matcher = hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            return layouts
        }
        val verbLayouts = layoutsOf(verb)
        val payloadLayouts = setPayload?.let { layoutsOf(it) }
        val name = "frontend-completion-${width}x$height-font${(font * 10).toInt()}-$scenario-api${Build.VERSION.SDK_INT}"
        val image = GoldenCapture.capture(compose)
        if (Build.VERSION.SDK_INT == 29) GoldenImageAssert.assertMatches(name, image)
        else NativeArtifacts.write(name, image.asAndroidBitmap())
        // Centered Text may keep a paragraph's maximum constraint while its
        // measured width shrinks to content. Compare actual line widths and
        // visible characters, not multiParagraph.width/didOverflowWidth.
        fun assertNotTruncated(label: String, text: String, layouts: List<TextLayoutResult>) {
            assertFalse("$label must not truncate", layouts.any { layout ->
                layout.didOverflowHeight || (0 until layout.lineCount).any {
                    layout.isLineEllipsized(it) || layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f
                } || layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) != text.length
            })
        }
        assertNotTruncated("primary verb", verb, verbLayouts)
        if (setPayload != null && payloadLayouts != null) assertNotTruncated("primary payload", setPayload, payloadLayouts)
        if (scenario == "next") {
            // The commit speaks the next lift's name in every orientation. Portrait also draws
            // it on the capped supporting line; landscape has no room for a second line.
            val nextName = checkNotNull(action.nextName)
            compose.onNodeWithTag(tag).assert(hasContentDescription(nextName, substring = true))
            val drawnName = compose.onNode(matcher = hasText(nextName) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            if (width <= height) drawnName.assertIsDisplayed() else drawnName.assertDoesNotExist()
        }

        if (scenario == "edit-denied") {
            // With rest alerts denied, an edit in progress still owns the companion slot:
            // Cancel edit outranks the notification honesty row, which returns once the
            // edit stands down. The identity and the commit's verb announce the edit too.
            compose.onNodeWithTag(WorkoutTestTags.liftCard(checkNotNull(fixture.vm.uiState.value.selectedExerciseId)))
                .assert(hasContentDescription(value = "Editing saved set", substring = true))
            assertEquals("Save changes", verb)
            compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertIsDisplayed().performClick()
            compose.waitUntil(15_000) { fixture.vm.uiState.value.editingSetId == null }
            compose.onNodeWithTag(WorkoutTestTags.NOTIF_RECOVERY).assertIsDisplayed()
            assertEquals(60.0, runBlocking(Dispatchers.IO) { repo.getSession(sessionId)!!.sets.single().weightKg }, 0.01)
        }
        if (scenario.startsWith("removed")) {
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithText("Return to entry").performClick()
            compose.waitUntil(15_000) { !fixture.vm.uiState.value.save.pending }
            val expected = if (scenario == "removed-owner") WorkoutPrimaryKind.LOG_SET else WorkoutPrimaryKind.ADD_EXERCISE
            compose.waitUntil(15_000) { fixture.vm.primaryAction.value.kind == expected && fixture.vm.primaryAction.value.enabled }
            assertTrue(runBlocking(Dispatchers.IO) { repo.getSession(sessionId)!!.sets.isEmpty() })
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-{3}")
        fun profiles(): List<Array<Any>> = buildList {
            for (scenario in listOf("next", "finish", "extra", "edit-denied", "removed-owner", "removed-last")) {
                add(arrayOf(360, 800, 1f, scenario))
                add(arrayOf(360, 640, 2f, scenario))
            }
            add(arrayOf(412, 840, 1.6f, "next"))
            add(arrayOf(600, 840, 2f, "next"))
            add(arrayOf(640, 360, 2f, "next"))
        }
    }
}
