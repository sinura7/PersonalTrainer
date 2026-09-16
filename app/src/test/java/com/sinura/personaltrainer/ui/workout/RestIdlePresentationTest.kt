package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.RestIdleCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestIdlePresentationTest {
    @Test
    fun idleDockWearsADimInstrumentFaceNotABrightClock() {
        val src = readOwned("ui/components/RestTimerUi.kt")
        val idleStart = src.indexOf("fun RestIdleRow")
        val idleEnd = src.indexOf("fun RestDurationSheet")
        assertTrue("RestIdleRow missing", idleStart >= 0)
        assertTrue("RestDurationSheet missing", idleEnd > idleStart)
        val idle = src.substring(idleStart, idleEnd)
        assertTrue(idle.contains("FloorInstrumentBar("))
        assertTrue("idle planned clock is numeralMd, dimmed", idle.contains("clockColor = TextSecondary"))
        assertFalse("idle must not pass a bright numeral", idle.contains("clockColor = TextPrimary"))
        assertTrue(idle.contains("progress = 0f"))
        assertTrue(idle.contains("showChevron = true"))
        assertTrue(idle.contains("TemperIcons.FloorRest"))
        assertTrue(idle.contains("RestIdleCopy"))
        assertTrue(idle.contains("RestIdleCopy.START") || idle.contains("startSpoken = RestIdleCopy.startSpoken"))
        assertFalse(
            "idle Start next must not be composed",
            idle.contains("RestIdleCopy.START_NEXT"),
        )
        assertFalse("idle must not expand presets inline", idle.contains("picking"))
        assertFalse("idle must not grow a Time set row", idle.contains("TextButton("))
        assertFalse("idle must not host preset chips", idle.contains("RestPresetChips("))
        assertFalse("idle rest must not use a filled Volt", idle.contains("PrimaryGymButton"))
        assertFalse(idle.contains("SnapValueWheel("))
        assertTrue(FloorCompactChrome.idleRestIsInstrumentBar())
        assertEquals(56, Metrics.logTimerRow.value.toInt())
    }

    @Test
    fun durationSheetHoldsPresetsCustomNudgeAndTimeSet() {
        val src = readOwned("ui/components/RestTimerUi.kt")
        val sheetStart = src.indexOf("fun RestDurationSheet")
        val sheetEnd = src.indexOf("fun RestIconControl")
        assertTrue(sheetStart >= 0 && sheetEnd > sheetStart)
        val sheet = src.substring(sheetStart, sheetEnd)
        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("RestPresetChips("))
        assertTrue(sheet.contains("CustomRestDialog("))
        assertTrue(sheet.contains("workout-rest-sheet-minus"))
        assertTrue(sheet.contains("workout-rest-sheet-plus"))
        assertTrue(sheet.contains("SetStopwatchCopy.START"))
        assertTrue(sheet.contains("workout-sheet-start-set-clock"))
        assertTrue(sheet.contains("LocalReducedMotion.current"))
        assertTrue(sheet.contains("sheetState.snapTo"))
        assertTrue(sheet.contains("verticalScroll"))
        assertTrue(sheet.contains("workout-rest-duration-sheet"))
        assertFalse(sheet.contains("PrimaryGymButton"))

        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("RestDurationSheet("))
        assertTrue(bar.contains("onSelectRestDuration"))
        assertTrue(bar.contains("durationSheet"))
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("WorkoutTestTags.REST_DURATION_SHEET"))
        assertTrue(screen.contains("onSelectRestDuration = viewModel::selectRestDuration"))
    }

    @Test
    fun firstRestMentionsUnrestrictedBattery() {
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("RestBatteryCopy.SENTENCE"))
        assertTrue(dock.contains("RestBatteryHintRow"))
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestBatteryHintRow"))
        assertTrue(floor.contains("RestFloorTags.BATTERY"))
        assertTrue(floor.contains("RestHonestyCopy.EXACT_DENIED"))
        assertTrue(floor.contains("RestFloorTags.EXACT"))
        assertFalse(floor.contains("precise", ignoreCase = true))
    }

    @Test
    fun idleFloorUsesEmptyRingAndNotRunningKicker() {
        val floor = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(floor.contains("RestIdleCopy.KICKER"))
        assertTrue(floor.contains("idleRingSeconds"))
        assertTrue(floor.contains("afterWarmupHint()"))
        assertFalse(floor.contains("\"Next rest\""))
    }

    @Test
    fun talkBackIdleClockAndStartMatchTheInstrument() {
        assertEquals(
            "Rest is not running. Rest 2:30. Tap to change duration.",
            RestIdleCopy.dockSpoken("2:30", afterWarmup = false),
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
