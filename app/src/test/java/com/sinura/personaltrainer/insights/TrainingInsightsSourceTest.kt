package com.sinura.personaltrainer.insights

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseRecencyRow
import com.sinura.personaltrainer.data.local.entity.SessionStillRow
import com.sinura.personaltrainer.data.local.entity.SessionSummaryRow
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.InsightFailure
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingInsightsCalculator
import com.sinura.personaltrainer.domain.TrainingInsightsInput
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ReadGate
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.catchingUncaught
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home, Plan, and the start sheet share one pipeline. A cancelled pass must not
 * become empty hints, and the calculator must not run on main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TrainingInsightsSourceTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val inputs = java.util.Collections.synchronizedList(mutableListOf<TrainingInsightsInput>())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        inputs.clear()
    }

    @After
    fun tearDown() {
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun sharedCollectorsExecuteOneComputeAndReplayTheSameInstance() = runBlocking {
        val src = source()
        val first = async { src.observeShared(includeWeekPlan = true).first() }
        val second = async { src.observeShared(includeWeekPlan = true).first() }
        val a = first.await()
        val b = second.await()
        assertSame(a, b)
        assertEquals(1, inputs.size)
    }

    @Test
    fun observeCollectorsShareTheAssembly() = runBlocking {
        val src = source()
        val first = async { src.observe(includeWeekPlan = true).first() }
        val second = async { src.observe(includeWeekPlan = true).first() }
        val a = first.await()
        val b = second.await()
        assertSame(a, b)
        assertEquals(1, inputs.size)
    }

    @Test
    fun loggingASetOnAnInProgressSessionDoesNotRecomputeInsights() = runBlocking {
        val seeded = seedTestWorkout(deps, finish = false)
        val src = source()
        val job = launch { src.observeShared(includeWeekPlan = false).collect { } }
        awaitComputes(1)
        deps.workoutRepository.logSet(
            sessionId = seeded.session.id,
            exerciseId = seeded.exercise.id,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        // Picker recency must see the live set — that is the leak's trigger.
        withTimeout(TestWaits.FLOW_MS) {
            deps.workoutRepository.observeLastLogged().first {
                it[seeded.exercise.id] != null
            }
        }
        repeat(10) {
            dispatcher.scheduler.runCurrent()
            delay(10)
        }
        assertEquals(1, inputs.size)
        job.cancel()
    }

    @Test
    fun bodyWindowChipRetargetsOnceWithoutAFullCompute() = runBlocking {
        val emissions = mutableListOf<TrainingInsights>()
        val src = source()
        val job = launch {
            src.observe(
                window = deps.preferencesRepository.heatWindow,
                includeWeekPlan = false,
            ).collect { emissions.add(it) }
        }
        awaitUntil {
            inputs.size == 1 &&
                emissions.any { it.snapshot?.window == HeatWindow.CURRENT_WEEK }
        }
        val computes = inputs.size
        val before = emissions.size
        deps.preferencesRepository.setHeatWindow(HeatWindow.CURRENT_MONTH)
        awaitUntil { emissions.any { it.snapshot?.window == HeatWindow.CURRENT_MONTH } }
        assertEquals(computes, inputs.size)
        assertEquals(before + 1, emissions.size)
        assertEquals(1, emissions.count { it.snapshot?.window == HeatWindow.CURRENT_MONTH })
        job.cancel()
    }

    @Test
    fun flippingTheHeatWindowDoesNotReloadHintsOrRecomputeTheCoach() = runBlocking {
        val window = MutableStateFlow(HeatWindow.CURRENT_WEEK)
        var loads = 0
        val emissions = mutableListOf<TrainingInsights>()
        val src = source(
            loadHints = { _, _, _ ->
                loads++
                emptyList()
            },
        )
        val job = launch {
            src.observe(window = window, includeWeekPlan = false).collect { emissions.add(it) }
        }
        awaitUntil { loads == 1 && emissions.any { it.snapshot?.window == HeatWindow.CURRENT_WEEK } }
        window.value = HeatWindow.CURRENT_MONTH
        awaitUntil { emissions.any { it.snapshot?.window == HeatWindow.CURRENT_MONTH } }
        assertEquals(1, loads)
        assertEquals(1, inputs.size)
        job.cancel()
    }

    @Test
    fun withAndWithoutWeekAreIndependentSharedPipelines() = runBlocking {
        val src = source()
        val withA = async { src.observeShared(includeWeekPlan = true).first() }
        val withB = async { src.observeShared(includeWeekPlan = true).first() }
        val without = async { src.observeShared(includeWeekPlan = false).first() }
        assertSame(withA.await(), withB.await())
        assertNotSame(withA.await(), without.await())
        assertEquals(2, inputs.size)
        assertEquals(1, inputs.count { it.includeWeekPlan })
        assertEquals(1, inputs.count { !it.includeWeekPlan })
        assertNotNull(withA.await().weekPlan)
        assertEquals(null, without.await().weekPlan)
    }

    @Test
    fun resubscribeWithinGraceReplaysWithoutRecompute() = runBlocking {
        val src = source()
        val first = src.observeShared(includeWeekPlan = true).first()
        assertEquals(1, inputs.size)
        val second = src.observeShared(includeWeekPlan = true).first()
        assertSame(first, second)
        assertEquals(1, inputs.size)
    }

    @Test
    fun resubscribeAfterGraceRecomputes() = runBlocking {
        // Grace shrunk and the wait widened: this used to sleep 80 ms against a 50 ms grace,
        // a 1.6x margin on Dispatchers.Default, a shared pool. Losing that race leaves the
        // share still cached, the resubscribe replays instead of recomputing, and
        // awaitComputes(2) dies as a 5 s timeout. Same property under test, 20x the margin.
        // The sibling resubscribeWithinGraceReplaysWithoutRecompute uses source()'s default
        // grace, not this override, so shrinking here cannot make that one tighter.
        val src = source(computeDispatcher = Dispatchers.Default, shareGraceMs = 10)
        val first = launch { src.observeShared(includeWeekPlan = true).collect { } }
        awaitComputes(1)
        first.cancel()
        delay(200)
        val second = launch { src.observeShared(includeWeekPlan = true).collect { } }
        awaitComputes(2)
        second.cancel()
    }

    @Test
    fun refreshForcesRecomputeOnUnchangedInputs() = runBlocking {
        val refresh = MutableStateFlow(0)
        val src = source()
        val job = launch { src.observe(refresh = refresh, includeWeekPlan = false).collect { } }
        awaitComputes(1)
        refresh.value = 1
        awaitComputes(2)
        assertEquals(inputs[0].history, inputs[1].history)
        job.cancel()
    }

    @Test
    fun latestInputCancelsTheInFlightPass() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        var loads = 0
        val emissions = mutableListOf<TrainingInsights>()
        val src = source(
            loadHints = { _, _, _ ->
                val n = ++loads
                if (n == 1) {
                    firstStarted.complete(Unit)
                    awaitCancellation()
                }
                emptyList()
            },
        )
        val job = launch { src.observe(includeWeekPlan = false).collect { emissions.add(it) } }
        firstStarted.await()
        assertTrue(emissions.isEmpty())
        seedTestWorkout(deps, finish = true)
        awaitUntil { loads >= 2 && emissions.any { it.history.size == 1 } }
        assertTrue(loads >= 2)
        assertEquals(1, emissions.last().history.size)
        assertTrue(emissions.none { it.failed(InsightFailure.PROGRESSION) })
        job.cancel()
    }

    @Test
    fun cancellationDoesNotBecomeProgressionFallback() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val emissions = mutableListOf<TrainingInsights>()
        val src = source(
            loadHints = { _, _, _ ->
                if (!firstStarted.isCompleted) {
                    firstStarted.complete(Unit)
                    awaitCancellation()
                }
                emptyList()
            },
        )
        val job = launch { src.observe(includeWeekPlan = false).collect { emissions.add(it) } }
        firstStarted.await()
        assertTrue(emissions.isEmpty())
        seedTestWorkout(deps, finish = true)
        awaitUntil { emissions.any { it.history.size == 1 } }
        assertTrue(emissions.none { it.failed(InsightFailure.PROGRESSION) })
        assertEquals(1, emissions.last().history.size)
        job.cancel()
    }

    @Test
    fun hintFailureIsolatesToProgression() = runBlocking {
        val src = source(loadHints = { _, _, _ -> error("hints down") })
        val insights = src.observe(includeWeekPlan = false).first()
        assertTrue(insights.failed(InsightFailure.PROGRESSION))
        assertNotNull(insights.snapshot)
        assertTrue(insights.hints.isEmpty())
        assertFalse(insights.failed(InsightFailure.HEAT))
    }

    @Test
    fun computeRunsOffMain() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "insights-compute")
        }
        executor.asCoroutineDispatcher().use { computeDispatcher ->
            var computeThread: Thread? = null
            val src = source(
                computeDispatcher = computeDispatcher,
                compute = { input ->
                    computeThread = Thread.currentThread()
                    TrainingInsightsCalculator.compute(input)
                },
            )
            src.observe(includeWeekPlan = false).first()
            assertTrue(computeThread?.name?.startsWith("insights-compute") == true)
        }
    }

    private fun source(
        computeDispatcher: CoroutineDispatcher = dispatcher,
        shareGraceMs: Long = TrainingInsightsSource.SHARE_GRACE_MS,
        compute: (TrainingInsightsInput) -> TrainingInsights = { input ->
            inputs += input
            TrainingInsightsCalculator.compute(input)
        },
        loadHints: suspend (List<Routine>, WeightUnit, Boolean) -> List<ProgressionHint> =
            { _, _, _ -> emptyList() },
    ): TrainingInsightsSource = TrainingInsightsSource(
        workoutRepository = deps.workoutRepository,
        routineRepository = deps.routineRepository,
        exerciseRepository = deps.exerciseRepository,
        preferencesRepository = deps.preferencesRepository,
        scheduleRepository = deps.scheduleRepository,
        activityRepository = deps.activityRepository,
        computeDispatcher = computeDispatcher,
        nowMs = { NOW_MS },
        zone = { ZONE },
        compute = compute,
        loadHints = loadHints,
        shareGraceMs = shareGraceMs,
    )

    /**
     * Audit DB-1: four of the pipeline's inputs skipped the read guard the others use, and
     * its scope had no handler, so one failed history read closed the app, and Home, which
     * subscribes on its first frame, closed it again on every open. Now a read that fails
     * holds what it last had and the summary keeps updating from everything else. One test
     * per strength read; the activity read is the first-read case below.
     */
    @Test
    fun aSummariesReadThatFailsKeepsHomeUpdatingInsteadOfClosingTheApp() =
        assertHomeOutlivesAFailed(InsightRead.SUMMARIES) { it.summaries }

    @Test
    fun aRecentWorkoutsReadThatFailsKeepsHomeUpdatingInsteadOfClosingTheApp() =
        assertHomeOutlivesAFailed(InsightRead.RECENT_SESSIONS) { it.history }

    @Test
    fun aLastLoggedReadThatFailsKeepsHomeUpdatingInsteadOfClosingTheApp() =
        assertHomeOutlivesAFailed(InsightRead.LAST_LOGGED) { it.lastLoggedAtByExerciseId }

    /**
     * A read that fails before its first value has nothing to hold. It must still not close
     * the app; the summary waits for the next visit (F4 makes that wait a read-error state).
     */
    @Test
    fun anActivityReadThatFailsFirstDoesNotCloseTheApp() = runBlocking {
        val gate = ReadGate(shouldFail = true)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { dao -> FailingActivityStillsDao(dao, gate) },
        )
        val src = source()
        val crash = catchingUncaught {
            val job = launch { src.observeShared(includeWeekPlan = false).collect { } }
            awaitUntil { gate.refusals.get() > 0 }
            settle()
            job.cancel()
        }
        assertNull("a failed first activity read closed the app", crash)
    }

    private fun assertHomeOutlivesAFailed(
        read: InsightRead,
        held: (TrainingInsightsInput) -> Any?,
    ) = runBlocking {
        var failing: FailingInsightReadsDao? = null
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { dao -> FailingInsightReadsDao(dao, read).also { failing = it } },
        )
        val reads = checkNotNull(failing)
        val seeded = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)), finish = true)
        val src = source()
        var afterFailure: TrainingInsightsInput? = null
        var beforeFailure: Any? = null
        val crash = catchingUncaught {
            val job = launch { src.observeShared(includeWeekPlan = false).collect { } }
            awaitComputes(1)
            beforeFailure = held(inputs.last())
            reads.shouldFail = true
            // Another finished lift moves every finished-work read, so the failing one runs.
            val another = deps.workoutRepository.startFreeWorkout("Push")
            deps.workoutRepository.logSet(
                sessionId = another.id,
                exerciseId = seeded.exercise.id,
                weightKg = 105.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
            )
            deps.workoutRepository.finishSession(another.id, notes = "")
            awaitUntil { reads.refusals.get() > 0 }
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            afterFailure = withTimeoutOrNull(TestWaits.FLOW_MS) {
                var recomputed: TrainingInsightsInput? = null
                while (recomputed == null) {
                    dispatcher.scheduler.runCurrent()
                    delay(10)
                    recomputed = inputs.lastOrNull { it.unit == WeightUnit.KG }
                }
                recomputed
            }
            job.cancel()
        }
        assertNull("a failed ${read.name} read closed the app", crash)
        val recomputed = checkNotNull(afterFailure) { "Home stopped updating after one failed ${read.name} read" }
        assertEquals("the failed read must hold what it had", beforeFailure, held(recomputed))
    }

    /**
     * Audit AR-1: a pass that throws while assembling used to end the process and leave the
     * shared summary with nothing to restart it. It now ends that pass; the next visit after
     * the grace period assembles again.
     */
    @Test
    fun aSummaryThatFailsToAssembleDoesNotCloseTheAppAndTheNextVisitTriesAgain() = runBlocking {
        val attempts = AtomicInteger()
        val src = source(
            shareGraceMs = 0L,
            compute = { input ->
                if (attempts.getAndIncrement() == 0) error("boom: the summary could not be computed")
                inputs += input
                TrainingInsightsCalculator.compute(input)
            },
        )
        var nextVisit: TrainingInsights? = null
        val crash = catchingUncaught {
            val firstVisit = launch { src.observeShared(includeWeekPlan = false).collect { } }
            awaitUntil { attempts.get() > 0 }
            settle()
            firstVisit.cancel()
            settle()
            nextVisit = withTimeoutOrNull(TestWaits.FLOW_MS) {
                src.observeShared(includeWeekPlan = false).first()
            }
        }
        assertNull("a failed pass closed the app", crash)
        assertNotNull("the next visit never assembled the summary again", nextVisit)
    }

    private suspend fun settle() = repeat(5) {
        dispatcher.scheduler.runCurrent()
        delay(10)
    }

    private suspend fun awaitComputes(count: Int) = awaitUntil { inputs.size >= count }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(TestWaits.FLOW_MS) {
            while (!predicate()) {
                dispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
    }

    private companion object {
        const val NOW_MS = 1_713_441_600_000L
        val ZONE = ZoneOffset.UTC
    }
}

/** The three finished-work reads the insights pipeline makes of the workout store. */
private enum class InsightRead { SUMMARIES, RECENT_SESSIONS, LAST_LOGGED }

/** One of the pipeline's workout reads made to refuse on demand, as a Room fault would. */
private class FailingInsightReadsDao(
    private val delegate: WorkoutDao,
    private val read: InsightRead,
) : WorkoutDao by delegate {
    @Volatile var shouldFail = false
    val refusals = AtomicInteger()

    override suspend fun sessionSummaries(): List<SessionSummaryRow> {
        refuseIf(InsightRead.SUMMARIES)
        return delegate.sessionSummaries()
    }

    override suspend fun getFinishedSessionsSince(minDateMs: Long): List<SessionWithDetails> {
        refuseIf(InsightRead.RECENT_SESSIONS)
        return delegate.getFinishedSessionsSince(minDateMs)
    }

    override suspend fun finishedLastLogged(): List<ExerciseRecencyRow> {
        refuseIf(InsightRead.LAST_LOGGED)
        return delegate.finishedLastLogged()
    }

    private fun refuseIf(which: InsightRead) {
        if (shouldFail && read == which) {
            refusals.incrementAndGet()
            throw SQLiteException("boom: Room could not read ${which.name}")
        }
    }
}

/** The activity summaries' stills read, refused while the gate is shut. */
private class FailingActivityStillsDao(
    private val delegate: ActivityDao,
    private val gate: ReadGate,
) : ActivityDao by delegate {
    override suspend fun completedSessionStills(): List<SessionStillRow> {
        gate.check("the activity history")
        return delegate.completedSessionStills()
    }
}
