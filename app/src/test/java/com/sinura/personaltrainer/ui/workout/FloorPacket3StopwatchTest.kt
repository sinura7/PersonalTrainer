package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 3: manual set stopwatch in the dock timer slot.
 */
class FloorPacket3StopwatchTest {
    @Test
    fun dockOffersStartAndStopWithoutASecondVolt() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.manualSetStopwatch())
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("SetStopwatchCopy.START"))
        assertTrue(dock.contains("SetStopwatchCopy.STOP"))
        assertTrue(dock.contains("workout-start-set-clock"))
        assertTrue(dock.contains("workout-stop-set-clock"))
        assertTrue(dock.contains("offerSetClock"))
        val idleStart = dock.indexOf("fun RestIdleRow")
        val idleEnd = dock.indexOf("fun RestLinearTrack")
        val idle = dock.substring(idleStart, idleEnd)
        assertFalse("Time set must not be a filled Volt", idle.contains("PrimaryGymButton"))
    }

    @Test
    fun logBarWiresTheStopwatchAndScreenDoesNotStopRestToStartIt() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("onStartSetClock"))
        assertTrue(bar.contains("onStopSetClock"))
        assertTrue(bar.contains("stopwatchRunning"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("onStartSetClock = viewModel::startSetStopwatch"))
        assertTrue(screen.contains("offerSetClock ="))
        assertTrue(screen.contains("state.offerSetClock"))
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        val start = vm.indexOf("fun startSetStopwatch")
        val stop = vm.indexOf("fun stopSetStopwatch")
        assertTrue(start >= 0 && stop > start)
        val body = vm.substring(start, stop)
        assertFalse(
            "starting the set clock must not cancel a pending rest alarm",
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
