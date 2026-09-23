package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's rest card on a 360 dp phone with its words measured for real (native graphics):
 * beside Time set and Start rest, and beside −15 / +15 / Skip, its text column is about
 * 80 dp wide. At normal text the controls sit beside the clock, every word stays whole on its
 * one line there, and the card keeps its height when rest starts, or the floor above it jumps.
 *
 * W1b first tried "PLANNED REST" over the idle clock, which this width cut to "PLANNED…",
 * and "Planned rest · 2:00" under the running one, which wrapped and grew the card; the card
 * says "Planned" and "Planned 2:00" instead, and the rest page carries the full words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestTimerCardFitRenderTest {
    @get:Rule val compose = createComposeRule()

    private val running = mutableStateOf(false)

    private fun showCard() {
        compose.showFloor {
            Box(modifier = Modifier.width(360.dp).padding(horizontal = Metrics.gutter)) {
                RestTimerCard(
                    remainingSeconds = 92,
                    totalSeconds = 120,
                    running = running.value,
                    completedTimerId = null,
                    afterWarmup = false,
                    offerSetClock = true,
                    onSkip = {},
                    onStart = {},
                    onNudge = {},
                    onEditDuration = {},
                    onStartSetClock = {},
                    onOpenRest = {},
                )
            }
        }
    }

    /** The control tagged [controlTag] sits to the right of the clock's tile, on its row. */
    private fun assertBesideTheClock(cardTag: String, clock: String, controlTag: String) {
        val tile = compose.onNode(hasClickAction() and hasText(clock) and hasAnyAncestor(hasTestTag(cardTag))).getBoundsInRoot()
        val control = compose.onNodeWithTag(controlTag).getBoundsInRoot()
        assertTrue("$controlTag sits beside the clock, not under it", control.left >= tile.right && control.top < tile.bottom)
    }

    private fun assertWholeOnOneLine(text: String) {
        val layout = compose.onNode(hasText(text), useUnmergedTree = true).textLayout()
        assertEquals("\"$text\" on one line", 1, layout.lineCount)
        assertFalse("\"$text\" is not cut short", layout.isLineEllipsized(0))
        assertTrue("\"$text\" fits its width", layout.fitsItsWidth())
    }

    @Test
    fun besideItsControlsTheCardsWordsStayWholeAndItKeepsItsHeightWhenRestStarts() {
        showCard()
        assertWholeOnOneLine("REST")
        assertWholeOnOneLine("2:00")
        assertWholeOnOneLine("Planned")
        assertBesideTheClock(cardTag = WorkoutTestTags.REST_IDLE, clock = "2:00", controlTag = WorkoutTestTags.START_SET_CLOCK)
        val idle = compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).getBoundsInRoot().height
        running.value = true
        compose.waitForIdle()
        assertWholeOnOneLine("REST")
        assertWholeOnOneLine("1:32")
        assertWholeOnOneLine("Planned 2:00")
        assertBesideTheClock(cardTag = WorkoutTestTags.REST_BAR, clock = "1:32", controlTag = WorkoutTestTags.REST_MINUS)
        assertEquals(idle, compose.onNodeWithTag(WorkoutTestTags.REST_BAR).getBoundsInRoot().height)
    }
}
