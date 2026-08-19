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
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.timer.RestTimerStatePersistence
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.timer.SharedPrefsRestTimerStatePersistence
import com.sinura.personaltrainer.workout.WorkoutDraftCache

class AppContainer(context: Context) {
    private val database: TrainerDatabase = TrainerDatabase.create(context)

    val exerciseRepository: ExerciseRepository = ExerciseRepository(
        exerciseDao = database.exerciseDao(),
        routineDao = database.routineDao(),
        workoutDao = database.workoutDao(),
    )
    val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    val workoutRepository: WorkoutRepository = WorkoutRepository(database, database.workoutDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
    // Exposed so the alarm receiver can read timer state after a process death, before any
    // ViewModel exists.
    val restTimerStatePersistence: RestTimerStatePersistence =
        SharedPrefsRestTimerStatePersistence(context)
    val restTimerStore: RestTimerStore = RestTimerStore(restTimerStatePersistence)
    val restTimerController: RestTimerController =
        RestTimerController(context, restTimerStore, restTimerStatePersistence)
    val workoutDraftCache: WorkoutDraftCache = WorkoutDraftCache()
    val backupRepository: BackupRepository = BackupRepository(
        localBackupRepository = LocalBackupRepository(
            database = database,
            preferencesRepository = preferencesRepository,
            // Runs at the wipe choke point: both of these hold a session id that is about to
            // stop existing, and a running rest timer would keep counting for a dead workout.
            onBeforeRestore = {
                restTimerController.stop()
                workoutDraftCache.clearAll()
            },
            safetySnapshotDir = java.io.File(context.filesDir, "safety-snapshots"),
        ),
        preferencesRepository = preferencesRepository,
        driveAuthClient = DriveAuthClient(),
        driveRestClient = DriveRestClient(),
        networkChecker = NetworkChecker(context),
    )
}
