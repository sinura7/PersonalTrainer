package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Idle rest on the redesigned dock is the same [RestTimerCard] at rest: dim
 * kicker, dim planned clock, an empty ring, a chevron into the duration
 * sheet, and quiet Start rest / Time set controls. Never a bright countdown,
 * never a filled Volt, never inline presets.
 *
 * What the idle card, its length sheet and the dock's battery sentence show, say and do is
 * rendered in RestTimerCardRenderTest, RestDurationSheetRenderTest and
 * WorkoutDockTimerRenderTest, and tapped through the screen in
 * FloorRestAndCoachWiringRenderTest. The bans stay here, with the two drawing facts no
 * semantics can see (the empty ring, the chevron) and the rest page's own lines.
 */
class RestIdlePresentationTest {
    @Test
    fun idleDockWearsADimInstrumentFaceNotABrightClock() {
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue("idle ring is empty", card.contains("remainingSeconds = if (running) safeRemaining else 0"))
        assertTrue("idle chevron into the duration sheet", card.contains("if (idle) {") && card.contains("TemperIcons.Chevron"))
        assertFalse(
            "idle Start next must not be composed",
            card.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse("idle must not expand presets inline", card.contains("picking"))
        assertFalse("idle must not grow a Time set row", card.contains("TextButton("))
        assertFalse("idle must not host preset chips", card.contains("RestPresetChips("))
        assertFalse("idle rest must not use a filled Volt", card.contains("PrimaryGymButton"))
        assertFalse(card.contains("SnapValueWheel("))
        assertFalse("the rest card never pulses", card.contains("rememberInfiniteTransition"))
        assertTrue(FloorCompactChrome.restIsDockCard())
        assertFalse(FloorCompactChrome.idleRestIsInstrumentBar())
        assertEquals(72, Metrics.commit.value.toInt())
        assertEquals(48, Metrics.restRingSmall.value.toInt())
    }

    @Test
    fun durationSheetHoldsPresetsCustomNudgeAndTimeSet() {
        val src = readOwned("ui/components/RestTimerUi.kt")
        val sheetStart = src.indexOf("fun RestDurationSheet")
        val sheetEnd = src.indexOf("fun RestSweepRing")
        assertTrue(sheetStart >= 0 && sheetEnd > sheetStart)
        val sheet = src.substring(sheetStart, sheetEnd)
        assertTrue(sheet.contains("LocalReducedMotion.current"))
        assertTrue(sheet.contains("Motion.durationMs"))
        assertFalse(sheet.contains("PrimaryGymButton"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("const val REST_DURATION_SHEET"))
    }

    @Test
    fun firstRestMentionsUnrestrictedBattery() {
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestHonestyCopy.pick("))
        assertTrue(floor.contains("RestHonestyRow("))
        assertTrue(floor.contains("notificationsEnabled = notificationsEnabled"))
        assertTrue(floor.contains("exactBestEffort = rest.exactAlarmBestEffort"))
        assertFalse(floor.contains("precise", ignoreCase = true))
    }

    @Test
    fun idleFloorNamesPlannedRestAndRingYieldsToLargeText() {
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("else -> \"Planned rest\""))
        assertTrue(floor.contains("remainingSeconds = if (rest.running) safeRemaining else 0"))
        assertTrue(floor.contains("if (showRing)"))
        assertFalse(floor.contains("\"Next rest\""))
    }

    @Test
    fun talkBackIdleClockAndStartMatchTheInstrument() {
        assertEquals(
            "Rest is not running. Rest 2:30. Tap to change duration.",
            RestIdleCopy.dockSpoken(clock = "2:30", afterWarmup = false),
        )
        assertEquals(
            "Start rest, 2 minutes 30 seconds",
            RestIdleCopy.startSpoken(150),
        )
        assertEquals("Time this set", com.sinura.personaltrainer.domain.SetStopwatchCopy.START_SPOKEN)
        val notes = com.sinura.personaltrainer.domain.AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("Time this set"))
        assertTrue(notes.contains("Start rest"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
