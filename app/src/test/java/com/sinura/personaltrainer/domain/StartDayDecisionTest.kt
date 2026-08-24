package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Start does, before anything is written.
 *
 * Two of these replace behaviours that lied to the user, and both lied in the same direction —
 * by giving them *something* rather than telling them what was wrong.
 */
class StartDayDecisionTest {

    @Test
    fun pinnedDayWithDeletedRoutineFailsExplicitly() {
        val decision = decideStart(day = pinnedDay(), routine = null, inProgress = null)

        assertTrue(decision.toString(), decision is StartDayDecision.RoutineGone)
        assertEquals(
            "That day's routine no longer exists. Swap or unpin it in Plan.",
            (decision as StartDayDecision.RoutineGone).message,
        )
    }

    @Test
    fun pinnedDayWithEmptyRoutineFailsExplicitly() {
        // The old fallback started a free workout named "Legs" here. You found out at the gym.
        val decision = decideStart(
            day = pinnedDay(),
            routine = routine(id = ROUTINE, name = "Legs", lifts = emptyList()),
            inProgress = null,
        )

        assertTrue(decision.toString(), decision is StartDayDecision.RoutineGone)
        assertEquals(
            "Legs has no lifts yet. Add lifts or swap the day's routine.",
            (decision as StartDayDecision.RoutineGone).message,
        )
    }

    @Test
    fun inProgressSessionBlocksInsteadOfSilentResume() {
        val decision = decideStart(
            day = pinnedDay(),
            routine = routine(id = ROUTINE, name = "Legs", lifts = listOf("Squat")),
            inProgress = inProgress("live-1"),
        )

        assertEquals(StartDayDecision.Blocked("live-1"), decision)
    }

    @Test
    fun blockedWinsOverAMissingRoutine() {
        // Order matters: there is no point telling someone their routine is gone when the real
        // answer is that they are already mid-workout.
        val decision = decideStart(day = pinnedDay(), routine = null, inProgress = inProgress("live-1"))
        assertEquals(StartDayDecision.Blocked("live-1"), decision)
    }

    @Test
    fun restDayIgnored() {
        assertEquals(
            StartDayDecision.Rest,
            decideStart(day = pinnedDay().copy(isRest = true), routine = null, inProgress = null),
        )
    }

    @Test
    fun focusOnlyDayStartsFreeWorkoutNamedAfterFocus() {
        // A focus-only pin has no routine BY DESIGN, so a free workout is the right answer here
        // — the distinction the old code could not make.
        val decision = decideStart(
            day = pinnedDay().copy(routineId = null, routineName = null, focusTitle = "Pull"),
            routine = null,
            inProgress = null,
        )
        assertEquals(StartDayDecision.StartFree("Pull"), decision)
    }

    @Test
    fun aHealthyPinnedDayStartsItsRoutine() {
        val legs = routine(id = ROUTINE, name = "Legs", lifts = listOf("Squat"))
        assertEquals(
            StartDayDecision.StartRoutine(legs),
            decideStart(day = pinnedDay(), routine = legs, inProgress = null),
        )
    }

    private fun pinnedDay(): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = Weekday.MONDAY,
        isRest = false,
        focusKind = SessionFocusKind.LEGS,
        focusTitle = "Legs",
        routineId = ROUTINE,
        routineName = "Legs",
        reason = "Pinned to your week.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
        slotId = "slot-0",
    )

    private fun routine(id: String, name: String, lifts: List<String>): Routine = Routine(
        id = id,
        name = name,
        notes = "",
        createdAt = 0L,
        updatedAt = 0L,
        exercises = lifts.mapIndexed { index, lift ->
            RoutineExercise(
                id = "$id-$index",
                routineId = id,
                exercise = Exercise("ex-$index", lift, "Quads", "", false),
                sortOrder = index,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            )
        },
    )

    private fun inProgress(id: String): WorkoutSession = WorkoutSession(
        id = id,
        routineId = null,
        routineName = "Free workout",
        date = 1_700_000_000_000L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1_700_000_000_000L,
        finishedAt = null,
        exercises = emptyList(),
        sets = emptyList(),
    )

    private companion object {
        const val ROUTINE = "r-legs"
    }
}
