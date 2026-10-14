package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerContextSnapshot
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class PickerViewRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val routine = DraftOwner(DraftForm.ROUTINE, "synthetic-routine")
    private var requested = routine
    private var owned = true
    private val seeds = mutableMapOf<DraftOwner, PickerFrameworkSeed>()
    private var restored = PickerContextSnapshot()
    private fun recovery(scope: TestScope, drafts: PickerContextDrafts) = PickerViewRecovery(
        scope = scope, drafts = drafts, owner = { requested }, stillOwned = { owned && it == requested },
        initial = { PickerContextSnapshot() }, framework = { seeds[it] },
        cache = { seeds[it.owner] = it }, restored = { restored = it },
    )
    private fun drafts(scope: TestScope, root: File, files: DraftFileIO = AtomicDraftFiles) =
        PickerContextDrafts(DurableDraftStore(directory = root, files = files,
            dispatcher = UnconfinedTestDispatcher(scope.testScheduler)))

    @Test fun departureFlushesLatestRawFieldsAndIsReservedOnce() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        assertFalse(actor.status.value.resumed)
        actor.changed(PickerContextSnapshot(query = "r"))
        val intended = PickerContextSnapshot(query = "  Row á 🏋  ", anchor = "ex-row", index = 16, offsetDp = 38f)
        actor.changed(intended)
        var exits = 0
        actor.leave { exits++ }
        actor.leave { exits++ }
        advanceUntilIdle()
        assertEquals(1, exits)
        assertEquals(intended, service.open(routine).remembered)
        assertNull(actor.status.value.error)
    }

    @Test fun explicitResetRetiresQueuedFieldsAndCannotRestoreTheOldFrameworkSeed() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        actor.changed(PickerContextSnapshot(query = "must not return", index = 9))
        actor.reset()
        advanceUntilIdle()
        assertEquals(PickerContextSnapshot(), actor.fields.value)
        actor.closeVisit()
        val reopened = recovery(this, drafts(this, root))
        reopened.open()
        advanceUntilIdle()
        assertEquals(PickerContextSnapshot(), reopened.fields.value)
        assertNull(reopened.status.value.error)
    }

    @Test fun reopeningRestoresOnlyTheRequestedOwnersContext() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val first = recovery(this, service)
        first.open()
        advanceUntilIdle()
        first.changed(PickerContextSnapshot(query = "routine", index = 5))
        advanceUntilIdle()
        first.closeVisit()
        requested = DraftOwner(DraftForm.WORKOUT, "synthetic-workout:add")
        val second = recovery(this, service)
        second.open()
        advanceUntilIdle()
        assertEquals("", second.fields.value.query)
        second.changed(PickerContextSnapshot(query = "workout"))
        advanceUntilIdle()
        second.closeVisit()
        requested = routine
        seeds.clear()
        val reopened = recovery(this, drafts(this, root))
        reopened.open()
        advanceUntilIdle()
        assertTrue(reopened.status.value.resumed)
        assertEquals("routine", restored.query)
        assertEquals(5, restored.index)
    }

    @Test fun failedKeepRetainsFieldsAndRetryCompletesTheOriginalDepartureOnce() = runTest {
        val root = temporary.newFolder()
        val io = object : DraftFileIO by AtomicDraftFiles {
            var fail = false
            override fun replace(file: File, bytes: ByteArray) {
                if (fail) throw IOException("synthetic private Keep failure")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val service = drafts(this, root, io)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        io.fail = true
        actor.changed(PickerContextSnapshot(query = "  exact latest  "))
        var exits = 0
        actor.leave { exits++ }
        advanceUntilIdle()
        assertEquals(0, exits)
        assertEquals("  exact latest  ", actor.fields.value.query)
        assertNotNull(actor.status.value.error)
        assertFalse(actor.editable)
        actor.changed(PickerContextSnapshot(query = "another operation"))
        assertEquals("  exact latest  ", actor.fields.value.query)
        io.fail = false
        actor.keep()
        actor.keep()
        advanceUntilIdle()
        assertEquals(1, exits)
        assertEquals(actor.fields.value, service.open(routine).remembered)
    }

    @Test fun newerFrameworkFieldsUseTheirMatchingDurableBasisAfterRecreation() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val first = recovery(this, service)
        first.open()
        advanceUntilIdle()
        val kept = checkNotNull(seeds[routine])
        val authored = kept.fields.copy(query = "not yet checkpointed")
        seeds[routine] = kept.copy(fields = authored, unkept = true)
        first.closeVisit()
        val recreated = recovery(this, service)
        recreated.open()
        advanceUntilIdle()
        assertEquals(authored, recreated.fields.value)
        assertEquals(authored, service.open(routine).remembered)
    }

    @Test fun foreignFrameworkSeedCannotCrossTheOwnershipBoundary() = runTest {
        val root = temporary.newFolder()
        val foreign = DraftOwner(DraftForm.WORKOUT, "foreign")
        seeds[routine] = PickerFrameworkSeed(owner = foreign, version = null,
            fields = PickerContextSnapshot(query = "foreign raw fields"), unkept = true)
        val service = drafts(this, root)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        assertEquals("", actor.fields.value.query)
        assertEquals("", service.open(routine).remembered?.query)
    }

    @Test fun unknownFrameworkBasisCannotOverwriteAChangedAcceptedView() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val kept = PickerContextSnapshot(query = "new accepted view")
        service.keep(service.open(routine), kept)
        val authored = PickerContextSnapshot(query = "older raw framework")
        seeds[routine] = PickerFrameworkSeed(owner = routine, version = null, fields = authored, unkept = true)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        assertTrue(actor.status.value.changedElsewhere)
        assertEquals(authored, actor.fields.value)
        actor.keep()
        advanceUntilIdle()
        assertEquals(kept, drafts(this, root).open(routine).remembered)
        // Reconfirming the inspected kept view permits navigation without replaying an old write.
        actor.useKeptView()
        advanceUntilIdle()
        assertEquals(kept, actor.fields.value)
        assertFalse(actor.status.value.changedElsewhere)
    }

    @Test fun missingOwnerDoesNotCreateAPickerCheckpointOrNavigate() = runTest {
        val root = temporary.newFolder()
        owned = false
        val service = drafts(this, root)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        assertNotNull(actor.status.value.error)
        var exits = 0
        actor.leave { exits++ }
        advanceUntilIdle()
        assertEquals(0, exits)
        assertNull(service.open(routine).remembered)
    }

    @Test fun anOlderVisitCannotOverwriteAnotherVisitsAcceptedView() = runTest {
        val root = temporary.newFolder()
        val service = drafts(this, root)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        actor.changed(PickerContextSnapshot(query = "old visit"))
        actor.closeVisit()
        val accepted = PickerContextSnapshot(query = "latest accepted", index = 9)
        service.keep(service.open(routine), accepted)
        actor.open()
        advanceUntilIdle()
        assertEquals(accepted, actor.fields.value)
        var exits = 0
        actor.leave { exits++ }
        advanceUntilIdle()
        assertEquals(1, exits)
        assertEquals(accepted, service.open(routine).remembered)
    }

    @Test fun automaticPositionAfterAnUnreadableOpenCannotOverwriteTheRememberedQueryOnRetry() = runTest {
        val root = temporary.newFolder()
        val remembered = PickerContextSnapshot(query = "  remembered raw search  ", index = 6, offsetDp = 27f)
        val healthy = drafts(this, root)
        healthy.keep(healthy.open(routine), remembered)
        val io = object : DraftFileIO by AtomicDraftFiles {
            var failRead = true
            override fun read(file: File): ByteArray? {
                if (failRead) throw IOException("synthetic unavailable private read")
                return AtomicDraftFiles.read(file)
            }
        }
        val actor = recovery(this, drafts(this, root, io))
        actor.open()
        advanceUntilIdle()
        assertNotNull(actor.status.value.error)
        actor.positionChanged(index = 0, offsetDp = 0f, anchor = "filters")
        io.failRead = false
        actor.keep()
        advanceUntilIdle()
        assertNull(actor.status.value.error)
        assertEquals(remembered, actor.fields.value)
        assertEquals(remembered, drafts(this, root).open(routine).remembered)
    }

    @Test fun returningAfterAStoreGenerationChangeReloadsAndReconcilesBeforeKeep() = runTest {
        val root = temporary.newFolder()
        val store = DurableDraftStore(directory = root, dispatcher = UnconfinedTestDispatcher(testScheduler))
        val service = PickerContextDrafts(store)
        val actor = recovery(this, service)
        actor.open()
        advanceUntilIdle()
        val intended = PickerContextSnapshot(query = "kept before restore")
        actor.changed(intended)
        advanceUntilIdle()
        actor.closeVisit()
        store.invalidateOnce("synthetic accepted restore")
        actor.open()
        advanceUntilIdle()
        assertNull(actor.status.value.error)
        assertEquals(intended, actor.fields.value)
        var exits = 0
        actor.leave { exits++ }
        advanceUntilIdle()
        assertEquals(1, exits)
        val inspected = PickerContextDrafts(store).open(routine)
        assertFalse(inspected.needsReconciliation)
        assertEquals(intended, inspected.remembered)
    }
}
