package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.testutil.TestGymIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PreviewWeightsGymTest {
    private lateinit var deps: FakeAppDependencies
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        deps = FakeAppDependencies(context)
        viewModel = HomeViewModel(context, deps)
        runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = PEC_ID,
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
        }
    }

    @Test
    fun previewWeightsFollowGym() = runBlocking {
        finish(TestGymIds.WESTON, 55.0)
        finish(MAJOR, 45.0)
        val routine = pecDeckRoutine()
        val atWeston = viewModel.previewWeights(routine, TestGymIds.WESTON)
        assertEquals(55.0, atWeston.lastKg[PEC_ID]!!, 0.001)
        val atMajor = viewModel.previewWeights(routine, MAJOR)
        assertEquals(45.0, atMajor.lastKg[PEC_ID]!!, 0.001)
        val atCotrelle = viewModel.previewWeights(routine, COTRELLE)
        assertNull(atCotrelle.suggestedKg[PEC_ID])
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
                    loadType = LoadType.EXTERNAL,
                ),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        ),
    )

    private suspend fun finish(gymId: String, weightKg: Double) {
        val sessionId = "s-$gymId"
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = sessionId,
                routineId = null,
                routineName = "Upper",
                date = STAMP,
                notes = "",
                durationMinutes = 30,
                startedAt = STAMP,
                finishedAt = STAMP + 1,
                gymId = gymId,
            ),
        )
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "sl-$sessionId",
                sessionId = sessionId,
                exerciseId = PEC_ID,
                setNumber = 1,
                weightKg = weightKg,
                reps = 10,
                rpe = null,
                isWarmup = false,
                completedAt = STAMP + 1,
            ),
        )
    }

    companion object {
        private const val STAMP = 1_700_000_000_000L
        private const val PEC_ID = "pec-deck"
        private const val MAJOR = "gym-af-major-mackenzie"
        private const val COTRELLE = "gym-af-contrelle"
    }
}
