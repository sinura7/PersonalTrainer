package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.RepeatOutcome
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository half of Phase 1b: every write that repairs a finished session, proved
 * against a real Room database rather than by inspection.
 *
 * These are the assertions that cannot live in the pure-domain lane — they are about what
 * SQLite actually holds after the call: the cascade a session delete relies on, the exact
 * `completedAt` an added set is stamped with, the id an undo restores, and the transaction
 * that stops a Repeat from silently resuming somebody else's workout.
 *
 * Runs only under `./gradlew testDebugUnitTest` (Studio, CI). It lives outside `domain/` on
 * purpose: `tools/run-domain-tests.sh` compiles that tree with no Android classpath, so a
 * Robolectric import there breaks the jar lane.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepairRepositoryTest {

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
                listOf(exerciseRow(SQUAT), exerciseRow(BENCH)),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -----------------------------------------------------------------------
    // Added sets
    // -----------------------------------------------------------------------

    @Test
    fun addSetToFinishedSessionTimestampsInsideTheSessionWindow() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 600_000)

        repository.addSetToFinishedSession(
            sessionId = SESSION,
            exerciseId = SQUAT,
            weightKg = 100.0,
            reps = 5,
            rpe = 8,
            isWarmup = false,
        )

        val sets = database.workoutDao().setsForExercise(SESSION, SQUAT)
        assertEquals(2, sets.size)
        val added = sets.first { it.id != "s1" }
        assertTrue("added set must follow the previous one", added.completedAt > START + 600_000)
        assertTrue("added set must land inside the session", added.completedAt >= START)
        assertTrue("added set must land inside the session", added.completedAt <= FINISH)
        assertEquals(2, added.setNumber)
    }

    // -----------------------------------------------------------------------
    // Session delete
    // -----------------------------------------------------------------------

    @Test
    fun deleteFinishedSessionCascadesExercisesAndSetLogs() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSessionExercise(id = "x1", sessionId = SESSION, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = "x2", sessionId = SESSION, exerciseId = BENCH, sortOrder = 1)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 1)
        insertSet(id = "s2", sessionId = SESSION, exerciseId = SQUAT, setNumber = 2, completedAt = START + 2)
        insertSet(id = "s3", sessionId = SESSION, exerciseId = BENCH, setNumber = 1, completedAt = START + 3)

        repository.deleteFinishedSession(SESSION)

        assertNull(database.workoutDao().getSessionRow(SESSION))
        assertEquals(0, database.workoutDao().setsForExercise(SESSION, SQUAT).size)
        assertEquals(0, database.workoutDao().setsForExercise(SESSION, BENCH).size)
        assertEquals(-1, database.workoutDao().maxSessionExerciseOrder(SESSION))
    }

    @Test
    fun deleteFinishedSessionRefusesAnInProgressSession() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = null)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 1)

        val thrown = runCatching { repository.deleteFinishedSession(SESSION) }.exceptionOrNull()

        assertNotNull("discard, not delete, owns an in-progress session", thrown)
        assertNotNull(database.workoutDao().getSessionRow(SESSION))
        assertEquals(1, database.workoutDao().setsForExercise(SESSION, SQUAT).size)
    }

    // -----------------------------------------------------------------------
    // Repeat
    // -----------------------------------------------------------------------

    @Test
    fun repeatSessionStartsWhenIdle() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSessionExercise(id = "x1", sessionId = SESSION, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = "x2", sessionId = SESSION, exerciseId = BENCH, sortOrder = 1)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 1)
        insertSet(id = "s2", sessionId = SESSION, exerciseId = SQUAT, setNumber = 2, completedAt = START + 2)

        val outcome = repository.repeatSession(SESSION)

        assertTrue(outcome.toString(), outcome is RepeatOutcome.Started)
        val newId = (outcome as RepeatOutcome.Started).sessionId
        val row = database.workoutDao().getSessionRow(newId)
        assertNotNull(row)
        assertNull("a repeat starts a live session, never a finished one", row!!.finishedAt)
        assertEquals(0, row.durationMinutes)
        val details = database.workoutDao().getSession(newId)!!
        assertEquals("a repeat copies the plan, never the log", 0, details.sets.size)
        assertEquals(
            listOf(SQUAT, BENCH),
            details.exercises.sortedBy { it.item.sortOrder }.map { it.item.exerciseId },
        )
    }

    @Test
    fun repeatSessionBlocksWhenASessionIsInProgress() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSessionExercise(id = "x1", sessionId = SESSION, exerciseId = SQUAT, sortOrder = 0)
        insertSession(LIVE_SESSION, startedAt = FINISH + 1, finishedAt = null)

        val outcome = repository.repeatSession(SESSION)

        assertTrue(outcome.toString(), outcome is RepeatOutcome.Blocked)
        assertEquals(LIVE_SESSION, (outcome as RepeatOutcome.Blocked).inProgressSessionId)
        // No third row was written and then abandoned: the only live session is still the
        // one that was already running.
        assertEquals(LIVE_SESSION, database.workoutDao().getInProgressSession()?.id)
    }

    // -----------------------------------------------------------------------
    // Delete and undo
    // -----------------------------------------------------------------------

    @Test
    fun deleteThenRestoreSetPreservesIdCompletedAtAndOrdering() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 100)
        insertSet(id = "s2", sessionId = SESSION, exerciseId = SQUAT, setNumber = 2, completedAt = START + 200)
        insertSet(id = "s3", sessionId = SESSION, exerciseId = SQUAT, setNumber = 3, completedAt = START + 300)

        val removed = repository.deleteSet("s2")

        assertNotNull(removed)
        assertEquals("s2", removed!!.setId)
        assertEquals(START + 200, removed.completedAt)
        assertEquals(2, removed.setNumber)
        assertEquals(
            listOf("s1" to 1, "s3" to 2),
            database.workoutDao().setsForExercise(SESSION, SQUAT).map { it.id to it.setNumber },
        )

        repository.restoreSet(removed)

        val after = database.workoutDao().setsForExercise(SESSION, SQUAT).sortedBy { it.completedAt }
        assertEquals(listOf("s1", "s2", "s3"), after.map { it.id })
        assertEquals(listOf(1, 2, 3), after.map { it.setNumber })
        assertEquals(START + 200, after[1].completedAt)
    }

    @Test
    fun restoreSetNoOpsWhenTheSessionIsGone() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH)
        insertSet(id = "s1", sessionId = SESSION, exerciseId = SQUAT, setNumber = 1, completedAt = START + 100)
        val removed = repository.deleteSet("s1")
        repository.deleteFinishedSession(SESSION)

        repository.restoreSet(removed!!)

        assertEquals(0, database.workoutDao().setsForExercise(SESSION, SQUAT).size)
    }

    // -----------------------------------------------------------------------
    // Notes
    // -----------------------------------------------------------------------

    @Test
    fun updateSessionNotesWritesOnFinishedSession() = runBlocking {
        insertSession(SESSION, startedAt = START, finishedAt = FINISH, notes = "old")
        val before = database.workoutDao().getSessionRow(SESSION)!!

        repository.updateSessionNotes(SESSION, "  new  ")

        val after = database.workoutDao().getSessionRow(SESSION)!!
        assertEquals("new", after.notes)
        assertEquals(before.finishedAt, after.finishedAt)
        assertEquals(before.durationMinutes, after.durationMinutes)
        assertEquals(before.date, after.date)
        assertEquals(before.startedAt, after.startedAt)
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private suspend fun insertSession(
        id: String,
        startedAt: Long,
        finishedAt: Long?,
        notes: String = "",
    ) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Session $id",
                date = startedAt,
                notes = notes,
                durationMinutes = if (finishedAt == null) 0 else 45,
                startedAt = startedAt,
                finishedAt = finishedAt,
            ),
        )
    }

    private suspend fun insertSessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String,
        sortOrder: Int,
    ) {
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        )
    }

    private suspend fun insertSet(
        id: String,
        sessionId: String,
        exerciseId: String,
        setNumber: Int,
        completedAt: Long,
        isWarmup: Boolean = false,
    ) {
        database.workoutDao().insertSet(
            SetLogEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = isWarmup,
                completedAt = completedAt,
            ),
        )
    }

    private companion object {
        const val SESSION = "session-1"
        const val LIVE_SESSION = "session-live"
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val START = 1_700_000_000_000L
        const val FINISH = START + 3_600_000L
    }
}
