package com.sinura.personaltrainer.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.LibraryBrowseDrafts
import com.sinura.personaltrainer.data.draft.LibraryBrowseSnapshot
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Shipping Library and private draft service; this host is not AppNav or physical evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class LibraryBrowseRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: ExerciseLibraryViewModel
    private val io = ControlledIO()
    private val runId = UUID.randomUUID().toString()
    private var profile = ""

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        io.release()
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
        val application = ApplicationProvider.getApplicationContext<Application>()
        deps = FakeAppDependencies(application)
        val lift = DefaultExercises.catalog().single { it.id == "ex-barbell-bench-press" }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = temporary.newFolder(), files = io))
        runBlocking {
            deps.database.exerciseDao().insertAll(listOf(ExerciseEntity(
                id = lift.id, name = lift.name, muscleGroup = lift.muscleGroup, notes = "", isCustom = false,
                equipment = lift.equipment.name, loadType = lift.loadType.name, movementKey = lift.movementKey,
                imageKey = lift.imageKey, nameKey = lift.name.lowercase(),
            )))
            service.keep(service.open(), LibraryBrowseSnapshot(query = "  bench  "))
        }
        val inventory = runBlocking { deps.exerciseRepository.observeAll().first() }
        var exits = 0
        vm = ExerciseLibraryViewModel(application = application, container = deps,
            savedState = SavedStateHandle(), browseDrafts = service)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    ExerciseLibraryScreen(onBack = { exits += 1 }, onCreateRoutine = {},
                        onOpenExercise = {}, viewModel = vm)
                }
            }
        }
        compose.awaitThat("durable search resumed", vm.uiState::value) {
            vm.uiState.value.browseResumed && !vm.uiState.value.isLoading &&
                vm.uiState.value.visibleExercises.map { it.id } == listOf(lift.id)
        }
        words("Last search resumed")
        compose.onNodeWithTag(LibraryTags.SEARCH).assertIsDisplayed()
        button(LibraryTags.BACK)
        capture("resumed")

        io.hold = true
        vm.onQueryChange("  manual press  ")
        tap(LibraryTags.BACK)
        compose.awaitThat("reserved Keep is visible", vm.uiState::value) { vm.uiState.value.browseKeeping }
        words("Keeping search…")
        assertFalse(vm.uiState.value.browseEditable)
        assertEquals(0, exits)
        capture("keeping")
        io.fail = true
        io.release()
        compose.awaitThat("failed Keep retains exact authored search", vm.uiState::value) {
            vm.uiState.value.browseError != null && !vm.uiState.value.browseKeeping
        }
        assertEquals("  manual press  ", vm.uiState.value.query)
        assertEquals(0, exits)
        words(checkNotNull(vm.uiState.value.browseError))
        button(LibraryTags.BROWSE_RETRY)
        button(LibraryTags.BROWSE_RESET)
        button(LibraryTags.BACK)
        compose.onNodeWithTag(LibraryTags.SEARCH).assertIsDisplayed()
        capture("keep-failure")
        tap(LibraryTags.BROWSE_RESET)
        words("Reset remembered view?")
        words("This forgets only search, filters and results position. Lifts, custom drafts and workout history stay saved.", scroll = true)
        compose.onNodeWithText("Reset view").assertIsDisplayed().assertIsEnabled()
        capture("reset-confirmation")
        io.fail = false
        compose.onNodeWithText("Reset view").performTouchInput { click(center) }
        compose.awaitThat("accepted reset keeps catalogue and clears only remembered context", vm.uiState::value) {
            vm.uiState.value.query.isEmpty() && vm.uiState.value.browseError == null &&
                !vm.uiState.value.browseKeeping && !vm.uiState.value.isLoading
        }
        assertEquals(0, exits)
        assertEquals(inventory, runBlocking { deps.exerciseRepository.observeAll().first() })
        assertEquals("", runBlocking { service.open().remembered?.query })
        capture("reset-accepted")
    }

    private fun button(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        assertTrue("$profile: $tag needs a 48 dp action", node.size.height >= 48f * node.layoutInfo.density.density - 1f)
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.settle()
    }

    private fun words(text: String, scroll: Boolean = false) {
        val action = compose.onNodeWithText(text, useUnmergedTree = true)
        val node = (if (scroll) action.performScrollTo() else action).assertIsDisplayed()
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
            val file = File("build/screen-renders/library-browse/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.resolveSibling("$stage.txt").writeText(
                compose.onAllNodes(SemanticsMatcher("all mounted native nodes") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { "bounds=${it.boundsInWindow} size=${it.size} config=${it.config}" },
            )
        } finally { bitmap.recycle() }
    }

    private class ControlledIO : DraftFileIO by AtomicDraftFiles {
        @Volatile var fail = false
        @Volatile var hold = false
        private val released = CountDownLatch(1)
        fun release() { hold = false; released.countDown() }
        override fun replace(file: File, bytes: ByteArray) {
            if (hold) check(released.await(30, TimeUnit.SECONDS)) { "Synthetic Keep was not released" }
            if (fail) throw IOException("synthetic private Keep failure")
            AtomicDraftFiles.replace(file, bytes)
        }
    }
}
