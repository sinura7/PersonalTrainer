package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The picker's suggested lift is the lift as it is now, and the lift the coach's card names now
 * (W2c review, S1).
 *
 * A custom lift can change while the Log is open: edited in the library, or by sync from another
 * device. The coach's insights recompute with the catalogue, so the card keeps its lift and its
 * reason and only names the lift afresh. W2c read the lift again only when the lift or the reason
 * moved, so the picker kept the old name, and taking the suggestion set the lift up by its old
 * load: sets, reps and rest for a barbell on what was now a pin-stack machine.
 *
 * The library: the Squat in the workout, and two back lifts that are not, a custom Row and a
 * Lat Pulldown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SuggestedLiftEditTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    /** The coach's cards, as the shared insights pipeline publishes them. */
    private val insights = MutableStateFlow(TrainingInsights())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            insights = insights,
            scheduler = dispatcher,
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking { logs.forEach { it.clearAndJoinForTest() } }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aLiftEditedWhileTheLogIsOpenIsSuggestedAndAddedAsItIsNow() = runBlocking {
        val session = seedWorkout()
        val log = createLog(session.id)
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        insights.value = cards(backCard(ROW, named = "Row"))
        val before = log.awaitSuggestion("the Row suggested") { it.suggestion?.id == ROW }
        assertEquals("the suggestion names the Row", "Row", before.suggestion?.name)
        val asItWas = AddDefaults.forExercise(checkNotNull(before.suggestion))

        // The edit, as the library's editor saves it: a new name, and a pin-stack machine now.
        val saved = deps.exerciseRepository.updateCustom(
            id = ROW,
            name = "Seated Cable Row",
            muscleGroup = "Back",
            notes = "",
            loadType = LoadType.STACK,
        )
        assertTrue("the edit is saved: $saved", saved is SaveExerciseResult.Saved)
        // The insights recompute with the catalogue: the same lift for the same reason, named afresh.
        insights.value = cards(backCard(ROW, named = "Seated Cable Row"))
        log.setPickerVisible(true)
        val edited = log.awaitSuggestion("the suggestion named as the lift is now") {
            it.showExercisePicker && it.suggestion?.name == "Seated Cable Row"
        }
        assertEquals("the suggestion keeps the card's reason", REASON, edited.suggestionReason)
        val suggested = checkNotNull(edited.suggestion)
        assertEquals("the suggestion is the lift as it is now: a pin stack", LoadType.STACK, suggested.loadType)

        val asItIs = AddDefaults.forExercise(checkNotNull(deps.exerciseRepository.getById(ROW)))
        assertNotEquals("the edit must change how the lift is set up, or this test proves nothing", asItWas, asItIs)
        log.addExercise(suggested)
        val added = deps.workoutRepository.awaitSession(session.id) { stored ->
            stored.exercises.any { it.exercise.id == ROW }
        }.exercises.first { it.exercise.id == ROW }
        assertEquals(
            "the lift taken from the suggestion is set up as the lift is now (sets, reps, rest)",
            listOf(asItIs.sets, asItIs.reps, asItIs.restSeconds),
            listOf(added.targetSets, added.targetReps, added.restSeconds),
        )
    }

    @Test
    fun theSuggestionFollowsTheCardToAnotherLiftForTheSameReason() = runBlocking {
        val session = seedWorkout()
        val log = createLog(session.id)
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        insights.value = cards(backCard(ROW, named = "Row"))
        log.awaitSuggestion("the Row suggested") { it.suggestion?.id == ROW }

        // Back is still behind chest; the card now names the pulldown for it.
        insights.value = cards(backCard(PULLDOWN, named = "Lat Pulldown"))
        val moved = log.awaitSuggestion("the suggestion moved to the Lat Pulldown for the same reason") {
            it.suggestion?.id == PULLDOWN
        }
        assertEquals("the suggestion names the pulldown", "Lat Pulldown", moved.suggestion?.name)
        assertEquals("the reason is the card's", REASON, moved.suggestionReason)
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun createLog(sessionId: String): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(logs::add)

    /** The Log's state once its suggestion is the one [what] names, waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitSuggestion(
        what: String,
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError(
            "Never saw $what; the Log suggested ${uiState.value.suggestion} for \"${uiState.value.suggestionReason}\"",
        )

    private fun cards(vararg recommendations: TrainingRecommendation) =
        TrainingInsights(recommendations = recommendations.toList())

    /** The coach's balance card, naming [liftId] as the insights name it from the catalogue. */
    private fun backCard(liftId: String, named: String) = TrainingRecommendation(
        id = "rec-back",
        kicker = "BALANCE",
        title = REASON,
        reason = "coverage",
        priority = RecommendationPriority.INFO,
        rankScore = 10,
        actionExerciseId = liftId,
        actionExerciseName = named,
    )

    /** A workout on the Squat; the Row (a custom barbell lift) and the Lat Pulldown are not in it. */
    private suspend fun seedWorkout(): WorkoutSession {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = SQUAT,
                    name = "Squat",
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = "EXTERNAL",
                    nameKey = "squat",
                ),
                ExerciseEntity(
                    id = ROW,
                    name = "Row",
                    muscleGroup = "Back",
                    notes = "",
                    isCustom = true,
                    loadType = "EXTERNAL",
                    nameKey = "row",
                ),
                ExerciseEntity(
                    id = PULLDOWN,
                    name = "Lat Pulldown",
                    muscleGroup = "Back",
                    notes = "",
                    isCustom = false,
                    loadType = "STACK",
                    nameKey = "lat pulldown",
                ),
            ),
        )
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Lower", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$SQUAT",
                routineId = ROUTINE,
                exerciseId = SQUAT,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        return deps.workoutRepository.startRoutine(checkNotNull(deps.routineRepository.getById(ROUTINE)))
    }

    private companion object {
        const val SQUAT = "squat"
        const val ROW = "row"
        const val PULLDOWN = "lat-pulldown"
        const val ROUTINE = "routine-lower"
        const val STAMP = 1_700_000_000_000L
        const val REASON = "Back is behind chest"
    }
}
