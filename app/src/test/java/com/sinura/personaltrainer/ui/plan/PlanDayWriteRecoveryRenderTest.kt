package com.sinura.personaltrainer.ui.plan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual picker → held write → failure → restored Retry → durable day, with captured history. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class PlanDayWriteRecoveryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val faults = PlanWriteFaults()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: PlanDayViewModel
    private val runId = UUID.randomUUID().toString()
    private val today = LocalDate.of(2026, 12, 31).toEpochDay()
    private var profile = "interaction"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        try { if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() } }
        finally {
            if (::deps.isInitialized) deps.close()
            Dispatchers.resetMain()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = journey("360x640-font10")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = journey("360x640-font16")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = journey("360x640-font20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardFont10() = journey("412x840-font10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardFont16() = journey("412x840-font16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardFont20() = journey("412x840-font20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = journey("640x360-font10")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = journey("640x360-font16")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = journey("640x360-font20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun tabletFont10() = journey("600x960-font10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletFont20() = journey("600x960-font20")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlReducedMotionRestoresHeldAndFailedChoice() = journey("360x640-font20-rtl", rtl = true)

    private fun journey(name: String, rtl: Boolean = false) {
        profile = name
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            time = FrozenTime(Instant.parse("2026-12-31T12:00:00Z").toEpochMilli(), "UTC"),
            plannerDaoDecorator = faults::decorate,
        )
        val fixture = runBlocking {
            seedTestWorkout(deps, routineName = "Upper strength and mobility", finish = true,
                loggedSets = listOf(TestSetInput(72.5, 9, 8)), notes = "Keep this captured result")
        }
        val historyBefore = history()
        vm = PlanDayViewModel(ApplicationProvider.getApplicationContext(), deps)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(
                LocalTodayEpochDay provides today,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    PlanDayScreen(epochDay = today, startInAdd = true, onBack = {}, onOpenRoutine = {}, viewModel = vm)
                }
            }
        }
        compose.awaitThat("actual Plan loaded", vm.uiState::value) { !vm.uiState.value.isLoading }
        compose.settle()
        val root = compose.onRoot().fetchSemanticsNode()
        assertEquals(RuntimeEnvironment.getApplication().resources.configuration.fontScale, root.layoutInfo.density.fontScale, .001f)
        tap(compose.onNodeWithText(PlanDayCopy.WORKOUT))
        faults.holdPublication()
        tap(compose.onNodeWithText(fixture.routine.name))
        compose.awaitThat("accepted update is visibly held", vm.uiState::value) { vm.uiState.value.isSaving }
        compose.settle()
        compose.onNodeWithTag(PlanDayTags.SAVING).assertIsDisplayed()
        words(PlanDayCopy.UPDATING)
        compose.onNodeWithTag(PlanDayTags.BACK).assertIsNotEnabled()
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).assertIsNotEnabled()
        compose.onNodeWithText(PlanDayCopy.NEW_WORKOUT).assertIsNotEnabled()
        assertEquals(0, compose.onAllNodesWithTag(PlanDayTags.ADD).fetchSemanticsNodes().size)
        capture("saving")
        if (rtl) {
            restoration.emulateSavedInstanceStateRestore()
            compose.settle()
            compose.onNodeWithTag(PlanDayTags.SAVING).assertIsDisplayed()
            compose.onNodeWithTag(PickerHeaderTags.CANCEL).assertIsNotEnabled()
        }
        faults.rejectPublication = true
        faults.releasePublication()
        compose.awaitThat("failed update remains retryable", vm.uiState::value) { vm.uiState.value.canRetryWrite }
        compose.settle()
        words(checkNotNull(vm.uiState.value.error))
        words(PlanDayCopy.RETRY_SCOPE)
        target(compose.onNodeWithTag(PlanDayTags.RETRY))
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).assertIsNotEnabled()
        compose.onNodeWithTag(PlanDayTags.BACK).assertIsEnabled()
        assertEquals(0L, vm.uiState.value.completedAdd)
        assertEquals(historyBefore, history())
        val acceptedSlots = runBlocking { deps.scheduleRepository.slots() }
        assertEquals(1, acceptedSlots.size)
        assertEquals(fixture.routine.id, acceptedSlots.single().routineId)
        capture("failed-retry")
        if (rtl) {
            restoration.emulateSavedInstanceStateRestore()
            compose.settle()
            target(compose.onNodeWithTag(PlanDayTags.RETRY))
        }
        faults.rejectPublication = false
        tap(compose.onNodeWithTag(PlanDayTags.RETRY))
        compose.awaitThat("exact accepted update completed", vm.uiState::value) {
            vm.uiState.value.completedAdd == 1L && !vm.uiState.value.isSaving && vm.agendaFor(today).size == 1
        }
        compose.settle()
        assertEquals(0, compose.onAllNodesWithTag(PickerHeaderTags.CANCEL).fetchSemanticsNodes().size)
        target(compose.onNodeWithTag(PlanDayTags.ADD))
        assertEquals(acceptedSlots, runBlocking { deps.scheduleRepository.slots() })
        assertEquals(fixture.routine.id, vm.agendaFor(today).single().rule?.routineId)
        assertEquals(1, runBlocking { deps.database.plannerDao().getRules().size })
        assertEquals(historyBefore, history())
        capture("completed-day")
        tap(compose.onNodeWithTag(PlanDayTags.ADD))
        tap(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
        assertEquals(acceptedSlots, runBlocking { deps.scheduleRepository.slots() })
        assertEquals(historyBefore, history())
    }

    private fun tap(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed().performTouchInput { click(center) }
        compose.settle()
    }
    private fun target(node: SemanticsNodeInteraction) {
        val semantics = node.assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        val floor = Metrics.touchMin.value * semantics.layoutInfo.density.density
        assertTrue("$profile target width", semantics.boundsInWindow.width >= floor - 1f)
        assertTrue("$profile target height", semantics.boundsInWindow.height >= floor - 1f)
    }
    private fun words(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val semantics = node.fetchSemanticsNode()
        val layout = node.textLayout()
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("$profile clipped $text", glyph.left >= -1f && glyph.right <= semantics.size.width + 1f &&
                glyph.top >= -1f && glyph.bottom <= semantics.size.height + 1f)
        }
    }
    private fun history(): List<Any?> = runBlocking {
        listOf(deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets())
    }
    private fun capture(stage: String) {
        val view = compose.activity.window.decorView
        val frame = compose.runOnUiThread {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/plan-write-recovery/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { frame.recycle() }
    }
}
