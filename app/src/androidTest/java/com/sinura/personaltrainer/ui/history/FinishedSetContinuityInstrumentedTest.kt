package com.sinura.personaltrainer.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.DraftRead
import com.sinura.personaltrainer.data.draft.FinishedSetDraftCodec
import com.sinura.personaltrainer.data.draft.FinishedSetDraft
import com.sinura.personaltrainer.data.draft.FinishedSetDrafts
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import com.sinura.personaltrainer.workout.WorkoutNumberDraft
import com.sinura.personaltrainer.workout.WorkoutNumberField
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Shipping AppNav and private IO, staged before the runner performs actual force-stop. */
@RunWith(AndroidJUnit4::class)
class FinishedSetContinuityInstrumentedTest {
    private val phase = InstrumentationRegistry.getArguments().getString("setRecoveryPhase") ?: "connected"
    private val environment = NativeWorkoutFixtureEnvironment("Synthetic P07 set")
    private lateinit var container: AppContainer
    private lateinit var marker: File
    private lateinit var sessionId: String
    private lateinit var routineId: String
    private lateinit var setId: String
    private var retained = false
    private val raw = "225."
    private val completedRaw = "225.5"
    private val preparation = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            marker = File(app.noBackupFilesDir, "synthetic-p07-finished-set.txt")
            environment.prepare(container)
            app.markColdStartIntroShown()
            try { read {
                check(container.workoutRepository.getInProgress() == null)
                check(container.activityRepository.getLive() == null)
                container.preferencesRepository.setOnboardingComplete(true)
                container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
                container.preferencesRepository.setLaunchPermissionsAsked(true)
                container.preferencesRepository.markRestAlertsAsked()
                container.preferencesRepository.markRestBatteryHintShown()
                container.preferencesRepository.setWeightUnit(if (phase == "resume") WeightUnit.KG else WeightUnit.LBS)
                if (phase == "resume") {
                    val ids = marker.readLines(); check(ids.size == 3)
                    sessionId = ids[0]; routineId = ids[1]; setId = ids[2]
                    check(container.workoutRepository.getSession(sessionId)?.routineName?.startsWith("Synthetic P07 set") == true)
                } else {
                    check(!marker.exists())
                    val exercise = withTimeout(15_000) {
                        container.exerciseRepository.observeAll().first { list -> list.any { it.id == "ex-barbell-back-squat" } }
                            .single { it.id == "ex-barbell-back-squat" }
                    }
                    val routine = container.routineRepository.create("Synthetic P07 set ${UUID.randomUUID()}")
                    routineId = routine.id
                    container.routineRepository.addExercise(routine.id, exercise, 1, 5, 100.0, 120)
                    val started = container.workoutRepository.startRoutine(checkNotNull(container.routineRepository.getById(routine.id)))
                    sessionId = started.id
                    container.workoutRepository.logSet(sessionId, exercise.id, 100.0, 5, 8, false)
                    container.workoutRepository.finishSession(sessionId, "Synthetic retained correction")
                    setId = checkNotNull(container.workoutRepository.getSession(sessionId)).sets.single().id
                    if (phase == "conflict") {
                        val finished = checkNotNull(container.workoutRepository.getSession(sessionId))
                        val row = finished.sets.single()
                        val before = WorkoutSetValues.from(row)
                        val command = WorkoutSetSave(sessionId = sessionId, exerciseId = row.exerciseId,
                            setId = row.id, completedAt = row.completedAt, values = before, original = before)
                        container.finishedSetDrafts.keep(container.finishedSetDrafts.open(sessionId),
                            FinishedSetDraft(FinishedSetDrafts.stamp(finished), command, before.copy(rpe = 9),
                                listOf(WorkoutNumberDraft(exerciseId = row.exerciseId, field = WorkoutNumberField.WEIGHT,
                                    text = raw, weightUnit = WeightUnit.LBS,
                                    selectionStart = raw.length, selectionEnd = raw.length, open = true))))
                        container.workoutRepository.saveFinishedSet(command.copy(values = before.copy(weightKg = 110.0)))
                    }
                }
            } } catch (failure: Throwable) {
                try { cleanup() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
        override fun after() { if (retained) environment.restore() else cleanup() }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(preparation).around(compose)

    @Test fun historicalRawUnitReopensAndCorrectionKeepsTheSameSavedIdentity() {
        awaitTag("navigation-history"); compose.onNodeWithTag("navigation-history").performClick()
        awaitTag(HistoryTags.LIST)
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToNode(hasTestTag(HistoryTags.CURRENT))
        compose.onNodeWithTag(HistoryTags.CURRENT).performClick()
        val row = HistoryTags.row(HistoryKind.WORKOUT, sessionId)
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToNode(hasTestTag(row))
        compose.onNode(hasTestTag(SessionLogTags.ROW) and hasAnyAncestor(hasTestTag(row))).performClick()
        if (phase == "conflict") {
            awaitTag(SetEditTestTags.CONTENT)
            compose.onNodeWithTag(NumberEntryTags.FIELD).assertDoesNotExist()
            val discard = hasTestTag("historical-set-discard") and hasAnyAncestor(hasTestTag(SetEditTestTags.CONTENT))
            compose.onNode(discard).performScrollTo().assertIsDisplayed()
            capture("conflict-recovery-reachable")
            compose.onNode(discard).performClick()
            compose.waitUntil(30_000) { privateDraft() == null && compose.onAllNodes(hasTestTag(SetEditTestTags.CONTENT)).fetchSemanticsNodes().isEmpty() }
            assertEquals(110.0, read { checkNotNull(container.workoutRepository.getSession(sessionId)).sets.single().weightKg }, 0.0)
            compose.onNodeWithTag(SessionDetailTestTags.CONTENT).performScrollToNode(hasTestTag(SessionDetailTestTags.EDIT_SET))
            compose.onNodeWithTag(SessionDetailTestTags.EDIT_SET).performClick()
            awaitTag("Type a weight")
            capture("clean-editor-after-private-discard")
            return
        }
        if (phase != "resume") {
            awaitTag(SessionDetailTestTags.CONTENT)
            compose.onNodeWithTag(SessionDetailTestTags.CONTENT).performScrollToNode(hasTestTag(SessionDetailTestTags.EDIT_SET))
            compose.onNodeWithTag(SessionDetailTestTags.EDIT_SET).performClick()
            awaitTag(SetEditTestTags.CONTENT)
            compose.onNodeWithText("9").performScrollTo().performClick()
            awaitTag("Type a weight")
            compose.onNodeWithTag("Type a weight").performScrollTo().performClick()
            awaitTag(NumberEntryTags.FIELD)
            compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(raw)
            compose.waitUntil(30_000) { privateDraft()?.numbers?.singleOrNull()?.text == raw }
            capture("raw-before-stop")
            assertEquals(100.0, read { checkNotNull(container.workoutRepository.getSession(sessionId)).sets.single().weightKg }, 0.0)
            if (phase == "stage") {
                marker.writeText(listOf(sessionId, routineId, setId).joinToString("\n"))
                retained = true
                return
            }
        } else {
            awaitTag(NumberEntryTags.FIELD)
            assertEquals(raw, compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithText("lb").assertIsDisplayed()
            assertEquals(WeightUnit.LBS, privateDraft()?.numbers?.single()?.weightUnit)
            capture("raw-after-reopen-with-kg-preference")
        }
        // The retained text deliberately ends mid-decimal; finish it before confirming.
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(completedRaw)
        compose.onNodeWithTag(NumberEntryTags.CONFIRM).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isEmpty() }
        awaitTag(SetEditTestTags.CONTENT)
        compose.onNodeWithTag("Type a rep count").performScrollTo().performClick()
        awaitTag(NumberEntryTags.FIELD)
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement("6")
        compose.onNodeWithTag(NumberEntryTags.CONFIRM).performClick()
        val original = read { checkNotNull(container.workoutRepository.getSession(sessionId)).sets.single() }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val expected = checkNotNull(NumericEntry.parseWeightKg(completedRaw, WeightUnit.LBS))
        compose.waitUntil(30_000) { read { container.workoutRepository.getSession(sessionId)?.sets?.singleOrNull()?.weightKg == expected } }
        compose.waitUntil(30_000) { privateDraft() == null }
        val corrected = read { checkNotNull(container.workoutRepository.getSession(sessionId)).sets.single() }
        assertEquals(setId, corrected.id)
        assertEquals(original.completedAt, corrected.completedAt)
        assertEquals(original.setNumber, corrected.setNumber)
        assertEquals(6, corrected.reps)
        assertEquals(9, corrected.rpe)
        assertNull(privateDraft())
        capture("confirmed-same-set")
        compose.onNodeWithTag(SessionDetailTestTags.BACK).performClick()
        awaitTag(HistoryTags.LIST)
    }
    private fun awaitTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    private fun capture(name: String): String {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        return NativeArtifacts.write("set-$name", checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
    }
    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { withTimeout(30_000) { block() } }
    private fun privateDraft() = read {
        val binding = container.durableDrafts.bind(DraftOwner(DraftForm.FINISHED_SET, sessionId), FinishedSetDraftCodec)
        (binding.read() as DraftRead.PresentCheckpoint).checkpoint.draft
    }
    private fun cleanup() {
        try {
            if (::sessionId.isInitialized) read {
                container.finishedSetDrafts.discard(container.finishedSetDrafts.open(sessionId))
                container.workoutRepository.deleteFinishedSession(sessionId)
                container.routineRepository.delete(routineId)
            }
            if (::marker.isInitialized && marker.exists()) check(marker.delete())
        } finally { environment.restore() }
    }
}
