package com.sinura.personaltrainer

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import com.sinura.personaltrainer.data.backup.DriveAuthClient
import com.sinura.personaltrainer.data.backup.DriveRestClient
import com.sinura.personaltrainer.data.backup.NetworkChecker
import com.sinura.personaltrainer.data.backup.RestoreJournalStore
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
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.timer.RestTimerGateway
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Real repositories on an in-memory database, plus a controllable insights stream.
 *
 * ViewModel tests assert projections and actions, not [TrainingInsightsCalculator].
 */
class FakeAppDependencies(
    context: Context,
    insights: Flow<TrainingInsights> = MutableStateFlow(TrainingInsights()),
    val safetySnapshotDir: File = File(context.cacheDir, "safety-snapshots-${System.nanoTime()}")
        .also { it.mkdirs() },
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
        WorkoutRepository(
            database,
            database.workoutDao(),
            dbMaintenance,
            restoreInProgress = { backupRepository.restoreInProgress() },
        )
    private val prefsContext = IsolatedAppContext(context.applicationContext)
    private val prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsStore = PreferenceDataStoreFactory.create(
        scope = prefsScope,
        produceFile = { File(prefsContext.filesDir, "datastore/user_settings.preferences_pb") },
    )
    override val preferencesRepository: PreferencesRepository =
        PreferencesRepository(prefsContext, prefsStore)
    override val onboardingApplier: OnboardingApplier = OnboardingApplier(
        routineRepository = routineRepository,
        scheduleRepository = scheduleRepository,
        preferencesRepository = preferencesRepository,
    )
    override val restTimerStatePersistence: RestTimerStatePersistence =
        SharedPrefsRestTimerStatePersistence(context)
    override val restTimerStore: RestTimerStore = RestTimerStore(restTimerStatePersistence)
    private val inMemoryRestTimer = InMemoryRestTimerGateway(restTimerStore)
    override val restTimerController: RestTimerGateway = inMemoryRestTimer

    fun setExactAlarmAttempt(attempt: ExactAlarmAttempt) {
        inMemoryRestTimer.setAttempt(attempt)
    }
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
    override val pendingAnswerReplay = MutableStateFlow(false)
    override val startTrainingDay: StartTrainingDay = StartTrainingDay(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
    )
    val restoreJournal = RestoreJournalStore(
        File(context.cacheDir, "restore-journal-${System.nanoTime()}").also { it.mkdirs() },
    )
    val localBackupRepository = LocalBackupRepository(
        database = database,
        preferencesRepository = preferencesRepository,
        onBeforeRestore = {},
        safetySnapshotDir = safetySnapshotDir,
    )
    override val backupRepository: BackupRepository = BackupRepository(
        localBackupRepository = localBackupRepository,
        preferencesRepository = preferencesRepository,
        dbMaintenance = dbMaintenance,
        driveAuthClient = DriveAuthClient(),
        driveRestClient = DriveRestClient(),
        networkChecker = NetworkChecker(context),
        restoreJournal = restoreJournal,
    )

    fun close() {
        val preferencesJob = prefsScope.coroutineContext[Job]
        preferencesJob?.cancel()
        runBlocking { preferencesJob?.join() }
        database.close()
    }
}

/** Cancels [viewModelScope] so collectors do not outlive the test. */
fun ViewModel.clearForTest() {
    viewModelScope.cancel()
}

/** Cancels and awaits every ViewModel child before a test resets Dispatchers.Main. */
suspend fun ViewModel.clearAndJoinForTest() {
    viewModelScope.coroutineContext[Job]?.cancelAndJoin()
}

/**
 * Each fake graph gets its own DataStore file *and* its own
 * [PreferenceDataStoreFactory] instance. A unique `filesDir` is not enough:
 * [androidx.datastore.preferences.preferencesDataStore] is a process singleton.
 */
private class IsolatedAppContext(base: Context) : ContextWrapper(base) {
    private val root = File(base.cacheDir, "fake-prefs-${System.nanoTime()}").also { it.mkdirs() }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
}

/**
 * No Context, service, AlarmManager, or wall time. ViewModel tests assert the
 * contract and store; Android delivery is exercised by connected tests.
 */
private class InMemoryRestTimerGateway(
    private val store: RestTimerStore,
) : RestTimerGateway {
    private var elapsedRealtimeMs: Long = 0L
    private val _lastAlarmSchedule = MutableStateFlow(AlarmScheduleResult.EXACT)
    private val _exactAlarmAttempt = MutableStateFlow(ExactAlarmAttempt.EXACT)

    override val snapshot: StateFlow<RestTimerSnapshot> = store.snapshot
    override val remainingSeconds: Flow<Int> = snapshot
        .map { state -> if (state.running) state.remainingSeconds(elapsedRealtimeMs) else 0 }
        .distinctUntilChanged()
    override val runningSessionId: Flow<String?> = snapshot
        .map { state -> state.sessionId.takeIf { state.running } }
        .distinctUntilChanged()
    override val lastAlarmSchedule: StateFlow<AlarmScheduleResult> = _lastAlarmSchedule
    override val exactAlarmAttempt: StateFlow<ExactAlarmAttempt> = _exactAlarmAttempt

    fun setAttempt(attempt: ExactAlarmAttempt) {
        _exactAlarmAttempt.value = attempt
    }

    override fun start(totalSeconds: Int, sessionId: String?) {
        store.start(totalSeconds, sessionId, elapsedRealtimeMs)
    }

    override fun adjust(deltaSeconds: Int) {
        store.adjust(deltaSeconds, elapsedRealtimeMs)
    }

    override fun stop(fromService: Boolean) {
        store.clear()
    }

    override fun rehydrate(): Boolean = false
}
