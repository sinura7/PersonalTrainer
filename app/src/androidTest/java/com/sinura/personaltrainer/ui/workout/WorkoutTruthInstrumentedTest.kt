package com.sinura.personaltrainer.ui.workout

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.ui.components.NotesTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.SessionDetailViewModel
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.io.FileInputStream
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** Focused UX23 native truth checks. Android SQLite and actual windows/IME; synthetic data only. */
@RunWith(AndroidJUnit4::class)
class WorkoutTruthInstrumentedTest {
    private val compose = createComposeRule()
    private lateinit var fixture: NativeWorkoutTruthFixture
    private val windowRule = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            fixture = NativeWorkoutTruthFixture()
            val environment = NativeWorkoutFixtureEnvironment("UX23 native fixture")
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val previousFlags = automation.serviceInfo.flags
            val previousFont = shell("settings get system font_scale").trim()
            val previousKeyboard = shell("settings get secure show_ime_with_hard_keyboard").trim()
            try {
                environment.prepare(fixture.owner)
                automation.serviceInfo = automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
                shell("settings put system font_scale 2.0")
                shell("settings put secure show_ime_with_hard_keyboard 1")
                awaitFont(2f)
                base.evaluate()
            } finally {
                fixture.close()
                automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
                shell(if (previousFont.toFloatOrNull() != null) "settings put system font_scale $previousFont" else "settings delete system font_scale")
                shell(if (previousKeyboard in setOf("0", "1")) "settings put secure show_ime_with_hard_keyboard $previousKeyboard" else "settings delete secure show_ime_with_hard_keyboard")
                awaitFont(previousFont.toFloatOrNull() ?: 1f)
                environment.restore()
            }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(windowRule).around(compose)

    @Test fun heldWriteDoesNotClaimSavedAndLatestCorrectionWins() {
        fixture.seed()
        val original = fixture.stored()
        val vm = mountActive()
        openLiveNotes()
        fixture.writeGate = CompletableDeferred()
        noteField().performTextReplacement("First pending sentence")
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.SAVING }
        compose.onNodeWithTag(NotesTestTags.STATUS).assertTextContains("Saving notes…")
        assertEquals("Stored original", fixture.stored().notes)
        compose.onNodeWithText("Notes saved").assertDoesNotExist()
        noteField().performTextReplacement("Corrected final sentence")
        capture("notes-held-correction-font2-ime")
        fixture.writeGate!!.complete(Unit)
        compose.waitUntil(15_000) { fixture.stored().notes == "Corrected final sentence" && vm.uiState.value.notesSave.status == NotesSaveStatus.SAVED }
        compose.onNodeWithTag(NotesTestTags.STATUS).assertTextContains("Notes saved")
        assertEquals(listOf("First pending sentence", "Corrected final sentence"), fixture.attemptedWrites.toList())
        assertEquals(original.sets, fixture.stored().sets)
        assertEquals(original.startedAt, fixture.stored().startedAt)
        assertNull(fixture.stored().finishedAt)
    }

    @Test fun failedLiveNotesKeepExactTextAndRetryCommitsOnlyNotes() {
        fixture.seed()
        val original = fixture.stored()
        val vm = mountActive()
        openLiveNotes()
        fixture.failWrites.set(1)
        noteField().performTextReplacement("  Exact retained sentence  ")
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.FAILED }
        awaitKeyboard()
        compose.onNodeWithTag(NotesTestTags.STATUS).assertTextContains("Notes not saved. Your text is kept here.")
        assertEquals("  Exact retained sentence  ", vm.uiState.value.notes)
        assertEquals(original, fixture.stored())
        val retry = compose.onNodeWithTag(NotesTestTags.RETRY).performScrollTo()
        assertNativeAction(retry, 48)
        capture("notes-failed-retry-font2-ime")
        retry.performTouchInput { click() }
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.SAVED }
        assertEquals("Exact retained sentence", fixture.stored().notes)
        assertEquals(original.sets, fixture.stored().sets)
        assertEquals(original.startedAt, fixture.stored().startedAt)
        assertNull(fixture.stored().finishedAt)
        assertEquals(listOf("Exact retained sentence", "Exact retained sentence"), fixture.attemptedWrites.toList())
    }

    @Test fun finishWaitsForTheHeldLatestNoteAndKeepsTheSetIdentity() {
        fixture.seed()
        val original = fixture.stored()
        var finished: String? = null
        val vm = mountActive { finished = it }
        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText("Session notes", substring = true).performClick()
        noteField().performClick()
        awaitKeyboard()
        fixture.writeGate = CompletableDeferred()
        noteField().performTextReplacement("Finish barrier sentence")
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.SAVING }
        // Finish is explicitly allowed while ordinary notes are pending/saving. Its
        // ordered writer barrier must complete before the finished row can be claimed.
        val save = compose.onNodeWithTag(EndWorkoutTags.SAVE)
        assertNativeAction(save, 48)
        save.performTouchInput { click() }
        assertNull(finished)
        assertNull(fixture.stored().finishedAt)
        assertEquals("Stored original", fixture.stored().notes)
        fixture.writeGate!!.complete(Unit)
        compose.waitUntil(15_000) { finished == fixture.sessionId }
        val stored = fixture.stored()
        assertEquals("Finish barrier sentence", stored.notes)
        assertEquals(original.sets, stored.sets)
        assertEquals(original.startedAt, stored.startedAt)
        assertTrue(stored.finishedAt != null)
        capture("notes-finish-confirmed-font2")
    }

    @Test fun historyFailureRetryAndClearVerifyTheStoredFinishedRow() {
        fixture.seed(finished = true)
        val original = fixture.stored()
        val vm = mountDetail()
        compose.onNodeWithText("Session notes", substring = true).performScrollTo().performClick()
        noteField().performClick()
        awaitKeyboard()
        fixture.failWrites.set(1)
        noteField().performTextReplacement("History correction")
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.FAILED }
        assertEquals(original, fixture.stored())
        val retry = compose.onNodeWithTag(NotesTestTags.RETRY).performScrollTo()
        assertNativeAction(retry, 48)
        retry.performTouchInput { click() }
        compose.waitUntil(15_000) { vm.uiState.value.notesSave.status == NotesSaveStatus.SAVED }
        assertEquals("History correction", fixture.stored().notes)
        noteField().performTextReplacement("")
        compose.waitUntil(15_000) { fixture.stored().notes.isEmpty() && vm.uiState.value.notesSave.status == NotesSaveStatus.SAVED }
        compose.onNodeWithTag(NotesTestTags.STATUS).performScrollTo().assertTextContains("Notes cleared")
        assertEquals(original.sets, fixture.stored().sets)
        assertEquals(original.startedAt, fixture.stored().startedAt)
        assertEquals(original.finishedAt, fixture.stored().finishedAt)
        capture("history-notes-cleared-font2-ime")
    }

    @Test fun missingDetailDoesNotClaimHistoryIsSafeOrOfferReadRetry() {
        fixture.seed(finished = true)
        val original = fixture.stored()
        mountDetail("ux23-absent-row")
        compose.onNodeWithText("Session not found").assertIsDisplayed()
        compose.onNodeWithText("This workout is no longer on this phone.").assertIsDisplayed()
        compose.onNodeWithTag(SessionDetailTestTags.RETRY).assertDoesNotExist()
        compose.onNodeWithText("Your history is not lost", substring = true).assertDoesNotExist()
        assertEquals(original, fixture.stored())
        capture("detail-missing-font2")
    }

    @Test fun failedDetailReadIsRetryableAndRecoversTheSameFinishedRows() {
        fixture.seed(finished = true)
        val original = fixture.stored()
        fixture.failReads.set(true)
        val vm = mountDetail(waitForRow = false)
        compose.waitUntil(15_000) { vm.uiState.value.failed }
        compose.onNodeWithText(DataHealthCopy.SESSION_TITLE).assertIsDisplayed()
        compose.onNodeWithText("Session not found").assertDoesNotExist()
        capture("detail-read-failed-font2")
        fixture.failReads.set(false)
        compose.onNodeWithTag(SessionDetailTestTags.RETRY).assertIsEnabled().performTouchInput { click() }
        compose.waitUntil(15_000) { vm.uiState.value.session?.id == fixture.sessionId && !vm.uiState.value.failed }
        assertEquals(original, fixture.stored())
        capture("detail-read-recovered-font2")
    }

    @Test fun missingLiveWorkoutUsesFactualCopyAndNeverOffersLogOrReadRetry() {
        fixture.seed()
        val original = fixture.stored()
        val vm = mountActive(id = "ux23-absent-row", waitForRow = false)
        compose.waitUntil(15_000) { vm.uiState.value.loadState == SessionLoadState.MISSING }
        compose.onNodeWithText("Workout not live").assertIsDisplayed()
        compose.onNodeWithText("This workout is not running. If you finished it, look in History.").assertIsDisplayed()
        compose.onNodeWithText("Back to home").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertDoesNotExist()
        compose.onNodeWithText("Your history is not lost", substring = true).assertDoesNotExist()
        assertEquals(original, fixture.stored())
        capture("live-missing-font2")
    }

    @Test fun failedLiveReadOffersRetryAndRecoversTheSameDraftAndRows() {
        fixture.seed()
        val original = fixture.stored()
        fixture.failReads.set(true)
        val vm = mountActive(waitForRow = false)
        compose.waitUntil(15_000) { vm.uiState.value.loadState == SessionLoadState.FAILED }
        compose.onNodeWithText("Workout unavailable").assertIsDisplayed()
        compose.onNodeWithText("Workout not live").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsEnabled().performTouchInput { click() }
        // The read is still refused: Retry must not turn failure into a missing row.
        compose.waitUntil(15_000) { vm.uiState.value.loadState == SessionLoadState.FAILED }
        capture("live-read-failed-font2")
        fixture.failReads.set(false)
        compose.onNodeWithText("Retry").performTouchInput { click() }
        compose.waitUntil(15_000) { vm.uiState.value.session?.id == fixture.sessionId && vm.uiState.value.loadState == SessionLoadState.FOUND }
        assertEquals("Stored original", vm.uiState.value.notes)
        assertEquals(original, fixture.stored())
        capture("live-read-recovered-font2")
    }

    @Test fun actualAddASetWhyKeepPreservesManualDraftAndStoredRows() = addASetWhy(use = false)
    @Test fun actualAddASetWhyUseOnlyFillsDraftAndNeverLogsOrStartsRest() = addASetWhy(use = true)

    private fun addASetWhy(use: Boolean) {
        fixture.seed(savedSets = 2)
        val vm = mountActive()
        compose.waitUntil(15_000) { vm.tempoCoachTip.value is TempoCoachTip.AddASet }
        compose.runOnIdle { vm.setWeight(87.5); vm.setReps(12); vm.setRpe(8) }
        compose.waitUntil(15_000) {
            val entered = vm.uiState.value.draft
            entered.weightKg == 87.5 && entered.reps == 12 && entered.rpe == 8 &&
                vm.tempoCoachTip.value is TempoCoachTip.AddASet
        }
        val offer = vm.tempoCoachTip.value as TempoCoachTip.AddASet
        val original = fixture.stored()
        val manual = vm.uiState.value.draft
        assertTrue("The entire manual entry must arrive before opening Why",
            manual.weightKg == 87.5 && manual.reps == 12 && manual.rpe == 8)
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.MICRO_REC_WHY))
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_SUMMARY).performScrollTo()
            .assertTextContains("Your planned sets are complete. 4 of 5 readiness checks support one extra set.")
        for (label in listOf("Effort", "Today's sets", "Last session", "Weekly volume", "Recent volume")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("No earlier session to compare.").performScrollTo().assertIsDisplayed()
        for (fact in listOf("Average RPE 7 · within the 7.5 guide.", "No failed or cut-short sets flagged.",
            "Estimate 2 sets · below the 10-set guide.", "No recent-volume or block-week support.")) {
            compose.onNodeWithText(fact).performScrollTo().assertIsDisplayed()
        }
        val keep = compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_KEEP)
        val apply = compose.onNodeWithTag(WorkoutTestTags.TEMPO_WHY_USE)
        assertNativeAction(keep, 48)
        // The draft-only sheet action retains its existing 64 dp floor;
        // the workout's recording action has the separate 72 dp contract.
        assertNativeAction(apply, 64)
        capture("add-a-set-why-${if (use) "use" else "keep"}-font2")
        (if (use) apply else keep).performTouchInput { click() }
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(WorkoutTestTags.TEMPO_WHY_SHEET)).fetchSemanticsNodes().isEmpty() }
        if (use) {
            compose.waitUntil(15_000) { vm.extraSetRequested.value && vm.uiState.value.draft.weightKg == offer.seedRec.nextWeightKg }
            assertEquals(offer.seedRec.nextReps, vm.uiState.value.draft.reps)
            assertEquals(offer.seedRec.nextRpe, vm.uiState.value.draft.rpe)
        } else {
            assertEquals(manual, vm.uiState.value.draft)
            assertFalse(vm.extraSetRequested.value)
        }
        assertEquals(original, fixture.stored())
        assertFalse(fixture.timer.snapshot.value.running)
        assertTrue(fixture.attemptedWrites.isEmpty())
    }

    private fun mountActive(id: String = fixture.sessionId, waitForRow: Boolean = true, onFinished: (String) -> Unit = {}): ActiveWorkoutViewModel {
        val vm = fixture.active(id)
        GoldenCapture.mountDevice(compose, fontScale = 2f) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                Scaffold { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                    ActiveWorkoutScreen(onExit = {}, onFinished = onFinished, viewModel = vm,
                        restNotificationsEnabledOverride = true)
                } }
            }
        }
        compose.waitUntil(15_000) { if (waitForRow) vm.uiState.value.session?.id == id && vm.uiState.value.notes == "Stored original"
            else vm.uiState.value.loadState != SessionLoadState.LOADING }
        return vm
    }
    private fun mountDetail(id: String = fixture.sessionId, waitForRow: Boolean = true): SessionDetailViewModel {
        val vm = fixture.detail(id)
        GoldenCapture.mountDevice(compose, fontScale = 2f) {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                SessionDetailScreen(onBack = {}, onOpenExercise = {}, onOpenActiveSession = {}, viewModel = vm)
            }
        }
        compose.waitUntil(15_000) { !vm.uiState.value.isLoading && (!waitForRow || vm.uiState.value.session?.id == id || vm.uiState.value.missing) }
        return vm
    }
    private fun openLiveNotes() {
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.onNodeWithText("Session notes").performClick()
        noteField().performClick()
        awaitKeyboard()
    }
    private fun noteField() = compose.onNode(hasSetTextAction())
    private fun assertNativeAction(action: SemanticsNodeInteraction, minimumDp: Int) {
        val node = action.assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        assertEquals(2f, node.layoutInfo.density.fontScale, 0.01f)
        assertTrue("$minimumDp dp visible action required: $bounds", bounds.height / node.layoutInfo.density.density >= minimumDp - .5f)
        assertTrue("Action must be fully unclipped", bounds.width >= node.size.width - 1 && bounds.height >= node.size.height - 1)
        val dialogs = compose.onAllNodes(isDialog()).fetchSemanticsNodes()
        if (dialogs.size == 1) {
            val dialog = dialogs.single().boundsInRoot
            assertTrue("Action must fit native modal", bounds.left >= dialog.left && bounds.right <= dialog.right && bounds.top >= dialog.top && bounds.bottom <= dialog.bottom)
        }
        println("UX23_NATIVE_ACTION bounds=$bounds size=${node.size} density=${node.layoutInfo.density}")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        NativeArtifacts.write("ux23-$name-api${Build.VERSION.SDK_INT}", bitmap)
        bitmap.recycle()
    }
    private fun awaitKeyboard() = compose.waitUntil(15_000) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any { window ->
            val bounds = AndroidRect()
            window.getBoundsInScreen(bounds)
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && bounds.height() > 200
        }
    }
    private fun awaitFont(expected: Float) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (fixture.app.resources.configuration.fontScale != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertEquals(expected, fixture.app.resources.configuration.fontScale, .01f)
    }
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        .use { pipe -> FileInputStream(pipe.fileDescriptor).use { String(it.readBytes()) } }
}
