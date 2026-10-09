package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.BlockReviewBuilder
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.util.JvmTime
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real confirmed cardio keeps its captured day when the current device day moves backwards. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryCapturedDateTravelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val time = CapturedDateTravelTime(
        nowMs = Instant.parse("2026-10-08T20:20:00Z").toEpochMilli(),
        zone = "Asia/Tokyo",
    )
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
            time = time,
        )
    }

    @After
    fun tearDown() {
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun allHistoryKeepsAConfirmedTokyoActivityAfterTheCurrentDayMovesBackInHonolulu() = runBlocking {
        val capturedDay = CivilDate.of(2026, 10, 9).epochDay
        assertEquals(capturedDay, time.captureNow().localEpochDay)
        val accepted = deps.activityRepository.confirm(
            draft = ActivityDraft(
                id = ACTIVITY_ID,
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Synthetic Tokyo walk",
                performedStart = time.capture(time.nowMillis() - 600_000L),
                performedEnd = time.captureNow(),
                blocks = listOf(CardioBlock(
                    id = "tokyo-walk-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                    elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                    elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                )),
            ),
            now = time.captureNow(),
            ids = IdPort { ACTIVITY_ID },
            clock = time,
        )
        assertTrue(accepted.toString(), accepted is ActivityWrite.Accepted)
        assertEquals(ACTIVITY_ID, (accepted as ActivityWrite.Accepted).session.id)
        assertEquals(capturedDay, accepted.session.localEpochDay)
        val durableBefore = deps.database.activityDao().getAllGraphs()
        assertEquals(1, durableBefore.size)
        val savedSummary = deps.database.activityDao().observeCompletedSummaries().first().single()
        assertEquals(ACTIVITY_ID, savedSummary.id)
        assertEquals(capturedDay, savedSummary.localEpochDay)

        coroutineScope {
            val model = HistoryViewModel(
                application = ApplicationProvider.getApplicationContext(),
                savedStateHandle = SavedStateHandle(),
                container = deps,
            )
            val subscriber = launch(dispatcher) { model.uiState.collect {} }
            try {
                model.setHorizon(AnalyticsHorizon.ALL_TIME)
                val before = awaitAllReady(model, capturedDay)
                assertEquals(setOf(HistoryKind.ACTIVITY to ACTIVITY_ID), before.summaries.map { it.kind to it.id }.toSet())
                assertEquals(capturedDay, before.summaries.single().localEpochDay)

                // Nine hours later the instant has advanced, but Honolulu is still 8 October.
                time.nowMs += 9L * 60L * 60L * 1000L
                time.zone = "Pacific/Honolulu"
                assertEquals(Instant.parse("2026-10-09T05:20:00Z").toEpochMilli(), time.nowMillis())
                val currentDay = time.captureNow().localEpochDay
                assertEquals(CivilDate.of(2026, 10, 8).epochDay, currentDay)
                model.updateToday(currentDay)
                val after = awaitAllReady(model, currentDay)

                // Reading after travel must not mutate or relabel any durable field.
                assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
                assertEquals(savedSummary, deps.database.activityDao().observeCompletedSummaries().first().single())
                assertEquals(
                    "A completed activity captured on 9 October must remain reachable in All History " +
                        "when the device day is 8 October; current=$after",
                    setOf(HistoryKind.ACTIVITY to ACTIVITY_ID),
                    after.summaries.map { it.kind to it.id }.toSet(),
                )
                assertEquals(capturedDay, after.summaries.single().localEpochDay)
                assertEquals(setOf(capturedDay), after.completedEpochDays)
                assertCoherentCardio(state = after, containsActivity = true)
            } finally {
                model.clearAndJoinForTest()
                subscriber.cancelAndJoin()
            }
        }
    }

    @Test
    fun nextCanReachTheKnownCapturedDayAndReturnToTodayWithoutOpeningAnEmptyFutureDay() = runBlocking {
        confirmCardio()
        val capturedDay = CivilDate.of(2026, 10, 9).epochDay
        val currentDay = travelToHonolulu()
        val durableBefore = deps.database.activityDao().getAllGraphs()
        val handle = SavedStateHandle()
        withModel(handle) { model ->
            model.setHorizon(AnalyticsHorizon.DAY)
            val current = awaitReady(model) {
                it.horizon == AnalyticsHorizon.DAY && it.selection.followToday
            }
            assertEquals(HistoryPeriodRange(currentDay, currentDay + 1), current.periodRange)
            assertTrue(current.canGoNext)
            assertCoherentCardio(state = current, containsActivity = false)

            model.showNextPeriod()
            val next = awaitReady(model) {
                it.horizon == AnalyticsHorizon.DAY && !it.selection.followToday &&
                    it.selection.anchorEpochDay == capturedDay
            }
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), next.periodRange)
            assertFalse(next.canGoNext)
            assertCoherentCardio(state = next, containsActivity = true)
            val selected = savedValues(handle)
            model.showNextPeriod()
            assertEquals("Next cannot select an unknown future day", selected, savedValues(handle))
            model.selectDay(capturedDay + 1)
            assertEquals("Direct selection cannot author an empty future day", selected, savedValues(handle))

            model.showCurrentPeriod()
            val returned = awaitReady(model) {
                it.horizon == AnalyticsHorizon.DAY && it.selection.followToday &&
                    it.selection.anchorEpochDay == currentDay
            }
            assertEquals(currentDay, returned.today.epochDay)
            assertEquals(current.periodRange, returned.periodRange)
            assertCoherentCardio(state = returned, containsActivity = false)

            model.selectDay(capturedDay)
            val direct = awaitReady(model) {
                it.horizon == AnalyticsHorizon.DAY && !it.selection.followToday &&
                    it.selection.anchorEpochDay == capturedDay
            }
            assertEquals(next.periodRange, direct.periodRange)
            assertEquals(next.summaries, direct.summaries)
            assertCoherentCardio(state = direct, containsActivity = true)
        }
        assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
    }

    @Test
    fun currentWeekMonthAndYearExtendOnlyThroughTheirKnownCompletedCapturedDay() = runBlocking {
        confirmCardio()
        val capturedDay = CivilDate.of(2026, 10, 9).epochDay
        val currentDay = travelToHonolulu()
        val durableBefore = deps.database.activityDao().getAllGraphs()
        withModel { model ->
            val ranges = listOf(
                AnalyticsHorizon.WEEK to HistoryPeriodRange(CivilDate.of(2026, 10, 5).epochDay, capturedDay + 1),
                AnalyticsHorizon.MONTH to HistoryPeriodRange(CivilDate.of(2026, 10, 1).epochDay, capturedDay + 1),
                AnalyticsHorizon.YEAR to HistoryPeriodRange(CivilDate.of(2026, 1, 1).epochDay, capturedDay + 1),
            )
            ranges.forEach { (horizon, expectedRange) ->
                model.setHorizon(horizon)
                val state = awaitReady(model) { it.horizon == horizon }
                assertTrue(state.selection.followToday)
                assertEquals(currentDay, state.today.epochDay)
                assertEquals(expectedRange, state.periodRange)
                assertFalse(state.canGoNext)
                assertCoherentCardio(state = state, containsActivity = true)
            }
        }
        assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
    }

    @Test
    fun januaryCapturedDataIsReachableFromDecemberButDoesNotEnterTheCurrentYear() = runBlocking {
        time.nowMs = Instant.parse("2026-12-31T20:20:00Z").toEpochMilli()
        confirmCardio()
        val capturedDay = CivilDate.of(2027, 1, 1).epochDay
        assertEquals(capturedDay, time.captureNow().localEpochDay)
        val currentDay = travelToHonolulu()
        assertEquals(CivilDate.of(2026, 12, 31).epochDay, currentDay)
        val durableBefore = deps.database.activityDao().getAllGraphs()
        val handle = SavedStateHandle()
        withModel(handle) { model ->
            model.setHorizon(AnalyticsHorizon.ALL_TIME)
            val all = awaitReady(model) { it.horizon == AnalyticsHorizon.ALL_TIME }
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), all.periodRange)
            assertCoherentCardio(state = all, containsActivity = true)

            model.setHorizon(AnalyticsHorizon.MONTH)
            val december = awaitReady(model) { it.horizon == AnalyticsHorizon.MONTH && it.selection.followToday }
            assertEquals(
                HistoryPeriodRange(CivilDate.of(2026, 12, 1).epochDay, currentDay + 1),
                december.periodRange,
            )
            assertTrue(december.canGoNext)
            assertCoherentCardio(state = december, containsActivity = false)
            model.showNextPeriod()
            val nextJanuary = awaitReady(model) {
                it.horizon == AnalyticsHorizon.MONTH && !it.selection.followToday &&
                    it.calendar.month == CivilYearMonth(2027, 1)
            }
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), nextJanuary.periodRange)
            assertFalse(nextJanuary.canGoNext)
            assertCoherentCardio(state = nextJanuary, containsActivity = true)
            val januaryValues = savedValues(handle)
            model.selectMonth(CivilYearMonth(2027, 2))
            assertEquals("Unknown future month remains disabled", januaryValues, savedValues(handle))

            model.showCurrentPeriod()
            val decemberAgain = awaitReady(model) { it.horizon == AnalyticsHorizon.MONTH && it.selection.followToday }
            assertEquals(december.periodRange, decemberAgain.periodRange)
            model.selectMonth(CivilYearMonth(2027, 1))
            val directJanuary = awaitReady(model) {
                it.horizon == AnalyticsHorizon.MONTH && !it.selection.followToday &&
                    it.calendar.month == CivilYearMonth(2027, 1)
            }
            assertEquals(nextJanuary.periodRange, directJanuary.periodRange)
            assertCoherentCardio(state = directJanuary, containsActivity = true)

            model.showCurrentPeriod()
            model.setHorizon(AnalyticsHorizon.YEAR)
            val currentYear = awaitReady(model) { it.horizon == AnalyticsHorizon.YEAR && it.selection.followToday }
            assertEquals(
                HistoryPeriodRange(CivilDate.of(2026, 1, 1).epochDay, currentDay + 1),
                currentYear.periodRange,
            )
            assertTrue(currentYear.canGoNext)
            assertCoherentCardio(state = currentYear, containsActivity = false)
            model.showNextPeriod()
            val nextYear = awaitReady(model) {
                it.horizon == AnalyticsHorizon.YEAR && !it.selection.followToday &&
                    it.periodRange?.startEpochDay == capturedDay
            }
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), nextYear.periodRange)
            assertFalse(nextYear.canGoNext)
            assertCoherentCardio(state = nextYear, containsActivity = true)
            val yearValues = savedValues(handle)
            model.showNextPeriod()
            assertEquals("Unknown future year remains disabled", yearValues, savedValues(handle))
            model.showCurrentPeriod()
            val returnedYear = awaitReady(model) { it.horizon == AnalyticsHorizon.YEAR && it.selection.followToday }
            assertEquals(currentYear.periodRange, returnedYear.periodRange)
            assertCoherentCardio(state = returnedYear, containsActivity = false)
        }
        assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
    }

    @Test
    fun restoredKnownCapturedDayAndMonthKeepTheirPrimitiveAnchorAfterTravel() = runBlocking {
        time.nowMs = Instant.parse("2026-12-31T20:20:00Z").toEpochMilli()
        confirmCardio()
        val capturedDay = CivilDate.of(2027, 1, 1).epochDay
        val durableBefore = deps.database.activityDao().getAllGraphs()
        val dayHandle = SavedStateHandle()
        var storedDay: Map<String, Any?> = emptyMap()
        withModel(dayHandle) { model ->
            awaitReady(model)
            model.selectDay(capturedDay)
            val selected = awaitReady(model) { it.horizon == AnalyticsHorizon.DAY && !it.selection.followToday }
            assertEquals(HistoryPeriodSelection(AnalyticsHorizon.DAY, capturedDay, false), selected.selection)
            storedDay = savedValues(dayHandle)
            assertEquals(3, storedDay.size)
            assertTrue(storedDay.values.all { it is String || it is Long || it is Boolean })
        }
        val currentDay = travelToHonolulu()
        val restoredDayHandle = SavedStateHandle(storedDay)
        var storedMonth: Map<String, Any?> = emptyMap()
        withModel(restoredDayHandle) { model ->
            val restored = awaitReady(model) { it.horizon == AnalyticsHorizon.DAY }
            assertEquals(currentDay, restored.today.epochDay)
            assertEquals(HistoryPeriodSelection(AnalyticsHorizon.DAY, capturedDay, false), restored.selection)
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), restored.periodRange)
            assertEquals(storedDay, savedValues(restoredDayHandle))
            assertCoherentCardio(state = restored, containsActivity = true)
            model.selectMonth(CivilYearMonth(2027, 1))
            val month = awaitReady(model) { it.horizon == AnalyticsHorizon.MONTH }
            assertEquals(HistoryPeriodSelection(AnalyticsHorizon.MONTH, capturedDay, false), month.selection)
            storedMonth = savedValues(restoredDayHandle)
        }
        // This is a new owner/ViewModel with primitive saved state, not OS process-death proof.
        val restoredMonthHandle = SavedStateHandle(storedMonth)
        withModel(restoredMonthHandle) { model ->
            val restored = awaitReady(model) { it.horizon == AnalyticsHorizon.MONTH }
            assertEquals(currentDay, restored.today.epochDay)
            assertEquals(HistoryPeriodSelection(AnalyticsHorizon.MONTH, capturedDay, false), restored.selection)
            assertEquals(CivilYearMonth(2027, 1), restored.calendar.month)
            assertEquals(HistoryPeriodRange(capturedDay, capturedDay + 1), restored.periodRange)
            assertEquals(storedMonth, savedValues(restoredMonthHandle))
            assertCoherentCardio(state = restored, containsActivity = true)
        }
        assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
    }

    @Test
    fun deletingTheLastCapturedDayActivityFallsBackUntilSupportedBackupRecoveryRestoresThatDay() = runBlocking {
        confirmCardio()
        val capturedDay = CivilDate.of(2026, 10, 9).epochDay
        val currentDay = travelToHonolulu()
        val durableBefore = deps.database.activityDao().getAllGraphs()
        val workoutSessionsBefore = deps.database.workoutDao().getAllSessions()
        val workoutSetsBefore = deps.database.workoutDao().getAllSets()
        val backup = deps.backupService.exportJson()
        val handle = SavedStateHandle()
        withModel(handle) { model ->
            awaitReady(model)
            model.selectDay(capturedDay)
            val selected = awaitReady(model) {
                it.horizon == AnalyticsHorizon.DAY && !it.selection.followToday &&
                    it.selection.anchorEpochDay == capturedDay
            }
            assertCoherentCardio(state = selected, containsActivity = true)
            val storedSelection = savedValues(handle)
            val deletion = deps.activityRepository.deleteCompleted(ACTIVITY_ID)
            assertTrue(deletion.toString(), deletion is ActivityWrite.Accepted)
            assertEquals(ACTIVITY_ID, (deletion as ActivityWrite.Accepted).session.id)
            assertEquals(capturedDay, deletion.session.localEpochDay)
            assertTrue(deps.database.activityDao().getAllGraphs().isEmpty())

            val empty = awaitReady(model) { it.completedEpochDays.isEmpty() && it.summaries.isEmpty() }
            assertEquals(selected.selection, empty.selection)
            assertEquals(storedSelection, savedValues(handle))
            assertEquals(HistoryPeriodRange(currentDay, currentDay + 1), empty.periodRange)
            assertEquals(currentDay, empty.today.epochDay)
            assertFalse(empty.canGoNext)
            assertTrue(empty.monthGroups.isEmpty())
            assertTrue(empty.calendar.weeks.flatten().all { it.activityIds.isEmpty() && it.sessionIds.isEmpty() })
            val emptyTotals = checkNotNull(empty.horizonTotals)
            assertEquals(currentDay, emptyTotals.startEpochDay)
            assertEquals(currentDay, emptyTotals.endEpochDay)
            assertEquals(0, emptyTotals.sessionCount)
            assertEquals(0, emptyTotals.trainedDays)
            assertEquals(0, emptyTotals.activeMinutes)
            assertEquals(0L, emptyTotals.cardioSeconds)
            assertEquals(0.0, emptyTotals.cardioDistanceMeters, 0.0)
            assertEquals(
                BlockReviewBuilder.overRange(
                    startEpochDay = currentDay, endExclusiveEpochDay = currentDay + 1,
                    items = deps.completedTrainingRepository.all(), unit = empty.unit,
                ),
                empty.horizonProgress,
            )

            // Completed activities have no Undo API. Use the existing validated backup route.
            val recovery = deps.backupService.restoreFromJson(
                json = backup, sourceName = "synthetic-captured-day-recovery.json",
            )
            assertTrue(recovery.preferencesRestored)
            assertFalse(recovery.settingsPending)
            assertFalse(deps.backupService.restoreInProgress())
            assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
            assertEquals(workoutSessionsBefore, deps.database.workoutDao().getAllSessions())
            assertEquals(workoutSetsBefore, deps.database.workoutDao().getAllSets())
            val restored = awaitReady(model) {
                it.completedEpochDays == setOf(capturedDay) &&
                    it.summaries.singleOrNull()?.id == ACTIVITY_ID &&
                    it.periodRange == HistoryPeriodRange(capturedDay, capturedDay + 1)
            }
            assertEquals(selected.selection, restored.selection)
            assertEquals(storedSelection, savedValues(handle))
            assertEquals(selected.periodRange, restored.periodRange)
            assertEquals(selected.summaries, restored.summaries)
            assertEquals(currentDay, restored.today.epochDay)
            assertCoherentCardio(state = restored, containsActivity = true)
        }
        assertEquals(durableBefore, deps.database.activityDao().getAllGraphs())
    }

    private suspend fun confirmCardio() {
        val accepted = deps.activityRepository.confirm(
            draft = ActivityDraft(
                id = ACTIVITY_ID, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                title = "Synthetic captured-date walk",
                performedStart = time.capture(time.nowMillis() - 600_000L), performedEnd = time.captureNow(),
                blocks = listOf(CardioBlock(
                    id = "captured-walk-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                    elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                    elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                )),
            ),
            now = time.captureNow(), ids = IdPort { ACTIVITY_ID }, clock = time,
        )
        assertTrue(accepted.toString(), accepted is ActivityWrite.Accepted)
        assertEquals(ACTIVITY_ID, (accepted as ActivityWrite.Accepted).session.id)
        assertEquals(time.captureNow().localEpochDay, accepted.session.localEpochDay)
    }

    private fun travelToHonolulu(): Long {
        time.nowMs += 9L * 60L * 60L * 1000L
        time.zone = "Pacific/Honolulu"
        return time.captureNow().localEpochDay
    }

    private suspend fun withModel(
        handle: SavedStateHandle = SavedStateHandle(),
        block: suspend (HistoryViewModel) -> Unit,
    ) = coroutineScope {
        val model = HistoryViewModel(
            application = ApplicationProvider.getApplicationContext(), savedStateHandle = handle, container = deps,
        )
        val subscriber = launch(dispatcher) { model.uiState.collect {} }
        try {
            block(model)
        } finally {
            model.clearAndJoinForTest()
            subscriber.cancelAndJoin()
        }
    }

    private suspend fun awaitReady(
        model: HistoryViewModel,
        selected: (HistoryUiState) -> Boolean = { true },
    ): HistoryUiState = withTimeout(TestWaits.FLOW_MS) {
        model.uiState.first {
            !it.isLoading && !it.unavailable && !it.progressLoading && !it.progressFailed &&
                it.horizonProgress != null && selected(it)
        }
    }

    private fun savedValues(handle: SavedStateHandle): Map<String, Any?> =
        handle.keys().associateWith { handle.get<Any?>(it) }

    private suspend fun assertCoherentCardio(state: HistoryUiState, containsActivity: Boolean) {
        val range = checkNotNull(state.periodRange)
        val capturedDay = deps.database.activityDao().observeCompletedSummaries().first().single().localEpochDay
        assertEquals(setOf(capturedDay), state.completedEpochDays)
        assertEquals(containsActivity, capturedDay in range)
        assertEquals(
            if (containsActivity) setOf(HistoryKind.ACTIVITY to ACTIVITY_ID) else emptySet(),
            state.summaries.map { it.kind to it.id }.toSet(),
        )
        assertEquals(state.summaries.map { it.kind to it.id }.toSet(), state.monthGroups.flatMap { it.entries }.map { it.kind to it.id }.toSet())
        val totals = checkNotNull(state.horizonTotals)
        assertEquals(state.horizon, totals.horizon)
        assertEquals(range.startEpochDay, totals.startEpochDay)
        assertEquals(range.endEpochDay, totals.endEpochDay)
        assertEquals(if (containsActivity) 1 else 0, totals.sessionCount)
        assertEquals(if (containsActivity) 1 else 0, totals.trainedDays)
        assertEquals(0, totals.workingSets)
        assertEquals(0.0, totals.volumeKg, 0.0)
        assertEquals(if (containsActivity) 10 else 0, totals.activeMinutes)
        assertEquals(if (containsActivity) 600L else 0L, totals.cardioSeconds)
        assertEquals(if (containsActivity) 1000.0 else 0.0, totals.cardioDistanceMeters, 0.0)
        assertEquals(
            BlockReviewBuilder.overRange(
                startEpochDay = range.startEpochDay, endExclusiveEpochDay = range.endExclusiveEpochDay,
                items = deps.completedTrainingRepository.all(), unit = state.unit,
            ),
            state.horizonProgress,
        )
        if (state.horizon in listOf(AnalyticsHorizon.DAY, AnalyticsHorizon.WEEK, AnalyticsHorizon.MONTH)) {
            val calendarIds = state.calendar.weeks.flatten().flatMap { it.activityIds }.toSet()
            assertEquals(if (containsActivity) setOf(ACTIVITY_ID) else emptySet(), calendarIds)
        }
    }

    private suspend fun awaitAllReady(model: HistoryViewModel, today: Long): HistoryUiState =
        withTimeout(TestWaits.FLOW_MS) {
            model.uiState.first {
                it.today.epochDay == today && it.horizon == AnalyticsHorizon.ALL_TIME &&
                    !it.isLoading && !it.unavailable && !it.progressLoading && !it.progressFailed &&
                    it.horizonProgress != null
            }
        }

    private companion object {
        const val ACTIVITY_ID = "captured-tokyo-walk"
    }
}

private class CapturedDateTravelTime(var nowMs: Long, var zone: String) : TimePort by JvmTime {
    override fun nowMillis(): Long = nowMs
    override fun defaultZoneId(): String = zone
    override fun captureNow(zoneId: String): CapturedCivilTime = capture(nowMs, zoneId)
}
