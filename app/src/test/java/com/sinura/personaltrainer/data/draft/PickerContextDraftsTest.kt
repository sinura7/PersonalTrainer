package com.sinura.personaltrainer.data.draft

import com.google.gson.JsonParser
import com.sinura.personaltrainer.domain.CanonicalMuscle
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PickerContextDraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val owner = DraftOwner(DraftForm.ROUTINE, "synthetic-routine")
    private val fields = PickerContextSnapshot(query = "  bench á 🏋  ", muscle = CanonicalMuscle.CHEST,
        anchor = "ex-barbell-bench-press", index = 17, offsetDp = 36.5f, creating = true,
        customDraftId = "ex-custom-00000000-0000-0000-0000-000000000001")
    private fun service(root: File, files: DraftFileIO = AtomicDraftFiles) =
        PickerContextDrafts(DurableDraftStore(directory = root, files = files))

    @Test fun reopeningKeepsExactRawContextAndCustomReference() = runBlocking {
        val root = temporary.newFolder()
        val first = service(root)
        first.keep(first.open(owner), fields)
        val reopened = service(root).open(owner)
        assertTrue(reopened.readable)
        assertFalse(reopened.needsReconciliation)
        assertEquals(fields, reopened.remembered)
    }

    @Test fun ownersAndFormTypesRemainSeparateWhileAnotherPickerChanges() = runBlocking {
        val drafts = service(temporary.newFolder())
        val routine = drafts.open(owner)
        val secondOwner = DraftOwner(DraftForm.ROUTINE, "synthetic-second-routine")
        val second = drafts.open(secondOwner)
        val workoutOwner = DraftOwner(DraftForm.WORKOUT, owner.identity)
        val workout = drafts.open(workoutOwner)
        drafts.keep(routine, fields)
        drafts.keep(second, fields.copy(query = "second"))
        drafts.keep(workout, fields.copy(query = "workout"))
        assertEquals(fields, drafts.open(owner).remembered)
        assertEquals("second", drafts.open(secondOwner).remembered?.query)
        assertEquals("workout", drafts.open(workoutOwner).remembered?.query)
    }

    @Test fun oldVisitCannotReplaceTheNewerAcceptedView() = runBlocking {
        val drafts = service(temporary.newFolder())
        val old = drafts.open(owner)
        drafts.keep(old, fields)
        val current = drafts.open(owner)
        drafts.keep(current, fields.copy(query = "latest"))
        assertFalse(drafts.isCurrent(old))
        assertThrows(IllegalStateException::class.java) { runBlocking { drafts.keep(old, fields.copy(query = "stale")) } }
        assertEquals("latest", drafts.open(owner).remembered?.query)
    }

    @Test fun explicitDiscardRetiresTheHandleAndOnlyItsOwnContext() = runBlocking {
        val drafts = service(temporary.newFolder())
        val first = drafts.open(owner)
        val otherOwner = DraftOwner(DraftForm.ONBOARDING, "synthetic-week:Tuesday")
        val other = drafts.open(otherOwner)
        drafts.keep(first, fields)
        drafts.keep(other, fields.copy(query = "other day"))
        drafts.discard(first)
        assertFalse(drafts.isCurrent(first))
        assertThrows(IllegalStateException::class.java) { runBlocking { drafts.keep(first, fields) } }
        assertNull(drafts.open(owner).remembered)
        assertEquals("other day", drafts.open(otherOwner).remembered?.query)
    }

    @Test fun uncertainCheckpointAcknowledgementSettlesThePersistedValueWithoutReplay() = runBlocking {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var loseAck = false
            var replacements = 0
            override fun replace(file: File, bytes: ByteArray) {
                AtomicDraftFiles.replace(file, bytes)
                if (loseAck && file.name != "generation.json") {
                    replacements += 1
                    throw IOException("synthetic persisted checkpoint acknowledgement failure")
                }
            }
        }
        val drafts = service(root, io)
        val opened = drafts.open(owner)
        io.loseAck = true
        drafts.keep(opened, fields)
        assertEquals(1, io.replacements)
        assertEquals(fields, opened.remembered)
        drafts.keep(opened, fields)
        assertEquals(1, io.replacements)
        assertEquals(fields, service(root).open(owner).remembered)
    }

    @Test fun restoredGenerationRequiresActualOwnerReconciliationBeforeKeep() = runBlocking {
        val store = DurableDraftStore(directory = temporary.newFolder())
        val drafts = PickerContextDrafts(store)
        drafts.keep(drafts.open(owner), fields)
        assertTrue(store.invalidateOnce("synthetic-restore") is DraftGenerationChange.Changed)
        val restored = drafts.open(owner)
        assertTrue(restored.needsReconciliation)
        assertThrows(IllegalStateException::class.java) { runBlocking { drafts.keep(restored, fields.copy(query = "unchecked")) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { drafts.reconcile(restored) { false } } }
        assertEquals(fields, restored.remembered)
        drafts.reconcile(restored) { true }
        assertFalse(restored.needsReconciliation)
        drafts.keep(restored, fields.copy(query = "checked"))
        assertEquals("checked", drafts.open(owner).remembered?.query)
    }

    @Test fun repairedUnreadableViewMustBeCheckedBeforeConfirmedDiscard() = runBlocking {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var unreadable = false
            override fun read(file: File): ByteArray? {
                if (unreadable && file.name != "generation.json") throw IOException("synthetic unavailable view")
                return AtomicDraftFiles.read(file)
            }
        }
        val drafts = service(root, io)
        drafts.keep(drafts.open(owner), fields)
        io.unreadable = true
        val unavailable = drafts.open(owner)
        assertFalse(unavailable.readable)
        io.unreadable = false
        assertThrows(IllegalStateException::class.java) { runBlocking { drafts.discard(unavailable) } }
        drafts.reload(unavailable)
        assertEquals(fields, unavailable.remembered)
        drafts.discard(unavailable)
        assertNull(drafts.open(owner).remembered)
    }

    @Test fun invalidPrivateFieldsAreRefusedAndAuthoredTextIsRedacted() {
        val encoded = PickerContextCodec.encodeDraft(fields).asJsonObject
        assertEquals(fields, PickerContextCodec.decodeDraft(encoded))
        encoded.addProperty("index", "17")
        assertThrows(IllegalStateException::class.java) { PickerContextCodec.decodeDraft(encoded) }
        val missingIdentity = PickerContextCodec.encodeDraft(fields.copy(customDraftId = null))
        assertThrows(IllegalStateException::class.java) { PickerContextCodec.decodeDraft(missingIdentity) }
        val malformedText = JsonParser.parseString("""{"query":"\uD800","muscle":null,"anchor":null,"index":0,"offsetDp":0,"creating":false,"customDraftId":null}""")
        assertThrows(IllegalStateException::class.java) { PickerContextCodec.decodeDraft(malformedText) }
        assertFalse(fields.toString().contains(fields.query))
        assertFalse(fields.toString().contains(checkNotNull(fields.customDraftId)))
    }
}
