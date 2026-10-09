package com.sinura.personaltrainer.ui.plan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as PickerReachRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.count
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual Plan day and its ViewModel over isolated Room: browse, restore, cancel, no writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class PlanPickerHeaderRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val runId = UUID.randomUUID().toString()
    private val today = LocalDate.of(2026, 12, 31).toEpochDay()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: PlanDayViewModel
    private var navigation = 0
    private var profile = "interaction"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        try {
            if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() }
        } finally {
            if (::deps.isInitialized) {
                deps.restTimerController.stop()
                dispatcher.scheduler.advanceUntilIdle()
                deps.close()
            }
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1f)
    fun narrowFont10() = journey("320x640-font10", 1f)
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.6f)
    fun narrowFont16() = journey("320x640-font16", 1.6f)
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowFont20() = journey("320x640-font20", 2f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = journey("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = journey("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = journey("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardFont10() = journey("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardFont16() = journey("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardFont20() = journey("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = journey("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = journey("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = journey("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = journey("600x960-font20", 2f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlRestoresSelectedEquipmentAndCancelsWithoutWrites() =
        journey("360x640-font20-rtl-reduced-motion", 2f, rtl = true, restore = true)

    private fun journey(name: String, font: Float, rtl: Boolean = false, restore: Boolean = false) {
        profile = name
        assertEquals(font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, .001f)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            time = FrozenTime(Instant.parse("2026-12-31T12:00:00Z").toEpochMilli(), "UTC"),
        )
        vm = PlanDayViewModel(ApplicationProvider.getApplicationContext(), deps)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(
                LocalTodayEpochDay provides today,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    Surface(color = Pit) {
                        Box(Modifier.fillMaxSize()) {
                            PlanDayScreen(
                                epochDay = today,
                                startInAdd = true,
                                onBack = { navigation++ },
                                onOpenRoutine = { navigation++ },
                                viewModel = vm,
                            )
                        }
                    }
                }
            }
        }
        compose.awaitThat("real Plan feed is loaded", vm.uiState::value) { !vm.uiState.value.isLoading }
        compose.settle()
        val before = inventory()
        proveHeader(PlanDayCopy.PICK_KIND, "kind")
        tap(compose.onNodeWithText(PlanDayCopy.AUXILIARY))
        proveHeader(ExtraEquipment.PICK, "equipment")
        tap(compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.FREE_WEIGHTS)))
        proveHeader(PlanDayCopy.PICK_AUX, "packs")
        if (restore) {
            restoration.emulateSavedInstanceStateRestore()
            compose.settle()
            proveHeader(PlanDayCopy.PICK_AUX, "packs-restored")
            assertEquals(before, inventory())
        }
        tap(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
        proveHeader(ExtraEquipment.PICK, "equipment-return")
        tap(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
        reachable(compose.onNodeWithTag(PlanDayTags.ADD)).assertIsEnabled()
        compose.onNodeWithTag(PickerHeaderTags.TITLE).assertDoesNotExist()
        for ((choice, question) in listOf(
            PlanDayCopy.WORKOUT to PlanDayCopy.PICK_WORKOUT,
            PlanDayCopy.CARDIO to PlanDayCopy.PICK_CARDIO,
        )) {
            tap(compose.onNodeWithTag(PlanDayTags.ADD))
            proveHeader(PlanDayCopy.PICK_KIND, "kind-return-$choice")
            tap(compose.onNodeWithText(choice))
            proveHeader(question, "question-$choice")
            tap(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
            reachable(compose.onNodeWithTag(PlanDayTags.ADD)).assertIsEnabled()
        }
        assertEquals("browse/restore/cancel preserves every stored owner", before, inventory())
        assertEquals(0, navigation)
        assertNull(vm.navigateToEditor.value)
        capture("cancelled")
        assertTarget(compose.onNodeWithTag(PlanDayTags.BACK))
        reachable(compose.onNodeWithTag(PlanDayTags.BACK)).performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, center.y)) }
        compose.settle()
        assertEquals("a real pointer tap requests exactly one route exit", 1, navigation)
        assertEquals("Back does not author schedule data", before, inventory())
    }

    private fun proveHeader(title: String, stage: String) {
        val date = DateCopy.weekdayFullDate(LocalDate.ofEpochDay(today))
        proveWords(compose.onNodeWithText(date, useUnmergedTree = true), date, "$stage-fixed-date")
        assertTarget(compose.onNodeWithTag(PlanDayTags.BACK))
        val question = reachable(compose.onNodeWithTag(PickerHeaderTags.TITLE))
        val cancel = reachable(compose.onNodeWithTag(PickerHeaderTags.CANCEL)).assertIsEnabled()
        val titleNode = question.fetchSemanticsNode()
        val cancelNode = cancel.fetchSemanticsNode()
        assertTrue(titleNode.config.getOrNull(SemanticsProperties.Heading) == Unit)
        assertEquals(Role.Button, cancelNode.config.getOrNull(SemanticsProperties.Role))
        assertTarget(cancel)
        val titleBounds = titleNode.boundsInWindow
        val cancelBounds = cancelNode.boundsInWindow
        assertTrue("$profile/$stage question and Cancel have separate real bounds",
            titleBounds.right <= cancelBounds.left + 1f || cancelBounds.right <= titleBounds.left + 1f ||
                titleBounds.bottom <= cancelBounds.top + 1f || cancelBounds.bottom <= titleBounds.top + 1f)
        proveWords(question, title.uppercase(Locale.ENGLISH), stage)
        proveWords(compose.onNodeWithText(PlanDayCopy.CANCEL, useUnmergedTree = true), PlanDayCopy.CANCEL, stage)
        capture(stage)
    }

    private fun proveWords(interaction: SemanticsNodeInteraction, words: String, stage: String) {
        val node = reachable(interaction).fetchSemanticsNode()
        val layout = interaction.textLayout()
        assertEquals(words, layout.layoutInput.text.text)
        assertEquals(words.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertEquals(RuntimeEnvironment.getApplication().resources.configuration.fontScale, node.layoutInfo.density.fontScale, .001f)
        val frame = drawWindow()
        try {
            val visible = node.boundsInWindow.intersect(PickerReachRect(0f, 0f, frame.width.toFloat(), frame.height.toFloat()))
            repeat(layout.lineCount) { line ->
                assertFalse("$profile/$stage full line $line", layout.isLineEllipsized(line))
                val glyphs = PickerReachRect(
                    node.positionInWindow.x + layout.getLineLeft(line), node.positionInWindow.y + layout.getLineTop(line),
                    node.positionInWindow.x + layout.getLineRight(line), node.positionInWindow.y + layout.getLineBottom(line),
                )
                assertTrue("$profile/$stage complete glyph bounds $glyphs in $visible",
                    glyphs.left >= visible.left - 1f && glyphs.right <= visible.right + 1f &&
                        glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                assertTrue("$profile/$stage real glyph ink for line $line", frame.count(glyphs.intersect(visible), TextSecondary) >= 5)
            }
        } finally { frame.recycle() }
    }

    private fun tap(interaction: SemanticsNodeInteraction) {
        val target = reachable(interaction).assertIsEnabled()
        target.performTouchInput { click(center) }
        compose.settle()
    }

    private fun assertTarget(interaction: SemanticsNodeInteraction) {
        val node = reachable(interaction).fetchSemanticsNode()
        val min = Metrics.touchMin.value * node.layoutInfo.density.density
        if (node.boundsInWindow.width < min - 1f || node.boundsInWindow.height < min - 1f) capture("small-target-${node.id}")
        assertTrue("full width >=48dp: ${node.boundsInWindow}, size=${node.size}, touch=${node.touchBoundsInRoot}", node.boundsInWindow.width >= min - 1f)
        assertTrue("full height >=48dp: ${node.boundsInWindow}, size=${node.size}, touch=${node.touchBoundsInRoot}", node.boundsInWindow.height >= min - 1f)
    }

    /** Reach a fully visible native target through actual bounded ancestor scrolls. */
    private fun reachable(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        compose.settle()
        repeat(25) {
            val node = interaction.fetchSemanticsNode()
            val decor = compose.activity.window.decorView
            val window = PickerReachRect(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
            val visible = node.boundsInWindow.intersect(window)
            if (visible.width >= node.size.width - 1f && visible.height >= node.size.height - 1f) {
                return interaction.assertIsDisplayed()
            }
            val full = PickerReachRect(node.positionInWindow.x, node.positionInWindow.y,
                node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
            var parent = node.parent
            var moved = false
            while (parent != null) {
                val candidate = parent
                val axis = candidate.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                val scroll = candidate.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (axis != null && scroll != null) {
                    val viewport = candidate.boundsInWindow.intersect(window)
                    val delta = when {
                        full.top < viewport.top - 1f -> full.top - viewport.top
                        full.bottom > viewport.bottom + 1f -> full.bottom - viewport.bottom
                        else -> 0f
                    }
                    val amount = if (axis.reverseScrolling) -delta else delta
                    if (amount < -1f && axis.value() > 0f || amount > 1f && axis.value() < axis.maxValue()) {
                        moved = compose.runOnUiThread { scroll(0f, amount) }
                        if (moved) { compose.settle(); break }
                    }
                }
                parent = candidate.parent
            }
            if (!moved) {
                capture("unreachable-${node.id}")
                throw AssertionError("$profile cannot reach full target $full clipped $visible in $window")
            }
        }
        throw AssertionError("$profile exceeded 25 measured scrolls")
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(
            deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.bodyweightDao().getAll(),
            deps.database.plannerDao().getAllOccurrences(), deps.database.plannerDao().getRules(),
            deps.database.plannerDao().getDecisions(), deps.database.plannerDao().getDeliveries(),
            deps.database.scheduleDao().getAll(),
            deps.database.routineDao().getAllRoutines(), deps.database.routineDao().getAllRoutineExercises(),
            deps.database.activityDao().getAllGraphs(), deps.rawPreferenceValues(), deps.pendingOccurrenceId.value,
            deps.restTimerStore.current(),
        )
    }

    private fun drawWindow(): Bitmap = compose.runOnUiThread {
        val decor = compose.activity.window.decorView
        check(decor.width > 1 && decor.height > 1)
        Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
    }

    private fun capture(stage: String) {
        val frame = drawWindow()
        try {
            val file = File("build/screen-renders/plan-picker-geometry/$runId/$profile/$stage.png")
            file.parentFile.mkdirs()
            file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { frame.recycle() }
    }
}
