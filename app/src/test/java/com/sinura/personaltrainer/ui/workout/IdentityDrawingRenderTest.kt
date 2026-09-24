package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Surface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The current lift's identity as it is drawn on the real screen: no card around it, so where
 * nothing is drawn inside it the floor's own ground shows through, beside the picture and in the
 * row's far corner; and its one fill is the small mark on the picture's corner that says the
 * picture opens Details.
 *
 * These were counts over ExerciseHeader.kt read as text (one `Surface2)`, one `.background(`,
 * and that one `.background(Surface2)`), which said nothing about what the lifter sees: a card
 * could come from any fill. The pixels can say it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class IdentityDrawingRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
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

    @Test
    fun noCardIsDrawnAroundTheIdentity() {
        showFloor()
        val px = compose.density.density
        val row = compose.onNodeWithTag(WorkoutTestTags.liftCard(FLOOR_LIFT_ID)).windowBounds()
        val picture = compose.onNodeWithTag(WorkoutTestTags.DETAILS).windowBounds()
        val floor = compose.onNodeWithTag(WorkoutTestTags.CONTENT).windowBounds()
        val frame = compose.drawWindow()
        assertTrue("the floor's ground is Pit", isNear(frame.getPixel((floor.left + GUTTER_DP * px).toInt(), row.center.y.toInt()), Pit, GROUND_TOLERANCE))
        // Inside the identity, where nothing is drawn: the gap between the picture and the words,
        // and the far top corner of the row, above the words. Any card behind them would show.
        val gap = box(picture.right + 2f * px, row.top + 2f * px, picture.right + (Metrics.space3.value - 2f) * px, row.bottom - 2f * px)
        val corner = box(row.right - CORNER_DP * px, row.top, row.right, row.top + Metrics.space1.value * px)
        listOf("between the picture and the words" to gap, "in the row's far corner" to corner).forEach { (where, spot) ->
            val area = (spot.width.toInt() * spot.height.toInt())
            val ground = frame.count(spot, Pit, GROUND_TOLERANCE)
            assertTrue("the floor shows through $where: $ground of $area pixels", area > 0 && ground == area)
        }
    }

    @Test
    fun theDetailsMarkIsTheIdentitysOneFill() {
        showFloor()
        val px = compose.density.density
        val picture = compose.onNodeWithTag(WorkoutTestTags.DETAILS).windowBounds()
        // The mark: 24 dp, in the picture's bottom-end corner, 4 dp in.
        val inset = Metrics.space1.value * px
        val side = Metrics.equipmentGlyph.value * px
        val mark = box(picture.right - inset - side, picture.bottom - inset - side, picture.right - inset, picture.bottom - inset)
        val filled = compose.drawWindow().count(mark, Surface2, GROUND_TOLERANCE)
        assertTrue("the mark is a Surface2 disc: $filled of ${side * side} pixels", filled >= side * side * MARK_SHARE)
    }

    private fun showFloor() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
    }

    private companion object {
        /** Pit and Surface2 differ by some fifteen per channel; a fill is flat to within a few. */
        const val GROUND_TOLERANCE = 4

        /** Into the floor's 16 dp gutter, clear of anything drawn. */
        const val GUTTER_DP = 4f

        /** The row's last few dp, level with its top padding. */
        const val CORNER_DP = 24f

        /** A disc fills π/4 of its box; its hairline edge and the chevron take a little more. */
        const val MARK_SHARE = 0.5f
    }
}
