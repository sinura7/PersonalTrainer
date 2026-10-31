package com.sinura.personaltrainer.ui.library

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.data.repository.DeleteExerciseResult
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.ui.components.LoadTypeTags
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Actual AppNav and private app graph. Stage/resume invocations span an external force-stop. */
@RunWith(AndroidJUnit4::class)
class CustomLiftRecoveryInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private var ownerId: String? = null
    private var stageComplete = false
    private val phase = InstrumentationRegistry.getArguments().getString("draftRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Custom lift recovery")
    private val seed = object : ExternalResource() {
        override fun before() {
            check(phase in setOf("connected", "stage", "resume", "stage-pending", "resume-pending"))
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            marker = File(app.noBackupFilesDir, "synthetic-custom-lift-recovery-owner.txt")
            try { read {
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestBatteryHintShown()
                container.preferencesRepository.markRestAlertsAsked()
                val handle = container.customExerciseDrafts.open(null, "Back")
                if (phase.startsWith("resume")) {
                    check(marker.isFile)
                    ownerId = marker.readText()
                    check(handle.authored.createId == ownerId && handle.authored.name == RAW_NAME)
                } else {
                    check(!marker.exists() && handle.authored.name.isBlank() && handle.authored.notes.isBlank() && handle.checkpoint.pending == null)
                    ownerId = handle.authored.createId
                }
                check(container.exerciseRepository.customDraftRecord(checkNotNull(ownerId))?.row == null)
            } } catch (failure: Throwable) {
                try { cleanup() } catch (secondary: Throwable) { failure.addSuppressed(secondary) }
                throw failure
            }
        }
        override fun after() { cleanup() }
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(seed).around(compose)

    @Test fun customLiftReturnsWithExactFieldsAndCreatesThenEditsOneStableRecord() {
        val history = read { container.workoutRepository.observeHistory().first() }
        awaitTag("navigation-routines")
        tap("navigation-routines")
        awaitTag(PlanTags.ROUTINES_ENTRY)
        tap(PlanTags.ROUTINES_ENTRY)
        awaitTag(PlanTags.LIBRARY)
        tap(PlanTags.LIBRARY)
        awaitTag(LibraryTags.FAB)
        tap(LibraryTags.FAB)
        if (phase == "resume-pending") {
            compose.waitUntil(30_000) { compose.onAllNodesWithTag(LiftEditorTags.PRIMARY).fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { text -> text.text == "Retry save" }
            } }
        } else awaitEnabled(LiftEditorTags.NAME)
        if (!phase.startsWith("resume")) {
            compose.onNodeWithTag(LiftEditorTags.NAME).performTextReplacement(RAW_NAME)
            compose.onNodeWithTag(LiftEditorTags.MUSCLE).performScrollTo().performClick()
            val backSelected = compose.onAllNodesWithText("✓ Back").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithText(if (backSelected) "✓ Back" else "Back").performClick()
            compose.onNodeWithTag(LiftEditorTags.NOTES).performScrollTo().performTextReplacement(RAW_NOTES)
            compose.onNodeWithTag(LoadTypeTags.chip(LoadType.STACK)).performScrollTo().performClick()
            hideKeyboard()
            tap(LiftEditorTags.CLOSE)
            awaitGone(LiftEditorTags.NAME)
            scenario.scenario.recreate()
            awaitTag(LibraryTags.FAB)
            tap(LibraryTags.FAB)
            awaitEnabled(LiftEditorTags.NAME)
        }
        compose.onNodeWithTag(LiftEditorTags.NAME).performScrollTo().assertTextContains(RAW_NAME)
        compose.onNodeWithTag(LiftEditorTags.NOTES).performScrollTo().assertTextContains(RAW_NOTES)
        compose.onNodeWithTag(LoadTypeTags.chip(LoadType.STACK)).performScrollTo().assertIsSelected()
        assertEquals("Back", read { container.customExerciseDrafts.open(null, "").authored.muscle })
        assertNull(read { container.exerciseRepository.customDraftRecord(checkNotNull(ownerId))?.row })
        if (phase != "resume-pending") {
            compose.onNodeWithTag(LiftEditorTags.NAME).performScrollTo().performClick()
            compose.waitUntil(30_000) { keyboardShown() }
            compose.onNodeWithTag(LiftEditorTags.PRIMARY).assertIsDisplayed()
            capture("$phase-ime-primary-reachable")
        }
        capture("$phase-resumed")
        if (phase.startsWith("stage")) {
            tap(LiftEditorTags.CLOSE)
            awaitGone(LiftEditorTags.NAME)
            read {
                val handle = container.customExerciseDrafts.open(null, "")
                assertEquals(RAW_NAME, handle.authored.name)
                if (phase == "stage-pending") container.customExerciseDrafts.admit(handle, handle.authored)
            }
            marker.writeText(checkNotNull(ownerId))
            stageComplete = true
            return
        }
        awaitEnabled(LiftEditorTags.PRIMARY)
        tap(LiftEditorTags.PRIMARY)
        awaitGone(LiftEditorTags.NAME)
        val saved = read { container.exerciseRepository.customDraftRecord(checkNotNull(ownerId))?.row!! }
        assertEquals(RAW_NAME.trim(), saved.name)
        assertEquals(RAW_NOTES.trim(), saved.notes)
        assertEquals(LoadType.STACK.name, saved.loadType)
        assertEquals("Back", saved.muscleGroup)
        assertEquals(1, read { container.exerciseRepository.observeAll().first().count { it.id == ownerId } })
        val search = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)), useUnmergedTree = true)
        search.performTextReplacement(saved.name)
        hideKeyboard()
        awaitTag(LibraryTags.options(saved.id))
        compose.onNodeWithTag(LibraryTags.options(saved.id)).performScrollTo().performClick()
        compose.onNodeWithText("Edit lift").performClick()
        awaitEnabled(LiftEditorTags.NAME)
        compose.onNodeWithTag(LiftEditorTags.NOTES).performScrollTo().performTextReplacement("")
        compose.onNodeWithTag(LoadTypeTags.chip(LoadType.BODYWEIGHT)).performScrollTo().performClick()
        hideKeyboard()
        tap(LiftEditorTags.CLOSE)
        awaitGone(LiftEditorTags.NAME)
        compose.onNodeWithTag(LibraryTags.options(saved.id)).performScrollTo().performClick()
        compose.onNodeWithText("Edit lift").performClick()
        awaitEnabled(LiftEditorTags.NAME)
        assertEquals("", compose.onNodeWithTag(LiftEditorTags.NOTES).performScrollTo().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag(LoadTypeTags.chip(LoadType.BODYWEIGHT)).performScrollTo().assertIsSelected()
        hideKeyboard()
        capture("$phase-edit-resumed")
        tap(LiftEditorTags.PRIMARY)
        awaitGone(LiftEditorTags.NAME)
        val corrected = read { container.exerciseRepository.customDraftRecord(saved.id)?.row!! }
        assertEquals(saved.id, corrected.id)
        assertEquals(saved.name, corrected.name)
        assertEquals("", corrected.notes)
        assertEquals(LoadType.BODYWEIGHT.name, corrected.loadType)
        assertEquals(history, read { container.workoutRepository.observeHistory().first() })
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun awaitEnabled(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) && (tag != LiftEditorTags.NAME || it.config.contains(SemanticsActions.SetText)) } }
        compose.waitForIdle()
    }
    private fun awaitGone(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick(); compose.waitForIdle() }
    private fun keyboardShown(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys input_method"))
            .use { it.bufferedReader().readText().contains("mInputShown=true") }
    }
    private fun hideKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val input = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys input_method"))
            .use { it.bufferedReader().readText() }
        if (input.contains("mInputShown=true")) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    private fun capture(stage: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val file = File(app.getExternalFilesDir(null), "ux-context/custom-lift-recovery/$stage.png")
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun cleanup() {
        try {
            if (::container.isInitialized && !stageComplete && ownerId != null) read {
                val row = container.exerciseRepository.customDraftRecord(checkNotNull(ownerId))?.row
                check(row == null || row.isCustom && row.name == RAW_NAME.trim())
                if (row != null) {
                    val edit = container.customExerciseDrafts.open(row.id, "")
                    check(edit.authored.editId == row.id && edit.authored.name.trim() == RAW_NAME.trim())
                    container.customExerciseDrafts.discard(edit)
                    assertEquals(DeleteExerciseResult.Deleted, container.exerciseRepository.deleteCustom(row.id))
                }
                val handle = container.customExerciseDrafts.open(null, "")
                check(handle.authored.name.isBlank() || handle.authored.createId == ownerId && handle.authored.name == RAW_NAME)
                if (handle.authored.createId == ownerId || handle.authored.name.isBlank() && handle.authored.notes.isBlank()) container.customExerciseDrafts.discard(handle)
                if (::marker.isInitialized && marker.exists()) check(marker.delete())
            }
        } finally { environment.restore() }
    }
    private companion object {
        const val RAW_NAME = "  Custom lift recovery chest-supported row  "
        const val RAW_NOTES = "  Keep these complete notes.\nExact authored values.  "
    }
}
