package com.sinura.personaltrainer.domain

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.ui.activity.CardioInputDraft
import com.sinura.personaltrainer.ui.activity.ComposerCardioLine
import com.sinura.personaltrainer.ui.activity.ComposerDraftSnapshot
import com.sinura.personaltrainer.ui.activity.SavedStateCardioDraft
import com.sinura.personaltrainer.ui.activity.SavedStateComposerDraft
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraft
import com.sinura.personaltrainer.workout.WorkoutDraftCache
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

/**
 * Draft recovery is one contract, three stores. An accepted save clears;
 * a rejected save leaves what was typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DraftStoreTest {
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
    fun workoutHandleReadWriteClear() {
        val store: DraftStore<WorkoutDraft> = SavedStateWorkoutDraft(SavedStateHandle())
        val draft = WorkoutDraft("s1", "ex", 80.0, 5, 8, false, "")
        store.write(draft)
        assertEquals(draft, store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun cacheSliceClearsOnlyThatSession() {
        val cache = WorkoutDraftCache()
        cache.put(WorkoutDraft("a", "ex", 100.0, 5, null, false, ""))
        cache.put(WorkoutDraft("b", "ex", 90.0, 5, null, false, ""))
        val store = cache.storeFor("a")
        store.clear()
        assertNull(store.read())
        assertEquals("b", cache.get("b")?.sessionId)
    }

    @Test
    fun acceptedFinishClearsTheCacheSliceAndARefusalLeavesIt() = runBlocking {
        val empty = seedTestWorkout(deps)
        val emptyDraft = WorkoutDraft(empty.session.id, empty.exercise.id, 100.0, 5, null, false, "")
        deps.workoutDraftCache.put(emptyDraft)
        val refused = deps.completeTraining.finishWorkout(empty.session.id)
        assertTrue(refused is CompleteTrainingOutcome.Rejected)
        assertEquals(emptyDraft, deps.workoutDraftCache.storeFor(empty.session.id).read())

        val logged = seedTestWorkout(
            deps,
            exerciseId = "logged-squat",
            exerciseName = "Logged squat",
            routineId = "logged-routine",
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        deps.workoutDraftCache.put(
            WorkoutDraft(logged.session.id, logged.exercise.id, 100.0, 5, null, false, ""),
        )
        val accepted = deps.completeTraining.finishWorkout(logged.session.id)
        assertTrue(accepted is CompleteTrainingOutcome.Accepted)
        assertNull(deps.workoutDraftCache.storeFor(logged.session.id).read())
    }

    @Test
    fun composerAndCardioStoresRoundTripThenClear() {
        val composer: DraftStore<ComposerDraftSnapshot> = SavedStateComposerDraft(SavedStateHandle())
        val snapshot = ComposerDraftSnapshot(
            title = "Leg day",
            epochDay = 20_000L,
            strength = emptyList(),
            cardio = listOf(ComposerCardioLine(CardioType.RUN, 30, 5.0, false)),
        )
        composer.write(snapshot)
        assertEquals(snapshot, composer.read())
        composer.clear()
        assertNull(composer.read())

        val cardio: DraftStore<CardioInputDraft> = SavedStateCardioDraft(SavedStateHandle())
        val inputs = CardioInputDraft(CardioType.RIDE, indoor = true, distanceKm = "12.4")
        cardio.write(inputs)
        assertEquals(inputs, cardio.read())
        cardio.clear()
        assertNull(cardio.read())
    }
}
