package com.sinura.personaltrainer.data.draft

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.util.JvmTime
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MissedWorkDraftsTest {
    private val week = CivilDate(2026, 8, 17)
    private val at = JvmTime.startOfDayMillis(week.plusDays(2), "UTC") + 12 * 60 * 60_000
    private val time = FrozenTime(at, "UTC")
    private lateinit var deps: FakeAppDependencies
    private lateinit var directory: File
    private var writes = 0
    private var failBeforeWrite = false
    private var loseAck = false
    private var failRead = false
    private var restore = false
    @Before fun setup() {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), time = time)
        directory = File.createTempFile("p08-draft-", "").apply { delete(); mkdirs() }
    }
    @After fun close() { deps.close(); directory.deleteRecursively() }
    private fun service() = MissedWorkDrafts(DurableDraftStore(directory, Dispatchers.Unconfined), deps.dbMaintenance,
        { restore }, time, deps.plannerRepository::previewMissedWork,
        { if (failRead) throw IOException("synthetic unavailable read") else deps.plannerRepository.inspectMissedWork(it) },
        { if (failBeforeWrite) throw IOException("synthetic refused write")
          writes++; deps.plannerRepository.commitMissedWorkLocked(it)
          if (loseAck) throw IOException("synthetic lost acknowledgement") }, Dispatchers.Unconfined)
    private suspend fun seed() {
        deps.plannerRepository.addTimedRule(weekday = Weekday.MONDAY, hour = 18, minute = 0,
            modality = ScheduleModality.CARDIO, nowMs = JvmTime.startOfDayMillis(week, "UTC") - 1)
        deps.plannerRepository.ensureWeek(week, "UTC", at - 1)
    }

    @Test fun reopenRestoresTheExactPreviewWithoutWritingAndDiscardIsPrivate() = runBlocking {
        seed()
        val first = service()
        val handle = first.open()
        first.propose(handle, MissedWorkChoice.MOVE_REMAINING, week.epochDay)
        val proposed = handle.proposal
        val reopened = service()
        val retained = reopened.open()
        assertEquals(proposed, retained.proposal)
        assertEquals(0, writes)
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
        reopened.discard(retained)
        assertNull(reopened.open().proposal)
        assertEquals(proposed!!.occurrencesBefore, deps.plannerRepository.occurrencesBetween(week.epochDay, week.epochDay + 6))
    }

    @Test fun failedSaveRetainsImmutableCommandAndRetryCommitsItOnce() = runBlocking {
        seed()
        val first = service()
        val handle = first.open()
        first.propose(handle, MissedWorkChoice.MOVE_REMAINING, week.epochDay)
        val proposed = handle.proposal
        failBeforeWrite = true
        assertEquals(DraftRecoveryOutcome.UNCOMMITTED, first.confirm(handle))
        assertTrue(handle.pending)
        val admitted = handle.checkpoint!!.pending!!.identity
        failBeforeWrite = false
        val second = service()
        val recovered = second.open()
        assertEquals(admitted, recovered.checkpoint!!.pending!!.identity)
        assertEquals(proposed, recovered.proposal)
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, second.confirm(recovered))
        assertEquals(1, writes)
        assertNull(second.open().proposal)
        assertEquals(proposed!!.decision, deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun lostBusinessAcknowledgementSettlesByExactReadbackWithoutReplay() = runBlocking {
        seed()
        val drafts = service()
        val handle = drafts.open()
        drafts.propose(handle, MissedWorkChoice.SKIP_MISSED, week.epochDay)
        val intended = handle.proposal
        loseAck = true
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, drafts.confirm(handle))
        assertEquals(1, writes)
        assertNull(service().open().proposal)
        assertEquals(intended!!.decision, deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun unavailableInspectionAndChangedScheduleNeverAuthorizeReplay() = runBlocking {
        seed()
        val drafts = service()
        val handle = drafts.open()
        drafts.propose(handle, MissedWorkChoice.MOVE_REMAINING, week.epochDay)
        val proposed = handle.proposal
        failRead = true
        assertEquals(DraftRecoveryOutcome.UNAVAILABLE, drafts.confirm(handle))
        assertEquals(0, writes)
        assertEquals(proposed, handle.proposal)
        failRead = false
        val rule = deps.plannerRepository.rules().single()
        deps.plannerRepository.upsertRule(rule.copy(hour = 9, updatedAtMs = at + 1))
        assertEquals(DraftRecoveryOutcome.CONFLICT, drafts.confirm(handle))
        assertEquals(0, writes)
        assertEquals(proposed, handle.proposal)
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun restoreCoordinatorBlocksWritesAndASecondVisitRetiresTheOldHandle() = runBlocking {
        seed()
        val drafts = service()
        val old = drafts.open()
        drafts.propose(old, MissedWorkChoice.KEEP_DATES, week.epochDay)
        val current = drafts.open()
        assertTrue(runCatching { drafts.confirm(old) }.isFailure)
        restore = true
        assertTrue(runCatching { drafts.confirm(current) }.isFailure)
        assertEquals(0, writes)
        restore = false
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, drafts.confirm(current))
        assertEquals(1, writes)
    }

    @Test fun reviewingAConflictKeepsTheOldProposalUntilReadableAndDoesNotCommitTheNewOne() = runBlocking {
        seed()
        val drafts = service()
        val handle = drafts.open()
        drafts.propose(handle, MissedWorkChoice.MOVE_REMAINING, week.epochDay)
        val original = handle.proposal
        failRead = true
        assertEquals(DraftRecoveryOutcome.UNAVAILABLE, drafts.confirm(handle))
        assertTrue(runCatching { drafts.reviewCurrent(handle, week.epochDay) }.isFailure)
        assertEquals(original, handle.proposal)
        assertTrue(handle.pending)
        failRead = false
        val rule = deps.plannerRepository.rules().single()
        deps.plannerRepository.upsertRule(rule.copy(hour = 9, updatedAtMs = at + 1))
        assertEquals(DraftRecoveryOutcome.CONFLICT, drafts.confirm(handle))
        drafts.reviewCurrent(handle, week.epochDay)
        assertFalse(handle.pending)
        assertEquals(9, handle.proposal!!.rulesBefore.single().hour)
        assertEquals(0, writes)
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
        assertEquals(DraftRecoveryOutcome.EXACT_SAVED, drafts.confirm(handle))
        assertEquals(1, writes)
    }

    @Test fun privateCodecRejectsUnknownFieldsAndFabricatedResults() = runBlocking {
        seed()
        val drafts = service()
        val handle = drafts.open()
        drafts.propose(handle, MissedWorkChoice.KEEP_DATES, week.epochDay)
        val codec = MissedWorkCodec(time)
        val good = codec.encodeDraft(handle.proposal!!)
        assertEquals(handle.proposal, codec.decodeDraft(good))
        val unknown = good.deepCopy().asJsonObject.apply { addProperty("newOwner", "other") }
        assertTrue(runCatching { codec.decodeDraft(unknown) }.isFailure)
        val fabricated = handle.proposal!!.copy(occurrencesAfter = emptyList())
        assertTrue(runCatching { codec.decodeDraft(codec.encodeDraft(fabricated)) }.isFailure)
    }
}
