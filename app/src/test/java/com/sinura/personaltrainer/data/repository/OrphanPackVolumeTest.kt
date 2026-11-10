package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Orphan name fallback when the routine row is gone; id-only must not drop user Core. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OrphanPackVolumeTest {
    private lateinit var deps: FakeAppDependencies
    private val squat = "ex-squat"

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = squat,
                        name = "Squat",
                        muscleGroup = "Legs",
                        notes = "",
                        isCustom = false,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun deletedPackRoutineStillExcludedBySessionName() = runBlocking {
        insertSession(
            id = "orphan-pack",
            routineId = null,
            routineName = AuxiliaryPacks.Stretch.title,
        )
        assertTrue(
            deps.workoutRepository.excludeAuxiliaryVolume(null, AuxiliaryPacks.Stretch.title),
        )
        assertEquals(0, deps.workoutRepository.workingSetCountsSince(START).size)
    }

    @Test
    fun userCoreRoutineStillCountsWhenRoutineExists() = runBlocking {
        val routine = deps.routineRepository.create("Core")
        insertSession(id = "user-core", routineId = routine.id, routineName = "Core")
        assertFalse(deps.workoutRepository.excludeAuxiliaryVolume(routine.id, "Core"))
        assertEquals(1, deps.workoutRepository.workingSetCountsSince(START).size)
    }

    private suspend fun insertSession(id: String, routineId: String?, routineName: String) {
        deps.database.workoutDao().upsertSession(
            com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity(
                id = id,
                routineId = routineId,
                routineName = routineName,
                date = START,
                notes = "",
                durationMinutes = 20,
                startedAt = START,
                finishedAt = START + 1,
            ),
        )
        deps.database.workoutDao().insertSet(
            com.sinura.personaltrainer.data.local.entity.SetLogEntity(
                id = "$id-set",
                sessionId = id,
                exerciseId = squat,
                setNumber = 1,
                weightKg = 60.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = START + 1,
            ),
        )
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
