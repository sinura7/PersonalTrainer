package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WorkoutSession

data class TestWorkoutFixture(
    val exercise: Exercise,
    val routine: Routine,
    val session: WorkoutSession,
)

suspend fun seedTestWorkout(
    deps: FakeAppDependencies,
    exerciseId: String = "test-squat",
    exerciseName: String = "Test squat",
    routineId: String = "test-routine",
    routineName: String = "Test lower",
    targetSets: Int = 3,
    targetReps: Int = 5,
    targetWeightKg: Double? = 100.0,
    restSeconds: Int = 90,
    loggedSets: List<TestSetInput> = emptyList(),
    finish: Boolean = false,
    notes: String = "",
): TestWorkoutFixture {
    deps.database.exerciseDao().insertAll(
        listOf(
            ExerciseEntity(
                id = exerciseId,
                name = exerciseName,
                muscleGroup = "Quads",
                notes = "",
                isCustom = false,
                nameKey = exerciseName.lowercase(),
            ),
        ),
    )
    deps.database.routineDao().upsertRoutine(
        RoutineEntity(
            id = routineId,
            name = routineName,
            notes = "",
            createdAt = TEST_STAMP,
            updatedAt = TEST_STAMP,
        ),
    )
    deps.database.routineDao().upsertRoutineExercise(
        RoutineExerciseEntity(
            id = "item-$routineId-$exerciseId",
            routineId = routineId,
            exerciseId = exerciseId,
            sortOrder = 0,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeightKg = targetWeightKg,
            restSeconds = restSeconds,
        ),
    )
    val exercise = checkNotNull(deps.exerciseRepository.getById(exerciseId))
    val routine = checkNotNull(deps.routineRepository.getById(routineId))
    val live = deps.workoutRepository.startRoutine(routine)
    loggedSets.forEach { set ->
        deps.workoutRepository.logSet(
            sessionId = live.id,
            exerciseId = exerciseId,
            weightKg = set.weightKg,
            reps = set.reps,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
        )
    }
    if (finish) deps.workoutRepository.finishSession(live.id, notes)
    return TestWorkoutFixture(
        exercise = exercise,
        routine = routine,
        session = checkNotNull(deps.workoutRepository.getSession(live.id)),
    )
}

suspend fun insertTestExercise(
    deps: FakeAppDependencies,
    id: String,
    name: String,
    muscleGroup: String = "Back",
    isCustom: Boolean = false,
): Exercise {
    deps.database.exerciseDao().insertAll(
        listOf(
            ExerciseEntity(
                id = id,
                name = name,
                muscleGroup = muscleGroup,
                notes = "",
                isCustom = isCustom,
                nameKey = name.lowercase(),
            ),
        ),
    )
    return checkNotNull(deps.exerciseRepository.getById(id))
}

data class TestSetInput(
    val weightKg: Double,
    val reps: Int,
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
)

const val TEST_STAMP = 1_700_000_000_000L
