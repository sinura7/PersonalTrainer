package com.sinura.personaltrainer.ui.workout

import android.database.sqlite.SQLiteFullException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.AlarmScheduleResult
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.timer.RestTimerGateway
import com.sinura.personaltrainer.timer.RestTimerStore
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.workout.FinishWorkout
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Native Android SQLite, isolated preferences, and the production repositories/VMs.
 * No catalog, history, profile, timer service or preferences in the installed app are written.
 * A DAO delegate holds/fails the actual one-column note write before SQLite receives it.
 */
internal class NativeWorkoutTruthFixture {
    val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
    val owner = app.container
    private val database = Room.inMemoryDatabaseBuilder(app, TemperDatabase::class.java).build()
    private val prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsFile = File(app.cacheDir, "ux23-${UUID.randomUUID()}.preferences_pb")
    private val prefsStore = PreferenceDataStoreFactory.create(scope = prefsScope, produceFile = prefsFile::getAbsoluteFile)
    val failWrites = AtomicInteger(0)
    val failReads = AtomicBoolean(false)
    val attemptedWrites = CopyOnWriteArrayList<String>()
    @Volatile var writeGate: CompletableDeferred<Unit>? = null
    private val realDao = database.workoutDao()
    private val controlledDao = object : WorkoutDao by realDao {
        override suspend fun updateSessionNotes(id: String, notes: String) {
            attemptedWrites += notes
            writeGate?.await()
            if (failWrites.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                throw SQLiteFullException("Synthetic UX23 note-write failure")
            }
            realDao.updateSessionNotes(id, notes)
        }
        override fun observeSession(id: String): Flow<SessionWithDetails?> = flow {
            if (failReads.get()) throw android.database.sqlite.SQLiteException("Synthetic UX23 read failure")
            emitAll(realDao.observeSession(id))
        }
    }
    private val maintenance = DbMaintenance(database)
    val repository = WorkoutRepository(database, controlledDao, maintenance)
    private val preferences = PreferencesRepository(context = app, dataStore = prefsStore,
        bodyweightDao = database.bodyweightDao(), trainingBlockDao = database.trainingBlockDao())
    private val exercises = ExerciseRepository(database.exerciseDao(), database.routineDao(), realDao,
        database.catalogDao(), database)
    val timer = NoServiceRestTimer()
    private val drafts = WorkoutDraftCache()
    private val insights = object : TrainingInsightsPublisher {
        override fun observeShared(includeWeekPlan: Boolean): Flow<TrainingInsights> = MutableStateFlow(TrainingInsights())
        override fun observe(window: Flow<HeatWindow>, refresh: Flow<Any?>, includeWeekPlan: Boolean): Flow<TrainingInsights> =
            MutableStateFlow(TrainingInsights())
    }
    val dependencies = object : AppDependencies by owner {
        override val dbMaintenance = maintenance
        override val workoutRepository = repository
        override val preferencesRepository = preferences
        override val exerciseRepository = exercises
        override val workoutDraftCache = drafts
        override val restTimerController = timer
        override val restTimerStore = RestTimerStore()
        override val trainingInsights = insights
        override val finishWorkout = FinishWorkout(repository, timer, drafts)
    }
    val sessionId = "ux23-session-${UUID.randomUUID()}"
    val exerciseId = "ex-leg-extension"
    private val viewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    fun seed(finished: Boolean = false, notes: String = "Stored original", savedSets: Int = 1) = runBlocking(Dispatchers.IO) {
        withTimeout(30_000) {
            preferences.setWeightUnit(WeightUnit.KG)
            preferences.setTrainingGoal(TrainingGoal.HYPERTROPHY)
            val catalogExercise = owner.exerciseRepository.observeById(exerciseId).first { it != null }!!
            database.exerciseDao().insert(catalogExercise.toEntity())
            database.catalogDao().insertCredits(catalogExercise.muscles.map { ExerciseMuscleEntity(exerciseId, it.muscleKey, it.weight) })
            val now = System.currentTimeMillis()
            realDao.upsertSession(WorkoutSessionEntity(sessionId, null, "UX23 native fixture", now,
                notes, 0, now - 60_000, null))
            realDao.insertSessionExercises(listOf(SessionExerciseEntity("ux23-lift-$sessionId", sessionId,
                exerciseId, 0, 2, 10, 70.0, 90)))
            repeat(savedSets) { index -> repository.logSet(sessionId, exerciseId, 70.0, 10,
                if (index == 0) 6 else 8, false) }
            if (finished) repository.finishSession(sessionId, notes)
        }
    }

    fun active(id: String = sessionId) = ActiveWorkoutViewModel(app, SavedStateHandle(mapOf("sessionId" to id)), dependencies)
        .also { viewModels += it }
    fun detail(id: String = sessionId) = SessionDetailViewModel(app, SavedStateHandle(mapOf("sessionId" to id)), dependencies)
        .also { viewModels += it }
    fun stored() = runBlocking(Dispatchers.IO) { checkNotNull(repository.getSession(sessionId)) }

    fun close() = runBlocking {
        writeGate?.complete(Unit)
        viewModels.forEach { it.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
        prefsScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        database.close()
        check(!prefsFile.exists() || prefsFile.delete()) { "Could not remove isolated UX23 preference file" }
    }
}

/** A native notes fixture must never gain a foreground service and bias Recents/process survival. */
internal class NoServiceRestTimer : RestTimerGateway {
    override val snapshot = MutableStateFlow(RestTimerSnapshot())
    override val remainingSeconds = MutableStateFlow(0)
    override val runningSessionId = MutableStateFlow<String?>(null)
    override val lastAlarmSchedule = MutableStateFlow(AlarmScheduleResult.FAILED)
    override val exactAlarmAttempt = MutableStateFlow(ExactAlarmAttempt.BEST_EFFORT)
    override val lastCompletedTimerId = MutableStateFlow<String?>(null)
    override fun start(totalSeconds: Int, sessionId: String?) = error("This notes/coach fixture must not start rest")
    override fun adjust(deltaSeconds: Int) = error("This notes/coach fixture must not adjust rest")
    override fun stop(fromService: Boolean) { snapshot.value = RestTimerSnapshot() }
    override fun skipIfShown(timerId: String, fromService: Boolean) = false
    override fun rehydrate() = false
}
