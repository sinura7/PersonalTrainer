package com.sinura.personaltrainer.activity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OneLiveCrossLaneTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun liveCardioBlocksAStrengthStart() = runBlocking {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Cardio", listOf(cardioBlock()), now)
        assertTrue(started is ActivityWrite.Accepted)
        val outcome = deps.workoutRepository.startFreeWorkoutSafely("Push")
        assertTrue(outcome.toString().contains("One live") || outcome is com.sinura.personaltrainer.data.repository.StartSessionOutcome.Unavailable)
    }

    @Test
    fun inProgressWorkoutBlocksLiveCardio() = runBlocking {
        deps.workoutRepository.startFreeWorkout("Legs")
        val now = JvmTime.captureNow()
        val write = deps.startLiveActivity("Cardio", listOf(cardioBlock()), now)
        assertEquals(ActivityWrite.Rejected("One live activity at a time."), write)
    }

    @Test
    fun overlappingCardioAndStrengthStartLeavesExactlyOneLive() = runBlocking {
        val now = JvmTime.captureNow()
        val cardio = async(Dispatchers.IO) {
            deps.startLiveActivity("Cardio", listOf(cardioBlock()), now)
        }
        val strength = async(Dispatchers.IO) {
            deps.workoutRepository.startFreeWorkoutSafely("Push")
        }
        val liveWrite = cardio.await()
        val workout = strength.await()
        val liveAccepted = liveWrite is ActivityWrite.Accepted
        val workoutStarted = workout is StartSessionOutcome.Started
        assertEquals(
            "exactly one lane must win",
            1,
            (if (liveAccepted) 1 else 0) + (if (workoutStarted) 1 else 0),
        )
        if (liveAccepted) {
            assertTrue(deps.activityRepository.getLive() != null)
            assertEquals(null, deps.database.workoutDao().getInProgressSession())
        } else {
            assertEquals(null, deps.activityRepository.getLive())
            assertTrue(deps.database.workoutDao().getInProgressSession() != null)
        }
    }

    private fun cardioBlock() = CardioBlock(
        id = "blk-live",
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 0,
        movingSeconds = 0,
        distanceMeters = null,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
