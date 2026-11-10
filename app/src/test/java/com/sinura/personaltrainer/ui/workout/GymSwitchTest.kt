package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.TestGymIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GymSwitchTest {
    @Test
    fun moveSessionUpdatesGymAndMachineSetCount() = runBlocking {
        val deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val pecId = "pec-deck"
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
        val started = deps.workoutRepository.startFreeWorkoutSafely(gymId = TestGymIds.WESTON)
        val sessionId = (started as com.sinura.personaltrainer.data.repository.StartSessionOutcome.Started).session.id
        deps.workoutRepository.addExerciseToSession(
            sessionId,
            Exercise(
                id = pecId,
                name = "Pec Deck",
                muscleGroup = "Chest",
                notes = "",
                isCustom = false,
                equipment = EquipmentType.MACHINE,
                loadType = LoadType.EXTERNAL,
            ),
            targetSets = 1,
            targetReps = 10,
            targetWeightKg = 50.0,
            restSeconds = 90,
        )
        deps.workoutRepository.logSet(sessionId, pecId, 50.0, 10, null, false)
        assertEquals(1, deps.workoutRepository.machineSetCountForSession(sessionId))
        val cotrelle = "gym-af-contrelle"
        deps.workoutRepository.moveSessionToGym(sessionId, cotrelle)
        assertEquals(cotrelle, deps.workoutRepository.getSession(sessionId)!!.gymId)
    }
}
