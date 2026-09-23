package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.components.SetWorkDock
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dock's in-set clock, composed on its own: a hold counting down to its target, a hold
 * that reached it, and the manual set stopwatch counting up with its Stop.
 *
 * It was held as a slice of RestTimerUi.kt between `fun SetWorkDock` and the next
 * `@Composable` (`FloorInstrumentBar(`, `HoldWork.liveDockSeconds`, `workout-hold-clock`,
 * `onStop = onStop`) and as the instrument bar's `heightIn(min = Metrics.logTimerRow)`,
 * `workout-stop-set-clock` and `SetStopwatchCopy.STOP`. W2a trims the bar under this clock
 * and the slices would throw; what the lifter reads, hears and taps stays the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SetWorkDockRenderTest {
    @get:Rule val compose = createComposeRule()

    private var stops = 0

    private fun showClock(
        elapsed: Int,
        remaining: Int = 0,
        total: Int = 0,
        hold: Boolean,
        targetReached: Boolean = false,
        stoppable: Boolean = false,
    ) {
        compose.showFloor {
            SetWorkDock(
                elapsedSeconds = elapsed,
                remainingSeconds = remaining,
                totalSeconds = total,
                hold = hold,
                targetReached = targetReached,
                running = !targetReached,
                onStop = if (stoppable) stop else null,
            )
        }
    }

    private val stop: () -> Unit = { stops += 1 }

    /** What TalkBack reads for the clock: the bar's one spoken sentence. */
    private fun clockSpoken(): List<String> = compose.onNode(
        hasAnyAncestor(hasTestTag(WorkoutTestTags.HOLD_CLOCK)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
    ).spokenDescriptions()

    private fun word(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text), useUnmergedTree = true)

    private fun inkOf(text: String): Color = word(text).textLayout().layoutInput.style.color

    private val liveRegions = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

    @Test
    fun aRunningHoldCountsDownToItsTargetAndHasNoStop() {
        showClock(elapsed = 5, remaining = 25, total = 30, hold = true)
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed().assertHeightIsAtLeast(Metrics.logTimerRow)
        word("HOLD").assertIsDisplayed()
        // Remaining, not elapsed: the hold counts down to its target.
        word("0:25").assertIsDisplayed()
        compose.onAllNodesWithText("0:05", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(listOf("HOLD 0:25 remaining"), clockSpoken())
        assertEquals(RestCyan, inkOf("HOLD"))
        word("HOLD").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
        compose.onNodeWithTag(STOP).assertDoesNotExist()
        compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aRunningHoldNeverReadsZeroBeforeItsTarget() {
        showClock(elapsed = 30, remaining = 0, total = 30, hold = true)
        word("0:01").assertIsDisplayed()
        assertEquals(listOf("HOLD 0:01 remaining"), clockSpoken())
    }

    @Test
    fun aRunningHoldNeverCountsFromAboveItsTarget() {
        showClock(elapsed = 0, remaining = 35, total = 30, hold = true)
        word("0:30").assertIsDisplayed()
        assertEquals(listOf("HOLD 0:30 remaining"), clockSpoken())
    }

    @Test
    fun aHoldThatReachedItsTargetSaysDoneAndLogsWithTheElapsedTime() {
        showClock(elapsed = 32, remaining = 0, total = 30, hold = true, targetReached = true)
        word("HOLD DONE").assertIsDisplayed()
        word("0:32").assertIsDisplayed()
        assertEquals(listOf("HOLD DONE. Log hold with elapsed time."), clockSpoken())
        assertEquals(PrGold, inkOf("HOLD DONE"))
    }

    @Test
    fun theSetStopwatchCountsUpAndStopsFromTheBar() {
        showClock(elapsed = 12, hold = false, stoppable = true)
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed().assertHeightIsAtLeast(Metrics.logTimerRow)
        word("SET TIME").assertIsDisplayed()
        word("0:12").assertIsDisplayed()
        assertEquals(listOf("Set time 0:12 elapsed"), clockSpoken())
        val stop = compose.onNodeWithTag(STOP)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Stop"), stop.mergedTexts())
        stop.performClick()
        assertEquals(1, stops)
        compose.onAllNodes(liveRegions, useUnmergedTree = true).assertCountEquals(0)
    }

    private companion object {
        const val STOP = "workout-stop-set-clock"
    }
}
