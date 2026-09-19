package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.AccessibilityMatrix
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SessionTelemetryCopy
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.TalkBackPolicy
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Motion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet H (WE-H): goldens / accessibility final pass on the redesigned floor. Evidence,
 * not a rewrite.
 *
 * Every assertion here pins a Packet H ticket against the floor as it now stands: H2
 * TalkBack channels (hero numeral actions, the round plates, the named commit, the rest
 * card's one announcement), H3 widths / fonts / reduced motion (side-by-side numerals
 * that stack at large text, a rest card without a pulse), H4 colour-independent words
 * (Saved / Editing / Current on the chips, Applied on the card, Last ten seconds, the
 * recommended RPE chip's spoken word).
 */
class FloorPacketHFinalPassTest {
    @Test
    fun heroNumeralsExposeDecreaseIncreaseAndTypeActions() {
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("CustomAccessibilityAction(decrementSpoken)"))
        assertTrue(editor.contains("CustomAccessibilityAction(incrementSpoken)"))
        assertTrue(editor.contains("CustomAccessibilityAction(typeLabel)"))
        assertTrue(editor.contains("decrementSpoken = \"Decrease \$weightField by \$stepShown \${unit.suffix}\""))
        assertTrue(editor.contains("incrementSpoken = \"Increase \$weightField by \$stepShown \${unit.suffix}\""))
        assertTrue(editor.contains("decrementSpoken = \"Decrease reps by 1\""))
        assertTrue(editor.contains("incrementSpoken = \"Increase reps by 1\""))
        assertTrue(editor.contains("typeLabel = \"Type a rep count\""))
        // The round plates say the same words, so a sighted tap and a TalkBack action agree.
        assertTrue(editor.contains("RoundPlate(label = \"−\", spoken = decrementSpoken"))
        assertTrue(editor.contains("RoundPlate(label = \"+\", spoken = incrementSpoken"))
        assertTrue(editor.contains("modifier = Modifier.semantics { contentDescription = spoken }"))
        // The history edit sheet's panel keeps the same three actions.
        val panel = readOwned("ui/components/SetEntryPanel.kt")
        assertTrue(panel.contains("CustomAccessibilityAction(\"Decrease \$label\")"))
        assertTrue(panel.contains("CustomAccessibilityAction(\"Increase \$label\")"))
        assertTrue(panel.contains("CustomAccessibilityAction(typeLabel)"))
    }

    @Test
    fun weightWellIsNamedForWhatTheLiftMeasures() {
        assertEquals("Weight", WeightMeaning.LIFTED.fieldLabel)
        assertEquals("Added weight", WeightMeaning.ADDED.fieldLabel)
        assertEquals("Assistance", WeightMeaning.ASSISTANCE.fieldLabel)
        assertTrue(
            setOf(
                WeightMeaning.LIFTED.fieldLabel,
                WeightMeaning.ADDED.fieldLabel,
                WeightMeaning.ASSISTANCE.fieldLabel,
            ).size == 3,
        )
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("label = meaning.fieldLabel") && editor.contains("unitLabel = unit.suffix"))
        assertTrue(editor.contains("val showWeight = meaning != WeightMeaning.NONE"))
    }

    @Test
    fun logNamesItsPayloadAndItsDisabledReason() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("LogCommitCopy.disabledReason("))
        assertTrue(dock.contains("supporting = state.payload"))
        assertTrue(dock.contains("val spokenAction = listOfNotNull(state.verb, state.spokenPayload ?: state.payload).joinToString(\" · \")"))
        assertTrue(dock.contains(".semantics { contentDescription = spokenAction }"))
        assertTrue(LogCommitCopy.LOGGING_WAIT.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = true, liftReady = true)!!.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = false, liftReady = false)!!.isNotBlank())
    }

    @Test
    fun recommendedRpeRemainsUnselectedSupportingText() {
        assertEquals(
            "RPE 8, about two reps left, not selected",
            RpeCopy.spoken(value = 8, selected = false),
        )
        assertEquals(
            "RPE 8, about two reps left, not selected, recommended",
            RpeCopy.spoken(value = 8, selected = false, recommended = true),
        )
        assertEquals("RPE 10, max, selected", RpeCopy.spoken(value = 10, selected = true, recommended = true))
        val selector = readOwned("ui/workout/RpeSelector.kt")
        assertTrue(selector.contains("val selected = rpe == value"))
        assertTrue(selector.contains("val recommended = recommendedRpe == value && !selected"))
        assertTrue(selector.contains("recommended = recommended,"))
        assertTrue(selector.contains("spoken = RpeCopy.spoken(value = value, selected = selected, recommended = recommended)"))
        assertFalse("a recommendation outlines a chip; it never selects one", selector.contains("selected = recommendedRpe"))
        assertTrue(selector.contains("RpeCopy.EASY_END"))
        assertTrue(selector.contains("RpeCopy.MAX_END"))
        assertEquals("Easy", RpeCopy.EASY_END)
        assertEquals("Max effort", RpeCopy.MAX_END)
    }

    @Test
    fun runningClockNeverStreamsSecondsToTalkBack() {
        assertFalse(TalkBackPolicy.announceRestKicker(justFinished = false))
        assertTrue(TalkBackPolicy.announceRestKicker(justFinished = true))
        assertEquals("Back to the bar", TalkBackPolicy.restKicker(justFinished = true))
        // The dock's rest card is a live region only at the finished flash, never per tick.
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(card.contains("if (TalkBackPolicy.announceRestKicker(justFinished)) {"))
        assertTrue(card.contains("liveRegion = LiveRegionMode.Polite"))
        assertEquals(1, card.split("liveRegion = LiveRegionMode.Polite").size - 1)
        // One live region for the rest kicker, the card's: the old bar's copy is gone.
        val rest = readOwned("ui/components/RestTimerUi.kt")
        assertFalse(rest.contains("TalkBackPolicy.announceRestKicker"))
    }

    @Test
    fun liftPicturesStayDecorativeInsideTheNamedIdentity() {
        val thumb = readOwned("ui/components/ExerciseThumb.kt")
        assertTrue(thumb.contains("clearAndSetSemantics { }"))
        val header = readOwned("ui/workout/ExerciseHeader.kt")
        assertTrue(header.contains(".semantics(mergeDescendants = true) {"))
        assertTrue(header.contains("contentDescription = \"\$spoken. \$setContext. \${CurrentLiftCopy.SWITCH}\""))
        assertTrue(header.contains("selected = true"))
        assertTrue(header.contains("showBadge = false"))
        // The session progress bar is decorative too: the progress line says it in words.
        val chrome = readOwned("ui/workout/WorkoutHeader.kt")
        assertTrue(chrome.contains(".testTag(WorkoutTestTags.PROGRESS_BAR)"))
        assertTrue(chrome.contains(".clearAndSetSemantics { }"))
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
    fun setChipsAndLiftOverflowSpeakWordsNeverGlyphsAlone() {
        assertEquals("Actions for set 2", SetRowCopy.actionsForSet(2))
        assertEquals("Revise set 2", SetRowCopy.reviseSet(2))
        assertEquals("Delete set 2", SetRowCopy.deleteSet(2))
        assertEquals("Remove lift", CurrentLiftCopy.REMOVE)
        assertEquals("Delete its sets first", CurrentLiftCopy.EDIT_BLOCKED_REASON)
        assertEquals("Switch exercise", CurrentLiftCopy.SWITCH)
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue("the chip menu names the chip's own ordinal", strip.contains("SetRowCopy.actionsFor(ordinal)"))
        assertTrue(strip.contains("contentDescription = \"\$ordinal, \$spokenSet, \$state\""))
        val menu = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(menu.contains("contentDescription = \"Workout options\""))
    }

    @Test
    fun floorPutsWeightAndRepsSideBySideUntilLargeText() {
        assertFalse(FloorCompactChrome.stackWeightAboveReps())
        assertTrue(FloorCompactChrome.heroNumeralsSideBySide())
        assertFalse(LogLoopScale.stackEntryWells(1f))
        assertTrue(LogLoopScale.stackEntryWells(LogLoopScale.STACK_WELLS_FROM))
        val editor = readOwned("ui/workout/WeightRepsEditor.kt")
        assertTrue(editor.contains("val stack = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)"))
        assertTrue(editor.contains("if (showWeight && !stack) {"))
        val layout = editor.substring(editor.indexOf("if (showWeight && !stack) {"))
        assertTrue(layout.substring(0, layout.indexOf("} else {")).contains("Row("))
        assertTrue(layout.substring(layout.indexOf("} else {")).contains("Column("))
        // Weight, reps and hold time are the same hero numeral, sized from a fixed sample so
        // the plates never move as digits come and go.
        assertEquals(3, editor.replace("private fun HeroNumeral(", "").split("HeroNumeral(").size - 1)
        assertTrue(editor.contains("sample = WEIGHT_SAMPLE"))
        assertTrue(editor.contains("sample = REPS_SAMPLE"))
        assertTrue(editor.contains("sample = TIME_SAMPLE"))
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
        // The dock's rest card has no pulse at all; its ring and accent go through the
        // instrument specs, which snap under reduced motion.
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("rememberInfiniteTransition"))
        assertFalse(card.lowercase().contains("pulse"))
        assertTrue(card.contains("animationSpec = instrumentLinear(Motion.TICK_MS)"))
        assertTrue(card.contains("animationSpec = instrumentTween(Motion.BASE)"))
        val motion = readOwned("ui/theme/Motion.kt")
        assertTrue(motion.contains("if (LocalReducedMotion.current) snap() else tween(durationMs)"))
        val linear = motion.substring(motion.indexOf("fun <T> instrumentLinear"))
        assertTrue(linear.contains("LocalReducedMotion.current"))
        assertTrue(linear.contains("snap()"))
        // The rest page's own bar and ring still stop their pulse under reduced motion.
        val rest = readOwned("ui/components/RestTimerUi.kt")
        assertTrue(rest.contains("!reduceMotion"))
        assertTrue(rest.contains("LocalReducedMotion.current"))
        assertTrue(rest.substring(rest.indexOf("fun RestDurationSheet")).contains("Motion.durationMs"))
    }

    @Test
    fun everyFloorStateHasAWordNotJustAColour() {
        assertEquals("Current", CurrentLiftCopy.CURRENT)
        assertEquals("WU 2", SetOrdinalCopy.warmup(2))
        assertEquals("W", SetOrdinalCopy.WARMUP_MARK)
        assertEquals("Personal record", PersonalRecordCopy.BANNER)
        assertEquals("Back to the bar", TalkBackPolicy.REST_FINISHED_KICKER)
        // Every chip state is a word beside its Volt ring, and TalkBack hears it.
        val strip = readOwned("ui/workout/SetHistoryStrip.kt")
        assertTrue(strip.contains("private const val CURRENT = \"Current\""))
        assertTrue(strip.contains("private const val EDITING = \"Editing\""))
        assertTrue(strip.contains("private const val SAVED = \"Saved\""))
        assertTrue(strip.contains("editing -> \"editing\""))
        assertTrue(strip.contains("saved -> \"saved\""))
        assertTrue(strip.contains("else -> \"logged\""))
        assertTrue(readOwned("ui/workout/NextSetRecommendation.kt").contains("\"Applied\""))
        val table = readOwned("ui/components/SetTable.kt")
        assertTrue(table.contains("Latest"))
        val card = readOwned("ui/workout/RestTimerCard.kt")
        assertTrue(card.contains("Last ten seconds"))
        assertFalse(card.contains("\"10 seconds\""))
        assertTrue(card.contains("private const val REST_COMPLETE = \"Rest complete\""))
        assertTrue(card.contains("justFinished -> TalkBackPolicy.REST_FINISHED_KICKER"))
        val rest = readOwned("ui/components/RestTimerUi.kt")
        // The floor's rest words live on the card now; the bar file keeps none of its own.
        assertTrue(card.contains("Last ten seconds"))
        assertFalse(card.contains("\"10 seconds\""))
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
        assertTrue(notes.contains("edit/delete menus"))
        assertTrue(notes.contains("round plates say the step and unit"))
        assertTrue(notes.contains("Apply never saves"))
        assertTrue(notes.contains("Easy and Max effort"))
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
