package com.sinura.personaltrainer.ui.workout

import android.app.Application
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.workout.FinishOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionNotesSaveStateTest {
    @Test
    fun pendingAndHeldWriteNeverClaimTheNewTextSaved() = runTest {
        var stored: String? = "old"
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = FloorSessionNotes(sessionId = "session", write = {
            held.complete(Unit)
            release.await()
            stored = it.trim()
        }, readStored = { stored })
        owner.sessionRead("old")
        try {
            backgroundScope.launch { owner.writeOnTypingPause() }
            runCurrent()
            owner.edit("new")
            assertEquals(NotesSaveStatus.PENDING, owner.saveState.value.status)
            advanceTimeBy(399)
            runCurrent()
            assertFalse(held.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(held.isCompleted)
            assertEquals(NotesSaveStatus.SAVING, owner.saveState.value.status)
            assertEquals("old", stored)
            release.complete(Unit)
            runCurrent()
            assertEquals("new", stored)
            assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun failedClearKeepsTextAndAnExplicitDoubleRetryIsOneWrite() = runTest {
        var stored: String? = "old"
        var writes = 0
        var fail = true
        val release = CompletableDeferred<Unit>()
        val owner = FloorSessionNotes(sessionId = "session", write = {
            writes += 1
            check(!fail) { "Injected notes failure" }
            release.await()
            stored = it.trim()
        }, readStored = { stored })
        owner.sessionRead("old")
        owner.edit("")
        assertFalse(owner.writeNow())
        assertEquals(NotesSaveStatus.FAILED, owner.saveState.value.status)
        assertEquals("", owner.text.value)
        assertEquals("old", stored)
        fail = false
        try {
            assertTrue(owner.beginRetry())
            assertFalse(owner.beginRetry())
            val retry = launch { owner.retryNow() }
            runCurrent()
            assertEquals(2, writes)
            assertFalse(owner.saveState.value.canRetry)
            release.complete(Unit)
            retry.join()
            assertEquals("", stored)
            assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
            assertTrue(owner.saveState.value.cleared)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun anOlderHeldWriteAndExitFlushAreSerializedWithLatestTextLast() = runTest {
        var stored: String? = "seed"
        val writes = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val owner = FloorSessionNotes(sessionId = "session", write = {
            writes += it
            if (it == "older") release.await()
            stored = it.trim()
        }, readStored = { stored })
        owner.sessionRead("seed")
        try {
            owner.edit("older")
            val old = launch { owner.writeNow() }
            runCurrent()
            owner.edit("latest")
            val exit = async { owner.writeNow() }
            runCurrent()
            assertEquals(listOf("older"), writes)
            assertEquals(NotesSaveStatus.PENDING, owner.saveState.value.status)
            release.complete(Unit)
            old.join()
            assertTrue(exit.await())
            assertEquals(listOf("older", "latest"), writes)
            assertEquals("latest", stored)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun anOlderFailureCannotTurnNewerTypedTextIntoFailedOrSaved() = runTest {
        var stored: String? = "seed"
        val release = CompletableDeferred<Unit>()
        val owner = FloorSessionNotes(sessionId = "session", write = {
            if (it == "older") {
                release.await()
                error("Injected older failure")
            }
            stored = it.trim()
        }, readStored = { stored })
        owner.sessionRead("seed")
        try {
            owner.edit("older")
            val old = launch { owner.writeNow() }
            runCurrent()
            owner.edit("latest ")
            release.complete(Unit)
            old.join()
            assertEquals("latest ", owner.text.value)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
        assertEquals(NotesSaveStatus.PENDING, owner.saveState.value.status)
        assertTrue(owner.writeNow())
        assertEquals("latest", stored)
        assertEquals("latest ", owner.text.value)
    }

    @Test
    fun aSuccessfulUnitUpdateOnAMissingRowDoesNotBecomeSaved() = runTest {
        val owner = FloorSessionNotes(sessionId = "missing", write = {}, readStored = { null })
        owner.sessionRead("seed")
        owner.edit("latest")
        assertFalse(owner.writeNow())
        assertEquals(NotesSaveStatus.FAILED, owner.saveState.value.status)
        assertTrue(owner.saveState.value.missing)
        assertFalse(owner.saveState.value.canRetry)
        assertEquals("latest", owner.text.value)
    }

    @Test
    fun equivalentCanonicalTextDoesNotRewriteOrNormalizeTheDraft() = runTest {
        var writes = 0
        val owner = FloorSessionNotes(sessionId = "session", write = { writes += 1 }, readStored = { "seed" })
        owner.sessionRead("seed")
        owner.edit(" seed ")
        assertTrue(owner.writeNow())
        assertEquals(" seed ", owner.text.value)
        assertEquals(0, writes)
        assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
    }

    @Test
    fun aRestoredEmptyDeletionWaitsForHydrationAndIsThenConfirmed() = runTest {
        var stored: String? = "seed"
        var writes = 0
        val owner = FloorSessionNotes(sessionId = "session", write = { writes += 1; stored = it.trim() }, readStored = { stored })
        owner.restore("")
        assertFalse(owner.writeNow())
        assertEquals(NotesSaveStatus.UNKNOWN, owner.saveState.value.status)
        assertEquals(0, writes)
        owner.sessionRead("seed")
        assertEquals("", owner.text.value)
        assertTrue(owner.writeNow())
        assertEquals("", stored)
        assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
    }

    @Test
    fun finishWaitsForOlderWritesAndDisarmsLaterDebounceWrites() = runTest {
        var stored: String? = "seed"
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        var finishes = 0
        val owner = FloorSessionNotes(sessionId = "session", write = {
            writes += it
            release.await()
            stored = it.trim()
        }, readStored = { stored })
        owner.sessionRead("seed")
        try {
            owner.edit("older")
            val older = launch { owner.writeNow() }
            runCurrent()
            owner.edit("final")
            val finish = async { owner.finishWithLatest {
                finishes += 1
                stored = it.trim()
                FinishOutcome.Finished("session")
            } }
            runCurrent()
            assertEquals(0, finishes)
            release.complete(Unit)
            older.join()
            assertEquals(FinishOutcome.Finished("session"), finish.await())
            assertEquals("final", stored)
            owner.writeNow()
            assertEquals(listOf("older"), writes)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun aRestoredClearWhosePauseExpiredBeforeTheRowStillAutosavesAfterHydration() = runTest {
        var stored: String? = "old"
        var writes = 0
        val owner = FloorSessionNotes(sessionId = "session", write = { writes += 1; stored = it.trim() }, readStored = { stored })
        owner.restore("")
        backgroundScope.launch { owner.writeOnTypingPause() }
        runCurrent()
        advanceTimeBy(401)
        runCurrent()
        assertEquals(0, writes)
        assertEquals(NotesSaveStatus.UNKNOWN, owner.saveState.value.status)
        owner.sessionRead("old")
        runCurrent()
        assertEquals("", owner.text.value)
        assertEquals(NotesSaveStatus.PENDING, owner.saveState.value.status)
        advanceTimeBy(401)
        runCurrent()
        assertEquals(1, writes)
        assertEquals("", stored)
        assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
        assertTrue(owner.saveState.value.cleared)
    }

    @Test
    fun aFailedFinishKeepsLatestNotesAndAllowsTheirOwnRetry() = runTest {
        var stored: String? = "old"
        val owner = FloorSessionNotes(sessionId = "session", write = { stored = it.trim() }, readStored = { stored })
        owner.sessionRead("old")
        owner.edit("latest ")
        assertEquals(FinishOutcome.Failed("Injected finish failure"), owner.finishWithLatest {
            assertEquals("latest ", it)
            FinishOutcome.Failed("Injected finish failure")
        })
        assertEquals("latest ", owner.text.value)
        assertEquals(NotesSaveStatus.FAILED, owner.saveState.value.status)
        assertTrue(owner.beginRetry())
        owner.retryNow()
        assertEquals("latest", stored)
        assertEquals(NotesSaveStatus.SAVED, owner.saveState.value.status)
    }
}
