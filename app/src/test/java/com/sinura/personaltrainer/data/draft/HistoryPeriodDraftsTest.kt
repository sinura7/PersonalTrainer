package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonParser
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryPeriodDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val day = HistoryPeriodSelection(AnalyticsHorizon.WEEK, 20739, false)

    @Test fun freshServiceRestoresTheExactCivilSelectionWithoutACommand() = runTest {
        val root = temporary.newFolder()
        val first = HistoryPeriodDrafts(DurableDraftStore(root))
        first.keep(first.open(), day)
        val reopened = HistoryPeriodDrafts(DurableDraftStore(root)).open()
        assertTrue(reopened.readable)
        assertEquals(day, reopened.remembered)
        assertNull(reopened.checkpoint?.pending)
    }

    @Test fun anOlderHistoryCannotReplaceOrResetTheNewerVisit() = runTest {
        val store = HistoryPeriodDrafts(DurableDraftStore(temporary.newFolder()))
        val old = store.open()
        val latest = store.open()
        store.keep(latest, day)
        assertTrue(runCatching { store.keep(old, day.copy(anchorEpochDay = 20737)) }.isFailure)
        assertTrue(runCatching { store.reset(old) }.isFailure)
        assertEquals(day, store.open().remembered)
    }

    @Test fun lostAcknowledgementIsInspectedAndRepeatedKeepDoesNotWriteAgain() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var writes = 0
            override fun replace(file: File, bytes: ByteArray) {
                AtomicDraftFiles.replace(file, bytes)
                if (file.name != "generation.json") {
                    writes += 1
                    throw IOException("synthetic lost acknowledgement")
                }
            }
        }
        val store = HistoryPeriodDrafts(DurableDraftStore(directory = root, files = io))
        val owner = store.open()
        store.keep(owner, day)
        val acceptedWrites = io.writes
        store.keep(owner, day)
        assertEquals(acceptedWrites, io.writes)
        assertEquals(day, HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun generationChangesReconcileOnlyThisNavigationPreference() = runTest {
        val durable = DurableDraftStore(temporary.newFolder())
        val store = HistoryPeriodDrafts(durable)
        val owner = store.open()
        store.keep(owner, day)
        assertTrue(durable.invalidateOnce("synthetic-restore") is DraftGenerationChange.Changed)
        val changed = day.copy(anchorEpochDay = 20738)
        store.keep(owner, changed)
        assertEquals(changed, store.open().remembered)
    }

    @Test fun unreadablePreferenceNeedsExplicitResetAndDoesNotEraseOtherOwners() = runTest {
        val root = temporary.newFolder()
        val durable = DurableDraftStore(root)
        val unrelated = durable.bind(DraftOwner(DraftForm.HISTORY, "other-owner"), HistoryPeriodCodec)
        val other = (unrelated.read() as DraftRead.PresentCheckpoint).checkpoint
        unrelated.checkpoint(other.version, day)
        val store = HistoryPeriodDrafts(durable)
        store.keep(store.open(), day)
        root.listFiles()!!.filter { it.name.endsWith(".json") && it.name != "generation.json" }
            .single { JsonParser.parseString(it.readText()).asJsonObject.get("owner").asString == "selected-period" }
            .writeText("invalid private checkpoint")
        val owner = store.open()
        assertFalse(owner.readable)
        assertTrue(runCatching { store.keep(owner, day) }.isFailure)
        store.reset(owner)
        assertTrue(owner.readable)
        assertNull(owner.remembered)
        assertEquals(day, (unrelated.read() as DraftRead.PresentCheckpoint).checkpoint.draft)
    }

    @Test fun malformedCivilSelectionIsNotReinterpreted() {
        for (raw in listOf("20736.5", "2e4", "\"20736\"", "9223372036854775807")) {
            val document = JsonParser.parseString("{\"horizon\":\"WEEK\",\"anchorEpochDay\":$raw,\"followToday\":false}")
            assertTrue(raw, runCatching { HistoryPeriodCodec.decodeDraft(document) }.isFailure)
        }
        for (raw in listOf(
            "{\"horizon\":\"UNKNOWN\",\"anchorEpochDay\":20736,\"followToday\":false}",
            "{\"horizon\":\"WEEK\",\"anchorEpochDay\":20736,\"followToday\":\"false\"}",
            "{\"horizon\":\"WEEK\",\"anchorEpochDay\":20736,\"followToday\":false,\"unexpected\":1}",
        )) assertTrue(raw, runCatching { HistoryPeriodCodec.decodeDraft(JsonParser.parseString(raw)) }.isFailure)
    }

    @Test fun allHorizonsKeepTheirExactAnchorAndFollowTodayIntent() = runTest {
        val root = temporary.newFolder()
        val store = HistoryPeriodDrafts(DurableDraftStore(root))
        for (horizon in AnalyticsHorizon.entries) for (follow in listOf(false, true)) {
            val value = day.copy(horizon = horizon, followToday = follow)
            store.keep(store.open(), value)
            assertEquals(value, HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered)
        }
    }
}
