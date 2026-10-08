package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Real floor, ViewModel and Room: numeric entry, effort and Apply must receive the
 * intended touch in constrained layouts. Semantics visibility alone does not prove
 * this: a pinned coach previously covered the target while it remained "displayed".
 * Every touched floor control is measured against its full layout size and the
 * unobstructed viewport before a normal touch. No dismiss, direct draft setter or
 * semantics-click shortcut repairs a covered control.
 *
 * Numeric confirmation retains FloorTestKit's documented Robolectric keypad clock /
 * Set-action seam. Android window/IME and phone touch acceptance are separate lanes.
 * The floor font matrix is a scoped Compose density override. Modal windows retain
 * their Android resource font configuration; their geometry/touches are checked at
 * each window size, but native tests must establish their actual system-font matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class AdaptiveTempoReachabilityRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private val font = mutableFloatStateOf(1f)
    private val runId = UUID.randomUUID().toString()
    private val measurements = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun portrait360_font10() = exerciseControls("360x640-font10", 1f, inline = false)
    @Test fun portrait360_font16() = exerciseControls("360x640-font16", 1.6f, inline = true)
    @Test fun portrait360_font20() = exerciseControls("360x640-font20", 2f, inline = true)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font10() = exerciseControls("412x840-font10", 1f, inline = false)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font16() = exerciseControls("412x840-font16", 1.6f, inline = true)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font20() = exerciseControls("412x840-font20", 2f, inline = true)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font10() = exerciseControls("640x360-land-font10", 1f, inline = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font16() = exerciseControls("640x360-land-font16", 1.6f, inline = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font20() = exerciseControls("640x360-land-font20", 2f, inline = true)

    @Test
    fun openWhySurvivesRelocationEvenWhenTheInlineCardIsOffscreen() {
        val vm = openFloor(fontScale = 1f)
        val before = vm.uiState.value
        val storedBefore = stored(vm)
        assertCardPlacement(inline = false)
        reveal(WorkoutTestTags.MICRO_REC_WHY, "why-relocation")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
        capture("why-relocation", "pinned-why-open")
        observeWhy("pinned-why-open")

        // The top of the floor remains at its identity; the new inline coach is
        // below entry and effort, outside this short viewport. Its dialog must not
        // disappear when that LazyColumn item is not composed.
        compose.runOnIdle { font.floatValue = 2f }
        compose.waitForIdle()
        compose.onAllNodes(
            hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
                !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT)),
        ).assertCountEquals(0)
        compose.onAllNodesWithTag(WorkoutTestTags.TEMPO_COACH_CARD).assertCountEquals(0)
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
        capture("why-relocation", "inline-offscreen-why-open")
        observeWhy("inline-offscreen-why-open")

        compose.runOnIdle { font.floatValue = 1f }
        compose.waitForIdle()
        assertCardPlacement(inline = false)
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
        observeWhy("returned-pinned-before-keep-touch")
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_USE, 64f)
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_KEEP, 48f).performClick()
        observeWhy("returned-pinned-after-keep-touch")
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertDoesNotExist()
        assertEquals(before.draft, vm.uiState.value.draft)
        assertEquals(storedBefore, stored(vm))
    }

    @Test
    fun whyUseChangesTheDraftAndClosesWithoutRecordingASet() {
        // Applying a Tempo tip dismisses it until the next save. Use an independent
        // fixture so this real sheet action does not manufacture a second offer or
        // prevent the nine profile cases from exercising the real card Apply.
        val profile = "why-use-independent"
        val vm = openFloor(fontScale = 2f)
        val storedBefore = stored(vm)
        reveal(WorkoutTestTags.WEIGHT_STEPPER, profile)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), "82.5")
        compose.awaitThat("the actual numeral touch changes the draft", vm.uiState::value) {
            abs(vm.uiState.value.draft.weightKg - WeightConverter.toKg(82.5, WeightUnit.LBS)) < EPSILON
        }
        val rec = checkNotNull(vm.microRec.value)
        assertTrue("the manual draft differs from the advice", abs(rec.nextWeightKg - vm.uiState.value.draft.weightKg) > EPSILON)
        openWhy(profile, "before-use-touch")
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_KEEP, 48f)
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_USE, 64f).performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertDoesNotExist()
        compose.awaitThat("Use suggestion changes only the draft", vm.uiState::value) {
            val draft = vm.uiState.value.draft
            abs(draft.weightKg - rec.nextWeightKg) < EPSILON &&
                draft.reps == rec.nextReps && draft.rpe == rec.nextRpe
        }
        assertEquals("the sheet action does not log", storedBefore, stored(vm))
        assertEquals(storedBefore.sets.map { it.id }, vm.uiState.value.session?.sets?.map { it.id })
        capture(profile, "after-use-touch")
    }

    private fun exerciseControls(profile: String, fontScale: Float, inline: Boolean) {
        val vm = openFloor(fontScale)
        val storedBefore = stored(vm)
        reveal(WorkoutTestTags.WEIGHT_STEPPER, profile)
        capture(profile, "numeric-before-touch")
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), "82.5")
        compose.awaitThat("the actual numeral touch changes the draft", vm.uiState::value) {
            abs(vm.uiState.value.draft.weightKg - WeightConverter.toKg(82.5, WeightUnit.LBS)) < EPSILON
        }
        assertEquals("numeric entry must not save work", storedBefore, stored(vm))

        reveal(WorkoutTestTags.rpeChoice(8), profile)
        capture(profile, "effort-before-touch")
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).assertIsEnabled().performClick()
        compose.awaitThat("the actual effort touch selects 8", vm.uiState::value) {
            vm.uiState.value.draft.rpe == 8
        }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).assertIsSelected()
        assertEquals(WeightConverter.toKg(82.5, WeightUnit.LBS), vm.uiState.value.draft.weightKg, EPSILON)
        assertEquals(storedBefore, stored(vm))

        val beforeWhy = vm.uiState.value.draft
        openWhy(profile, "before-keep-touch")
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_USE, 64f)
        assertWhyActionReachable(WorkoutTestTags.TEMPO_WHY_KEEP, 48f).performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertDoesNotExist()
        assertEquals("Keep my numbers preserves the manual draft", beforeWhy, vm.uiState.value.draft)
        assertEquals(storedBefore, stored(vm))

        val rec = checkNotNull(vm.microRec.value)
        assertTrue("the synthetic manual draft differs from the advice", abs(rec.nextWeightKg - vm.uiState.value.draft.weightKg) > EPSILON)
        reveal(WorkoutTestTags.MICRO_REC_APPLY, profile)
        assertCardPlacement(inline)
        capture(profile, "apply-before-touch")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsEnabled().performClick()
        compose.awaitThat("Apply changes the draft to the same recommendation", vm.uiState::value) {
            val draft = vm.uiState.value.draft
            abs(draft.weightKg - rec.nextWeightKg) < EPSILON &&
                draft.reps == rec.nextReps && draft.rpe == rec.nextRpe
        }
        assertEquals("Apply remains draft-only after reflow", storedBefore, stored(vm))
        assertEquals(storedBefore.sets.map { it.id }, vm.uiState.value.session?.sets?.map { it.id })
        capture(profile, "apply-after-touch")
        evidenceDirectory(profile).resolve("reachability.tsv").writeText(
            "tag\tattempt\tbounds_px\tlayout_px\tcontent_px\tpinned_tempo_px\tfully_visible\n" +
                measurements.joinToString("\n", postfix = "\n"),
        )
    }

    private fun openWhy(profile: String, stage: String) {
        reveal(WorkoutTestTags.MICRO_REC_WHY, profile)
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).assertIsDisplayed()
        observeWhy(stage, profile)
    }

    /** Full independent footer targets must fit the actual modal Android window. */
    private fun assertWhyActionReachable(tag: String, minimumHeightDp: Float): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        val target = node.fetchSemanticsNode()
        val bounds = target.boundsInWindow
        val sheet = compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SHEET).fetchSemanticsNode().boundsInWindow
        val density = target.layoutInfo.density.density
        val modalSize = compose.runOnIdle {
            val decor = checkNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            decor.width to decor.height
        }
        val whole = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
            bounds.left >= sheet.left - 1f && bounds.right <= sheet.right + 1f &&
            bounds.top >= sheet.top - 1f && bounds.bottom <= sheet.bottom + 1f &&
            bounds.left >= -1f && bounds.top >= -1f &&
            bounds.right <= modalSize.first + 1f && bounds.bottom <= modalSize.second + 1f
        println("ADAPTIVE_TEMPO_WHY_TARGET run=$runId tag=$tag bounds=$bounds layout=${target.size} " +
            "sheet=$sheet modal=$modalSize density=$density minHeightDp=$minimumHeightDp fullyVisible=$whole")
        assertTrue("$tag needs its full target in the modal window", whole)
        assertTrue("$tag must retain at least $minimumHeightDp dp of visible target", bounds.height / density + 0.01f >= minimumHeightDp)
        assertTrue("$tag must retain at least $minimumHeightDp dp of layout target", target.size.height / density + 0.01f >= minimumHeightDp)
        return node.assertIsDisplayed().assertIsEnabled()
    }

    private fun openFloor(fontScale: Float): ActiveWorkoutViewModel {
        font.floatValue = fontScale
        val vm = openLegExtension(deps, viewModels, loggedSets = twoWorkingSetsLogged())
        compose.showFloor(fontScale = font) {
            Box(Modifier.fillMaxSize()) {
                ActiveWorkoutScreen(onExit = {}, onFinished = {}, viewModel = vm, restNotificationsEnabledOverride = true)
            }
        }
        compose.awaitThat("the loaded synthetic lift and advice are ready", vm.uiState::value) {
            FLOOR_LIFT_READY(vm.uiState.value) && vm.microRec.value != null && vm.tempoCoachTip.value != null
        }
        compose.waitForIdle()
        assertNotNull(vm.uiState.value.session)
        return vm
    }

    private fun assertCardPlacement(inline: Boolean) {
        compose.onAllNodesWithTag(WorkoutTestTags.TEMPO_COACH_CARD).assertCountEquals(1)
        compose.onAllNodes(
            hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
                hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT)),
        ).assertCountEquals(if (inline) 1 else 0)
    }

    /** Bounded real swipes, with full-size / no-overlay geometry as the stop condition. */
    private fun reveal(tag: String, profile: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        val pinned = hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
            !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT))
        // Pinned coach controls already belong to the fixed floor; inline coach
        // controls, numerals and effort belong to the scrollable content.
        if (tag != WorkoutTestTags.MICRO_REC_APPLY && tag != WorkoutTestTags.MICRO_REC_WHY ||
            compose.onAllNodes(pinned).fetchSemanticsNodes().isEmpty()
        ) {
            compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
            compose.waitForIdle()
        }
        for (attempt in 0..3) {
            val target = node.fetchSemanticsNode()
            val bounds = target.boundsInRoot
            val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
            val tempo = compose.onAllNodes(pinned).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            val isPinnedAction = (tag == WorkoutTestTags.MICRO_REC_APPLY || tag == WorkoutTestTags.MICRO_REC_WHY) && tempo != null
            val safeBottom = if (isPinnedAction) content.bottom else minOf(content.bottom, tempo?.top ?: content.bottom)
            val fullyVisible = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
                bounds.left >= content.left - 1f && bounds.right <= content.right + 1f &&
                bounds.top >= content.top - 1f && bounds.bottom <= safeBottom + 1f
            measurements += listOf(tag, attempt, bounds, target.size, content, tempo, fullyVisible).joinToString("\t")
            println("ADAPTIVE_TEMPO_REACHABILITY run=$runId profile=$profile ${measurements.last()}")
            if (fullyVisible) return node.assertIsDisplayed()
            capture(profile, "$tag-clipped-attempt-$attempt")
            check(attempt < 3) { "$tag is clipped or covered after three real scroll gestures" }
            val safeHeight = safeBottom - content.top
            check(safeHeight > target.size.height + 48f) { "The floor has no unobstructed scroll area for $tag" }
            compose.onNodeWithTag(WorkoutTestTags.CONTENT).performTouchInput {
                swipeUp(startY = safeHeight * 0.8f, endY = safeHeight * 0.3f, durationMillis = 600)
            }
            compose.waitForIdle()
        }
        error("No reachable control: $tag")
    }

    private fun stored(vm: ActiveWorkoutViewModel) = checkNotNull(runBlocking {
        deps.workoutRepository.getSession(checkNotNull(vm.uiState.value.session).id)
    })

    private fun evidenceDirectory(profile: String): File = File("build/screen-renders/adaptive-tempo-reachability/$runId/$profile").also {
        check(it.exists() || it.mkdirs()) { "Cannot create reachability evidence directory $it" }
    }

    private fun capture(profile: String, name: String) {
        // This frame draws only the activity window. Modal sheets use another
        // Android window; observeWhy records its own window separately if available.
        val frame = compose.drawWindow()
        try {
            evidenceDirectory(profile).resolve("$name.png").outputStream().use {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save $profile/$name" }
            }
        } finally {
            frame.recycle()
        }
    }

    private fun observeWhy(stage: String, profile: String = "why-relocation") {
        compose.waitForIdle()
        val tags = listOf(
            WorkoutTestTags.TEMPO_WHY_SHEET, WorkoutTestTags.TEMPO_WHY_TITLE,
            WorkoutTestTags.TEMPO_WHY_SUMMARY, WorkoutTestTags.TEMPO_WHY_CALLOUT,
            WorkoutTestTags.TEMPO_WHY_USE, WorkoutTestTags.TEMPO_WHY_KEEP,
        )
        val lines = tags.map { tag ->
            val nodes = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
            "tag=$tag nodes=${nodes.map { "root=${it.boundsInRoot} window=${it.boundsInWindow} layout=${it.size}" }}"
        }.toMutableList()
        val modal = compose.runOnIdle {
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { decor ->
                lines += "modalDecor=${decor.width}x${decor.height} shown=${decor.isShown}"
                if (decor.width > 0 && decor.height > 0) {
                    Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                        decor.draw(Canvas(it))
                    }
                } else null
            }
        }
        if (modal == null) lines += "modalPixels=unavailable; activity frame does not capture the sheet"
        else {
            try {
                evidenceDirectory(profile).resolve("$stage-modal-window.png").outputStream().use {
                    check(modal.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save modal window" }
                }
            } finally {
                modal.recycle()
            }
        }
        lines.forEach { println("ADAPTIVE_TEMPO_WHY run=$runId profile=$profile stage=$stage $it") }
        evidenceDirectory(profile).resolve("$stage-geometry.txt").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val EPSILON = 0.000001
    }
}
