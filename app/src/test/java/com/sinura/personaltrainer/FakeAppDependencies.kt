package com.sinura.personaltrainer

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.CoroutineDispatcher
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
import com.sinura.personaltrainer.activity.ConfirmActivity
import com.sinura.personaltrainer.activity.DiscardActivity
import com.sinura.personaltrainer.activity.FinishActivity
import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.BackupRepository
import com.sinura.personaltrainer.timer.CardioTimerPersistence
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.OnboardingApplier
import com.sinura.personaltrainer.data.repository.GoalRepository
import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.ScheduleRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.reminder.NoOpReminderScheduler
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
import com.sinura.personaltrainer.workout.StartLiveCardio
import com.sinura.personaltrainer.workout.StartOccurrence
import com.sinura.personaltrainer.workout.StartTrainingDay
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import java.io.File
import java.util.concurrent.Executors
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
    /**
     * When set, DataStore, [ioDispatcher] and [computeDispatcher] all run here.
     *
     * Left null by default, which keeps those hops on real threads the test scheduler cannot
     * see: a view model coroutine that suspends on a preferences read parks until something pumps the scheduler again, so a
     * test that waits on the resulting write never sees it. The poll loops this packet is
     * removing were doing that pumping as a side effect of waiting, which is why deleting one
     * without passing a test dispatcher here hangs.
     *
     * Pass the test's own dispatcher to put those hops on the scheduler, and
     * `advanceUntilIdle()` then drives the whole read-compute-write chain to completion.
     *
     * Room stays on real threads. `UnconfinedTestDispatcher.dispatch` throws unless the
     * caller is `yield`, so it cannot be an `Executor`; a `StandardTestDispatcher`
     * executor queues work that `runBlocking` never pumps. Tests wait on Room with
     * `first { }` on the Flow. The transaction executor is a separate single thread —
     * putting it on the test dispatcher deadlocks `withTransaction`, and setting only
     * the query executor would assign both to the same pool.
     */
    scheduler: CoroutineDispatcher? = null,
    prefsDispatcher: CoroutineDispatcher = scheduler ?: Dispatchers.IO,
    override val ioDispatcher: CoroutineDispatcher = scheduler ?: Dispatchers.IO,
    override val computeDispatcher: CoroutineDispatcher = scheduler ?: Dispatchers.Default,
    override val time: com.sinura.personaltrainer.domain.TimePort = JvmTime,
) : AppDependencies {
    /**
     * Real threads, not the test scheduler. See the constructor KDoc on why Room stays
     * off that dispatcher.
     */
    private val queryExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "room-query-test").apply { isDaemon = true }
    }
    private val transactionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "room-txn-test").apply { isDaemon = true }
    }

    val database: TemperDatabase = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(queryExecutor)
        .setTransactionExecutor(transactionExecutor)
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
    override val goalRepository: GoalRepository = GoalRepository(database.goalDao())
    override val plannerRepository: PlannerRepository = PlannerRepository(
        database = database,
        scheduler = NoOpReminderScheduler(),
        time = time,
    )
    override val pendingOccurrenceId = MutableStateFlow<String?>(null)
    override val workoutRepository: WorkoutRepository =
        WorkoutRepository(
            database,
            database.workoutDao(),
            dbMaintenance,
            restoreInProgress = { backupRepository.restoreInProgress() },
        )
    private val prefsContext = IsolatedAppContext(context.applicationContext)
    private val prefsScope = CoroutineScope(SupervisorJob() + prefsDispatcher)
    private val prefsStore = PreferenceDataStoreFactory.create(
        scope = prefsScope,
        produceFile = { File(prefsContext.filesDir, "datastore/user_settings.preferences_pb") },
    )
    override val preferencesRepository: PreferencesRepository =
        PreferencesRepository(
            prefsContext,
            prefsStore,
            bodyweightDao = database.bodyweightDao(),
            trainingBlockDao = database.trainingBlockDao(),
        )
    override val activityRepository: ActivityRepository = ActivityRepository(
        database,
        dbMaintenance = dbMaintenance,
        onOccurrenceCompleted = { plannerRepository.cancelRemindersFor(it) },
    )
    override val confirmActivity: ConfirmActivity =
        ConfirmActivity(activityRepository, IdFactory.Uuid, time)
    override val startLiveActivity: StartLiveActivity =
        StartLiveActivity(activityRepository, IdFactory.Uuid, time)
    override val discardActivity: DiscardActivity = DiscardActivity(activityRepository)
    override val finishActivity: FinishActivity = FinishActivity(activityRepository, time)
    override val cardioTimerPersistence: CardioTimerPersistence = InMemoryCardioTimerPersistence()
    override val startLiveCardio: StartLiveCardio = StartLiveCardio(
        startLiveActivity = startLiveActivity,
        cardioTimerPersistence = cardioTimerPersistence,
    )
    override val onboardingApplier: OnboardingApplier = OnboardingApplier(
        database = database,
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
    override val pendingCustomWeek =
        MutableStateFlow<com.sinura.personaltrainer.domain.CustomWeekLaunch?>(null)
    override val startTrainingDay: StartTrainingDay = StartTrainingDay(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
    )
    override val startOccurrence: StartOccurrence = StartOccurrence(
        plannerRepository = plannerRepository,
        routineRepository = routineRepository,
        startTrainingDay = startTrainingDay,
        startLiveCardio = startLiveCardio,
    )
    val restoreJournal = RestoreJournalStore(
        File(context.cacheDir, "restore-journal-${System.nanoTime()}").also { it.mkdirs() },
    )
    val localBackupRepository = LocalBackupRepository(
        database = database,
        activityDao = database.activityDao(),
        plannerDao = database.plannerDao(),
        goalDao = database.goalDao(),
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
        ioDispatcher = ioDispatcher,
    )

    fun close() {
        val preferencesJob = prefsScope.coroutineContext[Job]
        preferencesJob?.cancel()
        runBlocking { preferencesJob?.join() }
        database.close()
        queryExecutor.shutdown()
        transactionExecutor.shutdown()
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
    private val _lastCompletedTimerId = MutableStateFlow<String?>(null)
    override val lastCompletedTimerId: StateFlow<String?> = _lastCompletedTimerId
    override val lastAlarmSchedule: StateFlow<AlarmScheduleResult> = _lastAlarmSchedule
    override val exactAlarmAttempt: StateFlow<ExactAlarmAttempt> = _exactAlarmAttempt

    fun setAttempt(attempt: ExactAlarmAttempt) {
        _exactAlarmAttempt.value = attempt
    }

    override fun markCompleted(timerId: String) {
        if (timerId.isBlank()) return
        _lastCompletedTimerId.value = timerId
    }

    override fun start(totalSeconds: Int, sessionId: String?) {
        _lastCompletedTimerId.value = null
        store.start(totalSeconds, sessionId, elapsedRealtimeMs)
    }

    override fun adjust(deltaSeconds: Int) {
        store.adjust(deltaSeconds, elapsedRealtimeMs)
    }

    override fun stop(fromService: Boolean) {
        _lastCompletedTimerId.value = null
        store.clear()
    }

    override fun rehydrate(): Boolean = false
}

private class InMemoryCardioTimerPersistence : CardioTimerPersistence {
    private var stored: PersistedCardioTimer? = null

    override fun save(state: PersistedCardioTimer) {
        stored = state
    }

    override fun load(): PersistedCardioTimer? = stored

    override fun clear() {
        stored = null
    }
}
