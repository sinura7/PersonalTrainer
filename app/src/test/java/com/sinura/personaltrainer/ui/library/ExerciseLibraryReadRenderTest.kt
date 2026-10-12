package com.sinura.personaltrainer.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.components.AddToRoutineTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

/** Native Library recovery and real Room; manual draft survives the same editor owner. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ExerciseLibraryReadRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: ExerciseLibraryViewModel
    private val fail = MutableStateFlow(true)
    private var readGate: CompletableDeferred<Unit>? = null
    private val runId = UUID.randomUUID().toString()
    private var profile = ""
    private var failAddLookup = false

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        readGate?.complete(Unit)
        try { if (::vm.isInitialized) runBlocking { vm.clearAndJoinForTest() } }
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

    private fun journey(display: String, rtl: Boolean = false) {
        profile = display
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val fixture = runBlocking { seedTestWorkout(deps) }
        val row = runBlocking {
            insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        }
        val original = runBlocking { deps.exerciseRepository.observeAll().first() }
        val originalRoutines = runBlocking { deps.database.routineDao().getAllRoutineExercises() }
        val exerciseDao = object : ExerciseDao by deps.database.exerciseDao() {
            override fun observeAll(): Flow<List<ExerciseEntity>> = fail.flatMapLatest { failed ->
                flow {
                    if (failed) throw IOException("Synthetic native Library read failure")
                    readGate?.await()
                    emitAll(deps.database.exerciseDao().observeAll())
                }
            }
        }
        val container = object : AppDependencies by deps {
            override val exerciseRepository = ExerciseRepository(
                exerciseDao = exerciseDao, routineDao = deps.database.routineDao(),
                workoutDao = deps.database.workoutDao(), catalogDao = deps.database.catalogDao(), database = deps.database,
            )
            override val routineRepository = RoutineRepository(
                routineDao = object : RoutineDao by deps.database.routineDao() {
                    override suspend fun getById(id: String): RoutineWithExercises? {
                        if (failAddLookup) throw IOException("Synthetic native destination read failure")
                        return deps.database.routineDao().getById(id)
                    }
                },
                database = deps.database,
            )
        }
        vm = ExerciseLibraryViewModel(application = ApplicationProvider.getApplicationContext(), container = container)
        vm.onQueryChange("  ROW  ")
        vm.onMuscleSelected(CanonicalMuscle.BACK)
        vm.onEquipmentSelected(EquipmentType.OTHER)
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    ExerciseLibraryScreen(onBack = {}, onCreateRoutine = {}, onOpenExercise = {}, viewModel = vm)
                }
            }
        }
        ready(LibraryReadState.UNAVAILABLE)
        compose.onNodeWithTag(LibraryTags.BACK).assertIsDisplayed()
        words(LibraryReadCopy.TITLE)
        capture("first-failure")
        words(LibraryReadCopy.BODY)
        capture("failure-explanation")
        compose.onNodeWithTag(LibraryTags.LIST).assertDoesNotExist()
        compose.onNodeWithText("Library is empty").assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.READ_RETRY).performScrollTo().assertIsEnabled().performTouchInput { click() }
        ready(LibraryReadState.UNAVAILABLE)
        compose.onNodeWithTag(LibraryTags.READ_RETRY).assertIsEnabled()
        capture("retry-refused")

        readGate = CompletableDeferred()
        fail.value = false
        compose.onNodeWithTag(LibraryTags.READ_RETRY).performTouchInput { click() }
        compose.awaitThat("pending required read", vm.uiState::value) { vm.uiState.value.retryPending }
        compose.onNodeWithTag(LibraryTags.READ_RETRY).assertIsNotEnabled()
        capture("retry-pending")
        readGate!!.complete(Unit)
        ready(LibraryReadState.CURRENT)
        assertContext(row.id)
        compose.onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasText(row.name))
        words(row.name)
        capture("recovered-context")

        val draft = ExerciseEditorDraft(id = row.id, name = "  My manual row  ", muscleGroup = "Back", notes = "Keep these unsaved notes")
        compose.runOnIdle { vm.openEdit(row); vm.updateEditor(draft) }
        compose.onNodeWithText("Save changes").performScrollTo().assertIsEnabled()
        compose.runOnIdle { fail.value = true }
        ready(LibraryReadState.STALE)
        compose.onNodeWithText("Save changes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(LibraryTags.EDITOR_READ_RETRY).performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("editor-failure-retry")
        readGate = CompletableDeferred()
        compose.runOnIdle { fail.value = false }
        compose.onNodeWithTag(LibraryTags.EDITOR_READ_RETRY).performTouchInput { click() }
        compose.awaitThat("editor retries without dismissal", vm.uiState::value) { vm.uiState.value.retryPending }
        compose.onNodeWithTag(LibraryTags.EDITOR_READ_RETRY).assertIsNotEnabled()
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        capture("editor-retry-pending")
        readGate!!.complete(Unit)
        ready(LibraryReadState.CURRENT)
        assertEquals(draft, vm.uiState.value.editor)
        compose.onNodeWithTag(LibraryTags.EDITOR_READ_RETRY).assertDoesNotExist()
        compose.onNodeWithText("Save changes").performScrollTo().assertIsEnabled()
        capture("editor-recovered")
        assertContext(row.id)
        assertEquals(original, runBlocking { deps.exerciseRepository.observeAll().first() })
        assertEquals(originalRoutines, runBlocking { deps.database.routineDao().getAllRoutineExercises() })
        compose.runOnIdle { vm.dismissEditor(); vm.openAddToRoutine(row) }
        compose.onNodeWithTag(AddToRoutineTags.destination(fixture.routine.id)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { failAddLookup = true }
        compose.onNodeWithTag(AddToRoutineTags.destination(fixture.routine.id)).performTouchInput { click() }
        compose.awaitThat("failed destination read dismisses chooser to expose recovery", vm.uiState::value) {
            vm.uiState.value.addToRoutine == null && vm.uiState.value.error == "Could not add that lift to the routine."
        }
        compose.onNodeWithTag(AddToRoutineTags.SHEET).assertDoesNotExist()
        words("Could not add that lift to the routine.", scroll = false)
        capture("add-failure-visible")
        assertEquals(originalRoutines, runBlocking { deps.database.routineDao().getAllRoutineExercises() })
    }

    private fun ready(expected: LibraryReadState) {
        compose.awaitThat("required Library state $expected", vm.uiState::value) {
            vm.uiState.value.readState == expected && !vm.uiState.value.retryPending
        }
        compose.settle()
    }

    private fun assertContext(id: String) {
        assertEquals("  ROW  ", vm.uiState.value.query)
        assertEquals(CanonicalMuscle.BACK, vm.uiState.value.selectedMuscle)
        assertEquals(EquipmentType.OTHER, vm.uiState.value.selectedEquipment)
        assertEquals(listOf(id), vm.uiState.value.visibleExercises.map { it.id })
    }

    private fun words(text: String, scroll: Boolean = true) {
        val found = compose.onNodeWithText(text, useUnmergedTree = true)
        val node = (if (scroll) found.performScrollTo() else found).assertIsDisplayed()
        val layout = node.textLayout()
        val semantics = node.fetchSemanticsNode()
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("$profile clipped $text", glyph.left >= -1f && glyph.right <= semantics.size.width + 1f &&
                glyph.top >= -1f && glyph.bottom <= semantics.size.height + 1f)
        }
    }

    private fun capture(stage: String) {
        compose.settle()
        val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
            ?: compose.activity.window.decorView
        val bitmap = compose.runOnUiThread {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/library-read/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.resolveSibling("$stage.txt").writeText(
                compose.onAllNodes(SemanticsMatcher("all native nodes") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { "id=${it.id} bounds=${it.boundsInWindow} size=${it.size} config=${it.config}" },
            )
        } finally { bitmap.recycle() }
    }
}
