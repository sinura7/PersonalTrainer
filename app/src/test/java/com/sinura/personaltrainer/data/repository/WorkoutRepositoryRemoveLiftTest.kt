package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Packet 5: remove-lift undo restores the same session-exercise id and slot.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryRemoveLiftTest {

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
    fun removeThenRestoreKeepsIdAndSortOrder() = runBlocking {
        insertLiveSession()
        insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = SE_BENCH, exerciseId = BENCH, sortOrder = 1)

        val removed = repository.removeExerciseFromSession(
            sessionId = SESSION,
            itemId = SE_BENCH,
        )
        assertEquals(SE_BENCH, removed.item.id)
        assertEquals(1, removed.item.sortOrder)
        assertEquals("Bench", removed.name)
        assertEquals(
            listOf(SE_SQUAT),
            sessionExerciseIds(),
        )

        repository.restoreExerciseToSession(removed = removed)
        repository.restoreExerciseToSession(removed = removed)
        val restored = database.workoutDao().getSession(SESSION)!!
        val bench = restored.exercises.single { it.item.id == SE_BENCH }
        assertEquals(1, bench.item.sortOrder)
        assertEquals(BENCH, bench.item.exerciseId)
        assertEquals(2, restored.exercises.size)
    }

    @Test
    fun restoreNoOpsWhenTheSessionHasFinished() = runBlocking {
        insertLiveSession()
        insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = SE_BENCH, exerciseId = BENCH, sortOrder = 1)
        val removed = repository.removeExerciseFromSession(
            sessionId = SESSION,
            itemId = SE_BENCH,
        )
        database.workoutDao().finishSession(
            id = SESSION,
            notes = "",
            durationMinutes = 45,
            finishedAt = FINISH,
        )

        repository.restoreExerciseToSession(removed = removed)

        val after = database.workoutDao().getSession(SESSION)!!
        assertTrue(after.exercises.none { it.item.id == SE_BENCH })
        assertEquals(1, after.exercises.size)
        assertEquals(SE_SQUAT, after.exercises.single().item.id)
    }

    private suspend fun sessionExerciseIds(): List<String> =
        database.workoutDao().getSession(SESSION)!!.exercises.map { it.item.id }

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private suspend fun insertLiveSession() {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = SESSION,
                routineId = null,
                routineName = "Live lower",
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
    }

    private suspend fun insertSessionExercise(
        id: String,
        exerciseId: String,
        sortOrder: Int,
    ) {
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = id,
                sessionId = SESSION,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 80.0,
                restSeconds = 90,
            ),
        )
    }

    private companion object {
        const val SESSION = "session-remove-lift"
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val SE_SQUAT = "se-squat"
        const val SE_BENCH = "se-bench"
        const val START = 1_700_000_000_000L
        const val FINISH = START + 3_600_000L
    }
}
