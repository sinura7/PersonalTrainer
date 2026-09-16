package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.AccessibilityMatrix
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SessionTelemetryCopy
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.TalkBackPolicy
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.ui.theme.Motion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet H (WE-H): goldens / accessibility final pass. Evidence, not a rewrite.
 *
 * Every assertion here pins a Packet H ticket against the floor Packets A–G
 * built: H2 TalkBack channels, H3 widths / fonts / reduced motion, H4
 * colour-independent words. Behaviour changes stay out; the one exception is
 * the recommended RPE chip gaining its spoken word (H4), which this file
 * proves.
 */
class FloorPacketHFinalPassTest {
    @Test
    fun weightWellsExposeDecreaseIncreaseAndTypeActions() {
        val panel = readOwned("ui/components/SetEntryPanel.kt")
        assertTrue(panel.contains("CustomAccessibilityAction(\"Decrease \$label\")"))
        assertTrue(panel.contains("CustomAccessibilityAction(\"Increase \$label\")"))
        assertTrue(panel.contains("CustomAccessibilityAction(typeLabel)"))
    }

    @Test
    fun weightWellIsNamedForWhatTheLiftMeasures() {
        assertEquals("Weight", WeightMeaning.LIFTED.fieldLabel)
        assertEquals("Added", WeightMeaning.ADDED.fieldLabel)
        assertEquals("Assistance", WeightMeaning.ASSISTANCE.fieldLabel)
        assertTrue(
            setOf(
                WeightMeaning.LIFTED.fieldLabel,
                WeightMeaning.ADDED.fieldLabel,
                WeightMeaning.ASSISTANCE.fieldLabel,
            ).size == 3,
        )
    }

    @Test
    fun logNamesItsPayloadAndItsDisabledReason() {
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("LogCommitCopy.disabledReason("))
        assertTrue(LogCommitCopy.LOGGING_WAIT.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = true, liftReady = true)!!.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = false, liftReady = false)!!.isNotBlank())
    }

    @Test
    fun recommendedRpeChipSpeaksItsOutline() {
        assertEquals(
            "RPE 8, about two reps left, not selected",
            RpeCopy.spoken(8, selected = false),
        )
        assertEquals(
            "RPE 8, about two reps left, not selected, recommended",
            RpeCopy.spoken(8, selected = false, recommended = true),
        )
        assertEquals("RPE 10, max, selected", RpeCopy.spoken(10, selected = true, recommended = true))
        val bar = readOwned("ui/workout/WorkoutLogBar.kt")
        assertTrue(bar.contains("recommended = recommendedRpe == value"))
    }

    @Test
    fun runningClockNeverStreamsSecondsToTalkBack() {
        assertFalse(TalkBackPolicy.announceRestKicker(justFinished = false))
        assertTrue(TalkBackPolicy.announceRestKicker(justFinished = true))
        assertEquals("Back to the bar", TalkBackPolicy.restKicker(justFinished = true))
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("TalkBackPolicy.announceRestKicker"))
    }

    @Test
    fun liftPicturesStayDecorativeInsideTheNamedCard() {
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("clearAndSetSemantics { }"))
        val card = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(card.contains("mergeDescendants = true"))
        assertTrue(
            CurrentLiftCopy.cardSpoken(
                name = "Bench",
                number = 1,
                total = 6,
                workingLogged = 0,
                targetSets = 3,
                equipmentLabel = "Barbell",
                meaning = WeightMeaning.LIFTED,
            ).startsWith("Current. Bench"),
        )
    }

    @Test
    fun setRowsAndLiftOverflowSpeakWordsNeverGlyphsAlone() {
        assertEquals("Actions for set 2", SetRowCopy.actionsForSet(2))
        assertEquals("Revise set 2", SetRowCopy.reviseSet(2))
        assertEquals("Delete set 2", SetRowCopy.deleteSet(2))
        assertEquals("Remove lift", CurrentLiftCopy.REMOVE)
        assertEquals("Delete its sets first", CurrentLiftCopy.EDIT_BLOCKED_REASON)
        val panel = readOwned("ui/workout/LoggedSetsPanel.kt")
        assertTrue(panel.contains("SetRowCopy.actionsForSet("))
    }

    @Test
    fun floorStacksOneFieldPerRow() {
        val panel = readOwned("ui/components/SetEntryPanel.kt")
        val start = panel.indexOf("private fun CompactFloorEntry(")
        val end = panel.indexOf("private fun FloorNumeralRow(")
        assertTrue(start >= 0 && end > start)
        val body = panel.substring(start, end)
        assertTrue(body.contains("Column("))
        assertEquals(3, body.split("FloorNumeralRow(").size - 1)
    }

    @Test
    fun telemetryIsMinuteGrainAndDropsVolumeFirst() {
        assertEquals(1, SessionTelemetryCopy.elapsedMinutes(119))
        assertEquals(0, SessionTelemetryCopy.elapsedMinutes(59))
        assertFalse(SessionTelemetryCopy.includeVolume(fontScale = 2f, widthDp = 360))
        assertTrue(SessionTelemetryCopy.includeVolume(fontScale = 1f, widthDp = 360))
        assertTrue(SessionTelemetryCopy.includeVolume(fontScale = 2f, widthDp = 412))
    }

    @Test
    fun reducedMotionStopsTheFloorPulseAndSettles() {
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.REST_DONE_MS))
        assertEquals(240, Motion.REST_DONE_MS)
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("!reduceMotion"))
    }

    @Test
    fun everyFloorStateHasAWordNotJustAColour() {
        assertEquals("Current", CurrentLiftCopy.CURRENT)
        assertEquals("WU 2", SetOrdinalCopy.warmup(2))
        assertEquals("Personal record", PersonalRecordCopy.BANNER)
        assertEquals("Back to the bar", TalkBackPolicy.REST_FINISHED_KICKER)
        val table = readOwned("ui/components/SetTable.kt")
        assertTrue(table.contains("Latest"))
        val dock = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(dock.contains("Last ten seconds"))
        assertFalse(dock.contains("\"10 seconds\""))
        val colors = readMain("ui/theme/Color.kt")
        assertTrue(colors.contains("val PrGold = Color(0xFFFFC53D)"))
        assertTrue(colors.contains("val Warn = Color(0xFFFFB020)"))
    }

    @Test
    fun matrixNotesNameTheH2ChannelsAndWaitForThePhone() {
        val notes = AccessibilityMatrix.page("active-strength").talkBackNotes
        assertTrue(notes.contains("Decrease"))
        assertTrue(notes.contains("Assistance"))
        assertTrue(notes.contains("disabled reason"))
        assertTrue(notes.contains("once"))
        assertTrue(notes.contains("decorative"))
        assertTrue(notes.contains("Actions for set"))
        assertFalse(AccessibilityMatrix.page("active-strength").physicalTalkBack)
        assertFalse(AccessibilityMatrix.publicCandidateReady())
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }

    private fun readMain(relative: String): String = readOwned(relative)
}
