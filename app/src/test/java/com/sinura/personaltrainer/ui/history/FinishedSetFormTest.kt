package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.FinishedSetDraft
import com.sinura.personaltrainer.data.draft.FinishedSetDrafts
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetSaveResolution
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FinishedSetFormTest {
    @get:Rule val temporary = TemporaryFolder()
    private val main = StandardTestDispatcher()
    private val io = UnconfinedTestDispatcher(main.scheduler)
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private lateinit var session: WorkoutSession
    private val jobs = mutableListOf<Job>()
    private var writes = 0
    private var failBeforeWrite = false
    private var loseWriteAck = false
    private val files = FaultFiles()

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(main)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = io)
        directory = temporary.newFolder()
        session = seedTestWorkout(deps = deps, exerciseId = "finished-form-squat",
            loggedSets = listOf(TestSetInput(100.0, 5)), finish = true).session
    }
    @After fun close() = runBlocking {
        jobs.forEach { it.cancel() }; main.scheduler.runCurrent(); jobs.forEach { it.join() }
        deps.close(); Dispatchers.resetMain()
    }

    @Test fun closeAndFreshOwnerRetainValuesRawDecimalAndCapturedUnit() = runBlocking {
        val first = form(); await { first.recovery.value.ready }
        val fields = fields(); assertTrue(first.begin(fields))
        first.update(fields.values.copy(reps = 11, rpe = 8, isWarmup = true))
        val raw = WorkoutNumberDraft(exerciseId = fields.original.exerciseId, field = WorkoutNumberField.WEIGHT,
            text = "102.", weightUnit = WeightUnit.LBS, selectionStart = 4, selectionEnd = 4, open = true)
        first.put(raw)
        var closed = false
        first.close { closed = true }
        // A stale editable frame cannot change the value after Close reserved its durable flush.
        first.update(fields.values.copy(reps = 999))
        await { closed }
        assertFalse(first.recovery.value.visible)
        first.update(fields.values.copy(reps = 999))
        first.put(raw.copy(text = "999"))
        val reopened = form(); await { reopened.recovery.value.ready }
        assertTrue(reopened.recovery.value.resumed)
        assertTrue(reopened.recovery.value.visible)
        assertEquals(11, reopened.recovery.value.draft?.values?.reps)
        assertEquals(listOf(raw), reopened.recovery.value.draft?.numbers)
        assertEquals(0, writes)
        assertEquals(5, checkNotNull(deps.workoutRepository.getSession(session.id)).sets.single().reps)
    }

    @Test fun refusedWriteCanCloseWithItsExactFrozenCommandAndReopen() = runBlocking {
        val first = form(); await { first.recovery.value.ready }
        val fields = fields(); first.begin(fields); failBeforeWrite = true
        val writing = asyncMain { first.write(fields.command) }
        await { writing.isCompleted }
        assertTrue(writing.isCancelled)
        var closed = false; first.close { closed = true }; await { closed }
        val commands = mutableListOf<WorkoutSetSave>()
        val reopened = form(commands); await { reopened.recovery.value.ready }
        assertEquals(listOf(fields.command), commands)
        assertEquals(fields, reopened.recovery.value.draft)
        assertEquals(0, writes)
    }

    @Test fun retryCloseActuallyKeepsTheNewestFieldsAfterStorageRecovers() = runBlocking {
        val first = form(); await { first.recovery.value.ready }
        files.refuse = true
        val latest = fields().copy(values = fields().values.copy(reps = 12))
        first.begin(latest)
        await { first.recovery.value.message != null }
        var closed = false; first.close { closed = true }
        await { !first.isClosing }
        assertFalse(closed)
        files.refuse = false
        first.close { closed = true }; await { closed }
        val reopened = form(); await { reopened.recovery.value.ready }
        assertEquals(latest, reopened.recovery.value.draft)
        assertEquals(0, writes)
    }

    @Test fun readAgainNeverDropsNewerLiveFieldsWhenTheCheckpointStillCannotBeWritten() = runBlocking {
        val first = form(); await { first.recovery.value.ready }
        files.refuse = true
        val latest = fields().copy(values = fields().values.copy(reps = 13))
        first.begin(latest); await { first.recovery.value.message != null }
        first.readAgain(); await { !first.isClosing }
        assertEquals(latest, first.recovery.value.draft)
        assertFalse(first.recovery.value.ready)
        files.refuse = false
        var closed = false; first.close { closed = true }; await { closed }
        val reopened = form(); await { reopened.recovery.value.ready }
        assertEquals(latest, reopened.recovery.value.draft)
        assertEquals(0, writes)
    }

    @Test fun interruptedAcknowledgementReadsSavedSetAndCleansWithoutReplay() = runBlocking {
        val first = form(); await { first.recovery.value.ready }
        val fields = fields(); first.begin(fields); loseWriteAck = true
        val writing = asyncMain { runCatching { first.write(fields.command) } }
        await { writing.isCompleted }; assertTrue(writing.await().isFailure)
        val commands = mutableListOf<WorkoutSetSave>()
        val reopened = form(commands); await { reopened.recovery.value.ready }
        assertEquals(listOf(fields.command), commands)
        val inspecting = asyncMain { reopened.inspect(fields.command) }
        await { inspecting.isCompleted }; assertEquals(WorkoutSetSaveResolution.SAVED, inspecting.await())
        reopened.acknowledgeSaved()
        val cleaning = asyncMain { reopened.settle(fields.command) }
        await { cleaning.isCompleted }; cleaning.await()
        assertFalse(reopened.recovery.value.visible)
        assertNull(reopened.recovery.value.draft)
        assertEquals(1, writes)
    }

    @Test fun unreadablePrivateStateCanBeDiscardedWithoutChangingTheSavedRow() = runBlocking {
        val store = service(); store.keep(store.open(session.id), fields())
        directory.listFiles()!!.single { it.name != "generation.json" }.writeText("unreadable")
        val broken = form(); await { broken.recovery.value.message != null }
        assertFalse(broken.recovery.value.ready)
        var discarded = false; broken.discard { discarded = true }; await { discarded }
        assertTrue("confirmed discard reopens editing without an invisible recovery action", broken.canBegin())
        assertNull(broken.recovery.value.draft)
        assertEquals(0, writes)
        assertEquals(session.sets, checkNotNull(deps.workoutRepository.getSession(session.id)).sets)
    }

    private fun form(commands: MutableList<WorkoutSetSave> = mutableListOf()): FinishedSetForm {
        val job = SupervisorJob(); jobs.add(job)
        return FinishedSetForm(store = service(), sessionId = session.id, scope = CoroutineScope(job + main),
            operationPending = { false }, onPending = { commands.add(it) }).also { it.start() }
    }
    private fun fields(): FinishedSetDraft {
        val row = session.sets.single(); val before = WorkoutSetValues.from(row)
        val original = WorkoutSetSave(sessionId = row.sessionId, exerciseId = row.exerciseId,
            setId = row.id, completedAt = row.completedAt, values = before, original = before)
        return FinishedSetDraft(FinishedSetDrafts.stamp(session), original, before.copy(weightKg = 105.0, rpe = 9))
    }
    private fun service() = FinishedSetDrafts(
        store = DurableDraftStore(directory, io, files), maintenance = deps.dbMaintenance, restoreOpen = { false }, dispatcher = io,
        readSession = deps.workoutRepository::getSession, inspectSaved = deps.workoutRepository::inspectFinishedSetSave,
        readSaved = deps.workoutRepository::readFinishedSetSaveResult,
        writeLocked = { command ->
            if (failBeforeWrite) throw IOException("Synthetic refusal")
            val result = deps.workoutRepository.saveFinishedSetLocked(command); writes++
            if (loseWriteAck) { loseWriteAck = false; throw IOException("Synthetic acknowledgement loss") }
            result
        },
    )
    private fun <T> asyncMain(block: suspend () -> T) = CoroutineScope(jobs.first() + main).async { block() }
    private suspend fun await(ready: () -> Boolean) = withTimeout(TestWaits.FLOW_MS) {
        while (!ready()) { main.scheduler.runCurrent(); delay(10) }
    }
    private class FaultFiles : DraftFileIO by AtomicDraftFiles {
        var refuse = false
        override fun replace(file: File, bytes: ByteArray) {
            if (refuse) throw IOException("Synthetic private Keep refusal")
            AtomicDraftFiles.replace(file, bytes)
        }
    }
}
