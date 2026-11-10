package com.sinura.personaltrainer.ui.workout

import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpgradeLiveSessionGymTest {
    @Test
    fun legacySessionStartsUnassignedUntilGymAssigned() = runBlocking {
        val deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val outcome = deps.workoutRepository.startFreeWorkoutSafely(gymId = null)
        val sessionId = (outcome as StartSessionOutcome.Started).session.id
        assertNull(deps.workoutRepository.getSession(sessionId)!!.gymId)
        deps.workoutRepository.moveSessionToGym(sessionId, "gym-af-weston")
        assertEquals("gym-af-weston", deps.workoutRepository.getSession(sessionId)!!.gymId)
    }
}
