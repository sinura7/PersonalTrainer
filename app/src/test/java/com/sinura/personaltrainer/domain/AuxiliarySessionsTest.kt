package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliarySessionsTest {
    @Test
    fun recognizesPackRoutineByTitleAndLifts() {
        val pack = AuxiliaryPacks.Stretch
        val routine = Routine(
            id = "r-pack",
            name = pack.title,
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = pack.lifts.mapIndexed { index, lift ->
                RoutineExercise(
                    id = "re-$index",
                    routineId = "r-pack",
                    exercise = Exercise(
                        id = lift.exerciseId,
                        name = lift.exerciseId,
                        muscleGroup = "",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = index,
                    targetSets = lift.sets,
                    targetReps = lift.reps,
                    targetWeightKg = null,
                    restSeconds = lift.restSeconds,
                    targetSeconds = null,
                    targetSecondsMax = null,
                )
            },
        )
        assertTrue(AuxiliarySessions.isAuxiliaryRoutine(routine))
    }

    @Test
    fun userRoutineNamedCoreWithDifferentLiftsCountsAsWorkingVolume() {
        val routine = Routine(
            id = "r-user-core",
            name = "Core",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf(
                RoutineExercise(
                    id = "re-1",
                    routineId = "r-user-core",
                    exercise = Exercise(
                        id = "ex-bench-press",
                        name = "Bench",
                        muscleGroup = "",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 60.0,
                    restSeconds = 90,
                    targetSeconds = null,
                    targetSecondsMax = null,
                ),
            ),
        )
        val session = WorkoutSession(
            id = "s",
            routineId = routine.id,
            routineName = routine.name,
            date = 0L,
            notes = "",
            durationMinutes = 20,
            startedAt = 0L,
            finishedAt = 1L,
            exercises = routine.exercises.map { item ->
                SessionExercise(
                    id = item.id,
                    sessionId = "s",
                    exercise = item.exercise,
                    sortOrder = item.sortOrder,
                    targetSets = item.targetSets,
                    targetReps = item.targetReps,
                    targetWeightKg = item.targetWeightKg,
                    restSeconds = item.restSeconds,
                    targetSeconds = item.targetSeconds,
                    targetSecondsMax = item.targetSecondsMax,
                )
            },
            sets = emptyList(),
        )
        assertFalse(AuxiliarySessions.isAuxiliarySession(session, routine))
        assertTrue(AuxiliarySessions.countsAsWorkingVolume(session, routine))
    }
}
