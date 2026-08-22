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
import com.sinura.personaltrainer.data.repository.OnboardingApplier
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

class AppContainer(context: Context) : AppDependencies {
    private val database: TrainerDatabase = TrainerDatabase.create(context)

    /**
     * One lock over every wholesale rewrite of the catalog. The startup seed and a restore both
     * pass through here, so they queue instead of racing each other across the same tables.
     */
    override val dbMaintenance: DbMaintenance = DbMaintenance(database)

    override val exerciseRepository: ExerciseRepository = ExerciseRepository(
        exerciseDao = database.exerciseDao(),
        routineDao = database.routineDao(),
        workoutDao = database.workoutDao(),
        catalogDao = database.catalogDao(),
    )
    override val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    /** The week the user pinned. Nothing else in the app is allowed to write it. */
    override val scheduleRepository: ScheduleRepository = ScheduleRepository(database.scheduleDao())
    override val workoutRepository: WorkoutRepository = WorkoutRepository(database, database.workoutDao())
    override val preferencesRepository: PreferencesRepository = PreferencesRepository(context)

    /**
     * The one writer that spans preferences, routines and the schedule together. Constructed
     * here rather than in the view model because that ordering is a property of the app, not
     * of a screen.
     */
    override val onboardingApplier: OnboardingApplier = OnboardingApplier(
        routineRepository = routineRepository,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
    )
    // Exposed so the alarm receiver can read timer state after a process death, before any
    // ViewModel exists.
    override val restTimerStatePersistence: RestTimerStatePersistence =
        SharedPrefsRestTimerStatePersistence(context)
    override val restTimerStore: RestTimerStore = RestTimerStore(restTimerStatePersistence)
    override val restTimerController: RestTimerController =
        RestTimerController(context, restTimerStore, restTimerStatePersistence)
    override val workoutDraftCache: WorkoutDraftCache = WorkoutDraftCache()

    // Every finish and every discard in the app routes through these two, so no surface can
    // end a workout while leaving a rest timer running or a draft pointing at a dead session.
    override val finishWorkout: FinishWorkout = FinishWorkout(
        workoutRepository = workoutRepository,
        restTimer = restTimerController,
        draftCache = workoutDraftCache,
    )
    override val discardWorkout: DiscardWorkout = DiscardWorkout(
        workoutRepository = workoutRepository,
        restTimer = restTimerController,
        draftCache = workoutDraftCache,
    )

    /** One analytics pipeline behind Home, Schedule and Progress. */
    override val trainingInsights: TrainingInsightsSource = TrainingInsightsSource(
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
    override val pendingWeekSuggestion = MutableStateFlow(false)

    override val pendingAnswerReplay = MutableStateFlow(false)

    override val startTrainingDay: StartTrainingDay = StartTrainingDay(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
    )
    override val backupRepository: BackupRepository = BackupRepository(
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
