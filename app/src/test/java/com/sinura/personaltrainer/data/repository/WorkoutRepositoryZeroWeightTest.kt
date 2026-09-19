package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.SetLogRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryZeroWeightTest {

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
            database.exerciseDao().insertAll(
                listOf(
                    exerciseRow(
                        id = LUNGE,
                        name = "Walking Lunge",
                        equipment = "DUMBBELL",
                        loadType = "BODYWEIGHT_PLUS",
                        movementKey = "lunge",
                    ),
                    exerciseRow(
                        id = BENCH,
                        name = "Barbell Bench Press",
                        equipment = "BARBELL",
                        loadType = "EXTERNAL",
                        movementKey = "bench-press",
                    ),
                    exerciseRow(
                        id = OLD_LUNGE,
                        name = "Walking Lunge (unsynced)",
                        equipment = "DUMBBELL",
                        loadType = "EXTERNAL",
                        movementKey = "lunge",
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun walkingLungeLogsZeroAndBenchStillRefuses() = runBlocking {
        insertLiveSession()
        insertSessionExercise(SE_LUNGE, LUNGE, 0)
        insertSessionExercise(SE_BENCH, BENCH, 1)
        insertSessionExercise(SE_OLD, OLD_LUNGE, 2)

        val logged = repository.logSet(SESSION, LUNGE, 0.0, 13, rpe = null, isWarmup = false)
        val stored = database.workoutDao().setsForExercise(SESSION, LUNGE).single()
        assertEquals(logged.setId, stored.id)
        assertEquals(0.0, stored.weightKg, 0.0001)
        assertEquals(13, stored.reps)

        val unsynced = repository.logSet(SESSION, OLD_LUNGE, 0.0, 12, rpe = null, isWarmup = false)
        val unsyncedRow = database.workoutDao().setsForExercise(SESSION, OLD_LUNGE).single()
        assertEquals(unsynced.setId, unsyncedRow.id)
        assertEquals(0.0, unsyncedRow.weightKg, 0.0001)

        try {
            repository.logSet(SESSION, BENCH, 0.0, 5, rpe = null, isWarmup = false)
            fail("a barbell bench at 0 kg must still be refused")
        } catch (thrown: IllegalStateException) {
            assertEquals(SetLogRules.ZERO_WORKING_WEIGHT, thrown.message)
        }
        assertEquals(0, database.workoutDao().setsForExercise(SESSION, BENCH).size)
    }

    private fun exerciseRow(
        id: String,
        name: String,
        equipment: String,
        loadType: String,
        movementKey: String,
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
        equipment = equipment,
        loadType = loadType,
        movementKey = movementKey,
        nameKey = name.lowercase(),
    )

    private suspend fun insertLiveSession() {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = SESSION,
                routineId = null,
                routineName = "Lower A",
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
    }

    private suspend fun insertSessionExercise(id: String, exerciseId: String, sortOrder: Int) {
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = id,
                sessionId = SESSION,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                targetSets = 3,
                targetReps = 13,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        )
    }

    private companion object {
        const val SESSION = "session-zero-weight"
        const val LUNGE = "ex-walking-lunge"
        const val OLD_LUNGE = "ex-walking-lunge-old"
        const val BENCH = "ex-barbell-bench-press"
        const val SE_LUNGE = "se-lunge"
        const val SE_BENCH = "se-bench"
        const val SE_OLD = "se-old-lunge"
        const val START = 1_700_000_000_000L
    }
}
