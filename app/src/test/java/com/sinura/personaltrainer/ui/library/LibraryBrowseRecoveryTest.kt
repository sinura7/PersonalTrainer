package com.sinura.personaltrainer.ui.library

import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DraftVersion
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.LibraryBrowseDrafts
import com.sinura.personaltrainer.data.draft.LibraryBrowseSnapshot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowseRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private var current = LibraryBrowseSnapshot()
    private var basis: DraftVersion? = null
    private var unkept = false
    private var restorations = 0

    private class HeldIO(scheduler: TestCoroutineScheduler) : CoroutineDispatcher() {
        private val delegate = StandardTestDispatcher(scheduler)
        private val queued = mutableListOf<Pair<CoroutineContext, Runnable>>()
        var paused = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) queued += context to block else delegate.dispatch(context, block)
        }
        fun release() {
            paused = false
            val admitted = queued.toList()
            queued.clear()
            admitted.forEach { (context, block) -> delegate.dispatch(context, block) }
        }
    }

    private fun owner(scope: TestScope, service: LibraryBrowseDrafts) = LibraryBrowseRecovery(
        scope = scope, drafts = service, current = { current },
        restore = { current = it; restorations += 1 },
        framework = { LibraryFrameworkBasis(basis, unkept) },
        onBasis = { basis = it }, onKept = { basis = it; unkept = false },
    )

    @Test fun rapidAuthoredChangesCoalesceButDepartureFlushesTheLatestExactlyOnce() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(explicitRoute = false)
        advanceUntilIdle()
        assertFalse(recovery.status.value.resumed)
        current = current.copy(query = "r")
        unkept = true
        recovery.changed()
        current = current.copy(query = "  ROW  ", anchor = "ex-seated-cable-row", index = 4, offsetDp = 18f)
        recovery.changed()
        var exits = 0
        recovery.leave { exits += 1 }
        recovery.leave { exits += 1 }
        assertTrue(recovery.status.value.keeping)
        advanceUntilIdle()
        assertEquals(1, exits)
        assertEquals(1, restorations)
        assertNull(recovery.status.value.error)
        assertEquals(current, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun newerFrameworkFieldsFromTheSameCheckpointSurviveRecreation() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val first = owner(this, service)
        first.open(explicitRoute = false)
        advanceUntilIdle()
        current = current.copy(query = "  not yet checkpointed  ")
        unkept = true
        val expected = current
        val recreated = owner(this, service)
        recreated.open(explicitRoute = false)
        advanceUntilIdle()
        assertEquals(expected, current)
        assertEquals(expected, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun aLaterDurableResetOutranksAnOlderFrameworkSnapshot() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val handle = service.open()
        val old = LibraryBrowseSnapshot(query = "old authored search")
        service.keep(handle, old)
        basis = checkNotNull(handle.checkpoint).version
        service.reset(handle)
        current = old.copy(query = "older unkept framework")
        unkept = true
        val recovery = owner(this, service)
        recovery.open(explicitRoute = false)
        advanceUntilIdle()
        assertEquals(LibraryBrowseSnapshot(), current)
        assertFalse(recovery.status.value.resumed)
    }

    @Test fun failedKeepRetainsLiveFieldsAndRetryCompletesTheReservedDepartureOnce() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var fail = false
            override fun replace(file: File, bytes: ByteArray) {
                if (fail) throw IOException("synthetic context Keep failure")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(explicitRoute = false)
        advanceUntilIdle()
        current = current.copy(query = "  manual fields  ")
        unkept = true
        io.fail = true
        var exits = 0
        recovery.leave { exits += 1 }
        advanceUntilIdle()
        assertEquals(0, exits)
        assertEquals("  manual fields  ", current.query)
        assertTrue(recovery.status.value.error != null)
        io.fail = false
        recovery.keep()
        recovery.keep()
        advanceUntilIdle()
        assertEquals(1, exits)
        assertNull(recovery.status.value.error)
        assertEquals(current, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun explicitRouteDoesNotOpenTheRememberedUnrelatedFilter() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        service.keep(service.open(), LibraryBrowseSnapshot(query = "leg"))
        current = LibraryBrowseSnapshot(query = "", muscle = com.sinura.personaltrainer.domain.CanonicalMuscle.CHEST)
        val expected = current
        val recovery = owner(this, service)
        recovery.open(explicitRoute = true)
        advanceUntilIdle()
        assertEquals(expected, current)
        assertFalse(recovery.status.value.resumed)
    }

    @Test fun resetRetiresDelayedOlderCheckpointsInsteadOfResurrectingTheirQuery() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(explicitRoute = false)
        advanceUntilIdle()
        current = current.copy(query = "queued old search")
        unkept = true
        recovery.changed()
        recovery.reset(LibraryBrowseSnapshot())
        advanceUntilIdle()
        assertEquals(LibraryBrowseSnapshot(), current)
        assertEquals(current, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun anOlderRetainedVisitReentersUsingTheNewerAcceptedViewAndCanLeave() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val older = owner(this, service)
        older.open(false)
        advanceUntilIdle()
        current = current.copy(query = "old view")
        older.changed()
        older.closeVisit()
        val newer = service.open()
        val accepted = LibraryBrowseSnapshot(query = "new visit", anchor = "ex-new", index = 3)
        service.keep(newer, accepted)
        older.open(false)
        advanceUntilIdle()
        assertEquals(accepted, current)
        var exits = 0
        older.leave { exits += 1 }
        advanceUntilIdle()
        assertEquals(1, exits)
        assertNull(older.status.value.error)
        assertEquals(accepted, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun olderCheckpointAcknowledgementAdvancesOnlyTheBasisBeforeRecreation() = runTest {
        val root = temporary.newFolder()
        val io = HeldIO(testScheduler)
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = io))
        val first = owner(this, service)
        first.open(false)
        runCurrent()
        val initialBasis = basis
        current = current.copy(query = "first admitted")
        unkept = true
        first.changed()
        io.paused = true
        advanceTimeBy(150)
        runCurrent() // Keep is admitted, but the IO dispatcher has not executed it.
        current = current.copy(query = "newer typing")
        first.changed()
        io.release()
        runCurrent() // A completes; B's 150 ms checkpoint is still pending.
        assertTrue(basis != initialBasis)
        assertTrue(unkept)
        assertEquals("newer typing", current.query)
        first.closeVisit()
        val recreated = owner(this, service)
        recreated.open(false)
        runCurrent()
        assertEquals("newer typing", current.query)
        assertFalse(unkept)
    }

    @Test fun resetAfterAnOpaqueReadInspectsHealthyContextAndRequiresConfirmationAgain() = runTest {
        val root = temporary.newFolder()
        val healthy = LibraryBrowseSnapshot(query = "accepted remembered view")
        LibraryBrowseDrafts(DurableDraftStore(root)).let { it.keep(it.open(), healthy) }
        val io = object : DraftFileIO by AtomicDraftFiles {
            var failRead = true
            override fun read(file: File): ByteArray? {
                if (failRead) throw IOException("synthetic initial read failure")
                return AtomicDraftFiles.read(file)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(false)
        advanceUntilIdle()
        assertTrue(recovery.status.value.error != null)
        io.failRead = false
        recovery.reset(LibraryBrowseSnapshot())
        advanceUntilIdle()
        assertEquals(healthy, current)
        assertTrue(recovery.editable)
        assertTrue(recovery.status.value.error!!.contains("Confirm Reset again"))
        assertEquals(healthy, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
        recovery.reset(LibraryBrowseSnapshot())
        advanceUntilIdle()
        assertEquals(LibraryBrowseSnapshot(), current)
        assertNull(recovery.status.value.error)
    }

    @Test fun leavingCompositionDuringKeepCannotRunTheOldNavigationCallback() = runTest {
        val root = temporary.newFolder()
        val io = HeldIO(testScheduler)
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = io))
        val recovery = owner(this, service)
        recovery.open(false)
        runCurrent()
        current = current.copy(query = "latest departure")
        var exits = 0
        io.paused = true
        recovery.leave { exits += 1 }
        runCurrent()
        recovery.closeVisit()
        io.release()
        runCurrent()
        assertEquals(0, exits)
        recovery.open(false)
        runCurrent()
        assertTrue(recovery.editable)
        assertFalse(unkept)
        assertEquals("latest departure", LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered?.query)
    }

    @Test fun resetCompletedAfterDisposalIsAcknowledgedOnSameOwnerReturn() = runTest {
        val root = temporary.newFolder()
        val io = HeldIO(testScheduler)
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root,
            dispatcher = io))
        val recovery = owner(this, service)
        recovery.open(false)
        runCurrent()
        current = current.copy(query = "old query")
        recovery.leave {}
        runCurrent()
        io.paused = true
        recovery.reset(LibraryBrowseSnapshot())
        runCurrent()
        recovery.closeVisit()
        io.release()
        runCurrent()
        recovery.open(false)
        runCurrent()
        assertEquals(LibraryBrowseSnapshot(), current)
        assertTrue(recovery.editable)
        assertNull(recovery.status.value.error)
    }

    @Test fun retryAfterInitialReadFailureResumesTheRememberedViewWithoutErasingIt() = runTest {
        val root = temporary.newFolder()
        val healthy = LibraryBrowseSnapshot(query = "  remembered row  ",
            muscle = com.sinura.personaltrainer.domain.CanonicalMuscle.BACK,
            equipment = com.sinura.personaltrainer.domain.EquipmentType.CABLE,
            anchor = "ex-seated-cable-row", index = 4, offsetDp = 17f)
        LibraryBrowseDrafts(DurableDraftStore(root)).let { it.keep(it.open(), healthy) }
        val io = object : DraftFileIO by AtomicDraftFiles {
            var failRead = true
            override fun read(file: File): ByteArray? {
                if (failRead) throw IOException("synthetic initial read failure")
                return AtomicDraftFiles.read(file)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(false)
        advanceUntilIdle()
        assertTrue(recovery.status.value.error != null)
        io.failRead = false
        recovery.keep()
        advanceUntilIdle()
        assertEquals(healthy, current)
        assertNull(recovery.status.value.error)
        assertEquals(healthy, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun retryAfterInitialReadFailureKeepsChangesAuthoredSinceTheFailure() = runTest {
        val root = temporary.newFolder()
        LibraryBrowseDrafts(DurableDraftStore(root)).let { it.keep(it.open(), LibraryBrowseSnapshot(query = "old search")) }
        val io = object : DraftFileIO by AtomicDraftFiles {
            var failRead = true
            override fun read(file: File): ByteArray? {
                if (failRead) throw IOException("synthetic initial read failure")
                return AtomicDraftFiles.read(file)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(false)
        advanceUntilIdle()
        current = current.copy(query = "new authored search")
        unkept = true
        recovery.changed()
        io.failRead = false
        recovery.keep()
        advanceUntilIdle()
        assertEquals("new authored search", current.query)
        assertEquals(current, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun explicitBodyRouteSurvivesRetryAfterInitialPrivateReadFailure() = runTest {
        val root = temporary.newFolder()
        LibraryBrowseDrafts(DurableDraftStore(root)).let { it.keep(it.open(), LibraryBrowseSnapshot(query = "remembered back",
            muscle = com.sinura.personaltrainer.domain.CanonicalMuscle.BACK)) }
        val io = object : DraftFileIO by AtomicDraftFiles {
            var failRead = true
            override fun read(file: File): ByteArray? {
                if (failRead) throw IOException("synthetic initial read failure")
                return AtomicDraftFiles.read(file)
            }
        }
        current = LibraryBrowseSnapshot(muscle = com.sinura.personaltrainer.domain.CanonicalMuscle.CHEST)
        val requested = current
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io,
            dispatcher = UnconfinedTestDispatcher(testScheduler)))
        val recovery = owner(this, service)
        recovery.open(explicitRoute = true)
        advanceUntilIdle()
        // The same retained owner re-enters after composition/activity recreation.
        recovery.closeVisit()
        recovery.open(explicitRoute = false)
        advanceUntilIdle()
        io.failRead = false
        recovery.keep()
        advanceUntilIdle()
        assertEquals(requested, current)
        assertNull(recovery.status.value.error)
        assertEquals(requested, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }
}
