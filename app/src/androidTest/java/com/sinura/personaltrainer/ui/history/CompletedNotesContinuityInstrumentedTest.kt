package com.sinura.personaltrainer.ui.history

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.CompletedNoteKind
import com.sinura.personaltrainer.data.draft.CompletedNoteOwner
import com.sinura.personaltrainer.data.draft.CompletedNoteFieldsCodec
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.activity.ActivityDetailTags
import com.sinura.personaltrainer.ui.components.CompletedNotesTags
import com.sinura.personaltrainer.ui.components.NotesTestTags
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import com.sinura.personaltrainer.ui.units.DateCopy
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Shipping AppNav, real private files and Room; stage/resume is terminated by the runner. */
@RunWith(AndroidJUnit4::class)
class CompletedNotesContinuityInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("notesRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P07 notes")
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var id: String
    private var retained = false
    private val raw = "  Unfinished words\nKeep this exact text  "
    private val before = "Saved by another confirmed visit"
    private val key get() = CompletedNoteOwner(CompletedNoteKind.ACTIVITY, id)
    private val preparation = object : ExternalResource() {
        override fun before() {
            try {
                check(phase in setOf("connected", "stage", "resume"))
                val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
                container = app.container
                marker = File(app.noBackupFilesDir, "synthetic-p07-completed-notes.txt")
                environment.prepare(container)
                app.markColdStartIntroShown()
                read {
                    check(container.workoutRepository.getInProgress() == null)
                    container.preferencesRepository.setOnboardingComplete(true)
                    container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                    container.preferencesRepository.setLaunchPermissionsAsked(true)
                    container.preferencesRepository.markRestAlertsAsked()
                    container.preferencesRepository.markRestBatteryHintShown()
                    if (phase == "resume") {
                        id = marker.readText().trim()
                        check(id.startsWith("synthetic-p07-notes-"))
                        check(container.activityRepository.get(id)?.notes == before)
                    } else {
                        check(!marker.exists())
                        id = "synthetic-p07-notes-${UUID.randomUUID()}"
                        val now = container.time.captureNow()
                        val end = container.time.capture(now.instantMillis - 1_000)
                        val start = container.time.capture(end.instantMillis - 627_000)
                        val write = container.confirmActivity(ActivityDraft(
                            id = id, status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED,
                            title = "Synthetic receipt · complete notes", notes = "Original saved notes",
                            performedStart = start, performedEnd = end,
                            blocks = listOf(CardioBlock(
                                id = "$id-block", sortOrder = 0, type = CardioType.WALK, indoor = false,
                                elapsedSeconds = 627, movingSeconds = null, distanceMeters = 1_234.5,
                                elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
                            )),
                        ), now)
                        check(write is ActivityWrite.Accepted)
                        container.completedNotesDrafts.keep(container.completedNotesDrafts.open(key), raw)
                        check(container.activityRepository.updateCompletedNotes(id, before, now.instantMillis) is ActivityWrite.Accepted)
                    }
                }
            } catch (failure: Throwable) {
                try { cleanup() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        override fun after() { if (!retained) cleanup() else environment.restore() }
    }
    private val scenario = ActivityScenarioRule<MainActivity>(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    private val compose = AndroidComposeTestRule(scenario) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity)
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test fun receiptRetainsConflictingRawNotesAcrossBackAndFreshProcessThenDiscardsOnlyTheDraft() {
        awaitTag("navigation-history")
        compose.onNodeWithTag("navigation-history").performClick()
        awaitTag(HistoryTags.LIST)
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToNode(hasTestTag(HistoryTags.CURRENT))
        compose.onNodeWithTag(HistoryTags.CURRENT).performClick()
        val row = HistoryTags.row(HistoryKind.ACTIVITY, id)
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToNode(hasTestTag(row))
        compose.onNode(hasTestTag(SessionLogTags.ROW) and hasAnyAncestor(hasTestTag(row))).assertIsDisplayed().performClick()
        awaitTag(ActivityDetailTags.CONTENT)
        compose.onNodeWithText(DateCopy.civilDate(read { checkNotNull(container.activityRepository.get(id)).localEpochDay })).assertIsDisplayed()
        compose.onNodeWithTag(ActivityDetailTags.CONTENT).performScrollToNode(hasTestTag(CompletedNotesTags.RECOVERY))
        compose.onNodeWithText(raw).assertIsDisplayed()
        compose.onNodeWithTag(CompletedNotesTags.RETRY).assertIsDisplayed()
        assertEquals(before, read { container.activityRepository.get(id)?.notes })
        capture("restored-conflict-$phase")
        if (phase == "stage") {
            compose.onNodeWithTag(ActivityDetailTags.BACK).performClick()
            awaitTag(HistoryTags.LIST)
            assertEquals(raw, readRaw())
            marker.writeText(id)
            retained = true
            capture("kept-before-process-stop")
            return
        }
        compose.onNodeWithTag(CompletedNotesTags.DISCARD).performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Session notes")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Session notes").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().any {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.EditableText) { androidx.compose.ui.text.AnnotatedString("") }.text == before
        } }
        assertEquals(before, read { container.activityRepository.get(id)?.notes })
        assertNull(readRaw())
        capture("discard-restored-saved-notes")
        compose.onNode(hasSetTextAction()).performTextReplacement("New confirmed note")
        compose.waitUntil(30_000) { read { container.activityRepository.get(id)?.notes == "New confirmed note" } }
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(NotesTestTags.STATUS) and hasText("Notes saved")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(NotesTestTags.STATUS).assertTextEquals("Notes saved")
        capture("confirmed-note")
        compose.onNodeWithTag(ActivityDetailTags.BACK).performClick()
        awaitTag(HistoryTags.LIST)
        assertEquals("New confirmed note", read { container.activityRepository.get(id)?.notes })
        assertNull(readRaw())
    }

    private fun awaitTag(tag: String) {
        try {
            compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            capture("failure-$tag")
            compose.onRoot(useUnmergedTree = true).printToLog("P07-NativeFailure")
            throw failure
        }
    }
    private fun capture(name: String) = NativeArtifacts.write("notes-$name", checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    /** Observe files without claiming/revoking the active form's owning visit. */
    private fun readRaw(): String? = read {
        val binding = container.durableDrafts.bind(DraftOwner(DraftForm.COMPLETED_NOTES, "fields:" + key.key), CompletedNoteFieldsCodec)
        (binding.read() as DraftRead.PresentCheckpoint).checkpoint.draft?.raw
    }
    private fun cleanup() {
        try {
            if (::container.isInitialized && ::id.isInitialized) read {
                container.completedNotesDrafts.discard(container.completedNotesDrafts.open(key))
                val current = container.activityRepository.get(id)
                if (current != null) check(container.activityRepository.deleteCompleted(id) is ActivityWrite.Accepted)
            }
            if (::marker.isInitialized && marker.exists()) check(marker.delete())
        } finally { environment.restore() }
    }
}
