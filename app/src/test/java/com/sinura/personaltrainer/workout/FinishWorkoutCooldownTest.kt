package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FinishWorkoutCooldownTest {
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
    fun finishedOutcomeStartsOneCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.restTimerController.start(60, fixture.session.id, "Routine")

        val outcome = deps.finishWorkout(fixture.session.id)

        assertEquals(FinishOutcome.Finished(fixture.session.id), outcome)
        val snap = deps.restTimerStore.current()
        assertTrue(snap.running)
        assertEquals(RestTimerMarkers.COOLDOWN_SECONDS, snap.totalSeconds)
        assertEquals(RestTimerMarkers.COOLDOWN, snap.sourceTag)
        assertNull(snap.sessionId)
    }

    @Test
    fun refusedOutcomesDoNotStartCooldown() = runBlocking {
        val empty = seedTestWorkout(deps)
        assertEquals(FinishOutcome.NothingLogged, deps.finishWorkout(empty.session.id))
        assertFalse(deps.restTimerStore.current().running)

        val logged = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(50.0, 5)))
        assertEquals(FinishOutcome.SessionMissing, deps.finishWorkout("missing"))
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun idempotentFinishDoesNotRestartCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(80.0, 5)), finish = true)
        deps.restTimerController.startCooldown()
        val firstId = deps.restTimerStore.current().timerId

        assertEquals(FinishOutcome.Finished(fixture.session.id), deps.finishWorkout(fixture.session.id))
        assertEquals(firstId, deps.restTimerStore.current().timerId)
    }
}
