package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

        assertEquals(FinishOutcome.SessionMissing, deps.finishWorkout("missing"))
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun notesPendingDoesNotStartCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(70.0, 5)))
        deps.workoutDraftCache.markNotesPending(
            sessionId = fixture.session.id,
            owner = "notes-test",
            pending = true,
            busy = true,
            notes = "draft",
        )
        assertEquals(FinishOutcome.NotesPending, deps.finishWorkout(fixture.session.id))
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun inProgressFinishDoesNotStartCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(75.0, 5)))
        assertTrue(deps.workoutDraftCache.beginFinish(fixture.session.id))
        try {
            assertEquals(FinishOutcome.InProgress, deps.finishWorkout(fixture.session.id))
            assertFalse(deps.restTimerStore.current().running)
        } finally {
            deps.workoutDraftCache.endFinish(fixture.session.id)
        }
    }

    @Test
    fun failedFinishSessionWriteDoesNotStartCooldown() = runBlocking {
        val throwingDeps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real ->
                object : WorkoutDao by real {
                    override suspend fun finishSession(
                        id: String,
                        notes: String,
                        durationMinutes: Int,
                        finishedAt: Long,
                    ): Int = throw java.sql.SQLException("disk full")
                }
            },
        )
        try {
            val fixture = seedTestWorkout(throwingDeps, loggedSets = listOf(TestSetInput(88.0, 5)))
            val outcome = throwingDeps.finishWorkout(fixture.session.id)
            assertTrue(outcome is FinishOutcome.Failed)
            assertFalse(throwingDeps.restTimerStore.current().running)
        } finally {
            throwingDeps.close()
        }
    }

    @Test
    fun pendingSetFailedDoesNotStartCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(85.0, 5)))
        val exerciseId = fixture.session.exercises.first().exercise.id
        deps.workoutDraftCache.putPendingSave(
            WorkoutSetSave(
                sessionId = fixture.session.id,
                exerciseId = exerciseId,
                setId = "pending",
                completedAt = 1_700_000_000_000L,
                values = WorkoutSetValues(85.0, 5, 8, false, durationSeconds = null),
            ),
        )
        val outcome = deps.finishWorkout(fixture.session.id)
        assertTrue(outcome is FinishOutcome.Failed)
        assertFalse(deps.restTimerStore.current().running)
    }

    @Test
    fun idempotentFinishDoesNotRestartCooldown() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(80.0, 5)))
        assertEquals(FinishOutcome.Finished(fixture.session.id), deps.finishWorkout(fixture.session.id))
        val firstId = deps.restTimerStore.current().timerId
        assertTrue(deps.restTimerStore.current().running)
        assertTrue(deps.restTimerController.skipIfShown(firstId))
        assertFalse(deps.restTimerStore.current().running)

        assertEquals(FinishOutcome.Finished(fixture.session.id), deps.finishWorkout(fixture.session.id))
        assertFalse(deps.restTimerStore.current().running)
    }

    /**
     * Local guard check: without the idempotent cool-down gate, a second finish after skip would
     * start another cool-down. Remove the guard in [FinishWorkout] to see this fail.
     */
    @Test
    fun idempotentFinishWouldRestartCooldownWithoutTheGuard() = runBlocking {
        val fixture = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(80.0, 5)))
        deps.finishWorkout(fixture.session.id)
        val firstId = deps.restTimerStore.current().timerId
        deps.restTimerController.skipIfShown(firstId)
        assertFalse(deps.restTimerStore.current().running)

        deps.restTimerController.startCooldown()
        assertTrue(deps.restTimerStore.current().running)
        assertNotEquals(firstId, deps.restTimerStore.current().timerId)
    }
}
