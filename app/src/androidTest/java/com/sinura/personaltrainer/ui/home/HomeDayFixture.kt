package com.sinura.personaltrainer.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.insights.TrainingInsightsPublisher
import com.sinura.personaltrainer.reminder.NoOpReminderScheduler
import com.sinura.personaltrainer.util.JvmTime
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** Private Room and preferences; only the read-side Home graph is exercised here. */
internal class HomeDayFixture(private val scenario: String) {
    private val app: PersonalTrainerApp = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(app, TemperDatabase::class.java).build()
    private val preferencesFile = File(app.cacheDir, "home-fixture-${System.nanoTime()}.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = object : TimePort by JvmTime {
        override fun nowMillis(): Long = TODAY * 86_400_000L + 43_200_000L
        override fun defaultZoneId(): String = "UTC"
    }
    private val insights = MutableStateFlow(TrainingInsights())
    val dependencies = object : AppDependencies by app.container {
        override val time = clock
        override val plannerRepository = PlannerRepository(database, NoOpReminderScheduler(), clock)
        override val workoutRepository = WorkoutRepository(database, database.workoutDao())
        override val activityRepository = ActivityRepository(database)
        override val preferencesRepository = PreferencesRepository(
            context = app,
            dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }),
            bodyweightDao = database.bodyweightDao(), trainingBlockDao = database.trainingBlockDao(),
        )
        override val trainingInsights = object : TrainingInsightsPublisher {
            override fun observeShared(includeWeekPlan: Boolean): Flow<TrainingInsights> = insights
            override fun observe(window: Flow<HeatWindow>, refresh: Flow<Any?>, includeWeekPlan: Boolean): Flow<TrainingInsights> = insights
        }
    }
    lateinit var vm: HomeViewModel
        private set

    fun seed() {
        runBlocking(Dispatchers.IO) {
            dependencies.preferencesRepository.setOnboardingComplete(true)
            dependencies.preferencesRepository.recordBodyweight(80.0, TODAY)
            val title = if (scenario == "long") "Lower-body training with a controlled eccentric and single-leg accessory work" else "Lower A"
            val exerciseNames = listOf(
                "Barbell Back Squat", "Romanian Deadlift",
                if (scenario == "long") "Rear-foot elevated split squat with a controlled eccentric" else "Walking Lunge",
            )
            val routine = Routine("routine", title, "", 1L, 1L, exerciseNames.mapIndexed { index, name ->
                RoutineExercise(
                    id = "item-$index", routineId = "routine",
                    exercise = Exercise(
                        id = "exercise-$index", name = name, muscleGroup = "Legs",
                        notes = "", isCustom = false,
                    ),
                    sortOrder = index, targetSets = 3, targetReps = 10,
                    targetWeightKg = null, restSeconds = 90,
                )
            })
            val records = if (scenario == "empty") emptyList() else listOf(
                record("historical", TODAY - 2, title),
                record("today", TODAY, title),
                record("cardio", TODAY - 2, "Walk").copy(kind = HistoryKind.ACTIVITY, workingSets = 0),
            )
            insights.value = TrainingInsights(summaries = records, routines = listOf(routine))
            if (scenario != "empty") {
                val rule = ScheduleRule(
                    id = "rule", weekday = Weekday.SUNDAY, hour = 18, minute = 0,
                    modality = ScheduleModality.STRENGTH, routineId = routine.id,
                    createdAtMs = 1L, updatedAtMs = 1L,
                )
                database.plannerDao().upsertRule(rule.toEntity())
                database.plannerDao().upsertOccurrences(listOf(
                    occurrence("historical-occurrence", TODAY - 2, OccurrenceStatus.DONE, "historical").toEntity(),
                    occurrence("skipped", TODAY - 1, OccurrenceStatus.SKIPPED).toEntity(),
                    when (scenario) {
                        "cross-date" -> occurrence("planned", TODAY, OccurrenceStatus.DONE, "historical")
                        "missing-link" -> occurrence("planned", TODAY, OccurrenceStatus.DONE, "missing")
                        else -> occurrence("planned", TODAY, OccurrenceStatus.PLANNED)
                    }.toEntity(),
                ))
            }
            if (scenario == "live") dependencies.workoutRepository.startFreeWorkout("Live workout")
        }
        vm = HomeViewModel(app, dependencies)
    }

    fun close() {
        runBlocking {
            if (::vm.isInitialized) vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
        database.close()
        preferencesFile.delete()
    }

    private fun occurrence(id: String, day: Long, status: OccurrenceStatus, recordId: String? = null) = ScheduleOccurrence(
        id = id, ruleId = "rule", status = status,
        captured = CapturedCivilTime(day * 86_400_000L, "UTC", 0, day), hour = 18, minute = 0,
        completedActivityId = recordId, createdAtMs = 1L, updatedAtMs = 1L,
    )

    private fun record(id: String, day: Long, title: String) = SessionSummary(
        id = id, routineId = "routine", routineName = title, date = day * 86_400_000L,
        finishedAt = day * 86_400_000L + 3_600_000L, durationMinutes = 60,
        workingSets = 12, volumeKg = 2040.0, localEpochDay = day,
    )

    companion object { const val TODAY = 20_002L }
}
