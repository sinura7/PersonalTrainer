package com.sinura.personaltrainer.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.LibraryBrowseDrafts
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native shipping Library screen; injected private IO only. This is not AppNav or phone evidence. */
@RunWith(AndroidJUnit4::class)
class LibraryBrowseFailureInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedKeepRetainsSearchAndExposesReachableExactRetry() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val environment = NativeWorkoutFixtureEnvironment("Synthetic P10")
        val store = ViewModelStore()
        environment.prepare(app.container)
        val root = File(app.cacheDir, "synthetic-p10-library-" + UUID.randomUUID()).also { check(it.mkdir()) }
        val io = object : DraftFileIO by AtomicDraftFiles {
            @Volatile var fail = false
            override fun replace(file: File, bytes: ByteArray) {
                if (fail) throw IOException("synthetic private Keep failure")
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        val service = LibraryBrowseDrafts(DurableDraftStore(directory = root, files = io))
        var exits = 0
        lateinit var viewModel: ExerciseLibraryViewModel
        try {
            compose.runOnUiThread {
                viewModel = ExerciseLibraryViewModel(application = app, container = app.container,
                    savedState = SavedStateHandle(), browseDrafts = service)
                store.put("synthetic-p10", viewModel)
            }
            compose.setContent { PersonalTrainerTheme {
                ExerciseLibraryScreen(onBack = { exits += 1 }, onCreateRoutine = {}, onOpenExercise = {}, viewModel = viewModel)
            } }
            await(LibraryTags.SEARCH)
            io.fail = true
            val search = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)), useUnmergedTree = true)
            search.performTextReplacement("  manual press  ")
            search.performImeAction()
            tap(LibraryTags.BACK)
            await(LibraryTags.BROWSE_RETRY)
            compose.onNodeWithTag(LibraryTags.BROWSE_STATUS).assertIsDisplayed()
            compose.onNodeWithTag(LibraryTags.BROWSE_RESET).assertIsDisplayed()
            compose.onNodeWithTag(LibraryTags.BROWSE_RETRY).assertIsDisplayed()
            compose.onNodeWithTag(LibraryTags.BACK).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)), useUnmergedTree = true)
                .assertIsDisplayed().assertTextContains("  manual press  ")
            assertEquals(0, exits)
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try { println("NATIVE_LIBRARY_CAPTURE=" + NativeArtifacts.write("library-private-keep-failure", bitmap)) }
            finally { bitmap.recycle() }
            io.fail = false
            tap(LibraryTags.BROWSE_RETRY)
            compose.waitUntil(30_000) { exits == 1 }
            assertEquals("  manual press  ", runBlocking(Dispatchers.IO) { service.open().remembered?.query })
        } finally {
            compose.runOnUiThread { store.clear() }
            // Only this fresh UUID-owned test directory is disposable.
            check(root.canonicalFile.parentFile == app.cacheDir.canonicalFile)
            check(root.name.startsWith("synthetic-p10-library-"))
            check(root.deleteRecursively())
            environment.restore()
        }
    }
    private fun await(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }
}
