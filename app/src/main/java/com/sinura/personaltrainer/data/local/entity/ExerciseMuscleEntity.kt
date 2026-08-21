package com.sinura.personaltrainer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * What one exercise trains, and how much of it.
 *
 * The v1 model was a single free-text `muscleGroup` per exercise, which meant a deadlift was
 * "Posterior chain" and a bench press was "Chest" — one label carrying a whole set's credit.
 * The body map then had to guess: it derived secondaries from a hard-coded table keyed on two
 * strings, and gave every derived secondary the same flat 0.4. So a deadlift credited the back
 * fully and the glutes at 0.4, when the honest split is close to the reverse.
 *
 * A junction table says it once, per lift, in data: exactly one primary at weight 1.0 and a
 * handful of secondaries in (0, 0.5]. `exercises.muscleGroup` survives untouched beside it as
 * display text — the planner, the library filter and every v1 backup still read it.
 */
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exerciseId", "muscleKey"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("muscleKey")],
)
data class ExerciseMuscleEntity(
    val exerciseId: String,
    val muscleKey: String,
    val weight: Double,
)
