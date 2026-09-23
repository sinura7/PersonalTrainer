package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet E: one dock clock, rest presets in the duration sheet, and Compose
 * (the SET bar and the rest card alike) stays visual-only for RestTick.
 *
 * The finish flash, its dwell and its one announcement, the presets and ±15 of the sheet and
 * the card, and the dock's wiring of them are rendered in RestTimerCardRenderTest,
 * RestDurationSheetRenderTest and WorkoutDockTimerRenderTest, and tapped through the ViewModel
 * in FloorRestAndCoachWiringRenderTest. The haptics policy and the bans stay here.
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
        assertFalse(rest.contains("Haptics.warn(view)"))
        assertTrue(rest.contains("Haptics.tick(view)") || rest.contains("Haptics.commit(view)"))
        assertTrue(
            "Skip is HA-13 commit, not a Compose RestTick pulse",
            rest.contains("if (confirm) Haptics.commit(view)"),
        )
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse("the rest card reads the service's clock and never pulses on its own", card.contains("Haptics"))
        assertFalse(card.contains("RestTick"))
        assertFalse(card.contains("rememberInfiniteTransition"))
        assertTrue("Skip on the card is the same HA-13 commit control", card.contains("confirm = true"))
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
        val src = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(src.contains("SnapValueWheel("))
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("SnapValueWheel("))
        assertFalse("presets live in the sheet, not on the card", card.contains("RestPresetChips("))
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
