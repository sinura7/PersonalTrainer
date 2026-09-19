package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 3: manual set stopwatch in the dock timer slot. Time set is a
 * quiet control on the idle rest card; Stop rides the SET instrument bar.
 */
class FloorPacket3StopwatchTest {
    @Test
    fun dockOffersStartAndStopWithoutASecondVolt() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.manualSetStopwatch())
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(card.contains("SetStopwatchCopy.START"))
        assertTrue(card.contains("SetStopwatchCopy.START_SPOKEN"))
        assertTrue(card.contains("WorkoutTestTags.START_SET_CLOCK"))
        assertTrue(card.contains("offerSetClock -> listOf(SetStopwatchCopy.START, START_REST)"))
        assertTrue(card.contains("if (offerSetClock) {"))
        assertTrue("Time set is a quiet RestControl", card.contains("RestControl("))
        assertFalse("Time set must not be a filled Volt", card.contains("PrimaryGymButton"))
        val bar = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(bar.contains("SetStopwatchCopy.STOP"))
        assertTrue(bar.contains("workout-stop-set-clock"))
        assertTrue(bar.contains("workout-sheet-start-set-clock"))
        val holdStart = bar.indexOf("fun SetWorkDock")
        val holdEnd = bar.indexOf("@Composable", holdStart)
        assertTrue(holdStart >= 0 && holdEnd > holdStart)
        val hold = bar.substring(holdStart, holdEnd)
        assertTrue("Stop rides the SET bar", hold.contains("onStop = onStop"))
        assertFalse(hold.contains("PrimaryGymButton"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("const val START_SET_CLOCK = \"workout-start-set-clock\""))
        assertTrue(screen.contains("const val STOP_SET_CLOCK = \"workout-stop-set-clock\""))
        assertTrue(screen.contains("const val SHEET_START_SET_CLOCK = \"workout-sheet-start-set-clock\""))
    }

    @Test
    fun logBarWiresTheStopwatchAndStartingItStopsRest() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("onStartSetClock"))
        assertTrue(dock.contains("onStopSetClock"))
        assertTrue(dock.contains("stopwatchRunning"))
        assertTrue(dock.contains("onStartSetClock = events.onStartSetClock"))
        assertTrue(
            "Stop belongs to the stopwatch, never to a hold",
            dock.contains("onStop = events.onStopSetClock.takeIf { timer.stopwatchRunning && !holdActive }"),
        )
        assertTrue(dock.contains("timer.stopwatchRunning -> \"Set time \${RestTimer.formatClock(timer.stopwatchElapsedSeconds)}\""))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("onStartSetClock = viewModel::startSetStopwatch"))
        assertTrue(screen.contains("onStopSetClock = viewModel::stopSetStopwatch"))
        assertTrue(screen.contains("offerSetClock ="))
        assertTrue(screen.contains("state.offerSetClock"))
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
