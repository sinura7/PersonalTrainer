package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepeatSessionEndsCooldownTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun repeatSessionDuringCooldownStopsTheCooldown() = runBlocking {
        val finished = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(60.0, 8)),
            finish = true,
        )
        deps.restTimerController.startCooldown()
        assertTrue(RestTimerMarkers.isCooldown(deps.restTimerStore.current()))

        val outcome = deps.workoutRepository.repeatSession(finished.session.id)
        assertTrue(outcome is RepeatOutcome.Started)
        assertFalse(deps.restTimerStore.current().running)
    }
}
