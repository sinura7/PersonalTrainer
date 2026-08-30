package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batched coach reads and aggregate PR detection must match the old
 * per-lift round-trips: top set, not last set; in-session work-up counts.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryInsightsQueriesTest {

    private lateinit var database: TrainerDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(database, database.workoutDao())
        runBlocking {
            database.exerciseDao().insertAll(listOf(exerciseRow(SQUAT), exerciseRow(BENCH)))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readyForProgressionUsesTheTopSetNotTheBackoff() = runBlocking {
        insertFinishedSession(
            id = "s1",
            finishedAt = START + 1,
            sets = listOf(
                Triple(SQUAT, 100.0, 5),
                Triple(SQUAT, 80.0, 8),
            ),
        )

        val hints = repository.readyForProgression(listOf(routine()), WeightUnit.KG)
        assertEquals(1, hints.size)
        assertEquals(SQUAT, hints.single().exerciseId)
        assertEquals(100.0, hints.single().lastWeightKg, 0.001)
        assertEquals(102.5, hints.single().suggestedWeightKg, 0.001)
        assertEquals(ProgressionAction.INCREASE, hints.single().action)
    }

    @Test
    fun readyForProgressionHoldsWhenTheLastTwoTopSetsAreGrinders() = runBlocking {
        insertFinishedSession(
            id = "s1",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
            rpe = 9,
        )
        insertFinishedSession(
            id = "s2",
            finishedAt = START + 2,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
            rpe = 9,
        )

        val hints = repository.readyForProgression(listOf(routine()), WeightUnit.KG)
        assertTrue(hints.none { it.exerciseId == SQUAT })
    }

    @Test
    fun aHeavierSetInTheLiveSessionBeatsTheWorkUp() = runBlocking {
        insertLiveSession("live")
        repository.logSet("live", SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        val second = repository.logSet("live", SQUAT, 105.0, 5, rpe = null, isWarmup = false)
        assertTrue(PersonalRecordKind.WEIGHT in second.records)
    }

    @Test
    fun theFirstSetOfALiftBreaksNothing() = runBlocking {
        insertLiveSession("live")
        val first = repository.logSet("live", SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        assertTrue(first.records.isEmpty())
    }

    @Test
    fun loggingAnInProgressSetDoesNotRescanFinishedSummaries() = runBlocking {
        insertFinishedSession(
            id = "done",
            finishedAt = START + 1,
            sets = listOf(Triple(SQUAT, 100.0, 5)),
        )
        val emissions = mutableListOf<Int>()
        val job = launch {
            repository.observeSessionSummaries().collect { emissions.add(it.size) }
        }
        awaitUntil { emissions.isNotEmpty() && emissions.last() == 1 }
        val before = emissions.size

        insertLiveSession("live")
        repository.logSet("live", SQUAT, 102.5, 5, rpe = null, isWarmup = false)
        delay(50)
        assertEquals("in-progress logs must not re-emit finished summaries", before, emissions.size)
        assertEquals(1, emissions.last())
        job.cancel()
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }
    }

    private fun routine() = Routine(
        id = "r",
        name = "Lower",
        notes = "",
        createdAt = START,
        updatedAt = START,
        exercises = listOf(
            item(SQUAT, "Squat"),
            item(BENCH, "Bench"),
        ),
    )

    private fun item(exerciseId: String, name: String) = RoutineExercise(
        id = "item-$exerciseId",
        routineId = "r",
        exercise = Exercise(
            id = exerciseId,
            name = name,
            muscleGroup = "Legs",
            notes = "",
            isCustom = false,
            loadType = LoadType.EXTERNAL,
        ),
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 90,
    )

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private suspend fun insertLiveSession(id: String) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Live",
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
    }

    private suspend fun insertFinishedSession(
        id: String,
        finishedAt: Long,
        sets: List<Triple<String, Double, Int>>,
        rpe: Int? = null,
    ) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Lower",
                date = START,
                notes = "",
                durationMinutes = 45,
                startedAt = START,
                finishedAt = finishedAt,
            ),
        )
        sets.forEachIndexed { index, (exerciseId, weightKg, reps) ->
            database.workoutDao().insertSet(
                SetLogEntity(
                    id = "$id-$exerciseId-$index",
                    sessionId = id,
                    exerciseId = exerciseId,
                    setNumber = index + 1,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = false,
                    completedAt = finishedAt + index,
                ),
            )
        }
    }

    private companion object {
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val START = 1_700_000_000_000L
    }
}
