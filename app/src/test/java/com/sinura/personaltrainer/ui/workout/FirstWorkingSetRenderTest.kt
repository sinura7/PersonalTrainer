package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Pit
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Packet W1c: the first working set of a lift leaves the entry wells where they were.
 *
 * Until that set is saved the stats row shows the Last cell alone (ADR-030 §2), and the save
 * brings Best and Volume. Side by side, below font 1.6, the lone cell drew a one-line label
 * where the full row reserves two, so the first working save pushed the numerals down one
 * caption line — the hosted journey's "ordinary logging must retain the entry position", 955
 * to 997 px since #374. FloorScreenWiringRenderTest's save guard opens with two sets logged,
 * after that transition, which is why the JVM gate stayed green. Stacked, from font 1.6, Best
 * and Volume still arrive above the entry: the owner accepted that on 23 September 2026
 * (ADR-030), so those frames hold that the entry and the commit stay reachable and that the
 * Last cell keeps its one-line label.
 *
 * Every case first checks the phase change it exists for: the Last cell alone before the save,
 * Best and Volume after. Side by side it holds the stats row's height, the cause, whatever the
 * scroll, and reads positions only with that row on screen.
 *
 * ADR-032's matrix — 360×640, 412 dp and landscape at font 1.0, 1.6 and 2.0 — each drawn
 * before and after the save to `app/build/screen-renders/w1c/`, through the real ViewModel,
 * Room and timer graph as FloorScreenWiringRenderTest composes them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class FirstWorkingSetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    @Before
    fun setUp() {
        // The ViewModel's scope runs inline on the test thread, as in FloorScreenWiringRenderTest.
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
    fun theFirstWorkingSetHoldsTheEntryAt360By640() = holdsTheEntry(widthDp = 360, heightDp = 640, fontScale = 1f)

    /** One of Android's own text-size steps below the stacked layout (Android 14 and later also offer 1.5). */
    @Test
    fun theFirstWorkingSetHoldsTheEntryAt360By640AtFont13() = holdsTheEntry(widthDp = 360, heightDp = 640, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun theFirstWorkingSetHoldsTheEntryAt412By840() = holdsTheEntry(widthDp = 412, heightDp = 840, fontScale = 1f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun theFirstWorkingSetHoldsTheEntryInLandscape() = holdsTheEntry(widthDp = 800, heightDp = 360, fontScale = 1f)

    /** A warm-up is still the prepare phase: neither it nor the working set after it moves the entry. */
    @Test
    fun aWarmupThenTheFirstWorkingSetHoldTheEntry() {
        val vm = openLegExtension()
        show(vm, widthDp = 360, heightDp = 640, fontScale = 1f)
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.draft.isWarmup }
        compose.waitForIdle()
        reachTheEntry()
        assertLastAlone()
        capture("warmup-first-360x640-font1.0-before")
        val stats = statsRowHeight()
        val entry = entryTop()
        val numeral = lastNumeralTop()
        logAndWait(vm, sets = 1)
        capture("warmup-first-360x640-font1.0-after-warmup")
        assertTrue("the first save must be the warm-up", vm.uiState.value.session!!.sets.single().isWarmup)
        assertLastAlone()
        assertEquals("a warm-up must not grow the stats row", stats, statsRowHeight(), 1f)
        assertEquals("a warm-up must not move the numerals", entry, entryTop(), 1f)
        assertEquals("a warm-up must not move the Last set's number", numeral, lastNumeralTop(), 1f)
        // The save hands the draft back as a working set, so the next Log is the first one. Two
        // Logs inside the double-tap window are one tap (performPrimary); a lifter's next set
        // comes long after, so the device clock passes the window first.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout().toLong()))
        logAndWait(vm, sets = 2)
        capture("warmup-first-360x640-font1.0-after-working")
        assertFalse("the second save must be a working set", vm.uiState.value.session!!.sets.last().isWarmup)
        assertFullRow()
        assertEquals("the first working set must not grow the stats row", stats, statsRowHeight(), 1f)
        assertEquals("the first working set must not move the numerals", entry, entryTop(), 1f)
        assertEquals("the first working set must not move the Last set's number", numeral, lastNumeralTop(), 1f)
        assertCommitReachable()
    }

    @Test
    fun stackedAt360By640Font16TheEntryAndCommitStayReachable() = staysReachable(widthDp = 360, heightDp = 640, fontScale = 1.6f)

    @Test
    fun stackedAt360By640Font20TheEntryAndCommitStayReachable() = staysReachable(widthDp = 360, heightDp = 640, fontScale = 2f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun stackedAt412By840Font16TheEntryAndCommitStayReachable() = staysReachable(widthDp = 412, heightDp = 840, fontScale = 1.6f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun stackedAt412By840Font20TheEntryAndCommitStayReachable() = staysReachable(widthDp = 412, heightDp = 840, fontScale = 2f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun stackedInLandscapeFont16TheEntryAndCommitStayReachable() = staysReachable(widthDp = 800, heightDp = 360, fontScale = 1.6f)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun stackedInLandscapeFont20TheEntryAndCommitStayReachable() = staysReachable(widthDp = 800, heightDp = 360, fontScale = 2f)

    /**
     * Side by side: the first working save leaves the stats row its height, and so the numerals
     * and the Last set's number above them exactly where they were. One pixel, as in
     * FloorScreenWiringRenderTest's save guard.
     *
     * The height is the cause and holds whatever the scroll. The positions are read with the stats
     * row on screen above the entry, where a taller row would push the entry down: scrolled until
     * the row is out of view, the list holds the entry still whatever the row does.
     */
    private fun holdsTheEntry(widthDp: Int, heightDp: Int, fontScale: Float) {
        assertTrue(!LogLoopScale.stackEntryWells(fontScale))
        val vm = openLegExtension()
        show(vm, widthDp, heightDp, fontScale)
        val name = "${widthDp}x$heightDp-font$fontScale"
        reachTheEntry()
        assertLastAlone()
        capture("$name-before")
        val stats = statsRowHeight()
        val entry = entryTop()
        val numeral = lastNumeralTop()
        logAndWait(vm, sets = 1)
        capture("$name-after")
        assertFullRow()
        assertEquals("the first working set must not grow the stats row", stats, statsRowHeight(), 1f)
        assertEquals("the first working set must not move the numerals", entry, entryTop(), 1f)
        assertEquals("the first working set must not move the Last set's number", numeral, lastNumeralTop(), 1f)
        assertCommitReachable()
    }

    /**
     * Stacked: Best and Volume arrive above the entry on the first working set, as ADR-030
     * accepts. What must hold is that the lifter can still reach the numerals and the commit, and
     * that the Last cell keeps its one-line label: stacked, nothing sits beside it to line up with.
     */
    private fun staysReachable(widthDp: Int, heightDp: Int, fontScale: Float) {
        assertTrue(LogLoopScale.stackEntryWells(fontScale))
        val vm = openLegExtension()
        show(vm, widthDp, heightDp, fontScale)
        val name = "${widthDp}x$heightDp-font$fontScale"
        reachTheEntry()
        capture("$name-before")
        assertCommitReachable()
        assertLastAlone()
        val gap = lastLabelGap()
        assertLastLabelIsOneLine()
        reachTheEntry()
        logAndWait(vm, sets = 1)
        capture("$name-after")
        assertFullRow()
        assertEquals("stacked, the Last cell keeps its one-line label", gap, lastLabelGap(), 1f)
        assertLastLabelIsOneLine()
        reachTheEntry()
        capture("$name-after-entry")
        assertCommitReachable()
    }

    /**
     * Scrolls the floor until the numerals are fully in view, as a thumb does on a screen too
     * short to show them under the identity. (The hosted journey scrolls to the weight stepper.)
     */
    private fun reachTheEntry() {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.SET_ENTRY))
        compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed()
    }

    /**
     * Scrolls the floor until the stats row is fully in view. From the entry that is the least
     * scroll back up, which leaves the row straight above the numerals.
     */
    private fun reachTheStatsRow() {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.STATS_ROW))
        compose.onNodeWithTag(WorkoutTestTags.STATS_ROW).assertIsDisplayed()
    }

    /** The prepare phase (ADR-030 §2), seen on screen: the Last cell alone. */
    private fun assertLastAlone() {
        reachTheStatsRow()
        compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.STAT_BEST).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.STAT_VOLUME).assertDoesNotExist()
    }

    /** After the first working set: Best and Volume have joined the Last cell. */
    private fun assertFullRow() {
        reachTheStatsRow()
        compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.STAT_BEST).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.STAT_VOLUME).assertExists()
    }

    /** Saves the draft through the dock's Log, as a thumb does, and waits for the row to land. */
    private fun logAndWait(vm: ActiveWorkoutViewModel, sets: Int) {
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.session?.sets?.size == sets }
        compose.waitForIdle()
    }

    private fun assertCommitReachable() {
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed()
    }

    /** The stats row's own laid-out height, in pixels, unclipped: the cause, whatever the scroll. */
    private fun statsRowHeight(): Float {
        reachTheStatsRow()
        val height = compose.onNodeWithTag(WorkoutTestTags.STATS_ROW).fetchSemanticsNode().size.height
        assertTrue("the stats row must be laid out", height > 0)
        return height.toFloat()
    }

    /**
     * Where the numerals sit, in pixels, so "did not move" can be held to one pixel. Read with the
     * stats row on screen, or a taller row could not move them at all.
     */
    private fun entryTop(): Float {
        compose.onNodeWithTag(WorkoutTestTags.STATS_ROW).assertIsDisplayed()
        return compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed().fetchSemanticsNode().positionInRoot.y
    }

    /**
     * The Last cell's two texts, label above value. Fails, rather than reading nothing, unless the
     * cell is laid out on screen.
     */
    private fun lastCellTexts(): List<SemanticsNode> {
        val cell = compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("the Last cell must be laid out", cell.size.height > 0)
        val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(WorkoutTestTags.STAT_LAST)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { SemanticsProperties.Text in it.config }
            .sortedBy { it.positionInRoot.y }
        assertEquals("the Last cell's label and number", 2, texts.size)
        return texts
    }

    /**
     * The Last cell's label is one line with no blank line reserved under it: its box is exactly
     * as tall as its one line of text.
     */
    private fun assertLastLabelIsOneLine() {
        val label = lastCellTexts().first()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        val layout = layouts.single()
        assertEquals("stacked, the Last cell's label fits one line", 1, layout.lineCount)
        assertEquals("stacked, the Last cell's label reserves no second line", layout.getLineBottom(0), label.size.height.toFloat(), 1f)
    }

    /** The top of the Last cell's number. */
    private fun lastNumeralTop(): Float = lastCellTexts().last().positionInRoot.y

    /** From the top of the Last cell's label to the top of its number, after scrolling to the cell. */
    private fun lastLabelGap(): Float {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.STAT_LAST))
        val (label, number) = lastCellTexts()
        return number.positionInRoot.y - label.positionInRoot.y
    }

    private fun show(vm: ActiveWorkoutViewModel, widthDp: Int, heightDp: Int, fontScale: Float) {
        compose.showFloor(fontScale = fontScale) {
            Box(modifier = Modifier.width(widthDp.dp).height(heightDp.dp).background(Pit)) {
                ActiveWorkoutScreen(
                    onExit = {},
                    onFinished = {},
                    viewModel = vm,
                    restNotificationsEnabledOverride = true,
                )
            }
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.loadState == SessionLoadState.FOUND }
        compose.waitUntil(timeoutMillis = WAIT_MS) { vm.uiState.value.session?.exercises?.isNotEmpty() == true }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        // Robolectric never delivers the draw callback captureToImage waits on; draw the
        // window ourselves, as WorkoutFloorRenderTest does.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val out = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(out))
            out
        }
        val out = File("build/screen-renders/w1c").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    /** Leg extension with nothing logged today and no history: the prepare phase, Last cell alone. */
    private fun openLegExtension(): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            seedTestWorkout(
                deps = deps,
                exerciseId = "leg-extension",
                exerciseName = "Leg Extension",
                routineName = "Lower B",
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = WeightConverter.lbsToKg(70.0),
                restSeconds = 120,
            ).session.id
        }
        return ActiveWorkoutViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
            undoTimeout = { it.toLong() },
        ).also(viewModels::add)
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
