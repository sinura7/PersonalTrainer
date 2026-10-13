package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonParser
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

class HomeDayDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val day = RememberedHomeDay(20739, 20736)

    @Test fun freshServiceRestoresTheExactCivilSelectionWithoutACommand() = runTest {
        val root = temporary.newFolder()
        val first = HomeDayDrafts(DurableDraftStore(root))
        first.keep(first.open(), day)
        val reopened = HomeDayDrafts(DurableDraftStore(root)).open()
        assertTrue(reopened.readable)
        assertEquals(day, reopened.remembered)
        assertNull(reopened.checkpoint?.pending)
    }

    @Test fun anOlderHomeCannotReplaceOrResetTheNewerVisit() = runTest {
        val store = HomeDayDrafts(DurableDraftStore(temporary.newFolder()))
        val old = store.open()
        val latest = store.open()
        store.keep(latest, day)
        assertTrue(runCatching { store.keep(old, day.copy(selectedEpochDay = 20737)) }.isFailure)
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
        val store = HomeDayDrafts(DurableDraftStore(directory = root, files = io))
        val owner = store.open()
        store.keep(owner, day)
        val acceptedWrites = io.writes
        store.keep(owner, day)
        assertEquals(acceptedWrites, io.writes)
        assertEquals(day, HomeDayDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun generationChangesReconcileOnlyThisNavigationPreference() = runTest {
        val durable = DurableDraftStore(temporary.newFolder())
        val store = HomeDayDrafts(durable)
        val owner = store.open()
        store.keep(owner, day)
        assertTrue(durable.invalidateOnce("synthetic-restore") is DraftGenerationChange.Changed)
        val changed = day.copy(selectedEpochDay = 20738)
        store.keep(owner, changed)
        assertEquals(changed, store.open().remembered)
    }

    @Test fun unreadablePreferenceNeedsExplicitResetAndDoesNotEraseOtherOwners() = runTest {
        val root = temporary.newFolder()
        val durable = DurableDraftStore(root)
        val unrelated = durable.bind(DraftOwner(DraftForm.HOME, "other-owner"), HomeDayCodec)
        val other = (unrelated.read() as DraftRead.PresentCheckpoint).checkpoint
        unrelated.checkpoint(other.version, day)
        val store = HomeDayDrafts(durable)
        store.keep(store.open(), day)
        root.listFiles()!!.filter { it.name.endsWith(".json") && it.name != "generation.json" }
            .single { JsonParser.parseString(it.readText()).asJsonObject.get("owner").asString == "selected-day" }
            .writeText("invalid private checkpoint")
        val owner = store.open()
        assertFalse(owner.readable)
        assertTrue(runCatching { store.keep(owner, day) }.isFailure)
        store.reset(owner)
        assertTrue(owner.readable)
        assertNull(owner.remembered)
        assertEquals(day, (unrelated.read() as DraftRead.PresentCheckpoint).checkpoint.draft)
    }

    @Test fun malformedCivilDaysAreNotReinterpreted() {
        for (raw in listOf("20736.5", "2e4", "\"20736\"", "9223372036854775807")) {
            val document = JsonParser.parseString("{\"selectedEpochDay\":$raw,\"todayEpochDay\":20736}")
            assertTrue(raw, runCatching { HomeDayCodec.decodeDraft(document) }.isFailure)
        }
    }
}
