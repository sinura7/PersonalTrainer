package com.sinura.personaltrainer.ui.home

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.testutil.FailingWeighInsDao
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.ReadGate
import com.sinura.personaltrainer.testutil.RefusingPlanLinkStore
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.catchingUncaught
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Home already names the ready lifts. The card that only says "some lifts are ready"
 * must not sit next to that list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: HomeViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    private fun graph(
        insights: MutableStateFlow<TrainingInsights> = MutableStateFlow(TrainingInsights()),
        bodyweightDaoDecorator: (BodyweightDao) -> BodyweightDao = { it },
        prefsStoreDecorator: (DataStore<Preferences>) -> DataStore<Preferences> = { it },
    ): FakeAppDependencies {
        val zone = ZoneId.systemDefault()
        val morning = ZonedDateTime.now(zone).toLocalDate().atTime(10, 0).atZone(zone)
        return FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            insights,
            scheduler = dispatcher,
            time = FrozenTime(morning.toInstant().toEpochMilli(), zone.id),
            bodyweightDaoDecorator = bodyweightDaoDecorator,
            prefsStoreDecorator = prefsStoreDecorator,
        )
    }

    /**
     * Audit DB-1: Home read the weigh-in log raw, so one failed read closed the app, and
     * Home is the first screen. It now keeps the log it had and goes on updating.
     */
    @Test
    fun aWeighInReadThatFailsKeepsHomeUpInsteadOfClosingTheApp() = runBlocking {
        val gate = ReadGate(shouldFail = false)
        val insights = MutableStateFlow(TrainingInsights())
        deps = graph(insights = insights, bodyweightDaoDecorator = { FailingWeighInsDao(it, gate) })
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        var updated: HomeUiState? = null
        val crash = catchingUncaught {
            gate.shouldFail = true
            deps.preferencesRepository.recordBodyweight(kg = 80.0, epochDay = todayEpochDay())
            withTimeout(TestWaits.FLOW_MS) {
                while (gate.refusals.get() == 0) delay(10)
            }
            insights.value = TrainingInsights(recommendations = listOf(rec("coverage-chest")))
            updated = withTimeoutOrNull(TestWaits.FLOW_MS) {
                viewModel!!.uiState.first { state -> state.recommendations.any { it.id == "coverage-chest" } }
            }
        }
        assertNull("a failed weigh-in read closed the app", crash)
        assertNotNull("Home stopped updating after one failed weigh-in read", updated)
    }

    /**
     * Home used to list every ready-to-progress lift, which made the card that
     * only says "some lifts are ready" redundant, so it was filtered out. That
     * list is off Home now and the card is the sole remaining signal, so the
     * filter is gone and the card must survive alongside the hints.
     */
    @Test
    fun keepsProgressionReadyNowThatHomeDoesNotListTheHints() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(
                hints = listOf(hint()),
                recommendations = listOf(rec("progression-ready"), rec("coverage-chest")),
            ),
        )
        deps = graph(insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertTrue(state.recommendations.any { it.id == "progression-ready" })
        assertEquals(
            listOf("progression-ready", "coverage-chest"),
            state.recommendations.map { it.id },
        )
    }

    @Test
    fun keepsProgressionReadyWhenNothingIsNamed() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(
                hints = emptyList(),
                recommendations = listOf(rec("progression-ready")),
            ),
        )
        deps = graph(insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertEquals(listOf("progression-ready"), state.recommendations.map { it.id })
    }

    @Test
    fun agendaListsIndependentOccurrences() = runBlocking {
        deps = graph()
        val weekStart = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(
            com.sinura.personaltrainer.domain.todayEpochDay(),
        ).previousOrSame(com.sinura.personaltrainer.domain.Weekday.MONDAY)
        val today = com.sinura.personaltrainer.domain.Weekday.fromEpochDay(
            com.sinura.personaltrainer.domain.todayEpochDay(),
        )
        deps.plannerRepository.addTimedRule(
            weekday = today,
            hour = 7,
            minute = 0,
            modality = com.sinura.personaltrainer.domain.ScheduleModality.CARDIO,
            nowMs = 1_700_000_000_000L,
        )
        deps.plannerRepository.addTimedRule(
            weekday = today,
            hour = 18,
            minute = 0,
            modality = com.sinura.personaltrainer.domain.ScheduleModality.STRENGTH,
            nowMs = 1_700_000_000_000L,
        )
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.awaitFirst { !it.isLoading && it.occurrences.size >= 2 }
        val names = state.routines.associate { it.id to it.name }
        val agenda = com.sinura.personaltrainer.domain.DailyAgenda.forDay(
            com.sinura.personaltrainer.domain.todayEpochDay(),
            state.occurrences,
            state.rules,
            names,
        )
        assertEquals(listOf(7, 18), agenda.map { it.occurrence.hour })
        assertEquals(
            listOf("Cardio", "Strength"),
            agenda.map { it.title },
        )
        assertEquals(
            com.sinura.personaltrainer.domain.HomeToday.Surface.AGENDA,
            com.sinura.personaltrainer.domain.HomeToday.surface(agenda),
        )
    }

    @Test
    /**
     * The last-session and days-since tiles are off Home, but the week strip's
     * done tick still comes from the same place, and it must keep reading the
     * all-time summaries rather than the 30-day heat window: a session older
     * than the window is still a session that happened.
     */
    fun loggedDaysReadAllTimeSummariesWhenTheHeatWindowIsEmpty() = runBlocking {
        val today = com.sinura.personaltrainer.domain.todayEpochDay()
        val old = com.sinura.personaltrainer.domain.SessionSummary(
            id = "ancient-pull",
            routineId = "r1",
            routineName = "Pull",
            date = 1_000L,
            finishedAt = 1_100L,
            durationMinutes = 48,
            workingSets = 16,
            volumeKg = 9_000.0,
            localEpochDay = today - 80,
        )
        val insights = MutableStateFlow(
            TrainingInsights(
                history = emptyList(),
                summaries = listOf(old),
            ),
        )
        deps = graph(insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertTrue(state.loggedEpochDays.contains(today - 80))
    }

    @Test
    fun plannedStartBindsOccurrenceSoFinishMarksItDone() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-squat", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.startSuggestedDay(plannedDay(today, weekday, routine.id, routine.name))
        val sessionId = viewModel!!.navigateToSession.awaitFirst { it != null }!!
        PendingOccurrence.complete(deps, sessionId)

        val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single()
        assertEquals(OccurrenceStatus.DONE, occurrence.status)
        assertEquals(sessionId, occurrence.completedActivityId)
    }

    @Test
    fun leftoverStartMovesTheOccurrenceOntoTodayThenOpensTheSession() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val yesterday = today - 1
        val yesterdayWeekday = Weekday.fromEpochDay(yesterday)
        val yesterdayWeekStart = CivilDate.fromEpochDay(yesterday).previousOrSame(Weekday.MONDAY)
        val todayWeekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-leftover-squat", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, yesterdayWeekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(yesterdayWeekStart)
        if (todayWeekStart.epochDay != yesterdayWeekStart.epochDay) {
            deps.plannerRepository.ensureWeek(todayWeekStart)
        }
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        val leftover = deps.plannerRepository.occurrencesBetween(yesterday, yesterday).single()
        viewModel!!.startOccurrence(leftover.id)
        val sessionId = viewModel!!.navigateToSession.awaitFirst { it != null }!!
        assertEquals(OccurrenceStatus.MOVED, leftover.id.let { deps.plannerRepository.getOccurrence(it) }!!.status)
        val moved = deps.plannerRepository.occurrencesBetween(today, today).single()
        assertEquals(today, moved.localEpochDay)
        assertEquals(OccurrenceStatus.PLANNED, moved.status)
        PendingOccurrence.complete(deps, sessionId)
        assertEquals(OccurrenceStatus.DONE, deps.plannerRepository.getOccurrence(moved.id)!!.status)
        assertEquals(sessionId, deps.plannerRepository.getOccurrence(moved.id)!!.completedActivityId)
        assertEquals(OccurrenceStatus.MOVED, deps.plannerRepository.getOccurrence(leftover.id)!!.status)
    }

    @Test
    fun reminderStartStartsTheOccurrence() = runBlocking {
        val occurrence = seedTodayStrength()
        viewModel!!.startOccurrence(occurrence.id)
        val sessionId = checkNotNull(viewModel!!.navigateToSession.awaitFirst { it != null })
        assertNull(viewModel!!.reviewOccurrenceId.value)
        assertEquals(sessionId, deps.workoutRepository.getInProgress()?.id)
    }

    /**
     * Audit UI-12: the link between the new session and its planned day is written after the
     * start has opened. When that write failed it threw past the start and closed the app.
     */
    @Test
    fun aReminderStartWhoseLinkCannotBeSavedStillOpensTheSession() = runBlocking {
        var store: RefusingPlanLinkStore? = null
        val occurrence = seedTodayStrength(
            graph(prefsStoreDecorator = { real -> RefusingPlanLinkStore(real).also { store = it } }),
        )
        val refusing = checkNotNull(store)
        refusing.refuse = true

        var opened: String? = null
        val crash = catchingUncaught {
            viewModel!!.startOccurrence(occurrence.id)
            opened = withTimeoutOrNull(TestWaits.FLOW_MS) {
                viewModel!!.navigateToSession.first { it != null }
            }
        }

        assertNull("a refused link write closed the app", crash)
        assertTrue("the link write never ran", refusing.refusals.get() > 0)
        assertEquals(deps.workoutRepository.getInProgress()?.id, opened)
    }

    @Test
    fun reminderReviewOpensConfirmAndStartsNothing() = runBlocking {
        val occurrence = seedTodayStrength()
        viewModel!!.reviewOccurrence(occurrence.id)
        assertEquals(occurrence.id, viewModel!!.reviewOccurrenceId.awaitFirst { it != null })
        assertEquals(occurrence.localEpochDay, viewModel!!.focusEpochDay.value)
        assertNull(viewModel!!.navigateToSession.value)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(viewModel!!.uiState.value.error)
    }

    @Test
    fun missingReminderStartSurfacesAMessage() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }
        viewModel!!.startOccurrence("gone")
        val state = viewModel!!.uiState.awaitFirst { it.error != null }
        assertEquals(com.sinura.personaltrainer.domain.ReminderCopy.GONE, state.error)
        assertNull(viewModel!!.navigateToSession.value)
    }

    @Test
    fun missingReminderReviewSurfacesAMessage() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }
        viewModel!!.reviewOccurrence("gone")
        val state = viewModel!!.uiState.awaitFirst { it.error != null }
        assertEquals(com.sinura.personaltrainer.domain.ReminderCopy.GONE, state.error)
        assertNull(viewModel!!.reviewOccurrenceId.value)
        assertNull(viewModel!!.navigateToSession.value)
    }

    /**
     * Audit UI-1: a reminder's Start marked its delivery started, and dismissed it, at the tap,
     * before the start could be refused. Now only a start that opens uses the reminder up.
     */
    @Test
    fun aReminderStartThatOpensUsesTheReminderUp() = runBlocking {
        val occurrence = seedTodayStrength()
        val reminder = postReminder(occurrence)

        viewModel!!.startOccurrence(occurrence.id, deliveryId = reminder)
        viewModel!!.navigateToSession.awaitFirst { it != null }

        assertEquals(ReminderDeliveryStatus.STARTED.name, deliveryStatus(reminder))
        assertFalse("its Snooze, Move and Skip must leave the shade", reminderShown(occurrence.id))
    }

    @Test
    fun aReminderStartRefusedByALiveSessionLeavesTheReminderForLater() = runBlocking {
        val occurrence = seedTodayStrength()
        val reminder = postReminder(occurrence)
        val live = startAnotherSession()

        viewModel!!.startOccurrence(occurrence.id, deliveryId = reminder)
        val blocked = checkNotNull(viewModel!!.blockedByInProgress.awaitFirst { it != null })
        assertEquals(live, blocked.sessionId)
        assertEquals(ReminderDeliveryStatus.PENDING.name, deliveryStatus(reminder))
        assertTrue("the reminder stays in the shade", reminderShown(occurrence.id))

        viewModel!!.resumeBlocked()
        assertEquals(live, viewModel!!.navigateToSession.value)
        assertEquals(ReminderDeliveryStatus.PENDING.name, deliveryStatus(reminder))
        assertTrue(reminderShown(occurrence.id))
    }

    @Test
    fun discardingTheLiveSessionForAReminderUsesItUpOnceTheStartOpens() = runBlocking {
        val occurrence = seedTodayStrength()
        val reminder = postReminder(occurrence)
        val live = startAnotherSession()
        viewModel!!.startOccurrence(occurrence.id, deliveryId = reminder)
        viewModel!!.blockedByInProgress.awaitFirst { it != null }

        viewModel!!.discardBlockedAndStart()
        val opened = checkNotNull(viewModel!!.navigateToSession.awaitFirst { it != null })

        assertTrue("a new session opened", opened != live)
        assertEquals(ReminderDeliveryStatus.STARTED.name, deliveryStatus(reminder))
        assertFalse(reminderShown(occurrence.id))
    }

    /** Nothing left to start: the reminder is dismissed, since its Start can never open anything. */
    @Test
    fun aReminderForASessionNoLongerPlannedIsDismissed() = runBlocking {
        val occurrence = seedTodayStrength()
        val reminder = postReminder(occurrence)
        deps.database.plannerDao().deleteOccurrence(occurrence.id)

        viewModel!!.startOccurrence(occurrence.id, deliveryId = reminder)
        val state = viewModel!!.uiState.awaitFirst { it.error != null }

        assertEquals(com.sinura.personaltrainer.domain.ReminderCopy.GONE, state.error)
        assertFalse(reminderShown(occurrence.id))
        assertNull(viewModel!!.navigateToSession.value)
    }

    /** A plan reminder in the shade for [occurrence], its delivery row waiting, as the worker posts it. */
    private suspend fun postReminder(occurrence: com.sinura.personaltrainer.domain.ScheduleOccurrence): String {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val deliveryId = "rem-${occurrence.id}"
        deps.database.plannerDao().upsertDelivery(
            ReminderDeliveryEntity(
                id = deliveryId,
                occurrenceId = occurrence.id,
                scheduledAtMs = 1L,
                status = ReminderDeliveryStatus.PENDING.name,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        ReminderNotifications.show(app, occurrence, deliveryId, "Push")
        check(reminderShown(occurrence.id)) { "the reminder was not posted" }
        return deliveryId
    }

    private fun reminderShown(occurrenceId: String): Boolean =
        ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == occurrenceId.hashCode() }

    private suspend fun deliveryStatus(id: String): String? = deps.database.plannerDao().getDelivery(id)?.status

    private suspend fun startAnotherSession(): String =
        (deps.workoutRepository.startFreeWorkoutSafely("Already going") as StartSessionOutcome.Started).session.id

    private suspend fun seedTodayStrength(
        built: FakeAppDependencies = graph(),
    ): com.sinura.personaltrainer.domain.ScheduleOccurrence {
        deps = built
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-reminder", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }
        return deps.plannerRepository.occurrencesBetween(today, today).single()
    }

    @Test
    fun freeWorkoutOpensAnEmptySessionWithoutMarkingThePlan() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-free", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.startFreeWorkout()
        val sessionId = viewModel!!.navigateToSession.awaitFirst { it != null }!!
        val session = deps.workoutRepository.getSession(sessionId)!!
        assertEquals("Free workout", session.routineName)
        assertTrue(session.exercises.isEmpty())
        assertNull(deps.pendingOccurrenceId.value)
        assertEquals(
            OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(today, today).single().status,
        )
    }

    @Test
    fun startRoutineOpensThatRoutineWithoutMarkingThePlan() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val planned = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-start-planned", "Squat")
        deps.routineRepository.addExercise(planned.id, squat, 3, 5, 100.0, 90)
        val extra = deps.routineRepository.create("Pull")
        val row = insertTestExercise(deps, "ex-home-start-pull", "Row")
        deps.routineRepository.addExercise(extra.id, row, 3, 8, 60.0, 90)
        deps.scheduleRepository.pin(planned.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.startRoutine(extra.id)
        val sessionId = viewModel!!.navigateToSession.awaitFirst { it != null }!!
        val session = deps.workoutRepository.getSession(sessionId)!!
        assertEquals(extra.id, session.routineId)
        assertEquals("Pull", session.routineName)
        assertEquals(1, session.exercises.size)
        assertNull(deps.pendingOccurrenceId.value)
        assertEquals(
            OccurrenceStatus.PLANNED,
            deps.plannerRepository.occurrencesBetween(today, today).single().status,
        )
    }

    @Test
    fun startCardioOpensLiveWalk() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.startCardio(com.sinura.personaltrainer.domain.CardioType.WALK)
        val sessionId = viewModel!!.navigateToCardio.awaitFirst { it != null }!!
        val live = deps.activityRepository.getLive()!!
        assertEquals(sessionId, live.id)
        assertEquals("Walk", live.title)
        assertNull(deps.pendingOccurrenceId.value)
    }

    @Test
    fun startAuxStartsThePackWithoutMintingAPlanRow() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        deps.dbMaintenance.seedCatalog()
        val today = todayEpochDay()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.startAux("golf")
        val sessionId = viewModel!!.navigateToSession.awaitFirst { it != null }!!
        val session = deps.workoutRepository.getSession(sessionId)!!
        assertEquals("Golf warm-up", session.routineName)
        assertTrue(session.exercises.isNotEmpty())
        assertTrue(deps.plannerRepository.occurrencesBetween(today, today).isEmpty())
        assertNull(deps.pendingOccurrenceId.value)
    }

    @Test
    fun skipOccurrenceMarksALeftoverSkippedWithoutChangingTheRule() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val yesterday = today - 1
        val yesterdayWeekday = Weekday.fromEpochDay(yesterday)
        val yesterdayWeekStart = CivilDate.fromEpochDay(yesterday).previousOrSame(Weekday.MONDAY)
        val todayWeekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-skip-squat", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, yesterdayWeekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(yesterdayWeekStart)
        if (todayWeekStart.epochDay != yesterdayWeekStart.epochDay) {
            deps.plannerRepository.ensureWeek(todayWeekStart)
        }
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        val leftover = deps.plannerRepository.occurrencesBetween(yesterday, yesterday).single()
        val enabled = deps.plannerRepository.rules().first { it.id == leftover.ruleId }.enabled
        viewModel!!.skipOccurrence(leftover.id)
        val skipped = withTimeout(TestWaits.FLOW_MS) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.any { it.id == leftover.id && it.status == OccurrenceStatus.SKIPPED }
            }.first { it.id == leftover.id }
        }
        assertEquals(OccurrenceStatus.SKIPPED, skipped.status)
        assertEquals(
            enabled,
            deps.plannerRepository.rules().first { it.id == leftover.ruleId }.enabled,
        )
    }

    @Test
    fun skipOccurrenceUndoRestoresTheLeftoverStatus() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val yesterday = today - 1
        val yesterdayWeekday = Weekday.fromEpochDay(yesterday)
        val yesterdayWeekStart = CivilDate.fromEpochDay(yesterday).previousOrSame(Weekday.MONDAY)
        val todayWeekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-skip-undo-squat", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, yesterdayWeekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(yesterdayWeekStart)
        if (todayWeekStart.epochDay != yesterdayWeekStart.epochDay) {
            deps.plannerRepository.ensureWeek(todayWeekStart)
        }
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        val leftover = deps.plannerRepository.occurrencesBetween(yesterday, yesterday).single()
        val previous = leftover.status
        viewModel!!.skipOccurrence(leftover.id)
        val offered = viewModel!!.skippedDay.awaitFirst { it != null }!!
        assertEquals(leftover.id, offered.occurrenceId)
        assertEquals(previous, offered.previousStatus)
        assertTrue(offered.title.isNotBlank())

        viewModel!!.undoSkipOccurrence()
        val restored = withTimeout(TestWaits.FLOW_MS) {
            deps.plannerRepository.observeOccurrences().first { rows ->
                rows.any { it.id == leftover.id && it.status == previous }
            }.first { it.id == leftover.id }
        }
        assertEquals(previous, restored.status)
        assertNull(viewModel!!.skippedDay.value)
    }

    @Test
    fun skipOccurrenceDoesNotSkipTodaysPlannedSession() = runBlocking {
        deps = graph()
        val today = todayEpochDay()
        val weekday = Weekday.fromEpochDay(today)
        val weekStart = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY)
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-skip-today", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        deps.scheduleRepository.pin(routine.id, null, weekday)
        deps.plannerRepository.importSlotsIfNeeded(1_700_000_000_000L)
        deps.plannerRepository.ensureWeek(weekStart)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        val planned = deps.plannerRepository.occurrencesBetween(today, today).single()
        viewModel!!.skipOccurrence(planned.id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(OccurrenceStatus.PLANNED, deps.plannerRepository.getOccurrence(planned.id)!!.status)
        assertNull(viewModel!!.skippedDay.value)
    }

    @Test
    fun addDaySessionOnceDisablesTheRuleAfterMintingThisWeek() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        val today = todayEpochDay()
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-add-once", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.Workout(routine.id), once = true)
        dispatcher.scheduler.advanceUntilIdle()
        val rule = withTimeout(TestWaits.FLOW_MS) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.routineId == routine.id && !it.enabled }
            }.single { it.routineId == routine.id }
        }
        assertFalse(rule.enabled)
        assertTrue(
            deps.plannerRepository.occurrencesBetween(today, today).any { it.ruleId == rule.id },
        )
    }

    @Test
    fun addDaySessionWeeklyKeepsTheRuleEnabled() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        val today = todayEpochDay()
        val routine = deps.routineRepository.create("Push")
        val squat = insertTestExercise(deps, "ex-home-add-week", "Squat")
        deps.routineRepository.addExercise(routine.id, squat, 3, 5, 100.0, 90)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.Workout(routine.id), once = false)
        dispatcher.scheduler.advanceUntilIdle()
        val rule = withTimeout(TestWaits.FLOW_MS) {
            deps.plannerRepository.observeRules().first { rows ->
                rows.any { it.routineId == routine.id && it.enabled }
            }.single { it.routineId == routine.id }
        }
        assertTrue(rule.enabled)
        // Wait on the occurrence, not on the rule. `publishPinnedWeek` is two writes and not
        // one transaction: `syncSlotsToRules` commits the rule and wakes `observeRules()`
        // before `ensureWeek` has written the week. Waiting for an *enabled* rule therefore
        // returns inside that gap — which is why the `once` sibling passes on the same path:
        // its barrier is the rule being *disabled*, which `mintTimed` only does after publish
        // returns. Reading the occurrences straight after the rule appears reads them early.
        withTimeout(TestWaits.FLOW_MS) {
            deps.plannerRepository.observeOccurrences().first { occurrences ->
                occurrences.any { it.ruleId == rule.id && it.localEpochDay == today }
            }
        }
        assertTrue(
            deps.plannerRepository.occurrencesBetween(today, today).any { it.ruleId == rule.id },
        )
    }

    @Test
    fun addNewWorkoutOnceOpensTheEditor() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        val today = todayEpochDay()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.NewWorkout, once = true)
        val editorId = viewModel!!.navigateToEditor.awaitFirst { it != null }!!
        val rule = deps.plannerRepository.rules().single { it.routineId == editorId }
        assertFalse(rule.enabled)
        assertTrue(
            deps.plannerRepository.occurrencesBetween(today, today).any { it.ruleId == rule.id },
        )
    }

    @Test
    fun requestAnswerReplayArmsPlanOnce() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        assertFalse(deps.pendingAnswerReplay.value)
        viewModel!!.requestAnswerReplay()
        assertTrue(deps.pendingAnswerReplay.value)
    }

    @Test
    fun firstInstallIsNotSetupComplete() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertFalse(state.setupComplete)
    }

    @Test
    fun aFinishedSetupHidesTheStarter() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertTrue(state.setupComplete)
    }

    @Test
    fun liveCardioHidesHomesFilledVolt() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.awaitFirst { !it.isLoading }
        assertFalse(viewModel!!.uiState.value.sessionLive)

        val outcome = deps.startLiveCardio(
            type = com.sinura.personaltrainer.domain.CardioType.RUN,
            now = deps.time.captureNow(),
        )
        assertTrue(outcome is com.sinura.personaltrainer.workout.StartCardioOutcome.Open)

        val state = viewModel!!.uiState.awaitFirst { it.sessionLive }
        assertNull(state.inProgress)
        assertNotNull(state.liveActivity)
        assertTrue(state.sessionLive)
    }

    private fun hint() = ProgressionHint(
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        lastWeightKg = 100.0,
        lastReps = 5,
        targetReps = 5,
        suggestedWeightKg = 102.5,
        action = ProgressionAction.INCREASE,
        loadType = LoadType.EXTERNAL,
    )

    private fun plannedDay(
        today: Long,
        weekday: Weekday,
        routineId: String,
        routineName: String,
    ) = SuggestedTrainingDay(
        epochDay = today,
        dayOfWeek = weekday,
        isRest = false,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = routineId,
        routineName = routineName,
        reason = "Planned.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun rec(id: String) = TrainingRecommendation(
        id = id,
        kicker = "PROGRESSION",
        title = "Ready",
        reason = "named",
        priority = RecommendationPriority.INFO,
        rankScore = 10,
    )
}
