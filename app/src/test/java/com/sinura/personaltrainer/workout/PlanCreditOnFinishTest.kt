package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.PlanCreditApplyResult
import com.sinura.personaltrainer.domain.PlanCreditResult
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.data.repository.AuxiliaryBlocks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanCreditOnFinishTest {
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
    fun autoCreditsWhenRoutineMatches() = runBlocking {
        val routine = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val session = finishSession(routine.id, "Push", "s-free")
        val result = PlanCreditOnFinish.afterFinish(deps, session, boundOccurrenceId = null, weekStart.epochDay)
        assertTrue(result is PlanCreditResult.Credited)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        assertEquals(OccurrenceStatus.DONE, occ.status)
        assertEquals("s-free", occ.completedActivityId)
    }

    @Test
    fun boundOccurrenceDoesNotMarkAgain() = runBlocking {
        val routine = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        deps.plannerRepository.markOccurrenceDone(occ.id, "other-session")
        val session = finishSession(routine.id, "Push", "s-other")
        val result = PlanCreditOnFinish.afterFinish(deps, session, boundOccurrenceId = occ.id, weekStart.epochDay)
        assertTrue(result is PlanCreditResult.Credited)
        val after = deps.plannerRepository.getOccurrence(occ.id)!!
        assertEquals("other-session", after.completedActivityId)
    }

    @Test
    fun mismatchReturnsNeedsChoice() = runBlocking {
        val push = deps.routineRepository.create("Push")
        val pull = deps.routineRepository.create("Pull")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val session = finishSession(pull.id, "Pull", "s-pull")
        val result = PlanCreditOnFinish.afterFinish(deps, session, boundOccurrenceId = null, weekStart.epochDay)
        assertTrue(result is PlanCreditResult.NeedsChoice)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        assertEquals(OccurrenceStatus.PLANNED, occ.status)
    }

    @Test
    fun undoReopensTheDay() = runBlocking {
        val routine = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(routine.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val session = finishSession(routine.id, "Push", "s-undo")
        PlanCreditOnFinish.afterFinish(deps, session, null, weekStart.epochDay)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        PlanCreditOnFinish.undoCredit(deps, occ.id, session.id)
        val reopened = deps.plannerRepository.getOccurrence(occ.id)!!
        assertEquals(OccurrenceStatus.PLANNED, reopened.status)
        assertEquals(null, reopened.completedActivityId)
    }

    @Test
    fun packReturnsNone() = runBlocking {
        val push = deps.routineRepository.create("Push")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        seedCatalog(AuxiliaryPacks.Stretch)
        val packRoutineId = AuxiliaryBlocks.ensureRoutine(
            pack = AuxiliaryPacks.Stretch,
            routines = deps.routineRepository,
            exercises = deps.exerciseRepository,
        )
        val session = finishSession(packRoutineId, AuxiliaryPacks.Stretch.title, "s-pack")
        val result = PlanCreditOnFinish.afterFinish(deps, session, null, weekStart.epochDay)
        assertEquals(PlanCreditResult.None, result)
    }

    @Test
    fun restDayCreditsNothing() = runBlocking {
        val session = finishSession(null, "Free", "s-alone")
        val result = PlanCreditOnFinish.afterFinish(deps, session, null, weekStart.epochDay)
        assertEquals(PlanCreditResult.None, result)
    }

    @Test
    fun applyChoiceDoesNotOverwriteAlreadyDone() = runBlocking {
        val push = deps.routineRepository.create("Push")
        val pull = deps.routineRepository.create("Pull")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        deps.plannerRepository.markOccurrenceDone(occ.id, "first-session")
        val outcome = PlanCreditOnFinish.applyChoice(deps, occ.id, "second-session", countForPlan = true)
        assertEquals(PlanCreditApplyResult.AlreadyDone, outcome)
        assertEquals("first-session", deps.plannerRepository.getOccurrence(occ.id)!!.completedActivityId)
    }

    @Test
    fun keepSeparatePersistsPerSession() = runBlocking {
        val push = deps.routineRepository.create("Push")
        val pull = deps.routineRepository.create("Pull")
        deps.scheduleRepository.pin(push.id, null, Weekday.MONDAY)
        deps.plannerRepository.importSlotsIfNeeded()
        deps.plannerRepository.ensureWeek(weekStart, "UTC", 1L)
        val session = finishSession(pull.id, "Pull", "s-keep")
        val occ = deps.plannerRepository.occurrencesBetween(weekStart.epochDay, weekStart.epochDay).single()
        val apply = PlanCreditOnFinish.applyChoice(deps, occ.id, session.id, countForPlan = false)
        assertEquals(PlanCreditApplyResult.Declined, apply)
        assertTrue(deps.preferencesRepository.isPlanCreditKeptSeparate(session.id))
        val ui = PlanCreditSummary.resolve(deps, session, weekStart.epochDay)
        assertEquals(null, ui)
    }

    private suspend fun seedCatalog(pack: com.sinura.personaltrainer.domain.AuxiliaryPack) {
        val ids = pack.lifts.map { it.exerciseId }
        val existing = deps.database.exerciseDao().getAll().map { it.id }.toSet()
        ids.filterNot { it in existing }.forEach { id ->
            deps.database.exerciseDao().insertAll(
                listOf(
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = id,
                        name = id,
                        muscleGroup = "Core",
                        notes = "",
                        isCustom = false,
                    ),
                ),
            )
        }
    }

    private fun finishSession(routineId: String?, routineName: String, sessionId: String): WorkoutSession =
        WorkoutSession(
            id = sessionId,
            routineId = routineId,
            routineName = routineName,
            date = 0L,
            notes = "",
            durationMinutes = 30,
            startedAt = 0L,
            finishedAt = 1L,
            exercises = emptyList(),
            sets = listOf(
                SetLog("l1", sessionId, "ex", "Lift", 1, 60.0, 8, null, false, 0L),
            ),
        )
}
