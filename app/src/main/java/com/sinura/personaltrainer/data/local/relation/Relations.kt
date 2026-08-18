package com.sinura.personaltrainer.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity

data class RoutineExerciseWithExercise(
    @Embedded val item: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val items: List<RoutineExerciseWithExercise>,
)

data class SessionExerciseWithExercise(
    @Embedded val item: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class SetLogWithExercise(
    @Embedded val set: SetLogEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class SessionWithDetails(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<SessionExerciseWithExercise>,
    @Relation(
        entity = SetLogEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val sets: List<SetLogWithExercise>,
)
