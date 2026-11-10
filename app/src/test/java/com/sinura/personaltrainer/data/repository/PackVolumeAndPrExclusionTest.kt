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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PackVolumeAndPrExclusionTest {
    private lateinit var deps: FakeAppDependencies
    private val bench = "ex-bench-press"

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = bench,
                        name = "Bench",
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                        loadType = "LOADED",
                        equipment = "BARBELL",
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
    fun weeklyVolumeQueryExcludesRealPackSessions() = runBlocking {
        seedPackCatalog(AuxiliaryPacks.Stretch)
        val packRoutineId = AuxiliaryBlocks.ensureRoutine(
            pack = AuxiliaryPacks.Stretch,
            routines = deps.routineRepository,
            exercises = deps.exerciseRepository,
        )
        val pushRoutine = deps.routineRepository.create("Push")
        insertFinishedSession("pack", packRoutineId, AuxiliaryPacks.Stretch.title, bench, START + 5)
        insertFinishedSession("push", pushRoutine.id, "Push", bench, START + 6)

        val rows = deps.workoutRepository.workingSetCountsSince(START)
        assertEquals(1, rows.size)
        assertEquals(bench, rows.single().exerciseId)
        assertEquals(pushRoutine.id, rows.single().routineId)
        assertFalse(rows.any { it.routineId == packRoutineId })
    }

    @Test
    fun packSetsDoNotBreakRecords() = runBlocking {
        seedPackCatalog(AuxiliaryPacks.Stretch)
        val packRoutineId = AuxiliaryBlocks.ensureRoutine(
            pack = AuxiliaryPacks.Stretch,
            routines = deps.routineRepository,
            exercises = deps.exerciseRepository,
        )
        insertFinishedSession("baseline", null, "Old", bench, START, weight = 80.0)
        val packSessionId = insertLiveSession("pack-live", packRoutineId, AuxiliaryPacks.Stretch.title, bench)
        val broken = deps.workoutRepository.recordsBrokenBy(
            exerciseId = bench,
            sessionId = packSessionId,
            weightKg = 120.0,
            reps = 5,
            completedAt = START + 2,
            setNumber = 1,
        )
        assertTrue(
            "Pack sessions must not claim PRs against real working history",
            broken.isEmpty(),
        )
    }

    private suspend fun seedPackCatalog(pack: com.sinura.personaltrainer.domain.AuxiliaryPack) {
        val existing = deps.database.exerciseDao().getAll().map { it.id }.toSet()
        pack.lifts.map { it.exerciseId }.filterNot { it in existing }.forEach { id ->
            deps.database.exerciseDao().insertAll(
                listOf(
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = id,
                        name = id,
                        muscleGroup = "Core",
                        notes = "",
                        isCustom = false,
                    ),
                ),
            )
        }
    }

    private suspend fun insertFinishedSession(
        id: String,
        routineId: String?,
        routineName: String,
        exerciseId: String,
        finishedAt: Long,
        weight: Double = 60.0,
    ) {
        deps.database.workoutDao().upsertSession(
            com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity(
                id = id,
                routineId = routineId,
                routineName = routineName,
                date = finishedAt,
                notes = "",
                durationMinutes = 20,
                startedAt = finishedAt,
                finishedAt = finishedAt,
            ),
        )
        deps.database.workoutDao().insertSet(
            com.sinura.personaltrainer.data.local.entity.SetLogEntity(
                id = "$id-set",
                sessionId = id,
                exerciseId = exerciseId,
                setNumber = 1,
                weightKg = weight,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = finishedAt,
            ),
        )
    }

    private suspend fun insertLiveSession(
        id: String,
        routineId: String?,
        routineName: String,
        exerciseId: String,
    ): String {
        deps.database.workoutDao().upsertSession(
            com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity(
                id = id,
                routineId = routineId,
                routineName = routineName,
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
        deps.database.workoutDao().insertSet(
            com.sinura.personaltrainer.data.local.entity.SetLogEntity(
                id = "$id-set",
                sessionId = id,
                exerciseId = exerciseId,
                setNumber = 1,
                weightKg = 120.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = START + 1,
            ),
        )
        return id
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
