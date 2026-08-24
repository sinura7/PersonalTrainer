package com.sinura.personaltrainer.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.domain.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WorkoutRepository against the phone's SQLite, not Robolectric's.
 *
 * The JVM lane already covers the Phase 1b repair writes. This file is the first device
 * proof of the live-session contract those writes sit on: one in-progress session, a log
 * that survives finish, a repeat that refuses to steal the live workout, and a finished
 * delete that actually cascades.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryInstrumentedTest {

    private lateinit var database: TrainerDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(database, database.workoutDao())
        runBlocking {
            database.exerciseDao().insertAll(listOf(exerciseRow(SQUAT)))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startRoutineCopiesThePlanAndIsTheOnlyLiveSession() = runBlocking {
        val routine = insertRoutineWithSquat()

        val session = repository.startRoutine(routine)
        val secondStart = repository.startRoutine(routine)

        assertEquals(session.id, secondStart.id)
        assertNull(session.finishedAt)
        assertEquals(1, session.exercises.size)
        assertEquals(SQUAT, session.exercises.single().exercise.id)
        assertEquals(3, session.exercises.single().targetSets)
        assertEquals(5, session.exercises.single().targetReps)
        assertEquals(session.id, repository.getInProgress()?.id)
    }

    @Test
    fun startRoutineSafelyReportsBlockedInsteadOfResuming() = runBlocking {
        val routine = insertRoutineWithSquat()

        val first = repository.startRoutineSafely(routine)
        assertTrue(first is StartSessionOutcome.Started)
        val started = (first as StartSessionOutcome.Started).session

        val second = repository.startRoutineSafely(routine)
        assertTrue(second is StartSessionOutcome.Blocked)
        assertEquals(started.id, (second as StartSessionOutcome.Blocked).inProgress.id)
        assertEquals(started.id, repository.getInProgress()?.id)
    }

    @Test
    fun loggedSetSurvivesFinishIntoHistory() = runBlocking {
        val routine = insertRoutineWithSquat()
        val live = repository.startRoutine(routine)

        val logged = repository.logSet(
            sessionId = live.id,
            exerciseId = SQUAT,
            weightKg = 140.0,
            reps = 5,
            rpe = 8,
            isWarmup = false,
        )
        repository.finishSession(live.id, notes = "felt strong")

        assertNull(repository.getInProgress())
        val history = repository.observeHistory().first()
        assertEquals(1, history.size)
        val finished = history.single()
        assertEquals(live.id, finished.id)
        assertNotNull(finished.finishedAt)
        assertEquals("felt strong", finished.notes)
        assertEquals(1, finished.sets.size)
        assertEquals(logged.setId, finished.sets.single().id)
        assertEquals(140.0, finished.sets.single().weightKg, 0.0001)
        assertEquals(5, finished.sets.single().reps)
    }

    @Test
    fun repeatSessionIsBlockedWhileAnotherWorkoutIsLive() = runBlocking {
        val routine = insertRoutineWithSquat()
        val first = repository.startRoutine(routine)
        repository.logSet(first.id, SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        repository.finishSession(first.id, notes = "")

        val live = repository.startFreeWorkout()
        val outcome = repository.repeatSession(first.id)

        assertTrue(outcome is RepeatOutcome.Blocked)
        val blocked = outcome as RepeatOutcome.Blocked
        assertEquals(live.id, blocked.inProgressSessionId)
        assertEquals(live.id, repository.getInProgress()?.id)
        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun deleteFinishedSessionCascadesExercisesAndSets() = runBlocking {
        val routine = insertRoutineWithSquat()
        val session = repository.startRoutine(routine)
        repository.logSet(session.id, SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        repository.finishSession(session.id, notes = "")

        repository.deleteFinishedSession(session.id)

        assertNull(database.workoutDao().getSessionRow(session.id))
        assertEquals(0, database.workoutDao().setsForExercise(session.id, SQUAT).size)
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun deleteFinishedSessionRefusesALiveSession() = runBlocking {
        val live = repository.startFreeWorkout()
        val thrown = runCatching { repository.deleteFinishedSession(live.id) }.exceptionOrNull()

        assertNotNull(thrown)
        assertNotNull(database.workoutDao().getSessionRow(live.id))
        assertEquals(live.id, repository.getInProgress()?.id)
    }

    private suspend fun insertRoutineWithSquat(): Routine {
        database.routineDao().upsertRoutine(
            RoutineEntity(
                id = ROUTINE,
                name = "Lower",
                notes = "",
                createdAt = STAMP,
                updatedAt = STAMP,
            ),
        )
        database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-squat",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 140.0,
                restSeconds = 120,
            ),
        )
        return RoutineRepository(database.routineDao()).getById(ROUTINE)
            ?: error("fixture routine missing")
    }

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private companion object {
        const val SQUAT = "squat"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
    }
}
