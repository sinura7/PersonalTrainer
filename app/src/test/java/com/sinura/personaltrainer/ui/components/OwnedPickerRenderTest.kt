package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.CustomExerciseDrafts
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.PickerContextDrafts
import com.sinura.personaltrainer.data.draft.PickerContextSnapshot
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.settle
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shipping content/controller/files in a native JVM host; separate from AppNav and phone evidence. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class OwnedPickerRenderTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private lateinit var contexts: PickerContextDrafts
    private lateinit var custom: CustomExerciseDrafts
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val owner = DraftOwner(DraftForm.ROUTINE, "synthetic-owned-picker")
    private val runId = UUID.randomUUID().toString()
    private val rows = (1..30).map { number -> Exercise(id = "synthetic-press-$number",
        name = "Press variation $number", muscleGroup = "Chest", notes = "", isCustom = false, equipment = EquipmentType.DUMBBELL,
        imageKey = "ex_incline_dumbbell_bench_press") }
    private val state = mutableStateOf(ExercisePickerState(query = "press", results = rows,
        title = "Add lifts", mode = ExercisePickerMode.MULTI_ADD, catalog = rows))
    private val showing = mutableStateOf(true)
    private val current = mutableStateOf<OwnedPickerRecovery?>(null)
    private val io = object : DraftFileIO by AtomicDraftFiles {
        var failView = false
        override fun replace(file: File, bytes: ByteArray) {
            if (failView && bytes.toString(Charsets.UTF_8).contains("owned-picker-view")) throw IOException("synthetic Keep failure")
            AtomicDraftFiles.replace(file, bytes)
        }
    }
    @Before fun setup() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val store = DurableDraftStore(directory = temporary.newFolder(), files = io)
        contexts = PickerContextDrafts(store)
        custom = CustomExerciseDrafts(store = store, repository = deps.exerciseRepository,
            maintenance = deps.dbMaintenance, restoreOpen = { false })
    }
    @After fun close() { runBlocking { withTimeout(5_000) { job.cancelAndJoin() } }; deps.close() }
    private fun owns(requested: DraftOwner): Boolean = requested == owner
    private fun fresh() = OwnedPickerRecovery(scope = scope, contextDrafts = contexts, customDrafts = custom,
        repository = deps.exerciseRepository, savedState = SavedStateHandle(), owner = { owner },
        stillOwned = ::owns, ownedAtCommit = ::owns, onQuery = { state.value = state.value.copy(query = it) },
        selectCreated = { false })

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun small20() = journey("small20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscape20() = journey("landscape20")

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun delayedCataloguePreservesAndRestoresAnchor() {
        val retained = PickerContextSnapshot(query = "press", anchor = "synthetic-press-18", index = 19, offsetDp = 12f)
        runBlocking { contexts.open(owner).also { contexts.keep(it, retained) } }
        state.value = state.value.copy(catalog = emptyList(), results = emptyList())
        current.value = fresh()
        mount()
        ready()
        assertEquals(retained, checkNotNull(current.value).view.fields.value)
        compose.runOnIdle { state.value = state.value.copy(catalog = rows, results = rows) }
        compose.settle()
        compose.onNodeWithText("Press variation 18").assertIsDisplayed()
        compose.onNodeWithTag("picker-content").performScrollToNode(hasText("Press variation 26"))
        compose.settle()
        compose.waitUntil(30_000) { checkNotNull(current.value).view.fields.value.index > retained.index }
        assertEquals("press", checkNotNull(current.value).view.fields.value.query)
        capture("small20", "delayed-catalogue-restored")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun removedAnchorFallsBackWhenCatalogueLoadsWithoutMatchingResults() {
        val retained = PickerContextSnapshot(query = "press", anchor = "synthetic-removed", index = 19, offsetDp = 12f)
        runBlocking { contexts.open(owner).also { contexts.keep(it, retained) } }
        state.value = state.value.copy(catalog = emptyList(), results = emptyList())
        current.value = fresh()
        mount()
        ready()
        assertEquals(retained, checkNotNull(current.value).view.fields.value)
        compose.runOnIdle { state.value = state.value.copy(catalog = rows) }
        compose.settle()
        compose.waitUntil(30_000) { checkNotNull(current.value).view.fields.value.anchor != retained.anchor }
        assertEquals("press", checkNotNull(current.value).view.fields.value.query)
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.settle()
        val updated = checkNotNull(current.value).view.fields.value
        assertTrue(updated.index > 0)
        assertTrue(updated.anchor != retained.anchor)
        capture("small20", "removed-anchor-fallback")
    }

    private fun mount() {
        compose.setContent {
            PersonalTrainerTheme {
                Surface {
                    if (showing.value) {
                        val recovery = checkNotNull(current.value)
                        DisposableEffect(recovery) {
                            recovery.view.open()
                            onDispose { recovery.view.closeVisit(); recovery.create.closeVisit() }
                        }
                        ExercisePickerContent(state = state.value, onEvent = { event ->
                            if (event == ExercisePickerEvent.Dismissed) recovery.leave { showing.value = false }
                        }, modifier = Modifier.fillMaxHeight(0.9f), recovery = recovery)
                    }
                }
            }
        }
    }

    private fun journey(profile: String) {
        current.value = fresh()
        mount()
        ready()
        search().performTextReplacement("press")
        compose.settle()
        search().performTextReplacement("press ")
        compose.settle()
        // Both queries trim to the same needle and expose the identical thirty result keys.
        compose.onNodeWithTag("picker-content").performScrollToNode(hasText("Press variation 20"))
        compose.settle()
        capture(profile, "scrolled-before-checkpoint")
        val observed = checkNotNull(current.value)
        println("SYNTHETIC_PICKER_POSITION=" + listOf(
            compose.onNodeWithTag("picker-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),
            observed.view.fields.value.index, observed.view.fields.value.anchor, observed.view.status.value,
        ))
        compose.waitUntil(30_000) { checkNotNull(current.value).view.fields.value.index > 0 }
        val position = checkNotNull(current.value).view.fields.value
        assertEquals("press ", position.query)
        assertTrue(position.anchor?.startsWith("synthetic-press-") == true)
        capture(profile, "authored-and-scrolled")
        compose.onNodeWithTag("picker-action").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { !showing.value }
        compose.settle()
        compose.runOnIdle { current.value = fresh(); showing.value = true }
        ready()
        compose.waitUntil(30_000) { checkNotNull(current.value).view.fields.value.anchor == position.anchor &&
            checkNotNull(current.value).view.fields.value.index > 0 }
        assertEquals("press ", checkNotNull(current.value).view.fields.value.query)
        capture(profile, "reopened-position")
        io.failView = true
        search().performTextReplacement("press  ")
        compose.onNodeWithTag("picker-action").performClick()
        compose.waitUntil(30_000) { checkNotNull(current.value).view.status.value.error != null }
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
        capture(profile, "failed-keep-reachable")
        io.failView = false
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(30_000) { !showing.value }
        assertTrue(runBlocking { deps.database.exerciseDao().getAllCustom().isEmpty() })
    }
    private fun ready() {
        compose.settle()
        compose.waitUntil(30_000) { current.value?.view?.status?.value?.busy == false }
        compose.settle()
    }
    private fun search() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")), useUnmergedTree = true)
    private fun capture(profile: String, phase: String) {
        compose.settle()
        val view = compose.activity.window.decorView
        val bitmap = compose.runOnUiThread { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        } }
        try {
            val target = File("build/screen-renders/owned-picker/$runId/$profile/$phase.png")
            check(target.parentFile.isDirectory || target.parentFile.mkdirs())
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
