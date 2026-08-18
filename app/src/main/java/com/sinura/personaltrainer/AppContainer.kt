package com.sinura.personaltrainer

import android.content.Context
import com.sinura.personaltrainer.data.backup.DriveAuthClient
import com.sinura.personaltrainer.data.backup.DriveRestClient
import com.sinura.personaltrainer.data.backup.NetworkChecker
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.repository.BackupRepository
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository

class AppContainer(context: Context) {
    private val database: TrainerDatabase = TrainerDatabase.create(context)

    val exerciseRepository: ExerciseRepository = ExerciseRepository(
        exerciseDao = database.exerciseDao(),
        routineDao = database.routineDao(),
        workoutDao = database.workoutDao(),
    )
    val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    val workoutRepository: WorkoutRepository = WorkoutRepository(database.workoutDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
    val backupRepository: BackupRepository = BackupRepository(
        localBackupRepository = LocalBackupRepository(database, preferencesRepository),
        preferencesRepository = preferencesRepository,
        driveAuthClient = DriveAuthClient(),
        driveRestClient = DriveRestClient(),
        networkChecker = NetworkChecker(context),
    )
}
