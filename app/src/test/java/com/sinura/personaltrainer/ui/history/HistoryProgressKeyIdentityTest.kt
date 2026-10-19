package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.testutil.FailingPastBlocksDao
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.ReadGate
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TH2 / UX32: scoped summaries identity in [HistoryViewModel]'s horizon progress key.
 * Harness mirrors [HistoryPeriodViewModelTest] (file-private gates cannot be reused).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryProgressKeyIdentityTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val reads = KeyIdentityFullLogReads()
    private val secondary = KeyIdentitySecondaryReads()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<HistoryViewModel>()
    private val emissions = mutableMapOf<HistoryViewModel, CopyOnWriteArrayList<HistoryUiState>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = FrozenTime(stamp(TODAY), "UTC"),
            activityDaoDecorator = { KeyIdentityHeldActivityDao(it, reads, secondary) },
            workoutDaoDecorator = { KeyIdentitySummaryFaultDao(it, secondary) },
            trainingBlockDaoDecorator = { FailingPastBlocksDao(it, secondary.blocks) },
        )
        runBlocking { insertTestExercise(deps, id = EXERCISE, name = "Bench", muscleGroup = "Chest") }
    }

    @After
    fun tearDown() {
        reads.releaseAll()
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        models.clear()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    /** T1: summaries move before revision; no stale READY on the changed request. */
    @Test
    fun summariesChangeWithProgressReadHeldNeverAttachStaleReady() = runBlocking {
        seedProgress()
        withModel { model ->
            awaitReady(model)
            val start = trace(model).size
            val held = reads.holdNext(late = true)
            deps.workoutRepository.updateSet("closing-set", 90.0, 5, 8, false)
            held.awaitEntered()
            val changed = awaitState(model) { it.horizonTotals?.volumeKg == 950.0 }
            assertTrue(changed.progressLoading)
            assertNull(changed.horizonProgress)
            assertPendingEmissions(model, start) { it.horizonTotals?.volumeKg == 950.0 }
            held.release.complete(Unit)
            assertEquals(950.0, awaitReady(model).horizonTotals!!.volumeKg, 0.0)
        }
    }

    /** T2: no summaries or revision change → no progress reload flash. */
    @Test
    fun identicalScopedSummariesAndRevisionKeepReadyProgressAttached() = runBlocking {
        seedProgress()
        withModel { model ->
            val ready = awaitReady(model)
            val attached = ready.horizonProgress
            val afterReady = trace(model).size
            dispatcher.scheduler.advanceUntilIdle()
            val latest = model.uiState.value
            assertFalse(latest.progressLoading)
            assertEquals(attached, latest.horizonProgress)
            trace(model).drop(afterReady).forEach { state ->
                assertFalse(
                    "unexpected progress reload with unchanged summaries: $state",
                    state.progressLoading && state.horizonTotals?.volumeKg == ready.horizonTotals?.volumeKg,
                )
            }
        }
    }

    /** T3: revision-only change still reloads horizon progress. */
    @Test
    fun revisionChangeWithSameScopedSummariesReloadsProgress() = runBlocking {
        seedProgress()
        withModel { model ->
            val ready = awaitReady(model)
            val start = trace(model).size
            val held = reads.holdNext(late = true)
            // RPE-only edit: scoped summary totals stay 550 kg / 550 kg; finished-work revision moves.
            deps.workoutRepository.updateSet("closing-set", 110.0, 5, 9, false)
            held.awaitEntered()
            val pending = awaitState(model) {
                it.progressLoading && it.horizonProgress == null &&
                    it.horizonTotals?.volumeKg == ready.horizonTotals?.volumeKg
            }
            assertFalse(pending.progressFailed)
            assertPendingEmissions(model, start) {
                it.progressLoading && it.horizonProgress == null &&
                    it.horizonTotals?.volumeKg == ready.horizonTotals?.volumeKg
            }
            held.release.complete(Unit)
            awaitReady(model)
        }
    }

    /** T4: edit outside the visible range does not change the progress key. */
    @Test
    fun outOfRangeSummaryEditDoesNotReloadVisibleProgress() = runBlocking {
        seedProgress()
        insertWorkout("outside-range", day(2024, 10, 15), 200.0)
        withModel { model ->
            val ready = awaitReady(model)
            val progress = ready.horizonProgress
            deps.workoutRepository.updateSet("outside-range-set", 50.0, 5, 8, false)
            dispatcher.scheduler.advanceUntilIdle()
            val latest = model.uiState.value
            assertEquals(progress, latest.horizonProgress)
            assertFalse(latest.progressLoading)
            assertEquals(ready.horizonTotals?.volumeKg, latest.horizonTotals?.volumeKg)
        }
    }

    private fun model(saved: SavedStateHandle = SavedStateHandle()): HistoryViewModel =
        HistoryViewModel(ApplicationProvider.getApplicationContext(), saved, deps).also(models::add)

    private suspend fun withModel(block: suspend (HistoryViewModel) -> Unit) = coroutineScope {
        val model = model()
        emissions[model] = CopyOnWriteArrayList()
        val subscriber = launch(dispatcher) {
            model.uiState.collect { state -> emissions[model]?.add(state) }
        }
        try {
            block(model)
        } finally {
            reads.releaseAll()
            model.clearAndJoinForTest()
            subscriber.cancel()
        }
    }

    private suspend fun awaitReady(model: HistoryViewModel): HistoryUiState = awaitState(model) {
        !it.isLoading && !it.unavailable && !it.progressLoading && !it.progressFailed && it.horizonProgress != null
    }

    private fun trace(model: HistoryViewModel): List<HistoryUiState> = checkNotNull(emissions[model]).toList()

    private fun assertPendingEmissions(
        model: HistoryViewModel,
        start: Int,
        changedIdentity: (HistoryUiState) -> Boolean,
    ) {
        val changed = trace(model).drop(start).filter(changedIdentity)
        assertTrue("no public state for the changed request was captured", changed.isNotEmpty())
        changed.forEach { state ->
            assertNull("old ready progress leaked into a changed request: $state", state.horizonProgress)
            assertTrue("changed request was neither hidden nor loading: $state", state.progressLoading)
            assertFalse("an old failure leaked into a pending request: $state", state.progressFailed)
        }
    }

    private suspend fun awaitState(model: HistoryViewModel, ready: (HistoryUiState) -> Boolean): HistoryUiState =
        withTimeout(TestWaits.FLOW_MS) { model.uiState.first(ready) }

    private suspend fun seedProgress() {
        insertWorkout("opening", day(2024, 11, 1), 100.0)
        insertWorkout("closing", day(2024, 11, 24), 110.0)
    }

    private suspend fun insertWorkout(id: String, localDay: Long, kg: Double) {
        val at = stamp(localDay)
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                routineName = "Synthetic $id",
                date = at,
                notes = "",
                durationMinutes = 10,
                startedAt = at,
                finishedAt = at + 600_000L,
            ),
        )
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "$id-set",
                sessionId = id,
                exerciseId = EXERCISE,
                setNumber = 1,
                weightKg = kg,
                reps = 5,
                rpe = 8,
                isWarmup = false,
                completedAt = at + 6_000L,
            ),
        )
    }

    private companion object {
        const val EXERCISE = "history-key-identity-bench"
        val TODAY = day(2024, 11, 25)
        fun day(year: Int, month: Int, day: Int) = CivilDate(year, month, day).epochDay
        fun stamp(epochDay: Long) = epochDay * 86_400_000L + 43_200_000L
    }
}

private class KeyIdentityHeldActivityDao(
    private val delegate: ActivityDao,
    private val reads: KeyIdentityFullLogReads,
    private val secondary: KeyIdentitySecondaryReads,
) : ActivityDao by delegate {
    override fun observeCompletedStrengthSetRecords(): Flow<List<RecordSetRow>> =
        delegate.observeCompletedStrengthSetRecords().map { rows ->
            secondary.records.check("period lifetime activity records")
            rows
        }

    override suspend fun getAllGraphs(): List<com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph> {
        reads.calls.incrementAndGet()
        val result = delegate.getAllGraphs()
        val held = reads.next.getAndSet(null) ?: reads.all.get() ?: return result
        held.entered.complete(Unit)
        if (held.late) withContext(NonCancellable) { held.release.await() }
        else held.release.await()
        return result
    }
}

private class KeyIdentityFullLogReads {
    val calls = AtomicInteger()
    val next = AtomicReference<KeyIdentityHeldRead?>()
    val all = AtomicReference<KeyIdentityHeldRead?>()
    private val held = mutableListOf<KeyIdentityHeldRead>()

    fun holdNext(late: Boolean = false): KeyIdentityHeldRead {
        val read = KeyIdentityHeldRead(late)
        check(next.compareAndSet(null, read)) { "another full-log read is already armed" }
        held += read
        return read
    }

    fun releaseAll() {
        all.set(null)
        held.forEach { it.release.complete(Unit) }
    }
}

private class KeyIdentitySecondaryReads {
    val records = ReadGate(false)
    val summaries = ReadGate(false)
    val blocks = ReadGate(false)
}

private class KeyIdentitySummaryFaultDao(
    private val delegate: WorkoutDao,
    private val secondary: KeyIdentitySecondaryReads,
) : WorkoutDao by delegate {
    override suspend fun sessionSummaries(): List<com.sinura.personaltrainer.data.local.entity.SessionSummaryRow> {
        val rows = delegate.sessionSummaries()
        secondary.summaries.check("period primary workout summaries")
        return rows
    }
}

private class KeyIdentityHeldRead(val late: Boolean) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    suspend fun awaitEntered() = withTimeout(TestWaits.FLOW_MS) { entered.await() }
}
