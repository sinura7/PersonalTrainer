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
        assertTrue(LandscapeChrome.hideSelectedLiftDock(landscape = true))
        assertTrue(LandscapeChrome.hideSelectedLiftDock(landscape = false))
        assertTrue(LandscapeChrome.foldMicroRecIntoCard(landscape = true))
        assertTrue(LandscapeChrome.foldMicroRecIntoCard(landscape = false))
        assertFalse(LandscapeChrome.compactHeader(landscape = false))
        assertTrue(LandscapeChrome.logVisibleInLandscape(restRunning = false))
        assertTrue(LandscapeChrome.logVisibleInLandscape(restRunning = true))
        val idleBudget = LandscapeChrome.logBudgetDp(360, landscape = true, restRunning = false)
        assertTrue("idle landscape budget $idleBudget", idleBudget >= LandscapeChrome.LOG_MIN_DP)
        assertEquals(240, LandscapeChrome.ringSizeDp(360))
        assertEquals(280, LandscapeChrome.ringSizeDp(800))
    }

    @Test
    fun ownedWorkoutAndRestSurfacesHonourLandscapeChrome() {
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("LandscapeChrome.compactHeader"))
        assertTrue(workout.contains("LandscapeChrome.hideIdleRest"))
        assertTrue(workout.contains("LandscapeChrome.hideSelectedLiftDock"))
        assertTrue(workout.contains("LandscapeChrome.foldMicroRecIntoCard"))
        val card = readOwned("ui/workout/WorkoutLiftCard.kt")
        assertTrue(card.contains("WorkoutLiftCardState"))
        assertTrue(card.contains("WorkoutLiftCardEvents"))
        assertFalse(card.contains("CartBadge("))
        val liftCard = readOwned("ui/components/LiftCard.kt")
        assertTrue(liftCard.contains("CountBadge("))

        val rest = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(rest.contains("LandscapeChrome.ringSizeDp"))
        assertTrue(rest.contains("ringSize = LandscapeChrome.ringSizeDp"))

        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("hideWhenIdle"))
        assertTrue(dock.contains("ringSize: Dp = REST_RING_SIZE"))
        assertTrue(readOwned("ui/components/GymButtons.kt").contains("fun DangerGymButton"))
    }

    @Test
    fun sessionPrimaryActionsSitInTheLowerDock() {
        // G-02 / Packet 2: timer slot and Log set share LogBar in
        // Scaffold.bottomBar. Finish stays in the header (not a mid-set act).
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        val bottomBar = workout.indexOf("bottomBar")
        val selectedDock = workout.indexOf("SelectedLiftDock(")
        val logBar = workout.indexOf("LogBar(")
        val lazy = workout.indexOf("LazyColumn(")
        assertTrue("bottomBar missing", bottomBar >= 0)
        assertTrue("SelectedLiftDock missing", selectedDock >= 0)
        assertTrue("LogBar missing", logBar >= 0)
        assertTrue("LazyColumn missing", lazy >= 0)
        assertTrue("SelectedLiftDock must sit in the lower dock", selectedDock > bottomBar)
        assertTrue("LogBar must sit in the lower dock", logBar > bottomBar)
        assertTrue("the lift list must not contain LogBar", lazy > logBar)
        assertTrue("LogBar owns the timer slot", bar.contains("FloorTimerSlot("))
        assertFalse(
            "FloorTimerSlot must not be composed in the scrolling column",
            workout.substring(lazy).contains("FloorTimerSlot("),
        )
        assertFalse(
            "SelectedLiftDock must not be composed in the scrolling column",
            workout.substring(lazy).contains("SelectedLiftDock("),
        )
        assertFalse(
            "RestDock is owned through FloorTimerSlot inside LogBar",
            workout.contains("RestDock("),
        )
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
