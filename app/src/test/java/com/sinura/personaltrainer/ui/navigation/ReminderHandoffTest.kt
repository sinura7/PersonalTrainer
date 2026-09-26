package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.PendingOccurrence
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.workout.StartCardioOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a reminder tap meets, read from the database as it arrives (audit UI-1). The live bar's
 * state read "nothing live" until the database answered, so a tap into a new activity, or into
 * one Android had restored, went to Home over the workout or was taken by Home first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderHandoffTest {
    private var deps: FakeAppDependencies? = null

    @After
    fun tearDown() {
        deps?.close()
    }

    private fun graph(workoutDaoDecorator: (WorkoutDao) -> WorkoutDao = { it }): FakeAppDependencies =
        FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = workoutDaoDecorator,
        ).also { deps = it }

    @Test
    fun withNothingLiveTheTapGoesToHome() = runBlocking {
        val graph = graph()

        assertEquals(TapVerdict.NothingLive, ReminderHandoff.verdict(graph, TAPPED))
    }

    @Test
    fun aWorkoutFollowingAnotherPlannedDayHoldsTheTap() = runBlocking {
        val graph = graph()
        val live = startWorkout(graph)
        PendingOccurrence.bindForSession(graph, "occ-other", live)

        assertEquals(TapVerdict.OtherSessionLive, ReminderHandoff.verdict(graph, TAPPED))
    }

    @Test
    fun aFreeWorkoutHoldsTheTap() = runBlocking {
        val graph = graph()
        startWorkout(graph)

        assertEquals(TapVerdict.OtherSessionLive, ReminderHandoff.verdict(graph, TAPPED))
    }

    /** The planned day started early, or from Home or Plan: its own reminder opens it. */
    @Test
    fun aWorkoutFollowingTheTappedDayIsThatSession() = runBlocking {
        val graph = graph()
        val live = startWorkout(graph)
        PendingOccurrence.bindForSession(graph, TAPPED, live)

        assertEquals(
            TapVerdict.ThisSessionLive(sessionId = live, cardio = false),
            ReminderHandoff.verdict(graph, TAPPED),
        )
    }

    @Test
    fun liveCardioHoldsATapForAnotherDayAndOpensForItsOwn() = runBlocking {
        val graph = graph()
        val open = graph.startLiveCardio(
            type = CardioType.RUN,
            now = graph.time.captureNow(),
            occurrenceId = TAPPED,
        ) as StartCardioOutcome.Open

        assertEquals(TapVerdict.OtherSessionLive, ReminderHandoff.verdict(graph, "occ-other"))
        assertEquals(
            TapVerdict.ThisSessionLive(sessionId = open.sessionId, cardio = true),
            ReminderHandoff.verdict(graph, TAPPED),
        )
    }

    /** Home then tries the start as it always did, and says what went wrong. */
    @Test
    fun aFailedReadCountsAsNothingLive() = runBlocking {
        val graph = graph(workoutDaoDecorator = { real -> UnreadableInProgress(real) })

        assertEquals(TapVerdict.NothingLive, ReminderHandoff.verdict(graph, TAPPED))
    }

    private suspend fun startWorkout(graph: FakeAppDependencies): String =
        (graph.workoutRepository.startFreeWorkoutSafely("Legs") as StartSessionOutcome.Started).session.id

    private class UnreadableInProgress(delegate: WorkoutDao) : WorkoutDao by delegate {
        override suspend fun getInProgressSession(): WorkoutSessionEntity? = error("boom: Room could not read")
    }

    private companion object {
        const val TAPPED = "occ-tapped"
    }
}
