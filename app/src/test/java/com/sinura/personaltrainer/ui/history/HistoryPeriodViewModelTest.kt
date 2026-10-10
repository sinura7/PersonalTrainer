package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.RecordSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SessionSummaryRow
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.FailingPastBlocksDao
import com.sinura.personaltrainer.testutil.ReadGate
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Period orchestration over real isolated Room/DataStore; no manufactured progress results. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryPeriodViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val reads = PeriodFullLogReads()
    private val secondary = PeriodSecondaryReads()
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
            activityDaoDecorator = { PeriodHeldActivityDao(it, reads, secondary) },
            workoutDaoDecorator = { PeriodSummaryFaultDao(it, secondary) },
            trainingBlockDaoDecorator = { FailingPastBlocksDao(it, secondary.blocks) },
        )
        runBlocking { insertTestExercise(deps, id = EXERCISE, name = "Bench", muscleGroup = "Chest") }
    }

    @After
    fun tearDown() {
        // A deliberately noncancellable old query must be released before awaiting its VM.
        reads.releaseAll()
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        models.clear()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theInitialSnapshotUsesActualTodayAndTheAndroidSavedStateConstructorExists() {
        val model = model()
        assertEquals(TODAY, model.uiState.value.today.epochDay)
        assertEquals(CivilYearMonth(2024, 11), model.uiState.value.calendar.month)
        assertEquals(HistoryPeriodSelection(AnalyticsHorizon.MONTH, TODAY, true), model.uiState.value.selection)
        assertNull(model.uiState.value.periodRange)
        assertTrue(model.uiState.value.recordsLoading)
        assertTrue(model.uiState.value.blocksLoading)
        assertNotNull(HistoryViewModel::class.java.getConstructor(Application::class.java, SavedStateHandle::class.java))
    }

    @Test
    fun unreadLifetimeSectionsAreUnavailableAndOnlySuccessfulRecoveryCanDeclareThemEmpty() = runBlocking {
        secondary.records.shouldFail = true
        secondary.blocks.shouldFail = true
        withModel { model ->
            val failed = awaitState(model) { !it.isLoading && it.recordsUnavailable && it.blocksUnavailable }
            assertFalse(failed.recordsLoading)
            assertFalse(failed.blocksLoading)
            assertTrue(failed.records.isEmpty())
            assertTrue(failed.pastBlocks.isEmpty())
            assertTrue(failed.blocksStale)
            secondary.records.shouldFail = false
            secondary.blocks.shouldFail = false
            model.retryHistory()
            val recovered = awaitState(model) {
                !it.isLoading && !it.recordsUnavailable && !it.blocksUnavailable &&
                    !it.recordsLoading && !it.blocksLoading && !it.recordsStale &&
                    !it.blocksStale && !it.progressLoading
            }
            assertTrue(recovered.records.isEmpty())
            assertTrue(recovered.pastBlocks.isEmpty())
        }
    }

    @Test
    fun lifetimeValuesAndTheirHealthSurviveAnUnavailablePrimaryList() = runBlocking {
        seedPastBlock()
        withModel { model ->
            val shown = awaitState(model) { !it.isLoading && it.records.isNotEmpty() && it.pastBlocks.isNotEmpty() }
            val firstUnavailableEmission = trace(model).size
            secondary.summaries.shouldFail = true
            model.retryHistory()
            val unavailable = awaitState(model) { it.unavailable && !it.blocksLoading }
            assertNull(unavailable.periodRange)
            assertEquals(shown.records, unavailable.records)
            assertEquals(shown.pastBlocks, unavailable.pastBlocks)
            assertFalse(unavailable.recordsLoading)
            assertFalse(unavailable.recordsUnavailable)
            assertFalse(unavailable.recordsStale)
            assertFalse(unavailable.blocksLoading)
            assertFalse(unavailable.blocksUnavailable)
            assertFalse(unavailable.blocksStale)
            assertEmptyBlockEmissionsAreLoading(model, firstUnavailableEmission)
            val firstRecoveryEmission = trace(model).size
            secondary.summaries.shouldFail = false
            model.retryHistory()
            val recovered = awaitState(model) {
                !it.isLoading && !it.unavailable && !it.progressLoading && !it.progressFailed &&
                    it.horizonProgress != null && !it.blocksLoading
            }
            assertEquals(shown.records, recovered.records)
            assertEquals(shown.pastBlocks, recovered.pastBlocks)
            assertEmptyBlockEmissionsAreLoading(model, firstRecoveryEmission)
        }
    }

    @Test
    fun anInitiallyFailedBlockReviewIsUnknownThenARecoverableLaterFailureRetainsTheReview() = runBlocking {
        seedPastBlock()
        reads.failAll.set(true)
        withModel { model ->
            val unknown = awaitState(model) { !it.isLoading && it.blocksUnavailable && it.blocksStale }
            assertTrue(unknown.pastBlocks.isEmpty())
            reads.failAll.set(false)
            model.retryHistory()
            val known = awaitState(model) { !it.blocksUnavailable && !it.blocksStale && it.pastBlocks.isNotEmpty() }
            reads.failAll.set(true)
            // Only a review input moves; the known block remains exactly the same.
            deps.preferencesRepository.recordBodyweight(80.0, TODAY)
            val retained = awaitState(model) { it.blocksStale && !it.blocksUnavailable }
            assertEquals(known.pastBlocks, retained.pastBlocks)
        }
    }

    @Test
    fun aFailedBlockListReadDoesNotReplacePreviouslyVerifiedReviewsWithAnArtificialEmptyList() = runBlocking {
        seedPastBlock()
        withModel { model ->
            val known = awaitState(model) { !it.isLoading && it.pastBlocks.isNotEmpty() && !it.blocksStale }
            val firstFailedEmission = trace(model).size
            secondary.blocks.shouldFail = true
            model.retryHistory()
            val retained = awaitState(model) { it.blocksStale }
            assertFalse(retained.blocksUnavailable)
            assertEquals(known.pastBlocks, retained.pastBlocks)
            assertEmptyBlockEmissionsAreLoading(model, firstFailedEmission)
            val firstRecoveryEmission = trace(model).size
            secondary.blocks.shouldFail = false
            model.retryHistory()
            val restored = awaitState(model) { !it.blocksLoading && !it.blocksStale && !it.blocksUnavailable }
            assertEquals(known.pastBlocks, restored.pastBlocks)
            assertEmptyBlockEmissionsAreLoading(model, firstRecoveryEmission)
        }
    }

    @Test
    fun theSelectedPeriodRoundTripsPrimitiveSavedStateIntoANewViewModel() = runBlocking {
        val handle = SavedStateHandle()
        withModel(handle) { first ->
            awaitReady(first)
            first.selectDay(day(2024, 10, 12))
            first.setHorizon(AnalyticsHorizon.WEEK)
            val selected = awaitState(first) {
                it.selection.horizon == AnalyticsHorizon.WEEK && !it.selection.followToday
            }
            assertEquals(day(2024, 10, 12), selected.selection.anchorEpochDay)
            val saved = handle.keys().associateWith { handle.get<Any?>(it) }
            assertEquals(3, saved.size)
            assertTrue(saved.values.all { it is String || it is Long || it is Boolean })

            withModel(SavedStateHandle(saved)) { restored ->
                val state = awaitReady(restored)
                assertEquals(selected.selection, state.selection)
                assertEquals(selected.periodRange, state.periodRange)
                assertEquals(CivilYearMonth(2024, 10), state.calendar.month)
                assertEquals(TODAY, state.today.epochDay)
            }
        }
    }

    @Test
    fun malformedPartialOrUnsupportedSavedSelectionsReturnToCurrentMonth() {
        val corrupt = listOf(
            emptyMap(),
            mapOf(SAVED_HORIZON to "WEEK"),
            saved("UNKNOWN", TODAY, false),
            saved("DAY", "2024-11-01", false),
            saved("DAY", TODAY.toInt(), false),
            saved("DAY", TODAY, "false"),
            saved("DAY", Long.MIN_VALUE, false),
            saved("DAY", Long.MAX_VALUE, false),
        )
        corrupt.forEach { values ->
            val handle = SavedStateHandle(values)
            val model = model(handle)
            assertEquals(values.toString(), HistoryPeriodSelection(AnalyticsHorizon.MONTH, TODAY, true), model.uiState.value.selection)
            assertEquals("MONTH", handle.get<String>(SAVED_HORIZON))
            assertEquals(TODAY, handle.get<Long>(SAVED_ANCHOR))
            assertEquals(true, handle.get<Boolean>(SAVED_FOLLOW))
        }
    }

    @Test
    fun aValidFutureSavedAnchorResolvesToTodayWithoutLosingTheSavedChoice() = runBlocking {
        val future = day(2025, 2, 1)
        withModel(SavedStateHandle(saved("DAY", future, false))) { model ->
            val state = awaitReady(model)
            assertEquals(future, state.selection.anchorEpochDay)
            assertEquals(HistoryPeriodRange(TODAY, TODAY + 1), state.periodRange)
            assertFalse(state.canGoNext)
            model.selectDay(TODAY + 1)
            assertEquals(future, model.uiState.value.selection.anchorEpochDay)
        }
    }

    @Test
    fun midnightChangesCurrentSelectionWithoutADaoWriteWhileHistoricalDayStaysFixed() = runBlocking {
        insertWorkout("today", TODAY, 100.0)
        insertWorkout("tomorrow", TODAY + 1, 110.0)
        withModel { model ->
            awaitReady(model)
            model.setHorizon(AnalyticsHorizon.DAY)
            val before = awaitState(model) { !it.progressLoading && it.horizon == AnalyticsHorizon.DAY }
            assertEquals(setOf("today"), before.summaries.map { it.id }.toSet())
            val beforeReads = reads.calls.get()
            model.updateToday(TODAY + 1)
            val current = awaitState(model) { !it.progressLoading && it.today.epochDay == TODAY + 1 }
            assertEquals(setOf("tomorrow"), current.summaries.map { it.id }.toSet())
            assertEquals(HistoryPeriodRange(TODAY + 1, TODAY + 2), current.periodRange)
            assertTrue(current.selection.followToday)
            assertTrue(reads.calls.get() > beforeReads)

            model.selectDay(TODAY)
            val fixed = awaitState(model) { !it.progressLoading && !it.selection.followToday }
            val fixedReads = reads.calls.get()
            model.updateToday(TODAY + 2)
            val later = awaitState(model) { it.today.epochDay == TODAY + 2 && !it.progressLoading }
            assertEquals(fixed.selection, later.selection)
            assertEquals(fixed.periodRange, later.periodRange)
            assertEquals(setOf("today"), later.summaries.map { it.id }.toSet())
            assertEquals("unchanged historical range needs no full-log reread", fixedReads, reads.calls.get())
        }
    }

    @Test
    fun listTotalsGroupsAndCalendarUseTheSameHalfOpenMixedSourceRange() = runBlocking {
        insertWorkout("before", day(2024, 10, 31), 90.0)
        insertWorkout("start", day(2024, 11, 1), 100.0)
        insertWorkout("today", TODAY, 110.0)
        // Known completed work retains its captured date ahead of device Today.
        insertWorkout("after", TODAY + 1, 120.0)
        insertWorkout("outside-next-month", day(2024, 12, 1), 130.0)
        val activityId = insertCardio(day(2024, 11, 10))
        withModel { model ->
            val state = awaitReady(model)
            val expected = setOf(
                HistoryKind.WORKOUT to "start", HistoryKind.WORKOUT to "today",
                HistoryKind.WORKOUT to "after", HistoryKind.ACTIVITY to activityId,
            )
            assertEquals(HistoryPeriodRange(day(2024, 11, 1), TODAY + 2), state.periodRange)
            assertEquals(expected, state.summaries.map { it.kind to it.id }.toSet())
            assertEquals(expected, state.monthGroups.flatMap { it.entries }.map { it.kind to it.id }.toSet())
            assertEquals(4, state.horizonTotals!!.sessionCount)
            assertEquals(3, state.horizonTotals!!.workingSets)
            assertEquals(1650.0, state.horizonTotals!!.volumeKg, 0.0)
            assertEquals(600L, state.horizonTotals!!.cardioSeconds)
            assertEquals(1000.0, state.horizonTotals!!.cardioDistanceMeters, 0.0)
            val calendarIds = state.calendar.weeks.flatten().flatMap { date ->
                date.sessionIds.map { HistoryKind.WORKOUT to it } + date.activityIds.map { HistoryKind.ACTIVITY to it }
            }.toSet()
            assertEquals(expected, calendarIds)

            model.selectMonth(CivilYearMonth(2024, 10))
            val october = awaitState(model) { it.calendar.month == CivilYearMonth(2024, 10) && !it.progressLoading }
            assertEquals(HistoryPeriodRange(day(2024, 10, 1), day(2024, 11, 1)), october.periodRange)
            assertEquals(setOf("before"), october.summaries.map { it.id }.toSet())
            assertEquals(1, october.horizonTotals!!.sessionCount)
        }
    }

    @Test
    fun anEmptySelectedDayHasZeroComputedTotalsAndLifetimeRecordsStayAvailable() = runBlocking {
        insertWorkout("old", day(2024, 10, 10), 150.0)
        withModel { model ->
            awaitReady(model)
            model.selectDay(day(2024, 11, 12))
            val empty = awaitState(model) { it.horizon == AnalyticsHorizon.DAY && !it.progressLoading }
            assertTrue(empty.summaries.isEmpty())
            assertTrue(empty.monthGroups.isEmpty())
            assertEquals(0, empty.horizonTotals!!.sessionCount)
            assertEquals(0, empty.horizonProgress!!.recordsBroken)
            assertFalse(empty.progressFailed)
            assertFalse(empty.unavailable)
            assertEquals(150.0, empty.records.single().valueKg, 0.0)
            assertFalse(empty.calendar.weeks.flatten().any { it.trained })
        }
    }

    @Test
    fun periodProgressStillJudgesRecordsAgainstEarlierWorkOutsideTheSelectedPeriod() = runBlocking {
        insertWorkout("prior", day(2024, 10, 25), 150.0)
        insertWorkout("opening", day(2024, 11, 1), 100.0)
        insertWorkout("closing", day(2024, 11, 24), 110.0)
        withModel { model ->
            val state = awaitReady(model)
            assertEquals(setOf("opening", "closing"), state.summaries.map { it.id }.toSet())
            assertEquals(0, state.horizonProgress!!.recordsBroken)
            assertEquals(EXERCISE, state.horizonProgress!!.movedMost!!.exerciseId)
            assertEquals(150.0, state.records.single().valueKg, 0.0)
        }
    }

    @Test
    fun aHeldCancelledOldPeriodCannotPublishIntoTheLatestDay() = runBlocking {
        seedProgress()
        withModel { model ->
            assertEquals(2, awaitReady(model).horizonProgress!!.recordsBroken)
            val firstTransition = trace(model).size
            val old = reads.holdNext(late = true)
            model.selectDay(day(2024, 11, 1))
            old.awaitEntered()
            val firstPending = awaitState(model) { it.selection.anchorEpochDay == day(2024, 11, 1) }
            assertTrue(firstPending.progressLoading)
            assertNull(firstPending.horizonProgress)
            assertPendingEmissions(model, firstTransition) { it.selection.anchorEpochDay == day(2024, 11, 1) }

            val latestTransition = trace(model).size
            model.selectDay(day(2024, 11, 24))
            val latestPending = awaitState(model) { it.selection.anchorEpochDay == day(2024, 11, 24) }
            assertTrue(latestPending.progressLoading)
            assertNull(latestPending.horizonProgress)
            assertEquals(setOf("closing"), latestPending.summaries.map { it.id }.toSet())
            assertFalse(old.returned.get())
            assertPendingEmissions(model, latestTransition) { it.selection.anchorEpochDay == day(2024, 11, 24) }
            old.release.complete(Unit)

            val latest = awaitState(model) { it.selection.anchorEpochDay == day(2024, 11, 24) && !it.progressLoading }
            assertTrue("the actual cancelled DAO read returned late", old.returned.get())
            assertEquals(2, latest.horizonProgress!!.recordsBroken)
            assertEquals(HistoryPeriodRange(day(2024, 11, 24), day(2024, 11, 25)), latest.periodRange)
            trace(model).drop(latestTransition).filter {
                it.selection.anchorEpochDay == day(2024, 11, 24) && !it.progressLoading
            }.forEach { assertEquals("late opening-day result attached to the latest day", 2,
                it.horizonProgress!!.recordsBroken) }
        }
    }

    @Test
    fun changingUnitsHidesOldFormattedProgressWhileTheNewRealReadWaits() = runBlocking {
        seedProgress()
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        withModel { model ->
            val pounds = awaitReady(model)
            assertTrue(pounds.horizonProgress!!.movedMost!!.fromLabel.endsWith("lb"))
            val changedUnit = trace(model).size
            val held = reads.holdNext()
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            held.awaitEntered()
            val pending = awaitState(model) { it.unit == WeightUnit.KG }
            assertTrue(pending.progressLoading)
            assertNull(pending.horizonProgress)
            assertFalse(pending.progressFailed)
            assertPendingEmissions(model, changedUnit) { it.unit == WeightUnit.KG }
            held.release.complete(Unit)
            val kilograms = awaitState(model) { it.unit == WeightUnit.KG && !it.progressLoading }
            assertTrue(kilograms.horizonProgress!!.movedMost!!.fromLabel.endsWith("kg"))
            assertEquals(pounds.horizonProgress!!.recordsBroken, kilograms.horizonProgress!!.recordsBroken)
            trace(model).drop(changedUnit).filter { it.unit == WeightUnit.KG && !it.progressLoading }
                .forEach { assertTrue("pound-formatted progress attached to kilogram state",
                    it.horizonProgress!!.movedMost!!.fromLabel.endsWith("kg")) }
        }
    }

    @Test
    fun aDelayedCorrectionRecorderExposesTheUnacknowledgedPendingTrace() = runBlocking {
        seedProgress()
        val recorderEntered = CompletableDeferred<Unit>()
        val recorderRelease = CompletableDeferred<Unit>()
        val recorded = CompletableDeferred<Unit>()
        val firstCorrection = AtomicBoolean(false)
        withModel(beforeRecord = { state ->
            if (state.horizonTotals?.volumeKg == 950.0 && firstCorrection.compareAndSet(false, true)) {
                recorderEntered.complete(Unit)
                withTimeout(TestWaits.FLOW_MS) { recorderRelease.await() }
                recorded
            } else null
        }) { model ->
            try {
                awaitReady(model)
                val correction = trace(model).size
                val held = reads.holdNext(late = true)
                deps.workoutRepository.updateSet("closing-set", 90.0, 5, 8, false)
                held.awaitEntered()
                val changed = awaitState(model) { it.horizonTotals?.volumeKg == 950.0 }
                assertTrue(changed.progressLoading)
                assertNull(changed.horizonProgress)
                withTimeout(TestWaits.FLOW_MS) { recorderEntered.await() }
                assertFalse("direct observation does not acknowledge the separate recorder append",
                    trace(model).drop(correction).any { it.horizonTotals?.volumeKg == 950.0 })
                assertFalse(recorderRelease.isCompleted)
                recorderRelease.complete(Unit)
                withTimeout(TestWaits.FLOW_MS) { recorded.await() }
                assertPendingEmissions(model, correction) { it.horizonTotals?.volumeKg == 950.0 }
                held.release.complete(Unit)
                assertEquals(950.0, awaitReady(model).horizonTotals!!.volumeKg, 0.0)
            } finally {
                recorderRelease.complete(Unit)
                reads.releaseAll()
            }
        }
    }

    @Test
    fun rapidExactRowCorrectionsHideTheOldRevisionAndIgnoreItsLateResult() = runBlocking {
        seedProgress()
        val correctionRecorded = CompletableDeferred<Unit>()
        val newestRecorded = CompletableDeferred<Unit>()
        val readyRecorded = CompletableDeferred<Unit>()
        val readyRequested = AtomicBoolean(false)
        withModel(beforeRecord = { state ->
            // Direct uiState.first and this recorder are independent subscribers.
            // Acknowledge after withModel appends, without excluding invalid pending states.
            when (state.horizonTotals?.volumeKg) {
                950.0 -> correctionRecorded
                1200.0 -> if (readyRequested.get() && !state.progressLoading) readyRecorded else newestRecorded
                else -> null
            }
        }) { model ->
            val opening = awaitReady(model)
            assertEquals(2, opening.horizonProgress!!.recordsBroken)
            assertNotNull(opening.horizonProgress!!.movedMost)
            val original = checkNotNull(deps.database.workoutDao().getSet("closing-set"))
            val correction = trace(model).size
            val held = reads.holdNext(late = true)
            deps.workoutRepository.updateSet(original.id, 90.0, 5, 8, false)
            held.awaitEntered()
            val changed = awaitState(model) { it.horizonTotals?.volumeKg == 950.0 }
            assertTrue(changed.progressLoading)
            assertNull(changed.horizonProgress)
            withTimeout(TestWaits.FLOW_MS) { correctionRecorded.await() }
            assertPendingEmissions(model, correction) { it.horizonTotals?.volumeKg == 950.0 }
            val nextCorrection = trace(model).size
            deps.workoutRepository.updateSet(original.id, 140.0, 5, 8, false)
            val newest = awaitState(model) { it.horizonTotals?.volumeKg == 1200.0 }
            assertTrue(newest.progressLoading)
            assertNull(newest.horizonProgress)
            assertEquals(original.copy(weightKg = 140.0), deps.database.workoutDao().getSet(original.id))
            withTimeout(TestWaits.FLOW_MS) { newestRecorded.await() }
            assertPendingEmissions(model, nextCorrection) { it.horizonTotals?.volumeKg == 1200.0 }
            readyRequested.set(true)
            held.release.complete(Unit)
            val ready = awaitState(model) { it.horizonTotals?.volumeKg == 1200.0 && !it.progressLoading }
            withTimeout(TestWaits.FLOW_MS) { readyRecorded.await() }
            assertTrue(held.returned.get())
            assertEquals(2, ready.horizonProgress!!.recordsBroken)
            assertEquals(setOf("opening", "closing"), ready.summaries.map { it.id }.toSet())
            assertTrue("old correction's formatted estimate remained on the latest row",
                ready.horizonProgress!!.movedMost!!.toLabel != opening.horizonProgress!!.movedMost!!.toLabel)
            trace(model).drop(nextCorrection).filter {
                it.horizonTotals?.volumeKg == 1200.0 && !it.progressLoading
            }.forEach {
                assertEquals("the held 90kg correction result attached to the newer 140kg row", 2,
                    it.horizonProgress!!.recordsBroken)
                assertEquals(ready.horizonProgress!!.movedMost!!.toLabel,
                    it.horizonProgress!!.movedMost!!.toLabel)
            }
        }
    }

    @Test
    fun retryHasItsOwnIdentityAndAFailedReadCannotMasqueradeAsZeroOrOldSuccess() = runBlocking {
        seedProgress()
        val armedPending = AtomicReference<CompletableDeferred<Unit>?>()
        val recordedPending = AtomicReference<HistoryUiState?>()
        val heldRequest = AtomicReference<PeriodHeldRead?>()
        val readyRecorded = CompletableDeferred<Unit>()
        val failedRecorded = CompletableDeferred<Unit>()
        withModel(beforeRecord = { state ->
            if (recordedPending.get() != null && heldRequest.get()?.release?.isCompleted == false) {
                assertTrue("every recorded state after the acknowledged boundary stays loading: $state", state.progressLoading)
                assertNull("every recorded state after the acknowledged boundary hides old progress: $state", state.horizonProgress)
                assertFalse("every recorded state after the acknowledged boundary hides old failure: $state", state.progressFailed)
            }
            when {
                state.progressLoading && state.horizonProgress == null && !state.progressFailed ->
                    armedPending.getAndSet(null)?.also { recordedPending.set(state) }
                !state.isLoading && !state.unavailable && !state.progressLoading &&
                    !state.progressFailed && state.horizonProgress != null && !readyRecorded.isCompleted -> readyRecorded
                state.progressFailed && !failedRecorded.isCompleted -> failedRecorded
                else -> null
            }
        }) { model ->
            assertEquals(2, awaitReady(model).horizonProgress!!.recordsBroken)
            withTimeout(TestWaits.FLOW_MS) { readyRecorded.await() }
            val failedRead = reads.holdNext(fail = true)
            heldRequest.set(failedRead)
            val retryRecorded = CompletableDeferred<Unit>()
            armedPending.set(retryRecorded)
            model.retryHistory()
            failedRead.awaitEntered()
            val pending = awaitState(model) { it.progressLoading }
            assertNull(pending.horizonProgress)
            assertFalse(pending.progressFailed)
            withTimeout(TestWaits.FLOW_MS) { retryRecorded.await() }
            val retryBoundary = checkNotNull(recordedPending.get())
            val retry = trace(model).indexOfFirst { it === retryBoundary }
            assertTrue("the new retry pending boundary was acknowledged after trace append", retry >= 0)
            assertPendingEmissions(model, retry) { true }
            failedRead.release.complete(Unit)
            val failed = awaitState(model) { it.progressFailed }
            assertTrue(failed.stale)
            assertFalse(failed.unavailable)
            assertFalse(failed.progressLoading)
            assertNull(failed.horizonProgress)
            assertEquals(2, failed.horizonTotals!!.sessionCount)
            withTimeout(TestWaits.FLOW_MS) { failedRecorded.await() }

            val recovery = reads.holdNext()
            recordedPending.set(null)
            heldRequest.set(recovery)
            val recoveryRecorded = CompletableDeferred<Unit>()
            armedPending.set(recoveryRecorded)
            model.retryHistory()
            recovery.awaitEntered()
            val retrying = awaitState(model) { it.progressLoading }
            assertNull(retrying.horizonProgress)
            assertFalse(retrying.progressFailed)
            withTimeout(TestWaits.FLOW_MS) { recoveryRecorded.await() }
            val recoveryBoundary = checkNotNull(recordedPending.get())
            val recover = trace(model).indexOfFirst { it === recoveryBoundary }
            assertTrue("the new recovery pending boundary was acknowledged after trace append", recover >= 0)
            assertPendingEmissions(model, recover) { true }
            recovery.release.complete(Unit)
            val restored = awaitReady(model)
            assertFalse(restored.stale)
            assertEquals(2, restored.horizonProgress!!.recordsBroken)
            assertEquals(failed.selection, restored.selection)
            assertEquals(failed.periodRange, restored.periodRange)
        }
    }

    @Test
    fun aDelayedPriorFailureRecorderDeterministicallyExposesTheRecoveryTraceCutRace() = runBlocking {
        seedProgress()
        val recorderRelease = CompletableDeferred<Unit>()
        val failedAtRecorder = CompletableDeferred<HistoryUiState>()
        val failedRecorded = CompletableDeferred<Unit>()
        val pendingRecorded = CompletableDeferred<Unit>()
        val firstFailure = AtomicBoolean(false)
        val recoveryRequested = AtomicBoolean(false)
        val pendingAcknowledgement = AtomicBoolean(false)
        val recoveryRead = AtomicReference<PeriodHeldRead?>()
        val chronology = CopyOnWriteArrayList<String>()
        val chronologySequence = AtomicInteger()
        fun note(event: String) {
            chronology += "${chronologySequence.incrementAndGet()}: $event"
        }
        withModel(beforeRecord = { state ->
            // Once the recorder has appended the new pending boundary, every later
            // recorded state must stay pending until the real recovery read is released.
            if (pendingAcknowledgement.get() && recoveryRead.get()?.release?.isCompleted == false) {
                assertTrue("a recorded recovery state stays loading while its read is held: $state", state.progressLoading)
                assertNull("a recorded recovery state has no old progress while its read is held: $state", state.horizonProgress)
                assertFalse("a recorded recovery state has no old failure while its read is held: $state", state.progressFailed)
            }
            when {
                state.progressFailed && firstFailure.compareAndSet(false, true) -> {
                    note("recorder received the first old FAILED before append: $state")
                    failedAtRecorder.complete(state)
                    withTimeout(TestWaits.FLOW_MS) { recorderRelease.await() }
                    note("recorder released the old FAILED for its original trace append")
                    failedRecorded
                }
                recoveryRequested.get() && state.progressLoading && !state.progressFailed &&
                    state.horizonProgress == null && pendingAcknowledgement.compareAndSet(false, true) -> {
                    note("recorder received the new recovery pending boundary before append: $state")
                    pendingRecorded
                }
                else -> null
            }
        }) { model ->
            try {
                val ready = awaitReady(model)
                assertEquals(2, ready.horizonProgress!!.recordsBroken)
                note("direct observer saw original ready: $ready")
                val failedRead = reads.holdNext(fail = true)
                model.retryHistory()
                failedRead.awaitEntered()
                note("first retry entered its held real full-log read")
                val initialPending = awaitState(model) { it.progressLoading }
                assertNull(initialPending.horizonProgress)
                assertFalse(initialPending.progressFailed)
                failedRead.release.complete(Unit)
                val failed = awaitState(model) { it.progressFailed }
                note("direct observer saw old FAILED while recorder is delayed: $failed")
                val delayedFailure = withTimeout(TestWaits.FLOW_MS) { failedAtRecorder.await() }
                assertTrue(delayedFailure.progressFailed)
                assertFalse(delayedFailure.progressLoading)
                assertNull(delayedFailure.horizonProgress)
                assertFalse("the old FAILED has not yet been appended", failedRecorded.isCompleted)

                val recovery = reads.holdNext()
                recoveryRead.set(recovery)
                val recover = trace(model).size
                note("trace cut=$recover BEFORE requesting recovery; current public=$failed")
                recoveryRequested.set(true)
                model.retryHistory()
                recovery.awaitEntered()
                note("recovery entered its held real full-log read")
                val publicPending = awaitState(model) { it.progressLoading }
                assertNull(publicPending.horizonProgress)
                assertFalse(publicPending.progressFailed)
                assertFalse("the recovery query remains held", recovery.release.isCompleted)
                assertFalse("the old FAILED still has not been appended", failedRecorded.isCompleted)
                note("direct observer saw genuine new pending before recorder release: $publicPending")

                recorderRelease.complete(Unit)
                withTimeout(TestWaits.FLOW_MS) { failedRecorded.await() }
                note("acknowledged original delayed FAILED trace append")
                withTimeout(TestWaits.FLOW_MS) { pendingRecorded.await() }
                note("acknowledged later new-pending trace append")
                val captured = trace(model)
                val failedIndex = captured.indices.first { it >= recover && captured[it] == delayedFailure }
                val pendingIndex = captured.indices.first {
                    it > failedIndex && captured[it].progressLoading && !captured[it].progressFailed &&
                        captured[it].horizonProgress == null
                }
                assertEquals("the concrete delayed old FAILED is retained", delayedFailure, captured[failedIndex])
                assertTrue("the new recorded pending boundary follows that old FAILED", pendingIndex > failedIndex)
                val heldPending = captured.drop(pendingIndex)
                assertTrue("a genuine new pending boundary was recorded", heldPending.isNotEmpty())
                heldPending.forEach { state ->
                    assertTrue("all recorded states after new pending remain loading while the read is held: $state", state.progressLoading)
                    assertNull("all recorded states after new pending have no old progress: $state", state.horizonProgress)
                    assertFalse("all recorded states after new pending have no old failure: $state", state.progressFailed)
                }
                assertFalse("the genuine recovery read has not been released", recovery.release.isCompleted)
                assertFalse("the genuine recovery read has not returned", recovery.returned.get())
                note("proved held recovery tail pending; oldFAILEDIndex=$failedIndex; newPendingIndex=$pendingIndex")
                val artifact = File("build/screen-renders/history-retry-observer/${UUID.randomUUID()}")
                check(artifact.isDirectory || artifact.mkdirs())
                File(artifact, "delayed-recorder-chronology.txt").writeText(
                    "counter=aDelayedPriorFailureRecorderDeterministicallyExposesTheRecoveryTraceCutRace\n" +
                        "traceCut=$recover\noldFailedIndex=$failedIndex\nnewPendingIndex=$pendingIndex\n" +
                        "recoveryEntered=${recovery.entered.isCompleted}\nrecoveryReleased=${recovery.release.isCompleted}\n" +
                        "recoveryReturned=${recovery.returned.get()}\ndelayedFailure=$delayedFailure\n" +
                        "directNewPending=$publicPending\nchronology:\n${chronology.joinToString("\n")}\n" +
                        "fullRecordedTrace:\n${captured.mapIndexed { index, state -> "$index: $state" }.joinToString("\n")}\n",
                )

                // The acknowledged new pending boundary identifies the recovery window.
                // The delayed old FAILED remains literal in the earlier chronology; every
                // actual state from the new boundary is checked by the unchanged helper.
                assertPendingEmissions(model, pendingIndex) { true }
                recovery.release.complete(Unit)
                val restored = awaitReady(model)
                assertFalse(restored.stale)
                assertEquals(2, restored.horizonProgress!!.recordsBroken)
                assertEquals(failed.selection, restored.selection)
                assertEquals(failed.periodRange, restored.periodRange)
                assertEquals(failed.unit, restored.unit)
                note("direct observer saw real recovery ready after releasing its read: $restored")
                File(artifact, "recovered-recorder-chronology.txt").writeText(
                    "traceCut=$recover\noldFailedIndex=$failedIndex\nnewPendingIndex=$pendingIndex\n" +
                        "restored=$restored\nchronology:\n${chronology.joinToString("\n")}\n" +
                        "fullRecordedTrace:\n${trace(model).mapIndexed { index, state -> "$index: $state" }.joinToString("\n")}\n",
                )
            } finally {
                // Release the recorder before withModel clears/joins the VM, even on red.
                recorderRelease.complete(Unit)
                reads.releaseAll()
            }
        }
    }

    @Test
    fun correctionDeleteAndUndoRefreshTheFixedPeriodWithoutChangingOriginalIdentityOrTime() = runBlocking {
        val chosenDay = day(2024, 10, 12)
        insertWorkout("chosen", chosenDay, 100.0)
        insertWorkout("other", day(2024, 11, 12), 120.0)
        withModel { model ->
            awaitReady(model)
            model.selectMonth(CivilYearMonth(2024, 10))
            val selected = awaitState(model) { it.calendar.month == CivilYearMonth(2024, 10) && !it.progressLoading }
            val dao = deps.database.workoutDao()
            val original = checkNotNull(dao.getSet("chosen-set"))
            val session = checkNotNull(dao.getSessionRow("chosen"))
            val untouched = checkNotNull(dao.getSet("other-set"))
            deps.workoutRepository.updateSet(original.id, 105.0, 6, 9, false)
            val edited = awaitState(model) { it.horizonTotals?.volumeKg == 630.0 && !it.progressLoading }
            assertEquals(selected.selection, edited.selection)
            assertEquals(setOf("chosen"), edited.summaries.map { it.id }.toSet())
            assertEquals(original.copy(weightKg = 105.0, reps = 6, rpe = 9), dao.getSet(original.id))
            assertEquals(session, dao.getSessionRow(session.id))
            assertEquals(untouched, dao.getSet(untouched.id))
            val deleted = checkNotNull(deps.workoutRepository.deleteSet(original.id))
            val empty = awaitState(model) { it.horizonTotals?.workingSets == 0 && !it.progressLoading }
            assertEquals(selected.selection, empty.selection)
            assertEquals(setOf("chosen"), empty.summaries.map { it.id }.toSet())
            deps.workoutRepository.restoreSet(deleted)
            val restored = awaitState(model) { it.horizonTotals?.volumeKg == 630.0 && !it.progressLoading }
            assertEquals(selected.selection, restored.selection)
            assertEquals(original.copy(weightKg = 105.0, reps = 6, rpe = 9), dao.getSet(original.id))
            assertEquals(session, dao.getSessionRow(session.id))
            assertEquals(untouched, dao.getSet(untouched.id))
        }
    }

    @Test
    fun supportedRestoreOfSameIdentityActivityContentRefreshesTheRetainedPeriodAndBlockReviews() = runBlocking {
        val openingId = insertStrengthActivity("restore-opening", day(2024, 11, 1), 100.0)
        val closingId = insertStrengthActivity("restore-closing", day(2024, 11, 21), 110.0)
        val archived = TrainingBlock(startEpochDay = TODAY - 28, weeks = 4)
        deps.preferencesRepository.setTrainingBlock(archived)
        deps.preferencesRepository.beginBlock(TrainingBlock(TODAY), todayEpochDay = TODAY)
        withModel { model ->
            model.selectMonth(CivilYearMonth(2024, 11))
            val before = awaitState(model) {
                !it.isLoading && !it.progressLoading && it.horizonProgress != null && it.pastBlocks.isNotEmpty()
            }
            assertEquals(2, before.horizonProgress!!.recordsBroken)
            assertNotNull(before.horizonProgress!!.movedMost)
            assertEquals(2, before.pastBlocks.single().review.recordsBroken)
            assertTrue(before.pastBlocks.single().review.movers.isNotEmpty())
            assertEquals(110.0, before.records.single().valueKg, 0.0)
            val originalActivities = deps.activityRepository.all()
            assertEquals(setOf(openingId, closingId), originalActivities.map { it.id }.toSet())
            val original = BackupJson.decode(deps.backupService.exportJson())
            val replacement = original.copy(activities = original.activities.map { activity ->
                activity.copy(blocks = activity.blocks.map { block ->
                    block.copy(sets = block.sets.map { set -> set.copy(weightKg = 105.0) })
                })
            })
            val expectedActivities = originalActivities.map { activity ->
                activity.copy(blocks = activity.blocks.map { block ->
                    if (block !is StrengthBlock) block else block.copy(sets = block.sets.map { set ->
                        set.copy(weightKg = 105.0)
                    })
                })
            }
            val firstRestoreEmission = trace(model).size
            val result = deps.backupService.restoreFromJson(
                json = BackupJson.encode(replacement), sourceName = "synthetic-same-identity-activity.json",
            )
            assertTrue(result.preferencesRestored)
            assertFalse(result.settingsPending)
            assertFalse(deps.backupService.restoreInProgress())
            assertNotNull(result.safetySnapshotId)
            // Exact durable domain inventory proves that only the authored weight changed:
            // IDs, revisions, counts, captured dates, set numbers and timestamps all match.
            assertEquals(expectedActivities, deps.activityRepository.all())
            assertEquals(original.sessions, BackupJson.decode(deps.backupService.exportJson()).sessions)
            assertEquals(original.setLogs, BackupJson.decode(deps.backupService.exportJson()).setLogs)

            val listUpdated = awaitState(model) {
                it.summaries.associate { row -> row.id to row.volumeKg } ==
                    mapOf(openingId to 525.0, closingId to 525.0) && it.records.singleOrNull()?.valueKg == 105.0
            }
            assertEquals("restore preserves aggregate volume while replacing individual sets",
                before.horizonTotals!!.volumeKg, listUpdated.horizonTotals!!.volumeKg, 0.0)
            assertEquals(before.horizonTotals!!.workingSets, listUpdated.horizonTotals!!.workingSets)
            assertEquals(before.horizonTotals!!.sessionCount, listUpdated.horizonTotals!!.sessionCount)
            assertEquals(before.selection, listUpdated.selection)
            assertEquals(before.periodRange, listUpdated.periodRange)
            assertEquals(before.unit, listUpdated.unit)
            assertEquals(setOf(openingId, closingId), listUpdated.summaries.map { it.id }.toSet())
            val range = checkNotNull(before.periodRange)
            val correctedItems = deps.completedTrainingRepository.all()
            val expectedProgress = BlockReviewBuilder.overRange(
                startEpochDay = range.startEpochDay, endExclusiveEpochDay = range.endExclusiveEpochDay,
                items = correctedItems, unit = before.unit,
            )
            val expectedReview = BlockReviewBuilder.build(block = archived, items = correctedItems, unit = before.unit)
            assertEquals(0, expectedProgress.recordsBroken)
            assertTrue(expectedProgress.movers.isEmpty())
            assertEquals(0, expectedReview.recordsBroken)
            assertTrue(expectedReview.movers.isEmpty())
            try {
                val refreshed = awaitState(model) {
                    !it.progressLoading && it.horizonProgress == expectedProgress &&
                        it.pastBlocks.singleOrNull()?.review == expectedReview
                }
                assertEquals(before.selection, refreshed.selection)
                assertEquals(before.periodRange, refreshed.periodRange)
                assertEquals(before.unit, refreshed.unit)
            } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError(
                    "Supported restore committed exact replacement rows and updated list/lifetime values, " +
                        "but retained progress/review did not converge; expectedProgress=$expectedProgress, " +
                        "expectedReview=$expectedReview, current=${model.uiState.value}, " +
                        "restoreEmissions=${trace(model).drop(firstRestoreEmission)}",
                    failure,
                )
            }
        }
    }

    @Test
    fun aChangedUnitCannotAttachOldHealthyBlockReviewsWhileAllRealFullLogReadsWait() = runBlocking {
        seedProgress()
        archiveCurrentMonthBlock()
        deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
        withModel { model ->
            val before = awaitState(model) {
                !it.isLoading && !it.progressLoading && it.pastBlocks.singleOrNull()?.review?.movers?.isNotEmpty() == true
            }
            assertTrue(before.pastBlocks.single().review.movers.single().toLabel.endsWith("kg"))
            val start = trace(model).size
            val held = reads.holdAll()
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            held.awaitEntered()
            awaitState(model) { it.unit == WeightUnit.LBS }
            val changed = trace(model).drop(start).filter { it.unit == WeightUnit.LBS }
            assertTrue("no changed-unit public state was captured", changed.isNotEmpty())
            changed.forEach { state ->
                assertTrue("old block review presented as ready in the new unit: $state", state.blocksLoading)
                assertTrue("old kilograms remain attached to the new-unit state: $state", state.pastBlocks.isEmpty())
            }
            held.release.complete(Unit)
            val ready = awaitState(model) {
                it.unit == WeightUnit.LBS && !it.blocksLoading && !it.blocksUnavailable && !it.blocksStale &&
                    it.pastBlocks.singleOrNull()?.review?.movers?.singleOrNull()?.toLabel?.endsWith("lb") == true
            }
            assertEquals(before.selection, ready.selection)
            assertEquals(before.periodRange, ready.periodRange)
            assertEquals(before.pastBlocks.single().block, ready.pastBlocks.single().block)
        }
    }

    @Test
    fun failedBlockRefreshCanRetainSameUnitButMustHideReviewsFormattedForADifferentUnit() = runBlocking {
        seedProgress()
        archiveCurrentMonthBlock()
        deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
        withModel { model ->
            val before = awaitState(model) {
                !it.isLoading && it.pastBlocks.singleOrNull()?.review?.movers?.isNotEmpty() == true && !it.blocksStale
            }
            reads.failAll.set(true)
            deps.preferencesRepository.recordBodyweight(80.0, TODAY)
            val sameUnit = awaitState(model) { it.blocksStale && !it.blocksUnavailable && !it.blocksLoading }
            assertEquals(before.pastBlocks, sameUnit.pastBlocks)
            val refusals = reads.failedCalls.get()
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            val changedUnit = awaitState(model) {
                it.unit == WeightUnit.LBS && !it.blocksLoading && it.blocksStale && reads.failedCalls.get() > refusals
            }
            assertTrue("failed new-unit computation cannot label a kilogram review current", changedUnit.blocksUnavailable)
            assertTrue("failed new-unit computation must hide the old formatted values", changedUnit.pastBlocks.isEmpty())
        }
    }

    @Test
    fun supportedAggregatePreservingWorkoutRestoreRefreshesEveryRetainedHistoryReadout() = runBlocking {
        insertWorkout("restore-workout-opening", day(2024, 11, 1), 100.0)
        insertWorkout("restore-workout-closing", day(2024, 11, 21), 110.0)
        val archived = archiveCurrentMonthBlock()
        withModel { model ->
            model.selectMonth(CivilYearMonth(2024, 11))
            val before = awaitState(model) {
                !it.isLoading && !it.progressLoading && it.horizonProgress != null && it.pastBlocks.isNotEmpty()
            }
            assertEquals(2, before.horizonProgress!!.recordsBroken)
            assertTrue(before.pastBlocks.single().review.movers.isNotEmpty())
            assertEquals(110.0, before.records.single().valueKg, 0.0)
            val dao = deps.database.workoutDao()
            val originalSessions = dao.getAllSessions()
            val originalSets = dao.getAllSets()
            val original = BackupJson.decode(deps.backupService.exportJson())
            val replacement = original.copy(setLogs = original.setLogs.map { it.copy(weightKg = 105.0) })
            val firstRestoreEmission = trace(model).size
            val result = deps.backupService.restoreFromJson(
                json = BackupJson.encode(replacement), sourceName = "synthetic-same-aggregate-workouts.json",
            )
            assertTrue(result.preferencesRestored)
            assertFalse(result.settingsPending)
            assertFalse(deps.backupService.restoreInProgress())
            assertNotNull(result.safetySnapshotId)
            // Every session and set field other than the two weights is preserved exactly.
            assertEquals(originalSessions, dao.getAllSessions())
            assertEquals(originalSets.map { it.copy(weightKg = 105.0) }, dao.getAllSets())
            assertEquals(originalSets.sumOf { it.weightKg * it.reps }, dao.getAllSets().sumOf { it.weightKg * it.reps }, 0.0)
            assertEquals(original.activities, BackupJson.decode(deps.backupService.exportJson()).activities)
            val range = checkNotNull(before.periodRange)
            val correctedItems = deps.completedTrainingRepository.all()
            val expectedProgress = BlockReviewBuilder.overRange(
                startEpochDay = range.startEpochDay, endExclusiveEpochDay = range.endExclusiveEpochDay,
                items = correctedItems, unit = before.unit,
            )
            val expectedReview = BlockReviewBuilder.build(block = archived, items = correctedItems, unit = before.unit)
            assertEquals(0, expectedProgress.recordsBroken)
            assertTrue(expectedProgress.movers.isEmpty())
            assertEquals(0, expectedReview.recordsBroken)
            try {
                val refreshed = awaitState(model) {
                    it.summaries.associate { row -> row.id to row.volumeKg } == mapOf(
                        "restore-workout-opening" to 525.0, "restore-workout-closing" to 525.0,
                    ) && it.records.singleOrNull()?.valueKg == 105.0 && !it.progressLoading &&
                        it.horizonProgress == expectedProgress && it.pastBlocks.singleOrNull()?.review == expectedReview
                }
                assertEquals(before.selection, refreshed.selection)
                assertEquals(before.periodRange, refreshed.periodRange)
                assertEquals(before.unit, refreshed.unit)
                assertEquals(before.horizonTotals, refreshed.horizonTotals)
            } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError(
                    "Supported workout restore preserved every ID/date/count/sum but replaced exact weights; " +
                        "expected individual summaries=525kg each, lifetime=105kg, " +
                        "expectedProgress=$expectedProgress, expectedReview=$expectedReview, " +
                        "current=${model.uiState.value}, restoreEmissions=${trace(model).drop(firstRestoreEmission)}",
                    failure,
                )
            }
        }
    }

    private fun model(saved: SavedStateHandle = SavedStateHandle()): HistoryViewModel =
        HistoryViewModel(ApplicationProvider.getApplicationContext(), saved, deps).also(models::add)

    private suspend fun withModel(
        saved: SavedStateHandle = SavedStateHandle(),
        beforeRecord: suspend (HistoryUiState) -> CompletableDeferred<Unit>? = { null },
        block: suspend (HistoryViewModel) -> Unit,
    ) = coroutineScope {
        val model = model(saved)
        val trace = CopyOnWriteArrayList<HistoryUiState>()
        emissions[model] = trace
        val subscriber = launch(dispatcher) {
            model.uiState.collect { state ->
                val recorded = beforeRecord(state)
                trace += state
                recorded?.complete(Unit)
            }
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

    /** A retry may hide a prior keyed result, but cannot declare those known blocks absent. */
    private fun assertEmptyBlockEmissionsAreLoading(model: HistoryViewModel, start: Int) {
        val changed = trace(model).drop(start)
        assertTrue("no public retry transition was captured", changed.isNotEmpty())
        changed.filter { it.pastBlocks.isEmpty() }.forEach { state ->
            assertTrue("known block reviews became a healthy empty result during retry: $state", state.blocksLoading)
        }
    }

    /** All captured public states for a changed identity, including those before DAO entry. */
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

    private suspend fun seedPastBlock() {
        insertWorkout("inside-past-block", TODAY - 60, 100.0)
        deps.preferencesRepository.setTrainingBlock(TrainingBlock(startEpochDay = TODAY - 70, weeks = 2))
        deps.preferencesRepository.beginBlock(TrainingBlock(TODAY), todayEpochDay = TODAY)
    }

    private suspend fun archiveCurrentMonthBlock(): TrainingBlock {
        val archived = TrainingBlock(startEpochDay = TODAY - 28, weeks = 4)
        deps.preferencesRepository.setTrainingBlock(archived)
        deps.preferencesRepository.beginBlock(TrainingBlock(TODAY), todayEpochDay = TODAY)
        return archived
    }

    private suspend fun insertWorkout(id: String, localDay: Long, kg: Double) {
        val at = stamp(localDay)
        deps.database.workoutDao().upsertSession(WorkoutSessionEntity(
            id = id, routineId = null, routineName = "Synthetic $id", date = at,
            notes = "", durationMinutes = 10, startedAt = at, finishedAt = at + 600_000L,
        ))
        deps.database.workoutDao().insertSet(SetLogEntity(
            id = "$id-set", sessionId = id, exerciseId = EXERCISE, setNumber = 1,
            weightKg = kg, reps = 5, rpe = 8, isWarmup = false, completedAt = at + 6_000L,
        ))
    }

    private suspend fun insertCardio(localDay: Long): String {
        val start = deps.time.capture(stamp(localDay), "UTC")
        val end = deps.time.capture(stamp(localDay) + 600_000L, "UTC")
        val outcome = deps.confirmActivity(ActivityDraft(
            status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
            title = "Synthetic walk", performedStart = start, performedEnd = end,
            blocks = listOf(CardioBlock(
                id = "walk-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
            )),
        ), deps.time.captureNow())
        assertTrue(outcome is ActivityWrite.Accepted)
        return (outcome as ActivityWrite.Accepted).session.id
    }

    private suspend fun insertStrengthActivity(id: String, localDay: Long, kg: Double): String {
        val start = deps.time.capture(stamp(localDay), "UTC")
        val outcome = deps.confirmActivity(ActivityDraft(
            id = id, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
            title = "Synthetic $id", performedStart = start,
            performedEnd = deps.time.capture(start.instantMillis + 600_000L, "UTC"),
            blocks = listOf(StrengthBlock(
                id = "$id-block", sortOrder = 0, exerciseId = EXERCISE, exerciseName = "Bench",
                loadType = LoadType.EXTERNAL, equipment = EquipmentType.BARBELL, muscles = emptyList(),
                sets = listOf(StrengthSet(
                    id = "$id-set", setNumber = 1, weightKg = kg, reps = 5, rpe = 8,
                    isWarmup = false, completedAtMs = start.instantMillis + 6_000L,
                )),
            )),
        ), deps.time.captureNow())
        assertTrue(outcome.toString(), outcome is ActivityWrite.Accepted)
        return (outcome as ActivityWrite.Accepted).session.id
    }

    private fun saved(horizon: Any, anchor: Any, follow: Any): Map<String, Any> =
        mapOf(SAVED_HORIZON to horizon, SAVED_ANCHOR to anchor, SAVED_FOLLOW to follow)

    private companion object {
        const val EXERCISE = "history-period-bench"
        const val SAVED_HORIZON = "history.period.horizon"
        const val SAVED_ANCHOR = "history.period.anchor"
        const val SAVED_FOLLOW = "history.period.follow-today"
        val TODAY = day(2024, 11, 25)
        fun day(year: Int, month: Int, day: Int) = CivilDate(year, month, day).epochDay
        fun stamp(epochDay: Long) = epochDay * 86_400_000L + 43_200_000L
    }
}

/** The DAO still reads Room; only returning that exact snapshot may wait or fail. */
private class PeriodHeldActivityDao(
    private val delegate: ActivityDao,
    private val reads: PeriodFullLogReads,
    private val secondary: PeriodSecondaryReads,
) : ActivityDao by delegate {
    override fun observeCompletedStrengthSetRecords(): Flow<List<RecordSetRow>> =
        delegate.observeCompletedStrengthSetRecords().map { rows ->
            secondary.records.check("period lifetime activity records")
            rows
        }

    override suspend fun getAllGraphs(): List<ActivitySessionGraph> {
        reads.calls.incrementAndGet()
        val result = delegate.getAllGraphs()
        if (reads.failAll.get()) {
            reads.failedCalls.incrementAndGet()
            error("synthetic failed full-log read after actual Room snapshot")
        }
        val held = reads.next.getAndSet(null) ?: reads.all.get() ?: return result
        held.entered.complete(Unit)
        if (held.late) withContext(NonCancellable) { held.release.await() }
        else held.release.await()
        held.returned.set(true)
        if (held.fail) error("synthetic full-log read failure after the real Room snapshot")
        return result
    }
}

private class PeriodFullLogReads {
    val calls = AtomicInteger()
    val next = AtomicReference<PeriodHeldRead?>()
    val failAll = AtomicBoolean(false)
    val failedCalls = AtomicInteger()
    val all = AtomicReference<PeriodHeldRead?>()
    private val held = mutableListOf<PeriodHeldRead>()

    fun holdNext(late: Boolean = false, fail: Boolean = false): PeriodHeldRead {
        val read = PeriodHeldRead(late, fail)
        check(next.compareAndSet(null, read)) { "another full-log read is already armed" }
        held += read
        return read
    }

    fun holdAll(): PeriodHeldRead {
        val read = PeriodHeldRead(late = false, fail = false)
        check(all.compareAndSet(null, read)) { "all full-log reads are already held" }
        held += read
        return read
    }

    fun releaseAll() {
        all.set(null)
        held.forEach { it.release.complete(Unit) }
    }
}

private class PeriodSecondaryReads {
    val records = ReadGate(false)
    val summaries = ReadGate(false)
    val blocks = ReadGate(false)
}

private class PeriodSummaryFaultDao(
    private val delegate: WorkoutDao,
    private val secondary: PeriodSecondaryReads,
) : WorkoutDao by delegate {
    override suspend fun sessionSummaries(): List<SessionSummaryRow> {
        val rows = delegate.sessionSummaries()
        secondary.summaries.check("period primary workout summaries")
        return rows
    }
}

private class PeriodHeldRead(val late: Boolean, val fail: Boolean) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val returned = AtomicBoolean(false)
    suspend fun awaitEntered() = withTimeout(TestWaits.FLOW_MS) { entered.await() }
}
