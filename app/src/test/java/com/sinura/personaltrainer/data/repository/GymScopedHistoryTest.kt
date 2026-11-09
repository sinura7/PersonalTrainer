package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.WeightUnit
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

@RunWith(RobolectricTestRunner::class)
class GymScopedHistoryTest {
    private lateinit var database: TemperDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = TemperDatabase.inMemoryBuilder(context as Context)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(database, database.workoutDao())
        runBlocking {
            database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = PEC_ID,
                        name = "Pec Deck",
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                        equipment = EquipmentType.MACHINE.name,
                        loadType = LoadType.EXTERNAL.name,
                        nameKey = "pec deck",
                        updatedAtMs = 0L,
                    ),
                    ExerciseEntity(
                        id = BENCH_ID,
                        name = "Bench Press",
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                        equipment = EquipmentType.BARBELL.name,
                        loadType = LoadType.EXTERNAL.name,
                        nameKey = "bench press",
                        updatedAtMs = 0L,
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
    fun machineLastPerformanceIsPerGymWithOtherGymHint() = runBlocking {
        finish("weston", WESTON, PEC_ID, 55.0, STAMP + 2)
        finish("major", MAJOR, PEC_ID, 45.0, STAMP + 1)

        val atWeston = repository.lastPerformance(PEC_ID, EquipmentType.MACHINE, WESTON)
        assertEquals(55.0, atWeston!!.topSet!!.weightKg, 0.001)

        val atMajor = repository.lastPerformance(PEC_ID, EquipmentType.MACHINE, MAJOR)
        assertEquals(45.0, atMajor!!.topSet!!.weightKg, 0.001)

        val atCotrelle = repository.lastPerformance(PEC_ID, EquipmentType.MACHINE, COTRELLE)
        assertNull(atCotrelle)
        val hint = repository.otherGymHint(PEC_ID, COTRELLE)
        assertNotNull(hint)
        assertEquals(55.0, hint!!.weightKg, 0.001)
        assertEquals("Weston", hint.gymShortName)
    }

    @Test
    fun freeWeightMatchesAtEveryGym() = runBlocking {
        finish("weston-bench", WESTON, BENCH_ID, 80.0, STAMP + 1)

        assertEquals(
            80.0,
            repository.lastPerformance(BENCH_ID, EquipmentType.BARBELL, WESTON)!!.topSet!!.weightKg,
            0.001,
        )
        assertEquals(
            80.0,
            repository.lastPerformance(BENCH_ID, EquipmentType.BARBELL, COTRELLE)!!.topSet!!.weightKg,
            0.001,
        )
    }

    @Test
    fun unassignedMachineHistoryDoesNotFeedGymScope() = runBlocking {
        finish("unassigned", null, PEC_ID, 60.0, STAMP)
        assertNull(repository.lastPerformance(PEC_ID, EquipmentType.MACHINE, WESTON))
    }

    @Test
    fun readyForProgressionUsesUnassignedMachineHistory() = runBlocking {
        finish("unassigned", null, PEC_ID, 45.0, STAMP + 1)
        val hints = repository.readyForProgression(listOf(pecDeckRoutine()), WeightUnit.KG)
        assertEquals(PEC_ID, hints.single().exerciseId)
        assertEquals(ProgressionAction.INCREASE, hints.single().action)
    }

    @Test
    fun unassignedMachinePrefillUsesUnassignedHistoryOnly() = runBlocking {
        finish("unassigned", null, PEC_ID, 60.0, STAMP + 1)
        finish("weston", WESTON, PEC_ID, 80.0, STAMP + 2)
        val atUnassigned = repository.progressionFor(
            exerciseId = PEC_ID,
            exerciseName = "Pec Deck",
            targetReps = 10,
            excludeSessionId = "live",
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            equipment = EquipmentType.MACHINE,
            gymId = null,
        )
        assertNotNull(atUnassigned)
        assertEquals(60.0, atUnassigned!!.lastWeightKg, 0.001)
        val atWeston = repository.progressionFor(
            exerciseId = PEC_ID,
            exerciseName = "Pec Deck",
            targetReps = 10,
            excludeSessionId = "live",
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            equipment = EquipmentType.MACHINE,
            gymId = WESTON,
        )
        assertEquals(80.0, atWeston!!.lastWeightKg, 0.001)
    }

    @Test
    fun recordPriorsCompareUnassignedVersusNamedGym() = runBlocking {
        finish("unassigned", null, PEC_ID, 60.0, STAMP)
        finish("weston", WESTON, PEC_ID, 50.0, STAMP + 1)
        liveSession("live-weston", WESTON)
        val westonRecords = repository.recordsBrokenBy(
            exerciseId = PEC_ID,
            sessionId = "live-weston",
            weightKg = 55.0,
            reps = 10,
            completedAt = STAMP + 100,
            setNumber = 1,
        )
        assertTrue(PersonalRecordKind.WEIGHT in westonRecords)
        liveSession("live-un", null)
        assertTrue(
            PersonalRecordKind.WEIGHT in repository.recordsBrokenBy(
                exerciseId = PEC_ID,
                sessionId = "live-un",
                weightKg = 65.0,
                reps = 10,
                completedAt = STAMP + 200,
                setNumber = 1,
            ),
        )
        val notUnassignedPr = repository.recordsBrokenBy(
            exerciseId = PEC_ID,
            sessionId = "live-un",
            weightKg = 55.0,
            reps = 10,
            completedAt = STAMP + 201,
            setNumber = 1,
        )
        assertTrue(PersonalRecordKind.WEIGHT !in notUnassignedPr)
    }

    private fun pecDeckRoutine() = Routine(
        id = "r-pec",
        name = "Push",
        notes = "",
        createdAt = STAMP,
        updatedAt = STAMP,
        exercises = listOf(
            RoutineExercise(
                id = "re-pec",
                routineId = "r-pec",
                exercise = Exercise(
                    id = PEC_ID,
                    name = "Pec Deck",
                    muscleGroup = "Chest",
                    notes = "",
                    isCustom = false,
                    equipment = EquipmentType.MACHINE,
                ),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        ),
    )

    private suspend fun liveSession(id: String, gymId: String?) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Live",
                date = STAMP,
                notes = "",
                durationMinutes = 0,
                startedAt = STAMP,
                finishedAt = null,
                gymId = gymId,
            ),
        )
    }

    private suspend fun finish(
        sessionId: String,
        gymId: String?,
        exerciseId: String,
        weightKg: Double,
        finishedAt: Long,
    ) {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = sessionId,
                routineId = null,
                routineName = "Upper",
                date = STAMP,
                notes = "",
                durationMinutes = 30,
                startedAt = STAMP,
                finishedAt = finishedAt,
                gymId = gymId,
            ),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "sl-$sessionId",
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                weightKg = weightKg,
                reps = 10,
                rpe = null,
                isWarmup = false,
                completedAt = finishedAt,
            ),
        )
    }

    private companion object {
        const val STAMP = 1_700_000_000_000L
        const val PEC_ID = "pec-deck"
        const val BENCH_ID = "bench"
        const val WESTON = "gym-af-weston"
        const val MAJOR = "gym-af-major-mackenzie"
        const val COTRELLE = "gym-af-contrelle"
    }
}
