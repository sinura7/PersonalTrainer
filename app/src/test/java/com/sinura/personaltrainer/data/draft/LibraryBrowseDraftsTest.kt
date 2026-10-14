package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonParser
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
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

class LibraryBrowseDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val view = LibraryBrowseSnapshot(query = "  ROW  ", muscle = CanonicalMuscle.BACK,
        equipment = EquipmentType.CABLE, expandedFamilies = listOf("horizontal-row"),
        anchor = "ex-seated-cable-row", index = 4, offsetDp = 23.5f)

    @Test fun freshStoreRestoresRawContextAndAnchorWithoutACommand() = runTest {
        val root = temporary.newFolder()
        val first = LibraryBrowseDrafts(DurableDraftStore(root))
        first.keep(first.open(), view)
        val reopened = LibraryBrowseDrafts(DurableDraftStore(root)).open()
        assertTrue(reopened.readable)
        assertEquals(view, reopened.remembered)
        assertNull(reopened.checkpoint?.pending)
        assertFalse(view.toString().contains("ROW"))
    }

    @Test fun anOlderVisitCannotReplaceOrResetTheLastActiveView() = runTest {
        val service = LibraryBrowseDrafts(DurableDraftStore(temporary.newFolder()))
        val old = service.open()
        val current = service.open()
        service.keep(current, view)
        assertTrue(runCatching { service.keep(old, view.copy(query = "old")) }.isFailure)
        assertTrue(runCatching { service.reset(old) }.isFailure)
        assertEquals(view, service.open().remembered)
    }

    @Test fun lostAcknowledgementSettlesTheExactViewWithoutAnotherReplacement() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var replacements = 0
            override fun replace(file: File, bytes: ByteArray) {
                AtomicDraftFiles.replace(file, bytes)
                if (file.name != "generation.json") {
                    replacements += 1
                    throw IOException("synthetic lost acknowledgement")
                }
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io))
        val handle = service.open()
        service.keep(handle, view)
        val accepted = io.replacements
        service.keep(handle, view)
        assertEquals(accepted, io.replacements)
        assertEquals(view, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun failedReplacementKeepsThePreviousViewAndAllowsExactRetry() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var fail = false
            override fun replace(file: File, bytes: ByteArray) {
                if (fail) throw IOException("synthetic refusal")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io))
        val handle = service.open()
        service.keep(handle, view)
        val changed = view.copy(query = "  LAT  ")
        io.fail = true
        assertTrue(runCatching { service.keep(handle, changed) }.isFailure)
        assertEquals(view, handle.remembered)
        io.fail = false
        service.keep(handle, changed)
        assertEquals(changed, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun resetLostAcknowledgementIsInspectedRatherThanReplayed() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var lost = false
            var replacements = 0
            override fun replace(file: File, bytes: ByteArray) {
                AtomicDraftFiles.replace(file, bytes)
                if (file.name != "generation.json") {
                    replacements += 1
                    if (lost) throw IOException("synthetic reset acknowledgement")
                }
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io))
        val handle = service.open()
        service.keep(handle, view)
        io.lost = true
        service.reset(handle)
        val accepted = io.replacements
        service.reset(handle)
        assertEquals(accepted, io.replacements)
        assertEquals(LibraryBrowseSnapshot(), LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun generationChangeReconcilesOnlyTheRememberedNavigationFields() = runTest {
        val durable = DurableDraftStore(temporary.newFolder())
        val service = LibraryBrowseDrafts(durable)
        val handle = service.open()
        service.keep(handle, view)
        assertTrue(durable.invalidateOnce("synthetic accepted restore") is DraftGenerationChange.Changed)
        val changed = view.copy(query = "press")
        service.keep(handle, changed)
        assertEquals(changed, service.open().remembered)
    }

    @Test fun opaqueResetRefusesARepairedHealthyView() = runTest {
        val root = temporary.newFolder()
        val service = LibraryBrowseDrafts(DurableDraftStore(root))
        service.keep(service.open(), view)
        val file = root.listFiles()!!.filter { it.name.endsWith(".json") && it.name != "generation.json" }
            .single { JsonParser.parseString(it.readText()).asJsonObject.get("owner").asString == "last-view" }
        val healthy = file.readBytes()
        file.writeText("synthetic corrupt checkpoint")
        val failed = service.open()
        assertFalse(failed.readable)
        file.writeBytes(healthy)
        assertTrue(runCatching { service.reset(failed) }.isFailure)
        assertEquals(view, LibraryBrowseDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun malformedContextIsRefusedRatherThanClampedOrAliased() {
        val base = LibraryBrowseCodec.encodeDraft(view).asJsonObject
        for (raw in listOf("-1", "1.5", "1e2", "2147483648", "\"4\"")) {
            val copy = base.deepCopy()
            copy.add("index", JsonParser.parseString(raw))
            assertTrue(raw, runCatching { LibraryBrowseCodec.decodeDraft(copy) }.isFailure)
        }
        val duplicate = base.deepCopy()
        duplicate.add("families", JsonParser.parseString("[\"row\",\"row\"]"))
        assertTrue(runCatching { LibraryBrowseCodec.decodeDraft(duplicate) }.isFailure)
        val unknown = base.deepCopy()
        unknown.addProperty("equipment", "MADE_UP")
        assertTrue(runCatching { LibraryBrowseCodec.decodeDraft(unknown) }.isFailure)
    }
}
