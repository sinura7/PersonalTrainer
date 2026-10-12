package com.sinura.personaltrainer.ui.routines

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** Real editor and Room: failed initial add -> reachable Retry -> selected lift -> stale selection. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class RoutineEditorInitialExerciseRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<RoutineEditorViewModel>()
    private val current = mutableStateOf<RoutineEditorViewModel?>(null)
    private val runId = UUID.randomUUID().toString()
    private var rejectAdd = true
    private var exits = 0
    private var profile = ""

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        try { runBlocking { viewModels.forEach { it.clearAndJoinForTest() } } }
        finally { if (::deps.isInitialized) deps.close(); Dispatchers.resetMain() }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun small10() = journey("360x640-font10")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun small16() = journey("360x640-font16")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun small20() = journey("360x640-font20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standard10() = journey("412x840-font10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standard16() = journey("412x840-font16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standard20() = journey("412x840-font20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscape10() = journey("640x360-font10")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscape16() = journey("640x360-font16")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscape20() = journey("640x360-font20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun tablet10() = journey("600x960-font10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun tablet16() = journey("600x960-font16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tablet20() = journey("600x960-font20")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtl20() = journey("360x640-font20-rtl", rtl = true)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun loadedAndHoldPreviewsKg() = targetPreviews("target-preview-360x640-font20-kg", WeightUnit.KG)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun loadedAndHoldPreviewsLb() = targetPreviews("target-preview-640x360-font20-lb", WeightUnit.LBS)

    private fun journey(name: String, rtl: Boolean = false) {
        profile = name
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val exercise = runBlocking { insertTestExercise(deps, "selected-row", "Chest-supported seated cable row with deliberate control throughout a complete movement") }
        val faultDao = object : RoutineDao by deps.database.routineDao() {
            override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
                if (rejectAdd) error("synthetic selected-lift add failure")
                deps.database.routineDao().upsertRoutineExercise(item)
            }
        }
        val faultDeps = object : AppDependencies by deps {
            override val routineRepository = RoutineRepository(faultDao)
        }
        val vm = editor(exercise.id, faultDeps)
        current.value = vm
        mount(rtl = rtl)
        compose.awaitThat("failed selected-lift preparation", vm.uiState::value) { vm.uiState.value.failed }
        words(RoutineSaveCopy.PREPARE_FAILED_TITLE)
        words(RoutineSaveCopy.PREPARE_FAILED_BODY)
        target(DataHealthCopy.RETRY)
        capture("failed-add-retry")
        val stub = runBlocking { deps.routineRepository.observeAll().first().single() }
        rejectAdd = false
        compose.onNodeWithText(DataHealthCopy.RETRY).performTouchInput { click(center) }
        compose.awaitThat("selected lift ready without another search", vm.uiState::value) {
            !vm.uiState.value.isLoading && !vm.uiState.value.failed &&
                vm.uiState.value.routine?.exercises?.size == 1
        }
        compose.settle()
        assertEquals(stub.id, vm.uiState.value.routine?.id)
        assertEquals(exercise.id, vm.uiState.value.routine?.exercises?.single()?.exercise?.id)
        compose.onNodeWithTag(RoutineEditorTags.SAVE).assertIsDisplayed().assertIsEnabled()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex), useUnmergedTree = true)
            .performScrollToNode(hasText(exercise.name))
        words(exercise.name)
        val item = checkNotNull(vm.uiState.value.routine).exercises.single()
        val identity = compose.onNodeWithTag(SessionLiftTags.card(item.id)).performScrollTo().assertIsDisplayed()
        assertEquals(Role.Button, identity.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("Edit targets", identity.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val savedTargets = runBlocking { deps.database.routineDao().getAllRoutineExercises() }
        capture("selected-lift-ready")
        identity.performTouchInput { click(center) }
        compose.settle()
        assertTrue(compose.onNodeWithTag(SessionLiftTags.card(item.id)).fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertEquals("Close targets", compose.onNodeWithTag(SessionLiftTags.card(item.id))
            .fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag(SessionLiftTags.EDITOR).assertExists()
        assertEquals("opening targets does not change stored targets", savedTargets,
            runBlocking { deps.database.routineDao().getAllRoutineExercises() })
        compose.onNodeWithTag("Type a target weight").performScrollTo().assertIsDisplayed()
        capture("selected-lift-targets")

        val missingVm = editor("no-longer-available", deps)
        compose.runOnIdle { current.value = missingVm }
        compose.settle()
        compose.awaitThat("stale selection offers return", missingVm.uiState::value) { missingVm.uiState.value.failed }
        words(RoutineSaveCopy.SELECTED_LIFT_MISSING_TITLE)
        words(RoutineSaveCopy.SELECTED_LIFT_MISSING_BODY)
        target(RoutineSaveCopy.BACK_TO_LIBRARY)
        capture("missing-lift-return")
        compose.onNodeWithText(RoutineSaveCopy.BACK_TO_LIBRARY).performTouchInput { click(center) }
        compose.awaitThat("stale selection returned", { exits }) { exits == 1 }
        assertEquals(listOf(stub.id), runBlocking { deps.routineRepository.observeAll().first().map { it.id } })
    }

    private fun targetPreviews(name: String, unit: WeightUnit) {
        profile = name
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val routine = runBlocking {
            val created = deps.routineRepository.create("Strength and controlled holds")
            val loaded = insertTestExercise(deps, "loaded-preview", "Bench press")
            val hold = insertTestExercise(deps, "hold-preview", "Weighted plank")
            deps.routineRepository.addExercise(created.id, loaded, 13, 101, 102.5, 150)
            deps.routineRepository.addExercise(created.id, hold, 3, 1, 27.5, 75, 20, 40)
            checkNotNull(deps.routineRepository.getById(created.id))
        }
        val vm = RoutineEditorViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            SavedStateHandle(mapOf("routineId" to routine.id)), deps,
        ).also { viewModels += it }
        current.value = vm
        mount(unit = unit)
        compose.awaitThat("the exact loaded and hold targets are ready", vm.uiState::value) {
            !vm.uiState.value.isLoading && vm.uiState.value.routine?.exercises?.size == 2
        }
        val stored = runBlocking { deps.database.routineDao().getAllRoutineExercises() }
        routine.exercises.forEachIndexed { index, item ->
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex), useUnmergedTree = true)
                .performScrollToNode(hasTestTag(SessionLiftTags.card(item.id)))
            words(item.exercise.name)
            val scope = SessionLiftTags.preview(item.id)
            words(if (index == 0) "13 × 101" else HoldWork.workLine(3, 20, 40), scope)
            words(if (index == 0) "2:30" else "1:15", scope)
            words(WeightConverter.formatLabel(if (index == 0) 102.5 else 27.5, unit), scope)
            listOf(SessionOrderCopy.WORK, SessionOrderCopy.REST, SessionOrderCopy.LOAD)
                .forEach { words(it.uppercase(), scope) }
            capture(if (index == 0) "loaded-preview" else "hold-preview")
        }
        val hold = routine.exercises.last()
        compose.onNodeWithTag(SessionLiftTags.card(hold.id)).performScrollTo()
            .performTouchInput { click(center) }
        compose.settle()
        compose.onNodeWithTag("Type hold seconds").performScrollTo().assertIsDisplayed()
        capture("targets-open")
        val close = compose.onNodeWithTag(SessionLiftTags.card(hold.id)).performScrollTo().assertIsDisplayed()
        assertEquals("Close targets", close.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        close.performTouchInput { click(center) }
        compose.settle()
        compose.onNodeWithTag(SessionLiftTags.EDITOR).assertDoesNotExist()
        assertFalse(compose.onNodeWithTag(SessionLiftTags.card(hold.id))
            .fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertEquals("Edit targets", compose.onNodeWithTag(SessionLiftTags.card(hold.id))
            .fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        assertEquals("opening and closing targets preserves exact stored values", stored,
            runBlocking { deps.database.routineDao().getAllRoutineExercises() })
        capture("targets-closed")
    }

    private fun mount(rtl: Boolean = false, unit: WeightUnit = WeightUnit.KG) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
                LocalWeightUnit provides unit,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    key(current.value) {
                        RoutineEditorScreen(onBack = { exits += 1 }, viewModel = checkNotNull(current.value))
                    }
                }
            }
        }
    }

    private fun editor(exerciseId: String, container: AppDependencies) = RoutineEditorViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        SavedStateHandle(mapOf("routineId" to "new", "initialExerciseId" to exerciseId)),
        container,
    ).also { viewModels += it }

    private fun words(text: String, scope: String? = null) {
        compose.settle()
        val matcher = if (scope == null) hasText(text) else hasText(text) and hasAnyAncestor(hasTestTag(scope))
        val node = compose.onNode(matcher, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val layout = node.textLayout()
        val semantics = node.fetchSemanticsNode()
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("$profile clipped $text", glyph.left >= -1f &&
                glyph.right <= semantics.size.width + 1f && glyph.top >= -1f &&
                glyph.bottom <= semantics.size.height + 1f)
        }
    }

    private fun target(text: String) {
        val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().assertIsEnabled()
        val semantics = node.fetchSemanticsNode()
        assertTrue(semantics.boundsInWindow.height >= 48f * semantics.layoutInfo.density.density - 1f)
        assertTrue(semantics.boundsInWindow.width >= 48f * semantics.layoutInfo.density.density - 1f)
    }

    private fun capture(stage: String) {
        compose.settle()
        val view = compose.activity.window.decorView
        val frame = compose.runOnUiThread {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/library-routine-handoff/$runId/$profile/$stage.png")
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
}
