package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.MissedWorkDrafts
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.util.JvmTime
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
class MissedWorkFormTest {
    private val week = CivilDate(2026, 8, 17)
    private val at = JvmTime.startOfDayMillis(week.plusDays(2), "UTC") + 12 * 60 * 60_000
    private val time = FrozenTime(at, "UTC")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var deps: FakeAppDependencies
    private lateinit var root: File
    private var pause: CompletableDeferred<Unit>? = null
    @Before fun setup() { runBlocking {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), time = time)
        root = File.createTempFile("p08-form-", "").apply { delete(); mkdirs() }
        deps.plannerRepository.addTimedRule(weekday = Weekday.MONDAY, hour = 18, minute = 0,
            modality = ScheduleModality.CARDIO, nowMs = JvmTime.startOfDayMillis(week, "UTC") - 1)
        deps.plannerRepository.ensureWeek(week, "UTC", at - 1)
    } }
    @After fun close() { scope.cancel(); deps.close(); root.deleteRecursively() }
    private fun drafts() = MissedWorkDrafts(DurableDraftStore(root, Dispatchers.Unconfined), deps.dbMaintenance,
        { false }, time, { choice, start, today, minutes, zone, stamp ->
            pause?.await()
            deps.plannerRepository.previewMissedWork(choice, start, today, minutes, zone, stamp)
        }, deps.plannerRepository::inspectMissedWork, deps.plannerRepository::commitMissedWorkLocked, Dispatchers.Unconfined)

    @Test fun duplicatePreparationAndCloseAreReservedBeforeTheFirstSuspension() = runBlocking {
        pause = CompletableDeferred()
        val form = MissedWorkForm(drafts(), scope)
        assertTrue(form.prepare(MissedWorkChoice.MOVE_REMAINING, week.epochDay))
        assertTrue(form.snapshot.value.busy)
        assertFalse(form.prepare(MissedWorkChoice.SKIP_MISSED, week.epochDay))
        form.close()
        assertTrue(form.snapshot.value.visible)
        pause!!.complete(Unit)
        // Room may resume on its executor; await actual form completion, not a fixed delay.
        kotlinx.coroutines.withTimeout(5_000) {
            form.snapshot.first { !it.busy }
        }
        assertEquals(MissedWorkChoice.MOVE_REMAINING, form.snapshot.value.proposal!!.choice)
        assertTrue(form.snapshot.value.canConfirm)
        form.close()
        assertFalse(form.snapshot.value.visible)
        assertTrue(form.snapshot.value.retained)
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun reopeningIdentifiesTheRetainedPreviewWithoutOpeningAFormOrWriting() = runBlocking {
        val first = MissedWorkForm(drafts(), scope)
        first.prepare(MissedWorkChoice.KEEP_DATES, week.epochDay)
        kotlinx.coroutines.withTimeout(5_000) { first.snapshot.first { !it.busy } }
        first.close()
        val reopened = MissedWorkForm(drafts(), scope)
        reopened.readRetained()
        kotlinx.coroutines.withTimeout(5_000) { reopened.snapshot.first { !it.busy } }
        assertTrue(reopened.snapshot.value.retained)
        assertTrue(reopened.snapshot.value.resumed)
        assertFalse(reopened.snapshot.value.visible)
        assertEquals(first.snapshot.value.proposal, reopened.snapshot.value.proposal)
        assertNull(deps.plannerRepository.decisionFor(week.epochDay))
    }

    @Test fun successfulConfirmClosesThePreviewAndASecondActivationCannotSaveAgain() = runBlocking {
        val form = MissedWorkForm(drafts(), scope)
        form.prepare(MissedWorkChoice.KEEP_DATES, week.epochDay)
        kotlinx.coroutines.withTimeout(5_000) { form.snapshot.first { !it.busy } }
        val proposal = form.snapshot.value.proposal!!
        assertTrue(form.confirm())
        assertFalse(form.confirm())
        kotlinx.coroutines.withTimeout(5_000) { form.snapshot.first { !it.busy } }
        assertFalse(form.snapshot.value.visible)
        assertFalse(form.snapshot.value.retained)
        assertNull(form.snapshot.value.proposal)
        assertEquals(proposal.decision, deps.plannerRepository.decisionFor(week.epochDay))
    }
}
