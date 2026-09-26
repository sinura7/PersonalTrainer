package com.sinura.personaltrainer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderScheduler
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.RefusingPlanLinkStore
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.workout.StartCardioOutcome
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
class PendingOccurrenceTest {
    private lateinit var deps: FakeAppDependencies
    private val weekStart = CivilDate(2026, 8, 17)

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun twoOccurrencesOnOneDateCompleteIndependently() = runBlocking {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 7,
            minute = 0,
            modality = ScheduleModality.CARDIO,
        )
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val week = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val monday = week.filter { it.localEpochDay == weekStart.epochDay }
        assertEquals(2, monday.size)
        val cardio = monday.single { it.hour == 7 }
        val strength = monday.single { it.hour == 18 }

        deps.plannerRepository.markOccurrenceDone(cardio.id, "activity-cardio", nowMs = 2L)
        PendingOccurrence.bindForSession(deps, strength.id, "session-strength")
        PendingOccurrence.complete(deps, "session-strength")

        val after = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
        assertEquals(
            mapOf(7 to OccurrenceStatus.DONE, 18 to OccurrenceStatus.DONE),
            after.associate { it.hour to it.status },
        )
        assertEquals("activity-cardio", after.single { it.hour == 7 }.completedActivityId)
        assertEquals("session-strength", after.single { it.hour == 18 }.completedActivityId)
        assertNull(deps.pendingOccurrenceId.value)
        assertNull(deps.preferencesRepository.pendingOccurrenceId.first())
    }

    @Test
    fun bindForPlannedDayMatchesThePinnedRoutine() = runBlocking {
        val routine = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        val plannedId = PendingOccurrence.plannedOccurrenceId(
            deps,
            SuggestedTrainingDay(
                epochDay = weekStart.epochDay,
                dayOfWeek = Weekday.MONDAY,
                isRest = false,
                focusKind = SessionFocusKind.PUSH,
                focusTitle = "Push",
                routineId = routine.id,
                routineName = routine.name,
                reason = "Planned.",
                emphasisMuscles = emptyList(),
                confidence = ScheduleConfidence.HIGH,
            ),
        )
        assertEquals(occ.id, plannedId)
    }

    @Test
    fun finishingADifferentSessionDoesNotConsumeTheBinding() = runBlocking {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        PendingOccurrence.bindForSession(deps, occ.id, "session-planned")

        // Tuesday's free workout finishing must not mark Wednesday's plan DONE.
        PendingOccurrence.complete(deps, "session-free")
        assertEquals(
            OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )

        PendingOccurrence.complete(deps, "session-planned")
        assertEquals(
            OccurrenceStatus.DONE,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )
    }

    @Test
    fun forgetIfSessionLeavesAnotherSessionsBindingAlone() = runBlocking {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        PendingOccurrence.bindForSession(deps, occ.id, "session-planned")
        PendingOccurrence.forgetIfSession(deps, "session-other")
        // Still armed: the right session's finish still completes it.
        PendingOccurrence.complete(deps, "session-planned")
        assertEquals(
            OccurrenceStatus.DONE,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )
        assertNull(deps.pendingOccurrenceId.value)
    }

    @Test
    fun composerTakesOnlyAComposerArm() = runBlocking {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()

        // A session-tagged binding is not the composer's to take.
        PendingOccurrence.bindForSession(deps, occ.id, "session-planned")
        assertNull(PendingOccurrence.takeForComposer(deps))
        PendingOccurrence.forget(deps)

        // A composer arm transfers exactly once.
        PendingOccurrence.bind(deps, occ.id)
        assertEquals(occ.id, PendingOccurrence.takeForComposer(deps))
        assertNull(deps.pendingOccurrenceId.value)
        assertNull(PendingOccurrence.takeForComposer(deps))
    }

    @Test
    fun restoreSurvivesADroppedInMemoryBinding() = runBlocking {
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        // A new process: the link is in the file only, and nothing this run has written yet.
        deps.preferencesRepository.setPendingOccurrenceId(occ.id)
        PendingOccurrence.restore(deps)
        assertEquals(occ.id, deps.pendingOccurrenceId.value)
        PendingOccurrence.complete(deps, "session-1")
        assertEquals(
            OccurrenceStatus.DONE,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )
    }

    /**
     * Audit UI-12: marking the planned session done is bookkeeping after a finish that has
     * already landed. Here it fails partway, the row written and its reminders' cancel
     * refused. It used to throw past that finish; now it is logged, and the link stays, since
     * nothing is lost by it and the next start replaces it.
     */
    @Test
    fun aPlanRowWhoseRemindersCannotBeCancelledDoesNotThrowAndKeepsTheLink() = runBlocking {
        val reminders = FailingCancels()
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            reminderScheduler = reminders,
        )
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        PendingOccurrence.bindForSession(deps, occ.id, "session-planned")
        reminders.failCancels = true

        PendingOccurrence.complete(deps, "session-planned")

        assertEquals("${occ.id}\nsession-planned", deps.pendingOccurrenceId.value)
    }

    /**
     * A clear whose saved copy failed must not come back from the file in the same run: the
     * composer arm would be taken twice, and a later, unrelated strength finish would mark the
     * planned day done.
     */
    @Test
    fun aClearThatCannotBeSavedIsNotReadBackInTheSameRun() = runBlocking {
        var store: RefusingPlanLinkStore? = null
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            prefsStoreDecorator = { real -> RefusingPlanLinkStore(real).also { store = it } },
        )
        deps.plannerRepository.addTimedRule(
            weekday = Weekday.MONDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
        )
        val occ = deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L).single()
        PendingOccurrence.bind(deps, occ.id)
        val refusing = checkNotNull(store)
        refusing.refuse = true

        assertEquals(occ.id, PendingOccurrence.takeForComposer(deps))
        assertTrue("the clear was never refused", refusing.refusals.get() > 0)
        assertNull(PendingOccurrence.takeForComposer(deps))
        PendingOccurrence.complete(deps, "session-unrelated")
        assertEquals(
            OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )
    }

    /**
     * Restore runs late at startup, on another thread. A link written before it lands is
     * newer than the file, here one whose save was refused, and must not be replaced by it.
     */
    @Test
    fun aRestoreThatLandsAfterALinkWasWrittenDoesNotReplaceIt() = runBlocking {
        var store: RefusingPlanLinkStore? = null
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            prefsStoreDecorator = { real -> RefusingPlanLinkStore(real).also { store = it } },
        )
        deps.preferencesRepository.setPendingOccurrenceId("occ-older")
        checkNotNull(store).refuse = true
        PendingOccurrence.bindForSession(deps, "occ-newer", "session-live")

        PendingOccurrence.restore(deps)

        assertEquals("occ-newer\nsession-live", deps.pendingOccurrenceId.value)
    }

    /**
     * Audit X6, R4: a planned day's reminder kept its Move and Skip while that day's session ran,
     * unless the session was opened from the reminder itself. Every planned strength start binds
     * here, whatever screen it came from, so the reminder showing goes on binding.
     */
    @Test
    fun bindingASessionTakesItsDaysReminderOffTheShade() = runBlocking {
        val shown = RecordingDismissals()
        deps.close()
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), reminderScheduler = shown)

        PendingOccurrence.bindForSession(deps, "occ-1", "session-1")

        assertEquals(listOf("occ-1"), shown.dismissed)
        assertEquals("occ-1\nsession-1", deps.pendingOccurrenceId.value)
    }

    /** A reminder that cannot be taken down leaves the start and its link as they are. */
    @Test
    fun aReminderThatCannotBeDismissedLeavesTheLink() = runBlocking {
        val shown = RecordingDismissals().apply { fail = true }
        deps.close()
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), reminderScheduler = shown)

        PendingOccurrence.bindForSession(deps, "occ-1", "session-1")

        assertEquals("occ-1\nsession-1", deps.pendingOccurrenceId.value)
        assertEquals("occ-1\nsession-1", deps.preferencesRepository.pendingOccurrenceId.first())
    }

    /** Audit X6, R4: what the reminder worker and the notification's actions ask. */
    @Test
    fun aDayIsTrainedNowWhileTheLiveWorkoutFollowsIt() = runBlocking {
        val live = startAWorkout()
        PendingOccurrence.bindForSession(deps, "occ-1", live)

        assertTrue(PendingOccurrence.isTrainedNow(deps, "occ-1"))
        assertFalse("another planned day is not being trained", PendingOccurrence.isTrainedNow(deps, "occ-2"))
    }

    @Test
    fun aFreeWorkoutTrainsNoPlannedDay() = runBlocking {
        startAWorkout()

        assertFalse(PendingOccurrence.isTrainedNow(deps, "occ-1"))
    }

    /** A link left from a session that is no longer live trains nothing. */
    @Test
    fun aLinkToASessionNoLongerLiveTrainsNothing() = runBlocking {
        PendingOccurrence.bindForSession(deps, "occ-1", "session-finished")

        assertFalse(PendingOccurrence.isTrainedNow(deps, "occ-1"))
    }

    @Test
    fun liveCardioTrainsTheDayItCarries() = runBlocking {
        val started = deps.startLiveCardio(type = CardioType.RUN, now = JvmTime.captureNow(), occurrenceId = "occ-1")
        assertTrue("the cardio did not start: $started", started is StartCardioOutcome.Open)

        assertTrue(PendingOccurrence.isTrainedNow(deps, "occ-1"))
        assertFalse(PendingOccurrence.isTrainedNow(deps, "occ-2"))
    }

    /** A read that fails says no, and the reminder behaves as it did before R4. */
    @Test
    fun aFailedReadSaysTheDayIsNotTrained() = runBlocking {
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real -> ThrowingInProgressReads(real) },
        )

        assertFalse(PendingOccurrence.isTrainedNow(deps, "occ-1"))
    }

    private suspend fun startAWorkout(): String =
        (deps.workoutRepository.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session.id

    /** The live workout cannot be read, as a failing disk would leave it. */
    private class ThrowingInProgressReads(delegate: WorkoutDao) : WorkoutDao by delegate {
        override suspend fun getInProgressSession(): WorkoutSessionEntity? = error("boom: the live workout cannot be read")
    }

    /** Records which planned days' reminders were taken off the screen; can refuse to. */
    private class RecordingDismissals : ReminderScheduler {
        val dismissed = mutableListOf<String>()
        @Volatile var fail = false

        override fun schedule(delivery: ReminderDelivery) = Unit

        override fun cancel(deliveryId: String) = Unit

        override fun cancelForOccurrence(occurrenceId: String) = Unit

        override fun dismissShown(occurrenceId: String) {
            if (fail) error("boom: the reminder for $occurrenceId could not be dismissed")
            dismissed += occurrenceId
        }
    }

    /** Reminders whose cancel throws once switched on, as WorkManager refusing one would. */
    private class FailingCancels : ReminderScheduler {
        @Volatile var failCancels = false

        override fun schedule(delivery: ReminderDelivery) = Unit

        override fun cancel(deliveryId: String) = Unit

        override fun cancelForOccurrence(occurrenceId: String) {
            if (failCancels) error("boom: the reminders for $occurrenceId could not be cancelled")
        }

        override fun dismissShown(occurrenceId: String) = Unit
    }
}
