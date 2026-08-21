package com.sinura.personaltrainer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import com.sinura.personaltrainer.data.backup.DriveAuthClient
import com.sinura.personaltrainer.data.backup.DriveRestClient
import com.sinura.personaltrainer.data.backup.NetworkChecker
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.repository.BackupRepository
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.insights.TrainingInsightsSource
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.timer.RestTimerStatePersistence
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.timer.SharedPrefsRestTimerStatePersistence
import com.sinura.personaltrainer.workout.DiscardWorkout
import com.sinura.personaltrainer.workout.FinishWorkout
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.WorkoutDraftCache

class AppContainer(context: Context) {
    private val database: TrainerDatabase = TrainerDatabase.create(context)

    /**
     * One lock over every wholesale rewrite of the catalog. The startup seed and a restore both
     * pass through here, so they queue instead of racing each other across the same tables.
     */
    val dbMaintenance: DbMaintenance = DbMaintenance(database)

    val exerciseRepository: ExerciseRepository = ExerciseRepository(
        exerciseDao = database.exerciseDao(),
        routineDao = database.routineDao(),
        workoutDao = database.workoutDao(),
        catalogDao = database.catalogDao(),
    )
    val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    /** The week the user pinned. Nothing else in the app is allowed to write it. */
    val scheduleRepository: ScheduleRepository = ScheduleRepository(database.scheduleDao())
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

    // Every finish and every discard in the app routes through these two, so no surface can
    // end a workout while leaving a rest timer running or a draft pointing at a dead session.
    val finishWorkout: FinishWorkout = FinishWorkout(
        workoutRepository = workoutRepository,
        restTimer = restTimerController,
        draftCache = workoutDraftCache,
    )
    val discardWorkout: DiscardWorkout = DiscardWorkout(
        workoutRepository = workoutRepository,
        restTimer = restTimerController,
        draftCache = workoutDraftCache,
    )

    /** One analytics pipeline behind Home, Schedule and Progress. */
    val trainingInsights: TrainingInsightsSource = TrainingInsightsSource(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
        exerciseRepository = exerciseRepository,
        preferencesRepository = preferencesRepository,
        scheduleRepository = scheduleRepository,
    )

    /**
     * A one-shot request to preview a suggested week, handed from Home's empty hero to the Plan
     * tab. App-scoped state rather than a nav argument or a captured lambda for the usual
     * reason: the tap and the arrival are separated by a navigation, and an Activity recreated
     * in between would drop a callback. The Plan tab consumes it exactly once and writes false
     * back; nothing is persisted by the deep link.
     */
    val pendingWeekSuggestion = MutableStateFlow(false)

    val startTrainingDay: StartTrainingDay = StartTrainingDay(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
    )
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
        dbMaintenance = dbMaintenance,
        driveAuthClient = DriveAuthClient(),
        driveRestClient = DriveRestClient(),
        networkChecker = NetworkChecker(context),
    )
}
