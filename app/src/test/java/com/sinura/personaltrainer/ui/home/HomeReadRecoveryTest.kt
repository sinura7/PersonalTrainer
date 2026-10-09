package com.sinura.personaltrainer.ui.home

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ReminderScheduler
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.reminder.ReminderNotifications
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.workout.WorkoutDraft
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
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

/** Required-read failures use the real repositories and undecorated Room inventories. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeReadRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private var deps: FakeAppDependencies? = null
    private var vm: HomeViewModel? = null
    private lateinit var faults: ReadFaults
    private lateinit var insights: MutableStateFlow<TrainingInsights>
    private lateinit var reminders: RecordingReminders
    private var routineId = ""
    private var liveId = ""
    private var today = 0L
    private lateinit var occurrence: ScheduleOccurrence
    private lateinit var leftover: ScheduleOccurrence
    private lateinit var suggestedDay: SuggestedTrainingDay

    @Before
    fun before() { Dispatchers.setMain(dispatcher) }

    @After
    fun after() = runBlocking {
        closeGraph()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private suspend fun closeGraph() {
        vm?.clearAndJoinForTest()
        vm = null
        deps?.close()
        deps = null
    }

    private suspend fun graph(seed: Boolean = true) {
        closeGraph()
        faults = ReadFaults()
        insights = MutableStateFlow(TrainingInsights())
        reminders = RecordingReminders()
        val zone = ZoneId.of("America/Toronto")
        val now = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, zone)
        val graph = FakeAppDependencies(
            context = app, insights = faults.read(Feed.INSIGHTS, insights), scheduler = dispatcher,
            time = FrozenTime(now.toInstant().toEpochMilli(), zone.id),
            reminderScheduler = reminders,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override fun observeInProgressSession(): Flow<WorkoutSessionEntity?> =
                    faults.read(Feed.WORKOUT, real.observeInProgressSession())
            } },
            activityDaoDecorator = { real -> object : ActivityDao by real {
                override fun observeLive(): Flow<ActivitySessionEntity?> =
                    faults.read(Feed.ACTIVITY, real.observeLive())
            } },
            plannerDaoDecorator = { real -> object : PlannerDao by real {
                override suspend fun getOccurrence(id: String): ScheduleOccurrenceEntity? {
                    check(!faults.failStartLookup) { "The admitted start lookup failed" }
                    return real.getOccurrence(id)
                }
                override fun observeOccurrences(): Flow<List<ScheduleOccurrenceEntity>> =
                    faults.read(Feed.OCCURRENCES, real.observeOccurrences())
                override fun observeRules(): Flow<List<ScheduleRuleEntity>> =
                    faults.read(Feed.RULES, real.observeRules())
                override fun observeDecisions(): Flow<List<MissedWorkDecisionEntity>> =
                    faults.read(Feed.DECISIONS, real.observeDecisions())
            } },
            bodyweightDaoDecorator = { real -> object : BodyweightDao by real {
                override fun observeAll(): Flow<List<BodyweightEntryEntity>> =
                    faults.read(Feed.BODYWEIGHT, real.observeAll())
                override suspend fun upsert(entry: BodyweightEntryEntity) {
                    faults.bodyweightWrites.incrementAndGet()
                    faults.heldBodyweightWrite?.await()
                    check(!faults.failBodyweightWrite) { "The weigh-in write failed" }
                    real.upsert(entry)
                }
            } },
            prefsStoreDecorator = { real -> object : DataStore<Preferences> {
                override val data: Flow<Preferences> = faults.read(Feed.PREFERENCES, real.data)
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    check(!faults.failPreferenceWrite) { "The preferences mirror write failed" }
                    return real.updateData(transform)
                }
            } },
        )
        deps = graph
        today = graph.time.civilDate(graph.time.nowMillis()).epochDay
        if (!seed) return
        graph.preferencesRepository.setOnboardingComplete(true)
        graph.preferencesRepository.recordBodyweight(81.25, today - 2)
        val routine = graph.routineRepository.create("Recovery Push")
        routineId = routine.id
        val exercise = insertTestExercise(graph, "home-recovery-squat", "Squat")
        graph.routineRepository.addExercise(routineId, exercise, 3, 8, 75.0, 90)
        // Both rules existed before the synthetic leftover's civil date. The planner
        // correctly refuses to backfill a yesterday occurrence from a rule created today.
        val rulesCreatedAtMs = now.minusDays(2).toInstant().toEpochMilli()
        for (day in listOf(today, today - 1)) {
            graph.plannerRepository.addTimedRule(
                weekday = Weekday.fromEpochDay(day), hour = 18, minute = 0,
                modality = com.sinura.personaltrainer.domain.ScheduleModality.STRENGTH,
                routineId = routineId, nowMs = rulesCreatedAtMs,
            )
            graph.plannerRepository.ensureWeek(CivilDate.fromEpochDay(day).previousOrSame(Weekday.MONDAY))
        }
        occurrence = graph.plannerRepository.occurrencesBetween(today, today).single()
        leftover = graph.plannerRepository.occurrencesBetween(today - 1, today - 1).single()
        val live = graph.workoutRepository.startFreeWorkout("Existing workout")
        liveId = live.id
        graph.workoutRepository.addExerciseToSession(liveId, exercise, 3, 8, 75.0, 90)
        graph.workoutRepository.logSet(liveId, exercise.id, 77.5, 9, 8, false)
        graph.workoutDraftCache.put(
            WorkoutDraft(
                sessionId = liveId,
                exerciseId = exercise.id,
                weightKg = 82.5,
                reps = 7,
                rpe = 9,
                isWarmup = false,
                notes = "Keep this draft",
                dirty = true,
            ),
        )
        graph.restTimerController.start(150, liveId)
        graph.pendingOccurrenceId.value = occurrence.id
        graph.preferencesRepository.setPendingOccurrenceId(occurrence.id)
        suggestedDay = SuggestedTrainingDay(
            epochDay = today, dayOfWeek = Weekday.fromEpochDay(today), isRest = false,
            focusKind = SessionFocusKind.FULL_BODY, focusTitle = routine.name,
            routineId = routineId, routineName = routine.name, reason = "Synthetic current plan",
            emphasisMuscles = emptyList(), confidence = ScheduleConfidence.HIGH,
        )
        insights.value = TrainingInsights(
            routines = listOf(checkNotNull(graph.routineRepository.getById(routineId))),
            weekPlan = WeeklySchedulePlan(
                weekStartEpochDay = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY).epochDay,
                generatedAtMs = graph.time.nowMillis(), preferences = SchedulePreferences(),
                resolvedSplit = SplitStyle.FULL_BODY, days = listOf(suggestedDay), thinHistory = true,
                summary = "Synthetic current plan for required-read admission",
            ),
        )
    }

    private fun startViewModel(container: AppDependencies = checkNotNull(deps)) {
        vm = HomeViewModel(app, container)
    }

    @Test
    fun eachInitialRequiredFailureEndsLoadingWithoutPretendingTheBoardIsEmptyOrWriting() = runBlocking {
        for (feed in Feed.entries) {
            graph()
            faults.failed.value = setOf(feed)
            val before = inventory()
            startViewModel()
            val unavailable = checkNotNull(vm).uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE }
            assertFalse("$feed left the initial spinner running", unavailable.isLoading)
            assertFalse(unavailable.mutationEnabled)
            assertNotNull(unavailable.readProblem)
            rejectEveryMutation()
            assertEquals("$feed changed storage while unavailable", before, inventory())
        }
    }

    @Test
    fun eachLaterRequiredFailureRetainsTheEntireCompleteBoardAndEveryLocalOffer() = runBlocking {
        for (feed in Feed.entries) {
            graph()
            startViewModel()
            val model = checkNotNull(vm)
            model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
            assertTrue(model.startRoutine(routineId))
            val blocked = model.blockedByInProgress.awaitFirst { it != null }
            assertTrue(model.skipOccurrence(leftover.id))
            val offered = model.skippedDay.awaitFirst { it != null }
            val complete = model.uiState.awaitFirst { state ->
                state.readState == HomeReadState.CURRENT &&
                    state.occurrences.any { it.id == leftover.id && it.status == OccurrenceStatus.SKIPPED }
            }
            assertEquals("the fallback target is valid before $feed fails", suggestedDay, complete.weekPlan?.dayOn(today))
            val before = inventory()
            faults.failed.value = setOf(feed)
            val stale = model.uiState.awaitFirst { it.readState == HomeReadState.STALE }
            assertEquals("$feed replaced part of the board", complete, stale.copy(readState = HomeReadState.CURRENT, readProblem = null))
            insights.value = TrainingInsights() // Other successful reads cannot hide this failure.
            rejectEveryMutation()
            model.onUndoOfferHandled() // A queued expiration must not consume a disabled Undo.
            assertEquals(blocked, model.blockedByInProgress.value)
            assertEquals(offered, model.skippedDay.value)
            assertEquals("$feed changed storage while stale", before, inventory())
            assertEquals(complete.routines, model.uiState.value.routines)
        }
    }

    @Test
    fun anInitialFaultIsVisibleEvenWhenInsightsHasNeverProducedAValue() = runBlocking {
        graph()
        faults.hold(Feed.INSIGHTS)
        faults.failed.value = setOf(Feed.WORKOUT)
        startViewModel()
        val state = checkNotNull(vm).uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE }
        assertFalse(state.isLoading)
        assertFalse(state.mutationEnabled)
    }

    @Test
    fun aSuccessfulPartialReadThatLaterFailsEndsLoadingWhileAnotherReadStillWaits() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        val heldInsights = faults.hold(Feed.INSIGHTS)
        val activityHealth = MutableStateFlow<DataHealth<ActivitySession?>?>(null)
        val sibling = launch {
            graph.activityRepository.observeLiveHealth().collect { activityHealth.value = it }
        }
        try {
            // This is a successful absence from the actual shared Room read. Its later
            // failure is Degraded(null), not an initial Unavailable or invented empty row.
            activityHealth.awaitFirst { it is DataHealth.Available }
            assertNull((activityHealth.value as DataHealth.Available).value)
            startViewModel()
            faults.awaitSubscriptions(Feed.INSIGHTS, 1)
            val model = checkNotNull(vm)
            val before = inventory()
            faults.failed.value = setOf(Feed.ACTIVITY)
            activityHealth.awaitFirst { it is DataHealth.Degraded }
            val failed = model.uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE }
            assertFalse("a known partial-read failure left the spinner running", failed.isLoading)
            assertFalse(failed.mutationEnabled)
            assertNotNull(failed.readProblem)
            assertFalse("the other required read is still waiting", heldInsights.isCompleted)
            rejectEveryMutation()
            assertEquals(before, inventory())
        } finally { sibling.cancelAndJoin() }
    }

    @Test
    fun successfulEmptyAndNullReadsAreCurrentAndOptionalInsightFallbacksRemainUsable() = runBlocking {
        graph(seed = false)
        insights.value = TrainingInsights(failures = setOf(InsightFailure.HEAT, InsightFailure.PLAN))
        startViewModel()
        val state = checkNotNull(vm).uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        assertFalse(state.isLoading)
        assertNull(state.inProgress)
        assertNull(state.liveActivity)
        assertTrue(state.routines.isEmpty())
        assertTrue(state.occurrences.isEmpty())
        assertFalse(state.setupComplete)
        assertTrue(state.mutationEnabled)
        assertNull(state.readProblem)
        assertTrue(checkNotNull(vm).startFreeWorkout())
        val opened = checkNotNull(vm).navigateToSession.awaitFirst { it != null }
        assertEquals(opened, checkNotNull(deps).workoutRepository.getInProgress()?.id)
    }

    @Test
    fun waitingRefusesAllMutationsAndKeepsTheSameReminderRequest() = runBlocking {
        graph()
        val held = faults.hold(Feed.INSIGHTS)
        val before = inventory()
        startViewModel()
        faults.awaitSubscriptions(Feed.INSIGHTS, 1)
        val model = checkNotNull(vm)
        assertEquals(HomeReadState.WAITING, model.uiState.value.readState)
        rejectEveryMutation()
        assertFalse(model.dispatchPendingOccurrenceStart(occurrence.id, "delivery", "waiting-request"))
        assertEquals(before, inventory())
        held.complete(Unit)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, "delivery", "waiting-request"))
        val blocked = model.blockedByInProgress.awaitFirst { it != null }
        assertEquals(occurrence.id, blocked?.occurrenceId)
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, "delivery", "waiting-request"))
        assertEquals(blocked, model.blockedByInProgress.value)
        assertEquals(before, inventory())
    }

    @Test
    fun retryRecollectsTheFailedReadAndHoldsTheCompleteBoardUntilFreshInputsArrive() = runBlocking {
        graph()
        startViewModel()
        val model = checkNotNull(vm)
        val complete = model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        faults.failed.value = setOf(Feed.INSIGHTS)
        model.uiState.awaitFirst { it.readState == HomeReadState.STALE }
        faults.failed.value = emptySet()
        insights.value = TrainingInsights()
        val held = faults.hold(Feed.INSIGHTS)
        val before = inventory()
        val count = faults.subscriptions(Feed.INSIGHTS)
        assertTrue(model.retryRead())
        assertFalse("duplicate Retry restarted the request", model.retryRead())
        faults.awaitSubscriptions(Feed.INSIGHTS, count + 1)
        assertTrue(model.uiState.awaitFirst { it.retryPending }.retryPending)
        assertEquals(complete.routines, model.uiState.value.routines)
        rejectEveryMutation()
        assertEquals(before, inventory())
        held.complete(Unit)
        val current = model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && !it.retryPending }
        assertTrue("fresh success did not replace the held board", current.routines.isEmpty())
        assertTrue(current.mutationEnabled)
        assertEquals(count + 1, faults.subscriptions(Feed.INSIGHTS))
        assertEquals(before, inventory())
    }

    @Test
    fun aFailedRetryUnlatchesTheButtonAndASecondExplicitRetryCanRecover() = runBlocking {
        graph()
        faults.failed.value = setOf(Feed.INSIGHTS)
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE }
        assertTrue(model.retryRead())
        faults.awaitSubscriptions(Feed.INSIGHTS, 2)
        model.uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE && !it.retryPending }
        assertFalse(model.startFreeWorkout())
        faults.failed.value = emptySet()
        assertTrue(model.retryRead())
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        assertEquals(3, faults.subscriptions(Feed.INSIGHTS))
    }

    @Test
    fun aSharedLiveReadRetriesWhileASiblingStaysSubscribedAndDoesNotWrite() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        val sibling = launch { graph.activityRepository.observeLiveHealth().collect() }
        try {
            startViewModel()
            val model = checkNotNull(vm)
            model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
            val count = faults.subscriptions(Feed.ACTIVITY)
            val before = inventory()
            faults.failed.value = setOf(Feed.ACTIVITY)
            model.uiState.awaitFirst { it.readState == HomeReadState.STALE }
            faults.failed.value = emptySet()
            val held = faults.hold(Feed.ACTIVITY)
            assertTrue(model.retryRead())
            faults.awaitSubscriptions(Feed.ACTIVITY, count + 1)
            assertTrue(model.uiState.awaitFirst { it.retryPending }.retryPending)
            assertFalse(model.startCardio(CardioType.WALK))
            held.complete(Unit)
            model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && !it.retryPending }
            assertEquals(before, inventory())
            assertTrue(sibling.isActive)
        } finally { sibling.cancelAndJoin() }
    }

    @Test
    fun requiredHealthBlocksActionsWhilePresentationComputationIsHeld() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        val queuedComputations = AtomicInteger()
        val latestComputeContext = java.util.concurrent.atomic.AtomicReference<CoroutineContext>()
        val scheduler = StandardTestDispatcher(dispatcher.scheduler)
        val compute = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queuedComputations.incrementAndGet()
                latestComputeContext.set(context)
                scheduler.dispatch(context, block)
            }
        }
        startViewModel(object : AppDependencies by graph { override val computeDispatcher = compute })
        val model = checkNotNull(vm)
        withTimeout(TestWaits.FLOW_MS) {
            while (model.uiState.value.readState != HomeReadState.CURRENT) {
                dispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
        val oldEnabledFrame = model.uiState.value
        val before = inventory()
        val queuedBefore = queuedComputations.get()
        insights.value = TrainingInsights() // Queues a new complete-board computation.
        withTimeout(TestWaits.FLOW_MS) {
            while (queuedComputations.get() <= queuedBefore) delay(10)
        }
        val heldCompute = checkNotNull(latestComputeContext.get()[Job])
        // A healthy replacement begins cancelling the held computation before the fault
        // arrives. Required health must stay live even while that join is blocked.
        insights.value = TrainingInsights(routines = oldEnabledFrame.routines)
        withTimeout(TestWaits.FLOW_MS) {
            while (!heldCompute.isCancelled) delay(10)
        }
        faults.failed.value = setOf(Feed.BODYWEIGHT)
        // Do not pump the compute dispatcher: cancellation/join remains queued there.
        model.uiState.awaitFirst { it.readState == HomeReadState.STALE }
        assertTrue(oldEnabledFrame.mutationEnabled)
        rejectEveryMutation() // The callback cannot trust that previously enabled frame.
        assertEquals(before, inventory())
        dispatcher.scheduler.runCurrent()
        assertEquals(HomeReadState.STALE, model.uiState.value.readState)
    }

    @Test
    fun aRefusedReminderIsNotConsumedDurablyAndRetryCannotStartItLater() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        val deliveryId = "read-refused-delivery"
        graph.database.plannerDao().upsertDelivery(ReminderDeliveryEntity(
            deliveryId, occurrence.id, 1L, ReminderDeliveryStatus.PENDING.name, 1L, 1L,
        ))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifications.show(app, occurrence, deliveryId, "Recovery Push")
        faults.failed.value = setOf(Feed.INSIGHTS)
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.UNAVAILABLE }
        val before = inventory()
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "refused-request"))
        assertEquals(before, inventory())
        assertTrue(notificationShown(occurrence.id))
        faults.failed.value = emptySet()
        assertTrue(model.retryRead())
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "refused-request"))
        assertNull(model.blockedByInProgress.value)
        assertNull(model.navigateToSession.value)
        assertEquals(before, inventory())
        assertEquals(ReminderDeliveryStatus.PENDING.name, graph.database.plannerDao().getDelivery(deliveryId)?.status)
        assertTrue(notificationShown(occurrence.id))
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "new-intentional-request"))
        model.blockedByInProgress.awaitFirst { it != null }
        assertEquals(before, inventory()) // One-live guard still owns the actual start.
    }

    @Test
    fun aFailedActualReminderStartIsTerminalForThatRequestAndLeavesItsDeliveryPending() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        val deliveryId = "failed-actual-start"
        graph.database.plannerDao().upsertDelivery(ReminderDeliveryEntity(
            deliveryId, occurrence.id, 1L, ReminderDeliveryStatus.PENDING.name, 1L, 1L,
        ))
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        val before = inventory()
        faults.failStartLookup = true
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "failed-start-request"))
        model.uiState.awaitFirst { it.error == com.sinura.personaltrainer.domain.ReminderCopy.START_FAILED }
        faults.failStartLookup = false
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "failed-start-request"))
        assertNull(model.blockedByInProgress.value)
        assertEquals(before, inventory())
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "new-start-request"))
        model.blockedByInProgress.awaitFirst { it != null }
        assertEquals(ReminderDeliveryStatus.PENDING.name, graph.database.plannerDao().getDelivery(deliveryId)?.status)
    }

    @Test
    fun aCurrentReminderStartsOnceAndMarksOnlyItsOwnDeliveryWhenTheActualSessionOpens() = runBlocking {
        graph()
        val graph = checkNotNull(deps)
        graph.database.workoutDao().deleteInProgressSession(liveId)
        graph.restTimerController.stop()
        val deliveryId = "current-start-delivery"
        graph.database.plannerDao().upsertDelivery(ReminderDeliveryEntity(
            deliveryId, occurrence.id, 1L, ReminderDeliveryStatus.PENDING.name, 1L, 1L,
        ))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifications.show(app, occurrence, deliveryId, "Recovery Push")
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && it.inProgress == null }
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "once-request"))
        val sessionId = model.navigateToSession.awaitFirst { it != null }
        assertEquals(sessionId, graph.workoutRepository.getInProgress()?.id)
        assertEquals(occurrence.id, PendingOccurrence.followedBy(graph, checkNotNull(sessionId)))
        assertNull("the planned binding cannot follow another session", PendingOccurrence.followedBy(graph, "other-session"))
        assertEquals(ReminderDeliveryStatus.STARTED.name, graph.database.plannerDao().getDelivery(deliveryId)?.status)
        assertFalse(notificationShown(occurrence.id))
        val after = inventory()
        assertTrue(model.dispatchPendingOccurrenceStart(occurrence.id, deliveryId, "once-request"))
        assertEquals(after, inventory())
        assertEquals(1, graph.database.workoutDao().getAllSessions().size)
    }

    @Test
    fun anAuthoredWeightSurvivesAFailedWriteAndSlowRetryAdmitsOnlyOneActualWrite() = runBlocking {
        graph()
        startViewModel()
        val model = checkNotNull(vm)
        val graph = checkNotNull(deps)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        val before = inventory()
        val writesBefore = faults.bodyweightWrites.get()
        faults.failBodyweightWrite = true
        assertTrue(model.recordBodyweight(87.5))
        val failed = model.uiState.awaitFirst { it.bodyweightSaveError != null }
        assertEquals(87.5, checkNotNull(failed.bodyweightDraftKg), 0.0)
        assertFalse(failed.bodyweightSavePending)
        assertFalse(model.bodyweightSaved.value)
        assertEquals(before, inventory())
        assertEquals(writesBefore + 1, faults.bodyweightWrites.get())

        faults.failBodyweightWrite = false
        val held = CompletableDeferred<Unit>()
        faults.heldBodyweightWrite = held
        assertTrue(model.recordBodyweight(87.5))
        assertFalse("duplicate Set admitted another write", model.recordBodyweight(87.5))
        assertFalse("duplicate IME admitted another write", model.recordBodyweight(88.0))
        assertFalse("Cancel pretended to stop an admitted write", model.discardBodyweightDraft())
        withTimeout(TestWaits.FLOW_MS) {
            while (faults.bodyweightWrites.get() != writesBefore + 2) delay(10)
        }
        val pending = model.uiState.awaitFirst { it.bodyweightSavePending }
        assertEquals(87.5, checkNotNull(pending.bodyweightDraftKg), 0.0)
        assertNull(pending.bodyweightSaveError)
        assertFalse(model.bodyweightSaved.value)
        assertEquals(before, inventory())
        faults.failed.value = setOf(Feed.INSIGHTS)
        model.uiState.awaitFirst { it.readState == HomeReadState.STALE }
        rejectEveryMutation()
        held.complete(Unit)
        model.bodyweightSaved.awaitFirst { it }
        model.uiState.awaitFirst { !it.bodyweightSavePending }
        val row = graph.database.bodyweightDao().getAll().single { it.epochDay == today }
        assertEquals(87.5, row.kg, 0.0)
        assertEquals(2, graph.database.bodyweightDao().getAll().size)
        assertEquals(writesBefore + 2, faults.bodyweightWrites.get())
        assertEquals(HomeReadState.STALE, model.uiState.value.readState)
        model.onBodyweightSaveHandled()
        assertFalse(model.bodyweightSaved.value)
        assertTrue(model.discardBodyweightDraft())
    }

    @Test
    fun aReplacedFallbackDayCannotStartFromAnOldConfirmation() = runBlocking {
        graph(seed = false)
        val graph = checkNotNull(deps)
        graph.preferencesRepository.setOnboardingComplete(true)
        val first = graph.routineRepository.create("Original Push")
        val second = graph.routineRepository.create("Replacement Pull")
        val lift = insertTestExercise(graph, "fallback-replacement-bench", "Bench Press")
        graph.routineRepository.addExercise(first.id, lift, 3, 8, 75.0, 90)
        graph.routineRepository.addExercise(second.id, lift, 2, 10, 55.0, 120)
        val original = SuggestedTrainingDay(
            epochDay = today, dayOfWeek = Weekday.fromEpochDay(today), isRest = false,
            focusKind = SessionFocusKind.PUSH, focusTitle = first.name, routineId = first.id,
            routineName = first.name, reason = "Synthetic fallback", emphasisMuscles = emptyList(),
            confidence = ScheduleConfidence.HIGH,
        )
        val plan = WeeklySchedulePlan(
            weekStartEpochDay = CivilDate.fromEpochDay(today).previousOrSame(Weekday.MONDAY).epochDay,
            generatedAtMs = graph.time.nowMillis(), preferences = SchedulePreferences(),
            resolvedSplit = SplitStyle.CUSTOM, days = listOf(original), thinHistory = true,
            summary = "Synthetic fallback replacement",
        )
        insights.value = TrainingInsights(weekPlan = plan, routines = listOf(first, second))
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && it.weekPlan == plan }
        val replacement = original.copy(
            routineId = second.id, routineName = second.name, focusTitle = second.name,
            focusKind = SessionFocusKind.PULL,
        )
        val replacementPlan = plan.copy(days = listOf(replacement))
        insights.value = insights.value.copy(weekPlan = replacementPlan)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT && it.weekPlan == replacementPlan }
        val before = inventory()
        assertFalse("an old confirmation cannot start a replaced day", model.startSuggestedDay(original))
        assertEquals(before, inventory())
        assertTrue(model.startSuggestedDay(replacement))
        val sessionId = model.navigateToSession.awaitFirst { it != null }
        val saved = graph.database.workoutDao().getAllSessions().single()
        assertEquals(sessionId, saved.id)
        assertEquals(second.id, saved.routineId)
    }

    @Test
    fun aSuccessfulWeightOwnsTheFieldUntilItsCloseIsAcknowledged() = runBlocking {
        graph()
        startViewModel()
        val model = checkNotNull(vm)
        val graph = checkNotNull(deps)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        val writesBefore = faults.bodyweightWrites.get()

        assertTrue(model.recordBodyweight(87.5))
        model.bodyweightSaved.awaitFirst { it }
        model.uiState.awaitFirst { !it.bodyweightSavePending }
        val saved = inventory()
        assertFalse("the unhandled success still owns editing", model.canEditBodyweightDraft())
        assertFalse("an old Set cannot admit another write before close", model.recordBodyweight(88.5))
        assertFalse("an old IME cannot admit another write before close", model.recordBodyweight(87.5))
        assertFalse("an old Cancel cannot discard the owned field", model.discardBodyweightDraft())
        assertEquals(saved, inventory())
        assertEquals(writesBefore + 1, faults.bodyweightWrites.get())
        assertEquals(87.5, graph.database.bodyweightDao().getAll().single { it.epochDay == today }.kg, 0.0)

        model.onBodyweightSaveHandled()
        assertTrue(model.canEditBodyweightDraft())
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        assertTrue("a newly opened field may save its intentional value", model.recordBodyweight(88.5))
        model.bodyweightSaved.awaitFirst { it }
        assertEquals(writesBefore + 2, faults.bodyweightWrites.get())
        val rows = graph.database.bodyweightDao().getAll()
        assertEquals(1, rows.count { it.epochDay == today })
        assertEquals(88.5, rows.single { it.epochDay == today }.kg, 0.0)
        model.onBodyweightSaveHandled()
    }

    @Test
    fun aLatePreferencesMirrorFailureKeepsTheAuthoredWeightAndNeverClaimsNothingWasWritten() = runBlocking {
        graph()
        startViewModel()
        val model = checkNotNull(vm)
        val graph = checkNotNull(deps)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        faults.failPreferenceWrite = true
        assertTrue(model.recordBodyweight(87.5))
        val failed = model.uiState.awaitFirst { it.bodyweightSaveError != null }
        assertEquals(87.5, checkNotNull(failed.bodyweightDraftKg), 0.0)
        assertFalse(model.bodyweightSaved.value)
        assertEquals(87.5, graph.database.bodyweightDao().getAll().single { it.epochDay == today }.kg, 0.0)
        assertTrue(checkNotNull(failed.bodyweightSaveError).contains("finish saving"))
        faults.failPreferenceWrite = false
        assertTrue(model.recordBodyweight(87.5))
        model.bodyweightSaved.awaitFirst { it }
        assertEquals(1, graph.database.bodyweightDao().getAll().count { it.epochDay == today })
        assertEquals(87.5, graph.database.bodyweightDao().getAll().single { it.epochDay == today }.kg, 0.0)
    }

    @Test
    fun invalidWeightsCannotProduceASavedEventOrATableWrite() = runBlocking {
        graph()
        startViewModel()
        val model = checkNotNull(vm)
        model.uiState.awaitFirst { it.readState == HomeReadState.CURRENT }
        val before = inventory()
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 1000.0)) {
            assertFalse(model.recordBodyweight(invalid))
            assertFalse(model.bodyweightSaved.value)
        }
        assertEquals(before, inventory())
        assertTrue(model.discardBodyweightDraft())
    }

    private fun rejectEveryMutation() {
        val model = checkNotNull(vm)
        val agenda = listOf(AgendaItem(occurrence, null, "Recovery Push"))
        val attempted = linkedMapOf(
            "free" to model.startFreeWorkout(),
            "routine" to model.startRoutine(routineId),
            "cardio" to model.startCardio(CardioType.WALK),
            "extra before routine mint" to model.startAux("golf"),
            "suggested" to model.startSuggestedDay(suggestedDay),
            "planned" to model.startOccurrence(occurrence.id),
            "leftover before relocation" to model.startOccurrence(leftover.id),
            "missed work" to model.applyMissedWork(MissedWorkChoice.KEEP_DATES),
            "skip" to model.skipOccurrence(leftover.id),
            "undo before offer clear" to model.undoSkipOccurrence(),
            "discard before dialog clear" to model.discardBlockedAndStart(),
            "add routine" to model.addDaySession(today, HomeDayAdd.Workout(routineId), false),
            "add new routine" to model.addDaySession(today, HomeDayAdd.NewWorkout, true),
            "add cardio" to model.addDaySession(today, HomeDayAdd.Cardio(CardioType.WALK), true),
            "add extra" to model.addDaySession(today, HomeDayAdd.Aux("golf"), true),
            "reorder" to model.moveDayBlock(agenda, occurrence.id, 1),
            "weigh in" to model.recordBodyweight(93.5),
        )
        attempted.forEach { (action, accepted) -> assertFalse("$action was admitted", accepted) }
    }

    /** Every relevant table is read through the original database DAO, never its fault wrapper. */
    private suspend fun inventory(): List<Any?> {
        val graph = checkNotNull(deps)
        val db = graph.database
        return listOf(
            db.workoutDao().getAllSessions(), db.workoutDao().getAllSessionExercises(), db.workoutDao().getAllSets(),
            db.activityDao().getAllGraphs(), db.activityDao().getAllTemplateGraphs(),
            db.routineDao().getAllRoutines(), db.routineDao().getAllRoutineExercises(), db.exerciseDao().getAll(),
            db.scheduleDao().getAll(), db.plannerDao().getRules(), db.plannerDao().getAllOccurrences(),
            db.plannerDao().getDecisions(), db.plannerDao().getDeliveries(), db.bodyweightDao().getAll(),
            graph.rawPreferenceValues(), graph.pendingOccurrenceId.value,
            graph.workoutDraftCache.all(liveId), graph.workoutDraftCache.selectedExerciseId(liveId),
            graph.workoutDraftCache.sessionNotes(liveId), graph.workoutDraftCache.pendingSave(liveId),
            graph.workoutDraftCache.editingOriginal(liveId), graph.workoutDraftCache.finishing.value,
            graph.restTimerController.snapshot.value, graph.cardioTimerPersistence.load(), reminders.calls.toList(),
            app.getSystemService(NotificationManager::class.java).activeNotifications.map { it.id }.sorted(),
        )
    }

    private fun notificationShown(id: String): Boolean =
        app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == id.hashCode() }

    private enum class Feed { INSIGHTS, WORKOUT, ACTIVITY, OCCURRENCES, RULES, DECISIONS, BODYWEIGHT, PREFERENCES }

    /** A failed cold observation ends; opening the gate alone never invents recovery. */
    private class ReadFaults {
        val failed = MutableStateFlow<Set<Feed>>(emptySet())
        @Volatile var failStartLookup = false
        val bodyweightWrites = AtomicInteger()
        @Volatile var failBodyweightWrite = false
        @Volatile var failPreferenceWrite = false
        @Volatile var heldBodyweightWrite: CompletableDeferred<Unit>? = null
        private val counts = ConcurrentHashMap<Feed, AtomicInteger>()
        private val barriers = ConcurrentHashMap<Feed, CompletableDeferred<Unit>>()
        fun subscriptions(feed: Feed): Int = counts[feed]?.get() ?: 0
        fun hold(feed: Feed): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { barriers[feed] = it }
        fun <T> read(feed: Feed, actual: Flow<T>): Flow<T> = flow {
            counts.computeIfAbsent(feed) { AtomicInteger() }.incrementAndGet()
            barriers[feed]?.await()
            emitAll(combine(actual, failed) { value, failures ->
                check(feed !in failures) { "Required read failed: $feed" }
                value
            })
        }
        suspend fun awaitSubscriptions(feed: Feed, count: Int) = withTimeout(TestWaits.FLOW_MS) {
            while (subscriptions(feed) < count) delay(10)
        }
    }

    private class RecordingReminders : ReminderScheduler {
        val calls = mutableListOf<String>()
        override fun schedule(delivery: ReminderDelivery) { calls += "schedule:${delivery.id}" }
        override fun cancel(deliveryId: String) { calls += "cancel:$deliveryId" }
        override fun cancelForOccurrence(occurrenceId: String) { calls += "cancelOccurrence:$occurrenceId" }
        override fun dismissShown(occurrenceId: String) { calls += "dismiss:$occurrenceId" }
    }
}
