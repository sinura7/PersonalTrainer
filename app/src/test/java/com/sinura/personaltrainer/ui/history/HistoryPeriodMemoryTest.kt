package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.HistoryPeriodDrafts
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPeriodMemoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val monday = HistoryPeriodSelection(AnalyticsHorizon.MONTH, 20735, false)
    private val friday = monday.copy(anchorEpochDay = 20739)

    @Test fun rapidChoicesKeepTheLatestDayAndReopenWithoutReplayingTheEarlierChoice() = runTest {
        val root = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, dispatcher))
        val memory = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        assertTrue(memory.snapshot.value.loaded)
        assertTrue(memory.choose(monday))
        assertTrue(memory.choose(friday))
        assertTrue(memory.snapshot.value.saving)
        runCurrent()
        assertFalse(memory.snapshot.value.saving)
        assertNull(memory.snapshot.value.error)
        assertEquals(friday, HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
    }

    @Test fun failedKeepRetainsTheExactLatestChoiceAndRetryPersistsOnlyThatChoice() = runTest {
        val root = temporary.newFolder()
        var fail = true
        val io = object : DraftFileIO by AtomicDraftFiles {
            override fun replace(file: File, bytes: ByteArray) {
                if (fail && file.name != "generation.json") throw IOException("synthetic full storage")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, dispatcher, io))
        val memory = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        memory.choose(monday)
        memory.choose(friday)
        runCurrent()
        assertNotNull(memory.snapshot.value.error)
        assertFalse(memory.snapshot.value.saving)
        assertNull(HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
        fail = false
        memory.retry()
        runCurrent()
        assertNull(memory.snapshot.value.error)
        assertEquals(friday, HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
    }

    @Test fun anOldMemoryRetryOrResetCannotReclaimANewerHomeSelection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(temporary.newFolder(), dispatcher))
        val old = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        old.choose(monday)
        runCurrent()
        val current = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        assertEquals(monday, current.snapshot.value.restored)
        current.choose(friday)
        runCurrent()
        old.retry()
        runCurrent()
        assertNotNull(old.snapshot.value.error)
        old.reset()
        runCurrent()
        assertNotNull(old.snapshot.value.error)
        assertEquals(friday, drafts.open().remembered)
    }

    @Test fun explicitResetIsAcknowledgedOnceAndAReopenedHomeHasNoRetainedDay() = runTest {
        val root = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, dispatcher))
        val memory = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        memory.choose(friday)
        runCurrent()
        memory.reset()
        assertFalse(memory.choose(monday))
        runCurrent()
        assertEquals(1L, memory.snapshot.value.resetSerial)
        assertNull(memory.snapshot.value.error)
        assertTrue(memory.consumeReset(1L))
        assertFalse(memory.consumeReset(1L))
        assertEquals(0L, memory.snapshot.value.resetSerial)
        assertNull(HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
    }

    @Test fun aChoiceAfterResetCompletesSupersedesItsQueuedScreenCue() = runTest {
        val root = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, dispatcher))
        val memory = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        memory.choose(monday)
        runCurrent()
        memory.reset()
        runCurrent()
        val queuedReset = memory.snapshot.value.resetSerial
        assertEquals(1L, queuedReset)
        assertTrue(memory.choose(friday))
        // An effect from the previous frame must not send the new choice back to today.
        assertFalse(memory.consumeReset(queuedReset))
        runCurrent()
        assertEquals(friday, HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
        assertEquals(0L, memory.snapshot.value.resetSerial)
        memory.reset()
        runCurrent()
        assertEquals(2L, memory.snapshot.value.resetSerial)
        assertFalse(memory.consumeReset(queuedReset))
        assertTrue(memory.consumeReset(2L))
    }

    @Test fun retryAfterFailedResetRepeatsTheConfirmedResetInsteadOfReloadingTheOldDay() = runTest {
        val root = temporary.newFolder()
        var fail = false
        val io = object : DraftFileIO by AtomicDraftFiles {
            override fun replace(file: File, bytes: ByteArray) {
                if (fail && file.name != "generation.json") throw IOException("synthetic reset failure")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, dispatcher, io))
        val memory = HistoryPeriodMemory(drafts, backgroundScope)
        runCurrent()
        memory.choose(friday)
        runCurrent()
        fail = true
        memory.reset()
        runCurrent()
        assertNotNull(memory.snapshot.value.error)
        assertEquals(0L, memory.snapshot.value.resetSerial)
        assertEquals(friday, HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
        fail = false
        memory.retry()
        assertFalse(memory.choose(monday))
        runCurrent()
        assertNull(memory.snapshot.value.error)
        assertEquals(1L, memory.snapshot.value.resetSerial)
        assertNull(HistoryPeriodDrafts(DurableDraftStore(root, dispatcher)).open().remembered)
    }
}
