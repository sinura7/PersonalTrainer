package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.RestDurationSheet
import com.sinura.personaltrainer.ui.components.SetWorkDock
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.RestCyanDim
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's clocks as they are drawn and felt, where semantics cannot look: the rest card's
 * ring is a small 48 dp ring with a 4 dp stroke beside the clock, draining in cyan while rest
 * runs and empty at rest, where a chevron beside the planned length says a tap opens the
 * sheet; the set clock's bar is filled cyan, and a hold's bar starts full and drains against
 * the hold's own target, so half the hold in, half the bar is left. Skip on the card lands as
 * a commit and ±15 as a tick. The device journey's tags name the real Stop, sheet and Time set.
 *
 * These were lines of RestTimerCard.kt, RestTimerUi.kt and ActiveWorkoutScreen.kt read as
 * text (`RestMiniRing(progress = progress, accent = accent, …)`, `Canvas(modifier =
 * Modifier.size(Metrics.restRingSmall))`, `Metrics.ringStroke.toPx()`, `remainingSeconds = if
 * (running) safeRemaining else 0`, `TemperIcons.Chevron`, `RestCyanDim` inside
 * `FloorInstrumentBar`, `holdTotalSeconds = holdTimer.totalSeconds`, `confirm = true`, the
 * three `const val` tags). T1b kept the drawing lines as text because no semantics can see
 * them; the pixels can, and W2a trims the bar they sat in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestCardDrawingRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var view: View

    /** The ViewModel's clock: its hold ticker waits here between reads of the device's. */
    private val viewModelClock = TestCoroutineScheduler()

    private var skips = 0
    private val nudges = mutableListOf<Int>()
    private var stops = 0
    private var timeSets = 0

    /** The ring's track: HairlineStrong, drawn over the card. */
    private val track = drawnOver(HairlineStrong, Surface2)

    /** A running set clock's or hold's fill: RestCyanDim, drawn over the bar. */
    private val fill = drawnOver(RestCyanDim, Surface2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = viewModelClock))
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
    fun theRestRingIsASmallFourDpRingBesideTheClock() {
        showCard(running = true, remaining = 60)
        val ring = ringBox(tile(WorkoutTestTags.REST_BAR, "1:00"))
        assertEquals("ring width in dp", 48f, ring.width / px, 1.5f)
        assertEquals("ring height in dp", 48f, ring.height / px, 1.5f)
        // Along the ring's middle row, from its left edge: the stroke, then the card again.
        val frame = compose.drawWindow()
        val row = ring.center.y.toInt()
        var stroke = 0
        var x = ring.left.toInt()
        while (x < ring.right.toInt() && !isNear(frame.getPixel(x, row), Surface2, tolerance = 14)) {
            stroke += 1
            x += 1
        }
        assertEquals("stroke in dp", 4f, stroke / px, 1f)
    }

    @Test
    fun aRunningRestDrainsTheRingInCyan() {
        showCard(running = true, remaining = 60)
        val samples = ringSamples(tile(WorkoutTestTags.REST_BAR, "1:00"))
        val cyan = samples.count { isNear(it, RestCyan) }
        val bare = samples.count { isNear(it, track) }
        // Half the rest is left, so about half the ring is cyan and the rest bare track.
        assertTrue("cyan share was $cyan of ${samples.size}", cyan in (samples.size * 35 / 100)..(samples.size * 65 / 100))
        assertTrue("bare share was $bare of ${samples.size}", bare >= samples.size * 30 / 100)
    }

    @Test
    fun atRestTheRingIsEmptyAndAChevronSaysTheLengthOpens() {
        // The store can still hold a remaining time at rest; the idle ring draws none of it.
        showCard(running = false, remaining = 92)
        val tile = tile(WorkoutTestTags.REST_IDLE, "2:00")
        val samples = ringSamples(tile)
        assertEquals("an idle ring is bare track all round", 0, samples.count { isNear(it, TextSecondary) })
        assertTrue(samples.count { isNear(it, track) } >= samples.size * 90 / 100)
        assertTrue("the chevron is drawn beside the planned length", inkRightOf(clock(WorkoutTestTags.REST_IDLE, "2:00")) >= CHEVRON_MIN_PIXELS)
    }

    @Test
    fun aRunningRestHasNoChevron() {
        showCard(running = true, remaining = 60)
        assertEquals(0, inkRightOf(clock(WorkoutTestTags.REST_BAR, "1:00")))
    }

    @Test
    fun theSetClocksBarIsFilledCyan() {
        compose.showFloor {
            Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                SetWorkDock(elapsedSeconds = 42, hold = false, running = true, onStop = { stops += 1 }, modifier = Modifier.fillMaxWidth())
            }
        }
        assertTrue("the set clock's bar is filled", fillShare(compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK), toFraction = 0.4f) >= 0.9f)
    }

    @Test
    fun aRunningHoldsBarDrainsAgainstTheHoldsOwnTarget() {
        val vm = openPlank()
        compose.showWorkoutScreen(vm)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.holdTimer.value.running }
        compose.waitForIdle()
        // Just started, nearly all of the target is left: the bar is nearly full.
        val target = vm.holdTimer.value.totalSeconds
        assertTrue(target > 0)
        val bar = compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK)
        assertTrue("the hold's bar is filled", fillShare(bar, toFraction = 0.25f) >= 0.9f)
        // Half the target later, on every clock the hold reads (the device's elapsed time,
        // which the ViewModel's ticker reads; and the ViewModel's own scheduler, which wakes
        // that ticker), half the target is left, and so is half the bar.
        val half = target / 2
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(half.toLong()))
        viewModelClock.advanceTimeBy(ONE_SECOND_MS)
        viewModelClock.runCurrent()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.holdTimer.value.remainingSeconds <= target - half }
        compose.waitForIdle()
        val hold = vm.holdTimer.value
        assertTrue("still holding", hold.running)
        val left = hold.remainingSeconds.toFloat() / target
        assertEquals("about half the target is left", 0.5f, left, 0.1f)
        assertEquals("the fill ends at the share of the target still to hold", left, fillEnd(bar), FILL_END_TOLERANCE)
    }

    @Test
    fun theCardsSkipLandsAsACommitAndItsStepsAsTicks() {
        showCard(running = true, remaining = 60)
        compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).performClick()
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, lastHaptic())
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).performClick()
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, lastHaptic())
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals("Skip ends rest, so it confirms", HapticFeedbackConstants.CONFIRM, lastHaptic())
        assertEquals(listOf(-15, 15), nudges)
        assertEquals(1, skips)
    }

    @Test
    fun theSetClocksStopCarriesTheTagTheDeviceJourneyFinds() {
        compose.showFloor {
            SetWorkDock(elapsedSeconds = 42, hold = false, running = true, onStop = { stops += 1 }, modifier = Modifier.fillMaxWidth())
        }
        compose.onNodeWithTag(WorkoutTestTags.STOP_SET_CLOCK).assertIsDisplayed().performClick()
        assertEquals(1, stops)
    }

    @Test
    fun theLengthSheetAndItsTimeSetCarryTheTagsTheDeviceJourneyFinds() {
        compose.showFloor {
            RestDurationSheet(
                selectedSeconds = 120,
                onSelect = {},
                onNudge = {},
                onCustomRest = { true },
                onDismiss = {},
                offerSetClock = true,
                onTimeSet = { timeSets += 1 },
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.SHEET_START_SET_CLOCK).performScrollTo().performClick()
        assertEquals(1, timeSets)
    }

    private val px: Float get() = compose.density.density

    private fun lastHaptic(): Int = Shadows.shadowOf(view).lastHapticFeedbackPerformed()

    private fun showCard(running: Boolean, remaining: Int, total: Int = 120) {
        compose.showFloor {
            view = LocalView.current
            Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                RestTimerCard(
                    remainingSeconds = remaining,
                    totalSeconds = total,
                    running = running,
                    completedTimerId = null,
                    afterWarmup = false,
                    offerSetClock = false,
                    onSkip = { skips += 1 },
                    onStart = {},
                    onNudge = { nudges += it },
                    onEditDuration = {},
                    onStartSetClock = {},
                    onOpenRest = {},
                )
            }
        }
    }

    /** The card's big tile: ring, kicker, clock and caption, tapped as one. */
    private fun tile(cardTag: String, clock: String): SemanticsNodeInteraction =
        compose.onNode(hasClickAction() and hasText(clock) and hasAnyAncestor(hasTestTag(cardTag)))

    /** The clock's own words, where they are drawn. */
    private fun clock(cardTag: String, clock: String): SemanticsNodeInteraction =
        compose.onNode(hasText(clock) and hasAnyAncestor(hasTestTag(cardTag)), useUnmergedTree = true)

    /** Everything drawn at the tile's leading edge, before the words start: the ring alone. */
    private fun ringBox(tile: SemanticsNodeInteraction): Rect {
        val bounds = tile.windowBounds()
        val lead = box(bounds.left, bounds.top, bounds.left + RING_ZONE_DP * px, bounds.bottom)
        return checkNotNull(compose.drawWindow().inkBox(lead, Surface2)) { "nothing is drawn where the ring should be" }
    }

    /** The ring's stroke, read along its middle all the way round. */
    private fun ringSamples(tile: SemanticsNodeInteraction): List<Int> {
        val ring = ringBox(tile)
        val strokeMiddle = ring.width / 2f - 2f * px
        return compose.drawWindow().around(ring.center.x, ring.center.y, strokeMiddle)
    }

    /** Pixels in the chevron's colour just after the clock's last glyph, on the clock's line. */
    private fun inkRightOf(clock: SemanticsNodeInteraction): Int {
        val words = clock.windowBounds()
        val beside = box(words.right + 1f, words.top, words.right + CHEVRON_ZONE_DP * px, words.bottom)
        return compose.drawWindow().count(beside, TextSecondary, tolerance = 48)
    }

    /**
     * Where a bar's fill stops, as a share of the bar's width, read along its bottom edge (0 when
     * nothing is filled). The fill is drawn from the bar's start, so this is how much it shows.
     */
    private fun fillEnd(bar: SemanticsNodeInteraction): Float {
        val bounds = bar.windowBounds()
        val frame = compose.drawWindow()
        val y = (bounds.bottom - 3f * px).toInt()
        val xs = (bounds.left + 8f * px).toInt() until (bounds.right - 8f * px).toInt()
        val last = xs.lastOrNull { isNear(frame.getPixel(it, y), fill, tolerance = 12) } ?: return 0f
        return (last + 1 - bounds.left) / bounds.width
    }

    /** How much of a bar's bottom edge, from its start to [toFraction] of its width, wears the fill. */
    private fun fillShare(bar: SemanticsNodeInteraction, toFraction: Float): Float {
        val bounds = bar.windowBounds()
        val frame = compose.drawWindow()
        val y = (bounds.bottom - 3f * px).toInt()
        val from = (bounds.left + 8f * px).toInt()
        val to = (bounds.left + bounds.width * toFraction).toInt()
        val xs = (from until to).toList()
        return xs.count { isNear(frame.getPixel(it, y), fill, tolerance = 12) }.toFloat() / xs.size
    }

    /** A plank: a hold, timed against its target rather than counted in reps. */
    private fun openPlank(): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            seedTestWorkout(
                deps = deps,
                exerciseId = "plank",
                exerciseName = "Plank",
                routineName = "Core",
                targetSets = 3,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = 60,
            ).session.id
        }
        return floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000L

        /** A bar's width is some 650 pixels here; this is a few dozen of them either way. */
        const val FILL_END_TOLERANCE = 0.05f

        /** The ring (48 dp) and a little of the 12 dp gap after it, never the kicker's first letter. */
        const val RING_ZONE_DP = 54f

        /** The gap after the clock (4 dp), the 16 dp chevron, and a little air. */
        const val CHEVRON_ZONE_DP = 24f

        /** A 16 dp chevron's strokes at xhdpi cover far more than this; a stray edge pixel does not. */
        const val CHEVRON_MIN_PIXELS = 20
    }
}
