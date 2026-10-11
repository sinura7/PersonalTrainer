package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerProgress
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.ui.activity.StrengthAdder
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ExercisePickerRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val runId = UUID.randomUUID().toString()
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun small10() = journey("small-font10")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f)
    fun small16() = journey("small-font16")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun small20() = journey("small-font20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f)
    fun standard10() = journey("standard-font10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f)
    fun standard16() = journey("standard-font16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f)
    fun standard20() = journey("standard-font20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f)
    fun landscape10() = journey("landscape-font10")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f)
    fun landscape16() = journey("landscape-font16")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f)
    fun landscape20() = journey("landscape-font20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f)
    fun tablet10() = journey("tablet-font10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f)
    fun tablet16() = journey("tablet-font16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f)
    fun tablet20() = journey("tablet-font20")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun rtlReducedMotion() = journey("small-font20-rtl", rtl = true)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
    fun nativeModalOwnsPendingDismissal() {
        val state = mutableStateOf(ExercisePickerState(query = "My press", results = emptyList(), title = "Choose lift", mode = ExercisePickerMode.SINGLE_ADD))
        val events = mutableListOf<ExercisePickerEvent>()
        compose.setContent { PersonalTrainerTheme { ExercisePickerSheet(state = state.value, onEvent = { events += it }) } }
        compose.settle()
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.onNodeWithTag("picker-create-entry").performClick()
        words("Chest")
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithTag("picker-action").assertIsEnabled().performClick()
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(creating = true)) }
        compose.settle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle()
        compose.onNodeWithTag("picker-action").assertIsDisplayed().assertIsNotEnabled()
        assertFalse(events.contains(ExercisePickerEvent.Dismissed))
        capture("native-modal", "pending-create")
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(error = "Creation failed", canRetry = true)) }
        words("Creation failed")
        compose.onNodeWithText("Retry").assertIsDisplayed()
        capture("native-modal", "retry")
    }

    @Test
    fun anEarlierCreationDoesNotDismissTheNextRestoredForm() {
        val state = mutableStateOf(ExercisePickerState(
            query = "First lift", results = emptyList(), title = "Add lifts", mode = ExercisePickerMode.MULTI_ADD,
        ))
        val events = mutableListOf<ExercisePickerEvent>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PersonalTrainerTheme { ExercisePickerContent(state.value, { events += it }, Modifier.fillMaxHeight(0.9f)) }
        }
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.onNodeWithTag("picker-create-entry").performClick()
        words("Chest")
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithTag("picker-action").performClick()
        compose.runOnIdle { state.value = state.value.copy(query = "Second lift", progress = ExercisePickerProgress(createdGeneration = 1)) }
        compose.settle()
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.onNodeWithTag("picker-create-entry").performClick()
        words("Chest")
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithTag("picker-content").performTouchInput { swipeUp() }
        compose.settle()
        compose.onNodeWithTag(LoadTypeTags.chip(LoadType.BODYWEIGHT)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            state.value = state.value.copy(progress = ExercisePickerProgress(createdGeneration = 1, error = "Second creation failed", canRetry = true))
        }
        restoration.emulateSavedInstanceStateRestore()
        words("Second lift")
        words("Second creation failed")
        compose.onNodeWithTag("picker-action").assertIsEnabled().performClick()
        assertEquals(ExercisePickerEvent.Created("Second lift", "Chest", LoadType.BODYWEIGHT), events.last())
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(createdGeneration = 0)) }
        compose.settle()
        words("Second lift")
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(createdGeneration = 1)) }
        compose.settle()
        compose.onNodeWithText("Back to lifts").assertDoesNotExist()
        compose.onNodeWithTag("picker-search").assertIsDisplayed()
    }

    @Test
    fun composerVetoesNativeDismissalBeforeBusyStateRecomposes() {
        var pending = false
        val progress = mutableStateOf(ExercisePickerProgress())
        compose.setContent {
            PersonalTrainerTheme {
                StrengthAdder(
                    catalog = emptyList(), onAdd = { _, _, _ -> },
                    onCreate = { _, _, _ -> pending = true },
                    pickerProgress = progress.value, canDismissPicker = { !pending },
                )
            }
        }
        compose.onNodeWithTag(ComposerTags.CHOOSE_LIFT).performClick()
        compose.settle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")), useUnmergedTree = true)
            .performTextInput("Held lift")
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.onNodeWithTag("picker-create-entry").performClick()
        words("Chest")
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithTag("picker-action").performClick()
        assertTrue(pending)
        // Deliberately leave rendered progress idle: the write owner already accepted it.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle()
        compose.onNodeWithTag("picker-action").assertIsDisplayed()
        compose.onNodeWithText("Back to lifts").assertIsDisplayed()
        compose.runOnIdle {
            pending = false
            progress.value = ExercisePickerProgress(error = "Held creation failed", canRetry = true)
        }
        words("Held creation failed")
        compose.onNodeWithText("Retry").assertIsDisplayed()
        capture("native-modal", "same-frame-retry")
    }

    @Test
    fun composerNeverSubstitutesAnUnrelatedLiftWhileTheCatalogCatchesUp() {
        val old = Exercise("old", "Old lift", "Chest", "", false)
        val created = Exercise("created", "New custom lift", "Chest", "", true)
        val catalog = mutableStateOf(listOf(old))
        val added = mutableListOf<Exercise>()
        compose.setContent {
            PersonalTrainerTheme { StrengthAdder(catalog.value, { lift, _, _ -> added += lift }, created = created) }
        }
        compose.settle()
        compose.onNodeWithText(old.name).assertDoesNotExist()
        compose.onNodeWithTag(ComposerTags.ADD_SET).assertIsNotEnabled()
        compose.runOnIdle { catalog.value = listOf(old, created) }
        compose.settle()
        compose.onNodeWithText(created.name).assertIsDisplayed()
        compose.onNodeWithTag(ComposerTags.ADD_SET).assertIsEnabled().performClick()
        assertEquals(listOf(created), added)
    }

    @Test
    fun anEmptyComposerCatalogCanOpenThePicker() {
        compose.setContent { PersonalTrainerTheme { StrengthAdder(emptyList(), { _, _, _ -> }) } }
        compose.onNodeWithTag(ComposerTags.CHOOSE_LIFT).assertIsDisplayed().performClick()
        compose.settle()
        compose.onNodeWithTag("picker-search").assertIsDisplayed()
    }

    @Test
    fun duplicateRecoverySelectsTheCanonicalExistingLiftInEveryMode() {
        val existing = Exercise("canonical", "Bench press", "Chest", "", false)
        val state = mutableStateOf(ExercisePickerState(
            query = "bench", results = listOf(existing), title = "Choose lift", mode = ExercisePickerMode.SINGLE_ADD,
            progress = ExercisePickerProgress(error = "That name already belongs to a lift.", existing = existing),
        ))
        val events = mutableListOf<ExercisePickerEvent>()
        compose.setContent { PersonalTrainerTheme { ExercisePickerContent(state.value, { events += it }, Modifier.fillMaxHeight(0.9f)) } }
        for (mode in ExercisePickerMode.entries) {
            compose.runOnIdle { state.value = state.value.copy(mode = mode) }
            words("Use existing lift: ${existing.name}")
            compose.onNodeWithText("Use existing lift: ${existing.name}").performClick()
            compose.settle()
            assertEquals(ExercisePickerEvent.Selected(existing), events.last())
        }
    }

    private fun journey(profile: String, rtl: Boolean = false) {
        val press = Exercise(
            id = "ex-incline-dumbbell-bench-press", name = "Incline dumbbell bench press",
            muscleGroup = "Chest", notes = "", isCustom = false, equipment = EquipmentType.DUMBBELL,
            imageKey = "ex_incline_dumbbell_bench_press",
        )
        val state = mutableStateOf(ExercisePickerState(
            query = "press", results = listOf(press), title = "Add lifts", mode = ExercisePickerMode.MULTI_ADD,
        ))
        val events = mutableListOf<ExercisePickerEvent>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    Surface {
                        ExercisePickerContent(state.value, { events += it }, Modifier.fillMaxHeight(0.9f))
                    }
                }
            }
        }
        words(press.name)
        capture(profile, "browse")
        compose.onNodeWithTag("picker-content").performScrollToNode(hasTestTag("picker-create-entry"))
        compose.onNodeWithTag("picker-create-entry").assertIsDisplayed().performClick()
        words("press")
        words("Saved to your Library, then selected here. Cancelling later keeps the Library lift.")
        compose.onNodeWithTag("picker-action").assertIsDisplayed()
        capture(profile, "create")
        compose.onNodeWithTag("picker-action").assertIsNotEnabled()
        words("Chest")
        compose.onNodeWithText("Chest").performClick()
        compose.settle()
        compose.onNodeWithTag("picker-action").assertIsEnabled().performClick()
        compose.settle()
        val created = events.last() as ExercisePickerEvent.Created
        assertEquals("press", created.name)
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(creating = true)) }
        compose.settle()
        compose.onNodeWithTag("picker-action").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Back to lifts").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(error = "Could not create that lift. Try again.", canRetry = true)) }
        words("Could not create that lift. Try again.")
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed()
        capture(profile, "retry")
        compose.onNodeWithText("Retry").performClick()
        assertEquals(ExercisePickerEvent.Retry, events.last())
        compose.runOnIdle { state.value = state.value.copy(progress = ExercisePickerProgress(createdGeneration = 1)) }
        compose.settle()
        words(press.name)
        // Single-add and swap have the same visible recovery as multi-add.
        for (mode in listOf(ExercisePickerMode.SINGLE_ADD, ExercisePickerMode.SWAP)) {
            compose.runOnIdle { state.value = state.value.copy(mode = mode, progress = ExercisePickerProgress(error = "Selection failed", canRetry = true)) }
            words("Selection failed")
            compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(ExercisePickerEvent.Retry, events.last())
        }
        compose.runOnIdle {
            state.value = state.value.copy(progress = ExercisePickerProgress(error = "That lift already exists.", existing = press))
        }
        val existingAction = "Use existing lift: ${press.name}"
        words(existingAction)
        capture(profile, "duplicate")
        compose.onNodeWithText(existingAction).performClick()
        compose.settle()
        assertEquals(ExercisePickerEvent.Selected(press), events.last())
    }

    private fun words(text: String) {
        compose.settle()
        compose.onNodeWithTag("picker-content").performScrollToNode(hasText(text))
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val layout = node.textLayout()
        val size = node.fetchSemanticsNode().size
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("Clipped $text", glyph.left >= -1f && glyph.right <= size.width + 1f && glyph.top >= -1f && glyph.bottom <= size.height + 1f)
        }
    }

    private fun capture(profile: String, stage: String) {
        compose.settle()
        val view = compose.activity.window.decorView
        val frame = compose.runOnUiThread {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                for (window in WindowInspector.getGlobalWindowViews().filter { it.isShown && it !== view }) {
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
            val file = File("build/screen-renders/exercise-picker/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { frame.recycle() }
    }
}
