package com.sinura.personaltrainer.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.navigation.InstrumentNavBar
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.navigation.shippingTabs
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.workout.settle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual agenda, action and five-tab components; isolated callbacks, no owner or Room data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HomeAgendaCompositionRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var quiet by mutableStateOf(false)
    private var live by mutableStateOf(false)
    private var starts = 0
    private var plannedStarts = 0

    @Test @Config(fontScale = 1f) fun small10() = journey("360x640-font10")
    @Test @Config(fontScale = 1.6f) fun small16() = journey("360x640-font16")
    @Test @Config(fontScale = 2f) fun small20() = journey("360x640-font20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standard10() = journey("412x840-font10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standard16() = journey("412x840-font16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standard20() = journey("412x840-font20")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscape10() = journey("800x360-font10")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscape16() = journey("800x360-font16")
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscape20() = journey("800x360-font20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun adaptive10() = journey("600x960-font10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun adaptive16() = journey("600x960-font16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun adaptive20() = journey("600x960-font20")
    @Test @Config(fontScale = 2f)
    fun rtlReducedMotion() = journey("360x640-rtl-font20-reduced", rtl = true)

    private fun journey(profile: String, rtl: Boolean = false) {
        val bench = lift("Incline Dumbbell Bench Press")
        val row = lift("Seated Cable Row")
        val longName = bench.copy(name = LONG_NAME)
        val labels = DayBlockCopy.preview(listOf(bench.name, row.name), 15)
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
                LocalTodayEpochDay provides TODAY,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Pit) {
                        Column(Modifier.fillMaxSize()) {
                            HomeBoardLayout(
                                modifier = Modifier.weight(1f),
                                startRank = homeStartRank(HomeToday.Surface.AGENDA, live, quiet,
                                    trainingToday = true, hasPlan = true, loggedToday = false, setupComplete = true),
                                onStartFree = { starts++ },
                            ) {
                                item("masthead") {
                                    Column {
                                        HomeMasthead(epochDay = TODAY, headline = "Strength day")
                                        WeekStrip(cells = WeekBoard.forWeek(TODAY - 6, emptyList(), emptyList()).map { cell ->
                                            if (cell.epochDay == TODAY) cell.copy(fill = DayFill.NONE, caption = "12 activities", plannedCount = 12) else cell
                                        },
                                            today = TODAY, selected = TODAY, onSelectDay = {}, flat = true)
                                    }
                                }
                                item("planned") {
                                    DayBlock(title = "Upper B", lines = labels, exercises = listOf(bench, row), action = "Start",
                                        onOpen = { plannedStarts++ }, modifier = Modifier.testTag("planned"))
                                }
                                item("long-name") {
                                    DayBlock(title = "A complete exercise name", lines = DayBlockCopy.preview(listOf(longName.name)),
                                        exercises = listOf(longName), action = "Start", onOpen = { plannedStarts++ },
                                        modifier = Modifier.testTag("long-name"))
                                }
                                items(10) { index ->
                                    DayBlock(title = "Session ${index + 2}", lines = DayBlockCopy.preview(listOf(bench.name)),
                                        exercises = listOf(bench), action = "Start", onOpen = { plannedStarts++ },
                                        modifier = Modifier.testTag("later-$index"))
                                }
                            }
                            InstrumentNavBar(tabs = shippingTabs, isSelected = { it.route == Route.Home }, onSelect = {})
                        }
                    }
                }
            }
        }
        compose.settle()
        val actual = compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().fetchSemanticsNode()
        val density = actual.layoutInfo.density.density
        val navigation = compose.onNodeWithTag("primary-navigation").fetchSemanticsNode().boundsInWindow
        assertTrue("Start's real touch target remains above navigation", actual.boundsInWindow.bottom <= navigation.top + 1f)
        assertTrue("Start has a full 48 dp target", actual.size.height / density >= Metrics.touchMin.value)
        assertTrue("Start is fully inside the actual window", actual.boundsInWindow.top >= 0f)
        assertEquals("one generic Start", 1, compose.onAllNodes(hasTestTag(HomeTags.START)).fetchSemanticsNodes().size)
        shippingTabs.forEach { tab ->
            val target = compose.onNodeWithTag("navigation-${tab.route.path}").assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${tab.label} stays reachable beside the anchored action", target.size.height / density >= Metrics.touchMin.value)
            assertTrue("${tab.label} retains a full touch width", target.size.width / density >= Metrics.touchMin.value)
        }

        capture(profile, "agenda")
        compose.onNodeWithTag(HomeTags.BOARD).performScrollToNode(hasTestTag("long-name"))
        compose.settle()
        val layouts = mutableListOf<TextLayoutResult>()
        val text = compose.onNodeWithText("1 $LONG_NAME", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(checkNotNull(text.config[SemanticsActions.GetTextLayoutResult].action).invoke(layouts))
        assertFalse("the complete name wraps instead of being ellipsized", layouts.single().hasVisualOverflow)
        capture(profile, "complete-name")

        compose.onNodeWithTag(HomeTags.BOARD).performScrollToNode(hasTestTag("later-9"))
        compose.settle()
        val after = compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().fetchSemanticsNode()
        assertEquals("the generic action does not leave with the first session", actual.boundsInWindow, after.boundsInWindow)
        compose.onNodeWithTag(HomeTags.START).performTouchInput { click(center) }
        compose.settle()
        assertEquals("a native tap dispatches the generic command once", 1, starts)
        assertEquals("generic Start never starts a planned row", 0, plannedStarts)
        capture(profile, "scrolled")

        compose.runOnUiThread { quiet = true }
        compose.settle()
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().performTouchInput { click(center) }
        compose.settle()
        assertEquals(2, starts)
        compose.runOnUiThread { live = true }
        compose.settle()
        compose.onNodeWithTag(HomeTags.START).assertDoesNotExist()
        compose.onNodeWithTag("primary-navigation").assertIsDisplayed()
        capture(profile, "live-start-suppressed")
    }

    private fun lift(name: String): Exercise {
        val seed = DefaultExercises.catalog().first { it.name == name }
        return Exercise(id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "", isCustom = false,
            equipment = seed.equipment, loadType = seed.loadType, movementKey = seed.movementKey,
            imageKey = seed.imageKey, muscles = seed.credits)
    }

    private fun capture(profile: String, stage: String) {
        val bitmap = compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/home-agenda-composition/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue(file.length() > 100)
        } finally { bitmap.recycle() }
    }

    private companion object {
        const val TODAY = 20_737L
        const val LONG_NAME = "Paused incline dumbbell bench press with deliberate control through the complete movement"
    }
}
