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
        assertTrue(LandscapeChrome.foldMicroRecIntoCard(landscape = true))
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
        assertTrue(workout.contains("LandscapeChrome.foldMicroRecIntoCard"))
        assertTrue(workout.contains("WorkoutLiftCardState"))
        assertTrue(workout.contains("WorkoutLiftCardEvents"))
        assertTrue(workout.contains("CountBadge("))
        assertFalse(workout.contains("CartBadge("))

        val rest = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(rest.contains("LandscapeChrome.ringSizeDp"))
        assertTrue(rest.contains("ringSize = LandscapeChrome.ringSizeDp"))

        val dock = readOwned("ui/components/Common.kt")
        assertTrue(dock.contains("hideWhenIdle"))
        assertTrue(dock.contains("fun DangerGymButton"))
        assertTrue(dock.contains("ringSize: Dp = REST_RING_SIZE"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
