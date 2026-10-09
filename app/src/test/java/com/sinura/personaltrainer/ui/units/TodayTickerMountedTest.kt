package com.sinura.personaltrainer.ui.units

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.util.JvmTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mounted lifecycle/clock effects over isolated data; no physical-midnight or process-death claim. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TodayTickerMountedTest {
    @get:Rule val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val time = MutableTickerTime(stamp(TODAY + 1) - 200L, "UTC")
    private lateinit var owner: MountedTickerOwner
    private lateinit var deps: FakeAppDependencies
    private var model: HistoryViewModel? = null
    private var mounted by mutableStateOf(true)
    private val composedDay = AtomicLong(Long.MIN_VALUE)
    private val composedDays = CopyOnWriteArrayList<Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher, time = time)
        compose.runOnIdle {
            owner = MountedTickerOwner()
            owner.event(Lifecycle.Event.ON_CREATE)
            owner.event(Lifecycle.Event.ON_START)
            owner.event(Lifecycle.Event.ON_RESUME)
        }
    }

    @After
    fun tearDown() {
        // Remove composition before draining anything: the midnight loop intentionally never ends.
        compose.runOnIdle { mounted = false }
        compose.waitForIdle()
        compose.runOnIdle { owner.event(Lifecycle.Event.ON_DESTROY) }
        runBlocking { model?.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun midnightAndResumeUpdateMountedHistoryWithoutWritesWhileAHistoricalChoiceStaysFixed() {
        runBlocking {
            insertTestExercise(deps, EXERCISE, "Ticker bench", "Chest")
            insertWorkout("before-midnight", TODAY)
            insertWorkout("after-midnight", TODAY + 1)
        }
        val originals = workoutSnapshot()
        val history = HistoryViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), deps)
        model = history
        history.setHorizon(AnalyticsHorizon.DAY)
        compose.setContent {
            if (mounted) CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val currentDay = rememberTodayEpochDay(time)
                SideEffect {
                    composedDay.set(currentDay)
                    composedDays += currentDay
                }
                CompositionLocalProvider(LocalTodayEpochDay provides currentDay) {
                    PersonalTrainerTheme {
                        HistoryScreen(
                            onOpenSession = {}, onOpenExercise = {}, onOpenActiveSession = {},
                            viewModel = history,
                        )
                    }
                }
            }
        }
        awaitHistory(TODAY, TODAY, "before-midnight")

        // Mutable TimePort is deliberately not Compose state; only the mounted timer can emit.
        time.nowMs = stamp(TODAY + 1) + 100L
        advanceClock(400L)
        awaitHistory(TODAY + 1, TODAY + 1, "after-midnight")
        assertEquals(originals, workoutSnapshot())

        compose.runOnIdle { history.selectDay(TODAY) }
        awaitHistory(TODAY + 1, TODAY, "before-midnight")
        assertFalse(history.uiState.value.selection.followToday)
        time.nowMs = stamp(TODAY + 2) + 100L
        resumeOwner()
        awaitHistory(TODAY + 2, TODAY, "before-midnight")
        assertEquals(originals, workoutSnapshot())
    }

    @Test
    fun resumeAfterATimezoneChangeReadsTheCurrentZoneWhenTheCivilDayChanges() {
        time.nowMs = stamp(TODAY) + 23 * 3_600_000L + 30 * 60_000L
        mountTicker()
        assertDay(TODAY)
        time.zone = "Asia/Tokyo"
        resumeOwner()
        assertDay(TODAY + 1)
    }

    @Test
    fun resumeAfterATimezoneChangeReschedulesMidnightEvenWhenTheCivilDayHasNotChangedYet() {
        time.nowMs = stamp(TODAY) + 23 * 3_600_000L - 200L
        mountTicker()
        assertDay(TODAY)
        // Paris is still on the same date, but its midnight is 200ms away rather than one hour.
        time.zone = "Europe/Paris"
        resumeOwner()
        assertDay(TODAY)
        time.nowMs = stamp(TODAY) + 23 * 3_600_000L + 100L
        advanceClock(400L)
        assertDay(TODAY + 1)
    }

    private fun mountTicker() {
        compose.setContent {
            if (mounted) CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Text(rememberTodayEpochDay(time).toString(), Modifier.testTag(TODAY_TAG))
            }
        }
        compose.waitForIdle()
    }

    private fun assertDay(day: Long) {
        compose.onNodeWithTag(TODAY_TAG).assertTextEquals(day.toString())
    }

    private fun resumeOwner() {
        compose.runOnIdle {
            owner.event(Lifecycle.Event.ON_PAUSE)
            owner.event(Lifecycle.Event.ON_STOP)
            owner.event(Lifecycle.Event.ON_START)
            owner.event(Lifecycle.Event.ON_RESUME)
        }
        // runOnIdle settles before its action; drain the snapshot update posted by resume.
        compose.waitForIdle()
    }

    private fun advanceClock(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
        // The composition may enqueue ViewModel work after its own clock advanced.
        dispatcher.scheduler.runCurrent()
    }

    private fun awaitHistory(today: Long, selected: Long, id: String) {
        try {
            compose.waitUntil(20_000L) {
                // A lifecycle restart can enqueue collection on the separate VM scheduler.
                // Run ready tasks only; the TimePort and all durable rows remain unchanged.
                dispatcher.scheduler.runCurrent()
                val state = model!!.uiState.value
                state.today.epochDay == today && state.periodRange?.startEpochDay == selected &&
                    state.periodRange?.endExclusiveEpochDay == selected + 1 && !state.isLoading &&
                    !state.progressLoading && !state.progressFailed && state.summaries.map { it.id } == listOf(id)
            }
            assertEquals("mounted civil day and History must agree", today, composedDay.get())
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(
                "Expected today=$today, selected=$selected, row=$id; composedDay=${composedDay.get()}, " +
                    "composedDays=$composedDays, now=${time.nowMs}, zone=${time.zone}, " +
                    "composeClock=${compose.mainClock.currentTime}, vmClock=${dispatcher.scheduler.currentTime}, " +
                    "owner=${owner.lifecycle.currentState}, ownerTransitions=${owner.transitions}, " +
                    "tickerReads=${time.tickerReads}, vm=${model!!.uiState.value}",
                failure,
            )
        }
    }

    private suspend fun insertWorkout(id: String, date: Long) {
        val at = stamp(date) + 12 * 3_600_000L
        deps.database.workoutDao().upsertSession(WorkoutSessionEntity(
            id, null, "Synthetic $id", at, "", 10, at, at + 600_000L,
        ))
        deps.database.workoutDao().insertSet(SetLogEntity(
            "$id-set", id, EXERCISE, 1, 100.0, 5, 8, false, at + 6_000L,
        ))
    }

    private fun workoutSnapshot() = runBlocking {
        deps.database.workoutDao().getAllSessions() to deps.database.workoutDao().getAllSets()
    }

    private companion object {
        val TODAY = CivilDate(2024, 11, 25).epochDay
        const val TODAY_TAG = "mounted-civil-today"
        const val EXERCISE = "mounted-ticker-bench"
        fun stamp(day: Long) = day * 86_400_000L
    }
}

private class MountedTickerOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    val transitions = CopyOnWriteArrayList<String>()
    override val lifecycle: Lifecycle get() = registry
    fun event(event: Lifecycle.Event) {
        transitions += "before $event: ${registry.currentState}, observers=${registry.observerCount}"
        registry.handleLifecycleEvent(event)
        transitions += "after $event: ${registry.currentState}, observers=${registry.observerCount}"
    }
}

/** Only clock and zone change; calendar/DST calculations are the real JVM adapter. */
private class MutableTickerTime(@Volatile var nowMs: Long, @Volatile var zone: String) : TimePort by JvmTime {
    val tickerReads = CopyOnWriteArrayList<String>()
    override fun nowMillis(): Long {
        val caller = Throwable().stackTrace.firstOrNull { it.className.contains("TodayTickerKt") }
        if (caller != null) tickerReads += "now=$nowMs zone=$zone at ${caller.className}.${caller.methodName}"
        return nowMs
    }
    override fun defaultZoneId(): String = zone
    override fun captureNow(zoneId: String): CapturedCivilTime = capture(nowMs, zoneId)
}
