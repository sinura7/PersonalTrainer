package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeChromeTest {
    @Test
    fun landscapeChromeLeavesRoomForTheLogAt640x360() {
        assertTrue(LandscapeChrome.isLandscape(640, 360))
        assertFalse(LandscapeChrome.isLandscape(360, 640))
        assertTrue(LandscapeChrome.compactHeader(landscape = true))
        assertTrue(LandscapeChrome.hideIdleRest(landscape = true))
        assertFalse(LandscapeChrome.hideIdleRest(landscape = false))
        assertFalse(LandscapeChrome.compactHeader(landscape = false))
        assertTrue(LandscapeChrome.logVisibleInLandscape(restRunning = false))
        assertTrue(LandscapeChrome.logVisibleInLandscape(restRunning = true))
        val idleBudget = LandscapeChrome.logBudgetDp(360, landscape = true, restRunning = false)
        assertTrue("idle landscape budget $idleBudget", idleBudget >= LandscapeChrome.LOG_MIN_DP)
        // Running rest is the 72 dp card, not the 56 dp idle row (ADR-027 decision 5).
        assertEquals(72, LandscapeChrome.REST_CARD_DP)
        assertEquals(360 - 56 - 72, LandscapeChrome.logBudgetDp(360, landscape = true, restRunning = true))
        assertEquals(240, LandscapeChrome.ringSizeDp(360))
        assertEquals(280, LandscapeChrome.ringSizeDp(800))
    }

    @Test
    fun ownedWorkoutAndRestSurfacesHonourLandscapeChrome() {
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("LandscapeChrome.compactHeader"))
        assertTrue(workout.contains("LandscapeChrome.hideIdleRest"))
        // ADR-027: there is one identity on the floor and the recommendation is its
        // own card, so the old selected-lift dock and fold-into-card switches are gone.
        val chrome = readOwned("ui/workout/LandscapeChrome.kt")
        assertFalse(chrome.contains("hideSelectedLiftDock"))
        assertFalse(chrome.contains("foldMicroRecIntoCard"))
        assertFalse(workout.contains("SelectedLiftDock("))
        val workoutDock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(workoutDock.contains("data class WorkoutDockState("))
        assertTrue(workoutDock.contains("class WorkoutDockEvents("))
        assertFalse(workoutDock.contains("CartBadge("))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        assertTrue(liftCard.contains("CountBadge("))

        val rest = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(rest.contains("val showRing = !largeText && maxHeight >= 560.dp"))
        assertTrue(rest.contains("verticalScroll(rememberScrollState())"))

        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("hideWhenIdle"))
        assertTrue(dock.contains("ringSize: Dp = REST_RING_SIZE"))
        assertTrue(readOwned("ui/components/GymButtons.kt").contains("fun DangerGymButton"))
    }

    @Test
    fun sessionPrimaryActionsSitInTheLowerDock() {
        // G-02 / Packet 2 / ADR-027: the timer surface and Log set share WorkoutDock
        // in Scaffold.bottomBar. Finish stays in the header (not a mid-set act).
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        val bottomBar = workout.indexOf("bottomBar = {")
        val workoutDock = workout.indexOf("WorkoutDock(")
        val lazy = workout.indexOf("LazyColumn(")
        assertTrue("bottomBar missing", bottomBar >= 0)
        assertTrue("WorkoutDock missing", workoutDock >= 0)
        assertTrue("LazyColumn missing", lazy >= 0)
        assertTrue("WorkoutDock must sit in the lower dock", workoutDock > bottomBar)
        assertTrue("the lift list must not contain the dock", lazy > workoutDock)
        assertTrue("the dock owns the timer surface", dock.contains("val timerSurface: @Composable () -> Unit"))
        assertTrue("the rest card is the dock's rest surface", dock.contains("RestTimerCard("))
        assertTrue("holds and the set clock keep their bar", dock.contains("SetWorkDock("))
        assertFalse(
            "RestTimerCard must not be composed in the scrolling column",
            workout.substring(lazy).contains("RestTimerCard("),
        )
        assertFalse(
            "SetWorkDock must not be composed in the scrolling column",
            workout.substring(lazy).contains("SetWorkDock("),
        )
        assertFalse("the dock is the only timer host", workout.contains("FloorTimerSlot("))
        assertFalse("RestDock is owned inside the dock", workout.contains("RestDock("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
