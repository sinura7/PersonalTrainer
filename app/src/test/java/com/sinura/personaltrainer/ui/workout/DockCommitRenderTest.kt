package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.InstrumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The commit at the foot of the floor: a verb over what it will write, said to TalkBack as
 * one line, set in its own type and never shorter than 72 dp; it makes no haptic of its own
 * (the set's landing does), says why it waits when it cannot act, lets an open edit save
 * before Finish, and never lets a press begun on one action land on the next. On the real
 * screen it names the next lift under a short verb, writes the set in the lifter's unit and
 * in the lift's own terms (reps alone for a bodyweight lift, reps less the help for an
 * assisted one, never a "no weight" load), and on the last lift finishes the workout while
 * still offering one more set.
 *
 * These were lines of WorkoutDock.kt and ActiveWorkoutScreen.kt read as text (`text =
 * state.verb`, `supporting = state.payload`, `contentDescription = spokenAction`, `textStyle =
 * InstrumentType.commit`, `height = Metrics.commit`, `hapticFeedback = false`,
 * `LogCommitCopy.disabledReason`, `finishAct = … && !state.editing`, `key(action.identity)`,
 * `verb = primaryAction.verb(includeNextName = false)`, `payload = primaryAction.payload(unit
 * = unit, loadClass = loadClass)`, `val showFinish = …`, `if (accepted && action.kind ==
 * FINISH) confirmEnd = true`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class DockCommitRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var view: View

    private val primaries = mutableListOf<WorkoutPrimaryAction>()
    private val logSet = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10, rpe = 8))
    private val next = floorPrimaryAction(kind = WorkoutPrimaryKind.NEXT_EXERCISE, nextName = "Leg Curl")
    private val finish = floorPrimaryAction(kind = WorkoutPrimaryKind.FINISH)
    private var dockState by mutableStateOf(floorDockState(action = logSet))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theCommitSaysItsVerbOverWhatItWillWriteAndSpeaksThemAsOne() {
        showDock(floorDockState(action = logSet))
        val payload = checkNotNull(logSet.payload(unit = FLOOR_UNIT, loadClass = LoadClass.LOADED))
        val commit = compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
        assertEquals(listOf("Log set", payload), commit.mergedTexts())
        assertEquals(listOf("Log set · $payload"), commit.spokenDescriptions())
        // Landscape draws no second line for the next lift, and still says it.
        dockState = floorDockState(action = next, payload = null, spokenPayload = "Leg Curl")
        compose.waitForIdle()
        val advance = compose.onNodeWithTag(WorkoutTestTags.NEXT)
        assertEquals(listOf("Next exercise"), advance.mergedTexts())
        assertEquals(listOf("Next exercise · Leg Curl"), advance.spokenDescriptions())
    }

    @Test
    fun theVerbIsSetInTheCommitsOwnType() {
        showDock(floorDockState(action = logSet))
        val verb = compose.onNode(hasText("Log set") and hasAnyAncestor(hasTestTag(WorkoutTestTags.LOG_SET)), useUnmergedTree = true)
        val style = verb.textLayout().layoutInput.style
        assertEquals(InstrumentType.commit.fontSize, style.fontSize)
        assertNotEquals("the commit's type is louder than a title", InstrumentType.title.fontSize, style.fontSize)
    }

    @Test
    fun theCommitIsNeverShorterThanSeventyTwoDp() {
        showDock(floorDockState(action = logSet, payload = null))
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertHeightIsAtLeast(COMMIT_MIN)
        dockState = floorDockState(action = next, payload = "Leg Curl")
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertHeightIsAtLeast(COMMIT_MIN)
    }

    @Test
    fun aTapOnTheCommitMakesNoHapticOfItsOwn() {
        // Refused here, as a save the lifter is still waiting on would be: nothing landed, so
        // nothing may feel as if it did. The set's own landing fires the commit haptic.
        showDock(floorDockState(action = logSet), accept = false)
        val before = Shadows.shadowOf(view).lastHapticFeedbackPerformed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        assertEquals(1, primaries.size)
        assertEquals(before, Shadows.shadowOf(view).lastHapticFeedbackPerformed())
        assertNotEquals(HapticFeedbackConstants.KEYBOARD_TAP, Shadows.shadowOf(view).lastHapticFeedbackPerformed())
    }

    @Test
    fun aCommitThatCannotActSaysWhyItWaits() {
        val waiting = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10), enabled = false)
        showDock(floorDockState(action = waiting).copy(canLog = false))
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        assertEquals(LogCommitCopy.WAITING_FOR_LIFT, stateOf(WorkoutTestTags.LOG_SET))
        dockState = floorDockState(action = waiting).copy(canLog = false, logging = true)
        compose.waitForIdle()
        assertEquals(LogCommitCopy.LOGGING_WAIT, stateOf(WorkoutTestTags.LOG_SET))
        // The lift is ready, it is only held: no reason is invented.
        dockState = floorDockState(action = waiting).copy(canLog = true)
        compose.waitForIdle()
        assertNull(stateOf(WorkoutTestTags.LOG_SET))
    }

    @Test
    fun anOpenEditSavesBeforeTheWorkoutCanFinish() {
        showDock(floorDockState(action = finish, payload = null, editing = true))
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.CANCEL_EDIT).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun aPressBegunOnOneActionNeverLandsOnTheNext() {
        showDock(floorDockState(action = logSet))
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performTouchInput { down(center) }
        // The set lands under the thumb and the commit turns into Next exercise.
        dockState = floorDockState(action = next, payload = "Leg Curl")
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.NEXT).performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("the lift-off of a press on Log set must not press Next, was $primaries", primaries.isEmpty())
        compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
        assertEquals(listOf(next), primaries)
    }

    @Test
    fun thePortraitCommitNamesTheNextLiftUnderAShortVerb() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(3), withNextLift = true)
        compose.showWorkoutScreen(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        val advance = compose.onNodeWithTag(WorkoutTestTags.NEXT)
        assertEquals(listOf("Next exercise", FLOOR_NEXT_LIFT_NAME), advance.mergedTexts())
        assertEquals(listOf("Next exercise · $FLOOR_NEXT_LIFT_NAME"), advance.spokenDescriptions())
    }

    @Test
    fun theCommitWritesTheSetInTheLiftersUnit() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(1))
        compose.showWorkoutScreen(vm)
        val action = vm.primaryAction.value
        assertEquals(WorkoutPrimaryKind.LOG_SET, action.kind)
        val inPounds = checkNotNull(action.payload(unit = WeightUnit.LBS, loadClass = LoadClass.LOADED))
        assertTrue("was $inPounds", inPounds.contains(" lb"))
        assertEquals(listOf("Log set", inPounds), compose.onNodeWithTag(WorkoutTestTags.LOG_SET).mergedTexts())
    }

    @Test
    fun aBodyweightLiftsCommitCountsRepsAndNeverANoWeightLoad() {
        val vm = openLift(id = "test-pull-up", name = "Pull-up", loadType = LoadType.BODYWEIGHT, targetLbs = null)
        compose.showWorkoutScreen(vm)
        awaitLogSetOf(vm, reps = 10)
        val commit = compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
        assertEquals(listOf("Log set", "10 reps"), commit.mergedTexts())
        assertEquals(listOf("Log set · 10 reps"), commit.spokenDescriptions())
    }

    @Test
    fun anAssistedLiftsCommitCountsRepsLessTheHelpAndNeverTheHelpAsALoad() {
        // 20 lb of help from the machine: the lifter moves their body less that, ten times.
        val vm = openLift(id = "test-assisted-dip", name = "Assisted Dip", loadType = LoadType.ASSISTED, targetLbs = 20.0)
        compose.showWorkoutScreen(vm)
        awaitLogSetOf(vm, reps = 10)
        val commit = compose.onNodeWithTag(WorkoutTestTags.LOG_SET)
        assertEquals(listOf("Log set", "10 reps −20 lb"), commit.mergedTexts())
        assertEquals(listOf("Log set · 10 reps −20 lb"), commit.spokenDescriptions())
    }

    @Test
    fun onTheLastLiftTheCommitFinishesAndStillOffersOneMoreSet() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(3))
        compose.showWorkoutScreen(vm)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.FINISH }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(WorkoutTestTags.DOCK_FINISH).assertIsDisplayed().performClick()
        compose.onNodeWithText(EndWorkoutCopy.TITLE).assertIsDisplayed()
        assertTrue("asking is not ending", vm.uiState.value.session?.finishedAt == null)
    }

    private fun stateOf(tag: String): String? =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun showDock(state: WorkoutDockState, accept: Boolean = true) {
        dockState = state
        val onPrimary: (WorkoutPrimaryAction) -> Boolean = { action -> primaries += action; accept }
        val events = floorDockEvents(onPrimary = onPrimary)
        compose.showFloor {
            view = LocalView.current
            WorkoutDock(state = dockState, events = events)
        }
    }

    /** The commit is Log set with [reps] in the entry, and the screen has drawn it. */
    private fun awaitLogSetOf(vm: ActiveWorkoutViewModel, reps: Int) {
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) {
            vm.primaryAction.value.kind == WorkoutPrimaryKind.LOG_SET && vm.uiState.value.draft.reps == reps && vm.uiState.value.canLog
        }
        compose.waitForIdle()
    }

    /**
     * A live session on one lift of [loadType], planned at 3 × 10 with [targetLbs] (none when
     * null), nothing saved yet. The lift's row goes in first: the fixture inserts its own row of
     * the same id, and the insert ignores a row that is already there.
     */
    private fun openLift(id: String, name: String, loadType: LoadType, targetLbs: Double?): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = id,
                        name = name,
                        muscleGroup = "Back",
                        notes = "",
                        isCustom = false,
                        loadType = loadType.name,
                        nameKey = name.lowercase(),
                    ),
                ),
            )
            seedTestWorkout(
                deps = deps,
                exerciseId = id,
                exerciseName = name,
                routineName = "Upper A",
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = targetLbs?.let(WeightConverter::lbsToKg),
                restSeconds = 90,
            ).session.id
        }
        return floorViewModel(deps = deps, sessionId = sessionId).also(viewModels::add)
    }

    private companion object {
        /** The owner's floor rule, as a number: a thumb-and-chalk target, not a token's name. */
        val COMMIT_MIN = 72.dp
    }
}
