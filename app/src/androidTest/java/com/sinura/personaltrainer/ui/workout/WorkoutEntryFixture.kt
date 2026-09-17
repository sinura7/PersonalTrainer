package com.sinura.personaltrainer.ui.workout

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Real Room/ViewModel fixture restricted to the disposable repository emulator. */
internal class WorkoutEntryFixture {
    private val app: PersonalTrainerApp = ApplicationProvider.getApplicationContext()
    val container get() = app.container
    lateinit var sessionId: String
        private set
    private lateinit var routineId: String
    private var customExerciseId: String? = null
    var expectedWeightKg = 60.0
        private set
    lateinit var vm: ActiveWorkoutViewModel
        private set

    fun seed(exerciseId: String = "ex-barbell-back-squat", targetSets: Int = 12, longName: Boolean = false) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        runBlocking(Dispatchers.IO) {
            container.restTimerController.stop()
            container.workoutRepository.getInProgress()?.let { live ->
                check(live.routineName?.startsWith(PREFIX) == true) { "Unexpected live fixture: ${live.routineName}" }
                container.discardWorkout(live.id)
            }
            container.routineRepository.observeAll().first().filter { it.name.startsWith(PREFIX) }
                .forEach { container.routineRepository.delete(it.id) }
            container.preferencesRepository.setOnboardingComplete(true)
            container.preferencesRepository.setWeightUnit(WeightUnit.KG)
            val exercise = if (longName) {
                when (val result = container.exerciseRepository.createCustom(
                    "$PREFIX · Single-leg contralateral eccentric Romanian deadlift with a controlled three-second lowering phase", "Hamstrings",
                )) {
                    is SaveExerciseResult.Saved -> result.exercise
                    is SaveExerciseResult.DuplicateName -> result.existing
                    SaveExerciseResult.MissingMuscle -> error("Fixture muscle is missing")
                }.also { customExerciseId = it.id }
            } else awaitExercise(exerciseId)
            expectedWeightKg = if (exercise.loadType == LoadType.BODYWEIGHT) 0.0 else 60.0
            val routine = container.routineRepository.create("$PREFIX · Lower A")
            routineId = routine.id
            container.routineRepository.addExercise(
                routineId = routine.id,
                exercise = exercise,
                targetSets = targetSets,
                targetReps = 8,
                targetWeightKg = expectedWeightKg,
                restSeconds = 90,
            )
            val planned = checkNotNull(container.routineRepository.getById(routine.id))
            sessionId = container.workoutRepository.startRoutine(planned).id
        }
        vm = ActiveWorkoutViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = container,
        )
    }

    fun close() {
        if (::vm.isInitialized) runBlocking { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        runBlocking(Dispatchers.IO) {
            container.restTimerController.stop()
            if (::sessionId.isInitialized && container.workoutRepository.getInProgress()?.id == sessionId) {
                container.discardWorkout(sessionId)
            }
            if (::routineId.isInitialized) container.routineRepository.delete(routineId)
            customExerciseId?.let { container.exerciseRepository.deleteCustom(it) }
        }
    }

    private suspend fun awaitExercise(id: String): Exercise = withTimeout(15_000) {
        var exercise = container.exerciseRepository.getById(id)
        while (exercise == null) {
            delay(50)
            exercise = container.exerciseRepository.getById(id)
        }
        exercise
    }

    private companion object { const val PREFIX = "F2 entry fixture" }
}
