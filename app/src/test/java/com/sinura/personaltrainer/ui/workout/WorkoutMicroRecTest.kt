package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The coach call the floor reads, from the session as it stands: `workoutMicroRec` and
 * `workoutCoachSuggestion` in SetMicroRecUi.kt.
 *
 * These were held as their source (`fun workoutMicroRec(`, `CoachEngine.suggest(`,
 * `historyWorking = historySets.map`, `toMicroRec()`). What the lifter depends on is that
 * the log's Next-set card and the rest page make the same call from the same inputs, that
 * the last session's effort seeds the first set, that a warm-up draft is never a preview,
 * and that the coach's goal changes the words it gives. Whether the workout hands a goal in
 * at all is W1b's to wire; these call the functions directly.
 */
class WorkoutMicroRecTest {
    private fun session(sets: List<Pair<Int, Int?>>, warmupFirst: Boolean = false): WorkoutSession {
        val working = sets.mapIndexed { index, (reps, rpe) -> floorSet(number = index + 2, weightKg = FLOOR_KG70, reps = reps, rpe = rpe) }
        val logged = if (warmupFirst) listOf(floorSet(number = 1, weightKg = FLOOR_KG70, reps = 8, warmup = true)) + working else working
        return floorSession(sets = logged, targetSets = 3)
    }

    private fun call(
        session: WorkoutSession?,
        draft: ActiveExerciseDraft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10),
        editingSetId: String? = null,
        history: List<ExerciseSetRecord> = emptyList(),
        prefs: CoachPreferences = CoachPreferences.DEFAULT,
    ): SetMicroRec? = workoutMicroRec(
        session = session,
        selectedExerciseId = "leg-ext",
        draft = draft,
        hint = null,
        editingSetId = editingSetId,
        lighterWeek = false,
        unit = FLOOR_UNIT,
        nowMs = 0L,
        todayEpochDay = 0L,
        historySets = history,
        coachPrefs = prefs,
    )

    @Test
    fun theLogAndTheRestPageMakeTheSameCall() {
        val current = session(listOf(9 to 8))
        val onTheLog = checkNotNull(call(current))
        val onTheRestPage = checkNotNull(
            workoutCoachSuggestion(
                session = current,
                selectedExerciseId = "leg-ext",
                draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10),
                hint = null,
                editingSetId = null,
                lighterWeek = false,
                unit = FLOOR_UNIT,
                nowMs = 0L,
                todayEpochDay = 0L,
            ),
        )
        assertEquals(onTheRestPage.toMicroRec(), onTheLog)
        // Nine of ten at RPE 8: one more rep at the same weight.
        assertEquals(SetMicroRecCalculator.CLIMB_REPS, onTheLog.reasonCode)
        assertEquals(FLOOR_KG70, onTheLog.nextWeightKg, 1e-9)
        assertEquals(10, onTheLog.nextReps)
    }

    @Test
    fun theLastSessionsEffortSeedsTheFirstSet() {
        val fresh = session(emptyList())
        assertNull("no history, no effort to suggest", checkNotNull(call(fresh)).nextRpe)
        val history = listOf(
            ExerciseSetRecord(setId = "old-1", sessionId = "old", weightKg = FLOOR_KG70, reps = 10, completedAt = 1L, rpe = 8),
            ExerciseSetRecord(setId = "old-2", sessionId = "old", weightKg = FLOOR_KG70, reps = 9, completedAt = 2L, rpe = 9),
        )
        val seeded = checkNotNull(call(fresh, history = history))
        assertEquals(SetMicroRecCalculator.FIRST_SET, seeded.reasonCode)
        assertEquals(9, seeded.nextRpe)
    }

    @Test
    fun aWarmupDraftIsNeverAPreviewOfTheNextWorkingSet() {
        val current = session(listOf(10 to 8))
        val working = checkNotNull(call(current))
        val warmupDraft = checkNotNull(call(current, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10, rpe = 10, isWarmup = true)))
        assertEquals(working, warmupDraft)
    }

    @Test
    fun anEditOrAMissingSessionHasNoCall() {
        assertNull(call(session(listOf(10 to 8)), editingSetId = "set-2"))
        assertNull(call(null))
    }

    @Test
    fun theCoachsGoalChangesTheWordsNotTheNumbers() {
        // Reps in the tank: add weight. A Strength goal says why it keeps reps first.
        val current = session(listOf(10 to 7))
        val general = checkNotNull(
            workoutCoachSuggestion(
                session = current,
                selectedExerciseId = "leg-ext",
                draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10),
                hint = null,
                editingSetId = null,
                lighterWeek = false,
                unit = FLOOR_UNIT,
                nowMs = 0L,
                todayEpochDay = 0L,
            ),
        )
        val strength = checkNotNull(
            workoutCoachSuggestion(
                session = current,
                selectedExerciseId = "leg-ext",
                draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10),
                hint = null,
                editingSetId = null,
                lighterWeek = false,
                unit = FLOOR_UNIT,
                nowMs = 0L,
                todayEpochDay = 0L,
                coachPrefs = CoachPreferences(goal = TrainingGoal.STRENGTH),
            ),
        )
        assertEquals("Had more in you — add weight", general.explanationShort)
        assertEquals("Had more in you — add weight · strength bias keeps reps before big jumps", strength.explanationShort)
        assertNotEquals(general.explanationShort, strength.explanationShort)
        assertEquals(general.toMicroRec(), strength.toMicroRec())
    }
}
