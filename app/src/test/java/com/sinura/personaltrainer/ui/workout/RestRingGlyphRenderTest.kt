package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.TalkBackPolicy
import com.sinura.personaltrainer.ui.components.RestSweepRing
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.RestCyan
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The big rest ring (the lock screen's, which says REST while it runs): a running ring wears the
 * rest glyph, in the rest's cyan, where the word REST would stand over the clock, and no word;
 * any other kicker, such as "Back to the bar" at the finish, is said in words.
 *
 * This was `RestTimerUi.kt contains "TemperIcons.FloorRest"`, read as text in
 * FloorPacket4KickerGlyphsTest, whose comment had the glyph standing beside the word; it stands
 * in its place. The glyph is decorative (the clock's spoken form says "Rest … remaining"), so it
 * is found by its ink.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestRingGlyphRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aRunningRingWearsTheRestGlyphWhereTheWordWas() {
        var finished by mutableStateOf(false)
        compose.showFloor {
            Box(modifier = Modifier.background(Pit).padding(24.dp)) {
                RestSweepRing(
                    remainingSeconds = if (finished) 0 else 90,
                    totalSeconds = 120,
                    accent = if (finished) PrGold else RestCyan,
                    clock = if (finished) "0:00" else "1:30",
                    kicker = if (finished) TalkBackPolicy.REST_FINISHED_KICKER else TalkBackPolicy.REST_RUNNING_KICKER,
                    running = true,
                    finished = finished,
                    modifier = Modifier.testTag(RING),
                    clockTestTag = CLOCK,
                )
            }
        }
        compose.onAllNodes(hasText(TalkBackPolicy.REST_RUNNING_KICKER, ignoreCase = true) and hasAnyAncestor(hasTestTag(RING)), useUnmergedTree = true)
            .assertCountEquals(0)
        val clock = compose.onNodeWithTag(CLOCK, useUnmergedTree = true).windowBounds()
        val ring = compose.onNodeWithTag(RING).windowBounds()
        val px = compose.density.density
        // Over the clock, down the ring's middle and inside its stroke: the glyph's ink, in the
        // running rest's cyan. The column is narrow enough that the stroke never enters it.
        val overClock = box(ring.center.x - HALF_COLUMN_DP * px, ring.top + INSIDE_STROKE_DP * px, ring.center.x + HALF_COLUMN_DP * px, clock.top)
        val ink = compose.drawWindow().count(overClock, RestCyan)
        assertTrue("the rest glyph is drawn over the clock, $ink cyan pixels", ink >= GLYPH_MIN_PIXELS)
        finished = true
        compose.waitForIdle()
        compose.onAllNodes(hasText(TalkBackPolicy.REST_FINISHED_KICKER, ignoreCase = true) and hasAnyAncestor(hasTestTag(RING)), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    private companion object {
        const val RING = "rest-ring"
        const val CLOCK = "rest-ring-clock"

        /** Half the 24 dp glyph and a little air. */
        const val HALF_COLUMN_DP = 16f

        /** Past the ring's 10 dp stroke and its anti-aliasing. */
        const val INSIDE_STROKE_DP = 16f

        /** A 24 dp glyph at xhdpi inks hundreds of pixels; a stray edge does not. */
        const val GLYPH_MIN_PIXELS = 100
    }
}
