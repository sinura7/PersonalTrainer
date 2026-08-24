package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
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
class WorkoutLifecycleUseCasesTest {
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
    fun finishWithNoSetsRefusesWithoutWriting() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val outcome = deps.finishWorkout(fixture.session.id)

        assertEquals(FinishOutcome.NothingLogged, outcome)
        assertNull(deps.workoutRepository.getSession(fixture.session.id)?.finishedAt)
    }

    @Test
    fun finishStopsRestPreservesNotesAndClearsDraft() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        deps.workoutRepository.updateSessionNotes(fixture.session.id, "keep")
        deps.workoutDraftCache.put(
            WorkoutDraft(fixture.session.id, fixture.exercise.id, 100.0, 5, null, false, ""),
        )
        deps.restTimerController.start(90, fixture.session.id)

        val outcome = deps.finishWorkout(fixture.session.id, notes = null)

        assertEquals(FinishOutcome.Finished(fixture.session.id), outcome)
        val saved = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        assertEquals("keep", saved.notes)
        assertTrue(saved.isFinished)
        assertFalse(deps.restTimerStore.current().running)
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
    }

    @Test
    fun alreadyFinishedIsIdempotentAndMissingIsExplicit() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            loggedSets = listOf(TestSetInput(100.0, 5)),
            finish = true,
        )

        assertEquals(
            FinishOutcome.Finished(fixture.session.id),
            deps.finishWorkout(fixture.session.id),
        )
        assertEquals(FinishOutcome.SessionMissing, deps.finishWorkout("missing"))
    }

    @Test
    fun discardStopsRestDeletesSessionAndClearsDraft() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutDraftCache.put(
            WorkoutDraft(fixture.session.id, fixture.exercise.id, 100.0, 5, null, false, ""),
        )
        deps.restTimerController.start(90, fixture.session.id)

        val outcome = deps.discardWorkout(fixture.session.id)

        assertEquals(DiscardOutcome.Discarded, outcome)
        assertNull(deps.workoutRepository.getSession(fixture.session.id))
        assertNull(deps.workoutDraftCache.get(fixture.session.id))
        assertFalse(deps.restTimerStore.current().running)
    }
}
