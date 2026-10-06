package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Volt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock as it is drawn: its commit is a filled Volt, and nothing else in it is, whatever the
 * companion above the commit holds. One filled Volt per screen is the floor's rule: the accent
 * is the decision.
 *
 * These were lines of WorkoutDock.kt read as text (`PrimaryGymButton(`, and exactly one
 * `PrimaryGymButton\(` in the file). A count of calls says nothing about what is drawn, since a
 * second Volt could come from any fill, so the pixels say it, in seven of the companion's states:
 * the idle rest card, a running rest, the plan met, the last lift and an edit (Cancel edit) draw
 * no other Volt at all; a save that needs attention and an undo offer say their action in Volt
 * words, and draw no other Volt fill. The count is kept as a ban too (FloorCompactPresentationTest
 * and FloorImageLedHeroTest), since a render sees only the states it is shown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class DockVoltRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val logSet = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10))
    private val next = floorPrimaryAction(kind = WorkoutPrimaryKind.NEXT_EXERCISE, nextName = "Leg Curl")
    private val finish = floorPrimaryAction(kind = WorkoutPrimaryKind.FINISH)

    @Test
    fun theCommitIsTheDocksOneFilledVoltWhileLogging() {
        showDock(floorDockState(action = logSet))
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertExists()
        assertTheOneVoltIs(WorkoutTestTags.LOG_SET)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltWhileRestRuns() {
        val running = WorkoutDockTimer(show = true, restRemainingSeconds = 60, restTotalSeconds = 120, restRunning = true)
        showDock(floorDockState(action = logSet, timer = running))
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertExists()
        assertTheOneVoltIs(WorkoutTestTags.LOG_SET)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltOnceThePlanIsMet() {
        showDock(floorDockState(action = next, payload = "Leg Curl"))
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertExists()
        assertTheOneVoltIs(WorkoutTestTags.NEXT)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltAtTheLastLift() {
        showDock(floorDockState(action = finish, payload = null))
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertExists()
        assertTheOneVoltIs(WorkoutTestTags.DOCK_FINISH)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltWhileAnEditCanStandDown() {
        showDock(floorDockState(action = logSet, editing = true))
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertExists()
        assertTheOneVoltIs(WorkoutTestTags.LOG_SET)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltWhileASaveNeedsAttention() {
        showDock(floorDockState(action = logSet, error = "Could not save that set."))
        compose.onNodeWithTag(WorkoutTestTags.ERROR_DETAILS).assertExists()
        assertTheOneFilledVoltIs(WorkoutTestTags.LOG_SET)
    }

    @Test
    fun theCommitIsTheDocksOneFilledVoltWhileAnUndoIsOffered() {
        showDock(floorDockState(action = logSet, undoMessage = "Set 2 deleted"))
        compose.onNodeWithText("Set 2 deleted", useUnmergedTree = true).assertExists()
        assertTheOneFilledVoltIs(WorkoutTestTags.LOG_SET)
    }

    /** The dock on the floor's own ground, as the screen lays it. */
    private fun showDock(state: WorkoutDockState) {
        val events = floorDockEvents()
        compose.showFloor {
            Box(modifier = Modifier.background(Pit)) {
                WorkoutDock(state = state, events = events)
            }
        }
    }

    private fun assertTheOneVoltIs(commitTag: String) {
        val commit = compose.onNodeWithTag(commitTag).windowBounds()
        val frame = compose.drawWindow()
        val area = commit.width * commit.height
        val filled = frame.count(commit, Volt)
        assertTrue("the commit is filled Volt: $filled of $area pixels", filled >= area * FILLED_SHARE)
        // Everywhere else in the window, which holds nothing but the dock: no Volt at all. The
        // commit's own anti-aliased edge is left out by a couple of pixels.
        val window = box(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        val outside = frame.count(window, Volt) - frame.count(inflate(commit, EDGE_PX), Volt)
        assertEquals("Volt drawn in the dock outside its commit", 0, outside)
    }

    /**
     * The commit is filled Volt, and nothing else in the dock is: a save that needs attention and
     * an undo offer say their action in Volt words (a text button's colour, the banner's accent),
     * which are strokes, not a fill. So outside the commit no square of [FILL_SIDE_PX] pixels is
     * Volt all through; the smallest filled button would hold hundreds of them.
     */
    private fun assertTheOneFilledVoltIs(commitTag: String) {
        val commit = compose.onNodeWithTag(commitTag).windowBounds()
        val frame = compose.drawWindow()
        val area = commit.width * commit.height
        val filled = frame.count(commit, Volt)
        assertTrue("the commit is filled Volt: $filled of $area pixels", filled >= area * FILLED_SHARE)
        val around = inflate(commit, EDGE_PX)
        val volt = Array(frame.height) { y ->
            BooleanArray(frame.width) { x -> !around.contains(Offset(x + 0.5f, y + 0.5f)) && isNear(frame.getPixel(x, y), Volt) }
        }
        val block = filledSquareIn(volt, FILL_SIDE_PX)
        assertNull("a filled Volt block in the dock outside its commit, at (x, y)", block)
    }

    /** The top-left corner of a [side] × [side] square that is true all through, or null. */
    private fun filledSquareIn(grid: Array<BooleanArray>, side: Int): Pair<Int, Int>? {
        // Summed areas: any square's count in constant time.
        val height = grid.size
        val width = grid.firstOrNull()?.size ?: 0
        val sums = Array(height + 1) { IntArray(width + 1) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                sums[y + 1][x + 1] = (if (grid[y][x]) 1 else 0) + sums[y][x + 1] + sums[y + 1][x] - sums[y][x]
            }
        }
        for (y in 0..height - side) {
            for (x in 0..width - side) {
                val inside = sums[y + side][x + side] - sums[y][x + side] - sums[y + side][x] + sums[y][x]
                if (inside == side * side) return x to y
            }
        }
        return null
    }

    private fun inflate(rect: Rect, by: Float): Rect = box(rect.left - by, rect.top - by, rect.right + by, rect.bottom + by)

    private companion object {
        /** The commit's fill leaves its words and rounded corners out; the rest is Volt. */
        const val FILLED_SHARE = 0.8f

        /** Anti-aliasing reaches a pixel or two past a fill's edge. */
        const val EDGE_PX = 2f

        /** 8 dp at xhdpi: thicker than any stroke of a word, far smaller than any button. */
        const val FILL_SIDE_PX = 16
    }
}
