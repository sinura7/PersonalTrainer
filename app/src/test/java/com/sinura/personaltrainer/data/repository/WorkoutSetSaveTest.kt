package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseRecordPriorsRow
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class WorkoutSetSaveTest {
    private lateinit var deps: FakeAppDependencies
    private var failRecordQuery = false
    private val repository get() = deps.workoutRepository

    @Before
    fun setUp() {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override suspend fun recordPriorsBefore(
                    exerciseId: String, sessionId: String, weightKg: Double, completedAt: Long, setNumber: Int,
                ): ExerciseRecordPriorsRow {
                    check(!failRecordQuery) { "Injected post-commit record read failure" }
                    return real.recordPriorsBefore(exerciseId, sessionId, weightKg, completedAt, setNumber)
                }
            } },
        )
    }

    @After
    fun tearDown() { deps.close() }

    @Test
    fun concurrentRepeatedCommandWritesOneIdentityAndOneOrdinal() = runBlocking {
        val command = command()
        val results = List(6) { async { repository.saveSet(command) } }.awaitAll()
        assertEquals(1, results.count { !it.alreadySaved })
        assertEquals(setOf(command.setId), results.map { it.row.id }.toSet())
        assertEquals(setOf(1), results.map { it.workingOrdinal }.toSet())
        assertEquals(1, repository.getSession(command.sessionId)!!.sets.size)
    }

    @Test
    fun identicalValuesWithDifferentOperationIdsAreSeparateSets() = runBlocking {
        val first = command()
        repository.saveSet(first)
        val second = repository.saveSet(first.copy(setId = "second", completedAt = first.completedAt + 1))
        assertFalse(second.alreadySaved)
        assertEquals(2, second.row.setNumber)
        assertEquals(2, repository.getSession(first.sessionId)!!.sets.size)
    }

    @Test
    fun postCommitRecordFailureStillReturnsSavedAndRetryDoesNotInsert() = runBlocking {
        val command = command()
        failRecordQuery = true
        val saved = repository.saveSet(command)
        assertEquals(command.setId, saved.row.id)
        assertTrue(saved.records.isEmpty())
        assertEquals(WorkoutSetSaveResolution.SAVED, repository.inspectSetSave(command))
        assertTrue(repository.saveSet(command).alreadySaved)
        assertEquals(1, repository.getSession(command.sessionId)!!.sets.size)
    }

    @Test
    fun inspectDoesNotWriteAndKeepsCapturedDurationAndTime() = runBlocking {
        val command = command().copy(values = values.copy(durationSeconds = 27))
        assertEquals(WorkoutSetSaveResolution.UNSAVED, repository.inspectSetSave(command))
        assertTrue(repository.getSession(command.sessionId)!!.sets.isEmpty())
        val saved = repository.saveSet(command)
        assertEquals(27, saved.row.durationSeconds)
        assertEquals(command.completedAt, saved.row.completedAt)
        assertTrue(repository.saveSet(command).alreadySaved)
    }

    @Test
    fun repeatedIdWithDifferentPayloadOrOwnerConflictsWithoutOverwriting() = runBlocking {
        val command = command()
        repository.saveSet(command)
        val changed = command.copy(values = values.copy(weightKg = 90.0))
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(changed))
        assertTrue(runCatching { repository.saveSet(changed) }.exceptionOrNull() is WorkoutRepository.SetSaveConflict)
        val otherOwner = command.copy(exerciseId = "another-exercise")
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(otherOwner))
        assertTrue(runCatching { repository.saveSet(otherOwner) }.exceptionOrNull() is WorkoutRepository.SetSaveConflict)
        assertEquals(60.0, repository.getSession(command.sessionId)!!.sets.single().weightKg, 0.0)
    }

    @Test
    fun editRetriesUpdateOnlyTheirOriginalRowAndPreserveCapturedTime() = runBlocking {
        val command = command()
        repository.saveSet(command)
        val edit = command.copy(original = command.values, values = values.copy(weightKg = 65.0, isWarmup = true))
        assertEquals(WorkoutSetSaveResolution.UNSAVED, repository.inspectSetSave(edit))
        val saved = repository.saveSet(edit)
        assertEquals(1, saved.warmupOrdinal)
        assertEquals(0, saved.workingOrdinal)
        assertTrue(repository.saveSet(edit).alreadySaved)
        val row = repository.getSession(command.sessionId)!!.sets.single()
        assertEquals(command.setId, row.id)
        assertEquals(command.completedAt, row.completedAt)
        assertEquals(65.0, row.weightKg, 0.0)
    }

    @Test
    fun delayedEditCannotOverwriteNewerCorrectionOrReinsertDeletedRow() = runBlocking {
        val command = command()
        repository.saveSet(command)
        val delayed = command.copy(original = values, values = values.copy(weightKg = 65.0))
        repository.updateSet(command.setId, 80.0, 8, rpe = 7, isWarmup = false)
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(delayed))
        assertTrue(runCatching { repository.saveSet(delayed) }.exceptionOrNull() is WorkoutRepository.SetSaveConflict)
        repository.deleteSet(command.setId)
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(delayed))
        assertTrue(runCatching { repository.saveSet(delayed) }.exceptionOrNull() is WorkoutRepository.SetSaveConflict)
        assertTrue(repository.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test
    fun removedExerciseCannotReceiveAnUnwrittenInteractiveCommand() = runBlocking {
        val command = command()
        val item = repository.getSession(command.sessionId)!!.exercises.single()
        repository.removeExerciseFromSession(command.sessionId, item.id)
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(command))
        assertTrue(runCatching { repository.saveSet(command) }.exceptionOrNull() is WorkoutRepository.SetSaveConflict)
        assertTrue(repository.getSession(command.sessionId)!!.sets.isEmpty())
    }

    @Test
    fun committedCommandCanBeAcknowledgedAfterFinishButAbsentCommandCannotInsert() = runBlocking {
        val command = command()
        repository.saveSet(command)
        repository.finishSession(command.sessionId, notes = "")
        assertEquals(WorkoutSetSaveResolution.SAVED, repository.inspectSetSave(command))
        assertTrue(repository.saveSet(command).alreadySaved)
        val absent = command.copy(setId = "not-yet-saved")
        assertEquals(WorkoutSetSaveResolution.CONFLICT, repository.inspectSetSave(absent))
        assertTrue(runCatching { repository.saveSet(absent) }.isFailure)
        assertEquals(1, repository.getSession(command.sessionId)!!.sets.size)
    }

    private suspend fun command(): WorkoutSetSave {
        val fixture = seedTestWorkout(deps)
        return WorkoutSetSave(
            sessionId = fixture.session.id, exerciseId = fixture.exercise.id, setId = "operation-1",
            completedAt = 1_700_000_001_000L, values = values,
        )
    }

    private val values = WorkoutSetValues(weightKg = 60.0, reps = 8, rpe = 7, isWarmup = false, durationSeconds = null)
}
