package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CompleteTrainingOutcome
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingGetGraphDao
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompleteTrainingTest {
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
    fun finishWorkoutMapsTheThreeAnswers() = runBlocking {
        val empty = seedTestWorkout(deps)
        val nothing = deps.completeTraining.finishWorkout(empty.session.id)
        assertTrue(nothing is CompleteTrainingOutcome.RuledOut)
        assertEquals(CompleteTraining.NOTHING_LOGGED, (nothing as CompleteTrainingOutcome.RuledOut).reason)

        val logged = seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)))
        val accepted = deps.completeTraining.finishWorkout(logged.session.id)
        assertEquals(CompleteTrainingOutcome.Written(logged.session.id), accepted)

        val missing = deps.completeTraining.finishWorkout("missing")
        assertTrue(missing is CompleteTrainingOutcome.RuledOut)
        assertEquals(
            DataHealthCopy.FINISH_NOT_FOUND,
            (missing as CompleteTrainingOutcome.RuledOut).reason,
        )
    }

    @Test
    fun confirmMapsAcceptedAndRejected() = runBlocking {
        val now = JvmTime.captureNow()
        val empty = deps.completeTraining.confirm(
            ActivityDraft(
                origin = ActivityOrigin.BACKDATED,
                title = "Nothing",
                performedStart = now,
                performedEnd = now,
                blocks = emptyList(),
            ),
            now,
        )
        assertTrue(empty is CompleteTrainingOutcome.RuledOut)
        assertEquals("Nothing to save.", (empty as CompleteTrainingOutcome.RuledOut).reason)

        val accepted = deps.completeTraining.confirm(
            ActivityDraft(
                origin = ActivityOrigin.BACKDATED,
                title = "Easy run",
                performedStart = now,
                performedEnd = now,
                blocks = listOf(runBlock("blk-confirm")),
            ),
            now,
        )
        assertTrue(accepted is CompleteTrainingOutcome.Written)
        assertEquals(
            (accepted as CompleteTrainingOutcome.Written).id,
            deps.activityRepository.all().single().id,
        )
    }

    @Test
    fun finishLiveActivityAcceptsAndClearsTheTimer() = runBlocking {
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock("blk-live")), now)
        val live = (started as ActivityWrite.Accepted).session
        deps.cardioTimerPersistence.save(
            PersistedCardioTimer(
                sessionId = live.id,
                startedAtElapsedRealtime = 0L,
                startedAtWallClockMillis = now.instantMillis,
                bootMarker = 1L,
            ),
        )

        val outcome = deps.completeTraining.finishLiveActivity(live.id, now)
        assertEquals(CompleteTrainingOutcome.Written(live.id), outcome)
        assertNull(deps.cardioTimerPersistence.load())
        assertTrue(deps.activityRepository.get(live.id)!!.isCompleted)
    }

    @Test
    fun aThrownLiveFinishIsFailedNotRejected() = runBlocking {
        val gate = ActivityReadGate(shouldFail = false)
        deps.close()
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { FailingGetGraphDao(it, gate) },
        )
        val now = JvmTime.captureNow()
        val started = deps.startLiveActivity("Easy run", listOf(runBlock("blk-fail")), now)
        val live = (started as ActivityWrite.Accepted).session
        gate.shouldFail = true

        val outcome = deps.completeTraining.finishLiveActivity(live.id, now)
        assertTrue(outcome is CompleteTrainingOutcome.Failed)
        assertEquals(CompleteTraining.LIVE_FINISH_FAILED, (outcome as CompleteTrainingOutcome.Failed).message)
    }

    @Test
    fun savedStateDraftsImplementTheContract() {
        val handle = SavedStateHandle()
        val store = SavedStateWorkoutDraft(handle)
        val draft = WorkoutDraft("s1", "ex", 100.0, 5, null, false, "note")
        store.write(draft)
        assertEquals(draft, store.read())
        store.clear()
        assertNull(store.read())
    }

    private fun runBlock(id: String) = CardioBlock(
        id = id,
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 600L,
        movingSeconds = 600L,
        distanceMeters = 1_500.0,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )
}
