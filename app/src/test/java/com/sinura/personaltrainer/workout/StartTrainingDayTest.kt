package com.sinura.personaltrainer.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.time.DayOfWeek
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
class StartTrainingDayTest {
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
    fun healthyPinnedDayStartsRoutine() = runBlocking {
        val fixture = seedTestWorkout(deps)
        deps.workoutRepository.discardSession(fixture.session.id)

        val outcome = deps.startTrainingDay(day(routineId = fixture.routine.id))

        assertTrue(outcome is StartDayOutcome.Open)
        val id = (outcome as StartDayOutcome.Open).sessionId
        assertEquals(fixture.routine.id, deps.workoutRepository.getSession(id)?.routineId)
    }

    @Test
    fun focusOnlyDayStartsNamedFreeWorkout() = runBlocking {
        val outcome = deps.startTrainingDay(
            day(routineId = null, routineName = null, focusTitle = "Pull"),
        )

        val id = (outcome as StartDayOutcome.Open).sessionId
        assertEquals("Pull", deps.workoutRepository.getSession(id)?.routineName)
    }

    @Test
    fun restDayIsIgnoredWithoutWriting() = runBlocking {
        val outcome = deps.startTrainingDay(day(isRest = true))
        assertEquals(StartDayOutcome.Ignored, outcome)
        assertNull(deps.workoutRepository.getInProgress())
    }

    @Test
    fun liveSessionBlocksInsteadOfSilentResume() = runBlocking {
        val fixture = seedTestWorkout(deps)

        val outcome = deps.startTrainingDay(day(routineId = fixture.routine.id))

        assertEquals(StartDayOutcome.Blocked(fixture.session.id), outcome)
        assertEquals(fixture.session.id, deps.workoutRepository.getInProgress()?.id)
    }

    @Test
    fun deletedOrEmptyPinnedRoutineFailsExplicitly() = runBlocking {
        val missing = deps.startTrainingDay(day(routineId = "gone", routineName = "Gone"))
        assertTrue(missing is StartDayOutcome.Failed)

        val empty = deps.routineRepository.create("Empty routine")
        val emptyOutcome = deps.startTrainingDay(
            day(routineId = empty.id, routineName = empty.name),
        )
        assertTrue(emptyOutcome is StartDayOutcome.Failed)
        assertTrue((emptyOutcome as StartDayOutcome.Failed).message.contains("no lifts"))
    }

    private fun day(
        isRest: Boolean = false,
        routineId: String? = "test-routine",
        routineName: String? = "Test lower",
        focusTitle: String = "Legs",
    ) = SuggestedTrainingDay(
        epochDay = 20_000,
        dayOfWeek = DayOfWeek.MONDAY,
        isRest = isRest,
        focusKind = SessionFocusKind.LEGS,
        focusTitle = focusTitle,
        routineId = routineId,
        routineName = routineName,
        reason = "Test",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
        slotId = "slot-test",
    )
}
