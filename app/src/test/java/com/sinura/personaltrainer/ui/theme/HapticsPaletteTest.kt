package com.sinura.personaltrainer.ui.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-06: tick light, last-3s medium, complete strong, log-set click,
 * error double. System haptic intensity stays the off switch.
 */
class HapticsPaletteTest {
    @Test
    fun paletteNamesTheFiveGymActs() {
        val haptics = readOwned("ui/theme/Haptics.kt")
        assertTrue(haptics.contains("fun tick("))
        assertTrue(haptics.contains("CLOCK_TICK"))
        assertTrue(haptics.contains("fun warn("))
        assertTrue(haptics.contains("CONTEXT_CLICK"))
        assertTrue(haptics.contains("fun commit("))
        assertTrue(haptics.contains("fun reject("))
        assertTrue(haptics.contains("postDelayed"))
        assertTrue(haptics.contains("ERROR_BEAT_GAP_MS"))
        assertTrue(haptics.contains("fun celebrate("))
        assertFalse(haptics.contains("LocalHapticFeedback"))
    }

    @Test
    fun restLastThreeSecondsUseTheWarnPulse() {
        val rest = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(rest.contains("RestTick.isWarn(safeRemaining)"))
        assertTrue(rest.contains("Haptics.warn(view)"))
        assertTrue(rest.contains("Haptics.tick(view)"))

        val alerts = readOwned("timer/RestTimerAlerts.kt")
        assertTrue(alerts.contains("RestTick.pulseMs(second)"))
        assertTrue(alerts.contains("COMPLETE_PATTERN"))
        assertTrue(alerts.contains("createWaveform(COMPLETE_PATTERN"))

        val service = readOwned("timer/RestTimerService.kt")
        assertTrue(service.contains("second = second"))
        assertTrue(service.contains("RestTimerAlerts.tick("))
    }

    @Test
    fun aRefusedEntryIsTheDoubleBeat() {
        val composer = readOwned("ui/activity/ActivityComposerScreen.kt")
        assertTrue(composer.contains("Haptics.reject(view)"))
        val restUi = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(restUi.contains("Haptics.reject(view)"))
        val workout = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(workout.contains("Haptics.commit(view)"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
