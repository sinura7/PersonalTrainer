package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 1 floor defects on the redesigned dock ([WorkoutDock]): the commit's
 * verb follows the Working / Warm-up toggle; Warm-up sits outside RPE 6–10;
 * Next exercise / Add another set stand in the dock until chosen (no dwell
 * auto-advance).
 *
 * The commit's words and what they say to TalkBack, its type, its height, its missing haptic,
 * its waiting reason and the press that never carries over to Next are rendered in
 * DockCommitRenderTest; the warm-up hold's Start hold in WorkoutPrimaryActionsTest; the
 * set-type toggle, the entry's order and "Add another set" in ExerciseHeaderRenderTest,
 * FloorScreenWiringRenderTest and WorkoutDockRenderTest; the effort track and its warm-up
 * reason in RpeSelectorRenderTest; Next on the ViewModel in FloorVmContractTest; a record's
 * accent in FloorFeedbackRenderTest (audit T1c-1). The action's own words are read here
 * directly. The no-auto-advance bans stay, and are also held in behaviour by those tests.
 */
class WorkoutLogBarTest {
    @Test
    fun logBarCommitCopyFollowsTheWarmupSwitch() {
        val action = ownedSource("ui/workout/WorkoutPrimaryAction.kt")
        assertFalse(action.contains("\"Log \$draftLabel\""))
        assertFalse(action.contains("\"Save \$draftLabel\""))

        val warmup = floorPrimaryAction(
            kind = WorkoutPrimaryKind.LOG_WARMUP,
            draft = ActiveExerciseDraft(weightKg = 60.0, reps = 8, rpe = 8, isWarmup = true),
        )
        assertEquals("Log warm-up", warmup.verb())
        val warmupPayload = requireNotNull(warmup.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED))
        assertTrue(warmupPayload.contains("× 8"))
        assertFalse("a warm-up never carries effort", warmupPayload.contains("RPE"))
        val working = floorPrimaryAction(
            kind = WorkoutPrimaryKind.LOG_SET,
            draft = ActiveExerciseDraft(weightKg = 60.0, reps = 8, rpe = 8),
        )
        assertEquals("Log set", working.verb())
        val workingPayload = requireNotNull(working.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED))
        assertTrue(workingPayload.contains("× 8"))
        assertTrue(workingPayload.endsWith(" · RPE 8"))
    }

    @Test
    fun warmupSitsOutsideTheEqualWeightRpeTrack() {
        val selector = ownedSource("ui/workout/RpeSelector.kt")
        assertFalse(
            "Warm-up must not share a scrolling row with RPE",
            selector.contains("LazyRow("),
        )
        assertFalse("Warm-up chip lives in the header toggle", selector.contains("label = \"Warm-up\""))
    }

    @Test
    fun advanceIsAStandingDockChoiceNotADwellAutoMove() {
        val dock = ownedSource("ui/workout/WorkoutDock.kt")
        assertFalse("the dock never advances on its own", dock.contains("advanceNow"))

        val screen = ownedSource("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(
            "dwell must not call advanceNow",
            screen.contains("onDismissed = { viewModel.advanceNow() }"),
        )
        assertFalse("advance is a tap on the Volt, never a timer", screen.contains("advanceNow"))
        assertFalse(screen.contains("Stay here"))
        assertFalse(screen.contains("onStartNextLift"))
        assertFalse(screen.contains("Haptics.celebrate"))

        val next = floorPrimaryAction(
            kind = WorkoutPrimaryKind.NEXT_EXERCISE,
            draft = ActiveExerciseDraft(),
            nextName = "Leg curl",
        )
        assertEquals("Next exercise · Leg curl", next.verb())
        assertEquals("Next exercise", next.verb(includeNextName = false))
        assertNull(next.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED))
        val finish = floorPrimaryAction(
            kind = WorkoutPrimaryKind.FINISH,
            draft = ActiveExerciseDraft(),
        )
        assertEquals("Finish workout", finish.verb())
        assertNull(finish.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED))
        val saving = floorPrimaryAction(
            kind = WorkoutPrimaryKind.SAVING,
            draft = ActiveExerciseDraft(),
        )
        assertEquals("Saving…", saving.verb())
    }
}
