package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.coach.AddASetTrace
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.coach.TempoWhySheetCopy
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Actual Room offer and sheet actions, with the modal window's real system font scale. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class AddASetWhyJourneyRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private val runId = UUID.randomUUID().toString()
    private lateinit var deps: FakeAppDependencies

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.setTrainingGoal(TrainingGoal.HYPERTROPHY)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After fun tearDown() {
        try {
            runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
            viewModels.clear()
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.0f)
    fun smallPhoneFont10() = verifyJourney("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallPhoneFont16() = verifyJourney("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
    fun smallPhoneLargestTextShowsTruthAndKeepsBothDraftActionsReachable() = verifyJourney("360x640-font20", 2f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.0f)
    fun largerPhoneFont10() = verifyJourney("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun largerPhoneFont16() = verifyJourney("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2.0f)
    fun largerPhoneFont20() = verifyJourney("412x840-font20", 2f)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.0f)
    fun landscapeFont10() = verifyJourney("640x360-land-font10", 1f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = verifyJourney("640x360-land-font16", 1.6f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2.0f)
    fun landscapeLargestTextShowsTruthAndKeepsBothDraftActionsReachable() = verifyJourney("640x360-land-font20", 2f)

    private fun verifyJourney(profile: String, expectedFont: Float) = runBlocking {
        val actualFont = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        assertEquals(expectedFont, actualFont, 0.0f)
        deps.preferencesRepository.coachPreferences.awaitFirst { it.goal == TrainingGoal.HYPERTROPHY }
        val vm = openLegExtension(deps, viewModels, targetSets = 2, loggedSets = listOf(
            TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 6),
            TestSetInput(weightKg = FLOOR_KG70, reps = 10, rpe = 8),
        ))
        vm.awaitState(FLOOR_LIFT_READY)
        vm.tempoCoachTip.awaitFirst { it is TempoCoachTip.AddASet }
        vm.setWeight(87.5)
        vm.setReps(12)
        vm.setRpe(8)
        vm.awaitState { !it.entryLocked && it.draft.weightKg == 87.5 && it.draft.reps == 12 && it.draft.rpe == 8 }
        val tip = vm.tempoCoachTip.awaitFirst { it is TempoCoachTip.AddASet } as TempoCoachTip.AddASet
        val facts = tip.trace.facts.associate { it.name to it.value }
        assertEquals(AddASetTrace.RULE_ID, tip.trace.ruleId)
        assertEquals(AddASetTrace.ACTION, tip.trace.action)
        assertEquals("4", facts["readinessCount"])
        assertEquals("7.0", facts["meanRpe"])
        assertEquals("false", facts["comparisonAvailable"])
        assertEquals("true", facts["performanceSignal"])
        assertEquals("false", facts["trendSignal"])
        assertEquals("false", facts["blockSignal"])
        val before = vm.uiState.value
        val stored = checkNotNull(deps.workoutRepository.getSession(checkNotNull(before.session).id))
        val rest = deps.restTimerStore.current()
        compose.showFloor(fontScale = actualFont) {
            Box(Modifier.fillMaxSize()) {
                ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = vm, restNotificationsEnabledOverride = true)
            }
        }
        compose.waitForIdle()
        openWhy()
        renderedText(profile, "title", "Why Tempo suggests an extra set", TextPrimary, false)
        renderedText(profile, "summary", "Your planned sets are complete. 4 of 5 readiness checks support one extra set.", TextSecondary)
        val decisions = listOf(
            TempoWhySheetCopy.LABEL_LAST_SET to checkNotNull(facts["lastSet"]),
            TempoWhySheetCopy.LABEL_ADD_EFFORT to "Average RPE 7 · within the 7.5 guide.",
            TempoWhySheetCopy.LABEL_ADD_COMPLETION to "No failed or cut-short sets flagged.",
            TempoWhySheetCopy.LABEL_ADD_COMPARISON to "No earlier session to compare.",
            TempoWhySheetCopy.LABEL_ADD_WEEKLY to "Estimate 2 sets · below the 10-set guide.",
            TempoWhySheetCopy.LABEL_ADD_TIMING to "No recent-volume or block-week support.",
        )
        decisions.forEachIndexed { index, (label, value) ->
            renderedText(profile, "row-$index-label", label, TextTertiary)
            renderedText(profile, "row-$index-value", value, TextPrimary)
        }
        reachableAction(WorkoutTestTags.TEMPO_WHY_USE, Metrics.logFloorCommit.value, actualFont)
        reachableAction(WorkoutTestTags.TEMPO_WHY_KEEP, Metrics.touchMin.value, actualFont)
        renderedText(profile, "keep-action", SetMicroRecCopy.KEEP_MY_NUMBERS, TextSecondary, false)
        renderedText(profile, "use-action", SetMicroRecCopy.USE_SUGGESTION, Pit, false)
        reachableAction(WorkoutTestTags.TEMPO_WHY_KEEP, Metrics.touchMin.value, actualFont).performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertDoesNotExist()
        assertEquals(before.draft, vm.uiState.value.draft)
        assertFalse(vm.extraSetRequested.value)
        assertEquals(stored, deps.workoutRepository.getSession(stored.id))
        assertEquals(rest, deps.restTimerStore.current())

        openWhy()
        reachableAction(WorkoutTestTags.TEMPO_WHY_KEEP, Metrics.touchMin.value, actualFont)
        reachableAction(WorkoutTestTags.TEMPO_WHY_USE, Metrics.logFloorCommit.value, actualFont)
        renderedText(profile, "use-before-touch", SetMicroRecCopy.USE_SUGGESTION, Pit, false)
        reachableAction(WorkoutTestTags.TEMPO_WHY_USE, Metrics.logFloorCommit.value, actualFont).performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertDoesNotExist()
        val after = vm.awaitState { it.draft.weightKg == tip.seedRec.nextWeightKg &&
            it.draft.reps == tip.seedRec.nextReps && it.draft.rpe == tip.seedRec.nextRpe }
        assertTrue(vm.extraSetRequested.value)
        assertEquals(before.selectedExerciseId, after.selectedExerciseId)
        assertEquals(stored, deps.workoutRepository.getSession(stored.id))
        assertEquals(stored.sets.map { it.id }, after.session?.sets?.map { it.id })
        assertEquals(rest, deps.restTimerStore.current())
        assertFalse(deps.restTimerStore.current().running)
    }

    private fun openWhy() {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.MICRO_REC_WHY))
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
    }

    private fun sheetText(text: String) = compose.onNode(
        matcher = hasText(text) and hasAnyAncestor(hasTestTag(WorkoutTestTags.TEMPO_WHY_SHEET)),
        useUnmergedTree = true,
    )

    /** Native pixels from the actual modal, not the activity window behind it. */
    private fun renderedText(profile: String, stage: String, text: String, color: Color, scroll: Boolean = true) {
        val node = sheetText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val target = node.fetchSemanticsNode()
        val layout = node.textLayout()
        // GetTextLayoutResult can rebuild short text at a wider paragraph width.
        // Centered button text inherits that paragraph's offset; its line span,
        // rather than its right-edge coordinate, must fit the actual text box.
        repeat(layout.lineCount) { line ->
            assertFalse("$stage text must not be ellipsized", layout.isLineEllipsized(line))
            val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue("$stage glyphs fit their actual text box", lineWidth <= target.size.width + 1f)
        }
        assertEquals("$stage keeps every character", layout.layoutInput.text.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        val frame = modalFrame()
        try {
            val bounds = target.boundsInWindow
            val ink = frame.count(bounds, color)
            val out = File("build/screen-renders/ux23-add-a-set-why/$runId/$profile").also {
                check(it.exists() || it.mkdirs()) { "Cannot create Why evidence directory $it" }
            }
            out.resolve("$stage-modal-window.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $profile/$stage modal" }
            }
            out.resolve("$stage-geometry.txt").writeText(
                "text=$text\nfont=${target.layoutInfo.density.fontScale}\nbounds=$bounds\nlayout=${target.size}\n" +
                    "modal=${frame.width}x${frame.height}\ninkPixels=$ink\n",
            )
            println("UX23_ADD_A_SET_WHY_RENDER run=$runId profile=$profile stage=$stage font=${target.layoutInfo.density.fontScale} " +
                "bounds=$bounds layout=${target.size} modal=${frame.width}x${frame.height} inkPixels=$ink")
            assertTrue("$profile/$stage has actual text ink in its modal window: $ink pixels", ink >= 20)
        } finally {
            frame.recycle()
        }
    }

    private fun modalFrame(): Bitmap = compose.runOnIdle {
        val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
        check(decor.width > 0 && decor.height > 0) { "Why modal must have a drawable window" }
        Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
    }

    /** Full targets, not just a visible clipped remnant, fit the actual Android modal. */
    private fun reachableAction(tag: String, minDp: Float, expectedFont: Float): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        val target = node.fetchSemanticsNode()
        val bounds = target.boundsInWindow
        val sheet = compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).fetchSemanticsNode().boundsInWindow
        val density = target.layoutInfo.density.density
        assertEquals("$tag uses its actual system font", expectedFont, target.layoutInfo.density.fontScale, 0.01f)
        val modal = compose.runOnIdle {
            val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            decor.width to decor.height
        }
        assertTrue("$tag retains its full layout target", bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f)
        assertTrue("$tag fits its sheet", bounds.left >= sheet.left - 1f && bounds.right <= sheet.right + 1f &&
            bounds.top >= sheet.top - 1f && bounds.bottom <= sheet.bottom + 1f)
        assertTrue("$tag fits its modal window", bounds.left >= -1f && bounds.top >= -1f &&
            bounds.right <= modal.first + 1f && bounds.bottom <= modal.second + 1f)
        assertTrue("$tag keeps its minimum touch height", bounds.height / density + 0.01f >= minDp)
        assertTrue("$tag keeps its minimum layout height", target.size.height / density + 0.01f >= minDp)
        println("UX23_ADD_A_SET_WHY_ACTION run=$runId tag=$tag font=$expectedFont bounds=$bounds layout=${target.size} " +
            "sheet=$sheet modal=$modal minimumHeightDp=$minDp")
        return node.assertIsDisplayed().assertIsEnabled()
    }
}
