package com.sinura.personaltrainer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        PendingOccurrence.bind(deps, occ.id)
        deps.pendingOccurrenceId.value = null
        PendingOccurrence.restore(deps)
        assertEquals(occ.id, deps.pendingOccurrenceId.value)
        PendingOccurrence.complete(deps, "session-1")
        assertEquals(
            OccurrenceStatus.DONE,
            deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay)
                .single().status,
        )
    }
}
