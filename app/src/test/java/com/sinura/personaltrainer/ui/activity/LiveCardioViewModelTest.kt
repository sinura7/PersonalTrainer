package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
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

    private fun createViewModel(
        sessionId: String,
        elapsedRealtime: () -> Long = { 0L },
        wallClock: () -> Long = { 0L },
    ): LiveCardioViewModel =
        LiveCardioViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
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
