package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Warn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dock's rest card, composed on its own: what the lifter reads and hears in each of its
 * three moods, and what each tap does.
 *
 * At rest it is dim and shows the planned length, and a tap edits that length; running it
 * counts down in cyan beside the planned length, and a tap opens the rest page; the last ten seconds
 * turn Warn and say so; done, it flashes "Back to the bar" in gold, announces that once, and
 * after the dwell settles back to rest. Its controls are Start rest (with Time set when the
 * lift allows) at rest and −15 / +15 / Skip while running.
 *
 * All of this was held as lines of RestTimerCard.kt (`else -> PLANNED`,
 * `onClick = if (idle) onEditDuration else onOpenRest`, `running -> listOf(MINUS, PLUS,
 * SKIP)`, one `liveRegion = LiveRegionMode.Polite`). W1b (design audit D11) names the planned
 * length one way: "Planned" under the idle clock, "Planned 2:00" under the running one, apart
 * from the time left, and "Planned rest 2:00" aloud, as the rest page says it. Its −15 / +15 /
 * Skip are the rest page's and the lock screen's too (RestPagesRenderTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestTimerCardRenderTest {
    @get:Rule val compose = createComposeRule()

    private val remaining = mutableStateOf(0)
    private val running = mutableStateOf(false)
    private val completed = mutableStateOf<String?>(null)
    private val afterWarmup = mutableStateOf(false)

    private var skipped = 0
    private var started = 0
    private val nudges = mutableListOf<Int>()
    private var edits = 0
    private var setClocks = 0
    private var opened = 0

    private fun showCard(total: Int = 120, offerSetClock: Boolean = false, fontScale: Float = 1f) {
        compose.showFloor(fontScale = fontScale) {
            RestTimerCard(
                remainingSeconds = remaining.value,
                totalSeconds = total,
                running = running.value,
                completedTimerId = completed.value,
                afterWarmup = afterWarmup.value,
                offerSetClock = offerSetClock,
                onSkip = { skipped += 1 },
                onStart = { started += 1 },
                onNudge = { nudges += it },
                onEditDuration = { edits += 1 },
                onStartSetClock = { setClocks += 1 },
                onOpenRest = { opened += 1 },
            )
        }
    }

    /** The card's big tile: ring, kicker, clock and caption, read and tapped as one. */
    private fun tile(cardTag: String, clock: String): SemanticsNodeInteraction =
        compose.onNode(hasClickAction() and hasText(clock) and hasAnyAncestor(hasTestTag(cardTag)))

    /** A visible word on the card, found where it is drawn rather than in the merged tile. */
    private fun word(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text), useUnmergedTree = true)

    private fun inkOf(text: String): Color = word(text).textLayout().layoutInput.style.color

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val liveRegions = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Test
    fun atRestTheCardShowsThePlannedLengthDimAndATapEditsIt() {
        // A stale remaining value from the last rest is not what the idle card shows.
        remaining.value = 45
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed().assertHeightIsAtLeast(Metrics.commit)
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertDoesNotExist()
        val tile = tile(WorkoutTestTags.REST_IDLE, clock = "2:00").assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)
        compose.onAllNodesWithText("0:45", useUnmergedTree = true).assertCountEquals(0)
        // The clock at rest is the length the next rest starts with, and says so (D11).
        word("REST").assertIsDisplayed()
        word("Planned").assertIsDisplayed()
        assertEquals(listOf("Rest is not running. Planned rest 2:00. Tap to change duration."), tile.spokenDescriptions())
        // Dim at rest: the clock and its kicker are secondary ink, a numeral in the medium size.
        assertEquals(TextSecondary, inkOf("2:00"))
        assertEquals(TextSecondary, inkOf("REST"))
        assertEquals(InstrumentType.numeralMd.fontSize, word("2:00").textLayout().layoutInput.style.fontSize)
        tile.performClick()
        assertEquals("an idle tap edits the planned length", 1, edits)
        assertEquals(0, opened)
    }

    @Test
    fun atRestStartRestIsTheOneControlAndSaysHowLong() {
        showCard(total = 150)
        val start = compose.onNodeWithTag(WorkoutTestTags.START_REST)
            .assertIsDisplayed()
            .assert(isButton)
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Start rest"), start.mergedTexts())
        assertEquals(listOf("Start rest, 2 minutes 30 seconds"), start.spokenDescriptions())
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).assertDoesNotExist()
        listOf(WorkoutTestTags.REST_MINUS, WorkoutTestTags.REST_PLUS, WorkoutTestTags.REST_SKIP).forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        // The retired idle "Start next" (phone check, 12 Sep 2026) is not on the card.
        compose.onAllNodesWithText("Start next", substring = true, useUnmergedTree = true).assertCountEquals(0)
        start.performClick()
        assertEquals(1, started)
        assertEquals("Start rest is not a tap on the tile", 0, edits)
    }

    @Test
    fun timeSetStandsBesideStartRestOnlyWhenTheLiftOffersIt() {
        showCard(offerSetClock = true)
        val timeSet = compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK)
            .assertIsDisplayed()
            .assert(isButton)
            .assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Time set"), timeSet.mergedTexts())
        assertEquals(listOf("Time this set"), timeSet.spokenDescriptions())
        val start = compose.onNodeWithTag(WorkoutTestTags.START_REST).getBoundsInRoot()
        assertTrue("Time set comes first, then Start rest", timeSet.getBoundsInRoot().right <= start.left)
        timeSet.performClick()
        assertEquals(1, setClocks)
        assertEquals("Time set does not start rest", 0, started)
    }

    @Test
    fun runningTheCardCountsDownBesideThePlannedLengthAndATapOpensTheRestPage() {
        running.value = true
        remaining.value = 92
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed().assertHeightIsAtLeast(Metrics.commit)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        val tile = tile(WorkoutTestTags.REST_BAR, clock = "1:32").assert(isButton)
        word("REST").assertIsDisplayed()
        // The time left is the clock; the plan is said apart from it, in the idle card's word.
        // That it stays on one line is measured in RestTimerCardFitRenderTest.
        word("Planned 2:00").assertIsDisplayed()
        assertEquals(listOf("Rest 1:32 remaining. Planned rest 2:00. Open rest timer."), tile.spokenDescriptions())
        // Bright while it runs: the time in primary ink, the kicker in rest cyan. The kicker
        // is a label inside the tile, not a heading TalkBack would jump to.
        assertEquals(TextPrimary, inkOf("1:32"))
        assertEquals(RestCyan, inkOf("REST"))
        word("REST").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag(WorkoutTestTags.START_REST).assertDoesNotExist()
        tile.performClick()
        assertEquals("a running tap opens the rest page", 1, opened)
        assertEquals(0, edits)
    }

    @Test
    fun runningTheCardOffersMinusPlusAndSkip() {
        running.value = true
        remaining.value = 92
        showCard()
        // The rest page and the lock screen show these three in the same words and order.
        val minus = compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)
        val plus = compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("−15"), minus.mergedTexts())
        assertEquals(listOf("Minus 15 seconds"), minus.spokenDescriptions())
        assertEquals(listOf("+15"), plus.mergedTexts())
        assertEquals(listOf("Plus 15 seconds"), plus.spokenDescriptions())
        val skip = compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assert(isButton).assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Skip"), skip.mergedTexts())
        // Skip is read by its word; it is not a number of seconds the way −15 and +15 are.
        assertTrue("was ${skip.spokenDescriptions()}", skip.spokenDescriptions().none { spoken -> spoken.any { it.isDigit() } })
        // Minus, plus, then Skip, left to right.
        val order = listOf(minus, plus, skip).map { it.getBoundsInRoot().left }
        assertEquals(order.sorted(), order)
        minus.performClick()
        plus.performClick()
        assertEquals(listOf(-15, 15), nudges)
        skip.performClick()
        assertEquals(1, skipped)
        assertEquals("no control on the card opens the rest page", 0, opened)
    }

    @Test
    fun theLastTenSecondsTurnWarnAndSaySo() {
        running.value = true
        remaining.value = 11
        showCard()
        val tile = tile(WorkoutTestTags.REST_BAR, clock = "0:11")
        assertEquals(listOf("Rest 0:11 remaining. Planned rest 2:00. Open rest timer."), tile.spokenDescriptions())
        assertEquals(RestCyan, inkOf("REST"))
        remaining.value = 10
        compose.waitForIdle()
        assertEquals(
            listOf("Rest 0:10 remaining. Planned rest 2:00. Last ten seconds. Open rest timer."),
            tile(WorkoutTestTags.REST_BAR, clock = "0:10").spokenDescriptions(),
        )
        assertEquals(Warn, inkOf("REST"))
    }

    @Test
    fun theRunningClockIsNeverALiveRegion() {
        // TalkBack must not hear every second: neither the running clock nor the idle card
        // is a live region, however the time changes under it.
        running.value = true
        remaining.value = 92
        showCard()
        compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)
        remaining.value = 91
        compose.waitForIdle()
        compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)
        running.value = false
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theFinishFlashesBackToTheBarOnceAndSettlesAfterTheDwell() {
        running.value = true
        remaining.value = 1
        showCard()
        compose.mainClock.autoAdvance = false
        try {
            // The service finishes the rest: the card is told by a completed timer's id.
            running.value = false
            remaining.value = 0
            completed.value = "rest-1"
            compose.settle()
            val tile = tile(WorkoutTestTags.REST_BAR, clock = "0:00")
            word("BACK TO THE BAR").assertIsDisplayed()
            word("Rest complete").assertIsDisplayed()
            assertEquals(listOf("Back to the bar. Rest complete."), tile.spokenDescriptions())
            assertEquals(PrGold, inkOf("BACK TO THE BAR"))
            // Announced once, politely: the flash is the card's one live region.
            val live = compose.onAllNodes(liveRegions, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, live.size)
            assertEquals(listOf("Back to the bar. Rest complete."), live.single().config[SemanticsProperties.ContentDescription])
            assertEquals("the flash waits its turn; it never cuts TalkBack off", LiveRegionMode.Polite, live.single().config[SemanticsProperties.LiveRegion])
            // The flash is not a moment to act: no controls until it settles.
            compose.onNodeWithTag(WorkoutTestTags.START_REST).assertDoesNotExist()
            compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assertDoesNotExist()

            compose.mainClock.advanceTimeBy(Motion.FINISHED_DWELL_MS - DWELL_MARGIN_MS)
            compose.settle()
            word("BACK TO THE BAR").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(DWELL_MARGIN_MS * 2)
            compose.settle()
            compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
            compose.onNodeWithTag(WorkoutTestTags.START_REST).assertIsDisplayed()
            compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)

            // The same finished timer does not flash twice when the card recomposes.
            afterWarmup.value = true
            compose.settle()
            compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
            compose.onAllNodesWithText("BACK TO THE BAR", useUnmergedTree = true).assertCountEquals(0)
            // The next rest's finish flashes again.
            afterWarmup.value = false
            completed.value = "rest-2"
            compose.settle()
            word("BACK TO THE BAR").assertIsDisplayed()
            // Starting the next rest during the flash shows the running clock at once.
            running.value = true
            remaining.value = 120
            compose.settle()
            tile(WorkoutTestTags.REST_BAR, clock = "2:00")
            compose.onAllNodesWithText("BACK TO THE BAR", useUnmergedTree = true).assertCountEquals(0)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun afterAWarmupTheIdleCardSaysWarmupsDoNotStartRest() {
        afterWarmup.value = true
        showCard()
        val tile = tile(WorkoutTestTags.REST_IDLE, clock = "2:00")
        word("WARM-UP").assertIsDisplayed()
        word("Warm-ups do not start rest").assertIsDisplayed()
        compose.onAllNodesWithText("Planned", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(
            listOf("Rest is not running. Warm-ups do not start rest. Planned rest 2:00. Tap to change duration."),
            tile.spokenDescriptions(),
        )
        compose.onNodeWithTag(WorkoutTestTags.START_REST).assertIsDisplayed()
    }

    @Test
    fun aRunningRestAfterAWarmupIsTheOrdinaryRunningCard() {
        // The warm-up words belong to the idle card only: once rest runs, it reads as rest.
        afterWarmup.value = true
        running.value = true
        remaining.value = 92
        showCard()
        val tile = tile(WorkoutTestTags.REST_BAR, clock = "1:32")
        word("REST").assertIsDisplayed()
        word("Planned 2:00").assertIsDisplayed()
        compose.onAllNodesWithText("WARM-UP", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Warm-ups do not start rest", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(listOf("Rest 1:32 remaining. Planned rest 2:00. Open rest timer."), tile.spokenDescriptions())
    }

    @Test
    fun atLargeTextTheRunningControlsDropUnderTheClock() {
        // From the stacking scale the −15 / +15 / Skip row wraps under the clock instead of
        // squeezing it into what is left beside the controls.
        running.value = true
        remaining.value = 92
        showCard(fontScale = LogLoopScale.STACK_WELLS_FROM)
        val tile = tile(WorkoutTestTags.REST_BAR, clock = "1:32").getBoundsInRoot()
        val minus = compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).assertIsDisplayed().getBoundsInRoot()
        assertTrue("−15 sits under the clock (top ${minus.top}, clock bottom ${tile.bottom})", minus.top >= tile.bottom)
    }

    private companion object {
        /** Well clear of the few frames each settle runs, on either side of the dwell's end. */
        const val DWELL_MARGIN_MS = 1_000L
    }
}
