package com.sinura.personaltrainer.data.draft

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.Exercise
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PickerSwapDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: TrainerDatabase
    private lateinit var maintenance: DbMaintenance
    private lateinit var repository: WorkoutRepository
    private lateinit var store: DurableDraftStore
    private var restoring = false
    private val files = object : DraftFileIO by AtomicDraftFiles {
        var loseAck = false
        override fun replace(file: File, bytes: ByteArray) {
            AtomicDraftFiles.replace(file, bytes)
            if (loseAck) { loseAck = false; throw IOException("Synthetic swap acknowledgement failure") }
        }
    }
    private val replacement = Exercise(id = "synthetic-new", name = "New press", muscleGroup = "Chest", notes = "", isCustom = false)
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrainerDatabase::class.java)
            .allowMainThreadQueries().build()
        maintenance = DbMaintenance(database)
        repository = WorkoutRepository(database, database.workoutDao(), maintenance)
        store = DurableDraftStore(directory = temporary.newFolder(), files = files)
        repository.beforeWorkoutDestruction = { event -> check(store.invalidateOnce(event) is DraftGenerationChange.Changed) }
        runBlocking {
            database.exerciseDao().insertAll(listOf("synthetic-old", "synthetic-new").map {
                ExerciseEntity(id = it, name = if (it == replacement.id) replacement.name else "Old press",
                    muscleGroup = "Chest", notes = "", isCustom = false)
            })
            database.workoutDao().upsertSession(WorkoutSessionEntity(id = "synthetic-session", routineId = null,
                routineName = "Synthetic workout", date = 1700000000000L, notes = "", durationMinutes = 0,
                startedAt = 1700000000000L, finishedAt = null))
            database.workoutDao().upsertSessionExercise(SessionExerciseEntity(id = "synthetic-source", sessionId = "synthetic-session",
                exerciseId = "synthetic-old", sortOrder = 0, targetSets = 3, targetReps = 8, targetWeightKg = 60.0, restSeconds = 90))
        }
    }
    @After fun close() { database.close() }
    private fun service() = PickerSwapDrafts(store = store, repository = repository, maintenance = maintenance, restoreOpen = { restoring })

    @Test fun interruptedAcknowledgementReopensTheSameFrozenResultAndSettlesWithoutAnotherSwap() = runBlocking {
        withTimeout(30_000) {
            val first = service()
            val handle = first.open("synthetic-session", "synthetic-source")
            first.admit(handle, "synthetic-session", "synthetic-source", replacement)
            val command = checkNotNull(handle.checkpoint.pending).value
            first.execute(handle) { true }
            val once = database.workoutDao().getAllSessionExercises()
            val reopened = service().open("synthetic-session", "synthetic-source")
            assertEquals(command, reopened.checkpoint.pending!!.value)
            assertEquals(command, service().openPending("synthetic-session")!!.checkpoint.pending!!.value)
            assertEquals(DraftRecoveryOutcome.EXACT_SAVED, service().inspect(reopened))
            assertEquals(DraftCommandPhase.ACCEPTED, reopened.checkpoint.pending!!.phase)
            assertEquals(once, database.workoutDao().getAllSessionExercises())
            service().finish(reopened)
            assertEquals(null, service().openPending("synthetic-session"))
            assertEquals(null, service().open("synthetic-session", "synthetic-source").checkpoint.pending)
            assertEquals(listOf(command.after), database.workoutDao().getAllSessionExercises())
        }
    }

    @Test fun lostAdmissionAckKeepsTheExactCommandAndRequiresInspectionBeforeRetry() = runBlocking {
        val first = service()
        val handle = first.open("synthetic-session", "synthetic-source")
        files.loseAck = true
        first.admit(handle, "synthetic-session", "synthetic-source", replacement)
        val command = handle.checkpoint.pending!!.value
        val reopened = service().open("synthetic-session", "synthetic-source")
        assertEquals(command, reopened.checkpoint.pending!!.value)
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.inspect(reopened))
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(reopened) { true } } }
        first.retry(reopened)
        first.execute(reopened) { true }
        assertEquals(listOf(command.after), database.workoutDao().getAllSessionExercises())
    }

    @Test fun restoreGenerationAndClosedRestoreGateRefuseAnUncommittedSwap() = runBlocking {
        val first = service()
        val handle = first.open("synthetic-session", "synthetic-source")
        first.admit(handle, "synthetic-session", "synthetic-source", replacement)
        assertTrue(store.invalidateOnce("synthetic-accepted-restore") is DraftGenerationChange.Changed)
        assertEquals(DraftRecoveryOutcome.CONFLICT, first.inspect(handle))
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(handle) { true } } }
        restoring = true
        assertThrows(IllegalStateException::class.java) { runBlocking { first.open("synthetic-session", "synthetic-source") } }
        assertEquals("synthetic-old", database.workoutDao().getAllSessionExercises().single().exerciseId)
    }

    @Test fun anOldAcceptedHandleCannotClearANewerAcceptedSwapAfterRestoredOwnership() = runBlocking {
        val first = service()
        val old = first.open("synthetic-session", "synthetic-source")
        first.admit(old, "synthetic-session", "synthetic-source", replacement)
        first.execute(old) { true }; first.inspect(old)
        val stale = PickerSwapHandle(old.binding, old.checkpoint, old.sessionId, old.itemId)
        val command = old.checkpoint.pending!!.value
        first.finish(old)
        database.workoutDao().deleteSessionExercise(command.after.id)
        database.workoutDao().upsertSessionExercise(command.before)
        assertTrue(store.invalidateOnce("synthetic-restore-original-owner") is DraftGenerationChange.Changed)
        val newer = first.open("synthetic-session", "synthetic-source")
        first.admit(newer, "synthetic-session", "synthetic-source", replacement)
        first.execute(newer) { true }; first.inspect(newer)
        val intended = newer.checkpoint.pending
        assertThrows(IllegalStateException::class.java) { runBlocking { first.finish(stale) } }
        assertEquals(intended, first.open("synthetic-session", "synthetic-source").checkpoint.pending)
        assertEquals(listOf(intended!!.value.after), database.workoutDao().getAllSessionExercises())
        first.finish(newer)
    }

    @Test fun importedIdsWithEmbeddedSeparatorsHaveDifferentPrivateOwners() = runBlocking {
        val first = service().open("session\u0000segment", "item")
        val second = service().open("session", "segment\u0000item")
        assertTrue(first.checkpoint.version.owner != second.checkpoint.version.owner)
    }

    @Test fun admissionCannotAdoptAnExistingCommandForDifferentSourceIds() = runBlocking {
        val first = service()
        val handle = first.open("synthetic-session", "synthetic-source")
        first.admit(handle, "synthetic-session", "synthetic-source", replacement)
        val intended = handle.checkpoint.pending
        assertThrows(IllegalStateException::class.java) {
            runBlocking { first.admit(handle, "another-session", "synthetic-source", replacement) }
        }
        val foreignHandle = PickerSwapHandle(handle.binding, handle.checkpoint, "another-session", "synthetic-source")
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(foreignHandle) { true } } }
        assertEquals(intended, first.open("synthetic-session", "synthetic-source").checkpoint.pending)
        assertEquals("synthetic-old", database.workoutDao().getAllSessionExercises().single().exerciseId)
    }

    @Test fun explicitResetClearsOnlyThePrivateSwapAndPreservesAnAlreadySavedReplacement() = runBlocking<Unit> {
        val first = service()
        val handle = first.open("synthetic-session", "synthetic-source")
        first.admit(handle, "synthetic-session", "synthetic-source", replacement)
        val command = handle.checkpoint.pending!!.value
        first.execute(handle) { true }
        first.discard("synthetic-session", "synthetic-source")
        assertEquals(null, first.openPending("synthetic-session"))
        assertEquals(listOf(command.after), database.workoutDao().getAllSessionExercises())
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(handle) { true } } }
    }

    @Test fun onlyOneSourceCanReserveTheSessionRecoveryLink() = runBlocking {
        val first = service()
        val original = first.open("synthetic-session", "synthetic-source")
        first.admit(original, "synthetic-session", "synthetic-source", replacement)
        val other = database.workoutDao().getAllSessionExercises().single().copy(id = "synthetic-other-source", sortOrder = 1)
        database.workoutDao().upsertSessionExercise(other)
        val competing = first.open("synthetic-session", other.id)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { first.admit(competing, "synthetic-session", other.id, replacement) }
        }
        assertEquals(original.checkpoint.pending, first.openPending("synthetic-session")!!.checkpoint.pending)
        assertThrows(IllegalStateException::class.java) { runBlocking { first.discard("synthetic-session", other.id) } }
        assertEquals(2, database.workoutDao().getAllSessionExercises().size)
    }

    @Test fun anUnreadableSessionLinkCanBeResetWithoutChangingRowsOrReplayingItsOldChild() = runBlocking {
        val first = service()
        val old = first.open("synthetic-session", "synthetic-source")
        first.admit(old, "synthetic-session", "synthetic-source", replacement)
        val before = database.workoutDao().getAllSessionExercises()
        val link = temporary.root.walkTopDown().filter { it.isFile && it.readText().contains("picker-swap-link") }.single()
        link.writeText("{synthetic-invalid")
        assertThrows(IllegalStateException::class.java) { runBlocking { first.openPending("synthetic-session") } }
        first.reset("synthetic-session", null)
        assertEquals(before, database.workoutDao().getAllSessionExercises())
        assertEquals(null, first.openPending("synthetic-session"))
        assertThrows(IllegalStateException::class.java) { runBlocking { first.execute(old) { true } } }
        val fresh = first.open("synthetic-session", "synthetic-source")
        assertEquals(null, fresh.checkpoint.pending)
        first.admit(fresh, "synthetic-session", "synthetic-source", replacement)
        first.execute(fresh) { true }
        assertEquals(listOf(fresh.checkpoint.pending!!.value.after), database.workoutDao().getAllSessionExercises())
    }
}
