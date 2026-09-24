package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPrimaryActionsTest {
    @Test
    fun completedSelectionAdvancesOnlyToUnfinishedExercisesIncludingEarlierSkippedOnes() {
        val state = state(selected = "row", sets = listOf(set("row")))
        val next = derive(state)
        assertEquals(WorkoutPrimaryKind.NEXT_EXERCISE, next.kind)
        assertEquals("squat", next.identity.nextExerciseId)
        assertEquals("squat", next.nextName)
        assertEquals(WorkoutPrimaryKind.FINISH, derive(state.copy(
            session = state.session!!.copy(sets = listOf(set("row"), set("squat"))),
        )).kind)
    }

    @Test
    fun warmupExtraAndEditingOverrideCompletionWhileFreeExercisesKeepLogging() {
        val complete = state(sets = listOf(set("squat")))
        assertEquals(WorkoutPrimaryKind.NEXT_EXERCISE, derive(complete).kind)
        assertEquals(WorkoutPrimaryKind.LOG_SET, derive(complete, extra = true).kind)
        assertEquals(WorkoutPrimaryKind.LOG_WARMUP, derive(complete.copy(
            draft = complete.draft.copy(isWarmup = true),
        )).kind)
        assertEquals(WorkoutPrimaryKind.SAVE_CHANGES, derive(complete.copy(editingSetId = "set-squat")).kind)
        val free = complete.copy(session = complete.session!!.copy(
            exercises = complete.session.exercises.map { it.copy(targetSets = 0) },
        ))
        assertEquals(WorkoutPrimaryKind.LOG_SET, derive(free).kind)
    }

    @Test
    fun undoAndWarmupEditsRecomputeCompletionFromDurableRows() {
        val complete = state(sets = listOf(set("squat")))
        assertEquals(WorkoutPrimaryKind.NEXT_EXERCISE, derive(complete).kind)
        assertEquals(WorkoutPrimaryKind.LOG_SET, derive(complete.copy(
            session = complete.session!!.copy(sets = emptyList()),
        )).kind)
        assertEquals(WorkoutPrimaryKind.LOG_SET, derive(complete.copy(
            session = complete.session.copy(sets = listOf(set("squat").copy(isWarmup = true))),
        )).kind)
    }

    @Test
    fun failedReadNeverLooksLikeAnEmptyWorkoutAndPendingSaveOwnsTheAction() {
        val empty = state().copy(session = state().session!!.copy(exercises = emptyList()), selectedExerciseId = null)
        assertEquals(WorkoutPrimaryKind.ADD_EXERCISE, derive(empty).kind)
        assertEquals(WorkoutPrimaryKind.UNAVAILABLE, derive(empty.copy(loadState = SessionLoadState.FAILED)).kind)
        val command = WorkoutSetSave("session", "squat", "new-set", 1, WorkoutSetValues(60.0, 5, null, false, null))
        val complete = state(sets = listOf(set("squat")))
        val expected = mapOf(
            WorkoutSavePhase.CHECKING to WorkoutPrimaryKind.CHECKING,
            WorkoutSavePhase.SAVING to WorkoutPrimaryKind.SAVING,
            WorkoutSavePhase.FAILED to WorkoutPrimaryKind.RETRY_SAVE,
            WorkoutSavePhase.CONFLICT to WorkoutPrimaryKind.REVIEW_SAVE,
        )
        expected.forEach { (phase, kind) ->
            val action = derive(complete.copy(save = WorkoutSaveState(phase, command)))
            assertEquals(kind, action.kind)
            assertEquals(phase == WorkoutSavePhase.FAILED || phase == WorkoutSavePhase.CONFLICT, action.enabled)
        }
    }

    @Test
    fun holdReadyRunningAndTargetReachedRequireAnExplicitAction() {
        val normal = state()
        val hold = normal.copy(session = normal.session!!.copy(exercises = listOf(lift("wall_sit"))), selectedExerciseId = "wall_sit")
        assertEquals(WorkoutPrimaryKind.START_HOLD, derive(hold).kind)
        val running = derive(hold, hold = HoldTimerUiState(running = true, totalSeconds = 30, remainingSeconds = 18, elapsedSeconds = 12))
        assertEquals(WorkoutPrimaryKind.LOG_HOLD, running.kind)
        assertEquals(12, running.durationSeconds)
        val reached = derive(hold, hold = HoldTimerUiState(running = false, totalSeconds = 30, remainingSeconds = 0, elapsedSeconds = 30))
        assertEquals(WorkoutPrimaryKind.LOG_HOLD, reached.kind)
        assertTrue(reached.enabled)
    }

    /**
     * A warm-up on a hold still has to be held: the commit starts its clock first, and only
     * then logs it as a warm-up. A strength warm-up has no clock and logs at once. This was
     * `state.draft.isWarmup -> if (isHold && !holdArmed) … START_HOLD else … LOG_WARMUP`, read
     * as text in WorkoutLogBarTest (audit T1c-1).
     */
    @Test
    fun aWarmupHoldStartsItsClockBeforeItLogsAsAWarmup() {
        val normal = state()
        val hold = normal.copy(session = normal.session!!.copy(exercises = listOf(lift("wall_sit"))), selectedExerciseId = "wall_sit")
        val warmupHold = hold.copy(draft = hold.draft.copy(isWarmup = true))
        assertEquals(WorkoutPrimaryKind.START_HOLD, derive(warmupHold).kind)
        val held = derive(warmupHold, hold = HoldTimerUiState(running = true, totalSeconds = 30, remainingSeconds = 18, elapsedSeconds = 12))
        assertEquals(WorkoutPrimaryKind.LOG_WARMUP, held.kind)
        assertEquals("Log warm-up", held.verb())
        assertEquals(WorkoutPrimaryKind.LOG_WARMUP, derive(normal.copy(draft = normal.draft.copy(isWarmup = true))).kind)
    }

    @Test
    fun timerTickPreservesPressIdentityButAChangedDraftOrSavedSetInvalidatesIt() {
        val state = state()
        val first = derive(state, stopwatch = SetStopwatchUiState(running = true, used = true, elapsedSeconds = 12))
        val later = derive(state, stopwatch = SetStopwatchUiState(running = true, used = true, elapsedSeconds = 13))
        assertEquals(first.identity, later.identity)
        assertEquals(12, first.durationSeconds)
        assertEquals(13, later.durationSeconds)
        assertFalse(first.identity == derive(state.copy(draft = state.draft.copy(reps = 7))).identity)
        assertFalse(first.identity == derive(state.copy(session = state.session!!.copy(sets = listOf(set("squat"))))).identity)
    }

    private fun derive(
        state: ActiveWorkoutUiState,
        extra: Boolean = false,
        hold: HoldTimerUiState = HoldTimerUiState(),
        stopwatch: SetStopwatchUiState = SetStopwatchUiState(),
    ) = WorkoutPrimaryActions.derive(state, extra, hold, stopwatch, timedGeneration = 1, activation = 0)

    private fun state(selected: String = "squat", sets: List<SetLog> = emptyList()) = ActiveWorkoutUiState(
        loadState = SessionLoadState.FOUND,
        session = WorkoutSession(
            id = "session", routineId = null, routineName = "Workout", date = 1, notes = "",
            durationMinutes = 0, startedAt = 1, finishedAt = null,
            exercises = listOf(lift("squat"), lift("row")), sets = sets,
        ),
        selectedExerciseId = selected, draft = ActiveExerciseDraft(weightKg = 60.0),
        liftReadiness = LiftEntryReadiness.READY,
    )

    private fun lift(id: String) = SessionExercise(
        id = "lift-$id", sessionId = "session",
        exercise = Exercise(id = id, name = if (id == "wall_sit") "Wall Sit" else id, muscleGroup = "Legs", notes = "", isCustom = false),
        sortOrder = 0, targetSets = 1, targetReps = 5, targetWeightKg = 60.0, restSeconds = 90,
    )

    private fun set(id: String) = SetLog(
        id = "set-$id", sessionId = "session", exerciseId = id, exerciseName = id,
        setNumber = 1, weightKg = 60.0, reps = 5, rpe = null, isWarmup = false, completedAt = 1,
    )
}
