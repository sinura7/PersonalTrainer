package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet E: one dock clock, rest presets, Compose is visual-only for RestTick.
 */
class FloorPacketEClockTest {
    @Test
    fun restTimerUiDoesNotCallHapticsForRestTick() {
        val rest = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(rest.contains("RestTick.isWarn"))
        assertFalse(
            "Compose must not pulse RestTick haptics",
            rest.contains("Haptics.warn(view)") && rest.contains("safeRemaining"),
        )
        val tickBlock = rest.substringAfter("fun RestDock", rest)
        assertFalse(tickBlock.contains("Haptics.warn(view)"))
        assertTrue(rest.contains("Haptics.tick(view)") || rest.contains("Haptics.commit(view)"))
        assertTrue(
            "Skip is HA-13 commit, not a Compose RestTick pulse",
            rest.contains("if (confirm) Haptics.commit(view)"),
        )
        val motion = readOwned("ui/theme/Motion.kt")
        assertTrue(motion.contains("CLOCK_SWAP_MS = 180"))
        assertTrue(motion.contains("REST_DONE_MS = 240"))
        val service = readOwned("timer/RestTimerService.kt")
        assertTrue(service.contains("RestTimerAlerts.tick("))
        val alerts = readOwned("timer/RestTimerAlerts.kt")
        assertTrue(alerts.contains("RestTick.pulseMs(second)"))
        assertTrue(alerts.contains("holdTargetTone"))
    }

    @Test
    fun floorRestUsesPresetsNotAWheel() {
        assertFalse(com.sinura.personaltrainer.domain.FloorCompactChrome.restLengthIsInlineWheel())
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(dock.contains("SnapValueWheel("))
        assertTrue(dock.contains("RestPresetChips("))
        assertTrue(dock.contains("onNudgeRest"))
        assertTrue(dock.contains("workout-rest-minus"))
        assertTrue(dock.contains("workout-rest-plus"))
        assertTrue(dock.contains("CustomRestDialog("))
        assertTrue(dock.contains("RestHonestyRow("))
    }

    @Test
    fun startSetStopwatchCancelsRestGeneration() {
        val vm = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        val start = vm.indexOf("fun startSetStopwatch")
        val stop = vm.indexOf("fun stopSetStopwatch")
        assertTrue(start >= 0 && stop > start)
        val body = vm.substring(start, stop)
        assertTrue(
            "starting the set clock must cancel a pending rest alarm",
            body.contains("restTimer.stop()"),
        )
        val hold = vm.indexOf("fun startHoldSet")
        val holdBody = vm.substring(hold, start)
        assertTrue(holdBody.contains("restTimer.stop()"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("SetStopwatchCopy.SWITCH_TITLE"))
        assertTrue(screen.contains("confirmStopTimingAndSwitch"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
