package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.TestWaits
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

/**
 * What the Log's lift picker is given: its list, its swap suggestions and the coach's suggested
 * lift (W2c). Nothing pinned these before W2c changed where they come from.
 *
 * The library: the Barbell Back Squat in the workout, two more squats (Front, Goblet), a Bench
 * Press and a Row. Last session trained the Goblet Squat, so the open picker leads with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutPickerSourcesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val logs = mutableListOf<ActiveWorkoutViewModel>()

    /** The coach's cards; [taken] counts the ones the Log has picked up. */
    private val insights = MutableStateFlow(TrainingInsights())
    private val taken = AtomicInteger()

    /** Room watches of one lift by id begun, through [countingLiftWatches]. */
    private val liftWatches = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Once armed, holds every catalogue answer, through [holdingNewCatalogueReads]. The Log's own
     * catalogue reads all answer while it loads, before the gate is armed, and nothing here
     * writes the catalogue after. A picker that began a read of its own on opening would then
     * draw its first frames without an answer: certain, rather than a race with Room's thread.
     */
    @Volatile private var catalogueGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            insights = insights.onEach { taken.incrementAndGet() },
            scheduler = dispatcher,
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        catalogueGate?.complete(Unit)
        runBlocking { logs.forEach { it.clearAndJoinForTest() } }
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    // --- Pins: green before W2c and after it ---------------------------------------------------

    @Test
    fun theOpenPickerLeadsWithWhatYouTrainedAndATypedSearchKeepsCatalogOrder() = runBlocking {
        val log = createLog(seedWorkout().session.id)
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }

        log.setPickerVisible(true)
        val open = log.awaitState { it.showExercisePicker && it.searchResults.size == LIBRARY_SIZE }
        assertEquals(
            "blank query: last trained first, then the catalog's rank, then name",
            listOf(GOBLET, SQUAT, BENCH, FRONT, ROW),
            open.searchResults.map { it.id },
        )

        log.onSearchQuery("squat")
        val typed = log.awaitState { it.searchQuery == "squat" && it.searchResults.size == 3 }
        assertEquals(
            "a typed search keeps catalog order; recency does not re-rank it",
            listOf(SQUAT, FRONT, GOBLET),
            typed.searchResults.map { it.id },
        )

        log.onSearchQuery("")
        val cleared = log.awaitState { it.searchQuery.isEmpty() && it.searchResults.size == LIBRARY_SIZE }
        assertEquals(
            "a cleared search leads with what you trained again",
            listOf(GOBLET, SQUAT, BENCH, FRONT, ROW),
            cleared.searchResults.map { it.id },
        )
    }

    @Test
    fun openingThePickerNeverShowsAnEmptyLibrary() = runBlocking {
        val log = createLog(seedWorkout().session.id, container = holdingNewCatalogueReads())
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        dispatcher.scheduler.advanceUntilIdle()
        val held = CompletableDeferred<Unit>().also { catalogueGate = it }

        val drawn = CopyOnWriteArrayList<ActiveWorkoutUiState>()
        val watch = launch(dispatcher) { log.uiState.collect { drawn += it } }
        log.setPickerVisible(true)
        log.awaitState { it.showExercisePicker }
        held.complete(Unit)
        log.awaitState { it.showExercisePicker && it.searchResults.size == LIBRARY_SIZE }
        watch.cancel()

        val empty = drawn.filter { it.showExercisePicker && it.searchResults.isEmpty() }
        assertTrue(
            "the picker opened on an empty \"Search the library\" card ${empty.size} time(s) before its list",
            empty.isEmpty(),
        )
    }

    @Test
    fun aSwapIsTitledSwapFromItsFirstFrameAndOffersTheLiftsSiblings() = runBlocking {
        val log = createLog(seedWorkout().session.id, container = holdingNewCatalogueReads())
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        dispatcher.scheduler.advanceUntilIdle()
        val held = CompletableDeferred<Unit>().also { catalogueGate = it }

        val drawn = CopyOnWriteArrayList<ActiveWorkoutUiState>()
        val watch = launch(dispatcher) { log.uiState.collect { drawn += it } }
        log.requestSwap()
        log.awaitState { it.showExercisePicker }
        held.complete(Unit)
        val swap = log.awaitState {
            it.showExercisePicker && it.swapSiblings.isNotEmpty() && it.searchResults.size == LIBRARY_SIZE
        }
        watch.cancel()

        val asAdd = drawn.filter { it.showExercisePicker && !it.swapping }
        assertTrue("the swap flashed \"Add a lift\" ${asAdd.size} time(s)", asAdd.isEmpty())
        val empty = drawn.filter { it.showExercisePicker && it.searchResults.isEmpty() }
        assertTrue("the swap opened on an empty library ${empty.size} time(s)", empty.isEmpty())
        assertTrue("the picker opened by Swap is a swap", swap.swapping)
        assertEquals(
            "the squat's siblings, in catalog order, and not the squat itself",
            listOf(FRONT, GOBLET),
            swap.swapSiblings.map { it.id },
        )

        log.setPickerVisible(false)
        val closed = log.awaitState { !it.showExercisePicker }
        assertFalse("closing ends the swap", closed.swapping)
        assertTrue("closing drops the swap's siblings", closed.swapSiblings.isEmpty())
    }

    @Test
    fun theSuggestedLiftFollowsTheCoachsCardItsLiftAndItsReason() = runBlocking {
        val log = createLog(seedWorkout().session.id)
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }

        insights.value = cards(lift(ROW, "Rows are due"))
        val row = log.awaitState { it.suggestion?.id == ROW }
        assertEquals("the suggestion's reason is the Row card's", "Rows are due", row.suggestionReason)

        insights.value = cards(lift(ROW, "Rows are overdue"))
        log.awaitState { it.suggestion?.id == ROW && it.suggestionReason == "Rows are overdue" }

        insights.value = cards(lift(BENCH, "Bench is due"))
        val bench = log.awaitState { it.suggestion?.id == BENCH }
        assertEquals("the suggestion's reason is the Bench card's", "Bench is due", bench.suggestionReason)

        insights.value = cards(lift(SQUAT, "Squat today"))
        val present = log.awaitState { it.suggestion == null }
        assertNull("a lift already in the workout is not suggested", present.suggestionReason)
    }

    // --- Red before W2c, green after it --------------------------------------------------------

    @Test
    fun aClosedPickerHoldsNoSearchResults() = runBlocking {
        val log = createLog(seedWorkout().session.id)
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }
        dispatcher.scheduler.advanceUntilIdle()
        val closed = log.uiState.value
        assertFalse("the picker starts closed", closed.showExercisePicker)
        assertEquals(
            "a closed picker held the sorted library",
            emptyList<String>(),
            closed.searchResults.map { it.id },
        )

        log.setPickerVisible(true)
        log.awaitState { it.showExercisePicker && it.searchResults.size == LIBRARY_SIZE }

        log.setPickerVisible(false)
        val reclosed = log.awaitState { !it.showExercisePicker }
        assertEquals(
            "a picker closed again kept its list",
            emptyList<String>(),
            reclosed.searchResults.map { it.id },
        )
    }

    @Test
    fun anUnchangedCardDoesNotReadItsLiftAgain() = runBlocking {
        val log = createLog(seedWorkout().session.id, container = countingLiftWatches())
        log.awaitState { it.loadState == SessionLoadState.FOUND && !it.entryLocked }

        insights.value = cards(lift(ROW, "Rows are due"))
        log.awaitState { it.suggestion?.id == ROW }

        // New insights, as every logged set brings, with the same lift card on top.
        val before = taken.get()
        insights.value = cards(OTHER_CARD, lift(ROW, "Rows are due"))
        awaitTaken(before + 1)
        insights.value = cards(lift(BENCH, "Bench is due"))
        log.awaitState { it.suggestion?.id == BENCH }

        assertEquals("the unchanged Row card began watching the Row in Room again", 1, watches(ROW))
        assertEquals("the Bench card watches the Bench once", 1, watches(BENCH))
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun createLog(sessionId: String, container: AppDependencies = deps): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = container,
        ).also(logs::add)

    /**
     * The graph with an exercise repository whose watches of one lift by id are counted as they
     * begin; the rest is shared. The suggestion watches its lift (so an edit reaches it), and
     * each new watch is a new Room query.
     */
    private fun countingLiftWatches(): AppDependencies = withExerciseDao { real ->
        object : ExerciseDao by real {
            override fun observeById(id: String): Flow<ExerciseEntity?> =
                real.observeById(id).onStart { liftWatches.getOrPut(id) { AtomicInteger() }.incrementAndGet() }
        }
    }

    /** The graph with a catalogue whose every answer waits on [catalogueGate] while it is armed. */
    private fun holdingNewCatalogueReads(): AppDependencies = withExerciseDao { real ->
        object : ExerciseDao by real {
            override fun observeAll(): Flow<List<ExerciseEntity>> =
                real.observeAll().onEach { catalogueGate?.await() }
        }
    }

    private fun withExerciseDao(decorate: (ExerciseDao) -> ExerciseDao): AppDependencies {
        val repository = ExerciseRepository(
            exerciseDao = decorate(deps.database.exerciseDao()),
            routineDao = deps.database.routineDao(),
            workoutDao = deps.database.workoutDao(),
            catalogDao = deps.database.catalogDao(),
            database = deps.database,
        )
        return object : AppDependencies by deps {
            override val exerciseRepository: ExerciseRepository = repository
        }
    }

    private fun watches(id: String): Int = liftWatches[id]?.get() ?: 0

    private suspend fun awaitTaken(count: Int) {
        try {
            withTimeout(TestWaits.FLOW_MS) { while (taken.get() < count) delay(5) }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("The Log never picked up the coach's cards; taken=${taken.get()}", timedOut)
        }
    }

    private fun cards(vararg recommendations: TrainingRecommendation) =
        TrainingInsights(recommendations = recommendations.toList())

    private fun lift(id: String, title: String) = TrainingRecommendation(
        id = "rec-$id",
        kicker = "NEXT",
        title = title,
        reason = "coverage",
        priority = RecommendationPriority.INFO,
        rankScore = 10,
        actionExerciseId = id,
    )

    /** A workout on the squat, after a finished one that trained the Goblet Squat. */
    private suspend fun seedWorkout(): SeededWorkout {
        insertExercise(SQUAT, "Barbell Back Squat", movementKey = "squat")
        insertExercise(FRONT, "Front Squat", movementKey = "squat")
        insertExercise(GOBLET, "Goblet Squat", movementKey = "squat")
        insertExercise(BENCH, "Barbell Bench Press", movementKey = "bench")
        insertExercise(ROW, "Row", movementKey = "row")
        val prior = routine(PRIOR, GOBLET)
        val last = deps.workoutRepository.startRoutine(prior)
        deps.workoutRepository.logSet(
            sessionId = last.id,
            exerciseId = GOBLET,
            weightKg = 24.0,
            reps = 10,
            rpe = null,
            isWarmup = false,
        )
        deps.workoutRepository.finishSession(last.id, notes = "")
        return SeededWorkout(deps.workoutRepository.startRoutine(routine(LOWER, SQUAT)))
    }

    private suspend fun routine(id: String, exerciseId: String) = run {
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = id, name = id, notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        deps.database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-$id-$exerciseId",
                routineId = id,
                exerciseId = exerciseId,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        checkNotNull(deps.routineRepository.getById(id))
    }

    private suspend fun insertExercise(id: String, name: String, movementKey: String) {
        deps.database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = name,
                    muscleGroup = "Legs",
                    notes = "",
                    isCustom = false,
                    loadType = "EXTERNAL",
                    movementKey = movementKey,
                    nameKey = name.lowercase(),
                ),
            ),
        )
    }

    private data class SeededWorkout(val session: WorkoutSession)

    private companion object {
        /** Real catalog ids, so the picker's rank (100, then 110) is in play. */
        const val SQUAT = "ex-barbell-back-squat"
        const val BENCH = "ex-barbell-bench-press"
        const val FRONT = "front-squat"
        const val GOBLET = "goblet-squat"
        const val ROW = "row"
        const val LIBRARY_SIZE = 5
        const val PRIOR = "routine-prior"
        const val LOWER = "routine-lower"
        const val STAMP = 1_700_000_000_000L

        val OTHER_CARD = TrainingRecommendation(
            id = "rec-balance",
            kicker = "BALANCE",
            title = "Push and pull are even",
            reason = "balance",
            priority = RecommendationPriority.INFO,
            rankScore = 20,
        )
    }
}
