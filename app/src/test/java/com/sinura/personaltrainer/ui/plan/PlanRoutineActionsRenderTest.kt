package com.sinura.personaltrainer.ui.plan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.TrainingInsights
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWorkoutFixture
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorScreen
import com.sinura.personaltrainer.ui.routines.RoutineEditorTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorViewModel
import com.sinura.personaltrainer.ui.routines.RoutinesScreen
import com.sinura.personaltrainer.ui.routines.RoutinesTags
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

/** Actual Plan + Room: distinct IDs with the same name, safe menu actions and deletion scope. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class PlanRoutineActionsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: PlanViewModel
    private lateinit var dayVm: PlanDayViewModel
    private lateinit var editorVm: RoutineEditorViewModel
    private val route = mutableStateOf("plan")
    private val dayOffset = mutableStateOf(0L)
    private lateinit var fixture: TestWorkoutFixture
    private lateinit var otherId: String
    private val opened = mutableListOf<String>()
    private val openedDays = mutableListOf<Pair<Long, Boolean>>()
    private val runId = UUID.randomUUID().toString()
    private val today = LocalDate.of(2026, 12, 31).toEpochDay()
    private var profile = "interaction"

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        try {
            runBlocking {
                if (::vm.isInitialized) vm.clearAndJoinForTest()
                if (::dayVm.isInitialized) dayVm.clearAndJoinForTest()
                if (::editorVm.isInitialized) editorVm.clearAndJoinForTest()
            }
        }
        finally { if (::deps.isInitialized) deps.close(); Dispatchers.resetMain() }
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
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun tabletFont16() = journey("600x960-font16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tabletFont20() = journey("600x960-font20")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlReducedMotion() = journey("360x640-font20-rtl", rtl = true)

    @Test
    fun cardioMetadataCannotBorrowRoutineLiftArtwork() {
        profile = "cardio-art-identity"
        show()
        val unusualRules = runBlocking {
            val rules = listOf(
                deps.plannerRepository.addTimedRule(
                    weekday = Weekday.THURSDAY, hour = 7, minute = 0,
                    modality = ScheduleModality.CARDIO, routineId = fixture.routine.id,
                ),
                deps.plannerRepository.addTimedRule(
                    weekday = Weekday.THURSDAY, hour = 9, minute = 0,
                    modality = ScheduleModality.STRENGTH, routineId = fixture.routine.id, templateId = "cardio:RUN",
                ),
            )
            deps.plannerRepository.publishPinnedWeek(Weekday.MONDAY, today)
            rules
        }
        compose.awaitThat("actual unusual cardio rules have dated rows", vm.uiState::value) {
            vm.uiState.value.occurrences.count { occurrence ->
                occurrence.localEpochDay == today && unusualRules.any { it.id == occurrence.ruleId }
            } == 2
        }
        val before = inventory()
        for ((index, rule) in unusualRules.withIndex()) {
            val occurrence = vm.uiState.value.occurrences.single { it.ruleId == rule.id && it.localEpochDay == today }
            val row = reach(PlanTags.agenda(occurrence.id)).assertTextContains(if (index == 0) "Cardio" else "Run")
            target(row)
            compose.onNodeWithTag(PlanTags.agendaThumb(occurrence.id), useUnmergedTree = true).assertDoesNotExist()
            tap(row)
            capture(if (index == 0) "cardio-modality-art" else "cardio-template-art")
        }
        assertEquals(listOf(today to false, today to false), openedDays)
        val importedRule = vm.uiState.value.rules.single { it.routineId == fixture.routine.id && it.id !in unusualRules.map { row -> row.id } }
        val workout = vm.uiState.value.occurrences.single { it.ruleId == importedRule.id && it.localEpochDay == today }
        reach(PlanTags.agendaThumb(workout.id), unmerged = true).assertIsDisplayed()
        assertEquals("read-only cardio projection and navigation preserve every stored row", before, inventory())
    }

    private fun show(rtl: Boolean = false) {
        val insights = flow {
            emitAll(deps.routineRepository.observeAll().map { TrainingInsights(routines = it) })
        }
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(), insights = insights,
            time = FrozenTime(Instant.parse("2026-12-31T12:00:00Z").toEpochMilli(), "UTC"),
        )
        runBlocking {
            val known = DefaultExercises.catalog().first { it.name == "Barbell Back Squat" }
            fixture = seedTestWorkout(
                deps, exerciseName = known.name, routineName = NAME, loggedSets = listOf(TestSetInput(72.5, 9, 8)),
                finish = true, notes = "Preserve this finished result",
            )
            val stored = checkNotNull(deps.database.exerciseDao().getById(fixture.exercise.id))
            deps.database.exerciseDao().update(stored.copy(
                equipment = known.equipment.name, movementKey = known.movementKey, imageKey = known.imageKey,
            ))
            val additional = listOf("ex-barbell-bench-press", "ex-seated-cable-row", "ex-ab-wheel-rollout").map { id ->
                DefaultExercises.catalog().single { it.id == id }
            }
            deps.database.exerciseDao().insertAll(additional.map { lift ->
                ExerciseEntity(
                    id = lift.id, name = lift.name, muscleGroup = lift.muscleGroup, notes = "", isCustom = false,
                    equipment = lift.equipment.name, loadType = lift.loadType.name, movementKey = lift.movementKey,
                    imageKey = lift.imageKey, nameKey = lift.name.lowercase(),
                )
            })
            for (lift in additional) {
                deps.routineRepository.addExercise(fixture.routine.id,
                    checkNotNull(deps.exerciseRepository.getById(lift.id)), 3, 8, 72.5, 90)
            }
            fixture = fixture.copy(
                exercise = checkNotNull(deps.exerciseRepository.getById(fixture.exercise.id)),
                routine = checkNotNull(deps.routineRepository.getById(fixture.routine.id)),
            )
            otherId = deps.routineRepository.create(NAME).id
            deps.scheduleRepository.pin(fixture.routine.id, null, Weekday.THURSDAY)
            deps.scheduleRepository.pin(otherId, null, Weekday.THURSDAY)
            deps.plannerRepository.publishPinnedWeek(Weekday.MONDAY, today)
        }
        vm = PlanViewModel(ApplicationProvider.getApplicationContext(), deps)
        dayVm = PlanDayViewModel(ApplicationProvider.getApplicationContext(), deps)
        editorVm = RoutineEditorViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(mapOf("routineId" to fixture.routine.id)), deps,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalTodayEpochDay provides today,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    when (route.value) {
                        "day" -> PlanDayScreen(
                            epochDay = today + dayOffset.value, startInAdd = true,
                            onBack = { route.value = "plan" },
                            onOpenRoutine = { id ->
                                assertEquals(fixture.routine.id, id)
                                route.value = "editor"
                            }, viewModel = dayVm,
                        )
                        "editor" -> RoutineEditorScreen(
                            onBack = { route.value = "day" }, viewModel = editorVm,
                        )
                        "routines" -> RoutinesScreen(
                            onBack = { route.value = "plan" }, onOpenRoutine = { opened += it },
                            onOpenLibrary = {}, viewModel = vm,
                        )
                        else -> PlanScreen(
                            onOpenRoutine = { opened += it }, onOpenRoutines = { route.value = "routines" },
                            onOpenDay = { epochDay, add -> openedDays += epochDay to add },
                            viewModel = vm,
                        )
                    }
                }
            }
        }
        compose.awaitThat("actual Plan loaded", vm.uiState::value) { !vm.uiState.value.isLoading }
        compose.onNodeWithText("Show routines").assertDoesNotExist()
        compose.onNodeWithText("Hide routines").assertDoesNotExist()
        // v4 V3: routines left Plan; the header carries one Routines entry instead.
        compose.onNodeWithTag(PlanTags.ROUTINES).assertDoesNotExist()
        compose.onNodeWithTag(PlanTags.routine(fixture.routine.id)).assertDoesNotExist()
        reach(PlanTags.ROUTINES_ENTRY).assertIsDisplayed()
        compose.settle()
    }

    private fun openRoutines() {
        tap(reach(PlanTags.ROUTINES_ENTRY))
        compose.awaitThat("Routines screen shown", { route.value }) { route.value == "routines" }
        val heading = compose.onNodeWithTag(PlanTags.ROUTINES, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("the Routines heading is not a disclosure action", !heading.config.contains(SemanticsActions.OnClick))
        reach(PlanTags.routine(fixture.routine.id)).assertIsDisplayed()
    }

    private fun journey(name: String, rtl: Boolean = false) {
        profile = name
        show(rtl)
        val before = inventory()
        val id = fixture.routine.id
        compose.onNodeWithTag(PlanTags.CONTENT).performScrollToIndex(0)
        compose.settle()
        capture("plan-overview")
        val rule = vm.uiState.value.rules.single { it.routineId == id }
        val plannedOccurrence = vm.uiState.value.occurrences.single { it.ruleId == rule.id && it.localEpochDay == today }
        val dayRow = reach(PlanTags.agenda(plannedOccurrence.id))
        target(dayRow)
        assertEquals(Role.Button, dayRow.fetchSemanticsNode().config[SemanticsProperties.Role])
        words(text = NAME, insideTag = PlanTags.agenda(plannedOccurrence.id))
        reach(PlanTags.agendaThumb(plannedOccurrence.id), unmerged = true).assertIsDisplayed()
        tap(dayRow)
        assertEquals("the scheduled row edits the exact selected day", listOf(today to false), openedDays)
        val add = reach(PlanTags.dayAdd(today))
        target(add)
        tap(add)
        assertEquals("Add opens the selected day with its picker", listOf(today to false, today to true), openedDays)
        // Blueprint 4.2: seven day rows, every one reachable, each header opening its own day.
        val weekStart = vm.uiState.value.week?.weekStartEpochDay ?: com.sinura.personaltrainer.domain.WeekBoard.weekStartEpochDay(today, vm.uiState.value.preferences.weekStart)
        for (dayIndex in 0L..6L) reach(PlanTags.day(weekStart + dayIndex))
        val header = reach(PlanTags.dayHeader(today))
        target(header)
        assertEquals(Role.Button, header.fetchSemanticsNode().config[SemanticsProperties.Role])
        tap(header)
        assertEquals("the day header opens that day", listOf(today to false, today to true, today to false), openedDays)
        // V1a Critic: Add follows the row it sits on, never today.
        tap(reach(PlanTags.dayAdd(today + 1)))
        assertEquals("the next row's Add opens that row's day", (today + 1) to true, openedDays.last())
        assertEquals("day navigation and Add callbacks do not create saved changes", before, inventory())
        capture("plan-selected-day")
        openRoutines()
        val row = reach(PlanTags.routine(id))
        target(row)
        assertEquals(Role.Button, row.fetchSemanticsNode().config[SemanticsProperties.Role])
        reach(PlanTags.routineName(id), unmerged = true).assertIsDisplayed()
        words(text = NAME, insideTag = PlanTags.routine(id))
        val preview = SessionOrderCopy.numberedPreview(vm.uiState.value.routines.single { it.id == id }.exercises.map { it.exercise.name })
        words(text = preview, insideTag = PlanTags.routine(id))
        val expectedLifts = fixture.routine.exercises.map { it.exercise }
        assertEquals(4, expectedLifts.size)
        val renderedLifts = vm.uiState.value.routines.single { it.id == id }.exercises.map { it.exercise }
        assertEquals("the ordered preview retains all exact exercise IDs and catalog keys",
            expectedLifts.map { it.id to it.imageKey }, renderedLifts.map { it.id to it.imageKey })
        val thumbnails = expectedLifts.take(3).map { lift ->
            reach(PlanTags.routineThumb(id, lift.id), unmerged = true).assertIsDisplayed().fetchSemanticsNode()
        }
        thumbnails.zipWithNext().forEach { (beforeThumb, afterThumb) ->
            assertTrue("$profile native thumbnails follow the exercise order",
                if (rtl) beforeThumb.boundsInWindow.left > afterThumb.boundsInWindow.left
                else beforeThumb.boundsInWindow.left < afterThumb.boundsInWindow.left)
        }
        compose.onNodeWithTag(PlanTags.routineThumb(id, expectedLifts[3].id), useUnmergedTree = true).assertDoesNotExist()
        row.performTouchInput { click(center) }
        compose.settle()
        assertEquals("one direct tap edits the exact routine", listOf(id), opened)
        assertEquals("opening a reusable routine creates no saved changes", before, inventory())
        capture("direct-routine")
        opened.clear()
        val options = reach(PlanTags.routineOptions(id))
        target(options)
        assertEquals(listOf("Options for $NAME"), options.fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        capture("routine-list")
        tap(options)
        assertTrue("options must not open the editor", opened.isEmpty())
        val edit = compose.onNodeWithTag(PlanTags.routineEdit(id))
        val delete = compose.onNodeWithTag(PlanTags.routineDelete(id))
        target(edit); target(delete)
        words("Edit routine"); words("Delete routine")
        capture("options")
        tap(edit)
        assertEquals(listOf(id), opened)
        delete.assertDoesNotExist()
        assertEquals(before, inventory())

        tap(reach(PlanTags.routineOptions(otherId)))
        tap(compose.onNodeWithTag(PlanTags.routineDelete(otherId)))
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed()
        compose.onNodeWithText("Delete $NAME?").assertIsDisplayed()
        capture("delete-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("cancel must retain the complete database and timer", before, inventory())
        assertEquals("delete does not edit or navigate", listOf(id), opened)

        // Original shortcut still reaches the same confirmation without a write.
        reach(PlanTags.routine(id))
            .performTouchInput { longClick(center) }
        compose.settle()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, inventory())

        // Actual weekly day → cancellation → routine editor → Save, with the same stored result.
        compose.runOnUiThread { route.value = "day" }
        compose.settle()
        compose.awaitThat("weekly day loaded", dayVm.uiState::value) { !dayVm.uiState.value.isLoading }
        words(PlanDayCopy.addScope(Weekday.THURSDAY))
        capture("weekly-add-scope")
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).performScrollTo().performClick()
        compose.settle()
        val dayRule = dayVm.uiState.value.rules.single { it.routineId == fixture.routine.id }
        tap(compose.onNodeWithTag(PlanDayTags.remove(dayRule.id)).performScrollTo())
        words(PlanDayCopy.removeBody(Weekday.THURSDAY, recurring = true))
        val confirm = compose.onNodeWithTag(ConfirmActionTags.CONFIRM).assertIsDisplayed().fetchSemanticsNode()
        val minTouch = Metrics.touchMin.value * confirm.layoutInfo.density.density
        assertTrue("confirmation touch width: ${confirm.touchBoundsInRoot}", confirm.touchBoundsInRoot.width >= minTouch - 1f)
        assertTrue("confirmation touch height: ${confirm.touchBoundsInRoot}", confirm.touchBoundsInRoot.height >= minTouch - 1f)
        capture("weekly-remove-scope")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("weekly remove cancellation preserves exact data", before, inventory())
        val occurrence = dayVm.uiState.value.occurrences.single {
            it.ruleId == dayRule.id && it.localEpochDay == today
        }
        tap(compose.onNodeWithTag(PlanDayTags.block(occurrence.id)).performScrollTo())
        compose.awaitThat("routine editor loaded", editorVm.uiState::value) {
            !editorVm.uiState.value.isLoading && editorVm.uiState.value.routine != null
        }
        compose.settle()
        words(RoutineSaveCopy.SCOPE)
        compose.onNodeWithTag(RoutineEditorTags.SAVE).assertIsDisplayed()
        capture("routine-editor-scope")
        val beforeSwap = inventory()
        editorVm.requestSwap(editorVm.uiState.value.routine!!.exercises.single {
            it.exercise.id == fixture.exercise.id
        }.id)
        compose.settle()
        words(RoutineSaveCopy.SWAP_SCOPE)
        capture("routine-swap-scope")
        editorVm.dismissSwap()
        compose.settle()
        assertEquals("opening and dismissing swap does not write", beforeSwap, inventory())
        val savedBefore = history()
        val slotsBefore = runBlocking { deps.scheduleRepository.slots() }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(0)
        compose.settle()
        compose.onNode(hasSetTextAction() and hasText(NAME)).performScrollTo().performTextReplacement("$NAME revised")
        compose.onNodeWithTag(RoutineEditorTags.SAVE).performClick()
        compose.settle()
        compose.awaitThat("returned to weekly day after Save", { route.value }) { route.value == "day" }
        assertEquals("$NAME revised", runBlocking { deps.routineRepository.getById(fixture.routine.id) }?.name)
        assertEquals("routine edit preserves captured results", savedBefore, history())
        assertEquals("routine edit preserves schedule ownership", slotsBefore, runBlocking { deps.scheduleRepository.slots() })
        assertEquals(NAME, runBlocking { deps.routineRepository.getById(otherId) }?.name)
        capture("routine-editor-return")
    }

    @Test
    fun pastDayKeepsRecordContextAndNoScheduleAddControls() {
        show()
        val before = inventory()
        compose.runOnUiThread { dayOffset.value = -1; route.value = "day" }
        compose.settle()
        compose.awaitThat("past day loaded", dayVm.uiState::value) { !dayVm.uiState.value.isLoading }
        compose.onNodeWithText(PlanDayCopy.PAST).assertIsDisplayed()
        compose.onNodeWithTag(PlanDayTags.SCOPE).assertDoesNotExist()
        compose.onNodeWithTag(PlanDayTags.ADD).assertDoesNotExist()
        assertEquals(before, inventory())
        capture("past-read-only")
    }

    @Test
    fun disabledOneOffRuleDoesNotClaimRecurringRemoval() {
        show()
        val rule = runBlocking { deps.plannerRepository.rules().single { it.routineId == fixture.routine.id } }
        runBlocking { deps.plannerRepository.setRuleEnabled(rule.id, false) }
        val before = inventory()
        compose.runOnUiThread { route.value = "day" }
        compose.settle()
        compose.awaitThat("disabled one-off day loaded", dayVm.uiState::value) {
            !dayVm.uiState.value.isLoading && dayVm.uiState.value.rules.any { it.id == rule.id && !it.enabled }
        }
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).performScrollTo().performClick()
        compose.settle()
        tap(compose.onNodeWithTag(PlanDayTags.remove(rule.id)).performScrollTo())
        compose.onNodeWithText(PlanDayCopy.REMOVE_BODY).assertIsDisplayed()
        compose.onNodeWithText(PlanDayCopy.removeBody(Weekday.THURSDAY, true)).assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, inventory())
    }

    @Test
    fun movedBlockRemovalNamesItsRuleWeekdayRatherThanTheSelectedDate() {
        show()
        val rule = runBlocking { deps.plannerRepository.rules().single { it.routineId == fixture.routine.id } }
        runBlocking {
            val occurrence = deps.plannerRepository.occurrencesBetween(today, today).single { it.ruleId == rule.id }
            assertTrue(deps.plannerRepository.moveOccurrenceToDay(occurrence.id, today + 2) != null)
        }
        val before = inventory()
        compose.runOnUiThread { dayOffset.value = 2; route.value = "day" }
        compose.settle()
        compose.awaitThat("moved weekly day loaded", dayVm.uiState::value) {
            !dayVm.uiState.value.isLoading && dayVm.uiState.value.occurrences.any {
                it.ruleId == rule.id && it.localEpochDay == today + 2
            }
        }
        compose.onNodeWithTag(PickerHeaderTags.CANCEL).performScrollTo().performClick()
        compose.settle()
        words(PlanDayCopy.addScope(Weekday.SATURDAY))
        tap(compose.onNodeWithTag(PlanDayTags.remove(rule.id)).performScrollTo())
        compose.onNodeWithText(PlanDayCopy.removeBody(Weekday.THURSDAY, true)).assertIsDisplayed()
        compose.onNodeWithText(PlanDayCopy.removeBody(Weekday.SATURDAY, true)).assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, inventory())
    }

    @Test
    fun confirmedDeleteRemovesOnlyChosenRoutineAndPinAndKeepsExactHistory() {
        show()
        val savedBefore = history()
        // Existing FK policy detaches the deleted template; all captured results stay exact.
        val expectedAfter = runBlocking {
            listOf(deps.database.workoutDao().getAllSessions().map {
                if (it.routineId == fixture.routine.id) it.copy(routineId = null) else it
            }, deps.database.workoutDao().getAllSessionExercises(), deps.database.workoutDao().getAllSets())
        }
        val otherBefore = runBlocking { deps.routineRepository.getById(otherId) }
        val otherPins = runBlocking { deps.scheduleRepository.slots().filter { it.routineId == otherId } }
        openRoutines()
        tap(reach(PlanTags.routineOptions(fixture.routine.id)))
        tap(compose.onNodeWithTag(PlanTags.routineDelete(fixture.routine.id)))
        compose.onNodeWithText("All planned sessions for this routine", substring = true)
            .assertIsDisplayed()
            .assertTextContains("Weekly schedule: Thursday.", substring = true)
            .assertTextContains("Planned dates in this week: Thu 31 Dec 2026.", substring = true)
            .assertTextContains("Completed sessions stay saved in History.", substring = true)
        assertEquals(savedBefore, history())
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()
        compose.awaitThat("chosen routine removed", vm.uiState::value) {
            vm.uiState.value.routines.none { it.id == fixture.routine.id }
        }
        assertEquals(expectedAfter, history())
        assertEquals(otherBefore, runBlocking { deps.routineRepository.getById(otherId) })
        assertEquals(otherPins, runBlocking { deps.scheduleRepository.slots().filter { it.routineId == otherId } })
        assertTrue(runBlocking { deps.scheduleRepository.slots() }.none { it.routineId == fixture.routine.id })
        assertTrue(opened.isEmpty())
        reach(PlanTags.routineOptions(otherId)).assertIsDisplayed()
    }

    private fun target(interaction: SemanticsNodeInteraction) {
        val node = interaction.assertIsDisplayed().fetchSemanticsNode()
        val floor = Metrics.touchMin.value * node.layoutInfo.density.density
        assertTrue("$profile width ${node.boundsInWindow}", node.boundsInWindow.width >= floor - 1f)
        assertTrue("$profile height ${node.boundsInWindow}", node.boundsInWindow.height >= floor - 1f)
    }

    private fun tap(interaction: SemanticsNodeInteraction) {
        interaction.assertIsDisplayed().performTouchInput { click(center) }
        compose.settle()
    }

    private fun words(text: String, insideTag: String? = null) {
        val interaction = if (insideTag == null) compose.onNodeWithText(text, useUnmergedTree = true)
        else compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(insideTag)), useUnmergedTree = true)
        val node = interaction.fetchSemanticsNode()
        val layout = interaction.textLayout()
        assertEquals(text, layout.layoutInput.text.text)
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("$profile clipped glyph $index of $text: $glyph in ${node.size}",
                glyph.left >= -1f && glyph.right <= node.size.width + 1f &&
                    glyph.top >= -1f && glyph.bottom <= node.size.height + 1f)
        }
    }

    private fun reach(tag: String, unmerged: Boolean = false): SemanticsNodeInteraction {
        val content = if (route.value == "routines") RoutinesTags.CONTENT else PlanTags.CONTENT
        // The list tag is in the unmerged tree a frame before it is merged. Scrolling
        // in that frame looks for the content list and misses it.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(content, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(content, useUnmergedTree = unmerged).performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag, useUnmergedTree = unmerged).performScrollTo()
    }

    private fun history(): List<Any?> = runBlocking {
        listOf(deps.database.workoutDao().getAllSessions(), deps.database.workoutDao().getAllSessionExercises(),
            deps.database.workoutDao().getAllSets())
    }

    private fun inventory(): List<Any?> = runBlocking {
        history() + listOf(deps.database.routineDao().getAllRoutines(),
            deps.database.routineDao().getAllRoutineExercises(), deps.database.scheduleDao().getAll(),
            deps.database.plannerDao().getRules(), deps.database.plannerDao().getAllOccurrences(),
            deps.restTimerStore.current())
    }

    private fun capture(stage: String) {
        val view = compose.activity.window.decorView
        val frame = compose.runOnUiThread {
            val windows = WindowInspector.getGlobalWindowViews().filter { it.isShown }
            if (stage == "options") check(windows.any { it.javaClass.simpleName == "PopupLayout" })
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                for (window in windows.filter { it !== view }) {
                    val location = IntArray(2)
                    window.getLocationOnScreen(location)
                    val saved = canvas.save()
                    canvas.translate(location[0].toFloat(), location[1].toFloat())
                    window.draw(canvas)
                    canvas.restoreToCount(saved)
                }
            }
        }
        try {
            val file = File("build/screen-renders/plan-routine-actions/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.resolveSibling("$stage.txt").writeText(
                compose.onAllNodes(SemanticsMatcher("all mounted native nodes") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { node ->
                        "id=${node.id} bounds=${node.boundsInWindow} size=${node.size} config=${node.config}"
                    },
            )
        } finally { frame.recycle() }
    }

    private companion object { const val NAME = "Upper body strength and mobility" }
}
