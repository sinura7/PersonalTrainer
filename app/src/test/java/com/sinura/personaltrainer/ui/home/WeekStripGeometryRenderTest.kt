package com.sinura.personaltrainer.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as WeekReachRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.StartOptionsCopy
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekBoardCell
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeeklySchedulePlan
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.components.WeekStripTags
import com.sinura.personaltrainer.ui.plan.ExtraEquipmentTags
import com.sinura.personaltrainer.ui.plan.PickerHeaderTags
import com.sinura.personaltrainer.ui.plan.PlanScreen
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.plan.PlanViewModel
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.count
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Actual Home/Plan and their ViewModels on synthetic Room. The fixed civil clock is
 * passed to FakeAppDependencies, including PlannerRepository, rather than just display.
 * Frames are real native window draws; assertions use the actual clipped semantics,
 * TextLayoutResults and glyph ink. No semantics click is used to select a hidden day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class WeekStripGeometryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<ViewModel>()
    private val insights = MutableStateFlow(TrainingInsights())
    private val requiredRead = RequiredRead()
    private val runId = UUID.randomUUID().toString()
    private lateinit var deps: FakeAppDependencies
    private lateinit var plan: PlanViewModel
    private var home by mutableStateOf<HomeViewModel?>(null)
    private var screen by mutableStateOf(Screen.HOME)
    private var scale by mutableFloatStateOf(1f)
    private var densityFactor by mutableFloatStateOf(1f)
    private var constrainedWidth by mutableStateOf<Int?>(null)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private var reducedMotion by mutableStateOf(false)
    private var profile = "interaction"
    private var today = LocalDate.of(2026, 12, 31).toEpochDay()
    private var weekStart = LocalDate.of(2026, 12, 28).toEpochDay()
    private var navigation = 0
    private var componentSelected by mutableStateOf(0L)
    private var componentProposals = emptyMap<Long, SuggestedTrainingDay>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        requiredRead.held?.complete(Unit)
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

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1f)
    fun narrowFont10() = matrix("320x640-font10", 1f)
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.6f)
    fun narrowFont16() = matrix("320x640-font16", 1.6f)
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun narrowFont20() = matrix("320x640-font20", 2f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun smallFont10() = matrix("360x640-font10", 1f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun smallFont16() = matrix("360x640-font16", 1.6f)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun smallFont20() = matrix("360x640-font20", 2f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standardFont10() = matrix("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standardFont16() = matrix("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standardFont20() = matrix("412x840-font20", 2f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscapeFont10() = matrix("800x360-font10", 1f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscapeFont16() = matrix("800x360-font16", 1.6f)
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscapeFont20() = matrix("800x360-font20", 2f)
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletLargestText() = matrix("600x960-font20", 2f)
    @Test @Config(qualifiers = "ldrtl-w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlLargestTextAndReducedMotion() {
        direction = LayoutDirection.Rtl
        reducedMotion = true
        matrix("360x640-rtl-font20-reduced-motion", 2f)
    }

    private fun matrix(name: String, font: Float) {
        profile = name
        scale = font
        graph()
        show()
        for (targetScreen in listOf(Screen.HOME, Screen.PLAN)) {
            changeScreen(targetScreen)
            awaitScreen()
            val stored = inventory()
            if (targetScreen == Screen.PLAN) assertPlanHeader()
            // Home and Plan default to the actual injected civil day. The initial
            // selected cell is revealed before any test scrolling takes place.
            boardTo(WeekStripTags.STRIP)
            assertFullyVisible(cell(today))
            assertTrue(cell(today).fetchSemanticsNode().config[SemanticsProperties.Selected])
            val widths = mutableListOf<Int>()
            for (offset in 0L..6L) {
                val day = weekStart + offset
                val reached = boardTo(WeekStripTags.cell(day))
                val node = reached.fetchSemanticsNode()
                touchTarget(node)
                widths += node.size.width
                assertNeighborsSeparated(day)
                assertCellWords(day)
                assertSpeech(day, selected = day == today)
                capture("${targetScreen.name.lowercase()}-day-$offset")
            }
            assertEquals("same measured width for every civil day", 1, widths.distinct().size)
            assertEquals("scrolling performs no durable writes or starts", stored, inventory())

            // Native pointer dispatch at the measured centre and at each inside edge
            // must select exactly this civil date, including across the year boundary.
            for ((index, offset) in listOf(0L, 6L, 3L, 4L, 2L).withIndex()) {
                val day = weekStart + offset
                tapDay(day, when (index % 3) { 1 -> Edge.LEFT; 2 -> Edge.RIGHT; else -> Edge.CENTRE })
                assertSelected(day)
                assertSpeech(day, selected = true)
                assertDateHeading(day)
                assertEquals("day selection cannot write or start an activity", stored, inventory())
            }
            assertEquals(0, navigation)
            capture("${targetScreen.name.lowercase()}-selected-heading")
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun sameGeometryHomeRestoresSelectionAndExploredOffsetThroughStaleRetryAndFreshLoading() {
        profile = "home-restoration-font20"
        scale = 2f
        graph()
        val restoration = StateRestorationTester(compose)
        show(restoration)
        awaitScreen()
        tapDay(weekStart)
        // Explore to Sunday without selecting it: the saved selection is now offscreen.
        boardTo(WeekStripTags.cell(weekStart + 6))
        val explored = stripOffset()
        assertTrue("manual exploration actually scrolls", explored > 0f)
        val stored = inventory()
        restoration.emulateSavedInstanceStateRestore()
        drainLayout()
        assertEquals("recreation retains exploration, rather than re-revealing selection", explored, stripOffset(), 1f)
        assertSelected(weekStart)

        // An unrelated insights read updates the real ViewModel while geometry is unchanged.
        insights.value = insights.value.copy(hints = emptyList(), weekPlan = restWeek(weekStart).copy(summary = "Updated synthetic read"))
        drainLayout()
        assertEquals("unrelated reads retain the explored offset", explored, stripOffset(), 1f)
        requiredRead.fail.value = true
        compose.awaitThat("actual Home retains stale data", { home!!.uiState.value }) { home!!.uiState.value.readState == HomeReadState.STALE }
        // Retry is deliberately held at the real DAO's next collection.
        requiredRead.fail.value = false
        requiredRead.held = CompletableDeferred()
        boardTo(HomeTags.RETRY).performClick()
        compose.awaitThat("actual held Home retry", { home!!.uiState.value }) { home!!.uiState.value.retryPending }
        restoration.emulateSavedInstanceStateRestore()
        requiredRead.held!!.complete(Unit)
        awaitScreen()
        boardTo(WeekStripTags.STRIP)
        assertEquals("stale/retry unmount and restore preserve exploration", explored, stripOffset(), 1f)
        assertSelected(weekStart)

        // Normal cached Home does not re-enter Loading. A fresh real VM with its first
        // required DAO read held exercises the actual route-recreation Loading return.
        requiredRead.held = CompletableDeferred()
        home = HomeViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
        drainLayout()
        assertTrue("fresh real VM has a pending first required read", home!!.uiState.value.isLoading)
        compose.onNodeWithTag(WeekStripTags.STRIP).assertDoesNotExist()
        capture("fresh-vm-first-required-read-loading")
        requiredRead.held!!.complete(Unit)
        awaitScreen()
        boardTo(WeekStripTags.STRIP)
        assertEquals("state allocated before Loading survives re-entry", explored, stripOffset(), 1f)
        assertSelected(weekStart)
        assertEquals("all lifecycle/read transitions are read-only", stored, inventory())
        capture("home-restored-exploration")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun planRestoresExplorationAndKeepsStableWidthsWhenRealCountsAndProposalsChange() {
        profile = "plan-restoration-counts-proposals-font20"
        scale = 2f
        graph()
        screen = Screen.PLAN
        val restoration = StateRestorationTester(compose)
        show(restoration)
        awaitScreen()
        tapDay(weekStart)
        boardTo(WeekStripTags.cell(weekStart + 6))
        val explored = stripOffset()
        val width = cell(weekStart + 6).fetchSemanticsNode().size.width
        restoration.emulateSavedInstanceStateRestore()
        drainLayout()
        assertEquals("actual Plan restores the user's explored offset", explored, stripOffset(), 1f)
        assertSelected(weekStart)
        val sunday = runBlocking { deps.database.plannerDao().getAllOccurrences() }.single { it.id == "geometry-6-2" }
        runBlocking { deps.database.plannerDao().upsertOccurrence(sunday.copy(status = OccurrenceStatus.DONE.name)) }
        compose.awaitThat("real Room completion count reaches Plan", { plan.uiState.value }) {
            plan.uiState.value.occurrences.firstOrNull { it.id == sunday.id }?.status == OccurrenceStatus.DONE
        }
        drainLayout()
        assertEquals("count wrapping does not alter width", width, cell(weekStart + 6).fetchSemanticsNode().size.width)
        assertEquals("count refresh preserves exploration", explored, stripOffset(), 1f)
        val stored = inventory()
        plan.suggestFills()
        compose.awaitThat("real Plan VM proposes remaining days", { plan.uiState.value }) { plan.uiState.value.proposals.isNotEmpty() }
        drainLayout()
        assertEquals("proposal wrapping preserves explored offset", explored, stripOffset(), 1f)
        assertEquals("proposal wrapping does not change the fixed cell width", width, cell(weekStart + 6).fetchSemanticsNode().size.width)
        val proposal = plan.uiState.value.proposals.first { day ->
            plan.uiState.value.occurrences.none { it.localEpochDay == day.epochDay && it.status != OccurrenceStatus.MOVED }
        }
        boardTo(WeekStripTags.cell(proposal.epochDay))
        val caption = "Suggested ${proposal.focusTitle}"
        assertWords(text(WeekStripTags.caption(proposal.epochDay)), caption)
        val spoken = speech(cell(proposal.epochDay).fetchSemanticsNode())
        assertTrue(spoken.contains("suggested", ignoreCase = true))
        assertTrue(spoken.contains(proposal.focusTitle))
        assertFalse("a suggestion is not accepted planned work", spoken.contains("1 planned"))
        assertTrue(spoken.contains(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(proposal.epochDay))))
        assertEquals("preview generation writes nothing", stored, inventory())
        capture("real-plan-proposal")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun changedFontDirectionWeekAndSelectionRevealTheActualSelectedCell() {
        profile = "geometry-changes"
        graph()
        show()
        for (targetScreen in listOf(Screen.HOME, Screen.PLAN)) {
            changeScreen(targetScreen)
            awaitScreen()
            tapDay(weekStart)
            boardTo(WeekStripTags.cell(weekStart + 6))
            val stored = inventory()
            scale = 2f
            drainLayout()
            boardTo(WeekStripTags.STRIP)
            assertFullyVisible(cell(weekStart))
            assertSelected(weekStart)
            boardTo(WeekStripTags.cell(weekStart + 6))
            direction = LayoutDirection.Rtl
            drainLayout()
            assertFullyVisible(cell(weekStart))
            tapDay(weekStart + 6, Edge.RIGHT)
            assertFullyVisible(cell(weekStart + 6))
            assertSelected(weekStart + 6)
            assertEquals("geometry changes and pointer selection do not write", stored, inventory())
            capture("${targetScreen.name.lowercase()}-font-direction-reveal")
            scale = 1f
            direction = LayoutDirection.Ltr
        }
        weekStart += 7
        insights.value = insights.value.copy(weekPlan = restWeek(weekStart))
        drainLayout()
        boardTo(WeekStripTags.STRIP)
        // Today lies outside the new window, so the caller clamps selection to its start.
        assertFullyVisible(cell(weekStart))
        assertSelected(weekStart)
        assertDateHeading(weekStart)
        capture("plan-new-civil-week")
    }

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun changedActualParentWidthAndComposeDensityRevealSelectionOnBothScreens() {
        profile = "actual-parent-constraints-and-density"
        graph()
        show()
        for (targetScreen in listOf(Screen.HOME, Screen.PLAN)) {
            changeScreen(targetScreen)
            awaitScreen()
            tapDay(weekStart)
            boardTo(WeekStripTags.cell(weekStart + 6))
            val before = inventory()
            // This changes real constraints on the mounted actual screen. It does not
            // overwrite a semantics rectangle or pretend the native window changed.
            constrainedWidth = 320
            drainLayout()
            boardTo(WeekStripTags.STRIP)
            val viewport = compose.onNodeWithTag(WeekStripTags.STRIP).fetchSemanticsNode()
            assertTrue("actual narrower parent changed the scroll viewport", viewport.size.width / viewport.layoutInfo.density.density < 320f)
            assertFullyVisible(cell(weekStart))
            boardTo(WeekStripTags.cell(weekStart + 6))
            densityFactor = 1.2f
            drainLayout()
            assertFullyVisible(cell(weekStart))
            assertSelected(weekStart)
            assertEquals("real density and parent layout changes write nothing", before, inventory())
            capture("${targetScreen.name.lowercase()}-parent-width-density")
            constrainedWidth = null
            densityFactor = 1f
            drainLayout()
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun actualHomeExtraHeaderKeepsFullTitleAndSeparatedCancel() {
        profile = "home-extra-header-320-font20"
        scale = 2f
        graph()
        show()
        awaitScreen()
        val stored = inventory()
        tapReached(boardTo(HomeTags.START))
        expandVisibleSheet()
        tapReached(compose.onNodeWithTag(HomeStartTags.EXTRA))
        assertPickerHeader(ExtraEquipment.PICK)
        tapReached(compose.onNodeWithTag(ExtraEquipmentTags.choice(ExtraEquipment.NONE)))
        assertPickerHeader("Extra")
        tapReached(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
        assertPickerHeader(ExtraEquipment.PICK)
        tapReached(compose.onNodeWithTag(PickerHeaderTags.CANCEL))
        reach(compose.onNodeWithTag(HomeStartTags.EXTRA)).assertIsEnabled()
        compose.onNodeWithTag(PickerHeaderTags.TITLE).assertDoesNotExist()
        assertEquals("opening and cancelling the extra picker starts or saves nothing", stored, inventory())
        assertEquals(0, navigation)
        capture("home-extra-cancelled")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 2f)
    fun sharedStripRestProposalIsExplicitlySuggestedAndHasNoAcceptedCounts() {
        // PlanViewModel filters Rest out of suggestFills. This is deliberately only
        // the shared component's public preview contract, not a fabricated VM state.
        profile = "component-only-suggested-rest-320-font20"
        scale = 2f
        graph()
        screen = Screen.COMPONENT
        componentSelected = today
        componentProposals = mapOf(today to restWeek(weekStart).days.single { it.epochDay == today })
        show()
        drainLayout()
        boardTo(WeekStripTags.cell(today))
        assertWords(text(WeekStripTags.caption(today)), "Suggested Rest")
        val spoken = speech(cell(today).fetchSemanticsNode())
        assertTrue(spoken.contains("Suggested Rest", ignoreCase = true))
        assertTrue(spoken.contains("2026"))
        assertFalse(spoken.contains("planned", ignoreCase = true))
        capture("suggested-rest")
    }

    private fun graph() {
        val frozen = FrozenTime(LocalDate.ofEpochDay(today).atTime(6, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), "UTC")
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(), insights = insights, scheduler = dispatcher,
            time = frozen, bodyweightDaoDecorator = requiredRead::decorate,
        )
        runBlocking {
            deps.preferencesRepository.setOnboardingComplete(true)
            val routine = deps.routineRepository.create("Friday")
            val lift = insertTestExercise(deps, "geometry-lift", "Leg Extension")
            deps.routineRepository.addExercise(routine.id, lift, 3, 10, 20.0, 90)
            // Exactly ten owed blocks plus one vacated MOVED row. Direct synthetic
            // occurrence states avoid starting/logging activities as a test side effect.
            val statuses = listOf(
                listOf(OccurrenceStatus.DONE), listOf(OccurrenceStatus.SKIPPED),
                listOf(OccurrenceStatus.MISSED), listOf(OccurrenceStatus.DONE, OccurrenceStatus.PLANNED),
                listOf(OccurrenceStatus.PLANNED), listOf(OccurrenceStatus.MOVED),
                listOf(OccurrenceStatus.DONE, OccurrenceStatus.SKIPPED, OccurrenceStatus.MISSED, OccurrenceStatus.PLANNED),
            )
            statuses.forEachIndexed { offset, values ->
                values.forEachIndexed { index, status ->
                    val day = weekStart + offset
                    val rule = ScheduleRule(
                        id = "geometry-rule-$offset-$index", weekday = Weekday.fromEpochDay(day), hour = 18 + index,
                        minute = 0, modality = if (offset == 1 || index == 1) ScheduleModality.CARDIO else ScheduleModality.STRENGTH,
                        routineId = if (offset == 1 || index == 1) null else routine.id,
                        templateId = if (offset == 2) ScheduleKind.aux("lower-body") else null,
                        createdAtMs = 1L, updatedAtMs = 1L,
                    )
                    deps.database.plannerDao().upsertRule(rule.toEntity())
                    deps.database.plannerDao().upsertOccurrence(ScheduleOccurrence(
                        id = "geometry-$offset-$index", ruleId = rule.id, status = status,
                        captured = CapturedCivilTime(day * 86_400_000L, "UTC", 0, day), hour = rule.hour,
                        minute = 0, createdAtMs = 1L, updatedAtMs = 1L,
                    ).toEntity())
                }
            }
            insights.value = TrainingInsights(
                routines = deps.routineRepository.observeAll().first(), weekPlan = restWeek(weekStart),
                snapshot = BodyHeatSnapshot(HeatWindow.CURRENT_WEEK, 0L, 0L, emptyList(), false, false),
            )
        }
        assertEquals("fixture's clock really reaches the planner", today, deps.time.civilDate(deps.time.nowMillis()).epochDay)
        home = HomeViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
        plan = PlanViewModel(ApplicationProvider.getApplicationContext(), deps).also(models::add)
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * densityFactor, scale), LocalTodayEpochDay provides today,
                LocalWeightUnit provides WeightUnit.KG, LocalLayoutDirection provides direction,
                LocalReducedMotion provides reducedMotion,
            ) {
                PersonalTrainerTheme(reduceMotion = reducedMotion) {
                    Surface(Modifier.fillMaxSize(), color = Pit) {
                        // Surface propagates its full-window minimum. This real host
                        // loosens the child's minimum while keeping finite window maxima.
                        Box(Modifier.fillMaxSize()) {
                            val actualConstraints = constrainedWidth?.let { Modifier.width(it.dp).fillMaxHeight() } ?: Modifier.fillMaxSize()
                            Box(actualConstraints) {
                                key(screen) {
                                    when (screen) {
                                        Screen.HOME -> HomeScreen(onResumeWorkout = { navigation++ }, onOpenPlan = { navigation++ }, viewModel = checkNotNull(home))
                                        Screen.PLAN -> PlanScreen(onOpenRoutine = { navigation++ }, onOpenLibrary = { navigation++ }, onOpenDay = { _, _ -> navigation++ }, viewModel = plan)
                                        Screen.COMPONENT -> WeekStrip(
                                            cells = WeekBoard.forWeek(weekStart, emptyList(), emptyList()), today = today,
                                            selected = componentSelected, onSelectDay = { componentSelected = it }, proposals = componentProposals,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun changeScreen(next: Screen) { screen = next; drainLayout() }

    private fun awaitScreen() {
        compose.awaitThat("actual $screen required data is ready", {
            if (screen == Screen.HOME) home!!.uiState.value else plan.uiState.value
        }) {
            if (screen == Screen.HOME) home!!.uiState.value.readState == HomeReadState.CURRENT && !home!!.uiState.value.retryPending
            else !plan.uiState.value.isLoading
        }
        drainLayout()
    }

    private fun cell(day: Long) = compose.onNodeWithTag(WeekStripTags.cell(day))
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun stripOffset(): Float = compose.onNodeWithTag(WeekStripTags.STRIP).fetchSemanticsNode()
        .config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun tapReached(interaction: SemanticsNodeInteraction) {
        reach(interaction).assertIsEnabled().performTouchInput { click(center) }
        drainLayout()
    }

    private fun expandVisibleSheet() {
        // Material's partially expanded sheet can leave the final kind outside the
        // window even when the inner scroll is at its measured end. Use the sheet's
        // actual offered accessibility action, then dispatch the row's native tap.
        val offered = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).fetchSemanticsNodes()
        if (offered.isEmpty()) return
        assertEquals("one actual expandable Home sheet", 1, offered.size)
        val sheet = offered.single()
        assertTrue("the real sheet surface is currently visible", sheet.boundsInWindow.intersect(windowBounds()).height > 0f)
        assertTrue(compose.runOnUiThread { checkNotNull(sheet.config[SemanticsActions.Expand].action)() })
        drainLayout()
    }

    private fun tapDay(day: Long, edge: Edge = Edge.CENTRE) {
        val reached = boardTo(WeekStripTags.cell(day))
        val size = reached.fetchSemanticsNode().size
        reached.performTouchInput {
            click(Offset(when (edge) { Edge.LEFT -> 1f; Edge.RIGHT -> size.width - 1f; Edge.CENTRE -> size.width / 2f }, size.height / 2f))
        }
        drainLayout()
    }

    private fun assertSelected(day: Long) {
        val nodes = compose.onAllNodes(hasTestTag(WeekStripTags.cell(day))).fetchSemanticsNodes()
        assertEquals(1, nodes.size)
        assertTrue("caller retains exact civil-day selection $day", nodes.single().config[SemanticsProperties.Selected])
        val selected = (0L..6L).map { weekStart + it }.filter { cell(it).fetchSemanticsNode().config[SemanticsProperties.Selected] }
        assertEquals(listOf(day), selected)
    }

    private fun assertDateHeading(day: Long) {
        val tag = if (screen == Screen.HOME) HomeTags.DATE else PlanTags.SELECTED_DATE
        val expected = DateCopy.weekdayFullDate(LocalDate.ofEpochDay(day))
        val title = if (screen == Screen.PLAN && day == today) "Today · $expected" else expected
        assertWords(boardTo(tag, unmerged = true), title.uppercase())
        assertTrue("the full selected year remains visible", expected.contains(LocalDate.ofEpochDay(day).year.toString()))
        if (screen == Screen.HOME) {
            if (day == today) compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertDoesNotExist()
            else compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertExists()
        }
    }

    private fun cells(): List<WeekBoardCell> {
        val state = if (screen == Screen.HOME) home!!.uiState.value.let { it.occurrences to it.rules }
        else plan.uiState.value.let { it.occurrences to it.rules }
        return WeekBoard.forWeek(weekStart, state.first, state.second)
    }

    private fun assertCellWords(day: Long) {
        val cell = cells().single { it.epochDay == day }
        val caption = when (day - weekStart) { 0L, 4L -> "Workout"; 1L -> "Cardio"; 2L -> "Lower-body warm-up"; 3L -> "2 activities"; 5L -> "Rest"; else -> "4 activities" }
        assertEquals("fixture produces exact occurrence caption", caption, cell.caption)
        assertWords(text(WeekStripTags.weekday(day)), Weekday.fromEpochDay(day).shortLabel().uppercase())
        assertWords(text(WeekStripTags.date(day)), LocalDate.ofEpochDay(day).dayOfMonth.toString())
        assertWords(text(WeekStripTags.caption(day)), caption)
        val frame = drawWindow()
        try {
            val target = cell(day).fetchSemanticsNode().boundsInWindow.intersect(windowBounds())
            assertEquals("native Volt mark identifies today independently of selection", day == today, frame.count(target, Volt) > 0)
        } finally { frame.recycle() }
        val counts = when (day - weekStart) {
            0L -> listOf("1 completed"); 1L -> listOf("1 skipped"); 2L -> listOf("1 missed")
            3L -> listOf("1 completed", "1 planned"); 4L -> listOf("1 planned")
            5L -> emptyList(); else -> listOf("1 completed", "1 skipped", "1 missed", "1 planned")
        }
        if (counts.isEmpty()) text(WeekStripTags.status(day)).assertDoesNotExist()
        else assertWords(text(WeekStripTags.status(day)), counts.joinToString(" · "))
        assertEquals("MOVED is a vacated day", day - weekStart == 5L, cell.fill == DayFill.EMPTY)
    }

    private fun assertSpeech(day: Long, selected: Boolean) {
        val node = cell(day).fetchSemanticsNode()
        assertEquals(Role.Tab, node.config.getOrNull(SemanticsProperties.Role))
        val spoken = speech(node)
        assertTrue("speech contains the unambiguous full civil date: $spoken", spoken.contains(DateCopy.weekdayFullDate(LocalDate.ofEpochDay(day))))
        assertEquals("speech separates selected from today", selected, spoken.contains("selected", ignoreCase = true))
        assertEquals("today remains the real civil date", day == today, spoken.contains("today", ignoreCase = true))
        when (day - weekStart) {
            0L -> assertTrue(spoken.contains("1 completed"))
            1L -> { assertTrue(spoken.contains("1 skipped")); assertFalse(spoken.contains("completed")) }
            2L -> assertTrue(spoken.contains("1 missed"))
            3L -> { assertTrue(spoken.contains("2 activities")); assertTrue(spoken.contains("1 completed")); assertTrue(spoken.contains("1 planned")) }
            4L -> assertTrue(spoken.contains("1 planned"))
            5L -> { assertTrue(spoken.contains("rest", ignoreCase = true)); assertFalse(spoken.contains("planned")) }
            6L -> listOf("1 completed", "1 skipped", "1 missed", "1 planned").forEach { assertTrue("separate truthful count in $spoken", spoken.contains(it)) }
        }
    }

    private fun speech(node: SemanticsNode) = node.config[SemanticsProperties.ContentDescription].joinToString(" ")

    private fun assertNeighborsSeparated(day: Long) {
        val all = (0L..6L).map { cell(weekStart + it).fetchSemanticsNode() }
        all.zipWithNext().forEach { (left, right) ->
            val a = fullBounds(left)
            val b = fullBounds(right)
            val gap = if (direction == LayoutDirection.Ltr) b.left - a.right else a.left - b.right
            assertTrue("adjacent day targets have real separation at $day: $a / $b", gap >= left.layoutInfo.density.density * Metrics.space1.value - 1f)
        }
    }

    private fun touchTarget(node: SemanticsNode) {
        val density = node.layoutInfo.density.density
        assertTrue("actual selectable cell width is at least 48 dp", node.size.width / density >= 48f - .5f)
        assertTrue("actual selectable cell height is at least 48 dp", node.size.height / density >= 48f - .5f)
        assertFullyVisible(compose.onNodeWithTag(node.config[SemanticsProperties.TestTag]))
    }

    private fun assertWords(interaction: SemanticsNodeInteraction, words: String) {
        reach(interaction)
        val node = interaction.fetchSemanticsNode()
        val reported = interaction.textLayout()
        val input = reported.layoutInput
        // Compose 1.11's simple Text semantics rebuilds its paragraph at the offered
        // parent's max width while retaining the actual measured text size. Re-measure
        // with that exact input and font resolver at this real node's width, as the
        // native ParagraphLayoutCache does. Pixel assertions below verify the result.
        val layout = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
            text = input.text, style = input.style, overflow = input.overflow,
            softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
            constraints = input.constraints.copy(minWidth = 0, maxWidth = node.size.width),
        )
        assertEquals("measured-width paragraph preserves the reported line count", reported.lineCount, layout.lineCount)
        repeat(layout.lineCount) { line ->
            assertEquals("measured-width paragraph preserves reported line starts", reported.getLineStart(line), layout.getLineStart(line))
            assertEquals("measured-width paragraph preserves reported line ends", reported.getLineEnd(line), layout.getLineEnd(line))
        }
        assertEquals("actual laid-out copy", words, layout.layoutInput.text.text)
        assertEquals("last character is laid out", words.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        val bitmap = drawWindow()
        try {
            val visible = node.boundsInWindow.intersect(WeekReachRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()))
            val origin = node.positionInWindow
            assertTrue("complete text height fits its real clip", layout.size.height <= visible.height + 1f)
            repeat(layout.lineCount) { line ->
                assertFalse("line $line is not ellipsized", layout.isLineEllipsized(line))
                val glyphs = WeekReachRect(origin.x + layout.getLineLeft(line), origin.y + layout.getLineTop(line), origin.x + layout.getLineRight(line), origin.y + layout.getLineBottom(line))
                assertTrue("full text line fits actual clip: $words / $glyphs / $visible", glyphs.left >= visible.left - 1f && glyphs.right <= visible.right + 1f && glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                assertTrue("native glyph ink exists on each visible line of $words", bitmap.count(glyphs.intersect(visible), layout.layoutInput.style.color) > 0)
            }
            val last = layout.getBoundingBox(words.lastIndex)
            val finalGlyph = WeekReachRect(origin.x + last.left, origin.y + last.top, origin.x + last.right, origin.y + last.bottom)
            assertTrue("native final glyph is visible: $words", bitmap.count(finalGlyph.intersect(visible), layout.layoutInput.style.color) > 0)
        } finally { bitmap.recycle() }
    }

    private fun assertPickerHeader(title: String) {
        drainLayout()
        assertWords(text(PickerHeaderTags.TITLE), title.uppercase())
        val cancel = reach(compose.onNodeWithTag(PickerHeaderTags.CANCEL)).assertIsEnabled()
        touchTarget(cancel.fetchSemanticsNode())
        assertWords(compose.onNodeWithText("Cancel", useUnmergedTree = true), "Cancel")
        val a = fullBounds(text(PickerHeaderTags.TITLE).fetchSemanticsNode())
        val b = fullBounds(cancel.fetchSemanticsNode())
        assertFalse("header title and Cancel have separated actual geometry", a.overlaps(b))
        capture("header-${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}")
    }

    private fun assertPlanHeader() {
        assertWords(boardTo(PlanTags.TITLE, unmerged = true), "Plan")
        val start = boardTo(PlanTags.START_SHEET).assertIsEnabled()
        touchTarget(start.fetchSemanticsNode())
        assertWords(compose.onNodeWithText(StartOptionsCopy.OPEN, useUnmergedTree = true), StartOptionsCopy.OPEN)
        val library = boardTo(PlanTags.LIBRARY).assertIsEnabled()
        touchTarget(library.fetchSemanticsNode())
        assertWords(compose.onNodeWithText("Library", useUnmergedTree = true), "Library")
        capture("plan-complete-header")
    }

    /** Find a lazy item through bounded real ScrollBy, then reveal both clipping axes. */
    private fun boardTo(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction {
        drainLayout()
        if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return reach(compose.onNodeWithTag(tag, useUnmergedTree = unmerged))
        val boards = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        assertEquals("one real screen lazy board", 1, boards.size)
        val board = boards.single()
        compose.runOnUiThread { checkNotNull(board.config[SemanticsActions.ScrollToIndex].action)(0) }
        drainLayout()
        repeat(40) {
            if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()) return reach(compose.onNodeWithTag(tag, useUnmergedTree = unmerged))
            val current = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().single()
            val range = current.config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("$tag appears before the actual lazy board end", range.value() < range.maxValue())
            compose.runOnUiThread { checkNotNull(current.config[SemanticsActions.ScrollBy].action)(0f, current.boundsInWindow.height * .75f) }
            drainLayout()
        }
        throw AssertionError("$profile cannot compose $tag within 40 actual bounded scrolls")
    }

    private fun reach(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        repeat(25) {
            val target = interaction.fetchSemanticsNode()
            val window = windowBounds()
            val full = fullBounds(target)
            val visible = target.boundsInWindow.intersect(window)
            if (visible.width >= target.size.width - 1f && visible.height >= target.size.height - 1f) return interaction.assertIsDisplayed()
            var ancestor = target.parent
            var moved = false
            while (ancestor != null && !moved) {
                val candidate = ancestor
                val action = candidate.config.getOrNull(SemanticsActions.ScrollBy)?.action
                if (action != null) {
                    val viewport = candidate.boundsInWindow.intersect(window)
                    for ((horizontal, range) in listOf(true to candidate.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange), false to candidate.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))) {
                        if (range == null) continue
                        val low = if (horizontal) full.left else full.top
                        val high = if (horizontal) full.right else full.bottom
                        val start = if (horizontal) viewport.left else viewport.top
                        val end = if (horizontal) viewport.right else viewport.bottom
                        val delta = when { low < start - 1f -> low - start; high > end + 1f -> high - end; else -> 0f }
                        // ScrollAxisRange carries only explicit reverseScrolling; RTL
                        // additionally reverses a horizontal physical-to-logical delta.
                        val reversed = range.reverseScrolling xor (horizontal && target.layoutInfo.layoutDirection == LayoutDirection.Rtl)
                        val amount = if (reversed) -delta else delta
                        if (amount < -1f && range.value() > 0f || amount > 1f && range.value() < range.maxValue()) {
                            moved = compose.runOnUiThread { action(if (horizontal) amount else 0f, if (horizontal) 0f else amount) }
                            if (moved) { drainLayout(); break }
                        }
                    }
                }
                ancestor = candidate.parent
            }
            if (!moved) throw AssertionError("$profile $screen cannot fully reveal actual $full clipped to $visible: ${target.config}")
        }
        throw AssertionError("$profile bounded two-axis reach failed")
    }

    private fun assertFullyVisible(interaction: SemanticsNodeInteraction) {
        val node = interaction.fetchSemanticsNode()
        val actual = node.boundsInWindow.intersect(windowBounds())
        assertTrue("actual complete target is visible without additional reveal: ${node.config} / $actual / ${node.size}", actual.width >= node.size.width - 1f && actual.height >= node.size.height - 1f)
        interaction.assertIsDisplayed()
    }

    private fun fullBounds(node: SemanticsNode): WeekReachRect {
        val position = node.positionInWindow
        return WeekReachRect(position.x, position.y, position.x + node.size.width, position.y + node.size.height)
    }

    private fun decor(): View = compose.runOnUiThread {
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
    }
    private fun windowBounds(): WeekReachRect = decor().let { WeekReachRect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }

    /** Compose 1.11's real RootForTest traversal drains queued native measure work. */
    private fun drainLayout() {
        repeat(3) {
            compose.settle()
            val pending = compose.runOnUiThread {
                val roots = mutableListOf<View>()
                fun visit(view: View) {
                    if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") roots += view
                    if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView)
                check(roots.isNotEmpty())
                roots.forEach { root -> root.javaClass.methods.single { it.name == "measureAndLayoutForTest" && it.parameterCount == 0 }.invoke(root) }
                roots.any { root -> root.javaClass.methods.single { it.name == "getHasPendingMeasureOrLayout" && it.parameterCount == 0 }.invoke(root) as Boolean }
            }
            if (!pending) return
        }
        throw AssertionError("$profile real Compose roots still have pending measure/layout after 60 explicit frames")
    }

    private fun drawWindow(): Bitmap = compose.runOnUiThread {
        val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
        check(view.width > 1 && view.height > 1)
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun capture(stage: String) {
        drainLayout()
        val bitmap = drawWindow()
        try {
            val directory = File("build/screen-renders/home-week-geometry/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val file = directory.resolve("$stage.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("meaningful actual native frame saved", file.length() > 1_000)
            directory.resolve("$stage.txt").writeText("screen=$screen\nviewport=${bitmap.width}x${bitmap.height}\nfont=$scale\ndirection=$direction\ntoday=${LocalDate.ofEpochDay(today)}\nweekStart=${LocalDate.ofEpochDay(weekStart)}\n")
        } finally { bitmap.recycle() }
    }

    private fun inventory(): List<Any?> = runBlocking {
        listOf(deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets(), deps.database.activityDao().getAllGraphs(),
            deps.database.plannerDao().getRules(), deps.database.plannerDao().getAllOccurrences(),
            deps.database.plannerDao().getDecisions(), deps.database.plannerDao().getDeliveries(), deps.database.scheduleDao().getAll(),
            deps.database.routineDao().getAllRoutines(), deps.database.routineDao().getAllRoutineExercises(),
            deps.database.bodyweightDao().getAll(), deps.rawPreferenceValues(), deps.pendingOccurrenceId.value,
            deps.restTimerStore.current())
    }

    private fun restWeek(start: Long) = WeeklySchedulePlan(
        weekStartEpochDay = start, generatedAtMs = 1L, preferences = SchedulePreferences.DEFAULT,
        resolvedSplit = SplitStyle.UPPER_LOWER, thinHistory = true, summary = "Synthetic cross-year week",
        days = (0L..6L).map { offset -> SuggestedTrainingDay(
            epochDay = start + offset, dayOfWeek = Weekday.fromEpochDay(start + offset), isRest = true,
            focusKind = SessionFocusKind.RECOVERY, focusTitle = "Rest", routineId = null, routineName = null,
            reason = "Synthetic geometry fixture", emphasisMuscles = emptyList(), confidence = ScheduleConfidence.HIGH,
        ) },
    )

    private class RequiredRead {
        val fail = MutableStateFlow(false)
        @Volatile var held: CompletableDeferred<Unit>? = null
        fun decorate(real: BodyweightDao): BodyweightDao = object : BodyweightDao by real {
            override fun observeAll(): Flow<List<BodyweightEntryEntity>> = flow {
                held?.await()
                emitAll(real.observeAll().combine(fail) { rows, refused ->
                    check(!refused) { "Synthetic required geometry-test read failure" }
                    rows
                })
            }
        }
    }

    private enum class Screen { HOME, PLAN, COMPONENT }
    private enum class Edge { CENTRE, LEFT, RIGHT }
}
