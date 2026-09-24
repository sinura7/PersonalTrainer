package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Volt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The effort chips as they are drawn, where semantics cannot look: a chosen value wears a Volt
 * edge; a recommended one wears a small Volt dot in its top-end corner and the plain hairline
 * edge, so a suggestion never reads as a choice; and a value that is both chosen and recommended
 * shows only the choice. The dot is the non-colour signal ADR-023 asks for, and sits in the
 * chip's corner so it costs the label no width.
 *
 * These were lines of InstrumentChip.kt read as text (`if (focused || selected) Volt else
 * Hairline`, `if (recommended && !selected) {`, `.size(Metrics.markDot)`,
 * `.align(Alignment.TopEnd)`). The chips are composed alone, as the effort track composes them;
 * the track's own rule that its recommended value is never also its selected one is
 * RpeSelectorRenderTest's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RpeChipDrawingRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val px: Float get() = compose.density.density

    @Test
    fun aChosenEffortWearsAVoltEdgeAndNoDot() {
        showChips()
        val chosen = chip(CHOSEN)
        assertTrue("the chosen chip's edge is Volt", edgeShare(chosen) >= EDGE_SHARE)
        assertEquals("no dot on a chosen chip", 0, voltIn(dotSpot(chosen)))
    }

    @Test
    fun aRecommendedEffortWearsACornerDotAndAPlainEdge() {
        showChips()
        val recommended = chip(RECOMMENDED)
        assertEquals("a suggestion never wears the chosen edge", 0f, edgeShare(recommended))
        // Nor any tint of it: its edge is drawn pixel for pixel as a plain chip's.
        val plainEdge = edgeOf(chip(PLAIN))
        val recommendedEdge = edgeOf(recommended)
        assertEquals(plainEdge.size, recommendedEdge.size)
        val differing = plainEdge.indices.count { plainEdge[it] != recommendedEdge[it] }
        assertEquals("edge pixels unlike a plain chip's, of ${plainEdge.size}", 0, differing)
        val corner = box(recommended.center.x, recommended.top, recommended.right, recommended.center.y)
        val dot = checkNotNull(compose.drawWindow().voltBox(corner)) { "no dot in the top-end corner" }
        assertEquals("dot width in dp", Metrics.markDot.value, dot.width / px, DOT_SLACK_DP)
        assertEquals("dot height in dp", Metrics.markDot.value, dot.height / px, DOT_SLACK_DP)
        assertEquals("inset from the chip's end", Metrics.space1.value, (recommended.right - dot.right) / px, DOT_SLACK_DP)
        assertEquals("inset from the chip's top", Metrics.space1.value, (dot.top - recommended.top) / px, DOT_SLACK_DP)
        // The dot is all the Volt the chip wears.
        assertEquals(voltIn(dot), voltIn(recommended))
        assertEquals("a plain chip wears no Volt", 0, voltIn(chip(PLAIN)))
    }

    @Test
    fun aRecommendationThatIsAlsoChosenShowsOnlyTheChoice() {
        showChips()
        val both = chip(BOTH)
        assertTrue("chosen: the Volt edge", edgeShare(both) >= EDGE_SHARE)
        assertEquals("and no dot beside it", 0, voltIn(dotSpot(both)))
    }

    private fun showChips() {
        compose.showFloor {
            Row(
                modifier = Modifier.width(360.dp).background(Pit).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InstrumentChip(label = "7", selected = false, onClick = {}, modifier = Modifier.weight(1f).testTag(PLAIN), role = Role.RadioButton)
                InstrumentChip(label = "8", selected = false, onClick = {}, modifier = Modifier.weight(1f).testTag(RECOMMENDED), recommended = true, role = Role.RadioButton)
                InstrumentChip(label = "9", selected = true, onClick = {}, modifier = Modifier.weight(1f).testTag(CHOSEN), role = Role.RadioButton)
                InstrumentChip(label = "10", selected = true, onClick = {}, modifier = Modifier.weight(1f).testTag(BOTH), recommended = true, role = Role.RadioButton)
            }
        }
    }

    private fun chip(tag: String): Rect = compose.onNodeWithTag(tag).windowBounds()

    /** How much of the chip's start edge, down its straight middle, is Volt. */
    private fun edgeShare(chip: Rect): Float {
        val frame = compose.drawWindow()
        val x = (chip.left + 0.5f).toInt()
        val ys = (chip.top + CORNER_CLEAR_DP * px).toInt() until (chip.bottom - CORNER_CLEAR_DP * px).toInt()
        return ys.count { isNear(frame.getPixel(x, it), Volt) }.toFloat() / ys.count()
    }

    /**
     * The chip's start edge and its bottom edge, down and along their straight runs, two pixels
     * deep (a 1 dp stroke at xhdpi), as drawn. The chips in the row share a height, so two chips'
     * edges line up pixel for pixel; the dot's top-end corner is left out.
     */
    private fun edgeOf(chip: Rect): List<Int> {
        val frame = compose.drawWindow()
        val clear = (CORNER_CLEAR_DP * px).toInt()
        val left = chip.left.toInt()
        val top = chip.top.toInt()
        val bottom = chip.bottom.toInt()
        val right = chip.right.toInt()
        val down = (0 until STROKE_PX).flatMap { dx -> (top + clear until bottom - clear).map { y -> frame.getPixel(left + dx, y) } }
        val along = (1..STROKE_PX).flatMap { dy -> (left + clear until right - clear).map { x -> frame.getPixel(x, bottom - dy) } }
        return down + along
    }

    /** Where the recommendation's dot would sit: the top-end corner, inside the chip's edge. */
    private fun dotSpot(chip: Rect): Rect {
        val inset = Metrics.space1.value * px
        val side = Metrics.markDot.value * px
        return box(chip.right - inset - side, chip.top + inset, chip.right - inset, chip.top + inset + side)
    }

    private fun voltIn(area: Rect): Int = compose.drawWindow().count(area, Volt)

    /** The bounding box of the Volt drawn inside [area], or null when there is none. */
    private fun Bitmap.voltBox(area: Rect): Rect? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in area.top.toInt() until area.bottom.toInt()) {
            for (x in area.left.toInt() until area.right.toInt()) {
                if (isNear(getPixel(x, y), Volt)) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else box(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    private companion object {
        const val PLAIN = "chip-plain"
        const val RECOMMENDED = "chip-recommended"
        const val CHOSEN = "chip-chosen"
        const val BOTH = "chip-chosen-and-recommended"

        /** Below the rounded corner, where the edge runs straight. */
        const val CORNER_CLEAR_DP = 8f

        /** A 1 dp stroke at xhdpi is two pixels; the column half a pixel in lies on it all the way. */
        const val EDGE_SHARE = 0.9f

        /** A 1 dp edge at xhdpi. */
        const val STROKE_PX = 2

        /** A round dot's anti-aliased rim is worth about half a dp either way. */
        const val DOT_SLACK_DP = 1f
    }
}
