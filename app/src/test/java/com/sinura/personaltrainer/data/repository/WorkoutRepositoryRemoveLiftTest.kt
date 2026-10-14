package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.draft.DraftRecoveryOutcome
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Packet 5: remove-lift undo restores the same session-exercise id and slot.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryRemoveLiftTest {

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
            database.exerciseDao().insertAll(listOf(exerciseRow(SQUAT), exerciseRow(BENCH)))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun removeThenRestoreKeepsIdAndSortOrder() = runBlocking {
        insertLiveSession()
        insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = SE_BENCH, exerciseId = BENCH, sortOrder = 1)

        val removed = repository.removeExerciseFromSession(
            sessionId = SESSION,
            itemId = SE_BENCH,
        )
        assertEquals(SE_BENCH, removed.item.id)
        assertEquals(1, removed.item.sortOrder)
        assertEquals("Bench", removed.name)
        assertEquals(
            listOf(SE_SQUAT),
            sessionExerciseIds(),
        )

        repository.restoreExerciseToSession(removed = removed)
        repository.restoreExerciseToSession(removed = removed)
        val restored = database.workoutDao().getSession(SESSION)!!
        val bench = restored.exercises.single { it.item.id == SE_BENCH }
        assertEquals(1, bench.item.sortOrder)
        assertEquals(BENCH, bench.item.exerciseId)
        assertEquals(2, restored.exercises.size)
    }

    @Test
    fun restoreNoOpsWhenTheSessionHasFinished() = runBlocking {
        insertLiveSession()
        insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        insertSessionExercise(id = SE_BENCH, exerciseId = BENCH, sortOrder = 1)
        val removed = repository.removeExerciseFromSession(
            sessionId = SESSION,
            itemId = SE_BENCH,
        )
        database.workoutDao().finishSession(
            id = SESSION,
            notes = "",
            durationMinutes = 45,
            finishedAt = FINISH,
        )

        repository.restoreExerciseToSession(removed = removed)

        val after = database.workoutDao().getSession(SESSION)!!
        assertTrue(after.exercises.none { it.item.id == SE_BENCH })
        assertEquals(1, after.exercises.size)
        assertEquals(SE_SQUAT, after.exercises.single().item.id)
    }

    @Test fun frozenPickerSwapCommitsItsStableReplacementOnceAndInspectsAfterTheSourceDisappears() = runBlocking {
        insertLiveSession(); insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        val replacement = Exercise(id = BENCH, name = "Bench", muscleGroup = "Legs", notes = "", isCustom = false)
        val command = repository.preparePickerSwap(sessionId = SESSION, itemId = SE_SQUAT,
            replacement = replacement, resultId = "synthetic-stable-swap")
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, repository.inspectPickerSwap(command))
        repository.commitPickerSwap(command) { true }
        val once = database.workoutDao().getSession(SESSION)!!.exercises.map { it.item }
        repository.commitPickerSwap(command) { false }
        assertEquals(once, database.workoutDao().getSession(SESSION)!!.exercises.map { it.item })
        assertEquals(listOf(command.after), once)
        assertEquals(null, once.single().targetWeightKg)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, repository.inspectPickerSwap(command))
    }

    @Test fun pickerSwapRefusesForeignResultIdentityAndAChangedSourceWithoutReplacingEitherRow() = runBlocking {
        insertLiveSession(); insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        val replacement = Exercise(id = BENCH, name = "Bench", muscleGroup = "Legs", notes = "", isCustom = false)
        val command = repository.preparePickerSwap(sessionId = SESSION, itemId = SE_SQUAT,
            replacement = replacement, resultId = "synthetic-conflict-swap")
        database.workoutDao().upsertSessionExercise(command.before.copy(id = command.after.id, sortOrder = 1))
        val before = database.workoutDao().getAllSessionExercises()
        assertEquals(DraftRecoveryOutcome.CONFLICT, repository.inspectPickerSwap(command))
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.commitPickerSwap(command) { true } } }
        assertEquals(before, database.workoutDao().getAllSessionExercises())
        database.workoutDao().deleteSessionExercise(command.after.id)
        database.workoutDao().upsertSessionExercise(command.before.copy(targetSets = 4))
        assertEquals(DraftRecoveryOutcome.CONFLICT, repository.inspectPickerSwap(command))
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.commitPickerSwap(command) { true } } }
        assertEquals(4, database.workoutDao().getSession(SESSION)!!.exercises.single().item.targetSets)
    }

    @Test fun pickerSwapEnforcesAdmissionAndKeepsTimedReplacementRules() = runBlocking {
        insertLiveSession(); insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        database.exerciseDao().insertAll(listOf(exerciseRow("plank").copy(name = "Plank")))
        val replacement = Exercise(id = "plank", name = "Plank", muscleGroup = "Legs", notes = "", isCustom = false)
        val command = repository.preparePickerSwap(sessionId = SESSION, itemId = SE_SQUAT,
            replacement = replacement, resultId = "synthetic-timed-swap")
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.commitPickerSwap(command) { false } } }
        assertEquals(SE_SQUAT, database.workoutDao().getSession(SESSION)!!.exercises.single().item.id)
        repository.commitPickerSwap(command) { true }
        val saved = database.workoutDao().getSession(SESSION)!!.exercises.single().item
        assertEquals(HoldWork.HOLD_REPS_PLACEHOLDER, saved.targetReps)
        assertEquals(HoldWork.DEFAULT_SECONDS, saved.targetSeconds)
        assertEquals(3, saved.targetSets); assertEquals(90, saved.restSeconds)
    }

    @Test fun pickerSwapCannotReplaceAResultIdentityOwnedByAnotherSession() = runBlocking {
        insertLiveSession(); insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        val replacement = Exercise(id = BENCH, name = "Bench", muscleGroup = "Legs", notes = "", isCustom = false)
        val command = repository.preparePickerSwap(sessionId = SESSION, itemId = SE_SQUAT,
            replacement = replacement, resultId = "synthetic-foreign-result")
        database.workoutDao().upsertSession(WorkoutSessionEntity(id = "synthetic-other-session", routineId = null,
            routineName = "Other session", date = START, notes = "", durationMinutes = 0, startedAt = START + 1, finishedAt = null))
        database.workoutDao().upsertSessionExercise(command.after.copy(sessionId = "synthetic-other-session"))
        val before = database.workoutDao().getAllSessionExercises()
        assertEquals(DraftRecoveryOutcome.CONFLICT, repository.inspectPickerSwap(command))
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.commitPickerSwap(command) { true } } }
        assertEquals(before, database.workoutDao().getAllSessionExercises())
    }

    @Test fun aWarmupLoggedAfterAdmissionPreventsTheSwapAndPreservesTheRecordedLift() = runBlocking {
        insertLiveSession(); insertSessionExercise(id = SE_SQUAT, exerciseId = SQUAT, sortOrder = 0)
        val replacement = Exercise(id = BENCH, name = "Bench", muscleGroup = "Legs", notes = "", isCustom = false)
        val command = repository.preparePickerSwap(sessionId = SESSION, itemId = SE_SQUAT,
            replacement = replacement, resultId = "synthetic-refused-logged-swap")
        repository.logSet(sessionId = SESSION, exerciseId = SQUAT, weightKg = 40.0, reps = 8, rpe = null, isWarmup = true)
        val before = database.workoutDao().getSession(SESSION)
        assertEquals(DraftRecoveryOutcome.CONFLICT, repository.inspectPickerSwap(command))
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.commitPickerSwap(command) { true } } }
        assertEquals(before, database.workoutDao().getSession(SESSION))
    }

    private suspend fun sessionExerciseIds(): List<String> =
        database.workoutDao().getSession(SESSION)!!.exercises.map { it.item.id }

    private fun exerciseRow(id: String) = ExerciseEntity(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        muscleGroup = "Legs",
        notes = "",
        isCustom = false,
    )

    private suspend fun insertLiveSession() {
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = SESSION,
                routineId = null,
                routineName = "Live lower",
                date = START,
                notes = "",
                durationMinutes = 0,
                startedAt = START,
                finishedAt = null,
            ),
        )
    }

    private suspend fun insertSessionExercise(
        id: String,
        exerciseId: String,
        sortOrder: Int,
    ) {
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = id,
                sessionId = SESSION,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 80.0,
                restSeconds = 90,
            ),
        )
    }

    private companion object {
        const val SESSION = "session-remove-lift"
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val SE_SQUAT = "se-squat"
        const val SE_BENCH = "se-bench"
        const val START = 1_700_000_000_000L
        const val FINISH = START + 3_600_000L
    }
}
