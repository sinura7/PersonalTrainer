package com.sinura.personaltrainer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.cancel
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
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.timer.RestTimerController
import com.sinura.personaltrainer.timer.RestTimerStatePersistence
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.timer.SharedPrefsRestTimerStatePersistence
import com.sinura.personaltrainer.workout.DiscardWorkout
import com.sinura.personaltrainer.workout.FinishWorkout
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Real repositories on an in-memory database, plus a controllable insights stream.
 *
 * ViewModel tests assert projections and actions, not [TrainingInsightsCalculator].
 */
class FakeAppDependencies(
    context: Context,
    insights: Flow<TrainingInsights> = MutableStateFlow(TrainingInsights()),
) : AppDependencies {
    val database: TrainerDatabase = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    override val dbMaintenance: DbMaintenance = DbMaintenance(database)
    override val exerciseRepository: ExerciseRepository = ExerciseRepository(
        exerciseDao = database.exerciseDao(),
        routineDao = database.routineDao(),
        workoutDao = database.workoutDao(),
        catalogDao = database.catalogDao(),
    )
    override val routineRepository: RoutineRepository = RoutineRepository(database.routineDao())
    override val scheduleRepository: ScheduleRepository = ScheduleRepository(database.scheduleDao())
    override val workoutRepository: WorkoutRepository =
        WorkoutRepository(database, database.workoutDao())
    override val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
    override val onboardingApplier: OnboardingApplier = OnboardingApplier(
        routineRepository = routineRepository,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
    )
    override val restTimerStatePersistence: RestTimerStatePersistence =
        SharedPrefsRestTimerStatePersistence(context)
    override val restTimerStore: RestTimerStore = RestTimerStore(restTimerStatePersistence)
    override val restTimerController: RestTimerController =
        RestTimerController(context, restTimerStore, restTimerStatePersistence)
    override val workoutDraftCache: WorkoutDraftCache = WorkoutDraftCache()
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
    override val trainingInsights: TrainingInsightsPublisher = object : TrainingInsightsPublisher {
        override fun observeShared(includeWeekPlan: Boolean): Flow<TrainingInsights> = insights
        override fun observe(
            window: Flow<HeatWindow>,
            refresh: Flow<Any?>,
            includeWeekPlan: Boolean,
        ): Flow<TrainingInsights> = insights
    }
    override val pendingWeekSuggestion = MutableStateFlow(false)
    override val startTrainingDay: StartTrainingDay = StartTrainingDay(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
    )
    override val backupRepository: BackupRepository = BackupRepository(
        localBackupRepository = LocalBackupRepository(
            database = database,
            preferencesRepository = preferencesRepository,
            onBeforeRestore = {},
            safetySnapshotDir = File(context.cacheDir, "safety-snapshots"),
        ),
        preferencesRepository = preferencesRepository,
        dbMaintenance = dbMaintenance,
        driveAuthClient = DriveAuthClient(),
        driveRestClient = DriveRestClient(),
        networkChecker = NetworkChecker(context),
    )

    fun close() {
        database.close()
    }
}

/** Cancels [viewModelScope] so collectors do not outlive the test. */
fun ViewModel.clearForTest() {
    viewModelScope.cancel()
}
