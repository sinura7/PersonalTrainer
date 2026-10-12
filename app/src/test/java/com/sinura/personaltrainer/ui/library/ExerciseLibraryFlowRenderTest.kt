package com.sinura.personaltrainer.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.ui.components.AddToRoutineTags
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
import org.robolectric.shadows.ShadowDialog

/** Actual Library/Room with exact-ID callbacks; this host does not establish full AppNav. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ExerciseLibraryFlowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: ExerciseLibraryViewModel
    private val route = mutableStateOf("library")
    private val opened = mutableListOf<String>()
    private val created = mutableListOf<String>()
    private val runId = UUID.randomUUID().toString()
    private var profile = ""

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
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
        val longName = "Chest-supported row with deliberate control throughout the complete movement"
        val catalog = DefaultExercises.catalog().filter {
            it.id in setOf("ex-seated-cable-row", "ex-barbell-bench-press", "ex-ab-wheel-rollout")
        }
        assertEquals(3, catalog.size)
        val custom = runBlocking {
            deps.database.exerciseDao().insertAll(catalog.map { lift ->
                ExerciseEntity(
                    id = lift.id, name = lift.name, muscleGroup = lift.muscleGroup,
                    notes = "", isCustom = false, equipment = lift.equipment.name,
                    loadType = lift.loadType.name, movementKey = lift.movementKey,
                    imageKey = lift.imageKey, nameKey = lift.name.lowercase(),
                )
            })
            (deps.exerciseRepository.createCustom(longName, "Back", loadType = LoadType.BODYWEIGHT)
                as SaveExerciseResult.Saved).exercise
        }
        val original = runBlocking { deps.exerciseRepository.observeAll().first() }
        val routineRows = runBlocking { deps.database.routineDao().getAllRoutineExercises() }
        vm = ExerciseLibraryViewModel(
            ApplicationProvider.getApplicationContext<Application>(), deps, SavedStateHandle(),
        )
        vm.onQueryChange("  row  ")
        vm.onMuscleSelected(CanonicalMuscle.BACK)
        vm.onEquipmentSelected(EquipmentType.BODYWEIGHT)
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl,
            ) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    if (route.value == "library") {
                        ExerciseLibraryScreen(
                            onBack = {}, onCreateRoutine = { created += it },
                            onOpenExercise = { opened += it; route.value = "details" }, viewModel = vm,
                        )
                    } else {
                        TextButton(onClick = { route.value = "library" }, modifier = Modifier.testTag(RETURN)) {
                            Text("Return to Library")
                        }
                    }
                }
            }
        }
        compose.awaitThat("combined filters retain the exact selected result", vm.uiState::value) {
            !vm.uiState.value.isLoading && vm.uiState.value.visibleExercises.map { it.id } == listOf(custom.id)
        }
        words(longName)
        words(LoadTypeCopy.libraryTag(custom).uppercase())
        assertEquals(Role.Button, compose.onNodeWithTag(LibraryTags.lift(custom.id))
            .fetchSemanticsNode().config[SemanticsProperties.Role])
        capture("filtered-preview")
        vm.onMuscleSelected(CanonicalMuscle.CHEST)
        compose.awaitThat("query and both facets combine, producing no matches", vm.uiState::value) {
            vm.uiState.value.selectedMuscle == CanonicalMuscle.CHEST && vm.uiState.value.visibleExercises.isEmpty()
        }
        emptyWords("No matches")
        emptyWords("Clear search or filters, or add a custom lift.")
        capture("no-matches-message")
        val create = compose.onNodeWithTag(LibraryTags.EMPTY_CREATE).performScrollTo().assertIsDisplayed()
        capture("no-matches")
        create.performTouchInput { click(center) }
        compose.awaitThat("No matches can open an uncommitted custom draft", vm.uiState::value) {
            vm.uiState.value.editor != null
        }
        compose.onNodeWithText("New lift").assertExists()
        dismissNativeSheet()
        compose.settle()
        assertEquals(null, vm.uiState.value.editor)
        assertEquals("  row  ", vm.uiState.value.query)
        assertEquals(CanonicalMuscle.CHEST, vm.uiState.value.selectedMuscle)
        assertEquals(EquipmentType.BODYWEIGHT, vm.uiState.value.selectedEquipment)
        assertTrue(vm.uiState.value.visibleExercises.isEmpty())
        compose.onNodeWithTag(LibraryTags.EQUIPMENT_FILTERS)
            .performScrollToNode(hasText(EquipmentType.OTHER.label))
        compose.onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertIsDisplayed()
            .performTouchInput { click(center) }
        compose.awaitThat("Clear filters retains the typed query and restores matching rows", vm.uiState::value) {
            vm.uiState.value.query == "  row  " && vm.uiState.value.selectedMuscle == null &&
                vm.uiState.value.selectedEquipment == null &&
                vm.uiState.value.visibleExercises.map { it.id }.toSet() == setOf(custom.id, "ex-seated-cable-row")
        }
        compose.onNodeWithTag(LibraryTags.CLEAR_FILTERS).assertDoesNotExist()
        words(catalog.single { it.id == "ex-seated-cable-row" }.name)
        capture("filters-cleared")
        words(longName)
        val add = compose.onNodeWithTag(LibraryTags.add(custom.id))
            .performScrollTo().assertIsDisplayed()
        val bounds = add.fetchSemanticsNode()
        assertEquals(Role.Button, bounds.config[SemanticsProperties.Role])
        assertEquals(listOf("Add $longName to a routine"), bounds.config[SemanticsProperties.ContentDescription])
        assertTrue(bounds.size.width >= 48f * bounds.layoutInfo.density.density - 1f)
        assertTrue(bounds.size.height >= 48f * bounds.layoutInfo.density.density - 1f)
        add.performTouchInput { click(center) }
        compose.settle()
        assertEquals(custom.id, vm.uiState.value.addToRoutine?.id)
        assertTrue("Add must not open Details", opened.isEmpty())
        compose.onNodeWithTag(AddToRoutineTags.CREATE).assertExists()
        dismissNativeSheet()
        compose.settle()
        compose.onNodeWithTag(AddToRoutineTags.SHEET).assertDoesNotExist()
        assertEquals(null, vm.uiState.value.addToRoutine)
        assertTrue(created.isEmpty())
        capture("add-cancelled")
        compose.onNodeWithTag(LibraryTags.options(custom.id)).performScrollTo().assertIsDisplayed()
            .performTouchInput { click(center) }
        compose.settle()
        listOf("Edit lift", "Delete lift").forEach { label ->
            val action = compose.onNodeWithText(label).performScrollTo()
            try { action.assertIsDisplayed() }
            catch (failure: AssertionError) { capture("options-failure"); throw failure }
            val node = action.fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            assertTrue(node.size.height >= 48f * node.layoutInfo.density.density - 1f)
        }
        capture("options-reachable")
        dismissNativeSheet()
        compose.settle()
        assertEquals(null, vm.uiState.value.editor)
        assertEquals(null, vm.uiState.value.pendingDelete)
        val lift = compose.onNodeWithTag(LibraryTags.lift(custom.id)).performScrollTo().assertIsDisplayed()
        val liftNode = lift.fetchSemanticsNode()
        val viewport = compose.onNodeWithTag(LibraryTags.LIST).fetchSemanticsNode().boundsInWindow
        val visible = liftNode.boundsInWindow.intersect(viewport)
        assertTrue("the long lift has a visible tap region", visible.height > 0f && visible.width > 0f)
        lift.performTouchInput { click(visible.center - liftNode.boundsInWindow.topLeft) }
        compose.settle()
        assertEquals(listOf(custom.id), opened)
        compose.onNodeWithTag(RETURN).performTouchInput { click(center) }
        compose.settle()
        words(longName)
        assertEquals("  row  ", vm.uiState.value.query)
        assertEquals("readonly exploration leaves the exact catalog intact", original,
            runBlocking { deps.exerciseRepository.observeAll().first() })
        assertEquals("cancelled Add leaves all routine rows intact", routineRows,
            runBlocking { deps.database.routineDao().getAllRoutineExercises() })
        capture("return-context")
        if (profile == "412x840-font10") {
            vm.onQueryChange("")
            compose.awaitThat("unfiltered catalog keeps the exact built-in identities", vm.uiState::value) {
                vm.uiState.value.grouped && vm.uiState.value.visibleExercises.map { it.id }.toSet() ==
                    catalog.map { it.id }.toSet() + custom.id
            }
            catalog.forEach { words(it.name) }
            compose.onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasText(catalog.first().name))
            capture("catalog-preview")
        }
    }

    private fun dismissNativeSheet() {
        val sheet = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { sheet.onBackPressedDispatcher.onBackPressed() }
    }

    private fun words(text: String) {
        compose.onNodeWithTag(LibraryTags.LIST).performScrollToNode(hasText(text))
        val node = compose.onNode(hasText(text), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertWords(text, node)
    }

    private fun emptyWords(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertWords(text, node)
    }

    private fun assertWords(text: String, node: SemanticsNodeInteraction) {
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

    private fun capture(stage: String) {
        compose.settle()
        val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
            ?: compose.activity.window.decorView
        val bitmap = compose.runOnUiThread {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            val file = File("build/screen-renders/library-flow/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.resolveSibling("$stage.txt").writeText(
                compose.onAllNodes(SemanticsMatcher("all mounted native nodes") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { node ->
                        "id=${node.id} bounds=${node.boundsInWindow} size=${node.size} config=${node.config}"
                    },
            )
        } finally { bitmap.recycle() }
    }

    private companion object { const val RETURN = "library-return-host" }
}
