package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.domain.Weekday
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StartOccurrenceTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun missingOccurrenceIsMissing() = runBlocking {
        assertEquals(StartOccurrenceOutcome.Missing, deps.startOccurrence("gone"))
    }

    @Test
    fun strengthOccurrenceStartsThePinnedRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        deps.scheduleRepository.pin(fixture.routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()

        val outcome = deps.startOccurrence(occurrence.id)

        val open = outcome as StartOccurrenceOutcome.OpenWorkout
        assertEquals(occurrence.id, open.occurrenceId)
        assertEquals(fixture.routine.id, deps.workoutRepository.getSession(open.sessionId)?.routineId)
    }

    @Test
    fun scheduledRideStartsAsRideNotRun() = runBlocking {
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        deps.plannerRepository.addTimedRule(
            weekday = weekday,
            hour = 7,
            minute = 0,
            modality = ScheduleModality.CARDIO,
            templateId = ScheduleKind.cardio(CardioType.RIDE),
            nowMs = 1_700_000_000_000L,
        )
        deps.plannerRepository.ensureWeek(weekStart)
        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()

        val outcome = deps.startOccurrence(occurrence.id)

        val open = outcome as StartOccurrenceOutcome.OpenCardio
        val session = checkNotNull(deps.activityRepository.get(open.sessionId))
        assertEquals(CardioType.RIDE, session.cardioBlocks.single().type)
        assertEquals("Ride", session.title)
        assertEquals(open.sessionId, deps.cardioTimerPersistence.load()?.sessionId)
    }

    @Test
    fun mixedOccurrenceOpensTheComposer() = runBlocking {
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        deps.plannerRepository.addTimedRule(
            weekday = weekday,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.MIXED,
            nowMs = 1_700_000_000_000L,
        )
        deps.plannerRepository.ensureWeek(weekStart)
        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()

        val outcome = deps.startOccurrence(occurrence.id)

        val composer = outcome as StartOccurrenceOutcome.OpenComposer
        assertEquals(occurrence.id, composer.occurrenceId)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(deps.activityRepository.getLive())
    }

    @Test
    fun liveSessionBlocksAStrengthOccurrence() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val squat = insertTestExercise(deps, "occ-block-squat", "Squat")
        val routine = deps.routineRepository.create("Push")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()

        val outcome = deps.startOccurrence(occurrence.id)

        val blocked = outcome as StartOccurrenceOutcome.Blocked
        assertEquals(fixture.session.id, blocked.inProgressSessionId)
        assertEquals(occurrence.id, blocked.occurrenceId)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun freeCardioUsesTheSharedRitual() = runBlocking {
        val now = com.sinura.personaltrainer.util.JvmTime.captureNow()
        val outcome = deps.startLiveCardio(
            type = CardioType.RUN,
            now = now,
            title = "Cardio",
        )
        val open = outcome as StartCardioOutcome.Open
        val session = checkNotNull(deps.activityRepository.get(open.sessionId))
        assertEquals("Cardio", session.title)
        assertEquals(CardioType.RUN, session.cardioBlocks.single().type)
        assertEquals(open.sessionId, deps.cardioTimerPersistence.load()?.sessionId)
    }
}
