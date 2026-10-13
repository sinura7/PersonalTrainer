package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.DraftGenerationChange
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.data.draft.CompletedNoteOwner
import com.sinura.personaltrainer.data.draft.CompletedNoteKind
import com.sinura.personaltrainer.data.draft.CompletedNoteFieldsCodec
import com.sinura.personaltrainer.data.draft.completedNotesDrafts
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
class CompletedSessionNotesRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val main = StandardTestDispatcher()
    private val io = UnconfinedTestDispatcher(main.scheduler)
    private lateinit var deps: FakeAppDependencies
    private var vm: SessionDetailViewModel? = null
    private val failFlow = MutableStateFlow(false)
    private val notesWrites = AtomicInteger()

    @Before fun setUp() {
        Dispatchers.setMain(main)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = io,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override fun observeSession(id: String): Flow<SessionWithDetails?> = failFlow.flatMapLatest { failed ->
                    if (failed) flow { throw IOException("Synthetic detail read failure") } else real.observeSession(id)
                }
                override suspend fun updateSessionNotes(id: String, notes: String) {
                    notesWrites.incrementAndGet()
                    real.updateSessionNotes(id, notes)
                }
            } },
        )
    }
    @After fun tearDown() {
        runBlocking {
            vm?.let { owner ->
                val job = checkNotNull(owner.viewModelScope.coroutineContext[Job])
                job.cancel()
                pumpUntil { job.isCompleted }
            }
        }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun completedOwnershipRemainsFencedDuringUnavailableRetry() = runBlocking {
        val id = "synthetic-completed-notes-owner"
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(id, null, "Synthetic strength", 100L, "Original", 1, 100L, 200L),
        )
        val privateStore = DurableDraftStore(temporary.newFolder(), io)
        val drafts = completedNotesDrafts(privateStore, deps) { false }
        val owner = SessionDetailViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to id)), deps, drafts)
        vm = owner
        val loaded = async { owner.uiState.first { !it.isLoading && it.session?.isFinished == true && owner.notesRecovery.value.ready } }
        pumpUntil { loaded.isCompleted }
        loaded.await()
        owner.setNotes("Kept unfinished words")
        pumpUntil { owner.notesRecovery.value.kept }
        failFlow.value = true
        pumpUntil { owner.uiState.value.failed }
        owner.retry()
        pumpUntil { owner.uiState.value.failed && owner.uiState.value.session == null }
        val binding = privateStore.bind(DraftOwner(DraftForm.COMPLETED_NOTES, "fields:" + CompletedNoteOwner(CompletedNoteKind.STRENGTH, id).key), CompletedNoteFieldsCodec)
        val checkpoint = (binding.read() as DraftRead.PresentCheckpoint).checkpoint
        assertTrue(deps.dbMaintenance.withMaintenanceLock { privateStore.advanceGeneration(checkpoint.currentGeneration) } is DraftGenerationChange.Changed)
        owner.persistNotesForExit()
        pumpUntil { owner.uiState.value.notesSave.status == NotesSaveStatus.FAILED }
        assertEquals("completed notes cannot fall back to an unchecked writer", 0, notesWrites.get())
        assertEquals("Original", deps.workoutRepository.getSession(id)?.notes)
        assertEquals("Kept unfinished words", owner.uiState.value.notes)
    }

    @Test fun freshMissingStrengthEntryRestoresPrivateTextAndCanKeepAndLeave() = runBlocking {
        val id = "synthetic-missing-completed-notes-owner"
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(id, null, "Synthetic strength", 100L, "Original", 1, 100L, 200L),
        )
        val privateStore = DurableDraftStore(temporary.newFolder(), io)
        val drafts = completedNotesDrafts(privateStore, deps) { false }
        val key = CompletedNoteOwner(CompletedNoteKind.STRENGTH, id)
        drafts.keep(drafts.open(key), "Private completed words")
        val binding = privateStore.bind(DraftOwner(DraftForm.COMPLETED_NOTES, "fields:" + key.key), CompletedNoteFieldsCodec)
        val checkpoint = (binding.read() as DraftRead.PresentCheckpoint).checkpoint
        assertTrue(deps.dbMaintenance.withMaintenanceLock { privateStore.advanceGeneration(checkpoint.currentGeneration) } is DraftGenerationChange.Changed)
        deps.database.workoutDao().deleteSession(id)
        val owner = SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext(), savedStateHandle = SavedStateHandle(mapOf("sessionId" to id)),
            container = deps, drafts = drafts,
        )
        vm = owner
        val missing = async { owner.uiState.first { it.missing && it.notes == "Private completed words" && owner.notesRecovery.value.loaded } }
        pumpUntil { missing.isCompleted }
        missing.await()
        assertTrue(owner.durableCompletedNotes)
        owner.requestNotesExit()
        owner.discardPrivateNotes()
        pumpUntil { owner.notesExitRequested.value }
        owner.discardPrivateNotes()
        main.scheduler.runCurrent()
        assertNull(deps.workoutRepository.getSession(id))
        assertEquals("Private completed words", drafts.open(key).raw)
        assertEquals(0, notesWrites.get())
    }

    private suspend fun pumpUntil(done: () -> Boolean) {
        withTimeout(10_000) {
            while (!done()) {
                main.scheduler.runCurrent()
                if (!done()) delay(10)
            }
        }
        main.scheduler.runCurrent()
    }
}
