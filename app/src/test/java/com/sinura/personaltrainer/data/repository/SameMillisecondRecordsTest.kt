package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.testutil.FrozenTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two sets logged inside one millisecond still know which came first.
 *
 * `WorkoutDao.recordPriorsBefore` asked for everything with `completedAt < :completedAt`, and a
 * millisecond stamp is not a total order over sets: a heavier second set landing in the same
 * millisecond as the first saw no priors at all, so it was judged the opening set of the lift
 * and broke nothing. `WorkoutRepositoryInsightsQueriesTest` caught that intermittently — it
 * only failed when the two writes happened to share a millisecond, which is common under load
 * and never happened in isolation, so the suite went green on luck.
 *
 * Pinned here with a frozen clock, so the two sets share a stamp on every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SameMillisecondRecordsTest {
    private lateinit var database: TemperDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TemperDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Possible at all because WorkoutRepository now takes its clock rather than calling
        // System.currentTimeMillis() at fourteen sites of its own.
        repository = WorkoutRepository(
            database = database,
            workoutDao = database.workoutDao(),
            time = FrozenTime(STAMP),
        )
        runBlocking {
            database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = SQUAT,
                        name = "Back Squat",
                        muscleGroup = "Legs",
                        notes = "",
                        isCustom = false,
                    ),
                ),
            )
            database.workoutDao().upsertSession(
                WorkoutSessionEntity(
                    id = SESSION,
                    routineId = null,
                    routineName = "Live",
                    date = STAMP,
                    notes = "",
                    durationMinutes = 0,
                    startedAt = STAMP,
                    finishedAt = null,
                ),
            )
            database.workoutDao().upsertSessionExercise(
                SessionExerciseEntity(
                    id = "se-1",
                    sessionId = SESSION,
                    exerciseId = SQUAT,
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = null,
                    restSeconds = 120,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aHeavierSecondSetInTheSameMillisecondStillBreaksTheWeightRecord() = runBlocking {
        val first = repository.logSet(SESSION, SQUAT, 100.0, 5, rpe = null, isWarmup = false)
        val second = repository.logSet(SESSION, SQUAT, 105.0, 5, rpe = null, isWarmup = false)

        val sets = database.workoutDao().setsForExercise(SESSION, SQUAT)
        assertEquals("both sets share a stamp", 1, sets.map { it.completedAt }.distinct().size)
        assertEquals(listOf(1, 2), sets.map { it.setNumber }.sorted())

        // The opening set of a lift breaks nothing. The heavier one after it is a record — and
        // was not, while "before" meant `completedAt <` alone.
        assertTrue(first.records.toString(), first.records.isEmpty())
        assertTrue(second.records.toString(), PersonalRecordKind.WEIGHT in second.records)
    }

    @Test
    fun aLighterSecondSetInTheSameMillisecondBreaksNothing() = runBlocking {
        repository.logSet(SESSION, SQUAT, 105.0, 5, rpe = null, isWarmup = false)
        val lighter = repository.logSet(SESSION, SQUAT, 95.0, 5, rpe = null, isWarmup = false)
        assertTrue(lighter.records.toString(), PersonalRecordKind.WEIGHT !in lighter.records)
    }

    private companion object {
        const val SQUAT = "ex-squat"
        const val SESSION = "live-1"
        const val STAMP = 1_700_000_000_000L
    }
}
