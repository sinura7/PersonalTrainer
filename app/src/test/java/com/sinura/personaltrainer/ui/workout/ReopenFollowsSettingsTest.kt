package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.FinishedWorkingSetRow
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.workout.SavedStateWorkoutDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
 * A workout left and reopened after the unit or the week's mark changed (W2e): an entry nobody
 * touched, on a lift with no working set logged today, takes the suggestion the reopened Log reads
 * (owner decision of 25 September 2026); a typed number, and the entry after a logged set, stay.
 *
 * What the phone does: Back on the Log, then Settings (or Body), then the live bar. That builds a
 * new ActiveWorkoutViewModel on a fresh SavedStateHandle, and the draft cache it reads is the
 * process's own, still holding what the last screen flushed. After a process death Android
 * rebuilds the screen on the same handle with the cache gone. Back is
 * [ActiveWorkoutViewModel.persistDraftForExit] then the ViewModel's end, as ReopenMidPrefillTest
 * has it; the process, and its cache, live on unless a test clears it.
 *
 * The rule needs no stamp on the saved entry: an untouched entry with no working set can hold only
 * what a load filled in, so any recovered one takes the suggestion once its lift reads it. That
 * includes an entry filled with the routine's weight because the suggestion could not be read the
 * first time, which one test here pins.
 *
 * Squat 3 × 5 at 100 kg after a finished 100 kg × 5 (102.5 kg in kilograms, 225.5 lb = 102.3 kg in
 * pounds, last time's 100 kg in a lighter week); Bench 3 × 5 at 60 kg, never trained.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReopenFollowsSettingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.KG) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        if (::deps.isInitialized) deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    // --- Red before W2e -------------------------------------------------------------------------

    @Test
    fun anUntouchedEntryReopenedInPoundsTakesThePoundStep() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        assertFalse("precondition: nobody touched the entry", before.awaitSettledOn(SQUAT, 102.5).draftDirty)
        leave(before)

        switchToPounds()
        val reopened = viewModel(sessionId)
        val state = reopened.awaitLog("the reopened Squat's untouched entry on the pound suggestion, 102.3 kg") {
            it.onSquat() && it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
        assertEquals("the entry reads the pound suggestion", "225.5", displayed(state.draft.weightKg, WeightUnit.LBS))
        assertEquals(
            "the followed entry is the one the rest page and the next reopen read",
            102.3,
            checkNotNull(deps.workoutDraftCache.getLift(sessionId, SQUAT)).weightKg,
            0.0,
        )
        val call = checkNotNull(reopened.awaitCall("the reopened Squat's first-set call, 102.3 kg") { it?.nextWeightKg == 102.3 })
        assertTrue(
            "the entry equals the Next card, so the card reads as applied",
            call.isApplied(state.draft.weightKg, state.draft.reps, state.draft.rpe, WeightUnit.LBS),
        )
    }

    @Test
    fun anUntouchedEntryReopenedInALighterWeekTakesLastTimesWeight() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        before.awaitSettledOn(SQUAT, 102.5)
        leave(before)

        markThisWeekLighter(sessionId)
        val reopened = viewModel(sessionId)
        val state = reopened.awaitLog("the reopened Squat's untouched entry at last time's 100 kg, in a lighter week") {
            it.onSquat() && it.hint?.lighterHold == true && it.draft.weightKg == 100.0
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
    }

    @Test
    fun anUntouchedEntryRebuiltAfterAProcessDeathTakesThePoundStep() = runBlocking {
        val sessionId = seedWorkout()
        val handle = SavedStateHandle(mapOf("sessionId" to sessionId))
        val before = viewModel(sessionId, handle = handle)
        before.awaitSettledOn(SQUAT, 102.5)
        end(before)
        deps.workoutDraftCache.clearAll()

        switchToPounds()
        val rebuilt = viewModel(sessionId, handle = handle)
        val state = rebuilt.awaitLog("the rebuilt Squat's untouched entry on the pound suggestion, 102.3 kg") {
            it.onSquat() && it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
        assertEquals(
            "saved state holds the followed entry, for the next process death",
            102.3,
            checkNotNull(SavedStateWorkoutDraft(handle).readLift(sessionId, SQUAT)).weightKg,
            0.0,
        )
    }

    @Test
    fun aLiftFollowsWhenItIsFirstOpenedAfterTheReopen() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        before.awaitSettledOn(SQUAT, 102.5)
        before.selectExercise(BENCH)
        before.awaitSettledOn(BENCH, 60.0)
        leave(before)

        switchToPounds()
        val reopened = viewModel(sessionId)
        val bench = reopened.awaitSettledOn(BENCH, 60.0)
        assertNull("precondition: the Bench, never trained, has no hint", bench.hint)
        reopened.selectExercise(SQUAT)
        val state = reopened.awaitLog("the Squat, opened after the reopen, on the pound suggestion, 102.3 kg") {
            it.onSquat() && it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
    }

    @Test
    fun anEntryFilledWithTheRoutinesWeightWhenTheSuggestionCouldNotBeReadTakesItOnReopen() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId, container = hintUnreadable())
        before.awaitLog("the Squat degraded to the routine's 100 kg, its hint unreadable") {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == SQUAT &&
                it.liftReadiness == LiftEntryReadiness.DEGRADED && it.draft.weightKg == 100.0 && !it.entryLocked
        }
        leave(before)

        // No setting changes: the suggestion reads now, where it could not before.
        val reopened = viewModel(sessionId)
        val state = reopened.awaitLog("the reopened Squat's untouched entry on the suggestion it reads now, 102.5 kg") {
            it.onSquat() && it.hint?.suggestedWeightKg == 102.5 && it.draft.weightKg == 102.5
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
        assertFalse("the suggestion is no longer missing", state.suggestionUnavailable)
    }

    @Test
    fun aReopenedLiftFollowsALiveUnitChange() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        before.awaitSettledOn(SQUAT, 102.5)
        leave(before)
        val reopened = viewModel(sessionId)
        reopened.awaitSettledOn(SQUAT, 102.5)

        switchToPounds()
        val state = reopened.awaitLog("the reopened Squat's untouched entry following a live switch to pounds, 102.3 kg") {
            it.onSquat() && it.hint?.suggestedWeightKg == 102.3 && it.draft.weightKg == 102.3
        }
        assertFalse("a number the app moved is still untouched", state.draftDirty)
    }

    // --- Guards: green before and after W2e ----------------------------------------------------

    @Test
    fun aWeightTypedBeforeLeavingStaysWhenTheWorkoutIsReopenedInPounds() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        before.awaitSettledOn(SQUAT, 102.5)
        before.setWeight(105.0)
        before.awaitLog("the typed 105 kg") { it.draft.weightKg == 105.0 && it.draftDirty }
        leave(before)

        switchToPounds()
        val reopened = viewModel(sessionId)
        reopened.awaitLog("the reopened Squat with its pound hint read") { it.onSquat() && it.hint?.suggestedWeightKg == 102.3 }
        reopened.neverShows("the typed entry moved") { it.draft.weightKg != 105.0 || !it.draftDirty }
        val after = reopened.awaitState { !it.entryLocked }
        assertEquals("a typed weight stays", 105.0, after.draft.weightKg, 0.0)
        assertTrue("the entry is still typed", after.draftDirty)
    }

    @Test
    fun anEntryAfterALoggedSetStaysWhenTheWorkoutIsReopenedInPounds() = runBlocking {
        val sessionId = seedWorkout()
        val before = viewModel(sessionId)
        before.awaitSettledOn(SQUAT, 102.5)
        before.logSetAndSettle(repository = deps.workoutRepository, scheduler = dispatcher.scheduler)
        val logged = before.awaitLog("the entry after the logged 102.5 kg") { state ->
            state.session?.sets?.any { it.exerciseId == SQUAT && !it.isWarmup } == true &&
                state.draft.weightKg == 102.5 && !state.entryLocked
        }
        assertFalse("precondition: the logged set's entry was never touched", logged.draftDirty)
        leave(before)

        switchToPounds()
        val reopened = viewModel(sessionId)
        reopened.awaitLog("the reopened Squat with its pound hint read") { it.onSquat() && it.hint?.suggestedWeightKg == 102.3 }
        reopened.neverShows("the entry after a logged set moved") { it.draft.weightKg != 102.5 || it.draftDirty }
        val after = reopened.awaitState { !it.entryLocked }
        assertEquals("the set just done stays in the entry", 102.5, after.draft.weightKg, 0.0)
        assertFalse("the entry is still untouched", after.draftDirty)
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** Back: flush the draft and the notes, then the screen ends. */
    private suspend fun leave(vm: ActiveWorkoutViewModel) {
        vm.persistDraftForExit()
        end(vm)
    }

    /** The ViewModel ends. The process, and its draft cache, live on. */
    private suspend fun end(vm: ActiveWorkoutViewModel) {
        vm.clearAndJoinForTest()
        viewModels.remove(vm)
    }

    /**
     * A new screen for the workout, as the live bar builds it: a fresh handle, the same cache.
     * Given [handle], the screen Android rebuilds after a process death instead.
     */
    private fun viewModel(
        sessionId: String,
        handle: SavedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        container: AppDependencies = deps,
    ) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = handle,
        container = container,
    ).also(viewModels::add)

    /** The graph with a DAO that fails every read of a lift's hint; every other read, and the cache, shared. */
    private fun hintUnreadable(): AppDependencies {
        val real = deps.database.workoutDao()
        val repository = WorkoutRepository(
            deps.database,
            object : WorkoutDao by real {
                override suspend fun finishedWorkingSetsForExercises(
                    exerciseIds: List<String>,
                ): List<FinishedWorkingSetRow> {
                    val hintRead = Throwable().stackTrace.any { it.methodName.startsWith("progressionFor") }
                    if (hintRead) error("Injected: the hint could not be read")
                    return real.finishedWorkingSetsForExercises(exerciseIds)
                }
            },
        )
        return object : AppDependencies by deps {
            override val workoutRepository: WorkoutRepository = repository
        }
    }

    /** Switched in Settings, or pulled in by sync, while the workout is closed. */
    private suspend fun switchToPounds() {
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        deps.preferencesRepository.weightUnit.awaitFirst { it == WeightUnit.LBS }
    }

    /** "Mark this week lighter" on Body, while the workout is closed. */
    private suspend fun markThisWeekLighter(sessionId: String) {
        val thisWeek = ProgressionHintLoader(deps, sessionId).thisWeekStart()
        deps.preferencesRepository.setLighterWeekStartEpochDay(thisWeek)
        deps.preferencesRepository.lighterWeekStartEpochDay.awaitFirst { it == thisWeek }
    }

    /** On the Squat, able to log and not locked. */
    private fun ActiveWorkoutUiState.onSquat(): Boolean =
        loadState == SessionLoadState.FOUND && selectedExerciseId == SQUAT && liftReadiness.allowsCommit() && !entryLocked

    /** Selected, prefilled with [weightKg], ready to log and not locked. */
    private suspend fun ActiveWorkoutViewModel.awaitSettledOn(exerciseId: String, weightKg: Double) =
        awaitLog("the Log on $exerciseId, settled at $weightKg kg") {
            it.loadState == SessionLoadState.FOUND && it.selectedExerciseId == exerciseId &&
                it.draft.weightKg == weightKg && it.liftReadiness.allowsCommit() && !it.entryLocked
        }

    /** The Log's state once it is the one [what] names, or a failure that says what it showed. */
    private suspend fun ActiveWorkoutViewModel.awaitLog(
        what: String,
        predicate: (ActiveWorkoutUiState) -> Boolean,
    ): ActiveWorkoutUiState = withTimeoutOrNull(TestWaits.FLOW_MS) { uiState.first(predicate) }
        ?: throw AssertionError("Never saw $what; the Log showed ${describe(uiState.value)}")

    /** Fails if the Log shows [predicate] within [NEVER_MS]. */
    private suspend fun ActiveWorkoutViewModel.neverShows(what: String, predicate: (ActiveWorkoutUiState) -> Boolean) {
        val shown = withTimeoutOrNull(NEVER_MS) { uiState.first(predicate) }
        assertNull("$what: the Log showed ${shown?.let(::describe)}", shown)
    }

    /** The Log's coach call once it is the one [what] names, waited for with a ceiling. */
    private suspend fun ActiveWorkoutViewModel.awaitCall(
        what: String,
        predicate: (SetMicroRec?) -> Boolean,
    ): SetMicroRec? {
        var matched: SetMicroRec? = null
        withTimeoutOrNull(TestWaits.FLOW_MS) { matched = microRec.first(predicate); true }
            ?: throw AssertionError("Never saw $what; the Log's call was ${microRec.value}")
        return matched
    }

    private fun displayed(weightKg: Double, unit: WeightUnit): String =
        WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(weightKg, unit))

    private fun describe(state: ActiveWorkoutUiState): String =
        "lift=${state.selectedExerciseId}, entry=${state.draft.weightKg} kg × ${state.draft.reps} " +
            "typed=${state.draftDirty}, hint=${state.hint?.let { "${it.suggestedWeightKg} kg lighter=${it.lighterHold}" }}, " +
            "readiness=${state.liftReadiness}, unavailable=${state.suggestionUnavailable}, " +
            "locked=${state.entryLocked}, load=${state.loadState}"

    /** Squat 3 × 5 at 100 kg after a finished 100 kg × 5, then Bench 3 × 5 at 60 kg, never trained. */
    private suspend fun seedWorkout(): String {
        deps.database.routineDao().upsertRoutine(
            RoutineEntity(id = ROUTINE, name = "Full body", notes = "", createdAt = STAMP, updatedAt = STAMP),
        )
        listOf(SQUAT to 100.0, BENCH to 60.0).forEachIndexed { order, (id, targetKg) ->
            deps.database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = id,
                        name = id.replaceFirstChar { it.uppercase() },
                        muscleGroup = "Legs",
                        notes = "",
                        isCustom = false,
                        loadType = "EXTERNAL",
                        nameKey = id,
                    ),
                ),
            )
            deps.database.routineDao().upsertRoutineExercise(
                RoutineExerciseEntity(
                    id = "re-$id",
                    routineId = ROUTINE,
                    exerciseId = id,
                    sortOrder = order,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = targetKg,
                    restSeconds = 90,
                ),
            )
        }
        val routine = checkNotNull(deps.routineRepository.getById(ROUTINE)) { "the routine is not stored" }
        val prior = deps.workoutRepository.startRoutine(routine)
        deps.workoutRepository.logSet(
            sessionId = prior.id,
            exerciseId = SQUAT,
            weightKg = 100.0,
            reps = 5,
            rpe = null,
            isWarmup = false,
        )
        deps.workoutRepository.finishSession(prior.id, notes = "")
        return deps.workoutRepository.startRoutine(routine).id
    }

    private companion object {
        /** How long a guard watches for what it guards to move, after the cause has happened. */
        const val NEVER_MS = 2_000L
        const val SQUAT = "squat"
        const val BENCH = "bench"
        const val ROUTINE = "routine-full"
        const val STAMP = 1_700_000_000_000L
    }
}
