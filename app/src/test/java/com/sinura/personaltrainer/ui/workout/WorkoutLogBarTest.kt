package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.WeightUnit
import java.io.File
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
 * The set-type toggle, the entry's order and the dock's "Add another set" are tapped in
 * ExerciseHeaderRenderTest, FloorScreenWiringRenderTest and WorkoutDockRenderTest, where
 * W1a's one "Add set" will change them on purpose. The no-auto-advance bans stay here and
 * are also held in behaviour by those tests.
 */
class WorkoutLogBarTest {
    @Test
    fun logBarCommitCopyFollowsTheWarmupSwitch() {
        val action = readOwned("ui/workout/WorkoutPrimaryAction.kt")
        assertTrue(action.contains("WorkoutPrimaryKind.LOG_WARMUP -> \"Log warm-up\""))
        assertTrue(action.contains("WorkoutPrimaryKind.LOG_SET -> \"Log set\""))
        assertTrue(
            action.contains(
                "state.draft.isWarmup -> if (isHold && !holdArmed) WorkoutPrimaryKind.START_HOLD else WorkoutPrimaryKind.LOG_WARMUP",
            ),
        )
        assertTrue(action.contains("identity.draft.rpe.takeUnless { identity.draft.isWarmup }"))
        assertFalse(action.contains("\"Log \$draftLabel\""))
        assertFalse(action.contains("\"Save \$draftLabel\""))

        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("text = state.verb"))
        assertTrue(dock.contains("supporting = state.payload"))
        assertTrue(dock.contains("contentDescription = spokenAction"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("WorkoutDock("))
        assertTrue(screen.contains("verb = primaryAction.verb(includeNextName = false)"))
        assertTrue(screen.contains("?: primaryAction.nextName.takeIf { !landscape && primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },"))
        assertTrue(screen.contains("spokenPayload = primaryAction.nextName.takeIf { primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE },"))
        assertTrue(screen.contains("payload = primaryAction.payload(unit = unit, loadClass = loadClass)"))

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
        val selector = readOwned("ui/workout/RpeSelector.kt")
        assertFalse(
            "Warm-up must not share a scrolling row with RPE",
            selector.contains("LazyRow("),
        )
        assertTrue("reflow, never scroll", selector.contains("FlowRow("))
        assertTrue("persistent help", selector.contains("RPE help"))
        assertTrue("RPE label", selector.contains("Kicker(\"RPE\")"))
        assertTrue("RPE track tag", selector.contains("WorkoutTestTags.RPE_TRACK"))
        assertTrue("RPE values", selector.contains("RpeCopy.VALUES"))
        assertTrue("compact chips", selector.contains("compact = true"))
        assertTrue("equal weight chips", selector.contains(".weight(1f)"))
        assertTrue("radio semantics", selector.contains("role = Role.RadioButton"))
        assertTrue("TalkBack meaning", selector.contains("RpeCopy.spoken("))
        assertTrue("named ends", selector.contains("RpeCopy.EASY_END") && selector.contains("RpeCopy.MAX_END"))
        assertTrue("warmup reason", selector.contains("Warm-ups leave RPE blank"))
        assertFalse("Warm-up chip lives in the header toggle", selector.contains("label = \"Warm-up\""))
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.warmupOutsideRpeTrack())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.rpeTrackFitsWithoutScroll())
        // Warm-up is the header's two-way radio, under the identity and above the numerals,
        // and it drives the draft, the commit's verb and the ramp: ExerciseHeaderRenderTest
        // and FloorScreenWiringRenderTest.
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.setTypeToggleUsesCompactChips())
    }

    @Test
    fun advanceIsAStandingDockChoiceNotADwellAutoMove() {
        val dock = readOwned("ui/workout/WorkoutDock.kt")
        assertTrue(dock.contains("val nextAct = action.kind == WorkoutPrimaryKind.NEXT_EXERCISE && !state.editing"))
        assertTrue(dock.contains("val finishAct = action.kind == WorkoutPrimaryKind.FINISH && !state.editing"))
        // "Add another set" standing beside Next / Finish, and time never pressing Next, are
        // held in WorkoutDockRenderTest and FloorScreenWiringRenderTest.
        assertTrue(dock.contains("nextAct -> WorkoutTestTags.NEXT"))
        assertTrue(dock.contains("finishAct -> WorkoutTestTags.DOCK_FINISH"))
        assertTrue(dock.contains("key(action.identity)"))
        assertFalse("the dock never advances on its own", dock.contains("advanceNow"))

        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("primaryAction.kind == WorkoutPrimaryKind.NEXT_EXERCISE"))
        assertTrue(screen.contains("primaryAction.kind == WorkoutPrimaryKind.FINISH"))
        assertTrue(screen.contains("val accepted = viewModel.performPrimary(action)"))
        assertTrue(screen.contains("if (accepted && action.kind == WorkoutPrimaryKind.FINISH) confirmEnd = true"))
        assertTrue(screen.contains("onFinish = { confirmEnd = true }"))
        assertTrue(screen.contains("onDismissed = viewModel::onUndoOfferExpired"))
        assertFalse(
            "dwell must not call advanceNow",
            screen.contains("onDismissed = { viewModel.advanceNow() }"),
        )
        assertFalse("advance is a tap on the Volt, never a timer", screen.contains("advanceNow"))
        assertFalse(screen.contains("Stay here"))
        assertFalse(screen.contains("onStartNextLift"))
        assertTrue(screen.contains("Haptics.recordAccent(view)"))
        assertFalse(screen.contains("Haptics.celebrate"))
        val viewModel = readOwned("ui/workout/ActiveWorkoutViewModel.kt")
        assertTrue(
            viewModel.contains("WorkoutPrimaryKind.NEXT_EXERCISE -> action.identity.nextExerciseId?.let(::selectExercise)"),
        )

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

        assertTrue(dock.contains("canLog"))
        assertTrue(dock.contains("LogCommitCopy.disabledReason"))
        assertTrue(dock.contains("else -> WorkoutTestTags.LOG_SET"))
        assertTrue(dock.contains("hapticFeedback = false"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
