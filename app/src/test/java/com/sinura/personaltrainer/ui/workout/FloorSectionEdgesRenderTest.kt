package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every block of the floor sits in its own drawn panel (owner ask of 29 September 2026,
 * packet P1 of docs/owner-eight-plan-2026-09-29.md): stats, the entry, effort, the coach's
 * Next card and the set history each have a `FloorSection` around them, at the default text
 * size, at 1.6 and at the largest, on the narrowest phone.
 *
 * The tag on a frame says where the frame is; it cannot say the frame is drawn. So each
 * frame's stroke is read off the window itself along the straight run of its left edge, and
 * the fill just inside it, in the colours the tokens say. A frame whose edge went back to the
 * 8% hairline, or lost its stroke or its fill in a refactor, fails here. The frames also cost
 * width: the checks that the stats label, the warm-up ramp captions and the five effort chips
 * still lay out whole inside them are here too, since the frames are what changed that width.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h1600dp-xhdpi")
class FloorSectionEdgesRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun everyFloorBlockIsADrawnPanelAtDefaultText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = twoWorkingSetsLogged())
        show(vm, fontScale = 1f)
        assertFramedAndDrawn()
        // The frame took width from the stats row; the label that used to fit still fits.
        assertOneLine("Last set · RPE 9")
    }

    @Test
    fun everyFloorBlockIsADrawnPanelAtMediumText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = twoWorkingSetsLogged())
        show(vm, fontScale = 1.6f)
        assertFramedAndDrawn()
    }

    @Test
    fun everyFloorBlockIsADrawnPanelAtTheLargestText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = twoWorkingSetsLogged())
        show(vm, fontScale = 2f)
        assertFramedAndDrawn()
        // Inside its frame the effort track still lays its five out on one row at 360 dp
        // (RpeSelector sizes the row from the width it is given, which the frame narrowed).
        val tops = RpeCopy.VALUES.map { compose.onNodeWithTag(WorkoutTestTags.rpeChoice(it)).windowBounds().top }
        assertEquals("the five effort chips share one row at the largest text, tops were $tops", 1, tops.map { it.toInt() }.toSet().size)
    }

    @Test
    fun theWarmUpRampCaptionsStayOnOneLineInsideTheEntryFrame() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm, fontScale = 1f) { vm.setWarmup(true) }
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithTag(WorkoutTestTags.WARMUP_RAMP).fetchSemanticsNodes().isNotEmpty()
        }
        // P1's first cut, at 12 dp inside the frame, wrapped this caption onto two lines. The
        // caption has always run past its preset's padded box into the padding (that is the
        // preset's own geometry, from before P1), so the check is the line count and that the
        // caption stays inside the ramp, not the layout's overflow flag.
        val caption = compose.onNode(hasText("40% · Suggested"), useUnmergedTree = true)
        assertEquals("'40% · Suggested' lays out on one line", 1, caption.textLayout().lineCount)
        val ramp = compose.onNodeWithTag(WorkoutTestTags.WARMUP_RAMP).windowBounds()
        val where = caption.windowBounds()
        assertTrue("the caption stays inside the ramp ($where in $ramp)", where.left >= ramp.left - 1f && where.right <= ramp.right + 1f)
    }

    @Test
    fun theTempoCoachCardKeepsItsNumbersWholeAtTheLargestText() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm, fontScale = 2f)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value != null }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithTag(WorkoutTestTags.TEMPO_COACH_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        val layout = compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).textLayout()
        assertTrue(
            "Tempo's numbers must be one whole line, were ${layout.lineCount} line(s), fits=${layout.fitsItsWidth()}",
            layout.lineCount == 1 && layout.fitsItsWidth(),
        )
    }

    private fun assertFramedAndDrawn() {
        compose.waitUntil(timeoutMillis = WAIT_MS) {
            compose.onAllNodesWithTag(WorkoutTestTags.SET_HISTORY).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val frames = listOf(
            WorkoutTestTags.SECTION_STATS,
            WorkoutTestTags.SECTION_ENTRY,
            WorkoutTestTags.SECTION_RPE,
            WorkoutTestTags.SECTION_SET_HISTORY,
        )
        frames.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        // The block is inside its frame, not beside it.
        compose.onNodeWithTag(WorkoutTestTags.SET_HISTORY)
            .assert(hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_SET_HISTORY)))
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK)
            .assert(hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_RPE)))
        val window = compose.drawWindow()
        frames.forEach { tag -> assertStrokeAndFill(window, compose.onNodeWithTag(tag), tag) }
    }

    /**
     * Reads the frame's left edge: its stroke in [SectionEdge] over [Surface1], and the fill
     * just inside it in [Surface1]. The corners are skipped (the stroke curves there), and the
     * run must be mostly the expected colour, not all of it: a child that reaches the padding
     * could sit on a row or two of it.
     */
    private fun assertStrokeAndFill(window: Bitmap, frame: SemanticsNodeInteraction, tag: String) {
        val density = frame.fetchSemanticsNode().layoutInfo.density.density
        val bounds = frame.windowBounds()
        val strokePx = Metrics.hairline.value * density
        val cornerPx = Radius.md.value * density
        val top = bounds.top + cornerPx
        val bottom = bounds.bottom - cornerPx
        assertTrue("$tag is tall enough to have a straight edge", bottom - top > strokePx * 4)
        val rows = (bottom - top).toInt()

        val edgeColumn = box(bounds.left, top, bounds.left + strokePx, bottom)
        val edgePixels = rows * strokePx.toInt()
        val edgeHits = window.count(edgeColumn, drawnOver(SectionEdge, Surface1))
        assertTrue(
            "$tag: the left edge must be the section edge over the panel fill, was $edgeHits of $edgePixels pixels",
            edgeHits >= edgePixels * 0.75,
        )
        // And not the 8% hairline it replaced: the two are 36 levels apart per channel, well
        // past the sampler's tolerance, so a reverted token reads as zero here.
        val oldHits = window.count(edgeColumn, drawnOver(Hairline, Surface1))
        assertTrue("$tag: the edge is not the old hairline ($oldHits of $edgePixels)", oldHits < edgePixels * 0.25)

        // Surface1 and Pit are one ladder step apart, 7 to 10 levels per channel, so the fill is
        // read with a tolerance under that step, not the sampler's default for anti-aliased ink.
        val fillColumn = box(bounds.left + strokePx + 1f, top, bounds.left + strokePx + 3f, bottom)
        val fillPixels = rows * 2
        val fillHits = window.count(fillColumn, Surface1, tolerance = FLAT_FILL_TOLERANCE)
        assertTrue(
            "$tag: just inside the edge the panel is filled Surface1, was $fillHits of $fillPixels pixels",
            fillHits >= fillPixels * 0.75,
        )
        val pitHits = window.count(fillColumn, Pit, tolerance = FLAT_FILL_TOLERANCE)
        assertTrue("$tag: the panel is not the bare floor ($pitHits of $fillPixels)", pitHits < fillPixels * 0.25)
    }

    /** The text found by [words] is laid out as one line that its box holds whole. */
    private fun assertOneLine(words: String) {
        val layout = compose.onNode(hasText(words), useUnmergedTree = true).textLayout()
        assertTrue(
            "'$words' must lay out on one line, was ${layout.lineCount} line(s), fits=${layout.fitsItsWidth()}",
            layout.lineCount == 1 && layout.fitsItsWidth(),
        )
    }

    private fun show(vm: ActiveWorkoutViewModel, fontScale: Float, drive: () -> Unit = {}) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalWeightUnit provides WeightUnit.LBS,
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                PersonalTrainerTheme {
                    Box(Modifier.width(360.dp).height(1600.dp).background(Pit)) {
                        ActiveWorkoutScreen(
                            onExit = {},
                            onFinished = {},
                            viewModel = vm,
                            restNotificationsEnabledOverride = true,
                        )
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        compose.waitUntil(timeoutMillis = WAIT_MS) { FLOOR_LIFT_READY(vm.uiState.value) }
        compose.waitForIdle()
        drive()
        compose.waitForIdle()
    }

    private companion object {
        const val WAIT_MS = 20_000L

        /** Under the 7-level step between Pit and Surface1, so the two flat fills read apart. */
        const val FLAT_FILL_TOLERANCE = 3
    }
}
