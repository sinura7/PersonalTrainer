package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Set numbers are assigned by count-then-insert (log) and delete-then-renumber.
 * Those two steps used to race (audit N5): overlapping calls could write the same
 * `setNumber`, and a committed delete with a half-applied renumber left a gap the
 * next log would collide with. JVM proves the numbering contract after the writes
 * sit in one Room transaction. True process-kill atomicity is an owner emulator gate.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositorySetNumberingTest {

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
    fun sequentialLogSetNumbersOneTwoThree() = runBlocking {
        insertLiveSession()

        logWorking(weightKg = 100.0)
        logWorking(weightKg = 102.5)
        logWorking(weightKg = 105.0)

        assertEquals(listOf(1, 2, 3), squatNumbers())
    }

    @Test
    fun setNumbersAreIndependentPerExercise() = runBlocking {
        insertLiveSession()

        logWorking(exerciseId = SQUAT)
        logWorking(exerciseId = BENCH)
        logWorking(exerciseId = SQUAT)
        logWorking(exerciseId = BENCH)

        assertEquals(listOf(1, 2), squatNumbers())
        assertEquals(listOf(1, 2), numbersOf(BENCH))
    }

    @Test
    fun warmupAndWorkingShareOneNumberSequence() = runBlocking {
        insertLiveSession()

        repository.logSet(SESSION, SQUAT, 60.0, 5, rpe = null, isWarmup = true)
        logWorking(weightKg = 100.0)
        repository.logSet(SESSION, SQUAT, 70.0, 3, rpe = null, isWarmup = true)

        assertEquals(listOf(1, 2, 3), squatNumbers())
    }

    @Test
    fun deleteMiddleSetRenumbersThenNextLogContinues() = runBlocking {
        insertLiveSession()
        val first = logWorking(weightKg = 100.0)
        val second = logWorking(weightKg = 102.5)
        logWorking(weightKg = 105.0)

        val removed = repository.deleteSet(second.setId)

        assertNotNull(removed)
        assertEquals(2, removed!!.setNumber)
        assertEquals(listOf(1, 2), squatNumbers())
        assertEquals(first.setId, squatIds().first())
        assertTrue(second.setId !in squatIds())

        logWorking(weightKg = 107.5)
        assertEquals(listOf(1, 2, 3), squatNumbers())
    }

    @Test
    fun restoreAfterDeleteRestoresContiguousNumbers() = runBlocking {
        insertLiveSession()
        logWorking(weightKg = 100.0)
        val middle = logWorking(weightKg = 102.5)
        logWorking(weightKg = 105.0)

        val removed = checkNotNull(repository.deleteSet(middle.setId))
        repository.restoreSet(removed)

        assertEquals(listOf(1, 2, 3), squatNumbers())
        assertEquals(3, squatRows().size)
    }

    @Test
    fun addSetToFinishedSessionContinuesTheSequence() = runBlocking {
        insertSession(finishedAt = FINISH)
        insertSet(id = "s1", setNumber = 1, completedAt = START + 1)
        insertSet(id = "s2", setNumber = 2, completedAt = START + 2)

        repository.addSetToFinishedSession(SESSION, SQUAT, 100.0, 5, rpe = 8, isWarmup = false)

        assertEquals(listOf(1, 2, 3), squatNumbers())
    }

    @Test
    fun overlappingLogSetCallsDoNotShareANumber() = runBlocking {
        insertLiveSession()

        val logged = (1..8).map { index ->
            async { logWorking(weightKg = 80.0 + index) }
        }.awaitAll()

        assertEquals(8, logged.size)
        assertEquals(8, logged.map { it.setId }.toSet().size)
        assertEquals((1..8).toList(), squatNumbers())
    }

    @Test
    fun deleteThenLogThenRestoreKeepsNumbersContiguous() = runBlocking {
        insertLiveSession()
        logWorking(weightKg = 100.0)
        val middle = logWorking(weightKg = 102.5)
        logWorking(weightKg = 105.0)

        val removed = checkNotNull(repository.deleteSet(middle.setId))
        logWorking(weightKg = 107.5)
        repository.restoreSet(removed)

        val numbers = squatNumbers()
        assertEquals(4, numbers.size)
        assertEquals(numbers.distinct(), numbers)
        assertTrue(numbers.first() == 1 && numbers.last() == numbers.size)
        assertEquals((1..4).toList(), numbers)
    }

    private suspend fun logWorking(
        exerciseId: String = SQUAT,
        weightKg: Double = 100.0,
        reps: Int = 5,
    ) = repository.logSet(
        sessionId = SESSION,
        exerciseId = exerciseId,
        weightKg = weightKg,
        reps = reps,
        rpe = null,
        isWarmup = false,
    )

    private suspend fun squatRows(): List<SetLogEntity> =
        database.workoutDao().setsForExercise(SESSION, SQUAT)

    private suspend fun squatNumbers(): List<Int> = squatRows().map { it.setNumber }

    private suspend fun squatIds(): List<String> = squatRows().map { it.id }

    private suspend fun numbersOf(exerciseId: String): List<Int> =
        database.workoutDao().setsForExercise(SESSION, exerciseId).map { it.setNumber }

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private suspend fun insertLiveSession() = insertSession(finishedAt = null)

    private suspend fun insertSession(finishedAt: Long?) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = SESSION,
                routineId = null,
                routineName = "Live lower",
                date = START,
                notes = "",
                durationMinutes = if (finishedAt == null) 0 else 45,
                startedAt = START,
                finishedAt = finishedAt,
            ),
        )
    }

    private suspend fun insertSet(id: String, setNumber: Int, completedAt: Long) {
        database.workoutDao().insertSet(
            SetLogEntity(
                id = id,
                sessionId = SESSION,
                exerciseId = SQUAT,
                setNumber = setNumber,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = completedAt,
            ),
        )
    }

    private companion object {
        const val SESSION = "session-numbering"
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val START = 1_700_000_000_000L
        const val FINISH = START + 3_600_000L
    }
}
