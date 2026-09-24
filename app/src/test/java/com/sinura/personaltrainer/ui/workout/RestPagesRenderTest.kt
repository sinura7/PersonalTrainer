package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.timer.RestLockScreen
import com.sinura.personaltrainer.timer.RestLockTags
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full rest page and the lock-screen rest page, composed for real: while rest runs, both
 * offer the dock card's −15 / +15 / Skip in its words, its order and its 15-second step
 * (design audit D11, W1b). Each used to spell its own ("−15s" and "Subtract 15 seconds" on
 * the page; "−15s", Skip, "+15s" with nothing for TalkBack on the lock screen), and no JVM
 * test composed either page. The page also names the planned length the card's way, "Planned
 * rest · 2:00" apart from the time left, and says it without the dot. Each page's Skip names the
 * rest it drew (W2b-3); what that name does is RestPageSkipTest's and RestLockSkipTest's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestPagesRenderTest {
    @get:Rule val compose = createComposeRule()

    private val nudges = mutableListOf<Int>()
    private var skips = 0
    private var starts = 0
    private lateinit var view: View

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun showRestPage(running: Boolean, fontScale: Float = 1f) {
        compose.showFloor(fontScale = fontScale) {
            view = LocalView.current
            RestFloorBody(
                rest = RestTimerUiState(remainingSeconds = 92, totalSeconds = 120, running = running),
                floor = RestFloorContext(exerciseName = "Leg extension", lastSetLine = null, sessionTargetLine = null),
                onSkip = { skips += 1 },
                onAdjust = { nudges += it },
                onSelectPreset = {},
                onCustom = { true },
                onStart = { starts += 1 },
                onAcknowledgeBattery = {},
                onBackToBar = {},
            )
        }
    }

    private fun control(tag: String): SemanticsNodeInteraction =
        compose.onNodeWithTag(tag).assertIsDisplayed().assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)

    /** The dock card's three under these tags: words, TalkBack, order and step. */
    private fun assertTheRunningThree(minusTag: String, plusTag: String, skipTag: String, oneRow: Boolean = true) {
        val minus = control(minusTag)
        val plus = control(plusTag)
        val skip = control(skipTag)
        assertEquals(listOf("−15"), minus.mergedTexts())
        assertEquals(listOf("Minus 15 seconds"), minus.spokenDescriptions())
        assertEquals(listOf("+15"), plus.mergedTexts())
        assertEquals(listOf("Plus 15 seconds"), plus.spokenDescriptions())
        assertEquals(listOf("Skip"), skip.mergedTexts())
        assertTrue("Skip is read by its word, was ${skip.spokenDescriptions()}", skip.spokenDescriptions().isEmpty())
        val bounds = listOf(minus, plus, skip).map { it.getBoundsInRoot() }
        if (oneRow) {
            assertEquals("one row", 1, bounds.map { it.top }.toSet().size)
            assertEquals("minus, plus, then Skip, left to right", bounds.map { it.left }.sorted(), bounds.map { it.left })
            // Three equal thumb-sized buttons sharing the row, not three labels' widths.
            val widths = bounds.map { (it.right - it.left).value }
            assertTrue("each at least 48 dp wide, were $widths", widths.all { it >= Metrics.touchMin.value })
            assertTrue("equal widths, were $widths", widths.all { abs(it - widths.first()) <= 1f })
        } else {
            assertTrue("minus sits left of plus", bounds[0].right <= bounds[1].left)
            assertTrue("Skip drops under them", bounds[2].top >= bounds[0].bottom)
        }
        minus.performClick()
        assertEquals("−15 ticks", HapticFeedbackConstants.CLOCK_TICK, lastHaptic())
        plus.performClick()
        assertEquals(listOf(-15, 15), nudges)
        skip.performClick()
        assertEquals(1, skips)
        // Skip ends rest, so it gives the firmer confirm, the dock card's Skip's.
        val skipHaptic = lastHaptic()
        compose.runOnUiThread { Haptics.commit(view) }
        assertEquals("Skip confirms", lastHaptic(), skipHaptic)
    }

    private fun lastHaptic(): Int = Shadows.shadowOf(view).lastHapticFeedbackPerformed()

    @Test
    fun theRestPageRunsTheDockCardsThreeBesideThePlannedLength() {
        showRestPage(running = true)
        assertTheRunningThree(minusTag = RestFloorTags.MINUS, plusTag = RestFloorTags.PLUS, skipTag = RestFloorTags.SKIP)
        val planned = compose.onNode(hasText("Planned rest · 2:00"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("Planned rest 2:00"), planned.spokenDescriptions())
        compose.onAllNodesWithText("−15s", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("+15s", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun atLargeTextTheRestPagesThreeWrapInTheSameOrder() {
        showRestPage(running = true, fontScale = LogLoopScale.STACK_WELLS_FROM)
        assertTheRunningThree(
            minusTag = RestFloorTags.MINUS,
            plusTag = RestFloorTags.PLUS,
            skipTag = RestFloorTags.SKIP,
            oneRow = false,
        )
    }

    @Test
    fun atRestThePageNamesThePlannedRestAndOffersStartRest() {
        showRestPage(running = false)
        compose.onNode(hasText("PLANNED REST"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("Planned rest · 2:00"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            listOf("Rest is not running. Planned rest 2:00. Start starts rest only."),
            compose.onNodeWithTag(RestFloorTags.CLOCK, useUnmergedTree = true).spokenDescriptions(),
        )
        compose.onNodeWithTag(RestFloorTags.MINUS).assertDoesNotExist()
        compose.onNodeWithTag(RestFloorTags.SKIP).assertDoesNotExist()
        compose.onNodeWithTag(RestFloorTags.START).performClick()
        assertEquals(1, starts)
    }

    @Test
    fun theRestPagesSkipNamesTheRestItDraws() {
        val named = mutableListOf<String>()
        var rest by mutableStateOf(RestTimerUiState(remainingSeconds = 92, totalSeconds = 120, running = true, timerId = "rest-a"))
        compose.showFloor {
            RestFloorBody(
                rest = rest,
                floor = RestFloorContext(exerciseName = "Leg extension", lastSetLine = null, sessionTargetLine = null),
                onSkip = { named += it },
                onAdjust = {},
                onSelectPreset = {},
                onCustom = { true },
                onStart = {},
                onAcknowledgeBattery = {},
                onBackToBar = {},
            )
        }
        compose.onNodeWithTag(RestFloorTags.SKIP).performClick()
        rest = rest.copy(timerId = "rest-b")
        compose.onNodeWithTag(RestFloorTags.SKIP).performClick()
        assertEquals(listOf("rest-a", "rest-b"), named)
    }

    @Test
    fun theLockScreensSkipNamesTheRestItDrewNotOneThatReplacedIt() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val controller = RestTimerController(context, RestTimerStore())
        val named = mutableListOf<String>()
        try {
            controller.start(120, "session-1")
            val drawn = controller.snapshot.value.timerId
            compose.showFloor {
                RestLockScreen(
                    controller = controller,
                    finishedLaunch = false,
                    onSkip = { named += it },
                    onAdjust = {},
                    onClose = {},
                    onBackToBar = {},
                )
            }
            val click = compose.onNodeWithTag(RestLockTags.SKIP).fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action
            // A +15 lands under a new id before the glance redraws; the tap was on the rest drawn.
            controller.adjust(15)
            checkNotNull(click).invoke()
            assertEquals(listOf(drawn), named)
            // Redrawn, the Skip names the +15.
            compose.onNodeWithTag(RestLockTags.SKIP).performClick()
            assertEquals(listOf(drawn, controller.snapshot.value.timerId), named)
        } finally {
            controller.stop()
        }
    }

    @Test
    fun theLockScreenRunsTheDockCardsThreeInTheSameOrder() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val controller = RestTimerController(context, RestTimerStore())
        try {
            controller.start(120, "session-1")
            compose.showFloor {
                view = LocalView.current
                RestLockScreen(
                    controller = controller,
                    finishedLaunch = false,
                    onSkip = { skips += 1 },
                    onAdjust = { nudges += it },
                    onClose = {},
                    onBackToBar = {},
                )
            }
            // Skip used to sit between the two, where a thumb reaching for +15 could end rest.
            assertTheRunningThree(minusTag = RestLockTags.MINUS, plusTag = RestLockTags.PLUS, skipTag = RestLockTags.SKIP)
        } finally {
            controller.stop()
        }
    }
}
