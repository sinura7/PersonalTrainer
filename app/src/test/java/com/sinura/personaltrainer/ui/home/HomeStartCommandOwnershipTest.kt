package com.sinura.personaltrainer.ui.home

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.workout.DiscardWorkout
import com.sinura.personaltrainer.workout.StartOccurrence
import com.sinura.personaltrainer.workout.StartTrainingDay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Command ownership uses real repositories and isolated, undecorated Room inventories. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeStartCommandOwnershipTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val writes = StartWrites()
    private val reads = RoutineReads()
    private lateinit var base: FakeAppDependencies
    private lateinit var vm: HomeViewModel
    private lateinit var selected: Routine
    private lateinit var other: Routine

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        writes.gate?.release?.complete(Unit)
        try {
            if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() }
        } finally {
            if (::base.isInitialized) {
                base.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                base.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test fun anImmediateFailureRefusesTheSameCapturedStartUntilExplicitRetry() = runBlocking {
        createGraph()
        val before = inventory()
        reads.failNext.set(1)
        val oldStart = { vm.startRoutine(routineId = selected.id) }

        assertTrue(oldStart())
        // Main's unconfined test dispatcher enters this launch immediately. The decorated
        // lookup throws before its first suspension; these callbacks share one old frame.
        assertFalse(vm.startState.value.pending)
        assertTrue(vm.startState.value.canRetry)
        assertTrue(vm.startState.value.error != null)
        assertFalse(oldStart())
        assertFalse(vm.startRoutine(routineId = other.id))
        assertFalse(vm.startFreeWorkout())
        assertEquals(1, reads.attempts.get())
        assertEquals(0, writes.insertAttempts.get())
        assertEquals(before, inventory())

        assertTrue(vm.retryStart())
        val opened = awaitWorkoutOpen()
        assertEquals(2, reads.attempts.get())
        assertEquals(1, writes.insertAttempts.get())
        assertSelectedRoutine(sessionId = opened)
        // A settled successful outcome still owns admission until navigation is acknowledged.
        assertFalse(vm.canDismissStart())
        assertFalse(vm.dismissStart())
        assertFalse(oldStart())
        assertEquals(opened, vm.navigateToSession.value)
        val openedAttempt = vm.startState.value.attemptId
        vm.onSessionNavigationHandled()
        assertEquals(HomeStartState(attemptId = openedAttempt), vm.startState.value)
    }

    @Test fun aRenderedRetryCannotRetryTheNextImmediateFailureOrDismissItsOwner() = runBlocking {
        createGraph()
        val before = inventory()
        reads.failNext.set(1)
        assertTrue(vm.startRoutine(routineId = selected.id))
        assertTrue(vm.startState.value.canRetry)
        assertFalse(vm.startState.value.pending)
        val firstAttempt = vm.startState.value.attemptId
        val oldRetry = { vm.retryStart(expectedAttemptId = firstAttempt) }

        reads.failNext.set(1)
        assertTrue(oldRetry())
        assertTrue(vm.startState.value.canRetry)
        assertFalse(vm.startState.value.pending)
        val secondAttempt = vm.startState.value.attemptId
        assertTrue(secondAttempt > firstAttempt)
        val secondFailure = vm.startState.value
        assertFalse(oldRetry())
        assertFalse(vm.dismissStart(expectedAttemptId = firstAttempt))
        assertEquals(secondFailure, vm.startState.value)
        assertEquals(2, reads.attempts.get())
        assertEquals(0, writes.insertAttempts.get())
        assertEquals(before, inventory())
        assertNull(vm.navigateToSession.value)

        assertTrue(vm.retryStart(expectedAttemptId = secondAttempt))
        val opened = awaitWorkoutOpen()
        assertTrue(vm.startState.value.attemptId > secondAttempt)
        assertEquals(3, reads.attempts.get())
        assertEquals(1, writes.insertAttempts.get())
        assertSelectedRoutine(sessionId = opened)
    }

    @Test fun actionsCapturedForBlockerACannotActOnTheNewBlockerB() = runBlocking {
        createGraph()
        val originalA = base.workoutRepository.startFreeWorkout(focusTitle = "Original A")
        assertTrue(vm.startRoutine(routineId = selected.id))
        awaitBlocked(sessionId = originalA.id)
        val blockerA = checkNotNull(vm.blockedByInProgress.value)
        val attemptA = vm.startState.value.attemptId
        val oldResume = { vm.resumeBlocked(expectedBlocked = blockerA, expectedAttemptId = attemptA) }
        val oldDiscard = { vm.discardBlockedAndStart(expectedBlocked = blockerA, expectedAttemptId = attemptA) }
        val oldCancel = { vm.dismissBlockedStart(expectedBlocked = blockerA, expectedAttemptId = attemptA) }

        vm.dismissBlockedStart(expectedBlocked = blockerA, expectedAttemptId = attemptA)
        assertNull(vm.blockedByInProgress.value)
        assertEquals(HomeStartState(attemptId = attemptA), vm.startState.value)
        assertEquals(1, base.database.workoutDao().deleteInProgressSession(id = originalA.id))
        val originalB = base.workoutRepository.startFreeWorkout(focusTitle = "Original B")
        assertTrue(vm.startRoutine(routineId = other.id))
        awaitBlocked(sessionId = originalB.id)
        val blockerB = checkNotNull(vm.blockedByInProgress.value)
        val attemptB = vm.startState.value.attemptId
        assertNotEquals(blockerA, blockerB)
        assertTrue(attemptB > attemptA)
        val before = inventory()

        oldResume()
        assertFalse(oldDiscard())
        oldCancel()
        // Each guard also refuses a stale owner even if a caller names the current attempt.
        vm.resumeBlocked(expectedBlocked = blockerA, expectedAttemptId = attemptB)
        assertFalse(vm.discardBlockedAndStart(expectedBlocked = blockerA, expectedAttemptId = attemptB))
        vm.dismissBlockedStart(expectedBlocked = blockerA, expectedAttemptId = attemptB)
        assertFalse(vm.discardBlockedAndStart(expectedBlocked = blockerB, expectedAttemptId = attemptA))
        assertEquals(blockerB, vm.blockedByInProgress.value)
        assertEquals(attemptB, vm.startState.value.attemptId)
        assertEquals(before, inventory())
        assertEquals(originalB.id, before.sessions.single().id)
        assertEquals(0, writes.discardAttempts.get())
        assertEquals(0, writes.insertAttempts.get())
        assertNull(vm.navigateToSession.value)

        vm.resumeBlocked(expectedBlocked = blockerB, expectedAttemptId = attemptB)
        assertEquals(originalB.id, vm.navigateToSession.value)
        assertNull(vm.blockedByInProgress.value)
        assertEquals(HomeStartState(attemptId = attemptB), vm.startState.value)
        assertEquals(before, inventory())
    }

    @Test fun anAcknowledgedDiscardRetriesOnlyTheFailedReplacementAndCannotResume() = runBlocking {
        createGraph()
        val original = base.workoutRepository.startFreeWorkout(focusTitle = "Original workout")
        val before = inventory()
        assertTrue(vm.startRoutine(routineId = selected.id))
        awaitBlocked(sessionId = original.id)
        assertFalse(vm.startFreeWorkout())
        assertFalse(vm.startRoutine(routineId = other.id))
        assertEquals(before, inventory())

        writes.failNextInsert.set(1)
        assertTrue(vm.discardBlockedAndStart())
        awaitFailed()
        assertEquals(1, writes.discardAttempts.get())
        assertEquals(1, writes.insertAttempts.get())
        assertTrue(vm.startState.value.discardedOriginal)
        assertFalse(vm.canResumeBlockedStart())
        vm.resumeBlocked() // A captured old Resume callback must also be refused by the owner.
        assertNull(vm.navigateToSession.value)
        assertTrue(inventory().sessions.isEmpty())
        assertTrue(inventory().exercises.isEmpty())
        assertTrue(inventory().sets.isEmpty())
        assertEquals(original.id, vm.blockedByInProgress.value?.sessionId)

        val gate = InsertGate().also { writes.gate = it }
        assertTrue(vm.retryStart())
        withTimeout(TestWaits.FLOW_MS) { gate.entered.await() }
        assertTrue(vm.startState.value.pending)
        assertTrue(vm.startState.value.discardedOriginal)
        assertFalse(vm.retryStart())
        assertFalse(vm.discardBlockedAndStart())
        assertFalse(vm.dismissStart())
        vm.resumeBlocked()
        assertNull(vm.navigateToSession.value)
        assertEquals(1, writes.discardAttempts.get())
        gate.release.complete(Unit)

        val opened = awaitWorkoutOpen()
        assertNotEquals(original.id, opened)
        assertEquals(1, writes.discardAttempts.get())
        assertEquals(2, writes.insertAttempts.get())
        assertNull(vm.blockedByInProgress.value)
        assertSelectedRoutine(sessionId = opened)
    }

    @Test fun aBlockedFreeWorkoutStaysEmptyFreeWorkAfterDiscardRatherThanSuggestedFocus() = runBlocking {
        createGraph()
        val original = base.workoutRepository.startFreeWorkout(focusTitle = "Original workout")
        assertTrue(vm.startFreeWorkout())
        awaitBlocked(sessionId = original.id)
        assertTrue(vm.discardBlockedAndStart())
        val opened = awaitWorkoutOpen()

        val saved = inventory()
        val row = saved.sessions.single()
        assertEquals(opened, row.id)
        assertNotEquals(original.id, row.id)
        assertNull(row.routineId)
        assertEquals("Free workout", row.routineName)
        // A generic SuggestedTrainingDay would manufacture "Suggested focus: Free workout".
        assertEquals("", row.notes)
        assertEquals(NOW_MS, row.date)
        assertEquals(NOW_MS, row.startedAt)
        assertNull(row.finishedAt)
        assertTrue(saved.exercises.isEmpty())
        assertTrue(saved.sets.isEmpty())
        assertEquals(1, writes.discardAttempts.get())
        assertEquals(1, writes.insertAttempts.get())
        assertNoOtherLaneWrites()
    }

    @Test fun aFailedDiscardKeepsTheOriginalResumeChoiceUntilDiscardIsAcknowledged() = runBlocking {
        createGraph()
        val original = base.workoutRepository.startFreeWorkout(focusTitle = "Original workout")
        assertTrue(vm.startRoutine(routineId = selected.id))
        awaitBlocked(sessionId = original.id)
        val before = inventory()
        writes.failNextDiscard.set(1)

        assertTrue(vm.discardBlockedAndStart())
        awaitFailed()
        assertFalse(vm.startState.value.discardedOriginal)
        assertTrue(vm.canResumeBlockedStart())
        assertEquals(original.id, vm.blockedByInProgress.value?.sessionId)
        assertEquals(before, inventory())
        assertEquals(1, writes.discardAttempts.get())
        assertEquals(0, writes.insertAttempts.get())
        assertNull(vm.navigateToSession.value)

        assertTrue(vm.retryStart())
        val opened = awaitWorkoutOpen()
        assertNotEquals(original.id, opened)
        assertEquals(2, writes.discardAttempts.get())
        assertEquals(1, writes.insertAttempts.get())
        assertSelectedRoutine(sessionId = opened)
    }

    @Test fun aRejectedCardioStartRetriesTheSelectedRideExplicitlyAfterTheBlockerLeaves() = runBlocking {
        createGraph()
        val original = base.workoutRepository.startFreeWorkout(focusTitle = "Original workout")
        val before = inventory()
        assertTrue(vm.startCardio(type = CardioType.RIDE))
        awaitFailed()
        assertNull(vm.blockedByInProgress.value)
        assertNull(vm.navigateToCardio.value)
        assertEquals(before, inventory())
        assertTrue(base.database.activityDao().getAllGraphs().isEmpty())
        assertFalse(vm.startCardio(type = CardioType.RUN))
        assertFalse(vm.startCardio(type = CardioType.RIDE))

        // This independent finish/discard is outside the Home attempt. Its retained choice
        // must stay Ride even if an old Run callback arrives before the deliberate Retry.
        assertEquals(1, base.database.workoutDao().deleteInProgressSession(id = original.id))
        vm.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && it.inProgress == null }
        assertTrue(vm.retryStart())
        vm.startState.awaitFirst { !it.pending && vm.navigateToCardio.value != null }
        val opened = checkNotNull(vm.navigateToCardio.value)

        val activity = base.database.activityDao().getAllGraphs().single()
        assertEquals(opened, activity.session.id)
        assertEquals("Ride", activity.session.title)
        assertEquals("ACTIVE", activity.session.status)
        assertEquals("LIVE", activity.session.origin)
        assertEquals("LIVE", activity.session.liveToken)
        assertEquals(NOW_MS, activity.session.performedStartInstantMs)
        assertNull(activity.session.occurrenceId)
        val block = activity.blocks.single()
        assertEquals(opened, block.block.sessionId)
        assertEquals("CARDIO", block.block.kind)
        assertEquals(CardioType.RIDE.name, block.block.cardioType)
        assertEquals(0L, block.block.elapsedSeconds)
        assertTrue(block.strengthSets.isEmpty())
        assertTrue(block.cardioIntervals.isEmpty())
        assertTrue(inventory().sessions.isEmpty())
        assertTrue(inventory().exercises.isEmpty())
        assertTrue(inventory().sets.isEmpty())
        assertEquals(0, writes.insertAttempts.get())
        assertEquals(0, writes.discardAttempts.get())
        assertFalse(vm.startCardio(type = CardioType.RUN))
        assertFalse(vm.dismissStart())
        assertNull(base.pendingOccurrenceId.value)
        assertTrue(base.database.plannerDao().getAllOccurrences().isEmpty())
        val openedAttempt = vm.startState.value.attemptId
        vm.onCardioNavigationHandled()
        assertEquals(HomeStartState(attemptId = openedAttempt), vm.startState.value)
    }

    @Test fun aMovedLeftoverCannotRetryItsOldCopyAfterTheLocalDayChanges() = runBlocking {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
            // Keep one injected instant for the entire attempt; only the device's zone changes.
            val travelingClock = deviceZoneClock()
            createGraph(clock = travelingClock)
            val firstToday = todayEpochDay(nowMs = travelingClock.nowMillis(), time = travelingClock)
            val yesterday = firstToday - 1L
            base.scheduleRepository.pin(
                routineId = selected.id, focusKind = null, anchorDay = Weekday.fromEpochDay(yesterday),
            )
            base.plannerRepository.importSlotsIfNeeded(nowMs = NOW_MS)
            val importedRule = base.database.plannerDao().getRules().single { it.routineId == selected.id }
            base.database.plannerDao().upsertRule(row = importedRule.copy(
                createdAtMs = travelingClock.startOfDayMillis(
                    date = CivilDate.fromEpochDay(yesterday), zoneId = travelingClock.defaultZoneId(),
                ) - 1L,
            ))
            base.plannerRepository.ensureWeek(
                weekStart = CivilDate.fromEpochDay(yesterday).previousOrSame(Weekday.MONDAY),
            )
            val original = base.plannerRepository.occurrencesBetween(
                startEpochDay = yesterday, endEpochDay = yesterday,
            ).single()
            val rulesBefore = base.database.plannerDao().getRules()
            writes.failNextInsert.set(1)

            assertTrue(vm.startOccurrence(occurrenceId = original.id))
            awaitFailed()
            val movedRows = base.database.plannerDao().getAllOccurrences()
            assertEquals(OccurrenceStatus.MOVED.name, movedRows.single { it.id == original.id }.status)
            val resolved = movedRows.single { it.id != original.id && it.localEpochDay == firstToday }
            assertEquals(OccurrenceStatus.PLANNED.name, resolved.status)
            assertEquals(original.id, vm.startState.value.occurrenceId)
            assertEquals(1, writes.insertAttempts.get())
            assertEquals(rulesBefore, base.database.plannerDao().getRules())
            val workBeforeExpiry = inventory()
            val deliveriesBeforeExpiry = base.database.plannerDao().getDeliveries()
            assertTrue(workBeforeExpiry.sessions.isEmpty())
            assertNull(base.pendingOccurrenceId.value)

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val nextToday = todayEpochDay(nowMs = travelingClock.nowMillis(), time = travelingClock)
            assertTrue("travel advanced the local day at the same injected instant", nextToday >= firstToday + 1L)
            assertEquals(NOW_MS, travelingClock.nowMillis())
            assertTrue(vm.retryStart())
            vm.startState.awaitFirst { !it.pending && it.error != null && !it.canRetry }

            assertEquals("The day has changed. Close this start and choose the workout again.", vm.startState.value.error)
            assertEquals(original.id, vm.startState.value.occurrenceId)
            assertFalse(vm.retryStart())
            assertEquals(1, writes.insertAttempts.get())
            assertEquals(0, writes.discardAttempts.get())
            assertEquals(workBeforeExpiry, inventory())
            assertEquals(rulesBefore, base.database.plannerDao().getRules())
            assertEquals(movedRows, base.database.plannerDao().getAllOccurrences())
            assertEquals(deliveriesBeforeExpiry, base.database.plannerDao().getDeliveries())
            assertTrue(base.database.activityDao().getAllGraphs().isEmpty())
            assertNull(vm.navigateToSession.value)
            assertNull(vm.navigateToCardio.value)
            assertNull(vm.navigateToComposer.value)
            assertNull(vm.blockedByInProgress.value)
            assertNull(base.pendingOccurrenceId.value)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test fun anExpiredMovedChoiceRefusesAnotherDiscardAndPreservesTheOriginalWorkout() = runBlocking {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+12"))
            val travelingClock = deviceZoneClock()
            createGraph(clock = travelingClock)
            val firstToday = todayEpochDay(nowMs = travelingClock.nowMillis(), time = travelingClock)
            val yesterday = firstToday - 1L
            base.scheduleRepository.pin(
                routineId = selected.id, focusKind = null, anchorDay = Weekday.fromEpochDay(yesterday),
            )
            base.plannerRepository.importSlotsIfNeeded(nowMs = NOW_MS)
            val importedRule = base.database.plannerDao().getRules().single { it.routineId == selected.id }
            base.database.plannerDao().upsertRule(row = importedRule.copy(
                createdAtMs = travelingClock.startOfDayMillis(
                    date = CivilDate.fromEpochDay(yesterday), zoneId = travelingClock.defaultZoneId(),
                ) - 1L,
            ))
            base.plannerRepository.ensureWeek(
                weekStart = CivilDate.fromEpochDay(yesterday).previousOrSame(Weekday.MONDAY),
            )
            val originalOccurrence = base.plannerRepository.occurrencesBetween(
                startEpochDay = yesterday, endEpochDay = yesterday,
            ).single()
            val originalWorkout = base.workoutRepository.startFreeWorkout(focusTitle = "Original workout")
            val workBefore = inventory()
            assertTrue(vm.startOccurrence(occurrenceId = originalOccurrence.id))
            awaitBlocked(sessionId = originalWorkout.id)
            writes.failNextDiscard.set(1)
            assertTrue(vm.discardBlockedAndStart())
            awaitFailed()
            assertEquals(1, writes.discardAttempts.get())
            assertEquals(0, writes.insertAttempts.get())
            assertFalse(vm.startState.value.discardedOriginal)
            assertEquals(workBefore, inventory())
            val rulesBeforeExpiry = base.database.plannerDao().getRules()
            val rowsBeforeExpiry = base.database.plannerDao().getAllOccurrences()
            val deliveriesBeforeExpiry = base.database.plannerDao().getDeliveries()
            assertEquals(
                OccurrenceStatus.MOVED.name,
                rowsBeforeExpiry.single { it.id == originalOccurrence.id }.status,
            )
            assertEquals(
                OccurrenceStatus.PLANNED.name,
                rowsBeforeExpiry.single { it.id != originalOccurrence.id && it.localEpochDay == firstToday }.status,
            )

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val nextToday = todayEpochDay(nowMs = travelingClock.nowMillis(), time = travelingClock)
            assertTrue(nextToday >= firstToday + 1L)
            assertEquals(NOW_MS, travelingClock.nowMillis())
            assertTrue(vm.retryStart())
            vm.startState.awaitFirst { !it.pending && it.error != null && !it.canRetry }

            assertEquals("The day has changed. Close this start and choose the workout again.", vm.startState.value.error)
            assertEquals(originalOccurrence.id, vm.startState.value.occurrenceId)
            assertFalse(vm.startState.value.discardedOriginal)
            assertTrue(vm.canResumeBlockedStart())
            assertFalse(vm.retryStart())
            assertEquals(1, writes.discardAttempts.get())
            assertEquals(0, writes.insertAttempts.get())
            assertEquals(workBefore, inventory())
            assertEquals(rulesBeforeExpiry, base.database.plannerDao().getRules())
            assertEquals(rowsBeforeExpiry, base.database.plannerDao().getAllOccurrences())
            assertEquals(deliveriesBeforeExpiry, base.database.plannerDao().getDeliveries())
            assertTrue(base.database.activityDao().getAllGraphs().isEmpty())
            assertNull(vm.navigateToSession.value)
            assertNull(vm.navigateToCardio.value)
            assertNull(vm.navigateToComposer.value)
            assertEquals(originalWorkout.id, vm.blockedByInProgress.value?.sessionId)
            assertNull(base.pendingOccurrenceId.value)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    private fun deviceZoneClock(): TimePort = object : TimePort by JvmTime {
        override fun nowMillis(): Long = NOW_MS
        override fun defaultZoneId(): String = ZoneId.systemDefault().id
    }

    private suspend fun createGraph(clock: TimePort = FrozenTime(nowMs = NOW_MS, zoneId = ZONE.id)) {
        val insights = MutableStateFlow(TrainingInsights())
        base = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), insights = insights,
            scheduler = dispatcher, time = clock,
        )
        val bench = insertTestExercise(deps = base, id = "command-owner-bench", name = "Incline Dumbbell Bench Press")
        val row = insertTestExercise(deps = base, id = "command-owner-row", name = "Chest Supported Row")
        val chosen = base.routineRepository.create(name = "Selected upper routine")
        base.routineRepository.addExercise(
            routineId = chosen.id, exercise = bench, targetSets = 3, targetReps = 8,
            targetWeightKg = 27.5, restSeconds = 120,
        )
        base.routineRepository.addExercise(
            routineId = chosen.id, exercise = row, targetSets = 2, targetReps = 12,
            targetWeightKg = 40.0, restSeconds = 75,
        )
        selected = checkNotNull(base.routineRepository.getById(id = chosen.id))
        val distractor = base.routineRepository.create(name = "Other routine")
        base.routineRepository.addExercise(
            routineId = distractor.id, exercise = row, targetSets = 1, targetReps = 5,
            targetWeightKg = 80.0, restSeconds = 180,
        )
        other = checkNotNull(base.routineRepository.getById(id = distractor.id))
        insights.value = TrainingInsights(routines = base.routineRepository.observeAll().first())
        val routines = RoutineRepository(
            routineDao = reads.decorate(delegate = base.database.routineDao()),
            database = base.database, planner = base.plannerRepository,
        )
        val workouts = WorkoutRepository(
            database = base.database, workoutDao = writes.decorate(delegate = base.database.workoutDao()),
            dbMaintenance = base.dbMaintenance, time = base.time,
        )
        val dayStart = StartTrainingDay(workoutRepository = workouts, routineRepository = routines)
        val graph = object : AppDependencies by base {
            override val routineRepository = routines
            override val workoutRepository = workouts
            override val startTrainingDay = dayStart
            override val startOccurrence = StartOccurrence(
                plannerRepository = base.plannerRepository, routineRepository = routines,
                startTrainingDay = dayStart, startLiveCardio = base.startLiveCardio,
            )
            override val discardWorkout = DiscardWorkout(
                workoutRepository = workouts, restTimer = base.restTimerController, draftCache = base.workoutDraftCache,
            )
        }
        vm = HomeViewModel(application = ApplicationProvider.getApplicationContext(), container = graph)
        vm.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
    }

    private suspend fun awaitBlocked(sessionId: String) {
        vm.startState.awaitFirst { !it.pending && vm.blockedByInProgress.value?.sessionId == sessionId }
        assertTrue(vm.canResumeBlockedStart())
        assertFalse(vm.canEditStartChoice())
    }

    private suspend fun awaitFailed() {
        vm.startState.awaitFirst { !it.pending && it.error != null && it.canRetry }
    }

    private suspend fun awaitWorkoutOpen(): String {
        vm.startState.awaitFirst { !it.pending && vm.navigateToSession.value != null }
        return checkNotNull(vm.navigateToSession.value)
    }

    private suspend fun inventory(): CanonicalWork = CanonicalWork(
        sessions = base.database.workoutDao().getAllSessions().sortedBy { it.id },
        exercises = base.database.workoutDao().getAllSessionExercises().sortedBy { it.id },
        sets = base.database.workoutDao().getAllSets().sortedBy { it.id },
    )

    private suspend fun assertSelectedRoutine(sessionId: String) {
        val saved = inventory()
        val session = saved.sessions.single()
        assertEquals(sessionId, session.id)
        assertEquals(selected.id, session.routineId)
        assertEquals(selected.name, session.routineName)
        assertEquals("", session.notes)
        assertEquals(NOW_MS, session.date)
        assertEquals(NOW_MS, session.startedAt)
        assertNull(session.finishedAt)
        val children = saved.exercises.sortedBy { it.sortOrder }
        assertEquals(selected.exercises.size, children.size)
        selected.exercises.sortedBy { it.sortOrder }.zip(children).forEachIndexed { index, (expected, actual) ->
            assertEquals(sessionId, actual.sessionId)
            assertEquals(expected.exercise.id, actual.exerciseId)
            assertEquals(index, actual.sortOrder)
            assertEquals(expected.targetSets, actual.targetSets)
            assertEquals(expected.targetReps, actual.targetReps)
            assertEquals(expected.targetWeightKg, actual.targetWeightKg)
            assertEquals(expected.restSeconds, actual.restSeconds)
            assertEquals(expected.targetSeconds, actual.targetSeconds)
            assertEquals(expected.targetSecondsMax, actual.targetSecondsMax)
        }
        assertTrue(saved.sets.isEmpty())
        assertNoOtherLaneWrites()
    }

    private suspend fun assertNoOtherLaneWrites() {
        assertTrue(base.database.activityDao().getAllGraphs().isEmpty())
        assertTrue(base.database.plannerDao().getAllOccurrences().isEmpty())
        assertNull(base.pendingOccurrenceId.value)
    }

    private data class CanonicalWork(
        val sessions: List<WorkoutSessionEntity>,
        val exercises: List<SessionExerciseEntity>,
        val sets: List<SetLogEntity>,
    )

    private class InsertGate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class StartWrites {
        val insertAttempts = AtomicInteger()
        val discardAttempts = AtomicInteger()
        val failNextInsert = AtomicInteger()
        val failNextDiscard = AtomicInteger()
        @Volatile var gate: InsertGate? = null

        fun decorate(delegate: WorkoutDao): WorkoutDao = object : WorkoutDao by delegate {
            override suspend fun upsertSession(session: WorkoutSessionEntity) {
                insertAttempts.incrementAndGet()
                gate?.let { held ->
                    held.entered.complete(Unit)
                    held.release.await()
                }
                if (failNextInsert.compareAndSet(1, 0)) throw SQLiteFullException("Synthetic replacement insert failure")
                delegate.upsertSession(session = session)
            }

            override suspend fun deleteInProgressSession(id: String): Int {
                discardAttempts.incrementAndGet()
                if (failNextDiscard.compareAndSet(1, 0)) throw SQLiteFullException("Synthetic unacknowledged discard failure")
                return delegate.deleteInProgressSession(id = id)
            }
        }
    }

    private class RoutineReads {
        val attempts = AtomicInteger()
        val failNext = AtomicInteger()

        fun decorate(delegate: RoutineDao): RoutineDao = object : RoutineDao by delegate {
            override suspend fun getById(id: String): RoutineWithExercises? {
                attempts.incrementAndGet()
                if (failNext.compareAndSet(1, 0)) throw SQLiteFullException("Synthetic immediate routine lookup failure")
                return delegate.getById(id = id)
            }
        }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Toronto")
        val NOW_MS: Long = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}
