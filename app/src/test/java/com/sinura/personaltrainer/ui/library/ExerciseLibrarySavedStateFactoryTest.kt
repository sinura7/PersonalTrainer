package com.sinura.personaltrainer.ui.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** A new Activity/owner through the framework factory, not an OS process-kill claim. */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class)
class ExerciseLibrarySavedStateFactoryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @Before fun setUp() { forgetFirstApplication() }
    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        compose.waitForIdle()
        forgetFirstApplication()
    }

    @Test fun queryAndClearedBodyFacetRestoreUsingANewFrameworkOwner() {
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        // Prepare the isolated app catalog before starting its combined rows/credits read.
        // Waiting for a later invalidation while blocking Robolectric's main thread cannot
        // drive the Android main looper. The completed seed is an idempotent fixture boundary.
        val original = runBlocking {
            withTimeout(10_000) {
                app.container.dbMaintenance.seedCatalog()
                app.container.exerciseRepository.observeAll().first()
            }
        }
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val firstActivity = checkNotNull(controller).get()
        val first = ViewModelProvider(firstActivity)[ExerciseLibraryViewModel::class.java]
        mount(firstActivity, first)
        first.onQueryChange("  ROW  ")
        first.onEquipmentSelected(EquipmentType.CABLE)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LibraryTags.LIST)).fetchSemanticsNodes()
            !first.uiState.value.isLoading && first.uiState.value.query == "  ROW  " &&
                first.uiState.value.selectedMuscle == CanonicalMuscle.BACK &&
                first.uiState.value.selectedEquipment == EquipmentType.CABLE
        }
        assertEquals(CanonicalMuscle.BACK, first.uiState.value.selectedMuscle)
        assertEquals(EquipmentType.CABLE, first.uiState.value.selectedEquipment)
        first.clearFilters()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LibraryTags.LIST)).fetchSemanticsNodes()
            !first.uiState.value.isLoading && first.uiState.value.query == "  ROW  " &&
                first.uiState.value.selectedMuscle == null && first.uiState.value.selectedEquipment == null
        }
        val expectedIds = first.uiState.value.visibleExercises.map { it.id }
        val saved = Bundle()
        controller!!.pause().saveInstanceState(saved).stop().destroy()
        compose.waitForIdle()
        controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create(saved).start().resume().visible()
        val secondActivity = checkNotNull(controller).get()
        val restored = ViewModelProvider(secondActivity)[ExerciseLibraryViewModel::class.java]
        assertNotSame(firstActivity, secondActivity)
        assertNotSame(first, restored)
        mount(secondActivity, restored)
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LibraryTags.LIST)).fetchSemanticsNodes()
            !restored.uiState.value.isLoading && restored.uiState.value.query == "  ROW  " &&
                restored.uiState.value.selectedMuscle == null && restored.uiState.value.selectedEquipment == null
        }
        assertNull(restored.uiState.value.selectedMuscle)
        assertNull(restored.uiState.value.selectedEquipment)
        assertEquals(expectedIds, restored.uiState.value.visibleExercises.map { it.id })
        assertEquals("return/reconstruction does not change the catalog", original,
            runBlocking { app.container.exerciseRepository.observeAll().first() })
    }

    private fun mount(activity: ComponentActivity, vm: ExerciseLibraryViewModel) {
        activity.setContent {
            PersonalTrainerTheme {
                ExerciseLibraryScreen(
                    onBack = {}, onCreateRoutine = {}, onOpenExercise = {}, viewModel = vm,
                    initialMuscle = CanonicalMuscle.BACK,
                )
            }
        }
    }
}
