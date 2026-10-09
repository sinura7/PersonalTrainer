package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.history.FilledLiftCardTags
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryViewModel
import com.sinura.personaltrainer.ui.theme.Motion
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One connected synthetic session through real Compose screens, ViewModels and Room.
 *
 * Start, numeric input, effort, Log, lift switching, correction, Finish, Summary Done,
 * and History's session row are rendered controls. Only routine setup, observation of
 * durable state, and navigation between screen callbacks belong to this test.
 *
 * This is deliberately a screen host, not AppNav: after Summary Done the test mounts
 * History itself. It therefore does not certify Home/tab routing or back-stack removal.
 * The existing in-memory rest gateway verifies the post-save command and timer identity,
 * not Android service, lock-screen, notification or elapsed-time delivery. This is not
 * owner usability evidence or a substitute for the constrained-layout matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ConnectedWorkoutJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<ViewModel>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var active: ActiveWorkoutViewModel
    private lateinit var summary: WorkoutSummaryViewModel
    private lateinit var history: HistoryViewModel
    private lateinit var detail: SessionDetailViewModel
    private var destination by mutableStateOf(Destination.START)
    private val startedIds = mutableListOf<String>()
    private val finishedIds = mutableListOf<String>()
    private val openedHistoryIds = mutableListOf<String>()
    private var summaryDone = false
    private val artifactRunId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
            deps.preferencesRepository.markRestBatteryHintShown()
        }
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
    fun manualResultRestSwitchCorrectionAndFinishAgreeInSummaryHistoryAndDetail() {
        val routineId = seedRoutine()
        showJourney()
        compose.awaitThat("the synthetic routine is available", { destination }) {
            compose.onAllNodes(hasText(ROUTINE) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(ROUTINE) and hasClickAction()).performScrollTo().performClick()
        compose.awaitThat("Start opens one live workout", { startedIds }) { startedIds.size == 1 }
        val sessionId = startedIds.single()
        awaitEntry("the first lift is ready") { it.selectedExerciseId == FLOOR_LIFT_ID }
        assertEquals(routineId, session().routineId)
        assertEquals(sessionId, runBlocking { deps.workoutRepository.getInProgress()?.id })
        assertTrue(session().sets.isEmpty())

        // The task's actual result differs from both prescribed values. No Apply or direct
        // ViewModel entry mutation can accidentally make this a prefill-only success.
        assertEquals(FLOOR_KG70, active.uiState.value.draft.weightKg, EPSILON)
        assertEquals(10, active.uiState.value.draft.reps)
        compose.awaitThat("the first-set recommendation is ready", { active.microRec.value }) {
            active.microRec.value != null
        }
        val advice = checkNotNull(active.microRec.value)
        assertTrue("the manual load differs from advice", abs(advice.nextWeightKg - kg(82.5)) > EPSILON)
        assertTrue("the manual repetitions differ from advice", advice.nextReps != 12)
        enterNumbers(weightLb = "82.5", reps = "12")
        awaitEntry("manual numbers reach the draft") { matches(it.draft, 82.5, 12) }
        assertNull(active.uiState.value.draft.rpe)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        chooseEffort(8)
        awaitEntry("effort preserves the typed result") { matches(it.draft, 82.5, 12) && it.draft.rpe == 8 }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsEnabled().performClick()
        awaitEntry("exactly one set is saved and the next effort is empty") {
            it.session?.sets?.size == 1 && it.draft.rpe == null
        }
        val original = session().sets.single()
        assertSet(original, sessionId, original.id, 82.5, 12, 8)
        assertEquals(original.id, active.logReceipt.value?.setId)

        // Advance the ViewModel's existing receipt-settle delay, without starting rest
        // manually. The post-save path must issue the command for this same session.
        compose.runOnIdle {
            dispatcher.scheduler.advanceTimeBy(Motion.ROW_SETTLE_MS.toLong() + 1)
            dispatcher.scheduler.runCurrent()
        }
        compose.awaitThat("the saved set starts rest", { deps.restTimerStore.current() }) {
            deps.restTimerStore.current().running
        }
        val rest = deps.restTimerStore.current()
        assertEquals(sessionId, rest.sessionId)
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()

        // Give the next set a distinct unsaved draft, then use the real switcher twice.
        // Its restoration must not substitute the last saved values or the other lift.
        enterNumbers(weightLb = "87.5", reps = "9")
        chooseEffort(7)
        switchTo(FLOOR_NEXT_LIFT_ID)
        switchTo(FLOOR_LIFT_ID)
        awaitEntry("the departing draft returns intact") { matches(it.draft, 87.5, 9) && it.draft.rpe == 7 }
        assertEquals("switching must not add work", listOf(original.id), session().sets.map { it.id })
        assertEquals(rest.timerId, deps.restTimerStore.current().timerId)
        assertTrue(deps.restTimerStore.current().running)

        // Correction starts from the saved chip, not from editSet called by the test.
        scrollFloorTo(WorkoutTestTags.setChip(original.id))
        captureFloorControl("saved-set-before-scroll", WorkoutTestTags.setChip(original.id))
        revealFloorControlAboveTempo(WorkoutTestTags.setChip(original.id))
        captureFloorControl("saved-set-after-scroll", WorkoutTestTags.setChip(original.id))
        compose.onNodeWithTag(WorkoutTestTags.setChip(original.id)).performClick()
        compose.onNodeWithText(SetRowCopy.revise(SetOrdinalCopy.working(1, TARGET_SETS))).performClick()
        awaitEntry("the saved row is open for correction") { it.editingSetId == original.id }
        enterNumbers(weightLb = "80", reps = "11")
        chooseEffort(9)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsEnabled()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitEntry("correction updates the same set and leaves correction mode") {
            it.editingSetId == null && it.session?.sets?.singleOrNull()?.let { set ->
                set.id == original.id && abs(set.weightKg - kg(80.0)) < EPSILON && set.reps == 11 && set.rpe == 9
            } == true
        }
        val corrected = session().sets.single()
        assertSet(corrected, sessionId, original.id, 80.0, 11, 9)
        assertEquals(original.completedAt, corrected.completedAt)
        assertEquals(original.setNumber, corrected.setNumber)

        // Finish the partial plan through its real confirmation. An unlogged draft and
        // the untouched second exercise must not turn into extra work in the receipt.
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsEnabled().performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        compose.onNodeWithText("Save as is").performClick()
        compose.awaitThat("Finish opens this session's summary", { finishedIds }) { finishedIds.size == 1 }
        assertEquals(listOf(sessionId), finishedIds)
        compose.awaitThat("Summary reads the finished row", { summary.uiState.value }) {
            !summary.uiState.value.isLoading
        }
        val finished = session()
        assertNotNull(finished.finishedAt)
        assertSet(finished.sets.single(), sessionId, original.id, 80.0, 11, 9)
        assertNull(runBlocking { deps.workoutRepository.getInProgress() })
        assertFalse(deps.restTimerStore.current().running)
        val summaryState = summary.uiState.value
        assertTrue(summaryState.savedConfirmed)
        assertFalse(summaryState.failed)
        assertFalse(summaryState.missing)
        assertEquals(sessionId, summaryState.sessionId)
        assertEquals(sessionId, summaryState.summary.sessionId)
        assertEquals(1, summaryState.summary.workingSets)
        assertEquals(kg(80.0) * 11, summaryState.summary.volumeKg, EPSILON)
        val highlight = summaryState.summary.highlights.single()
        assertEquals(FLOOR_LIFT_ID, highlight.exerciseId)
        assertEquals(kg(80.0), checkNotNull(highlight.topSet).weightKg, EPSILON)
        assertEquals(11, checkNotNull(highlight.topSet).reps)
        compose.onNodeWithText("WORKOUT COMPLETE").assertIsDisplayed()
        val setLine = SetCopy.setLine(kg(80.0), 11, LoadClass.LOADED, WeightUnit.LBS)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Top set $setLine"))
        compose.onNodeWithText("Top set $setLine").assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.DONE).performClick()
        assertTrue("the rendered Done control invokes its callback", summaryDone)

        // AppNav returns Done to Home. This host mounts History explicitly after observing
        // that callback; it makes no assertion about the app's tabs or navigation stack.
        compose.runOnIdle {
            history = rememberModel(HistoryViewModel(application(), deps))
            destination = Destination.HISTORY
        }
        // Synchronize the new composition before inspecting its WhileSubscribed state.
        // A callback changing destination does not itself mount the screen's collector.
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.awaitThat("History contains the same finished session", { history.uiState.value }) {
            !history.uiState.value.isLoading && history.uiState.value.summaries.any { it.id == sessionId }
        }
        val historical = history.uiState.value.summaries.single { it.id == sessionId }
        assertEquals(1, historical.workingSets)
        assertEquals(kg(80.0) * 11, historical.volumeKg, EPSILON)
        assertEquals(finished.finishedAt, historical.finishedAt)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(SessionLogTags.ROW))
        compose.onNodeWithTag(SessionLogTags.ROW).assertIsDisplayed().performClick()
        compose.awaitThat("History opens the same session detail", { openedHistoryIds }) { openedHistoryIds.size == 1 }
        assertEquals(listOf(sessionId), openedHistoryIds)
        compose.onNodeWithTag(SessionDetailTestTags.BACK).assertIsDisplayed()
        compose.awaitThat("Detail reads the corrected set", { detail.uiState.value }) {
            !detail.uiState.value.isLoading && detail.uiState.value.session?.sets?.singleOrNull()?.id == original.id
        }
        val detailSession = checkNotNull(detail.uiState.value.session)
        assertEquals(sessionId, detailSession.id)
        assertSet(detailSession.sets.single(), sessionId, original.id, 80.0, 11, 9)
        val tableRow = SetTableLine.fromLog(corrected, LoadClass.LOADED, WeightUnit.LBS)
        val rowDescription = "Recorded working ${tableRow.extras}, ${tableRow.line}"
        val recordedRow = hasContentDescription(rowDescription) and
            hasAnyAncestor(hasTestTag(FilledLiftCardTags.setRow(corrected.id)))
        compose.onNodeWithTag(SessionDetailTestTags.CONTENT).performScrollToNode(recordedRow)
        compose.onNode(recordedRow).assertIsDisplayed()
        compose.onNodeWithTag(SessionDetailTestTags.BACK).performClick()
        compose.awaitThat("detail returns to History", { destination }) { destination == Destination.HISTORY }
        assertNull(runBlocking { deps.workoutRepository.getInProgress() })
        val savedHistory = runBlocking {
            withTimeout(FLOOR_WAIT_MS) { deps.workoutRepository.observeHistory().first() }
        }
        assertEquals(listOf(sessionId), savedHistory.map { it.id })
        assertSet(savedHistory.single().sets.single(), sessionId, original.id, 80.0, 11, 9)
    }

    private fun showJourney() {
        val start = rememberModel(StartOptionsViewModel(application(), deps))
        compose.showFloor {
            Box(Modifier.width(360.dp).height(800.dp)) {
                when (destination) {
                    Destination.START -> StartOptionsSheet(
                        onDismiss = {},
                        onWorkoutStarted = { id ->
                            startedIds += id
                            active = rememberModel(floorViewModel(deps, id))
                            destination = Destination.WORKOUT
                        },
                        viewModel = start,
                    )
                    Destination.WORKOUT -> ActiveWorkoutScreen(
                        onExit = { error("The journey unexpectedly exited its live workout") },
                        onFinished = { id ->
                            finishedIds += id
                            summary = rememberModel(WorkoutSummaryViewModel(application(), sessionHandle(id), deps))
                            destination = Destination.SUMMARY
                        },
                        viewModel = active,
                        restNotificationsEnabledOverride = true,
                    )
                    Destination.SUMMARY -> WorkoutSummaryScreen(
                        onDone = { summaryDone = true },
                        onOpenSession = { error("This journey reaches detail through History") },
                        viewModel = summary,
                    )
                    Destination.HISTORY -> HistoryScreen(
                        onOpenSession = { id ->
                            openedHistoryIds += id
                            detail = rememberModel(SessionDetailViewModel(application(), sessionHandle(id), deps))
                            destination = Destination.DETAIL
                        },
                        onOpenExercise = {},
                        onOpenActiveSession = { error("Finished history unexpectedly opened a live session") },
                        viewModel = history,
                    )
                    Destination.DETAIL -> SessionDetailScreen(
                        onBack = { destination = Destination.HISTORY },
                        onOpenExercise = {},
                        onOpenActiveSession = { error("Finished detail unexpectedly opened a live session") },
                        viewModel = detail,
                    )
                }
            }
        }
    }

    private fun seedRoutine(): String = runBlocking {
        val first = insertTestExercise(deps, FLOOR_LIFT_ID, "Leg Extension", muscleGroup = "Quads")
        val second = insertTestExercise(deps, FLOOR_NEXT_LIFT_ID, FLOOR_NEXT_LIFT_NAME, muscleGroup = "Hamstrings")
        val routine = deps.routineRepository.create(ROUTINE)
        deps.routineRepository.addExercise(routine.id, first, TARGET_SETS, 10, FLOOR_KG70, 120)
        deps.routineRepository.addExercise(routine.id, second, TARGET_SETS, 8, kg(90.0), 90)
        routine.id
    }

    private fun enterNumbers(weightLb: String, reps: String) {
        scrollFloorTo(WorkoutTestTags.WEIGHT_STEPPER)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER), weightLb)
        scrollFloorTo(WorkoutTestTags.REPS_STEPPER)
        compose.withKeypad(compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER), reps)
    }

    private fun chooseEffort(value: Int) {
        scrollFloorTo(WorkoutTestTags.rpeChoice(value))
        captureFloorControl("rpe-$value-before-tap", WorkoutTestTags.rpeChoice(value))
        revealFloorControlAboveTempo(WorkoutTestTags.rpeChoice(value))
        captureFloorControl("rpe-$value-after-scroll", WorkoutTestTags.rpeChoice(value))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(value)).assertIsDisplayed().assertIsEnabled().performClick()
        captureFloorControl("rpe-$value-after-tap", WorkoutTestTags.rpeChoice(value))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(value)).assertIsSelected()
    }

    /** Keep real touches on their intended floor controls, above the pinned coach. */
    private fun revealFloorControlAboveTempo(tag: String) {
        for (attempt in 0..3) {
            val target = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            val bounds = target.boundsInRoot
            val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
            val tempo = compose.onAllNodes(
                hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
                    !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT)),
            ).fetchSemanticsNodes()
                .singleOrNull()?.boundsInRoot
            val safeBottom = minOf(content.bottom, tempo?.top ?: content.bottom)
            val fullyVisible = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
                bounds.top >= content.top && bounds.bottom <= safeBottom - 1f
            println("JVM_CONNECTED_REACHABILITY run=$artifactRunId tag=$tag attempt=$attempt bounds=$bounds " +
                "size=${target.size} content=$content tempo=$tempo fullyVisible=$fullyVisible")
            if (fullyVisible) return
            check(attempt < 3) { "$tag remains clipped or behind Tempo after three real content swipes" }
            val safeHeight = safeBottom - content.top
            check(safeHeight > target.size.height + 48f) { "No safe swipe area above Tempo for $tag" }
            compose.onNodeWithTag(WorkoutTestTags.CONTENT).performTouchInput {
                swipeUp(startY = safeHeight * 0.8f, endY = safeHeight * 0.3f, durationMillis = 600)
            }
            compose.waitForIdle()
        }
    }

    private fun captureFloorControl(state: String, targetTag: String) {
        for (tag in listOf(targetTag, WorkoutTestTags.TEMPO_COACH_CARD, WorkoutTestTags.CONTENT, WorkoutTestTags.LOG_SET)) {
            val nodes = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
            println("JVM_CONNECTED_TOUCH run=$artifactRunId state=$state tag=$tag " +
                "nodes=${nodes.map { "bounds=${it.boundsInRoot}, window=${it.boundsInWindow}, size=${it.size}" }}")
        }
        println("JVM_CONNECTED_TOUCH run=$artifactRunId state=$state draft=${active.uiState.value.draft}")
        val directory = File("build/screen-renders/connected-workout-journey-touch/$artifactRunId")
        check(directory.exists() || directory.mkdirs()) { "Cannot create touch evidence directory $directory" }
        val frame = compose.drawWindow()
        try {
            File(directory, "$state.png").outputStream().use { output ->
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot write $state frame" }
            }
        } finally {
            frame.recycle()
        }
    }

    private fun switchTo(exerciseId: String) {
        scrollFloorTo(WorkoutTestTags.LIFT_SWITCH)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(exerciseId)).performClick()
        awaitEntry("lift $exerciseId is selected") { it.selectedExerciseId == exerciseId }
    }

    private fun scrollFloorTo(tag: String) {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun awaitEntry(what: String, condition: (ActiveWorkoutUiState) -> Boolean) {
        compose.awaitThat(what, active.uiState::value) {
            FLOOR_LIFT_READY(active.uiState.value) && condition(active.uiState.value)
        }
        compose.waitForIdle()
    }

    private fun session(): WorkoutSession = checkNotNull(runBlocking {
        deps.workoutRepository.getSession(startedIds.single())
    })

    private fun assertSet(set: SetLog, sessionId: String, setId: String, weightLb: Double, reps: Int, effort: Int) {
        assertEquals(sessionId, set.sessionId)
        assertEquals(setId, set.id)
        assertEquals(FLOOR_LIFT_ID, set.exerciseId)
        assertEquals(kg(weightLb), set.weightKg, EPSILON)
        assertEquals(reps, set.reps)
        assertEquals(effort, set.rpe)
        assertFalse(set.isWarmup)
    }

    private fun matches(draft: ActiveExerciseDraft, weightLb: Double, reps: Int) =
        abs(draft.weightKg - kg(weightLb)) < EPSILON && draft.reps == reps

    private fun <T : ViewModel> rememberModel(model: T): T = model.also(viewModels::add)
    private fun application(): Application = ApplicationProvider.getApplicationContext()
    private fun sessionHandle(id: String) = SavedStateHandle(mapOf("sessionId" to id))
    // D15's existing typed-entry contract stores kilograms to a tenth. Keep a tight
    // comparison to that canonical value rather than accepting arbitrary conversion drift.
    private fun kg(lb: Double) = WeightConverter.toKg(lb, WeightUnit.LBS)

    private enum class Destination { START, WORKOUT, SUMMARY, HISTORY, DETAIL }

    private companion object {
        const val ROUTINE = "Connected journey lower"
        const val TARGET_SETS = 3
        const val EPSILON = 0.0001
    }
}
