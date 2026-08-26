package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ActivityDetailViewModel? = null

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
    fun missingActivityResolvesWithoutSpinner() = runBlocking {
        val state = createViewModel("missing").uiState.first { !it.isLoading }
        assertTrue(state.missing)
        assertFalse(state.isLoading)
    }

    @Test
    fun noonStampOmitsDurationAndKeepsStrengthVolume() = runBlocking {
        val now = JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Squat day",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(squat(now.instantMillis)),
            ),
            now,
        )
        val session = (write as ActivityWrite.Accepted).session
        val state = createViewModel(session.id).uiState.first { !it.isLoading }
        assertFalse(state.missing)
        assertEquals(500.0, state.volumeKg, 0.0001)
        assertEquals(1, state.strengthSetCount)
        assertEquals(0, state.durationMinutes)
        assertEquals(0, state.cardioMinutes)
    }

    @Test
    fun cardioReceiptUsesBlockMinutes() = runBlocking {
        val now = JvmTime.captureNow()
        val write = deps.confirmActivity(
            ActivityDraft(
                status = ActivityStatus.COMPLETED,
                origin = ActivityOrigin.BACKDATED,
                title = "Easy run",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(runBlock()),
            ),
            now,
        )
        val session = (write as ActivityWrite.Accepted).session
        val state = createViewModel(session.id).uiState.first { !it.isLoading }
        assertEquals(40, state.cardioMinutes)
        assertEquals(40, state.durationMinutes)
        assertEquals(0.0, state.volumeKg, 0.0001)
    }

    private fun createViewModel(activityId: String): ActivityDetailViewModel =
        ActivityDetailViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("activityId" to activityId)),
            container = deps,
        ).also { viewModel = it }

    private fun squat(completedAtMs: Long) = StrengthBlock(
        id = "blk-squat",
        sortOrder = 0,
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-1",
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAtMs = completedAtMs,
            ),
        ),
    )

    private fun runBlock() = CardioBlock(
        id = "blk-run",
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 2_400L,
        movingSeconds = 2_400L,
        distanceMeters = 6_000.0,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
