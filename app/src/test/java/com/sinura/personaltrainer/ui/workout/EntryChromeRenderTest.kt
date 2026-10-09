package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect as EntryReachRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.BodyHeatCopy
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.StartOptionsCopy
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.history.HistoryViewModel
import com.sinura.personaltrainer.ui.progress.BodyTags
import com.sinura.personaltrainer.ui.progress.ProgressScreen
import com.sinura.personaltrainer.ui.progress.ProgressViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Shared StartSheetOpener dependency evidence on actual Body and History screens,
 * with their real ViewModels over isolated synthetic Room/preferences. Neither main
 * screen uses ScreenHeader Back. Body's Front/Back controls rotate its illustration.
 * No navigation, selection, logging or stored preference change is induced by reach.
 * Native frames and text ink establish visibility separately from full semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class EntryChromeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private val runId = UUID.randomUUID().toString()
    private val today = LocalDate.of(2026, 10, 9).toEpochDay()
    private lateinit var deps: FakeAppDependencies
    private lateinit var body: ProgressViewModel
    private lateinit var history: HistoryViewModel
    private var screen by mutableStateOf(Screen.BODY)
    private var profile = "entry-chrome"
    private var font = 1f
    private var direction = LayoutDirection.Ltr
    private var navigation = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        try {
            runBlocking { models.forEach { it.clearAndJoinForTest() } }
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowLargestText() = matrix("320x640-font20", 2f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallDefaultText() = matrix("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallLargeText() = matrix("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallLargestText() = matrix("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardLargestText() = matrix("412x840-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardDefaultText() = matrix("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardLargeText() = matrix("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeDefaultText() = matrix("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeLargeText() = matrix("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeLargestText() = matrix("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = matrix("600x960-font20", 2f)
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextAndReducedMotion() = matrix("360x640-rtl-font20", 2f, LayoutDirection.Rtl)

    private fun matrix(name: String, scale: Float, layout: LayoutDirection = LayoutDirection.Ltr) {
        profile = name
        font = scale
        direction = layout
        graph()
        val stored = inventory()
        show()
        val failures = mutableListOf<Throwable>()
        for (target in Screen.entries) {
            screen = target
            drainLayout()
            checkStage("loaded", failures) {
                awaitScreen()
                capture("initial")
            }
            checkStage("title", failures) {
                val title = compose.onNodeWithText(target.title, useUnmergedTree = true)
                assertWords(title, target.title)
                assertEquals("a main title that fits the viewport stays a whole word", 1, title.textLayout().lineCount)
                assertTrue("main title is an accessible heading", title.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
            }
            checkStage("opener", failures) {
                val tag = if (target == Screen.BODY) BodyTags.START_SHEET else HistoryTags.START_SHEET
                val opener = reachTag(tag).assertIsEnabled()
                assertTarget(opener, Role.Button)
                assertEquals(StartOptionsCopy.OPEN_SPOKEN,
                    opener.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
                assertWords(wordsInside(tag, StartOptionsCopy.OPEN), StartOptionsCopy.OPEN)
                val heading = fullBounds(compose.onNodeWithText(target.title, useUnmergedTree = true).fetchSemanticsNode())
                assertFalse("main title and real opener do not overlap", heading.overlaps(fullBounds(opener.fetchSemanticsNode())))
                capture("full-opener")
            }
            checkStage("window-controls", failures) {
                if (target == Screen.BODY) assertBodyWindow() else assertHistoryHorizon()
                capture("window-controls")
            }
            checkStage("content-reach", failures) {
                if (target == Screen.BODY) assertBodyContent() else assertHistoryContent()
            }
            checkStage("read-only", failures) {
                assertEquals("layout/render/reach leave synthetic durable rows and preferences unchanged", stored, inventory())
                assertEquals("layout/render/reach cause no navigation", 0, navigation)
                if (target == Screen.BODY) assertEquals(HeatWindow.CURRENT_WEEK, body.uiState.value.window)
                else assertEquals(AnalyticsHorizon.MONTH, history.uiState.value.horizon)
            }
        }
        if (failures.isNotEmpty()) {
            val report = AssertionError("$profile actual entry chrome failed: ${failures.joinToString(" | ") { it.message.orEmpty() }}", failures.first())
            failures.drop(1).forEach(report::addSuppressed)
            throw report
        }
    }

    private fun graph() {
        val frozen = FrozenTime(LocalDate.ofEpochDay(today).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), "UTC")
        val snapshot = MuscleLoadCalculator.snapshot(
            sessions = emptyList(), window = HeatWindow.CURRENT_WEEK,
            nowMs = frozen.nowMillis(), time = frozen,
        )
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            insights = MutableStateFlow(TrainingInsights(snapshot = snapshot)),
            scheduler = dispatcher, time = frozen,
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            deps.preferencesRepository.setWeightUnit(WeightUnit.KG)
            deps.preferencesRepository.setHeatWindow(HeatWindow.CURRENT_WEEK)
        }
        assertEquals("repository clock matches displayed civil date", today, deps.time.civilDate(deps.time.nowMillis()).epochDay)
        body = ProgressViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
        history = HistoryViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
    }

    private fun show() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, font),
                LocalLayoutDirection provides direction,
                LocalTodayEpochDay provides today,
                LocalWeightUnit provides WeightUnit.KG,
            ) {
                PersonalTrainerTheme(reduceMotion = true) {
                    key(screen) {
                        when (screen) {
                            Screen.BODY -> ProgressScreen(
                                onOpenLibrary = { navigation++ }, onOpenExercise = { navigation++ },
                                onOpenRoutines = { navigation++ }, onOpenStartSheet = { navigation++ }, viewModel = body,
                            )
                            Screen.HISTORY -> HistoryScreen(
                                onOpenSession = { navigation++ }, onOpenExercise = { navigation++ },
                                onOpenActiveSession = { navigation++ }, onOpenActivity = { navigation++ },
                                onOpenStartSheet = { navigation++ }, viewModel = history,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun awaitScreen() {
        compose.awaitThat("actual $screen loaded data", {
            if (screen == Screen.BODY) body.uiState.value else history.uiState.value
        }) {
            if (screen == Screen.BODY) !body.uiState.value.isLoading && body.uiState.value.snapshot != null
            else !history.uiState.value.isLoading
        }
        if (screen == Screen.BODY) assertNull(body.uiState.value.error)
        else {
            assertFalse("History read is available", history.uiState.value.unavailable)
            assertFalse("History read is current", history.uiState.value.stale)
            assertEquals(today, history.uiState.value.today.epochDay)
        }
        drainLayout()
    }

    private fun assertBodyWindow() {
        for (window in HeatWindow.entries) {
            val tag = BodyTags.window(window)
            val chip = reachTag(tag).assertIsEnabled()
            assertTarget(chip, Role.RadioButton)
            assertEquals(window == HeatWindow.CURRENT_WEEK, chip.fetchSemanticsNode().config[SemanticsProperties.Selected])
            assertWords(wordsInside(tag, window.shortLabel), window.shortLabel)
        }
    }

    private fun assertHistoryHorizon() {
        for (horizon in AnalyticsHorizon.entries) {
            val tag = HistoryTags.horizon(horizon)
            val chip = reachTag(tag).assertIsEnabled()
            assertTarget(chip, Role.RadioButton)
            assertEquals(horizon == AnalyticsHorizon.MONTH, chip.fetchSemanticsNode().config[SemanticsProperties.Selected])
            assertWords(wordsInside(tag, horizon.label), horizon.label)
        }
    }

    private fun assertBodyContent() {
        for ((tag, label, selectionState) in listOf(
            Triple(BodyTags.VIEW_FRONT, "Front", true), Triple(BodyTags.VIEW_BACK, "Back", false),
        )) {
            val control = reachTag(tag).assertIsEnabled()
            assertTarget(control, Role.RadioButton)
            assertEquals("illustration selection is preserved", selectionState, control.fetchSemanticsNode().config[SemanticsProperties.Selected])
            assertWords(wordsInside(tag, label), label)
        }
        capture("illustration-controls")
        assertWords(reachTag(BodyTags.EMPTY, unmerged = true), BodyHeatCopy.EMPTY_LOG)
        capture("empty-content")
    }

    private fun assertHistoryContent() {
        val control = reachTag(HistoryTags.CALENDAR_MONTH).assertIsEnabled()
        assertTarget(control)
        assertWords(wordsInside(HistoryTags.CALENDAR_MONTH, HistoryCopy.CALENDAR_MONTH), HistoryCopy.CALENDAR_MONTH)
        capture("calendar-control")
        // Compose the lazy empty-state item, then reveal its meaningful labels
        // separately. Its decorative illustration need not fit with both labels.
        findTag(HistoryTags.EMPTY)
        assertWords(wordsInside(HistoryTags.EMPTY, HistoryCopy.EMPTY_TITLE), HistoryCopy.EMPTY_TITLE)
        assertWords(wordsInside(HistoryTags.EMPTY, HistoryCopy.EMPTY_LOG), HistoryCopy.EMPTY_LOG)
        capture("empty-content")
    }

    private fun wordsInside(tag: String, words: String): SemanticsNodeInteraction =
        compose.onNode(hasText(words) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun assertTarget(interaction: SemanticsNodeInteraction, role: Role? = null) {
        val node = interaction.fetchSemanticsNode()
        val density = node.layoutInfo.density
        assertEquals("real text scale reaches the screen", font, density.fontScale, .001f)
        assertTrue("real action width is at least 48 dp: ${node.config}", node.size.width / density.density >= 48f - .5f)
        assertTrue("real action height is at least 48 dp: ${node.config}", node.size.height / density.density >= 48f - .5f)
        assertTrue("real action has an offered click", node.config.getOrNull(SemanticsActions.OnClick)?.action != null)
        if (role != null) assertEquals(role, node.config.getOrNull(SemanticsProperties.Role))
        assertVisible(interaction)
    }

    private fun assertWords(interaction: SemanticsNodeInteraction, words: String) {
        reach(interaction)
        val node = interaction.fetchSemanticsNode()
        assertTrue("$words has an actual measured text area", node.size.width > 0 && node.size.height > 0)
        val reported = interaction.textLayout()
        val input = reported.layoutInput
        // Compose 1.11 rebuilds simple Text semantics at the offered parent width.
        // Use its exact fonts/input at the actual node width, retaining reported
        // line breaks, and then require native ink within the real clip below.
        val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
            text = input.text, style = input.style, overflow = input.overflow,
            softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
            constraints = input.constraints.copy(minWidth = 0, maxWidth = node.size.width),
        )
        assertEquals("measured-width paragraph retains reported line count", reported.lineCount, measured.lineCount)
        repeat(measured.lineCount) { line ->
            assertEquals("reported line start", reported.getLineStart(line), measured.getLineStart(line))
            assertEquals("reported line end", reported.getLineEnd(line), measured.getLineEnd(line))
        }
        assertEquals("actual copy", words, measured.layoutInput.text.text)
        assertEquals("last character is laid out", words.length, measured.getLineEnd(measured.lineCount - 1, visibleEnd = true))
        val bitmap = drawWindow()
        try {
            val visible = node.boundsInWindow.intersect(EntryReachRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
            val origin = node.positionInWindow
            assertTrue("complete text height fits actual clip: $words", measured.size.height <= visible.height + 1f)
            repeat(measured.lineCount) { line ->
                assertFalse("$words line $line has no ellipsis", measured.isLineEllipsized(line))
                val glyphs = EntryReachRect(origin.x + measured.getLineLeft(line), origin.y + measured.getLineTop(line),
                    origin.x + measured.getLineRight(line), origin.y + measured.getLineBottom(line))
                assertTrue("full line fits actual clip: $words / $glyphs / $visible",
                    glyphs.left >= visible.left - 1f && glyphs.right <= visible.right + 1f &&
                        glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                assertTrue("native text ink exists on each line: $words", bitmap.count(glyphs.intersect(visible), input.style.color) > 0)
            }
            val last = measured.getBoundingBox(words.lastIndex)
            val finalGlyph = EntryReachRect(origin.x + last.left, origin.y + last.top, origin.x + last.right, origin.y + last.bottom)
            assertTrue("native final glyph is visible: $words", bitmap.count(finalGlyph.intersect(visible), input.style.color) > 0)
        } finally { bitmap.recycle() }
    }

    private fun reachTag(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction =
        reach(findTag(tag, unmerged))

    /** Lazy content is discovered by bounded scrolling of its real screen list. */
    private fun findTag(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction {
        drainLayout()
        val target = compose.onNodeWithTag(tag, useUnmergedTree = unmerged)
        if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return target
        val boards = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        assertEquals("one real screen lazy content list", 1, boards.size)
        val board = boards.single()
        compose.runOnUiThread {
            checkNotNull(board.config[SemanticsActions.ScrollToIndex].action)(0)
        }
        drainLayout()
        repeat(30) {
            if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return target
            val current = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().single()
            val range = current.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("$tag appears before the real content list end", range.value() < range.maxValue())
            val viewport = current.boundsInWindow.intersect(windowBounds())
            assertTrue("actual pinned chrome leaves a nonzero content viewport for $tag: $viewport", viewport.height > 1f)
            compose.runOnUiThread { checkNotNull(current.config[SemanticsActions.ScrollBy].action)(0f, viewport.height * .75f) }
            drainLayout()
        }
        throw AssertionError("$profile $screen cannot compose $tag in 30 bounded real scrolls")
    }

    /** Reveal through actual scroll ancestors; pinned chrome cannot be manufactured into a scroll. */
    private fun reach(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        repeat(20) {
            val node = interaction.fetchSemanticsNode()
            val window = windowBounds()
            val full = fullBounds(node)
            val clipped = node.boundsInWindow.intersect(window)
            if (clipped.width >= node.size.width - 1f && clipped.height >= node.size.height - 1f) return interaction.assertIsDisplayed()
            var ancestor = node.parent
            var moved = false
            while (ancestor != null && !moved) {
                val current = ancestor
                val action = current.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (action != null) {
                    val viewport = current.boundsInWindow.intersect(window)
                    for ((horizontal, range) in listOf(
                        true to current.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange),
                        false to current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange),
                    )) {
                        if (range == null) continue
                        val low = if (horizontal) full.left else full.top
                        val high = if (horizontal) full.right else full.bottom
                        val start = if (horizontal) viewport.left else viewport.top
                        val end = if (horizontal) viewport.right else viewport.bottom
                        val delta = when { low < start - 1f -> low - start; high > end + 1f -> high - end; else -> 0f }
                        val reversed = range.reverseScrolling xor (horizontal && node.layoutInfo.layoutDirection == LayoutDirection.Rtl)
                        val amount = if (reversed) -delta else delta
                        if (amount < -1f && range.value() > 0f || amount > 1f && range.value() < range.maxValue()) {
                            moved = compose.runOnUiThread { action(if (horizontal) amount else 0f, if (horizontal) 0f else amount) }
                            if (moved) { drainLayout(); break }
                        }
                    }
                }
                ancestor = current.parent
            }
            if (!moved) throw AssertionError("$profile $screen cannot fully reveal $full clipped to $clipped: ${node.config}")
        }
        throw AssertionError("$profile $screen real scroll ancestors could not fully reveal target")
    }

    private fun assertVisible(interaction: SemanticsNodeInteraction) {
        val node = interaction.fetchSemanticsNode()
        val visible = node.boundsInWindow.intersect(windowBounds())
        assertTrue("actual complete control is visible: ${node.config} / $visible / ${node.size}",
            visible.width >= node.size.width - 1f && visible.height >= node.size.height - 1f)
        interaction.assertIsDisplayed()
    }

    private fun fullBounds(node: SemanticsNode): EntryReachRect {
        val origin = node.positionInWindow
        return EntryReachRect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    private fun windowBounds(): EntryReachRect = compose.runOnUiThread {
        val view = compose.activity.window.decorView
        EntryReachRect(0f, 0f, view.width.toFloat(), view.height.toFloat())
    }

    /** Drain actual Compose 1.11 RootForTest traversal, with the normal Floor frame bound. */
    private fun drainLayout() {
        repeat(3) {
            compose.settle()
            val pending = compose.runOnUiThread {
                val roots = mutableListOf<View>()
                fun visit(view: View) {
                    if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") roots += view
                    if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(compose.activity.window.decorView)
                check(roots.isNotEmpty())
                roots.forEach { root -> root.javaClass.methods.single { it.name == "measureAndLayoutForTest" && it.parameterCount == 0 }.invoke(root) }
                roots.any { root -> root.javaClass.methods.single { it.name == "getHasPendingMeasureOrLayout" && it.parameterCount == 0 }.invoke(root) as Boolean }
            }
            if (!pending) return
        }
        throw AssertionError("$profile real Compose roots still have pending layout after 60 explicit frames")
    }

    private fun drawWindow(): Bitmap = compose.runOnUiThread {
        val view = compose.activity.window.decorView
        check(view.width > 1 && view.height > 1)
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun checkStage(stage: String, failures: MutableList<Throwable>, assertion: () -> Unit) {
        try { assertion() } catch (failure: Throwable) {
            val detail = AssertionError("$screen $stage: ${failure.message}", failure)
            // Preserve the current native window even if the failed condition was
            // pending measure work; never substitute generated pixels or rectangles.
            runCatching { capture("failed-$stage", failure.stackTraceToString()) }.exceptionOrNull()?.let(detail::addSuppressed)
            failures += detail
        }
    }

    private fun capture(stage: String, failure: String? = null) {
        if (failure == null) drainLayout()
        val bitmap = drawWindow()
        try {
            val directory = File("build/screen-renders/home-week-geometry-entry/$runId/$profile/${screen.name.lowercase()}")
            check(directory.isDirectory || directory.mkdirs())
            val png = directory.resolve("$stage.png")
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("fresh native screen frame saved", png.length() > 1_000)
            val nodes = compose.onAllNodes(SemanticsMatcher("all actual semantics") { true }, useUnmergedTree = true)
                .fetchSemanticsNodes().joinToString("\n") { "${it.config} size=${it.size} position=${it.positionInWindow} clip=${it.boundsInWindow}" }
            directory.resolve("$stage.txt").writeText(
                "screen=$screen\nviewport=${bitmap.width}x${bitmap.height}\nfont=$font\ndirection=$direction\ntoday=${LocalDate.ofEpochDay(today)}\n" +
                    "bodyWindow=${body.uiState.value.window}\nhistoryHorizon=${history.uiState.value.horizon}\nnavigation=$navigation\n" +
                    (failure?.let { "failure=$it\n" } ?: "") + nodes,
            )
        } finally { bitmap.recycle() }
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.activityDao().getAllGraphs(),
            deps.database.plannerDao().getRules(), deps.database.plannerDao().getAllOccurrences(),
            deps.database.plannerDao().getDecisions(), deps.database.plannerDao().getDeliveries(), deps.database.scheduleDao().getAll(),
            deps.database.routineDao().getAllRoutines(), deps.database.routineDao().getAllRoutineExercises(),
            deps.database.bodyweightDao().getAll(), deps.rawPreferenceValues(), deps.pendingOccurrenceId.value,
            deps.restTimerStore.current(),
        )
    }

    private enum class Screen(val title: String) { BODY("Body"), HISTORY("History") }
}
