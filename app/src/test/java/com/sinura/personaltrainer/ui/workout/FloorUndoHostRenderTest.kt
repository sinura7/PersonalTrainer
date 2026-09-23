package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.UndoKind
import com.sinura.personaltrainer.domain.WeightUnit
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
import org.robolectric.annotation.GraphicsMode

/**
 * Undo on the floor, as the lifter meets it: a delete or a removed lift offers itself back in
 * words with an Undo, for as long as the accessibility setting asks, with a fresh dwell for
 * each offer even when two read the same, and a tap on Undo puts the thing back. While a lift
 * is selected the offer rides the dock's companion slot; with no lift left it rides the banner
 * above the floor. A delete lands as a warning and an undo as a commit, in the hand.
 *
 * These were lines of GymStatus.kt, WorkoutDock.kt and ActiveWorkoutScreen.kt read as text
 * (`fun GymUndoHost`, `UndoHostCopy.ACTION`, `offerKey`, `dwellMs`, `liveRegion`, `offerKey =
 * state.undoKey ?: state.undoMessage`, `undoKey = undoEntries.lastOrNull()?.offer?.key`,
 * `onUndo = viewModel::undoTopOffer`, `onUndoDismissed = viewModel::onUndoOfferExpired`,
 * `deleteFeedback.collect`). A renamed reference failed those lines while a broken Undo passed.
 *
 * The dock is composed on its own where the rule is the host's (the words, the dwell, the
 * key); the real screen and ViewModel where it is the wiring, with a [FeltView] as the
 * screen's view so every haptic the floor asks for is written down in order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class FloorUndoHostRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()
    private lateinit var felt: FeltView

    private var undone = 0
    private var dismissed = 0
    private val logSet = floorPrimaryAction(kind = WorkoutPrimaryKind.LOG_SET, draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10))
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
    fun theDocksOfferSaysWhatWentAndItsUndoPutsItBack() {
        showDock(floorDockState(action = logSet, undoMessage = SET_GONE).copy(undoKey = "undo-1"))
        compose.onNodeWithText(SET_GONE).assertIsDisplayed()
        // A screen reader hears the offer arrive without moving there.
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and hasAnyDescendant(hasText(SET_GONE)),
            useUnmergedTree = true,
        ).assertExists()
        undoButton().assertIsDisplayed().performClick()
        assertEquals(1, undone)
        // Taken, the offer does not also time out behind the lifter's back.
        compose.mainClock.advanceTimeBy(dockState.undoDwellMs + ONE_MINUTE_MS)
        compose.waitForIdle()
        assertEquals(0, dismissed)
    }

    @Test
    fun theDocksOfferStaysExactlyAsLongAsItsDwell() {
        showDock(floorDockState(action = logSet, undoMessage = SET_GONE).copy(undoKey = "undo-1", undoDwellMs = TALKBACK_DWELL_MS))
        compose.mainClock.advanceTimeBy(TALKBACK_DWELL_MS - ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals("still inside the twenty seconds TalkBack asked for", 0, dismissed)
        undoButton().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aNewOfferInTheSameWordsGetsAFreshDwell() {
        showDock(floorDockState(action = logSet, undoMessage = SET_GONE).copy(undoKey = "undo-1", undoDwellMs = TALKBACK_DWELL_MS))
        compose.mainClock.advanceTimeBy(15 * ONE_SECOND_MS)
        compose.waitForIdle()
        // A second set with the same numbers goes: the words match, the offer is new.
        dockState = dockState.copy(undoKey = "undo-2")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(10 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals("the first offer's twenty seconds are up, the second's are not", 0, dismissed)
        undoButton().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(11 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun aDeletedSetComesBackFromTheDocksUndo() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(2))
        showFelt(vm)
        val first = checkNotNull(vm.uiState.value.session).sets.minBy { it.completedAt }
        vm.deleteSet(first.id)
        awaitOffers(vm, count = 1)
        onDock(vm.undoEntries.value.last().offer.message).assertIsDisplayed()
        assertEquals("a delete lands as a warning", listOf(HapticFeedbackConstants.CONTEXT_CLICK), felt.felt())
        undoButton(inDock = true).performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.session?.sets?.size == 2 && vm.undoEntries.value.isEmpty() }
        compose.waitForIdle()
        assertTrue("the set is back", checkNotNull(vm.uiState.value.session).sets.any { it.id == first.id })
        assertEquals("an undo lands as a commit", HapticFeedbackConstants.CONFIRM, felt.felt().last())
        assertTrue("the offer is gone", compose.onAllNodes(hasText("Undo") and hasClickAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theDocksOfferStaysForTheAccessibilityDwellThenLetsGo() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(2), undoTimeout = dwellOf(LONG_DWELL_MS))
        showFelt(vm)
        vm.deleteSet(checkNotNull(vm.uiState.value.session).sets.minBy { it.completedAt }.id)
        awaitOffers(vm, count = 1)
        assertEquals(LONG_DWELL_MS, vm.undoDwellMs.value)
        compose.mainClock.advanceTimeBy(40 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals("forty seconds into a minute's dwell the offer stands", 1, vm.undoEntries.value.size)
        undoButton(inDock = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(25 * ONE_SECOND_MS)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.isEmpty() }
        compose.waitForIdle()
        assertTrue("the offer is gone", compose.onAllNodes(hasText("Undo") and hasClickAction()).fetchSemanticsNodes().isEmpty())
        assertEquals("letting it go puts nothing back", 1, checkNotNull(vm.uiState.value.session).sets.size)
    }

    @Test
    fun aSecondDeleteInTheSameWordsKeepsItsOwnDwellOnTheDock() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(2), undoTimeout = dwellOf(LONG_DWELL_MS))
        showFelt(vm)
        val (first, second) = checkNotNull(vm.uiState.value.session).sets.sortedBy { it.completedAt }
        vm.deleteSet(first.id)
        awaitOffers(vm, count = 1)
        compose.mainClock.advanceTimeBy(30 * ONE_SECOND_MS)
        compose.waitForIdle()
        vm.deleteSet(second.id)
        awaitOffers(vm, count = 2)
        val offers = vm.undoEntries.value.map { it.offer }
        assertEquals("both offers read the same", offers[0].message, offers[1].message)
        compose.mainClock.advanceTimeBy(40 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals("past the first offer's minute, the second's is still running", 2, vm.undoEntries.value.size)
        compose.mainClock.advanceTimeBy(25 * ONE_SECOND_MS)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.size == 1 }
    }

    @Test
    fun withNoLiftLeftTheBannerOffersTheLiftBack() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList())
        showFelt(vm)
        vm.removeSelectedLift()
        awaitOffers(vm, count = 1)
        assertEquals(UndoKind.REMOVED_LIFT, vm.undoEntries.value.last().offer.kind)
        // No lift is selected, so there is no dock companion: the offer is the banner's.
        compose.onNodeWithTag(WorkoutTestTags.TIMER_ROW).assertDoesNotExist()
        compose.onNodeWithText(LIFT_GONE).assertIsDisplayed()
        assertEquals("a removed lift lands as a warning", listOf(HapticFeedbackConstants.CONTEXT_CLICK), felt.felt())
        undoButton().performClick()
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.session?.exercises?.size == 1 && vm.undoEntries.value.isEmpty() }
        compose.waitForIdle()
        assertEquals("an undo lands as a commit", HapticFeedbackConstants.CONFIRM, felt.felt().last())
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    @Test
    fun theBannerStaysForTheAccessibilityDwellThenLetsGo() {
        val vm = openLegExtension(deps, viewModels, loggedSets = emptyList(), undoTimeout = dwellOf(LONG_DWELL_MS))
        showFelt(vm)
        vm.removeSelectedLift()
        awaitOffers(vm, count = 1)
        compose.mainClock.advanceTimeBy(40 * ONE_SECOND_MS)
        compose.waitForIdle()
        assertEquals(1, vm.undoEntries.value.size)
        compose.onNodeWithText(LIFT_GONE).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(25 * ONE_SECOND_MS)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.isEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText(LIFT_GONE).assertDoesNotExist()
        assertTrue("letting it go puts nothing back", checkNotNull(vm.uiState.value.session).exercises.isEmpty())
    }

    @Test
    fun theBannerGivesEachOfferUnderneathItsOwnDwell() {
        val vm = openLegExtension(deps, viewModels, loggedSets = floorSets(2), undoTimeout = dwellOf(TALKBACK_DWELL_MS))
        showFelt(vm)
        val (first, second) = checkNotNull(vm.uiState.value.session).sets.sortedBy { it.completedAt }
        vm.deleteSet(first.id)
        awaitOffers(vm, count = 1)
        vm.deleteSet(second.id)
        awaitOffers(vm, count = 2)
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.uiState.value.session?.sets?.isEmpty() == true }
        vm.removeSelectedLift()
        awaitOffers(vm, count = 3)
        compose.onNodeWithText(LIFT_GONE).assertIsDisplayed()
        // Each dwell that runs out reveals the offer underneath, which gets its own; the last
        // two read the same, and still each gets its own.
        listOf(2, 1, 0).forEach { left ->
            compose.mainClock.advanceTimeBy(TALKBACK_DWELL_MS + ONE_SECOND_MS)
            compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.size == left }
            compose.waitForIdle()
        }
    }

    private fun undoButton(inDock: Boolean = false): SemanticsNodeInteraction = compose.onNode(
        if (inDock) {
            hasText("Undo") and hasClickAction() and hasAnyAncestor(hasTestTag(WorkoutTestTags.TIMER_ROW))
        } else {
            hasText("Undo") and hasClickAction()
        },
    )

    private fun onDock(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(WorkoutTestTags.TIMER_ROW)))

    private fun awaitOffers(vm: ActiveWorkoutViewModel, count: Int) {
        compose.waitUntil(timeoutMillis = FLOOR_WAIT_MS) { vm.undoEntries.value.size == count && !vm.uiState.value.entryLocked }
        compose.waitForIdle()
    }

    private fun showDock(state: WorkoutDockState) {
        dockState = state
        val onUndo: () -> Unit = { undone += 1 }
        val events = floorDockEvents(onUndoDismissed = { dismissed += 1 }).copy(onUndo = onUndo)
        compose.showFloor { WorkoutDock(state = dockState, events = events) }
    }

    /** The real screen, with [felt] as its view so every haptic it asks for is written down. */
    private fun showFelt(vm: ActiveWorkoutViewModel) {
        felt = compose.attachedFeltView()
        compose.showWorkoutScreen(vm, view = felt)
    }

    /** What the accessibility setting asks of the undo dwell: [ms], whatever the base. */
    private fun dwellOf(ms: Long): UndoTimeoutProvider = UndoTimeoutProvider { ms }

    private companion object {
        const val ONE_SECOND_MS = 1_000L
        const val ONE_MINUTE_MS = 60_000L
        const val TALKBACK_DWELL_MS = 20_000L
        const val LONG_DWELL_MS = 60_000L
        const val SET_GONE = "Set deleted · 70 lb × 10"
        const val LIFT_GONE = "Lift removed · Leg Extension"
    }
}
