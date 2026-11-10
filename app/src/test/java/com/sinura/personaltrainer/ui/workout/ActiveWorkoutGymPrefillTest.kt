package com.sinura.personaltrainer.ui.workout

import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.TestGymIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActiveWorkoutGymPrefillTest {
    private lateinit var deps: FakeAppDependencies
    private val pecId = "pec-deck"

    @Before
    fun setUp() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = pecId,
                    name = "Pec Deck",
                    muscleGroup = "Chest",
                    notes = "",
                    isCustom = false,
                    equipment = EquipmentType.MACHINE.name,
                    loadType = LoadType.EXTERNAL.name,
                    nameKey = "pec",
                    updatedAtMs = 0L,
                ),
            ),
        )
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "hist-major",
                routineId = null,
                routineName = "Push",
                date = 1_700_000_000_000L,
                notes = "",
                durationMinutes = 30,
                startedAt = 1_700_000_000_000L,
                finishedAt = 1_700_000_060_000L,
                gymId = "gym-af-major-mackenzie",
            ),
        )
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "set-major",
                sessionId = "hist-major",
                exerciseId = pecId,
                setNumber = 1,
                weightKg = 45.0,
                reps = 10,
                rpe = null,
                isWarmup = false,
                completedAt = 1_700_000_030_000L,
            ),
        )
    }

    @Test
    fun machineHasNoLastPerformanceAtNewGymButOtherGymHint() = runBlocking {
        val outcome = deps.workoutRepository.startFreeWorkoutSafely(gymId = TestGymIds.WESTON)
        val sessionId = (outcome as StartSessionOutcome.Started).session.id
        assertNull(
            deps.workoutRepository.lastPerformance(
                pecId,
                EquipmentType.MACHINE,
                TestGymIds.WESTON,
                sessionId,
            ),
        )
        val hint = deps.workoutRepository.otherGymHint(pecId, TestGymIds.WESTON)
        checkNotNull(hint)
        assertEquals(45.0, hint.weightKg, 0.001)
    }
}
