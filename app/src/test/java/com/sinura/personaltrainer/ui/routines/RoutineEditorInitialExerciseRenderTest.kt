package com.sinura.personaltrainer.ui.routines

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
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

    private fun journey(name: String, rtl: Boolean = false) {
        profile = name
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val exercise = runBlocking { insertTestExercise(deps, "selected-row", "Chest-supported row") }
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
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    key(current.value) {
                        RoutineEditorScreen(onBack = { exits += 1 }, viewModel = checkNotNull(current.value))
                    }
                }
            }
        }
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
        capture("selected-lift-ready")

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

    private fun editor(exerciseId: String, container: AppDependencies) = RoutineEditorViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        SavedStateHandle(mapOf("routineId" to "new", "initialExerciseId" to exerciseId)),
        container,
    ).also { viewModels += it }

    private fun words(text: String) {
        compose.settle()
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
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
        } finally { frame.recycle() }
    }
}
