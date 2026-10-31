package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.ui.start.StartTags
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewTags
import android.content.Intent
import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.net.ConnectivityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.sinura.personaltrainer.testutil.openFinishFromOverflow
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.domain.LaunchPermissionCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot
import com.sinura.personaltrainer.domain.RestNudgeCopy
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.domain.RestExteriorPermissions
import com.sinura.personaltrainer.domain.RestExteriorPermissionCopy
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.isDisposableSdkEmulator
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.timer.AndroidPhoneCapabilities
import com.sinura.personaltrainer.timer.RestTimerNotifications
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.history.FilledLiftCardTags
import com.sinura.personaltrainer.ui.components.ThumbCache
import com.sinura.personaltrainer.ui.components.keyedArtwork
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.home.HomeStartTags
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.ui.summary.SummaryTags
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * One synthetic workout through MainActivity's real AppNav, production ViewModels and Room.
 * Home starts the live session; repeated rest-notification delivery and system Settings then
 * return to that same authored entry. No test-host navigation or direct entry mutation.
 *
 * Fixture setup creates a routine and separate prior history solely to suppress a first-ever
 * record overlay. During the journey all mutations use rendered controls. Rest is observed
 * running and skipped through its Android accessibility action; expiry, notification posting and
 * lock-screen behavior are outside this test. Captures are observations, not owner acceptance.
 * The fixture is offline with verified Android permissions. It does not exercise the remote
 * debug updater. A named disposable Temper AVD, or disposable CI emulator explicitly opting
 * into syntheticFixture=true, is required; hardware/fingerprint guards always exclude phones.
 */
@RunWith(AndroidJUnit4::class)
class ConnectedWorkoutJourneyInstrumentedTest {
    private lateinit var container: AppContainer
    private lateinit var fixture: Fixture
    private var sessionId: String? = null
    private val environment = NativeWorkoutFixtureEnvironment(PREFIX)

    private val seedRule = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            container = app.container
            environment.prepare(container)
            try {
                runBlocking(Dispatchers.IO) { withTimeout(60_000) { seed() } }
            } catch (failure: Throwable) {
                // ExternalResource does not call after() when before() throws. Roll back a
                // partial seed here and preserve that original failure if cleanup also fails.
                try { cleanAndRestore() } catch (cleanupFailure: Throwable) { failure.addSuppressed(cleanupFailure) }
                throw failure
            }
        }

        override fun after() {
            cleanAndRestore()
        }
    }
    private val scenarioRule = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
    )
    private val compose = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(seedRule).around(compose)

    @Test
    fun homeStart_manualResult_rest_switch_correct_finish_back_history_sameSession() {
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
        awaitScrollableContent()
        assertNoBlockingDialogs()
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().performClick()
        compose.onNodeWithTag(StartTags.START_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.routine(fixture.routineId)).performScrollTo().assertIsDisplayed()
        captureWindow("home-routine")
        compose.onNodeWithTag(StartTags.routine(fixture.routineId)).performClick()
        awaitCondition("the routine pick opens the preview") {
            compose.onAllNodes(hasTestTag(WorkoutPreviewTags.BEGIN)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).performClick()

        awaitCondition("Home creates the live session") {
            runBlocking(Dispatchers.IO) {
                container.workoutRepository.getInProgress()?.let {
                    if (it.routineId == fixture.routineId) sessionId = it.id
                    sessionId != null
                } == true
            }
        }
        awaitEntryWithoutEffort()
        assertEquals(fixture.routineId, session().routineId)
        assertTrue(session().sets.isEmpty())
        scrollFloorTo(WorkoutTestTags.liftCard(fixture.first.id))
        compose.onNodeWithTag(WorkoutTestTags.liftCard(fixture.first.id)).assertIsDisplayed()
        assertCatalogIdentity()
        captureWindow("quiet-catalog-identity")
        assertReadiness("Choose effort to log this set.")
        val primaryBottom = assertPrimaryTarget().bottom
        assertNoFloatingCoach()

        // Read each rendered prefill before typing different actual numbers. Confirming a
        // numeric dialog fills the draft; it must not create a saved set or choose effort.
        enterNumbers(87.5, 4, mustDiffer = true)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        assertReadiness("Choose effort to log this set.")
        assertTrue(session().sets.isEmpty())
        chooseEffort(8)
        awaitEnabledLog()
        assertReadiness("Ready to log your entry.")
        assertNoFloatingCoach()
        assertEquals("scrolling keeps the primary pinned", primaryBottom, assertPrimaryTarget().bottom, 1f)
        assertNoBlockingDialogs()
        captureWindow("manual-ready")
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        awaitCondition("one exact result is durable") { session().sets.size == 1 }
        val original = session().sets.single()
        assertSet(original, original.id, 87.5, 4, 8)
        assertNull(session().finishedAt)

        // Avoid Compose's idle wait while the real rest track animates every second.
        // No service/controller shortcut: locate the visible Skip label in Android's
        // accessibility tree and invoke its actual clickable ancestor.
        awaitCondition("rest starts for this session") {
            container.restTimerStore.current().let { it.running && it.sessionId == sessionId }
        }
        val rest = container.restTimerStore.current()
        assertEquals(150, rest.totalSeconds)
        assertTrue(rest.remainingSeconds(SystemClock.elapsedRealtime()) in 1..150)
        // Backend waits do not pump Compose's test frame clock. Advance the actual rendered
        // save/rest state, then hold animation time while interacting with the Android tree.
        val autoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assertIsDisplayed()
            captureWindow("saved-rest-running")
            clickVisibleAndroidText(RestNudgeCopy.SKIP)
            awaitCondition("the rendered Skip action stops rest") { !container.restTimerStore.current().running }
            compose.mainClock.advanceTimeBy(1_000)
        } finally {
            compose.mainClock.autoAdvance = autoAdvance
        }
        compose.waitForIdle()
        assertNoBlockingDialogs()
        awaitEntryWithoutEffort()
        assertReadiness("Choose effort to log this set.")

        // A distinct unsaved draft travels away and back. Neither the switch nor a later
        // correction may turn that draft into a second saved set.
        enterNumbers(92.5, 6)
        chooseEffort(7)
        switchTo(fixture.second)
        switchTo(fixture.first)
        assertDraftNumbers(92.5, 6)
        scrollFloorTo(WorkoutTestTags.rpeChoice(7))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(7)).assertIsSelected()
        assertReadiness("Ready to log your entry.")
        assertEquals(listOf(original.id), session().sets.map { it.id })
        verifyNotificationAndSettingsReturn(original.id)

        scrollFloorTo(WorkoutTestTags.setChip(original.id))
        captureWindow("saved-set-before-scroll")
        compose.revealFloorControlAboveTempo(WorkoutTestTags.setChip(original.id))
        captureWindow("saved-set-after-scroll")
        compose.onNodeWithTag(WorkoutTestTags.setChip(original.id)).performClick()
        assertReadiness("Draft resumed. Save changes updates this saved set.")
        enterNumbers(85.0, 3)
        // Editing restores the saved effort, not the departing unsaved draft's 7.
        val savedEffort = checkNotNull(original.rpe)
        compose.revealFloorControlAboveTempo(WorkoutTestTags.rpeChoice(savedEffort))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(savedEffort)).assertIsDisplayed().assertIsSelected().performClick()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        assertReadiness("Draft resumed. Choose effort to save this correction.")
        assertEquals(listOf(original.id), session().sets.map { it.id })
        chooseEffort(9)
        assertReadiness("Draft resumed. Save changes updates this saved set.")
        assertPrimaryTarget()
        captureWindow("quiet-correction-ready")
        compose.onNodeWithText("Save changes").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsEnabled().performClick()
        awaitCondition("correction updates the same row") {
            session().sets.singleOrNull()?.let { it.id == original.id && it.weightKg == 85.0 && it.reps == 3 && it.rpe == 9 } == true
        }
        val corrected = session().sets.single()
        assertSet(corrected, original.id, 85.0, 3, 9)
        assertEquals(original.completedAt, corrected.completedAt)
        assertEquals(original.setNumber, corrected.setNumber)
        assertFalse("correcting a saved set does not start another rest", container.restTimerStore.current().running)
        awaitEntryWithoutEffort()
        assertReadiness("Draft resumed. Choose effort to log this set.")

        // Eight actual logs share one AppNav session. Continue beyond the second lift's
        // prescription explicitly, including logs while its rest timer is still running.
        switchTo(fixture.second)
        val intended = mutableListOf(corrected)
        for (number in 1..7) {
            if (number > TARGET_SETS) {
                compose.onNodeWithTag(WorkoutTestTags.ANOTHER_SET).assertIsDisplayed().performClick()
            }
            enterNumbers(10.0 + number, 2)
            chooseEffort(8)
            awaitEnabledLog()
            compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
            awaitCondition("successive log $number saved once") { session().sets.size == number + 1 }
            val result = session().setsFor(fixture.second.id).last()
            assertSet(set = result, setId = result.id, weightKg = 10.0 + number, reps = 2, effort = 8, exerciseId = fixture.second.id)
            intended += result
        }
        assertEquals(8, intended.map { it.id }.distinct().size)
        assertEquals(451.0, session().work().volumeKg, EPSILON)
        compose.waitForIdle()
        val latest = intended.last()
        compose.waitUntil(30000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.setChip(latest.id))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.revealFloorControlAboveTempo(WorkoutTestTags.setChip(latest.id))
        val receipt = compose.onNodeWithTag(WorkoutTestTags.setChip(latest.id)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(receipt, receipt.startsWith("7 saved. Edit latest saved set:"))
        captureWindow("eight-results-during-rest")

        // Delete and Undo through the same actual navigation preserve the exact receipt.
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).performClick()
        awaitTag(WorkoutTestTags.SAVED_SETS_SHEET)
        compose.onNodeWithTag("workout-saved-sets-list")
            .performScrollToNode(hasTestTag(WorkoutTestTags.setOptions(latest.id)))
        compose.onNodeWithTag(WorkoutTestTags.setOptions(latest.id)).performClick()
        compose.onNodeWithText("Delete set").performClick()
        awaitCondition("latest result deleted") { session().sets.size == 7 }
        compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
        awaitCondition("Undo restores the exact latest result") { session().sets.firstOrNull { it.id == latest.id } == latest }
        assertEquals(intended.sortedBy { it.id }, session().sets.sortedBy { it.id })

        compose.openFinishFromOverflow()
        compose.onNodeWithTag(WorkoutTestTags.FINISH_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Finish workout?").assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).performClick()
        awaitTag(SummaryTags.DONE)
        assertNoBlockingDialogs()
        compose.onNodeWithText("WORKOUT SAVED").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.LOG_READINESS).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertDoesNotExist()
        compose.onNodeWithText(fixture.routineName).assertIsDisplayed()
        compose.onNodeWithContentDescription("kg volume, 451 kg").assertIsDisplayed()
        captureWindow("summary")
        val finished = session()
        assertNotNull(finished.finishedAt)
        assertEquals(intended.sortedBy { it.id }, finished.sets.sortedBy { it.id })
        assertEquals(451.0, finished.work().volumeKg, EPSILON)
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
        assertFalse(container.restTimerStore.current().running)
        val setLine = SetCopy.setLine(85.0, 3, LoadClass.LOADED, WeightUnit.KG)
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Top set $setLine"))
        compose.onNodeWithText("Top set $setLine").assertIsDisplayed()

        // System Back must return to Home without resurrecting the finished workout. Done
        // remains separately covered by the original native journey and Summary JVM paths.
        // The real History tab
        // must lead to the same finished session rather than mounting a test-host screen.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        compose.onNodeWithTag(SummaryTags.DONE).assertDoesNotExist()
        compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).assertDoesNotExist()
        compose.onNodeWithTag("navigation-history").performClick()
        compose.onNodeWithTag("navigation-history").assertIsSelected()
        // The list also exists during Loading. Wait for the READY branch before
        // scrolling; an unavailable or missing result must still fail this journey.
        awaitTag(com.sinura.personaltrainer.ui.history.HistoryTags.CURRENT)
        val historyRow = hasTestTag(SessionLogTags.ROW) and hasContentDescription(fixture.routineName, substring = true)
        compose.onNodeWithTag(com.sinura.personaltrainer.ui.history.HistoryTags.LIST).performScrollToNode(historyRow)
        val row = compose.onNode(historyRow).assertIsDisplayed()
        val spoken = row.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(spoken, spoken.contains("8 sets, 451 kg"))
        val historical = runBlocking(Dispatchers.IO) {
            container.workoutRepository.observeHistory().first().single { it.id == sessionId }
        }
        assertEquals(finished, historical)
        row.performClick()
        awaitTag(SessionDetailTestTags.CONTENT)
        compose.onNodeWithText(fixture.routineName).assertIsDisplayed()
        compose.onNodeWithText("451").assertIsDisplayed()
        compose.onNodeWithText("Working volume", ignoreCase = true).assertIsDisplayed()
        val tableRow = SetTableLine.fromLog(corrected, LoadClass.LOADED, WeightUnit.KG)
        val rowDescription = "Recorded working ${tableRow.extras}, ${tableRow.line}"
        val recordedRow = hasContentDescription(rowDescription) and
            hasAnyAncestor(hasTestTag(FilledLiftCardTags.setRow(corrected.id)))
        compose.onNodeWithTag(SessionDetailTestTags.CONTENT)
            .performScrollToNode(recordedRow)
        compose.onNode(recordedRow).assertIsDisplayed()
        assertEquals(intended.sortedBy { it.id }, session().sets.sortedBy { it.id })
        captureWindow("history-detail")
        compose.onNodeWithTag(SessionDetailTestTags.BACK).performClick()
        compose.onNodeWithTag("navigation-history").assertIsSelected()
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
    }

    @Test fun nativeSwapKeepsItsQueryAndReplacesTheIntendedUnloggedLiftOnce() {
        startSyntheticWorkoutFromHome()
        val original = session().exercises.first { it.exercise.id == fixture.first.id }
        val replacement = runBlocking(Dispatchers.IO) {
            container.exerciseRepository.observeAll().first().first { !it.isCustom && it.name.contains("Bench Press") }
        }
        openNativeSwap()
        val query = "  ${replacement.name}  "
        val queryField = hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search"))
        compose.onNode(queryField).assertIsEnabled().performTextReplacement(query)
        compose.onNode(queryField).performImeAction()
        captureWindow("swap-query")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isEmpty() }
        assertEquals(original, session().exercises.first { it.id == original.id })
        openNativeSwap()
        compose.onNode(queryField).assertTextEquals(query)
        val result = hasText(replacement.name) and hasAnyAncestor(hasTestTag("picker-content"))
        compose.onNodeWithTag("picker-content").performScrollToNode(result)
        compose.onNode(result).assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isEmpty() }
        awaitTag(WorkoutTestTags.liftCard(replacement.id))
        val actual = session().exercises.first { it.exercise.id == replacement.id }
        assertEquals(original.targetSets, actual.targetSets)
        assertEquals(original.targetReps, actual.targetReps)
        assertEquals(original.restSeconds, actual.restSeconds)
        assertNull(actual.targetWeightKg)
        assertEquals(2, session().exercises.size)
        assertTrue(session().sets.isEmpty())
        assertNull(runBlocking(Dispatchers.IO) { container.pickerSwapDrafts.openPending(checkNotNull(sessionId)) })
        captureWindow("swap-saved")
    }

    @Test fun interruptedAcceptedSwapShowsOneNativeContinueActionAndDoesNotRewrite() {
        startSyntheticWorkoutFromHome()
        val source = session().exercises.first { it.exercise.id == fixture.first.id }
        val replacement = runBlocking(Dispatchers.IO) {
            container.exerciseRepository.observeAll().first().first { !it.isCustom && it.name.contains("Bench Press") }
        }
        // Isolated fixture simulates a business commit whose UI acknowledgement was interrupted.
        // Reopening and Continue below use shipping AppNav and rendered controls.
        val intended = runBlocking(Dispatchers.IO) {
            val handle = container.pickerSwapDrafts.open(checkNotNull(sessionId), source.id)
            container.pickerSwapDrafts.admit(handle, checkNotNull(sessionId), source.id, replacement)
            container.pickerSwapDrafts.execute(handle) { true }
            checkNotNull(handle.checkpoint.pending).value.after
        }
        awaitEntryWithoutEffort()
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.SWITCHER_ADD_LIFT).performScrollTo().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-action") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("picker-action").assertIsEnabled().assertTextEquals("Continue")
        captureWindow("swap-recovered-saved")
        compose.onNodeWithTag("picker-action").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isEmpty() }
        assertEquals(intended.id, session().exercises.first { it.exercise.id == replacement.id }.id)
        assertTrue(session().sets.isEmpty())
        assertNull(runBlocking(Dispatchers.IO) { container.pickerSwapDrafts.openPending(checkNotNull(sessionId)) })
        captureWindow("swap-recovered-continued")
    }

    private fun startSyntheticWorkoutFromHome() {
        awaitTag("navigation-home")
        awaitScrollableContent()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithTag(StartTags.START_TITLE).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.routine(fixture.routineId)).performScrollTo().performClick()
        awaitCondition("the routine pick opens the preview") {
            compose.onAllNodes(hasTestTag(WorkoutPreviewTags.BEGIN)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutPreviewTags.BEGIN).performClick()
        awaitCondition("Home creates the synthetic swap session") {
            runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress()?.let {
                if (it.routineId == fixture.routineId) sessionId = it.id
                sessionId != null
            } == true }
        }
        awaitEntryWithoutEffort()
    }

    private fun openNativeSwap() {
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.onNodeWithText(CurrentLiftCopy.SWAP).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        try { awaitTag("picker-search") } catch (failure: Throwable) {
            captureWindow("swap-open-failed")
            throw failure
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun seed() {
        cleanSyntheticData()
        container.preferencesRepository.setOnboardingComplete(true)
        // This fixture exercises the accepted offline route, not the first-launch chooser.
        container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
        container.preferencesRepository.setLaunchPermissionsAsked(true)
        container.preferencesRepository.setWeightUnit(WeightUnit.KG)
        container.preferencesRepository.markRestBatteryHintShown()
        container.preferencesRepository.markRestAlertsAsked()
        val suffix = UUID.randomUUID().toString().take(8)
        // Read the catalog row so the identity capture contains its real keyed squat
        // still. Only the routine and its synthetic sessions/sets are authored here.
        val first = withTimeout(15_000) {
            container.exerciseRepository.observeAll().first { rows -> rows.any { it.id == "ex-barbell-back-squat" } }
                .single { it.id == "ex-barbell-back-squat" }
        }
        assertEquals("Barbell Back Squat", first.name)
        assertEquals("ex_barbell_back_squat", first.imageKey)
        assertFalse(first.isCustom)
        val second = createExercise("$PREFIX row $suffix")
        val routine = container.routineRepository.create("$PREFIX prior $suffix")
        for (exercise in listOf(first, second)) {
            container.routineRepository.addExercise(routine.id, exercise, TARGET_SETS, 5, 140.0, 120)
        }
        val prior = container.workoutRepository.startRoutine(checkNotNull(container.routineRepository.getById(routine.id)))
        container.workoutRepository.logSet(prior.id, first.id, 200.0, 5, null, false)
        container.workoutRepository.finishSession(prior.id, notes = "Synthetic prior record only")
        val name = "$PREFIX workout $suffix"
        container.routineRepository.updateDetails(routine.id, name, "Synthetic connected-journey fixture")
        fixture = Fixture(routine.id, name, first, second)
        assertNull(container.workoutRepository.getInProgress())
    }

    private suspend fun requireSyntheticOrNoLiveWork() {
        val live = container.workoutRepository.getInProgress()
        check(live == null || live.routineName?.startsWith(PREFIX) == true) {
            "Refusing to change an unrelated live workout on the test emulator."
        }
        check(container.activityRepository.getLive() == null) {
            "Refusing to change an emulator with a live cardio or other activity."
        }
    }

    private suspend fun cleanSyntheticData() {
        requireSyntheticOrNoLiveWork()
        val live = container.workoutRepository.getInProgress()
        container.restTimerController.stop()
        container.restTimerStatePersistence.clear()
        container.workoutDraftCache.clearAll()
        live?.let { container.discardWorkout(it.id) }
        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(PREFIX) == true }
            .forEach { container.workoutRepository.deleteFinishedSession(it.id) }
        container.routineRepository.observeAll().first().filter { it.name.startsWith(PREFIX) }
            .forEach { container.routineRepository.delete(it.id) }
        container.exerciseRepository.observeAll().first().filter { it.isCustom && it.name.startsWith(PREFIX) }
            .forEach { container.exerciseRepository.deleteCustom(it.id) }
    }

    private fun cleanAndRestore() {
        var cleanupFailure: Throwable? = null
        try {
            if (::container.isInitialized) {
                runBlocking(Dispatchers.IO) { withTimeout(30_000) { cleanSyntheticData() } }
            }
        } catch (failure: Throwable) {
            cleanupFailure = failure
        }
        try {
            environment.restore()
        } catch (failure: Throwable) {
            val earlier = cleanupFailure
            if (earlier == null) cleanupFailure = failure else earlier.addSuppressed(failure)
        }
        cleanupFailure?.let { throw it }
    }

    private suspend fun createExercise(name: String): Exercise =
        when (val result = container.exerciseRepository.createCustom(name, "Quads")) {
            is SaveExerciseResult.Saved -> result.exercise
            is SaveExerciseResult.DuplicateName -> error("Synthetic exercise name collided: ${result.existing.id}")
            SaveExerciseResult.MissingMuscle -> error("Synthetic exercise muscle rejected")
        }

    private fun session(): WorkoutSession = runBlocking(Dispatchers.IO) {
        checkNotNull(container.workoutRepository.getSession(checkNotNull(sessionId)))
    }

    private fun assertSet(set: SetLog, setId: String, weightKg: Double, reps: Int, effort: Int, exerciseId: String = fixture.first.id) {
        assertEquals(sessionId, set.sessionId)
        assertEquals(setId, set.id)
        assertEquals(exerciseId, set.exerciseId)
        assertEquals(weightKg, set.weightKg, EPSILON)
        assertEquals(reps, set.reps)
        assertEquals(effort, set.rpe)
        assertFalse(set.isWarmup)
    }

    private fun assertCatalogIdentity() {
        val artwork = checkNotNull(keyedArtwork(fixture.first.imageKey))
        assertEquals(R.drawable.ex_barbell_back_squat, artwork)
        compose.waitUntil(15_000) { ThumbCache.peek(artwork, ThumbCache.THUMB_SAMPLE) != null }
        compose.waitForIdle()
        val image = compose.onNodeWithTag(WorkoutTestTags.DETAILS).assertIsDisplayed().fetchSemanticsNode()
        val name = compose.onNode(hasText(fixture.first.name) and
            hasAnyAncestor(hasTestTag(WorkoutTestTags.liftCard(fixture.first.id))), useUnmergedTree = true)
            .assertIsDisplayed().assertTextEquals(fixture.first.name)
        val nameNode = name.fetchSemanticsNode()
        val imageBounds = image.boundsInRoot
        val nameBounds = nameNode.boundsInRoot
        val content = compose.onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
        val density = image.layoutInfo.density.density
        assertEquals("identity image width", 64f, imageBounds.width / density, 0.5f)
        assertEquals("identity image height", 64f, imageBounds.height / density, 0.5f)
        assertTrue("the full name sits beside the image", nameBounds.left > imageBounds.right)
        assertTrue("identity image is fully visible", imageBounds.height >= image.size.height - 1f &&
            imageBounds.top >= content.top && imageBounds.bottom <= content.bottom)
        assertTrue("the full name is visible", nameBounds.height >= nameNode.size.height - 1f &&
            nameBounds.top >= content.top && nameBounds.bottom <= content.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        repeat(layout.lineCount) { line ->
            assertFalse("the exercise name must not truncate", layout.isLineEllipsized(line))
            assertTrue("the name glyphs fit their actual box",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= nameNode.size.width + 1f)
        }
        assertEquals(fixture.first.name.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        // The known row/key, completed async decode and raw capture support visual review
        // of the actual still. Geometry and semantics alone do not identify bitmap pixels.
        println("NATIVE_QUIET_IDENTITY exercise=${fixture.first.id} name=${fixture.first.name} " +
            "imageKey=${fixture.first.imageKey} drawable=$artwork image=$imageBounds nameBounds=$nameBounds density=$density")
    }

    private fun assertReadiness(text: String) {
        compose.revealFloorControlAboveTempo(WorkoutTestTags.LOG_READINESS)
        val readiness = hasTestTag(WorkoutTestTags.LOG_READINESS) and
            hasAnyAncestor(hasTestTag(WorkoutTestTags.SECTION_RPE))
        compose.waitUntil(15_000) { compose.onAllNodes(readiness and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(readiness).assertIsDisplayed().assertTextEquals(text)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val rendered = node.fetchSemanticsNode()
        // Compose may rebuild short text at its offered width for this action.
        // Inspect actual glyph extents and the final character against the drawn box.
        repeat(layout.lineCount) { line ->
            assertFalse("the visible readiness message must not truncate", layout.isLineEllipsized(line))
            assertTrue("readiness glyphs fit their actual box",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= rendered.size.width + 1f)
        }
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        println("NATIVE_QUIET_READINESS text=$text bounds=${rendered.boundsInRoot}")
    }

    private fun assertNoFloatingCoach() {
        compose.onAllNodes(hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
            !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT))).assertCountEquals(0)
    }

    private fun assertPrimaryTarget(): androidx.compose.ui.geometry.Rect {
        compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET)).assertCountEquals(1)
        val primary = compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().fetchSemanticsNode()
        val bounds = primary.boundsInWindow
        val density = primary.layoutInfo.density.density
        val window = compose.runOnIdle { compose.activity.window.decorView.width to compose.activity.window.decorView.height }
        assertTrue("primary retains its 72dp target", bounds.height / density >= 71.5f)
        assertTrue("primary is fully inside the window", bounds.width >= primary.size.width - 1f &&
            bounds.height >= primary.size.height - 1f && bounds.left >= 0f && bounds.top >= 0f &&
            bounds.right <= window.first && bounds.bottom <= window.second)
        println("NATIVE_QUIET_PRIMARY bounds=$bounds size=${primary.size} density=$density window=$window")
        return bounds
    }

    private fun enterNumbers(weightKg: Double, reps: Int, mustDiffer: Boolean = false) {
        assertNoBlockingDialogs()
        scrollFloorTo(WorkoutTestTags.WEIGHT_STEPPER)
        if (mustDiffer) captureWeightEntryState("before-tap")
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        if (mustDiffer) captureWeightEntryState("after-scroll")
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        if (mustDiffer) captureWeightEntryState("after-tap")
        val weight = compose.onNodeWithTag(NumberEntryTags.FIELD)
        if (mustDiffer) {
            val prefill = NumericEntry.parseWeightKg(weight.fetchSemanticsNode().config[SemanticsProperties.EditableText].text, WeightUnit.KG)
            assertNotNull(prefill)
            assertTrue("manual weight differs from the rendered prefill", prefill != weightKg)
        }
        weight.performTextReplacement(weightKg.toString())
        compose.onNodeWithText("Set").performClick()
        compose.revealFloorControlAboveTempo(WorkoutTestTags.REPS_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).performClick()
        val repetitions = compose.onNodeWithTag(NumberEntryTags.FIELD)
        if (mustDiffer) {
            val prefill = NumericEntry.parseReps(repetitions.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertNotNull(prefill)
            assertTrue("manual reps differ from the rendered prefill", prefill != reps)
        }
        repetitions.performTextReplacement(reps.toString())
        compose.onNodeWithText("Set").performClick()
    }

    private fun assertDraftNumbers(weightKg: Double, reps: Int) {
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        val weight = compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
        assertEquals(weightKg, checkNotNull(NumericEntry.parseWeightKg(weight.config[SemanticsProperties.EditableText].text, WeightUnit.KG)), EPSILON)
        compose.onNodeWithText("Cancel").performClick()
        awaitNumericDialogClosed()
        compose.revealFloorControlAboveTempo(WorkoutTestTags.REPS_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).performClick()
        val repetitions = compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
        assertEquals(reps, NumericEntry.parseReps(repetitions.config[SemanticsProperties.EditableText].text))
        compose.onNodeWithText("Cancel").performClick()
        awaitNumericDialogClosed()
    }

    private fun awaitNumericDialogClosed() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(NumberEntryTags.FIELD)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun chooseEffort(value: Int) {
        assertNoBlockingDialogs()
        compose.revealFloorControlAboveTempo(WorkoutTestTags.rpeChoice(value))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(value)).performClick()
    }

    private fun switchTo(exercise: Exercise) {
        assertNoBlockingDialogs()
        scrollFloorTo(WorkoutTestTags.LIFT_SWITCH)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).performClick()
        compose.onNodeWithTag(WorkoutTestTags.liftSwitcherRow(exercise.id)).performClick()
        scrollFloorTo(WorkoutTestTags.liftCard(exercise.id))
        compose.onNodeWithTag(WorkoutTestTags.liftCard(exercise.id)).assertIsDisplayed()
    }

    private fun scrollFloorTo(tag: String) {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitScrollableContent() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitEntryWithoutEffort() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, LogCommitCopy.EFFORT_MISSING,
            )).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
    }

    private fun awaitEnabledLog() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoBlockingDialogs() {
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private fun verifyNotificationAndSettingsReturn(originalSetId: String) {
        val context = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        val notification = RestTimerNotifications.openAppIntent(context, checkNotNull(sessionId))
        repeat(2) {
            lateinit var previousIntent: Intent
            scenarioRule.scenario.onActivity { previousIntent = it.intent }
            notification.send()
            // A floor node already exists. Wait for Android's actual onNewIntent delivery
            // before letting AppNav settle, or a late tap could arrive after the Back check.
            awaitCondition("notification delivery reaches the Activity") {
                var received = false
                scenarioRule.scenario.onActivity { received = it.intent !== previousIntent }
                received
            }
            compose.waitForIdle()
            awaitTag(WorkoutTestTags.CONTENT)
            compose.onAllNodes(hasTestTag(WorkoutTestTags.CONTENT)).assertCountEquals(1)
            assertDraftNumbers(92.5, 6)
            assertEquals(listOf(originalSetId), session().sets.map { it.id })
        }
        assertNoBlockingDialogs()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).assertDoesNotExist()
        // Today resumes the live workout from its own primary; the bar steps aside there.
        compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.TODAY_RESUME).performClick()
        awaitTag(WorkoutTestTags.CONTENT)
        assertDraftNumbers(92.5, 6)

        val notifications = NotificationManagerCompat.from(context)
        assertTrue("fixture starts with notifications enabled", notifications.areNotificationsEnabled())
        fun changeInSystemSettings(enabled: Boolean) {
            scenarioRule.scenario.onActivity { activity ->
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            clickVisibleAndroidText("Show notifications", expectedPackage = "com.android.settings")
            awaitCondition("system notification choice accepted") {
                notifications.areNotificationsEnabled() == enabled
            }
            // The Settings window belongs to another package; use Android's system Back
            // action rather than app-scoped Instrumentation input injection.
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            awaitCondition("workout is resumed in the foreground after Settings") {
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                try {
                    root?.packageName?.toString() == context.packageName &&
                        scenarioRule.scenario.state == Lifecycle.State.RESUMED
                } finally { root?.recycle() }
            }
            awaitTag(WorkoutTestTags.CONTENT)
        }
        try {
            changeInSystemSettings(false)
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText(RestNotificationCopy.RECOVERY_TITLE)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE).assertIsDisplayed()
            // The accepted permission guard covers entry while the capability is missing.
            // Observe retained state without interacting with controls behind that dialog.
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText(RestExteriorPermissionCopy.BLOCKING_TITLE)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(RestExteriorPermissionCopy.BLOCKING_TITLE).assertIsDisplayed()
            val retained = checkNotNull(container.workoutDraftCache.getLift(checkNotNull(sessionId), fixture.first.id))
            assertEquals(92.5, retained.weightKg, EPSILON)
            assertEquals(6, retained.reps)
            assertEquals(7, retained.rpe)
            assertEquals(listOf(originalSetId), session().sets.map { it.id })
            capturePermissionGuard()
            changeInSystemSettings(true)
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText(RestNotificationCopy.RECOVERY_TITLE)).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodes(hasText(RestExteriorPermissionCopy.BLOCKING_TITLE)).fetchSemanticsNodes().isEmpty()
            }
            assertNoBlockingDialogs()
            assertDraftNumbers(92.5, 6)
            scrollFloorTo(WorkoutTestTags.rpeChoice(7))
            compose.onNodeWithTag(WorkoutTestTags.rpeChoice(7)).assertIsSelected()
            assertReadiness("Draft resumed. Ready to log your entry.")
            assertEquals(listOf(originalSetId), session().sets.map { it.id })
        } finally {
            if (!notifications.areNotificationsEnabled()) changeInSystemSettings(true)
        }
    }

    private fun capturePermissionGuard() {
        compose.onNodeWithText(RestExteriorPermissionCopy.BLOCKING_TITLE).assertIsDisplayed()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { NativeArtifacts.write("connected-workout-system-settings-return-notifications-off-api${Build.VERSION.SDK_INT}", bitmap) }
        finally { bitmap.recycle() }
    }

    private fun clickVisibleAndroidText(label: String, expectedPackage: String = "com.sinura.personaltrainer.debug") {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        awaitCondition("visible Android $label action") {
            val root = automation.rootInActiveWindow ?: return@awaitCondition false
            try {
                fun click(node: AccessibilityNodeInfo): Boolean {
                    if (node.isVisibleToUser && node.isEnabled && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                        while (candidate != null) {
                            val current = candidate
                            if (current.isClickable) {
                                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                current.recycle()
                                return clicked
                            }
                            candidate = current.parent
                            current.recycle()
                        }
                    }
                    for (index in 0 until node.childCount) {
                        val child = node.getChild(index) ?: continue
                        try { if (click(child)) return true } finally { child.recycle() }
                    }
                    return false
                }
                root.packageName?.toString() == expectedPackage && click(root)
            } finally { root.recycle() }
        }
    }

    private fun captureWeightEntryState(state: String) {
        for (tag in listOf(WorkoutTestTags.WEIGHT_STEPPER, WorkoutTestTags.TEMPO_COACH_CARD,
            WorkoutTestTags.CONTENT, WorkoutTestTags.LOG_SET, NumberEntryTags.FIELD)) {
            val bounds = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot }
            println("NATIVE_ENTRY_DIAGNOSTIC journey=connected state=$state tag=$tag bounds=$bounds")
        }
        captureWindow("weight-$state")
    }

    private fun captureWindow(state: String) {
        SystemClock.sleep(750)
        environment.assertNoBlockingPrompts()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot() ?: automation.executeShellCommand("screencap -p").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use(BitmapFactory::decodeStream)
        }
        checkNotNull(bitmap) { "Both Android screenshot paths failed for $state" }
        try { NativeArtifacts.write("connected-workout-$state-api${Build.VERSION.SDK_INT}", bitmap) }
        finally { bitmap.recycle() }
    }

    private fun awaitCondition(label: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("$label not met after ${timeoutMs}ms\n${environment.describeWindow()}")
    }

    private data class Fixture(val routineId: String, val routineName: String, val first: Exercise, val second: Exercise)

    private companion object {
        const val PREFIX = "Connected AppNav"
        const val TARGET_SETS = 3
        const val EPSILON = 0.0001
    }
}

/** Real swipes expose leaf controls; an inline coach is ordinary scrollable content. */
internal fun ComposeContentTestRule.revealFloorControlAboveTempo(tag: String) {
    onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    for (attempt in 0..3) {
        val target = onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val bounds = target.boundsInRoot
        val content = onNodeWithTag(WorkoutTestTags.CONTENT).fetchSemanticsNode().boundsInRoot
        val tempo = this.onAllNodes(hasTestTag(WorkoutTestTags.TEMPO_COACH_CARD) and
            !hasAnyAncestor(hasTestTag(WorkoutTestTags.CONTENT))).fetchSemanticsNodes()
            .singleOrNull()?.boundsInRoot
        val safeBottom = minOf(content.bottom, tempo?.top ?: content.bottom)
        val fullyVisible = bounds.width >= target.size.width - 1f && bounds.height >= target.size.height - 1f &&
            bounds.top >= content.top && bounds.bottom <= safeBottom - 1f
        println("NATIVE_ENTRY_REACHABILITY tag=$tag attempt=$attempt bounds=$bounds size=${target.size} " +
            "content=$content tempo=$tempo fullyVisible=$fullyVisible")
        if (fullyVisible) return
        check(attempt < 3) { "$tag remains clipped or behind Tempo after three real content swipes" }
        val safeHeight = safeBottom - content.top
        check(safeHeight > target.size.height + 48f) { "No safe swipe area above Tempo for $tag" }
        // Keep the entire gesture above the overlay. A semantics action would bypass
        // the real hit test and could conceal the interaction that failed on Debug124.
        onNodeWithTag(WorkoutTestTags.CONTENT).performTouchInput {
            if (target.positionInRoot.y < content.top) {
                swipeDown(startY = safeHeight * 0.3f, endY = safeHeight * 0.8f, durationMillis = 600)
            } else {
                swipeUp(startY = safeHeight * 0.8f, endY = safeHeight * 0.3f, durationMillis = 600)
            }
        }
        waitForIdle()
    }
}

/**
 * Real, reversible OS setup shared by the native workout journeys. Record cleanup is separate;
 * app preferences intentionally remain at the synthetic offline fixture values.
 */
internal class NativeWorkoutFixtureEnvironment(private val workoutPrefix: String) {
    private val restoreCommands = mutableListOf<String>()
    private val originalSettings = linkedMapOf<String, String>()
    private val originalAppOps = linkedMapOf<String, String>()
    private var originalCapabilities: PhoneCapabilitySnapshot? = null

    fun prepare(container: AppContainer) {
        check(isDisposableSdkEmulator()) {
            "Synthetic fixtures require Android SDK emulator hardware, never a phone."
        }
        val avdName = shell("getprop ro.kernel.qemu.avd_name").trim()
            .ifEmpty { shell("getprop ro.boot.qemu.avd_name").trim() }
        val optedIn = InstrumentationRegistry.getArguments().getString("syntheticFixture") == "true"
        check(avdName in setOf("temper-tests-api26", "temper-tests-api29", "temper-tests-api36") || optedIn) {
            "Use a disposable Temper AVD or explicitly opt the disposable CI emulator into syntheticFixture=true."
        }
        val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
        check(app.packageName == PACKAGE)
        runBlocking(Dispatchers.IO) {
            withTimeout(30_000) {
                val live = container.workoutRepository.getInProgress()
                check(live == null || live.routineName?.startsWith(workoutPrefix) == true) {
                    "Refusing to change an unrelated live workout on the test emulator."
                }
                check(container.activityRepository.getLive() == null) { "Refusing to change an emulator with a live activity." }
            }
        }
        try {
            for (setting in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
                val prior = shell("settings get global $setting").trim()
                check(prior == "null" || prior.toFloatOrNull() != null) { "Cannot read original $setting: $prior" }
                originalSettings[setting] = prior
                restoreCommands += if (prior == "null") "settings delete global $setting" else "settings put global $setting $prior"
                shell("settings put global $setting 0")
            }
            // Airplane mode alone does not disable emulator Wi-Fi. Record and restore each
            // transport, and prove there is no connected network before MainActivity starts.
            for ((setting, service) in listOf("wifi_on" to "wifi", "mobile_data" to "data")) {
                val priorText = shell("settings get global $setting").trim()
                val prior = checkNotNull(priorText.toIntOrNull()) { "Cannot read original $setting: $priorText" }
                originalSettings[setting] = priorText
                restoreCommands += "svc $service ${if (prior == 0) "disable" else "enable"}"
                shell("svc $service disable")
            }
            val connectivity = app.getSystemService(ConnectivityManager::class.java)
            await("offline emulator") { connectivity.activeNetwork == null }
            val capabilities = AndroidPhoneCapabilities(app)
            val before = capabilities.read()
            originalCapabilities = before
            fun allowAppOp(operation: String) {
                val prior = readAppOpMode(operation)
                originalAppOps[operation] = prior
                restoreCommands += "cmd appops set $PACKAGE $operation $prior"
                shell("cmd appops set $PACKAGE $operation allow")
            }
            if (!before.canDrawOverlays) allowAppOp("SYSTEM_ALERT_WINDOW")
            if (Build.VERSION.SDK_INT >= 31 && !before.canScheduleExactAlarms) allowAppOp("SCHEDULE_EXACT_ALARM")
            if (Build.VERSION.SDK_INT >= 33 && !before.postNotificationsGranted) {
                restoreCommands += "pm revoke $PACKAGE android.permission.POST_NOTIFICATIONS"
                shell("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
            }
            if (!before.batteryUnrestricted) {
                restoreCommands += "dumpsys deviceidle whitelist -$PACKAGE"
                shell("dumpsys deviceidle whitelist +$PACKAGE")
            }
            await("required Android rest permissions") {
                capabilities.read().let { RestExteriorPermissions.missing(it).isEmpty() && it.batteryUnrestricted }
            }
            val installed = app.packageManager.getPackageInfo(PACKAGE, 0)
            val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            println("NATIVE_WORKOUT_FIXTURE avd=$avdName api=${Build.VERSION.SDK_INT} " +
                "versionCode=$installedCode versionName=${installed.versionName} " +
                "offline=true capabilities=${capabilities.read()}")
        } catch (failure: Throwable) {
            try { restore() } catch (restoreFailure: Throwable) { failure.addSuppressed(restoreFailure) }
            throw failure
        }
    }

    fun restore() {
        var firstFailure: Throwable? = null
        for (command in restoreCommands.asReversed()) {
            try { shell(command) } catch (failure: Throwable) {
                val earlier = firstFailure
                if (earlier == null) firstFailure = failure else earlier.addSuppressed(failure)
            }
        }
        // executeShellCommand does not expose an exit code: stdout alone cannot prove a
        // successful restore. Read each captured OS value back, including permission state.
        try {
            var mismatches = emptyList<String>()
            try {
                await("original OS state restored") {
                    mismatches = restorationMismatches()
                    mismatches.isEmpty()
                }
            } catch (failure: Throwable) {
                throw AssertionError("OS restoration was not verified: ${mismatches.joinToString()}", failure)
            }
            println("NATIVE_WORKOUT_FIXTURE_RESTORED settings=$originalSettings appOps=$originalAppOps " +
                "postNotificationsGranted=${originalCapabilities?.postNotificationsGranted} " +
                "batteryUnrestricted=${originalCapabilities?.batteryUnrestricted}")
        } catch (failure: Throwable) {
            val earlier = firstFailure
            if (earlier == null) firstFailure = failure else earlier.addSuppressed(failure)
        } finally {
            restoreCommands.clear()
            originalSettings.clear()
            originalAppOps.clear()
            originalCapabilities = null
        }
        firstFailure?.let { throw it }
    }

    private fun readAppOpMode(operation: String): String {
        val response = shell("cmd appops get $PACKAGE $operation").trim()
        val mode = Regex("(?m)^\\s*$operation: (allow|ignore|deny|default|foreground)(?:;|\\s|$)")
            .find(response)?.groupValues?.get(1)
        if (mode != null) return mode
        val lines = response.lineSequence().map(String::trim).toList()
        if (lines == listOf("No operations.") || lines == listOf("No operations.", "Default mode: default")) return "default"
        error("Cannot verify $operation mode: $response")
    }

    private fun restorationMismatches(): List<String> = buildList {
        for ((setting, expected) in originalSettings) {
            val actual = shell("settings get global $setting").trim()
            if (actual != expected) add("$setting expected=$expected actual=$actual")
        }
        for ((operation, expected) in originalAppOps) {
            val actual = readAppOpMode(operation)
            if (actual != expected) add("$operation expected=$expected actual=$actual")
        }
        originalCapabilities?.let { expected ->
            val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()
            val actual = AndroidPhoneCapabilities(app).read()
            if (actual.postNotificationsGranted != expected.postNotificationsGranted) {
                add("postNotificationsGranted expected=${expected.postNotificationsGranted} actual=${actual.postNotificationsGranted}")
            }
            if (actual.batteryUnrestricted != expected.batteryUnrestricted) {
                add("batteryUnrestricted expected=${expected.batteryUnrestricted} actual=${actual.batteryUnrestricted}")
            }
            if (actual.canDrawOverlays != expected.canDrawOverlays) {
                add("canDrawOverlays expected=${expected.canDrawOverlays} actual=${actual.canDrawOverlays}")
            }
            if (actual.canScheduleExactAlarms != expected.canScheduleExactAlarms) {
                add("canScheduleExactAlarms expected=${expected.canScheduleExactAlarms} actual=${actual.canScheduleExactAlarms}")
            }
        }
    }

    fun assertNoBlockingPrompts() {
        val visibleLabels = mutableSetOf<String>()
        val window = describeWindow(visibleLabels)
        for (title in listOf(
            RestExteriorPermissionCopy.BLOCKING_TITLE, DebugUpdateCopy.REQUIRED_TITLE,
            LaunchPermissionCopy.BATTERY_TITLE, LaunchPermissionCopy.NOTIFICATIONS_TITLE, LaunchPermissionCopy.EXACT_TITLE,
        )) {
            check(title !in visibleLabels) { "A blocking prompt obscures the native journey: $title\n$window" }
        }
    }

    /** Failure evidence comes from the actual Android window before fixture cleanup. */
    fun describeWindow(visibleLabels: MutableSet<String>? = null): String {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: return "Android accessibility root unavailable"
        val output = StringBuilder()
        var visited = 0
        fun describe(node: AccessibilityNodeInfo, depth: Int) {
            if (++visited > 250) return
            if (node.isVisibleToUser) {
                node.text?.toString()?.let { visibleLabels?.add(it) }
                node.contentDescription?.toString()?.let { visibleLabels?.add(it) }
            }
            output.append(" ".repeat(depth.coerceAtMost(12)))
                .append("package=${node.packageName} visible=${node.isVisibleToUser} clickable=${node.isClickable}")
                .append(" text=${node.text} description=${node.contentDescription}\n")
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { describe(child, depth + 1) } finally { child.recycle() }
            }
        }
        try { describe(root, 0) } finally { root.recycle() }
        return output.toString()
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        error("$label not ready after 15000ms")
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe ->
            FileInputStream(pipe.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private companion object { const val PACKAGE = "com.sinura.personaltrainer.debug" }
}
