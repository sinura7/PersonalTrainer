package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 3: manual set stopwatch in the dock timer slot. Time set is a
 * quiet control on the idle rest card; Stop rides the SET instrument bar.
 *
 * Time set on the card and in the sheet, Stop on the SET bar, the dock's "Set time" clock
 * and the screen's wiring to the ViewModel's stopwatch are rendered in
 * RestTimerCardRenderTest, RestDurationSheetRenderTest, SetWorkDockRenderTest,
 * WorkoutDockTimerRenderTest and FloorRestAndCoachWiringRenderTest.
 */
class FloorPacket3StopwatchTest {
    @Test
    fun dockOffersStartAndStopWithoutASecondVolt() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.manualSetStopwatch())
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse("Time set must not be a filled Volt", card.contains("PrimaryGymButton"))
        val bar = readOwned("ui/components/RestTimerUi.kt")
        val holdStart = bar.indexOf("fun SetWorkDock")
        val holdEnd = bar.indexOf("@Composable", holdStart)
        assertTrue(holdStart >= 0 && holdEnd > holdStart)
        val hold = bar.substring(holdStart, holdEnd)
        assertFalse(hold.contains("PrimaryGymButton"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("const val STOP_SET_CLOCK = \"workout-stop-set-clock\""))
        assertTrue(screen.contains("const val SHEET_START_SET_CLOCK = \"workout-sheet-start-set-clock\""))
    }

    @Test
    fun logBarWiresTheStopwatchAndStartingItStopsRest() {
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("SetStopwatchCopy.SWITCH_TITLE"))
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        val start = vm.indexOf("fun startSetStopwatch")
        val stop = vm.indexOf("fun stopSetStopwatch")
        assertTrue(start >= 0 && stop > start)
        val body = vm.substring(start, stop)
        assertTrue(
            "starting the set clock must cancel a pending rest alarm",
            body.contains("restTimer.stop()"),
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
