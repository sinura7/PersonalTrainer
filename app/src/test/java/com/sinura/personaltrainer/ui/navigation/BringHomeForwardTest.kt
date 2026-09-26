package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit X6, R4, on the real navigation library: a reminder tap with nothing live must find Home
 * in front. Home's tab alone brought back whatever was left over Home, so Home, not drawn, took
 * the tap only once that screen was left. What is over Home is closed, except a screen that asks
 * before it is left; other tabs keep theirs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BringHomeForwardTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = Route.Home.path) {
                composable(Route.Home.path) { Text("Home") }
                composable(Route.Routines.path) { Text("Plan") }
                composable(Route.WorkoutSummary.path) { Text("Summary") }
                composable(Route.SessionDetail.path) { Text("Session") }
                composable(Route.ActivityComposer.path) { Text("Composer") }
                composable(Route.RoutineEditor.path) { Text("Editor") }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSummaryOverHomeIsClosedAndHomeIsInFront() {
        go(Route.WorkoutSummary.create("s1"))
        go(Route.SessionDetail.create("s1"))

        assertEquals(HomeReach.IN_FRONT, bring())
        assertEquals(Route.Home.path, current())
        assertNull("nothing is left under Home", nav.previousBackStackEntry)
        assertFalse("the summary is gone, not saved to come back", has(Route.WorkoutSummary.path))
    }

    @Test
    fun anActivityBeingLoggedIsKept() {
        go(Route.ActivityComposer.create("past"))

        assertEquals(HomeReach.BEHIND_AN_EDIT, bring())
        assertEquals(Route.ActivityComposer.path, current())
    }

    @Test
    fun aRoutineBeingEditedIsKept() {
        go(Route.RoutineEditor.create("r1"))

        assertEquals(HomeReach.BEHIND_AN_EDIT, bring())
        assertEquals(Route.RoutineEditor.path, current())
    }

    /** The trunk defect through another tab: Home's tab restored the summary left under it. */
    @Test
    fun aSummaryLeftUnderHomesTabIsClosedWhenComingFromAnotherTab() {
        go(Route.WorkoutSummary.create("s1"))
        tab(Route.Routines.path)

        assertEquals(HomeReach.IN_FRONT, bring())
        assertEquals(Route.Home.path, current())
    }

    /** Another tab's own screens are its to keep: going back to that tab finds them. */
    @Test
    fun anEditorOnAnotherTabIsLeftThereAndHomeIsInFront() {
        tab(Route.Routines.path)
        go(Route.RoutineEditor.create("r1"))

        assertEquals(HomeReach.IN_FRONT, bring())
        assertEquals(Route.Home.path, current())

        tab(Route.Routines.path)
        assertEquals(Route.RoutineEditor.path, current())
    }

    @Test
    fun homeAlreadyInFrontStaysAsItIs() {
        assertEquals(HomeReach.IN_FRONT, bring())
        assertEquals(Route.Home.path, current())
        assertNull(nav.previousBackStackEntry)
    }

    private fun go(route: String) {
        compose.runOnUiThread { nav.navigate(route) }
        compose.waitForIdle()
    }

    private fun tab(path: String) {
        compose.runOnUiThread { nav.goToTab(path) }
        compose.waitForIdle()
    }

    private fun bring(): HomeReach {
        var reach: HomeReach? = null
        compose.runOnUiThread { reach = nav.bringHomeForward() }
        compose.waitForIdle()
        return checkNotNull(reach)
    }

    private fun current(): String? = nav.currentBackStackEntry?.destination?.route

    private fun has(route: String): Boolean = runCatching { nav.getBackStackEntry(route) }.isSuccess
}
