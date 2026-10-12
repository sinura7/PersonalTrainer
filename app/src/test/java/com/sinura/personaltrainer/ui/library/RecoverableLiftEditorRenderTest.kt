package com.sinura.personaltrainer.ui.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.ui.components.LoadTypeTags
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
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
import org.robolectric.shadows.ShadowDialog

/** Native Compose layout/semantics, not a substitute for navigation, durable IO or physical IME. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class RecoverableLiftEditorRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val draft = mutableStateOf(ExerciseEditorDraft(name = LONG_NAME, muscleGroup = "Back", notes = "Keep the complete authored notes.\nControl every repetition.", loadType = LoadType.STACK))
    private val status = mutableStateOf(LiftEditorStatus(LiftEditorPhase.EDITING, "Draft resumed"))
    private val runId = UUID.randomUUID().toString()
    private var profile = ""
    private var saves = 0
    private var checks = 0
    private var closes = 0
    private var discards = 0

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f) fun small10() = journey("360x640-font10")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.6f) fun small16() = journey("360x640-font16")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f) fun small20() = journey("360x640-font20")
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1f) fun tall10() = journey("360x800-font10")
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.6f) fun tall16() = journey("360x800-font16")
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 2f) fun tall20() = journey("360x800-font20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f) fun standard10() = journey("412x840-font10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f) fun standard16() = journey("412x840-font16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f) fun standard20() = journey("412x840-font20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f) fun landscape10() = journey("640x360-font10")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f) fun landscape16() = journey("640x360-font16")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f) fun landscape20() = journey("640x360-font20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f) fun tablet10() = journey("600x960-font10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f) fun tablet16() = journey("600x960-font16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f) fun tablet20() = journey("600x960-font20")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f) fun rtl20() = journey("360x640-font20-rtl", rtl = true)

    private fun show(rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReducedMotion provides rtl) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    RecoverableLiftEditorSheet(draft = draft.value, muscleOptions = MuscleGroups.catalog, status = status.value, error = null, libraryReady = true,
                        onDraftChange = { draft.value = it }, onSave = { saves++ }, onCheck = { checks++ },
                        onEditInstead = { status.value = LiftEditorStatus(LiftEditorPhase.EDITING) },
                        onDiscard = { discards++ }, onDismiss = { closes++ }, onLibraryRetry = null)
                }
            }
        }
        compose.settle()
    }

    private fun journey(display: String, rtl: Boolean = false) {
        profile = display
        show(rtl)
        target(LiftEditorTags.CLOSE)
        target(LiftEditorTags.PRIMARY)
        compose.onNodeWithTag(LiftEditorTags.NAME).performScrollTo().assertIsDisplayed().assertTextContains(LONG_NAME)
        capture("resumed-fields")
        for (type in LoadType.entries) {
            val node = compose.onNodeWithTag(LoadTypeTags.chip(type)).performScrollTo().assertIsDisplayed()
            assertEquals(Role.RadioButton, node.fetchSemanticsNode().config[SemanticsProperties.Role])
            node.assertIsEnabled().performClick()
            compose.settle()
            node.assertIsSelected()
            assertEquals(type, draft.value.loadType)
        }
        compose.onNodeWithTag(LiftEditorTags.NOTES).performScrollTo().assertIsDisplayed().assertTextContains(draft.value.notes)
        capture("load-and-notes")
        compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, saves)
        compose.runOnIdle { status.value = LiftEditorStatus(LiftEditorPhase.RETRYABLE, "Not saved yet. Retry keeps these exact values.") }
        compose.settle()
        compose.onNodeWithTag(LiftEditorTags.NAME).performScrollTo().assertIsDisplayed().assertTextContains(LONG_NAME)
        assertFalse(compose.onNodeWithTag(LiftEditorTags.NAME).textLayout().hasVisualOverflow)
        assertFalse(compose.onNodeWithTag(LiftEditorTags.NAME).fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        capture("checked-uncommitted")
        compose.onNodeWithText("Retry save").assertIsDisplayed()
        target(LiftEditorTags.PRIMARY)
        compose.onNodeWithTag(LiftEditorTags.DISCARD).performClick()
        compose.onNodeWithText("Discard this draft?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, discards)
    }

    @Test fun pendingAndFailureActionsHaveDistinctReachableStatesAndBusyCloseCannotHideTheSheet() {
        profile = "360x640-state-actions"
        show()
        val actions = linkedMapOf(
            LiftEditorPhase.OPENING to "Opening…", LiftEditorPhase.SAVING to "Saving…", LiftEditorPhase.CHECKING to "Checking…",
            LiftEditorPhase.CHECKPOINT_FAILED to "Keep draft", LiftEditorPhase.UNCERTAIN to "Check save",
            LiftEditorPhase.DISCARD_UNCERTAIN to "Check discard", LiftEditorPhase.READ_FAILED to "Check again",
            LiftEditorPhase.CONFLICT to "Return to library")
        for ((phase, label) in actions) {
            compose.runOnIdle { status.value = LiftEditorStatus(phase, "Fields remain available for recovery.") }
            compose.settle()
            compose.onNodeWithText(label).assertIsDisplayed()
            target(LiftEditorTags.PRIMARY)
            if (status.value.busy) {
                compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsNotEnabled()
                compose.onNodeWithTag(LiftEditorTags.CLOSE).assertIsNotEnabled()
                compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
                compose.settle()
                assertEquals(0, closes)
                compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsDisplayed()
            } else {
                compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsEnabled().performClick()
                compose.settle()
            }
            capture(phase.name.lowercase())
        }
        assertEquals(4, checks)
        assertEquals(1, closes)
        assertEquals(0, saves)
        assertEquals(0, discards)
        // A non-busy Back asks the owner to flush, but cannot hide the form itself.
        // Keeping this failure state mounted models a refused preservation acknowledgement.
        compose.runOnIdle { status.value = LiftEditorStatus(LiftEditorPhase.CHECKPOINT_FAILED, "Draft not confirmed on this device. Check again before closing.") }
        compose.settle()
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.settle()
        // Material may ask during both transition confirmation and dismissal. The controller
        // reserves the flush synchronously; this sheet assertion concerns retained visibility.
        assertTrue(closes > 1)
        compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Keep draft").assertIsDisplayed()
        capture("failed-preservation-back-stays-visible")
    }

    private fun target(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        assertTrue(node.size.width >= 48f * node.layoutInfo.density.density - 1f)
        assertTrue(node.size.height >= 48f * node.layoutInfo.density.density - 1f)
    }
    private fun capture(stage: String) {
        compose.settle()
        val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
        val bitmap = compose.runOnUiThread { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) } }
        try {
            val file = File("build/screen-renders/custom-lift-recovery/$runId/$profile/$stage.png")
            check(file.parentFile.isDirectory || file.parentFile.mkdirs())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.resolveSibling("$stage.txt").writeText(compose.onAllNodes(SemanticsMatcher("all mounted native nodes") { true }, useUnmergedTree = true)
                .fetchSemanticsNodes().joinToString("\n") { "bounds=${it.boundsInWindow} size=${it.size} config=${it.config}" })
        } finally { bitmap.recycle() }
    }
    private companion object { const val LONG_NAME = "Chest-supported row with deliberate control throughout the complete movement" }
}
