package com.sinura.personaltrainer.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.time.ZoneId
import java.time.ZonedDateTime
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    ): FakeAppDependencies {
        val zone = ZoneId.systemDefault()
        val morning = ZonedDateTime.now(zone).toLocalDate().atTime(10, 0).atZone(zone)
        return FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            insights,
            scheduler = dispatcher,
            time = FrozenTime(morning.toInstant().toEpochMilli(), zone.id),
        )
    }

    @Test
    fun dropsProgressionReadyWhenHintsExist() = runBlocking {
        val insights = MutableStateFlow(
            TrainingInsights(
                hints = listOf(hint()),
                recommendations = listOf(rec("progression-ready"), rec("coverage-chest")),
            ),
        )
        deps = graph(insights)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val state = viewModel!!.uiState.first { !it.isLoading }
        assertTrue(state.recommendations.none { it.id == "progression-ready" })
        assertEquals(listOf("coverage-chest"), state.recommendations.map { it.id })
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

        val state = viewModel!!.uiState.first { !it.isLoading }
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
        val state = viewModel!!.uiState.first { !it.isLoading && it.occurrences.size >= 2 }
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
    fun lastSessionReadsAllTimeSummariesWhenTheHeatWindowIsEmpty() = runBlocking {
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

        val state = viewModel!!.uiState.first { !it.isLoading }
        assertEquals("ancient-pull", state.lastSession?.id)
        assertEquals(today - 80, state.lastSession?.localEpochDay)
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
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.startSuggestedDay(plannedDay(today, weekday, routine.id, routine.name))
        val sessionId = viewModel!!.navigateToSession.first { it != null }!!
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
        viewModel!!.uiState.first { !it.isLoading }

        val leftover = deps.plannerRepository.occurrencesBetween(yesterday, yesterday).single()
        viewModel!!.startOccurrence(leftover.id)
        val sessionId = viewModel!!.navigateToSession.first { it != null }!!
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
        val sessionId = checkNotNull(viewModel!!.navigateToSession.first { it != null })
        assertNull(viewModel!!.reviewOccurrenceId.value)
        assertEquals(sessionId, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun reminderReviewOpensConfirmAndStartsNothing() = runBlocking {
        val occurrence = seedTodayStrength()
        viewModel!!.reviewOccurrence(occurrence.id)
        assertEquals(occurrence.id, viewModel!!.reviewOccurrenceId.first { it != null })
        assertEquals(occurrence.localEpochDay, viewModel!!.focusEpochDay.value)
        assertNull(viewModel!!.navigateToSession.value)
        assertNull(deps.workoutRepository.getInProgress())
        assertNull(viewModel!!.uiState.value.error)
    }

    @Test
    fun missingReminderStartSurfacesAMessage() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.startOccurrence("gone")
        val state = viewModel!!.uiState.first { it.error != null }
        assertEquals(com.sinura.personaltrainer.domain.ReminderCopy.GONE, state.error)
        assertNull(viewModel!!.navigateToSession.value)
    }

    @Test
    fun missingReminderReviewSurfacesAMessage() = runBlocking {
        deps = graph()
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        viewModel!!.reviewOccurrence("gone")
        val state = viewModel!!.uiState.first { it.error != null }
        assertEquals(com.sinura.personaltrainer.domain.ReminderCopy.GONE, state.error)
        assertNull(viewModel!!.reviewOccurrenceId.value)
        assertNull(viewModel!!.navigateToSession.value)
    }

    private suspend fun seedTodayStrength(): com.sinura.personaltrainer.domain.ScheduleOccurrence {
        deps = graph()
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
        viewModel!!.uiState.first { !it.isLoading }
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
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.startFreeWorkout()
        val sessionId = viewModel!!.navigateToSession.first { it != null }!!
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
        viewModel!!.uiState.first { !it.isLoading }

        val leftover = deps.plannerRepository.occurrencesBetween(yesterday, yesterday).single()
        val enabled = deps.plannerRepository.rules().first { it.id == leftover.ruleId }.enabled
        viewModel!!.skipOccurrence(leftover.id)
        val skipped = withTimeout(5_000) {
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
        viewModel!!.uiState.first { !it.isLoading }

        val planned = deps.plannerRepository.occurrencesBetween(today, today).single()
        viewModel!!.skipOccurrence(planned.id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(OccurrenceStatus.PLANNED, deps.plannerRepository.getOccurrence(planned.id)!!.status)
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
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.Workout(routine.id), once = true)
        dispatcher.scheduler.advanceUntilIdle()
        val rule = withTimeout(5_000) {
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
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.Workout(routine.id), once = false)
        dispatcher.scheduler.advanceUntilIdle()
        val rule = withTimeout(5_000) {
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
        withTimeout(5_000) {
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
        viewModel!!.uiState.first { !it.isLoading }

        viewModel!!.addDaySession(today, HomeDayAdd.NewWorkout, once = true)
        val editorId = viewModel!!.navigateToEditor.first { it != null }!!
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
        val state = viewModel!!.uiState.first { !it.isLoading }
        assertFalse(state.setupComplete)
    }

    @Test
    fun aFinishedSetupHidesTheStarter() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val state = viewModel!!.uiState.first { !it.isLoading }
        assertTrue(state.setupComplete)
    }

    @Test
    fun liveCardioHidesHomesFilledVolt() = runBlocking {
        deps = graph()
        deps.preferencesRepository.setOnboardingComplete(true)
        viewModel = HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first { !it.isLoading }
        assertFalse(viewModel!!.uiState.value.sessionLive)

        val outcome = deps.startLiveCardio(
            type = com.sinura.personaltrainer.domain.CardioType.RUN,
            now = deps.time.captureNow(),
        )
        assertTrue(outcome is com.sinura.personaltrainer.workout.StartCardioOutcome.Open)

        val state = viewModel!!.uiState.first { it.sessionLive }
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
