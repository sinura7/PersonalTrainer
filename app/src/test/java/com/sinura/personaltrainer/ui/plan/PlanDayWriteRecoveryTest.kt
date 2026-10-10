package com.sinura.personaltrainer.ui.plan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room boundaries: admission, partial writes, exact retry and editor handoff. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanDayWriteRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val faults = PlanWriteFaults()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: PlanDayViewModel
    private val today = LocalDate.of(2026, 12, 31).toEpochDay()

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            time = FrozenTime(Instant.parse("2026-12-31T12:00:00Z").toEpochMilli(), "UTC"),
            plannerDaoDecorator = faults::decorate,
        )
        vm = PlanDayViewModel(ApplicationProvider.getApplicationContext(), deps)
        vm.uiState.awaitFirst { !it.isLoading }
        Unit
    }

    @After fun tearDown() {
        try { if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() } }
        finally {
            if (::deps.isInitialized) deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun heldLaterAddRejectsDuplicateAndUnrelatedActionsBeforeSuspensionFinishes() = runBlocking {
        val routine = deps.routineRepository.create("Thursday strength")
        faults.holdPublication()
        vm.addLaterSession(today, routine.id)
        faults.awaitPublication()
        vm.uiState.awaitFirst { it.isSaving }
        repeat(3) {
            vm.addLaterSession(today, routine.id)
            vm.buildDay(today)
            vm.addCardio(today, CardioType.WALK)
            vm.retryWrite()
        }
        assertTrue(deps.database.plannerDao().getRules().isEmpty())
        assertEquals(1, deps.routineRepository.count())
        assertEquals(0L, vm.uiState.value.completedAdd)
        assertNull(vm.navigateToEditor.value)
        faults.releasePublication()
        vm.uiState.awaitFirst { it.completedAdd == 1L && !it.isSaving }
        assertEquals(routine.id, deps.database.plannerDao().getRules().single().routineId)
        assertEquals(1, deps.plannerRepository.occurrencesBetween(today, today).size)
    }

    @Test fun pinRetryCompletesPublicationWithoutPinningTheSameWorkoutAgain() = runBlocking {
        val routine = deps.routineRepository.create("Upper")
        faults.rejectPublication = true
        vm.pinRoutine(today, routine.id)
        failed()
        val acceptedSlot = deps.scheduleRepository.slots().single()
        val acceptedRules = deps.database.plannerDao().getRules()
        vm.dismissError()
        vm.addLaterSession(today, routine.id)
        assertTrue(vm.uiState.value.canRetryWrite)
        assertEquals(listOf(acceptedSlot), deps.scheduleRepository.slots())
        vm.retryWrite()
        failed()
        assertEquals(listOf(acceptedSlot), deps.scheduleRepository.slots())
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        assertEquals(listOf(acceptedSlot), deps.scheduleRepository.slots())
        assertEquals(acceptedRules.map { it.id }, deps.database.plannerDao().getRules().map { it.id })
        assertEquals(routine.id, vm.agendaFor(today).single().rule?.routineId)
    }

    @Test fun failedNewWorkoutRetainsCreatedRoutineAndPinAndNavigatesOnlyAfterRetry() = runBlocking {
        faults.rejectPublication = true
        vm.buildDay(today)
        failed()
        val accepted = deps.routineRepository.observeAll().awaitFirst { it.size == 1 }.single()
        val slot = deps.scheduleRepository.slots().single()
        assertEquals(accepted.id, slot.routineId)
        assertNull(vm.navigateToEditor.value)
        assertEquals(0L, vm.uiState.value.completedAdd)
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        vm.navigateToEditor.awaitFirst { it == accepted.id }
        assertEquals(1, deps.routineRepository.count())
        assertEquals(listOf(slot), deps.scheduleRepository.slots())
        vm.onEditorNavigationHandled()
        assertNull(vm.navigateToEditor.value)
        vm.retryWrite()
        assertNull(vm.navigateToEditor.value)
        assertEquals(1L, vm.uiState.value.completedAdd)
    }

    @Test fun failedLaterCompositionRetainsExactNewRoutineAndRuleAcrossRetry() = runBlocking {
        faults.rejectPublication = true
        vm.composeLaterSession(today)
        failed()
        val routine = deps.routineRepository.observeAll().awaitFirst { it.size == 1 }.single()
        val rule = deps.database.plannerDao().getRules().single()
        assertEquals(routine.id, rule.routineId)
        assertNull(vm.navigateToEditor.value)
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        vm.navigateToEditor.awaitFirst { it == routine.id }
        assertEquals(1, deps.routineRepository.count())
        assertEquals(rule.id, deps.database.plannerDao().getRules().single().id)
        assertEquals(1, deps.plannerRepository.occurrencesBetween(today, today).size)
    }

    @Test fun preInsertFailureCanRetryWithoutCreatingAnyExtraRule() = runBlocking {
        val routine = deps.routineRepository.create("Lower")
        faults.rejectRuleInsert = true
        vm.addLaterSession(today, routine.id)
        failed()
        assertTrue(deps.database.plannerDao().getRules().isEmpty())
        assertTrue(deps.scheduleRepository.slots().isEmpty())
        faults.rejectRuleInsert = false
        vm.retryWrite()
        added()
        assertEquals(routine.id, deps.database.plannerDao().getRules().single().routineId)
        assertEquals(1, deps.routineRepository.count())
    }

    @Test fun laterAddRetryDoesNotMintAnotherRuleAfterTheFirstRuleAlreadySaved() = runBlocking {
        val routine = deps.routineRepository.create("Later")
        faults.rejectPublication = true
        vm.addLaterSession(today, routine.id)
        failed()
        val rule = deps.database.plannerDao().getRules().single()
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        assertEquals(listOf(rule), deps.database.plannerDao().getRules())
        vm.uiState.awaitFirst { it.occurrences.any { row -> row.localEpochDay == today && row.ruleId == rule.id } }
        assertEquals(rule.id, vm.agendaFor(today).single().rule?.id)
    }

    @Test fun removalRetryKeepsTheOtherBlockAndDoesNotRecreateTheRemovedRule() = runBlocking {
        val a = deps.plannerRepository.addTimedRule(Weekday.THURSDAY, 18, 0, ScheduleModality.CARDIO)
        val b = deps.plannerRepository.addTimedRule(Weekday.THURSDAY, 20, 0, ScheduleModality.CARDIO)
        deps.plannerRepository.publishPinnedWeek(Weekday.MONDAY, today)
        faults.rejectPublication = true
        vm.deleteSession(today, a.id)
        failed()
        assertNull(deps.plannerRepository.getRule(a.id))
        assertNotNull(deps.plannerRepository.getRule(b.id))
        faults.rejectPublication = false
        vm.retryWrite()
        vm.uiState.awaitFirst { !it.isSaving && it.error == null }
        assertEquals(listOf(b.id), deps.database.plannerDao().getRules().map { it.id })
        assertEquals(listOf(b.id), deps.plannerRepository.occurrencesBetween(today, today).map { it.ruleId })
        assertEquals(0L, vm.uiState.value.completedAdd)
    }

    @Test fun reorderFailureRollsBackAndRetryAppliesTheAcceptedOrderExactlyOnce() = runBlocking {
        val a = deps.plannerRepository.addTimedRule(Weekday.THURSDAY, 18, 0, ScheduleModality.CARDIO)
        val b = deps.plannerRepository.addTimedRule(Weekday.THURSDAY, 20, 0, ScheduleModality.CARDIO)
        deps.plannerRepository.publishPinnedWeek(Weekday.MONDAY, today)
        vm.uiState.awaitFirst { it.occurrences.count { row -> row.localEpochDay == today } == 2 }
        val beforeRules = deps.database.plannerDao().getRules()
        val beforeOccurrences = deps.database.plannerDao().getAllOccurrences()
        val agenda = vm.agendaFor(today)
        faults.rejectOrder = true
        vm.moveDayBlock(agenda, agenda.first().occurrence.id, 1)
        failed()
        assertEquals(beforeRules, deps.database.plannerDao().getRules())
        assertEquals(beforeOccurrences, deps.database.plannerDao().getAllOccurrences())
        faults.rejectOrder = false
        vm.retryWrite()
        vm.uiState.awaitFirst { !it.isSaving && it.error == null && it.rules.first { rule -> rule.id == b.id }.hour == 18 }
        assertEquals(listOf(b.id, a.id), deps.plannerRepository.occurrencesBetween(today, today).map { it.ruleId })
        assertEquals(0L, vm.uiState.value.completedAdd)
    }

    @Test fun cardioFailureDoesNotOfferAResumableWorkoutRetryOrClaimCompletion() = runBlocking {
        faults.rejectPublication = true
        vm.addCardio(today, CardioType.WALK)
        val state = vm.uiState.awaitFirst { !it.isSaving && it.error != null }
        assertFalse(state.canRetryWrite)
        assertTrue(state.error!!.contains("Check this day"))
        assertEquals(0L, state.completedAdd)
        vm.retryWrite()
        assertEquals(0, deps.database.plannerDao().getRules().size)
    }

    @Test fun pinRetryChangesOnlyItsAcceptedSlotEvenWhenAnotherRuleUsesTheSameRoutine() = runBlocking {
        val routine = deps.routineRepository.create("Upper twice")
        val existing = deps.plannerRepository.addTimedRule(Weekday.THURSDAY, 21, 0, ScheduleModality.STRENGTH, routineId = routine.id)
        faults.rejectPublication = true
        vm.pinRoutine(today, routine.id, hour = 17)
        failed()
        val slot = deps.scheduleRepository.slots().single()
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        assertEquals(existing, deps.plannerRepository.getRule(existing.id))
        assertEquals(17, deps.plannerRepository.getRule("rule-${slot.id}")!!.hour)
        assertEquals(listOf(slot), deps.scheduleRepository.slots())
    }

    @Test fun futureDayPinRetryPublishesTheSelectedWeekAndKeepsCurrentWeekRecurrence() = runBlocking {
        val future = today + 14
        val routine = deps.routineRepository.create("Future Thursday")
        faults.rejectPublication = true
        vm.pinRoutine(future, routine.id)
        failed()
        val slot = deps.scheduleRepository.slots().single()
        faults.rejectPublication = false
        vm.retryWrite()
        added()
        val selected = deps.plannerRepository.occurrencesBetween(future, future).single()
        assertEquals("rule-${slot.id}", selected.ruleId)
        assertEquals(selected.ruleId, deps.plannerRepository.occurrencesBetween(today, today).single().ruleId)
        vm.uiState.awaitFirst { it.occurrences.any { row -> row.id == selected.id } }
        assertEquals(routine.id, vm.agendaFor(future).single().rule?.routineId)
    }

    @Test fun futureLaterAddShowsTheAcceptedRuleOnItsSelectedDay() = runBlocking {
        val future = today + 14
        val routine = deps.routineRepository.create("Future later")
        vm.addLaterSession(future, routine.id)
        added()
        val rule = deps.database.plannerDao().getRules().single()
        assertEquals(rule.id, deps.plannerRepository.occurrencesBetween(future, future).single().ruleId)
        assertTrue(deps.scheduleRepository.slots().isEmpty())
    }

    private suspend fun failed() = vm.uiState.awaitFirst { !it.isSaving && it.canRetryWrite && it.error != null }
    private suspend fun added() = vm.uiState.awaitFirst { !it.isSaving && it.completedAdd == 1L && it.error == null }
}

/** Faults sit on real DAO boundaries; they never replace the application's write code. */
internal class PlanWriteFaults {
    @Volatile var rejectPublication = false
    @Volatile var rejectRuleInsert = false
    @Volatile var rejectOrder = false
    @Volatile private var publicationGate: CompletableDeferred<Unit>? = null
    private var entered = CompletableDeferred<Unit>()

    fun holdPublication() { entered = CompletableDeferred(); publicationGate = CompletableDeferred() }
    suspend fun awaitPublication() = withTimeout(TestWaits.FLOW_MS) { entered.await() }
    fun releasePublication() { publicationGate?.complete(Unit); publicationGate = null }

    fun decorate(delegate: PlannerDao): PlannerDao = object : PlannerDao by delegate {
        override suspend fun getOccurrencesBetween(start: Long, end: Long): List<ScheduleOccurrenceEntity> {
            if (rejectPublication) throw IOException("Synthetic week publication failure")
            return delegate.getOccurrencesBetween(start, end)
        }
        override suspend fun getRules(): List<ScheduleRuleEntity> {
            publicationGate?.let { gate -> entered.complete(Unit); gate.await() }
            return delegate.getRules()
        }
        override suspend fun upsertRule(row: ScheduleRuleEntity) {
            if (rejectRuleInsert) throw IOException("Synthetic rule insert failure")
            delegate.upsertRule(row)
        }
        override suspend fun upsertOccurrence(row: ScheduleOccurrenceEntity) {
            if (rejectOrder) throw IOException("Synthetic order write failure")
            delegate.upsertOccurrence(row)
        }
    }
}
