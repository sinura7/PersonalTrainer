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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
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
 * Packets W1c and W1d: the first working set of a lift leaves the entry wells where they were.
 *
 * Until that set is saved the stats row shows the Last cell alone (ADR-030 §2), and side by side
 * the save brings Best and Volume. W1c: below font 1.6, the lone cell drew a one-line label where
 * the full row reserves two, so the first working save pushed the numerals down one caption line
 * — the hosted journey's "ordinary logging must retain the entry position", 955 to 997 px since
 * #374. FloorScreenWiringRenderTest's save guard opens with two sets logged, after that
 * transition, which is why the JVM gate stayed green.
 *
 * W1d, on the owner's decisions of 23 September 2026 (ADR-030): a value too wide for its
 * third-width cell — `102.5 × 10` at font 1.3 — wrapped to a second line and moved the entry
 * too, and now shrinks to fit one line; and stacked, from font 1.6, Best and Volume arrived
 * above the entry and pushed it down, and now never join the floor at those sizes (they are in
 * Details). So every case holds the stats row's height, the numerals and the Last set's number
 * still across the first working save, reading positions only with the row on screen.
 *
 * Every case first checks the phase change it exists for: side by side the Last cell alone
 * before the save and Best and Volume after; stacked, the Last cell alone on both sides of it.
 *
 * ADR-032's matrix — 360×640, 412 dp and landscape at font 1.0, 1.6 and 2.0 — plus the
 * side-by-side sizes where a value wrapped, each drawn before and after the save to
 * `app/build/screen-renders/w1d/`, through the real ViewModel, Room and timer graph as
 * FloorScreenWiringRenderTest composes them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class FirstWorkingSetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<ActiveWorkoutViewModel>()

    /** The floor's own text measurer, at its density and font scale: what a value needs at a given size. */
    private lateinit var measurer: TextMeasurer

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
        val numeral = lastNumeralBaseline()
        logAndWait(vm, sets = 1)
        capture("warmup-first-360x640-font1.0-after-warmup")
        assertTrue("the first save must be the warm-up", vm.uiState.value.session!!.sets.single().isWarmup)
        assertLastAlone()
        assertEquals("a warm-up must not grow the stats row", stats, statsRowHeight(), 1f)
        assertEquals("a warm-up must not move the numerals", entry, entryTop(), 1f)
        assertEquals("a warm-up must not move the Last set's number", numeral, lastNumeralBaseline(), 1f)
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
        assertEquals("the first working set must not move the Last set's number", numeral, lastNumeralBaseline(), 1f)
        assertCommitReachable()
    }

    /**
     * `102.5 × 10` is wider than a third of a 360 dp row at font 1.3 (about 113 dp of type in a
     * 93 dp cell), where `First set` before the save fits: on 961148cd the first working save
     * wrapped Last and Best to a second line and pushed the entry down.
     */
    @Test
    fun aValueTooWideForItsCellShrinksAndHoldsTheEntryAt360By640AtFont13() =
        holdsTheEntry(widthDp = 360, heightDp = 640, fontScale = 1.3f, weightLb = 102.5, valuesMustShrink = true)

    /**
     * Android 14's 1.5 at 412 dp: `First set` fits its full-width cell before the save, and
     * `102.5 × 10` (about 131 dp) does not fit a 110 dp third after it.
     */
    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun aValueTooWideForItsCellShrinksAndHoldsTheEntryAt412By840AtFont15() =
        holdsTheEntry(widthDp = 412, heightDp = 840, fontScale = 1.5f, weightLb = 102.5, valuesMustShrink = true)

    /**
     * `135 × 10` at font 1.5 on 360 dp, with last time's `90 × 5` in the Last cell before the save:
     * one line before, and on 961148cd two after (Last, Best and `1,350 lb` all wrapped).
     */
    @Test
    fun aValueTooWideForItsCellShrinksAndHoldsTheEntryAfterLastTimeAtFont15() =
        holdsTheEntry(widthDp = 360, heightDp = 640, fontScale = 1.5f, weightLb = 135.0, lastTimeLb = 90.0, valuesMustShrink = true)

    @Test
    fun stackedAt360By640Font16TheFirstWorkingSetHoldsTheEntry() = holdsTheEntryStacked(widthDp = 360, heightDp = 640, fontScale = 1.6f)

    @Test
    fun stackedAt360By640Font20TheFirstWorkingSetHoldsTheEntry() = holdsTheEntryStacked(widthDp = 360, heightDp = 640, fontScale = 2f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun stackedAt412By840Font16TheFirstWorkingSetHoldsTheEntry() = holdsTheEntryStacked(widthDp = 412, heightDp = 840, fontScale = 1.6f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun stackedAt412By840Font20TheFirstWorkingSetHoldsTheEntry() = holdsTheEntryStacked(widthDp = 412, heightDp = 840, fontScale = 2f)

    /**
     * Landscape at large text shows the row or the entry, never both at once, so the entry's
     * place is held as the height of everything above it: the identity and the stats row.
     */
    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun stackedInLandscapeFont16TheFirstWorkingSetHoldsTheEntry() =
        holdsTheEntryStacked(widthDp = 800, heightDp = 360, fontScale = 1.6f, rowAndEntryOnOneScreen = false)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun stackedInLandscapeFont20TheFirstWorkingSetHoldsTheEntry() =
        holdsTheEntryStacked(widthDp = 800, heightDp = 360, fontScale = 2f, rowAndEntryOnOneScreen = false)

    /**
     * Side by side: the first working save leaves the stats row its height, and so the numerals
     * and the Last set's number above them exactly where they were. One pixel, as in
     * FloorScreenWiringRenderTest's save guard.
     *
     * The height is the cause and holds whatever the scroll. The positions are read with the stats
     * row on screen above the entry, where a taller row would push the entry down: scrolled until
     * the row is out of view, the list holds the entry still whatever the row does.
     */
    private fun holdsTheEntry(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float,
        weightLb: Double = 70.0,
        lastTimeLb: Double? = null,
        valuesMustShrink: Boolean = false,
    ) {
        assertTrue(!LogLoopScale.stackEntryWells(fontScale))
        val vm = openLegExtension(weightLb = weightLb, lastTimeLb = lastTimeLb)
        show(vm, widthDp, heightDp, fontScale)
        // The fixture's working set, whatever last time's progression would suggest instead.
        compose.runOnIdle {
            vm.setWeight(WeightConverter.lbsToKg(weightLb))
            vm.setReps(10)
        }
        compose.waitForIdle()
        val name = "${widthDp}x$heightDp-font$fontScale" + if (weightLb != 70.0) "-${WeightConverter.formatDisplayNumber(weightLb)}lb" else ""
        reachTheEntry()
        assertLastAlone()
        capture("$name-before")
        val stats = statsRowHeight()
        val entry = entryTop()
        val numeral = lastNumeralBaseline()
        logAndWait(vm, sets = 1)
        capture("$name-after")
        val saved = vm.uiState.value.session!!.sets.single()
        assertEquals("the save must be the fixture's working set", WeightConverter.lbsToKg(weightLb), saved.weightKg, 0.001)
        assertEquals(10, saved.reps)
        assertFullRow()
        assertEquals("the first working set must not grow the stats row", stats, statsRowHeight(), 1f)
        assertEquals("the first working set must not move the numerals", entry, entryTop(), 1f)
        assertEquals("the first working set must not move the Last set's number", numeral, lastNumeralBaseline(), 1f)
        assertEachValueIsOneWholeLine(mustShrink = valuesMustShrink)
        assertCommitReachable()
    }

    /**
     * Stacked, from font 1.6: the floor keeps the Last cell alone before and after the first
     * working set (owner decision of 23 September 2026, ADR-030), so the save leaves the row, the
     * numerals and the Last set's number where they were, and Best and Volume never appear on the
     * floor. The Last cell keeps one line for its label and one for its number, with nothing
     * reserved under either: stacked, nothing sits beside it to line up with.
     */
    private fun holdsTheEntryStacked(widthDp: Int, heightDp: Int, fontScale: Float, rowAndEntryOnOneScreen: Boolean = true) {
        assertTrue(LogLoopScale.stackEntryWells(fontScale))
        val vm = openLegExtension()
        show(vm, widthDp, heightDp, fontScale)
        val name = "${widthDp}x$heightDp-font$fontScale"
        reachTheEntry()
        capture("$name-before-entry")
        assertCommitReachable()
        assertLastAlone()
        capture("$name-before")
        val stats = statsRowHeight()
        val entry = if (rowAndEntryOnOneScreen) entryTop() else null
        val numeral = lastNumeralBaseline()
        val gap = lastLabelGap()
        assertLastLabelIsOneLine()
        assertLastNumberIsOneLine()
        // Last, as it scrolls: every reading above is taken on the same path before and after.
        val identity = identityHeight()
        reachTheEntry()
        logAndWait(vm, sets = 1)
        assertFalse("the save must be a working set", vm.uiState.value.session!!.sets.single().isWarmup)
        capture("$name-after")
        assertEquals("stacked, the first working set must not grow the stats row", stats, statsRowHeight(), 1f)
        if (entry != null) assertEquals("stacked, the first working set must not move the numerals", entry, entryTop(), 1f)
        assertLastAlone()
        assertEquals("stacked, the first working set must not move the Last set's number", numeral, lastNumeralBaseline(), 1f)
        assertEquals("stacked, the Last cell keeps its one-line label", gap, lastLabelGap(), 1f)
        assertLastLabelIsOneLine()
        assertLastNumberIsOneLine()
        assertEquals("stacked, the first working set must not grow the identity above the entry", identity, identityHeight(), 1f)
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

    /** The exercise identity's laid-out height, in pixels: with the stats row, all there is above the entry. */
    private fun identityHeight(): Float {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.CURRENT_LIFT))
        return compose.onNodeWithTag(WorkoutTestTags.CURRENT_LIFT).fetchSemanticsNode().size.height.toFloat()
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

    /**
     * Stacked, the Last cell's number is one line with nothing reserved around it (the W1c review's
     * surviving mutation M11): its text is one line and exactly that tall, it starts one gap under
     * the label, and the cell's content ends with it.
     */
    private fun assertLastNumberIsOneLine() {
        val (label, number) = lastCellTexts()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(number.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        val layout = layouts.single()
        val words = number.config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals(
            "stacked, the Last cell's \"$words\" is drawn at its full size",
            measurer.measure(words, style = InstrumentType.numeralSm).multiParagraph.intrinsics.maxIntrinsicWidth,
            layout.multiParagraph.intrinsics.maxIntrinsicWidth,
            1f,
        )
        assertEquals("stacked, the Last cell's number fits one line", 1, layout.lineCount)
        assertEquals("stacked, the Last cell's number reserves no second line", layout.getLineBottom(0), number.size.height.toFloat(), 1f)
        val density = number.layoutInfo.density
        val cell = compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).fetchSemanticsNode()
        assertEquals(
            "stacked, the Last cell's number sits one gap under its label",
            label.boundsInRoot.bottom + with(density) { Metrics.space1.toPx() },
            number.boundsInRoot.top,
            1f,
        )
        // The cell's tag sits inside its padding, so its box ends where its content does.
        assertEquals("stacked, nothing is reserved under the Last cell's number", cell.boundsInRoot.bottom, number.boundsInRoot.bottom, 1f)
    }

    /**
     * Side by side, after the first working set: every value is laid out whole on one line, never
     * ellipsised, and never smaller than the label above it. [mustShrink] also asks that at least
     * one of them was drawn smaller than its full size, so the fixture really exercises the fit.
     */
    private fun assertEachValueIsOneWholeLine(mustShrink: Boolean) {
        reachTheStatsRow()
        val sizes = listOf(WorkoutTestTags.STAT_LAST, WorkoutTestTags.STAT_BEST, WorkoutTestTags.STAT_VOLUME).map { tag ->
            val value = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { SemanticsProperties.Text in it.config }
                .sortedBy { it.positionInRoot.y }
                .last()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(value.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
            val layout = layouts.single()
            val words = value.config[SemanticsProperties.Text].joinToString { it.text }
            assertEquals("\"$words\" stays on one line", 1, layout.lineCount)
            assertFalse("\"$words\" is never ellipsised", layout.isLineEllipsized(0))
            assertTrue("\"$words\" is laid out whole", layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width)
            // The size it is drawn at, read from its width: the layout reports the style it was
            // given, not the size it was fitted to.
            val drawn = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            val full = measurer.measure(words, style = InstrumentType.numeralSm).multiParagraph.intrinsics.maxIntrinsicWidth
            val floor = measurer.measure(words, style = InstrumentType.numeralSm.copy(fontSize = InstrumentType.caption.fontSize))
                .multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue("\"$words\" is never drawn smaller than its label's size ($drawn px against $floor)", drawn >= floor - 1f)
            words to (drawn < full - 1f)
        }
        if (mustShrink) {
            assertTrue("a value this wide must have been drawn smaller to fit, were ${sizes.toMap()}", sizes.any { it.second })
        }
    }

    /**
     * The baseline of the Last cell's number: where it reads, so a value drawn smaller to fit its
     * cell still counts as where it was when it sits on the same line.
     */
    private fun lastNumeralBaseline(): Float {
        val number = lastCellTexts().last()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(number.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        return number.positionInRoot.y + layouts.single().firstBaseline
    }

    /** From the top of the Last cell's label to the top of its number, after scrolling to the cell. */
    private fun lastLabelGap(): Float {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.STAT_LAST))
        val (label, number) = lastCellTexts()
        return number.positionInRoot.y - label.positionInRoot.y
    }

    private fun show(vm: ActiveWorkoutViewModel, widthDp: Int, heightDp: Int, fontScale: Float) {
        compose.showFloor(fontScale = fontScale) {
            measurer = rememberTextMeasurer()
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
        val out = File("build/screen-renders/w1d").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    /**
     * Leg extension planned at [weightLb] × 10 with nothing logged today: the prepare phase, Last
     * cell alone. No history unless [lastTimeLb] names last time's one set (× 5), in a finished
     * session before this one.
     */
    private fun openLegExtension(weightLb: Double = 70.0, lastTimeLb: Double? = null): ActiveWorkoutViewModel {
        val sessionId = runBlocking {
            val seed: suspend (List<TestSetInput>, Boolean) -> String = { sets, finish ->
                seedTestWorkout(
                    deps = deps,
                    exerciseId = "leg-extension",
                    exerciseName = "Leg Extension",
                    routineName = "Lower B",
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = WeightConverter.lbsToKg(weightLb),
                    restSeconds = 120,
                    loggedSets = sets,
                    finish = finish,
                ).session.id
            }
            if (lastTimeLb != null) seed(listOf(TestSetInput(weightKg = WeightConverter.lbsToKg(lastTimeLb), reps = 5)), true)
            seed(emptyList(), false)
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
