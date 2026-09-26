package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.workout.SavedStateWorkoutSave
import com.sinura.personaltrainer.workout.WorkoutDraftCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FloorSetSaves] on its own (audit W2d-2), for what the floor cannot easily reach: another
 * command's clear, the refusals, the recovered flag, and a restored set left alone while the
 * workout is not open. The floor's own tests are [FloorSetSaveCharacterisationTest] and
 * [WorkoutSaveRecoveryTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FloorSetSavesTest {
    private val cache = WorkoutDraftCache()
    private val handle = SavedStateHandle(mapOf("sessionId" to SESSION))
    private val saved = SavedStateWorkoutSave(handle)
    private var found = true
    private var resolution = WorkoutSetSaveResolution.UNSAVED
    private var writeResult: (WorkoutSetSave) -> WorkoutRepository.SavedWorkoutSet = { savedResult(it, alreadySaved = false) }
    private val writes = mutableListOf<WorkoutSetSave>()
    private val savedCalls = mutableListOf<Triple<WorkoutSetSave, Boolean, Boolean>>()
    private val released = mutableListOf<Pair<WorkoutSetSave, Boolean>>()
    private var rejections = 0

    private fun saves() = FloorSetSaves(
        sessionId = SESSION,
        cache = cache,
        saved = saved,
        write = { command -> writes += command; writeResult(command) },
        inspect = { resolution },
        sessionFound = { found },
        onSaved = { command, result, recovered -> savedCalls += Triple(command, result != null, recovered) },
        onReleased = { command, conflict -> released += command to conflict },
        onRejected = { rejections++ },
    )

    @Test
    fun nothingToRestoreStartsIdle() {
        val saves = saves()

        assertNull(saves.restored)
        assertNull(saves.keepRestored())
        assertEquals(WorkoutSaveState(), saves.operation.value)
        assertFalse(saves.pending)
        assertFalse(saves.inFlight.value)
    }

    /** Clearing another command leaves the frozen set, its phase and its saved copy alone. */
    @Test
    fun clearingAnotherCommandKeepsTheFrozenSet() {
        val saves = saves()
        saves.freeze(COMMAND)

        saves.clear(OTHER)

        assertTrue(saves.owns(COMMAND))
        assertEquals(COMMAND, cache.pendingSave(SESSION))
        assertEquals(COMMAND, saved.read(SESSION))
        assertEquals(WorkoutSavePhase.SAVING, saves.operation.value.phase)
    }

    @Test
    fun retryIsRefusedWhileBusyAndOnAConflictAndEditOnlyWhileBusy() {
        val saves = saves()
        saves.freeze(COMMAND)
        assertNull("busy", saves.beginRetry())
        assertNull("busy", saves.beginEdit())

        writeResult = { throw WorkoutRepository.SetSaveConflict() }
        runBlocking { saves.persist(COMMAND) }

        assertEquals(WorkoutSavePhase.CONFLICT, saves.operation.value.phase)
        assertNull("a conflict is not retried", saves.beginRetry())
        assertEquals(COMMAND, saves.beginEdit())
        assertEquals(WorkoutSavePhase.CHECKING, saves.operation.value.phase)
        assertNull(saves.operation.value.message)
        assertTrue(saves.inFlight.value)
    }

    /** A write the database already held is passed on as recovered (the floor then shows no receipt, starts no rest). */
    @Test
    fun aWriteTheDatabaseAlreadyHeldIsRecovered() = runBlocking {
        writeResult = { savedResult(it, alreadySaved = true) }
        val saves = saves()
        saves.freeze(COMMAND)

        saves.persist(COMMAND)

        assertEquals(listOf(Triple(COMMAND, true, true)), savedCalls)
        assertFalse(saves.inFlight.value)
        assertEquals(0, rejections)
    }

    /** Retry on a set the database never saw writes it only while the workout is open. */
    @Test
    fun aRetryWhileTheWorkoutIsNotOpenOffersRetryAgainAndWritesNothing() = runBlocking {
        cache.putPendingSave(COMMAND)
        val saves = saves()
        assertNull("a restored set is checked before it can be retried", saves.beginRetry())
        saves.reconcile(COMMAND, retryWrite = false)
        found = false
        val command = checkNotNull(saves.beginRetry())

        saves.reconcile(command, retryWrite = true)

        assertTrue(writes.isEmpty())
        assertEquals(WorkoutSavePhase.FAILED, saves.operation.value.phase)
        assertEquals("This set has not been saved. Retry to save these values.", saves.operation.value.message)
        assertFalse(saves.inFlight.value)
        assertTrue(savedCalls.isEmpty())
    }

    @Test
    fun aRetryWhileTheWorkoutIsOpenWritesTheFrozenSet() = runBlocking {
        cache.putPendingSave(COMMAND)
        val saves = saves()
        saves.reconcile(COMMAND, retryWrite = false)
        val command = checkNotNull(saves.beginRetry())

        saves.reconcile(command, retryWrite = true)

        assertEquals(listOf(COMMAND), writes)
        assertEquals(listOf(Triple(COMMAND, true, false)), savedCalls)
        assertFalse(saves.inFlight.value)
    }

    @Test
    fun aCheckThatFindsTheSetAcknowledgesItAsRecoveredWithoutWriting() = runBlocking {
        cache.putPendingSave(COMMAND)
        resolution = WorkoutSetSaveResolution.SAVED
        val saves = saves()

        saves.reconcile(COMMAND, retryWrite = true)

        assertTrue(writes.isEmpty())
        assertEquals(listOf(Triple(COMMAND, false, true)), savedCalls)
    }

    @Test
    fun editReleasesAnUnwrittenSetAndAConflictedOneWithoutReplay() = runBlocking {
        cache.putPendingSave(COMMAND)
        saved.write(COMMAND)
        val saves = saves()

        saves.reconcile(COMMAND, retryWrite = false, releaseUnwritten = true)

        assertEquals(listOf(COMMAND to false), released)
        assertNull(cache.pendingSave(SESSION))
        assertNull(saved.read(SESSION))
        assertEquals(WorkoutSaveState(), saves.operation.value)

        cache.putPendingSave(OTHER)
        resolution = WorkoutSetSaveResolution.CONFLICT
        val conflicted = saves()
        conflicted.reconcile(OTHER, retryWrite = true, releaseUnwritten = true)

        assertEquals(OTHER to true, released.last())
        assertTrue("never replayed", writes.isEmpty())
    }

    private fun savedResult(command: WorkoutSetSave, alreadySaved: Boolean) = WorkoutRepository.SavedWorkoutSet(
        row = SetLogEntity(
            id = command.setId, sessionId = command.sessionId, exerciseId = command.exerciseId, setNumber = 1,
            weightKg = command.values.weightKg, reps = command.values.reps, rpe = command.values.rpe,
            isWarmup = command.values.isWarmup, completedAt = command.completedAt,
        ),
        workingOrdinal = 1,
        warmupOrdinal = 0,
        targetSets = 3,
        alreadySaved = alreadySaved,
    )

    private companion object {
        const val SESSION = "session-1"
        val COMMAND = WorkoutSetSave(
            sessionId = SESSION, exerciseId = "squat", setId = "set-1", completedAt = 1_000L,
            values = WorkoutSetValues(weightKg = 100.0, reps = 5, rpe = null, isWarmup = false, durationSeconds = null),
        )
        val OTHER = COMMAND.copy(setId = "set-2")
    }
}
