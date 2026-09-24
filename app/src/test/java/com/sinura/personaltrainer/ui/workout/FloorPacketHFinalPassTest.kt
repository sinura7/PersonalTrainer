package com.sinura.personaltrainer.ui.workout

import androidx.compose.ui.graphics.Color
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
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.PrGold
import com.sinura.personaltrainer.ui.theme.Warn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet H (WE-H): goldens / accessibility final pass on the redesigned floor. Evidence, not a
 * rewrite.
 *
 * Each case pins a Packet H ticket against the floor as it now stands: H2 TalkBack channels,
 * H3 widths / fonts / reduced motion, H4 colour-independent words. What a TalkBack user hears
 * and what the screen draws are rendered now: the numerals' actions, the plates' words, the
 * identity's one sentence and the chips' spoken states in WeightRepsEditorRenderTest,
 * ExerciseHeaderRenderTest and SetHistoryStripRenderTest; the commit's spoken payload and its
 * disabled reason in DockCommitRenderTest; and, since audit T1c-2, the ⋮ "Workout options" in
 * WorkoutHeaderRowRenderTest, the switcher's silent stills in LiftSwitcherSheetRenderTest, the
 * plan bar that says nothing in WorkoutHeaderRowRenderTest, and reduced motion snapping the
 * instrument specs and the dock card's ring in ReducedMotionRenderTest. What stays is copy,
 * tokens and the bans.
 *
 * The rest ring's pulse cannot be rendered: the Compose test clock stops every infinite
 * animation (the test rule adds its own infinite-animation policy after anything a test passes
 * in), so no render sees it pulse, reduced motion or not. It is held as a ban instead: no
 * infinite transition in RestTimerUi.kt without `!reduceMotion` ahead of it, and that flag is
 * always the composition's own.
 */
class FloorPacketHFinalPassTest {
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
        // What each weight well says, and that a bodyweight lift has none, is rendered in
        // WeightRepsEditorRenderTest and WorkoutFloorComponentsTest.
    }

    @Test
    fun logNamesItsPayloadAndItsDisabledReason() {
        // The commit's words, their spoken form and its reason for waiting: DockCommitRenderTest.
        assertTrue(LogCommitCopy.LOGGING_WAIT.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = true, liftReady = true)!!.isNotBlank())
        assertTrue(LogCommitCopy.disabledReason(logging = false, liftReady = false)!!.isNotBlank())
    }

    @Test
    fun recommendedRpeRemainsUnselectedSupportingText() {
        assertEquals(
            "RPE 8, about two reps left",
            RpeCopy.spoken(value = 8, selected = false),
        )
        assertEquals(
            "RPE 8, about two reps left, recommended",
            RpeCopy.spoken(value = 8, selected = false, recommended = true),
        )
        // A chosen chip is not also "recommended"; its Selected state says it is chosen.
        assertEquals("RPE 10, max", RpeCopy.spoken(value = 10, selected = true, recommended = true))
        // What each chip says and whether it is selected, the recommended one included, and the
        // named ends under the track: RpeSelectorRenderTest. How a recommendation is drawn (a
        // corner dot, never the chosen chip's Volt edge): RpeChipDrawingRenderTest.
        assertFalse(
            "a recommendation outlines a chip; it never selects one",
            ownedSource("ui/workout/RpeSelector.kt").contains("selected = recommendedRpe"),
        )
        assertEquals("Easy", RpeCopy.EASY_END)
        assertEquals("Max effort", RpeCopy.MAX_END)
    }

    @Test
    fun runningClockNeverStreamsSecondsToTalkBack() {
        assertFalse(TalkBackPolicy.announceRestKicker(justFinished = false))
        assertTrue(TalkBackPolicy.announceRestKicker(justFinished = true))
        assertEquals("Back to the bar", TalkBackPolicy.restKicker(justFinished = true))
        // The dock's rest card is a live region only at the finished flash, never per tick:
        // RestTimerCardRenderTest finds none while it runs and exactly one at the flash.
        // One live region for the rest kicker, the card's: the old bar's copy is gone.
        assertFalse(ownedSource("ui/components/RestTimerUi.kt").contains("TalkBackPolicy.announceRestKicker"))
    }

    @Test
    fun liftPicturesStayDecorativeInsideTheNamedIdentity() {
        // On the floor the still is the Details button and is spoken only as that
        // (ExerciseHeaderRenderTest); in the switcher a row is spoken once and its still adds
        // nothing (LiftSwitcherSheetRenderTest); the session bar says nothing either, the line
        // above it says it in words (WorkoutHeaderRowRenderTest).
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
        // Each chip's spoken sentence and the menu it names: SetHistoryStripRenderTest. The ⋮
        // is spoken "Workout options": WorkoutHeaderRowRenderTest.
    }

    @Test
    fun floorPutsWeightAndRepsSideBySideUntilLargeText() {
        assertFalse(LogLoopScale.stackEntryWells(1f))
        assertTrue(LogLoopScale.stackEntryWells(LogLoopScale.STACK_WELLS_FROM))
        // Side by side until large text, and plates placed from a fixed sample so they never
        // move as digits come and go: both rendered in WeightRepsEditorRenderTest.
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
        // instrument specs, which snap under reduced motion (ReducedMotionRenderTest).
        val card = ownedSource("ui/workout/RestTimerCard.kt")
        assertFalse(card.contains("rememberInfiniteTransition"))
        assertFalse(card.lowercase().contains("pulse"))
        // The rest pages' ring pulses in the last ten seconds, never under reduced motion.
        val rest = ownedSource("ui/components/RestTimerUi.kt")
        val unguarded = Regex("rememberInfiniteTransition\\(").findAll(rest).filterNot { call ->
            rest.substring(0, call.range.first).lines().takeLast(GUARD_LINES).joinToString("\n").contains("!reduceMotion")
        }
        assertFalse("an infinite transition in RestTimerUi runs under reduced motion", unguarded.any())
        // Every reduceMotion the file declares, a val, a var or a parameter, is the composition's
        // own: a parameter defaulting to false would switch the guard off from outside.
        val declared = Regex("\\b(?:val|var)\\s+reduceMotion\\b|\\breduceMotion\\s*:").findAll(rest).count()
        val fromComposition = Regex("\\bval reduceMotion = LocalReducedMotion\\.current\\b").findAll(rest).count()
        assertEquals("reduced motion is the composition's, never a value of the file's own", fromComposition, declared)
    }

    @Test
    fun everyFloorStateHasAWordNotJustAColour() {
        assertEquals("Current", CurrentLiftCopy.CURRENT)
        assertEquals("WU 2", SetOrdinalCopy.warmup(2))
        assertEquals("W", SetOrdinalCopy.WARMUP_MARK)
        assertEquals("Personal record", PersonalRecordCopy.BANNER)
        assertEquals("Back to the bar", TalkBackPolicy.REST_FINISHED_KICKER)
        // Every chip state is a word beside its Volt ring, and TalkBack hears it: the chips'
        // Current / Editing / Saved captions and spoken states are in SetHistoryStripRenderTest.
        // "Applied" on the coach card, and "Last ten seconds", "Rest complete" and "Back to the
        // bar" on the rest card: NextSetRecommendationRenderTest, RestTimerCardRenderTest.
        assertFalse(ownedSource("ui/workout/RestTimerCard.kt").contains("\"10 seconds\""))
        // The floor's rest words live on the card now; the bar file keeps none of its own.
        assertFalse(ownedSource("ui/components/RestTimerUi.kt").contains("\"10 seconds\""))
        // The two colours the floor's words stand beside: the gold of a record and a finished
        // rest, and the warning amber, which ADR-023 keeps from drifting toward orange.
        assertEquals("record gold", Color(0xFFFFC53D), PrGold)
        assertEquals("warning amber", Color(0xFFFFB020), Warn)
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
        assertTrue(AccessibilityMatrix.publicCandidateReady())
    }

    private companion object {
        /** A guard sits on the line that starts the transition or the one before it. */
        const val GUARD_LINES = 2
    }
}
