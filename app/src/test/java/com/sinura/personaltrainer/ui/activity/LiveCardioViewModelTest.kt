package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingGetGraphDao
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LiveCardioViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: LiveCardioViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingSessionResolvesGone() = runBlocking {
        val state = createViewModel("missing").uiState.first { it.missing }
        assertTrue(state.missing)
        assertNull(state.session)
    }

    @Test
    fun finishCompletesTheLiveRowAndClearsTheTimer() = runBlocking {
        deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val vm = createViewModel(
            sessionId = live.id,
            elapsedRealtime = { 60_000L },
            wallClock = { now.instantMillis + 60_000L },
        )
        val loaded = vm.uiState.first { it.session != null }
        assertFalse(loaded.missing)
        assertEquals(live.id, loaded.session?.id)

        vm.setDistanceKm("1.5")
        vm.finish()
        val finishedId = vm.finishedId.first { it != null }
        assertEquals(live.id, finishedId)
        assertNull(deps.activityRepository.getLive())
        assertNull(deps.cardioTimerPersistence.load())
        val completed = deps.activityRepository.get(live.id)
        assertEquals(1_500.0, completed?.cardioBlocks?.single()?.distanceMeters)
    }

    @Test
    fun finishParsesMilesWhenPoundsAreTheDisplayUnit() = runBlocking {
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val vm = createViewModel(
            sessionId = live.id,
            elapsedRealtime = { 60_000L },
            wallClock = { now.instantMillis + 60_000L },
        )
        vm.uiState.first { it.session != null }
        vm.setDistanceKm("1.5")
        vm.finish()
        vm.finishedId.first { it != null }
        val completed = deps.activityRepository.get(live.id)
        assertEquals(2_414.016, completed?.cardioBlocks?.single()?.distanceMeters!!, 0.001)
    }

    @Test
    fun discardRemovesTheLiveRow() = runBlocking {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val vm = createViewModel(live.id)
        vm.uiState.first { it.session != null }
        vm.discard()
        val gone = vm.uiState.first { it.missing }
        assertTrue(gone.missing)
        assertNull(deps.activityRepository.getLive())
        assertNull(deps.cardioTimerPersistence.load())
    }

    @Test
    fun recreationKeepsTheChangedInputs() = runBlocking {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val handle = SavedStateHandle(mapOf("sessionId" to live.id))
        val first = createViewModel(handle)
        withTimeout(TestWaits.FLOW_MS) { first.uiState.first { it.session != null } }
        first.setType(CardioType.WALK)
        first.setIndoor(true)
        first.setDistanceKm("2.5")
        first.clearAndJoinForTest()

        // The row still says RUN outdoors; the owner's later choices must win over it.
        val state = withTimeout(TestWaits.FLOW_MS) { createViewModel(handle).uiState.first { it.session != null } }
        assertEquals(CardioType.WALK, state.type)
        assertTrue(state.indoor)
        assertEquals("2.5", state.distanceKm)
    }

    @Test
    fun recreationKeepsIndoorWhenOnlyIndoorChanged() = runBlocking {
        // Each input is restored by its own key: a guard on the type key alone let the
        // row's outdoor flag overwrite an indoor toggle the owner had made on its own.
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        val handle = SavedStateHandle(mapOf("sessionId" to live.id))
        val first = createViewModel(handle)
        withTimeout(TestWaits.FLOW_MS) { first.uiState.first { it.session != null } }
        first.setIndoor(true)
        first.clearAndJoinForTest()

        val state = withTimeout(TestWaits.FLOW_MS) { createViewModel(handle).uiState.first { it.session != null } }
        assertTrue(state.indoor)
        assertEquals(CardioType.RUN, state.type)
    }

    @Test
    fun aFailedReadIsUnavailableNotGoneAndRetries() = runBlocking {
        val gate = ActivityReadGate(shouldFail = false)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { FailingGetGraphDao(it, gate) },
        )
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock()), now)
        val live = (started as ActivityWrite.Accepted).session
        gate.shouldFail = true

        val vm = createViewModel(live.id)
        val failed = withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { it.failed } }
        assertFalse("a read fault is not a gone session", failed.missing)
        assertNull(failed.session)

        gate.shouldFail = false
        vm.retry()
        val loaded = withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { it.session != null } }
        assertFalse(loaded.failed)
        assertEquals(live.id, loaded.session?.id)
        assertEquals(live.id, deps.activityRepository.getLive()?.id)
    }

    private fun createViewModel(
        sessionId: String,
        elapsedRealtime: () -> Long = { 0L },
        wallClock: () -> Long = { 0L },
    ): LiveCardioViewModel = createViewModel(
        handle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        elapsedRealtime = elapsedRealtime,
        wallClock = wallClock,
    )

    private fun createViewModel(
        handle: SavedStateHandle,
        elapsedRealtime: () -> Long = { 0L },
        wallClock: () -> Long = { 0L },
    ): LiveCardioViewModel =
        LiveCardioViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = handle,
            container = deps,
            elapsedRealtime = elapsedRealtime,
            wallClock = wallClock,
        ).also { viewModel = it }

    private fun runBlock() = CardioBlock(
        id = "blk-live",
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 0L,
        movingSeconds = 0L,
        distanceMeters = null,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
