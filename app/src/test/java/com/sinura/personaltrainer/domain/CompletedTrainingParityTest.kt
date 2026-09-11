package com.sinura.personaltrainer.domain

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.RestoreWitness
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityEditCopy
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingGetGraphDao
import com.sinura.personaltrainer.testutil.FailingObserveActivityRecordSetsDao
import com.sinura.personaltrainer.testutil.FailingObserveSessionDao
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.WorkoutReadGate
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.activity.ActivityDetailViewModel
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * The seven-row table in completed-training-convergence.md §4, run against
 * the four fixtures: a finished strength session, a backdated strength
 * activity, a cardio-only activity, and a mixed day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompletedTrainingParityTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var history: HistoryViewModel? = null
    private var activityDetail: ActivityDetailViewModel? = null
    private var sessionDetail: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            history?.clearAndJoinForTest()
            activityDetail?.clearAndJoinForTest()
            sessionDetail?.clearAndJoinForTest()
        }
        history = null
        activityDetail = null
        sessionDetail = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun chronologyAppearsOnceOnTheLocalDate() = runBlocking {
        val fixtures = seedFour()
        val vm = openHistory()
        val state = vm.uiState.awaitFirst { it.summaries.size == 4 && !it.isLoading }

        val ids = state.summaries.map { it.id }
        assertEquals(4, ids.toSet().size)
        assertTrue(ids.containsAll(fixtures.ids))

        assertEquals(fixtures.strengthDay, summary(state, fixtures.strengthId).localEpochDay)
        assertEquals(fixtures.backdatedDay, summary(state, fixtures.backdatedId).localEpochDay)
        assertEquals(fixtures.cardioDay, summary(state, fixtures.cardioId).localEpochDay)
        assertEquals(fixtures.mixedDay, summary(state, fixtures.mixedId).localEpochDay)

        val months = state.monthGroups.associate { it.month to it.entries.map { entry -> entry.id }.toSet() }
        val currentMonth = CivilYearMonth.from(CivilDate.fromEpochDay(fixtures.strengthDay))
        val previousMonth = CivilYearMonth.from(CivilDate.fromEpochDay(fixtures.backdatedDay))
        assertTrue(months.getValue(currentMonth).containsAll(
            setOf(fixtures.strengthId, fixtures.cardioId, fixtures.mixedId),
        ))
        assertTrue(months.getValue(previousMonth).contains(fixtures.backdatedId))

        val todayCell = state.calendar.weeks.flatten().single {
            it.date.epochDay == fixtures.strengthDay && it.inMonth
        }
        assertTrue(todayCell.sessionIds.contains(fixtures.strengthId))

        vm.showPreviousMonth()
        val previous = vm.uiState.awaitFirst {
            it.calendar.month == previousMonth &&
                it.calendar.weeks.flatten().any { day -> day.activityIds.contains(fixtures.backdatedId) }
        }
        val backdatedCell = previous.calendar.weeks.flatten().single {
            it.date.epochDay == fixtures.backdatedDay && it.inMonth
        }
        assertTrue(backdatedCell.activityIds.contains(fixtures.backdatedId))

        assertEquals(fixtures.strengthId, state.summaries.latest()?.id)
    }

    @Test
    fun totalsMatchTheKnownSetsVolumeAndCardio() = runBlocking {
        val fixtures = seedFour()
        val summaries = mergedSummaries()
        val today = CivilDate.fromEpochDay(fixtures.strengthDay)
        val projections = DailyProjectionBuilder.project(summaries)
        val allTime = HorizonMath.totals(
            horizon = AnalyticsHorizon.ALL_TIME,
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
        )
        assertEquals(4, allTime.sessionCount)
        assertEquals(3, allTime.workingSets)
        assertEquals(1_500.0, allTime.volumeKg, 0.0001)
        assertEquals(2_400L, allTime.cardioSeconds)
        assertEquals(7_000.0, allTime.cardioDistanceMeters, 0.0001)

        val vm = openHistory()
        vm.setHorizon(AnalyticsHorizon.ALL_TIME)
        val state = vm.uiState.awaitFirst {
            it.horizon == AnalyticsHorizon.ALL_TIME &&
                it.horizonTotals?.sessionCount == 4 &&
                !it.isLoading
        }
        val shown = checkNotNull(state.horizonTotals)
        assertEquals(3, shown.workingSets)
        assertEquals(1_500.0, shown.volumeKg, 0.0001)
        assertEquals(2_400L, shown.cardioSeconds)
        assertEquals(7_000.0, shown.cardioDistanceMeters, 0.0001)
    }

    @Test
    fun recordsKeepTheBestAndAnEditMovesItWhereSupported() = runBlocking {
        val fixtures = seedFour()
        val standing = standingNow()
        val squat = standing.single { it.exerciseId == fixtures.exerciseId }
        assertEquals(110.0, squat.valueKg, 0.0001)

        val weaker = standingNow()
        assertEquals(110.0, weaker.single { it.exerciseId == fixtures.exerciseId }.valueKg, 0.0001)

        val session = checkNotNull(deps.workoutRepository.getSession(fixtures.strengthId))
        val setId = session.sets.single().id
        deps.workoutRepository.updateSet(setId, 125.0, 5, rpe = null, isWarmup = false)
        val moved = standingNow().single { it.exerciseId == fixtures.exerciseId }
        assertEquals(125.0, moved.valueKg, 0.0001)

        val mixed = checkNotNull(deps.activityRepository.get(fixtures.mixedId))
        val mixedSet = mixed.strengthBlocks.single().sets.single()
        val refused = deps.activityRepository.updateStrengthSet(
            setId = mixedSet.id,
            weightKg = 200.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        assertTrue(refused is ActivityWrite.Rejected)
        assertEquals(ActivityEditCopy.SET_REPAIR_REFUSED, (refused as ActivityWrite.Rejected).reason)
        assertEquals(90.0, deps.activityRepository.get(fixtures.mixedId)!!.strengthBlocks.single().sets.single().weightKg, 0.0)
    }

    @Test
    fun horizonReadoutCountsTheFixtureRecordOnce() = runBlocking {
        val fixtures = seedFour()
        val items = deps.completedTrainingRepository.all()
        val earliest = items.minOf { it.localEpochDay }
        val progress = BlockReviewBuilder.overRange(
            startEpochDay = earliest,
            endExclusiveEpochDay = fixtures.strengthDay + 1,
            items = items,
            unit = WeightUnit.KG,
        )
        assertTrue(progress.recordsBroken > 0)

        val vm = openHistory()
        vm.setHorizon(AnalyticsHorizon.ALL_TIME)
        val state = vm.uiState.awaitFirst {
            it.horizon == AnalyticsHorizon.ALL_TIME &&
                (it.horizonProgress?.recordsBroken ?: 0) > 0
        }
        assertEquals(progress.recordsBroken, state.horizonProgress!!.recordsBroken)
    }

    @Test
    fun editsArePerCapability() = runBlocking {
        val fixtures = seedFour()
        val session = checkNotNull(deps.workoutRepository.getSession(fixtures.strengthId))
        val setId = session.sets.single().id
        val deleted = checkNotNull(deps.workoutRepository.deleteSet(setId))
        assertEquals(0.0, checkNotNull(deps.workoutRepository.getSession(fixtures.strengthId)).work().volumeKg, 0.0)
        deps.workoutRepository.restoreSet(deleted)
        assertEquals(500.0, checkNotNull(deps.workoutRepository.getSession(fixtures.strengthId)).work().volumeKg, 0.0001)

        assertEquals(
            ActivityEditCopy.REPEAT_REFUSED,
            (deps.activityRepository.repeatCompleted(fixtures.backdatedId) as ActivityWrite.Rejected).reason,
        )
        assertEquals(
            ActivityEditCopy.SET_REPAIR_REFUSED,
            (deps.activityRepository.updateStrengthSet("any", 200.0, 5, null, false) as ActivityWrite.Rejected).reason,
        )

        val notes = deps.activityRepository.updateCompletedNotes(
            sessionId = fixtures.cardioId,
            notes = "easy",
            nowMs = JvmTime.captureNow().instantMillis,
        )
        assertTrue(notes is ActivityWrite.Accepted)
        assertEquals("easy", (notes as ActivityWrite.Accepted).session.notes)
        assertEquals("easy", deps.activityRepository.get(fixtures.cardioId)!!.notes)

        val removed = deps.activityRepository.deleteCompleted(fixtures.mixedId)
        assertTrue(removed is ActivityWrite.Accepted)
        assertNull(deps.activityRepository.get(fixtures.mixedId))

        val names = ActivityDetailViewModel::class.java.methods.map { it.name }.toSet()
        assertFalse(names.contains("updateSet"))
        assertFalse(names.contains("repeatSession"))
        assertFalse(names.contains("addSet"))
    }

    @Test
    fun restoreKeepsTheFourIdsAndTheWitness() = runBlocking {
        val fixtures = seedFour()
        val json = deps.backupService.exportJson()
        val document = BackupJson.decode(json)
        val witness = RestoreWitness.of(document)
        assertTrue(document.sessions.any { it.id == fixtures.strengthId && it.finishedAt != null })
        assertTrue(document.activities.any { it.id == fixtures.backdatedId })
        assertTrue(document.activities.any { it.id == fixtures.cardioId })
        assertTrue(document.activities.any { it.id == fixtures.mixedId })

        val plan = deps.backupService.prepareRestore(json, sourceName = "parity.json")
        deps.backupService.commitRestore(plan)

        assertEquals(witness, RestoreWitness.of(BackupJson.decode(deps.backupService.exportJson())))
        assertEquals(fixtures.strengthId, deps.workoutRepository.getSession(fixtures.strengthId)?.id)
        assertEquals(fixtures.backdatedId, deps.activityRepository.get(fixtures.backdatedId)?.id)
        assertEquals(fixtures.cardioId, deps.activityRepository.get(fixtures.cardioId)?.id)
        assertEquals(fixtures.mixedId, deps.activityRepository.get(fixtures.mixedId)?.id)
    }

    @Test
    fun aThrownReadIsFailedNotMissingAndRetryDoesNotDuplicate() = runBlocking {
        val activityGate = ActivityReadGate(shouldFail = false)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            activityDaoDecorator = { FailingGetGraphDao(it, activityGate) },
        )
        val activityFixtures = seedFour()
        val before = deps.activityRepository.all().size
        activityGate.shouldFail = true
        val activityVm = ActivityDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("activityId" to activityFixtures.cardioId)),
            container = deps,
        ).also { activityDetail = it }
        val failedActivity = activityVm.uiState.awaitFirst { !it.isLoading }
        assertTrue(failedActivity.failed)
        assertFalse(failedActivity.missing)

        activityGate.shouldFail = false
        activityVm.retry()
        val loadedActivity = activityVm.uiState.awaitFirst { !it.isLoading && !it.failed }
        assertEquals(activityFixtures.cardioId, loadedActivity.session?.id)
        assertEquals(before, deps.activityRepository.all().size)

        val workoutGate = WorkoutReadGate(shouldFail = false)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            workoutDaoDecorator = { FailingObserveSessionDao(it, workoutGate) },
        )
        val sessionFixtures = seedFour()
        workoutGate.shouldFail = true
        val sessionVm = SessionDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionFixtures.strengthId)),
            container = deps,
        ).also { sessionDetail = it }
        val failedSession = sessionVm.uiState.awaitFirst { !it.isLoading }
        assertTrue(failedSession.failed)
        assertFalse(failedSession.missing)
        workoutGate.shouldFail = false
        sessionVm.retry()
        val loadedSession = sessionVm.uiState.awaitFirst { !it.isLoading && !it.failed }
        assertEquals(sessionFixtures.strengthId, loadedSession.session?.id)

        val historyGate = ActivityReadGate(shouldFail = true)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            activityDaoDecorator = { FailingObserveActivityRecordSetsDao(it, historyGate) },
        )
        val historyFixtures = seedFour()
        val historyVm = HistoryViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
        ).also { history = it }
        val stale = historyVm.uiState.awaitFirst { !it.isLoading && it.stale }
        assertTrue(stale.stale)
        assertFalse(stale.unavailable)
        assertEquals(1, stale.summaries.filter { it.id == historyFixtures.strengthId }.size)

        historyGate.shouldFail = false
        historyVm.retryHistory()
        val recovered = historyVm.uiState.awaitFirst { !it.isLoading && !it.stale && it.summaries.size == 4 }
        assertEquals(4, recovered.summaries.map { it.id }.toSet().size)
        assertEquals(3, deps.activityRepository.all().size)
        assertNotNull(deps.workoutRepository.getSession(historyFixtures.strengthId))
    }

    private suspend fun standingNow(): List<PrSummaryRow> {
        val workouts = deps.workoutRepository.observeRecordSetsHealth().first().presentValue().orEmpty()
        val activities = deps.activityRepository.observeRecordSetsHealth().first().presentValue().orEmpty()
        return RecordsCalculator.standing(workouts + activities)
    }

    private suspend fun mergedSummaries(): List<SessionSummary> {
        val workouts = deps.workoutRepository.observeSessionSummariesHealth().first().presentValue().orEmpty()
        val activities = deps.activityRepository.observeCompletedSummariesHealth().first().presentValue().orEmpty()
        return workouts + activities
    }

    private fun openHistory(): HistoryViewModel =
        HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
            .also { history = it }

    private fun summary(
        state: com.sinura.personaltrainer.ui.history.HistoryUiState,
        id: String,
    ) = state.summaries.single { it.id == id }

    private suspend fun seedFour(): FourFixtures {
        val now = JvmTime.captureNow()
        val today = CivilDate.fromEpochDay(now.localEpochDay)
        val backdatedDay = CivilDate(today.year, today.month, 1).minusDays(5).epochDay
        val cardioDay = today.minusDays(3).epochDay
        val mixedDay = today.minusDays(1).epochDay
        val strength = seedTestWorkout(
            deps = deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )
        val backdatedAt = JvmTime.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(backdatedDay), hour = 10, minute = 0),
            now.zoneId,
        )
        val cardioAt = JvmTime.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(cardioDay), hour = 7, minute = 0),
            now.zoneId,
        )
        val mixedAt = JvmTime.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(mixedDay), hour = 18, minute = 0),
            now.zoneId,
        )
        val backdated = acceptedActivity(
            title = "Make-up squat",
            at = backdatedAt,
            blocks = listOf(
                squatBlock(
                    id = "blk-backdated",
                    setId = "set-backdated",
                    exerciseId = strength.exercise.id,
                    weightKg = 110.0,
                    completedAtMs = now.instantMillis + 1_000L,
                ),
            ),
        )
        val cardio = acceptedActivity(
            title = "Easy run",
            at = cardioAt,
            blocks = listOf(
                runBlock(id = "blk-cardio", elapsedSeconds = 1_800L, distanceMeters = 5_000.0),
            ),
        )
        val mixed = acceptedActivity(
            title = "Mixed day",
            at = mixedAt,
            blocks = listOf(
                squatBlock(
                    id = "blk-mixed",
                    setId = "set-mixed",
                    exerciseId = strength.exercise.id,
                    weightKg = 90.0,
                    completedAtMs = mixedAt.instantMillis,
                ),
                runBlock(id = "blk-mixed-run", elapsedSeconds = 600L, distanceMeters = 2_000.0),
            ),
        )
        return FourFixtures(
            exerciseId = strength.exercise.id,
            strengthId = strength.session.id,
            backdatedId = backdated.id,
            cardioId = cardio.id,
            mixedId = mixed.id,
            strengthDay = checkNotNull(deps.workoutRepository.getSession(strength.session.id))
                .toSummary(JvmTime).localEpochDay,
            backdatedDay = backdated.localEpochDay,
            cardioDay = cardio.localEpochDay,
            mixedDay = mixed.localEpochDay,
        )
    }

    private suspend fun acceptedActivity(
        title: String,
        at: CapturedCivilTime,
        blocks: List<ActivityBlock>,
    ): ActivitySession {
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = title,
                performedStart = at,
                performedEnd = at,
                blocks = blocks,
            ),
            JvmTime.captureNow(),
        )
        return (write as ActivityWrite.Accepted).session
    }

    private fun squatBlock(
        id: String,
        setId: String,
        exerciseId: String,
        weightKg: Double,
        completedAtMs: Long,
    ) = StrengthBlock(
        id = id,
        sortOrder = 0,
        exerciseId = exerciseId,
        exerciseName = "Test squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = setId,
                setNumber = 1,
                weightKg = weightKg,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAtMs = completedAtMs,
            ),
        ),
    )

    private fun runBlock(
        id: String,
        elapsedSeconds: Long,
        distanceMeters: Double,
    ) = CardioBlock(
        id = id,
        sortOrder = 1,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = elapsedSeconds,
        movingSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )

    private data class FourFixtures(
        val exerciseId: String,
        val strengthId: String,
        val backdatedId: String,
        val cardioId: String,
        val mixedId: String,
        val strengthDay: Long,
        val backdatedDay: Long,
        val cardioDay: Long,
        val mixedDay: Long,
    ) {
        val ids: Set<String>
            get() = setOf(strengthId, backdatedId, cardioId, mixedId)
    }
}
