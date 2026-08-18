package com.sinura.personaltrainer

import android.content.Context
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository

class AppContainer(context: Context) {
    private val database: TrainerDatabase = TrainerDatabase.create(context)

    val exerciseRepository: ExerciseRepository = ExerciseRepository(database.exerciseDao())
    val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    val workoutRepository: WorkoutRepository = WorkoutRepository(database.workoutDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
}
