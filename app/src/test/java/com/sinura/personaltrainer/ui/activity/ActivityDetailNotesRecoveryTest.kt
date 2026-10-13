package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.draft.CompletedNotesDrafts
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.completedNotesDrafts
import com.sinura.personaltrainer.data.draft.CompletedNoteOwner
import com.sinura.personaltrainer.data.draft.CompletedNoteKind
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.util.JvmTime
import java.io.IOException
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room write fault and a fresh owner, with Main kept on its confined scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityDetailNotesRecoveryTest {
    private val main = StandardTestDispatcher()
    private val io = UnconfinedTestDispatcher(main.scheduler)
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private lateinit var drafts: CompletedNotesDrafts
    private val owners = mutableListOf<ActivityDetailViewModel>()
    private val refusedWrites = AtomicInteger()
    @Volatile private var failNotes = false

    @Before fun setUp() {
        Dispatchers.setMain(main)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = io,
            activityDaoDecorator = { real -> object : ActivityDao by real {
                override suspend fun updateSession(session: ActivitySessionEntity) {
                    if (failNotes) {
                        refusedWrites.incrementAndGet()
                        throw IOException("Synthetic completed-note write failure")
                    }
                    real.updateSession(session)
                }
            } },
        )
        directory = Files.createTempDirectory("temper-completed-notes-test-").toFile()
        drafts = completedNotesDrafts(DurableDraftStore(directory, io), deps) { false }
    }

    @After fun tearDown() {
        runBlocking { owners.forEach { retire(it) } }
        if (::deps.isInitialized) deps.close()
        if (::directory.isInitialized) directory.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test fun failedNotesSurviveClosingAndReopeningTheirActivity() = runBlocking {
        val now = JvmTime.captureNow()
        val saved = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                title = "Synthetic walk", notes = "Saved note",
                performedStart = now, performedEnd = now,
                blocks = listOf(CardioBlock(
                    id = "notes-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                    elapsedSeconds = 600L, movingSeconds = null, distanceMeters = null,
                    elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                )),
            ), now,
        ) as ActivityWrite.Accepted
        val vm = create(saved.session.id)
        val loaded = async { vm.uiState.first { !it.isLoading && it.session != null } }
        pumpUntil { loaded.isCompleted }
        assertEquals("Saved note", loaded.await().notes)
        val raw = "  More comfortable pace\nKeep this authored text  "
        failNotes = true
        vm.setNotes(raw)
        vm.persistNotesForExit()
        pumpUntil { refusedWrites.get() == 1 }
        retire(vm)
        assertEquals("failed mutation must preserve the original business record", "Saved note", deps.activityRepository.get(saved.session.id)?.notes)
        val reopened = create(saved.session.id)
        val restored = async { reopened.uiState.first { !it.isLoading && it.session != null } }
        pumpUntil { restored.isCompleted }
        val state = restored.await()
        println("Synthetic note recovery: activity=${saved.session.id}; saved=Saved note; requestedRaw=$raw; actualRaw=${state.notes}; visibleError=${reopened.error.value}")
        assertEquals("reopening the same owner must retain failed authored notes", raw, state.notes)
    }

    @Test fun missingOwnerRetainsItsDraftAndStillAllowsBack() = runBlocking {
        val id = seed()
        val key = CompletedNoteOwner(CompletedNoteKind.ACTIVITY, id)
        drafts.keep(drafts.open(key), "Retained orphan words")
        assertTrue(deps.activityRepository.deleteCompleted(id) is ActivityWrite.Accepted)
        val vm = create(id)
        val missing = async { vm.uiState.first { it.missing && it.notes == "Retained orphan words" } }
        pumpUntil { missing.isCompleted }
        missing.await()
        pumpUntil { vm.notesRecovery.value.loaded }
        vm.requestNotesExit()
        pumpUntil { vm.notesExitRequested.value }
        assertNull(deps.activityRepository.get(id))
        assertEquals("Retained orphan words", drafts.open(key).raw)
        assertEquals(0, refusedWrites.get())
    }

    @Test fun discardAdmissionRejectsLateTypingAndDoesNotRecreateTheDraft() = runBlocking {
        val id = seed()
        val vm = create(id)
        val loaded = async { vm.uiState.first { !it.isLoading && it.session != null } }
        pumpUntil { loaded.isCompleted }
        loaded.await()
        vm.setNotes("Words being discarded")
        pumpUntil { vm.notesRecovery.value.kept }
        vm.discardPrivateNotes()
        assertTrue("discard reserves before its coroutine runs", vm.notesRecovery.value.discarding)
        vm.setNotes("Late typing must be refused visibly")
        pumpUntil { !vm.notesRecovery.value.discarding && vm.uiState.value.notes == "Saved note" }
        main.scheduler.advanceTimeBy(400)
        main.scheduler.runCurrent()
        assertNull(drafts.open(CompletedNoteOwner(CompletedNoteKind.ACTIVITY, id)).raw)
        assertEquals("Saved note", deps.activityRepository.get(id)?.notes)
    }

    @Test fun backReservationPreventsDiscardingTheDraftBeingKeptForNavigation() = runBlocking {
        val id = seed()
        val key = CompletedNoteOwner(CompletedNoteKind.ACTIVITY, id)
        drafts.keep(drafts.open(key), "Unfinished words to keep")
        assertTrue(deps.activityRepository.updateCompletedNotes(id, "Changed saved notes", JvmTime.nowMillis()) is ActivityWrite.Accepted)
        val vm = create(id)
        val loaded = async { vm.uiState.first { !it.isLoading && it.notes == "Unfinished words to keep" && vm.notesRecovery.value.loaded } }
        pumpUntil { loaded.isCompleted }
        loaded.await()
        vm.requestNotesExit()
        assertTrue(vm.notesExiting.value)
        vm.discardPrivateNotes()
        assertFalse(vm.notesRecovery.value.discarding)
        vm.setNotes("Late input cannot replace the promised draft")
        pumpUntil { vm.notesExitRequested.value }
        vm.discardPrivateNotes()
        main.scheduler.runCurrent()
        assertEquals("Unfinished words to keep", drafts.open(key).raw)
        assertEquals("Changed saved notes", deps.activityRepository.get(id)?.notes)
    }

    private suspend fun seed(): String {
        val now = JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                title = "Synthetic notes walk", notes = "Saved note", performedStart = now, performedEnd = now,
                blocks = listOf(CardioBlock(
                    id = "notes-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                    elapsedSeconds = 600L, movingSeconds = null, distanceMeters = null,
                    elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                )),
            ), now,
        ) as ActivityWrite.Accepted
        return write.session.id
    }

    private fun create(id: String) = ActivityDetailViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("activityId" to id)), container = deps, drafts = drafts,
    ).also(owners::add)

    private suspend fun retire(vm: ActivityDetailViewModel) {
        val job = checkNotNull(vm.viewModelScope.coroutineContext[Job])
        job.cancel()
        pumpUntil { job.isCompleted }
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
