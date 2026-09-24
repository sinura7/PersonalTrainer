package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * The floor's rest, effort and coach wiring, proved by tapping it: ActiveWorkoutScreen
 * through the real ViewModel, Room and rest timer, as FloorScreenWiringRenderTest composes
 * it. Each wire is a tap and the state it reaches: Start rest runs the clock, the idle
 * card's sheet names the next rest, ±15 and Skip reach the running rest, Time set runs the
 * set stopwatch, an effort chip writes the draft, Apply copies the coach's call into it.
 *
 * These were lines of ActiveWorkoutScreen.kt and WorkoutDock.kt (`onStartRest =
 * viewModel::startSelectedRest`, `onSelectRestDuration = viewModel::selectRestDuration`,
 * `onNudgeRest = viewModel::nudgeRest`, `onRpe = viewModel::setRpe`, `recommendedRpe =
 * microRec?.nextRpe`, `onApply = viewModel::applyMicroRec`, `compact = coachCompact`,
 * `BackHandler(…) { keepAndExit() }`). A renamed reference failed those lines while a broken
 * tap passed; W1b reworded the rest controls and wired the coach goal against these taps.
 *
 * The phone check of 12 September rides along as behaviour, beside its source bans: the
 * header's X and system Back leave with the session kept and no popup, Finish owns the end,
 * and no idle "Start next" is offered anywhere on the floor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FloorRestAndCoachWiringRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private var exits = 0
    private val restPages = mutableListOf<String>()

    /** When set, a set's row waits here before it is written: a save the lifter is waiting on. */
    private var insertGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler()))
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            workoutDaoDecorator = { real -> GatedInserts(real) },
        )
        runBlocking { deps.preferencesRepository.setWeightUnit(WeightUnit.LBS) }
    }

    @After
    fun tearDown() {
        insertGate?.complete(Unit)
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
        viewModels.clear()
        deps.restTimerController.stop()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun startRestOnTheIdleCardRunsTheRestClockAndNothingOffersStartNext() {
        batteryRuleAlreadyRead()
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        assertNoStartNext()
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.running }
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
    }

    @Test
    fun aRunningRestIsTheCurrentLiftsAloneInTheSwitcher() {
        // The switcher's rows through the real screen: the current lift's rest is the one
        // running, and says so; every other lift names its own planned rest.
        batteryRuleAlreadyRead()
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1), withNextLift = true)
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        compose.awaitThat(what = "the rest runs", now = vm.restTimerState::value) { vm.restTimerState.value.running }
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCHER).assertIsDisplayed()
        val current = compose.onNodeWithTag(WorkoutTestTags.liftRest(FLOOR_LIFT_ID), useUnmergedTree = true).mergedTexts().single()
        assertTrue("the current lift's rest is running, was \"$current\"", current.startsWith("Rest remaining: "))
        val other = compose.onNodeWithTag(WorkoutTestTags.liftRest(FLOOR_NEXT_LIFT_ID), useUnmergedTree = true).mergedTexts().single()
        assertEquals("the next lift names its planned rest", "Rest: ${RestTimer.formatClock(NEXT_LIFT_REST_S)}", other)
    }

    @Test
    fun withThePlannedSetsDoneTheFloorStillOffersNoStartNext() {
        // The idle "Start next" used to stand here, once the plan was met and rest was idle.
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(3), withNextLift = true)
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.primaryAction.value.kind == WorkoutPrimaryKind.NEXT_EXERCISE }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.NEXT).assertIsDisplayed()
        assertNoStartNext()
    }

    @Test
    fun theIdleCardsSheetNamesTheNextRest() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        // A length the rest is not already set to, so the pick has to travel to count.
        assertTrue(vm.restTimerState.value.totalSeconds != 180)
        idleTile().performClick()
        compose.onNodeWithTag(SHEET).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SHEET))).performScrollToNode(hasText("3:00"))
        compose.onNode(hasText("3:00") and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.totalSeconds == 180 }
        compose.waitForIdle()
        compose.onNodeWithTag(SHEET).assertDoesNotExist()
        idleTile(clock = "3:00").assertIsDisplayed()
        assertTrue("a preset names the rest; it does not start it", !vm.restTimerState.value.running)
    }

    @Test
    fun theSheetsStepAndCustomLengthWriteThePlannedRest() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        val planned = vm.restTimerState.value.totalSeconds
        idleTile().performClick()
        // The sheet's +15 steps the planned rest through the ViewModel's nudge.
        compose.onNodeWithTag("workout-rest-sheet-plus").performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.totalSeconds == planned + 15 }
        // Off the presets now, the Custom chip (at the end of the preset row) carries the
        // stepped length.
        val stepped = RestTimer.formatClock(planned + 15)
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(SHEET))).performScrollToNode(hasText(stepped))
        compose.onNode(hasText(stepped) and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))).assertIsDisplayed()
        compose.holdingTheClock {
            compose.onNode(hasText(stepped) and hasClickAction() and hasAnyAncestor(hasTestTag(SHEET))).performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
            compose.onNode(hasSetTextAction()).performTextReplacement("2:30")
            compose.settle()
            compose.onNodeWithText("Set").performSemanticsAction(SemanticsActions.OnClick)
            compose.settle()
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.totalSeconds == 150 }
        compose.onNodeWithTag(SHEET).assertDoesNotExist()
    }

    @Test
    fun theRunningRestStepsSkipsAndOpensTheRestPage() {
        batteryRuleAlreadyRead()
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.running }
        val started = vm.restTimerState.value.remainingSeconds
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.remainingSeconds == started + 15 }
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_BAR)) and hasText("REST")).performClick()
        assertEquals("a tap on the running rest opens this session's rest page", listOf(checkNotNull(vm.uiState.value.session).id), restPages)
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.restTimerState.value.running }
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
    }

    @Test
    fun theFirstRestNamesTheBatteryRuleUntilItIsAcknowledged() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.batteryHint }
        compose.onNodeWithTag("workout-rest-battery").assertIsDisplayed()
        compose.onNode(hasText("Got it") and hasClickAction()).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.restTimerState.value.batteryHint }
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertIsDisplayed()
    }

    @Test
    fun timeSetRunsTheSetStopwatchAndStopEndsIt() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.setStopwatch.value.running }
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed()
        compose.onNode(hasText("SET TIME"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
        compose.onNodeWithTag(STOP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.setStopwatch.value.running }
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
    }

    @Test
    fun aHoldLiftOffersNoTimeSetAndItsClockCountsDownInTheDock() {
        val vm = openPlank()
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).assertDoesNotExist()
        val target = checkNotNull(vm.uiState.value.draft.durationSeconds)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.holdTimer.value.running }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.HOLD_CLOCK).assertIsDisplayed()
        compose.onNode(hasText("HOLD"), useUnmergedTree = true).assertIsDisplayed()
        // The hold's own target, counting down: remaining, not elapsed.
        compose.onNode(hasText(HoldWork.clock(target)) and hasAnyAncestor(hasTestTag(WorkoutTestTags.HOLD_CLOCK)), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag(STOP).assertDoesNotExist()
    }

    @Test
    fun withRestAlertsOffTheDockSaysSo() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm, notificationsEnabled = false)
        compose.onNodeWithTag("workout-notif-recovery").assertIsDisplayed()
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertDoesNotExist()
    }

    @Test
    fun theRestAlertsFixOpensTheAppsNotificationSettings() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm, notificationsEnabled = false)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).clearNextStartedActivities()
        compose.onNodeWithTag("workout-notif-recovery").performClick()
        compose.waitForIdle()
        val opened = checkNotNull(shadowOf(app).nextStartedActivity) { "the fix opened nothing" }
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(app.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun aRunningRestThatMayNotSurviveLeavingTheAppSaysSo() {
        batteryRuleAlreadyRead()
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.restTimerState.value.running }
        compose.onNodeWithTag(HONESTY).assertDoesNotExist()
        // The rest's row did not reach storage: the floor says the rest may be lost.
        deps.setRestPersistenceHealthy(false)
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.restTimerState.value.persistenceHealthy }
        compose.waitForIdle()
        compose.onNodeWithTag(HONESTY).assertIsDisplayed()
        compose.onNodeWithText(RestHonestyCopy.PERSISTENCE).assertIsDisplayed()
    }

    @Test
    fun afterAWarmupTheIdleCardSaysWarmupsDoNotStartRest() {
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(TestSetInput(weightKg = FLOOR_KG70, reps = 8, isWarmup = true)))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertIsDisplayed()
        onIdleCard("WARM-UP").assertIsDisplayed()
        // After a warm-up the idle card says why rest did not start in place of "Planned".
        onIdleCard("Warm-ups do not start rest").assertIsDisplayed()
        assertTrue("a warm-up does not start rest", !vm.restTimerState.value.running)
    }

    @Test
    fun whileASaveIsUnderwayTheEffortChoicesAreLocked() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        scrollTo(WorkoutTestTags.RPE_TRACK)
        RpeCopy.VALUES.forEach { compose.onNodeWithTag(WorkoutTestTags.rpeChoice(it)).assertIsEnabled() }
        val gate = CompletableDeferred<Unit>().also { insertGate = it }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.entryLocked }
        compose.waitForIdle()
        scrollTo(WorkoutTestTags.RPE_TRACK)
        RpeCopy.VALUES.forEach { compose.onNodeWithTag(WorkoutTestTags.rpeChoice(it)).assertIsNotEnabled() }
        gate.complete(Unit)
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.uiState.value.entryLocked && vm.uiState.value.session?.sets?.size == 2 }
    }

    @Test
    fun anEffortChipWritesTheDraftAndAWarmupHidesTheTrack() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        val choice = if (vm.uiState.value.draft.rpe == 9) 7 else 9
        scrollTo(WorkoutTestTags.RPE_TRACK)
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(choice)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.rpe == choice }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(choice)).assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(choice)).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.rpe == null }
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.isWarmup }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertDoesNotExist()
        scrollTo(WorkoutTestTags.RPE_WARMUP_REASON)
        compose.onNodeWithTag(WorkoutTestTags.RPE_WARMUP_REASON).assertIsDisplayed()
    }

    @Test
    fun theCoachsEffortIsRecommendedOnTheTrackButNeverChosenForTheLifter() {
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(set(reps = 9, rpe = 8)))
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value?.nextRpe != null }
        vm.setRpe(null)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.rpe == null && vm.microRec.value?.nextRpe != null }
        compose.waitForIdle()
        val recommended = checkNotNull(vm.microRec.value?.nextRpe)
        scrollTo(WorkoutTestTags.RPE_TRACK)
        val chip = compose.onNodeWithTag(WorkoutTestTags.rpeChoice(recommended)).assertIsNotSelected()
        assertTrue("was ${chip.spokenDescriptions()}", chip.spokenDescriptions().single().endsWith(", recommended"))
    }

    @Test
    fun applyCopiesTheCoachsCallIntoTheDraftAndTheCardStandsDown() {
        // Nine of ten: the coach calls one more rep at the same weight.
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(set(reps = 9, rpe = 8)))
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value != null }
        vm.setReps(8)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.reps == 8 }
        compose.waitForIdle()
        val rec = checkNotNull(vm.microRec.value)
        scrollTo(WorkoutTestTags.NEXT_SET)
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.reps == rec.nextReps }
        val draft = vm.uiState.value.draft
        assertEquals(rec.nextWeightKg, draft.weightKg, 1e-6)
        assertEquals(rec.nextRpe, draft.rpe)
        assertTrue("Apply fills the entry; it never logs", vm.uiState.value.session?.sets?.size == 1)
        // The entry now matches, so the card folds to its strip and says Applied.
        scrollTo(WorkoutTestTags.NEXT_SET)
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertIsDisplayed()
        compose.onNode(hasText("Applied"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    fun theCoachsCardSitsAfterTheEntryAndTheEffort() {
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(set(reps = 9, rpe = 8)))
        show(vm, heightDp = 1600)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value != null }
        vm.setReps(8)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.reps == 8 }
        compose.waitForIdle()
        // Numbers first, then how hard it felt, then what the coach calls next.
        val order = listOf(WorkoutTestTags.SET_ENTRY, WorkoutTestTags.RPE_TRACK, WorkoutTestTags.NEXT_SET)
        val tops = order.map { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
    }

    @Test
    fun beforeTheFirstWorkingSetTheCoachIsTheCompactStripAndAWarmupHidesIt() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value != null }
        compose.waitForIdle()
        scrollTo(WorkoutTestTags.NEXT_SET)
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertIsDisplayed()
        scrollTo(WorkoutTestTags.WARMUP_CHIP)
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.isWarmup }
        compose.waitForIdle()
        // Look where the card would be: the effort track's warm-up line sits just above it,
        // so the card's place is composed and a card there would be found.
        scrollTo(WorkoutTestTags.RPE_WARMUP_REASON)
        compose.onNodeWithTag(WorkoutTestTags.RPE_WARMUP_REASON).assertIsDisplayed()
        compose.onAllNodesWithTag(WorkoutTestTags.NEXT_SET).assertCountEquals(0)
        compose.onAllNodesWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertCountEquals(0)
    }

    @Test
    fun theFloorsCoachSpeaksWithTheTrainingGoal() {
        runBlocking { deps.preferencesRepository.setTrainingGoal(TrainingGoal.STRENGTH) }
        // Ten of ten at RPE 7: reps in the tank, add weight.
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(set(reps = 10, rpe = 7)))
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value?.explanation?.contains("strength bias") == true }
        vm.setWeight(FLOOR_KG70)
        compose.waitForIdle()
        scrollTo(WorkoutTestTags.NEXT_SET)
        // The card keeps the rule and Target RPE, drawn whole: text found by its words can
        // still be cut on screen, so the last drawn line is checked too.
        assertReasonDrawnWhole("Had more in you — add weight · Target RPE 7")
        // The goal set in Settings reaches the floor (audit C-1, W1b): a Strength lifter reads
        // the strength reason on the Why sheet, not the goal-free one it used to get.
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onNodeWithText(
            "Rule: Had more in you — add weight · strength bias keeps reps before big jumps",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun aGeneralGoalKeepsThePlainReason() {
        // The default goal adds nothing, so the card's reason is the rule alone.
        val vm = openLegExtension(deps, viewModels, loggedSets = listOf(set(reps = 10, rpe = 7)))
        show(vm)
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.microRec.value != null }
        vm.setWeight(FLOOR_KG70)
        compose.waitForIdle()
        scrollTo(WorkoutTestTags.NEXT_SET)
        assertReasonDrawnWhole("Had more in you — add weight · Target RPE 7")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onNodeWithText("Rule: Had more in you — add weight\n", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("strength bias", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertReasonDrawnWhole(reason: String) {
        val layout = compose.onNode(hasText(reason), useUnmergedTree = true).assertIsDisplayed().textLayout()
        assertTrue("\"$reason\" is not cut short", !layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue("\"$reason\" is laid out whole", !layout.hasVisualOverflow)
    }

    @Test
    fun theHeadersXAndSystemBackLeaveWithTheSessionKeptAndNoPopup() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithContentDescription("Exit workout").performClick()
        compose.waitForIdle()
        assertEquals(1, exits)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(2, exits)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        assertTrue("leaving keeps the session live", vm.uiState.value.session?.finishedAt == null)
    }

    @Test
    fun finishOwnsTheEndOfTheWorkout() {
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        assertEquals("Finish is not an exit", 0, exits)
    }

    @Test
    fun finishWaitsWhileASetIsStillBeingSaved() {
        // The header's Finish follows the ViewModel's canFinish: a set is saved and nothing is
        // being written. Mid-save it waits, so a workout cannot be ended over a write in flight.
        val vm = openLegExtension(deps, viewModels, loggedSets = sets(1))
        show(vm)
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsEnabled()
        val gate = CompletableDeferred<Unit>().also { insertGate = it }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.entryLocked }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsNotEnabled().performClick()
        compose.onAllNodesWithText("End workout?").assertCountEquals(0)
        gate.complete(Unit)
        compose.waitUntil(timeoutMillis = WAIT_MS) { !vm.uiState.value.entryLocked && vm.uiState.value.session?.sets?.size == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsEnabled()
    }

    /** No "Start next" anywhere: not a word on screen, not a control's spoken name. */
    private fun assertNoStartNext() {
        compose.onAllNodesWithText("Start next", substring = true, ignoreCase = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Start next", substring = true, ignoreCase = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun idleTile(clock: String? = null) = compose.onNode(
        hasClickAction() and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_IDLE)) and
            (if (clock == null) hasText("REST") else hasText(clock)),
    )

    /** A word drawn on the idle rest card, found where it is drawn rather than in the merged tile. */
    private fun onIdleCard(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(WorkoutTestTags.REST_IDLE)), useUnmergedTree = true)

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun show(vm: ActiveWorkoutViewModel, notificationsEnabled: Boolean = true, heightDp: Int = 800) {
        compose.showFloor {
            Box(modifier = Modifier.width(360.dp).height(heightDp.dp)) {
                ActiveWorkoutScreen(
                    onExit = { exits += 1 },
                    onFinished = {},
                    onOpenRest = { restPages += it },
                    viewModel = vm,
                    restNotificationsEnabledOverride = notificationsEnabled,
                )
            }
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        // Found is not ready: while the lift's numbers are still being filled in, the commit
        // cannot log and a tap on it is dropped, so wait for the lift itself to be ready.
        compose.awaitThat(what = "the lift is ready to log", now = vm.uiState::value) { FLOOR_LIFT_READY(vm.uiState.value) }
        compose.waitForIdle()
    }

    /** The first rest's battery sentence takes the card's place; one test reads it, the rest have. */
    private fun batteryRuleAlreadyRead() {
        runBlocking { deps.preferencesRepository.markRestBatteryHintShown() }
    }

    private fun set(reps: Int, rpe: Int?) = TestSetInput(weightKg = FLOOR_KG70, reps = reps, rpe = rpe)

    private fun sets(count: Int) = (1..count).map { set(reps = 10, rpe = 8) }

    /** A plank first: a hold, timed against its target rather than counted in reps. */
    private fun openPlank(): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            seedTestWorkout(
                deps = deps,
                exerciseId = "plank",
                exerciseName = "Plank",
                routineName = "Core",
                targetSets = 3,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = 60,
            ).session.id
        }
        return viewModel(sessionId)
    }

    private fun viewModel(sessionId: String) = ActiveWorkoutViewModel(
        application = ApplicationProvider.getApplicationContext(),
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        container = deps,
        undoTimeout = { it.toLong() },
    ).also(viewModels::add)

    /** Every write the floor makes, except that a set's row first waits for [insertGate]. */
    private inner class GatedInserts(private val real: WorkoutDao) : WorkoutDao by real {
        override suspend fun insertSet(set: SetLogEntity) {
            insertGate?.await()
            real.insertSet(set)
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val HONESTY = "workout-rest-honesty"
        const val SHEET = "workout-rest-duration-sheet"
        const val STOP = "workout-stop-set-clock"

        /** The kit's next lift rests 90 s between its sets. */
        const val NEXT_LIFT_REST_S = 90
    }
}
