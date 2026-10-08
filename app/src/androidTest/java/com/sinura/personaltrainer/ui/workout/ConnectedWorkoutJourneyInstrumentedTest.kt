package com.sinura.personaltrainer.ui.workout

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.net.ConnectivityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.data.repository.SaveExerciseResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.domain.LaunchPermissionCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogCommitCopy
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot
import com.sinura.personaltrainer.domain.RestNudgeCopy
import com.sinura.personaltrainer.domain.RestExteriorPermissions
import com.sinura.personaltrainer.domain.RestExteriorPermissionCopy
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.timer.AndroidPhoneCapabilities
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.components.SetTableLine
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
 * Home starts the live session; no deep link, test-host navigation or direct entry mutation.
 *
 * Fixture setup creates a routine and separate prior history solely to suppress a first-ever
 * record overlay. During the journey all mutations use rendered controls. Rest is observed
 * running and skipped through its Android accessibility action; expiry, notifications and
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
    fun homeStart_manualResult_rest_switch_correct_finish_done_history_sameSession() {
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
        awaitScrollableContent()
        assertNoBlockingDialogs()
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag(HomeTags.START))
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed().performClick()
        compose.onNodeWithTag(HomeStartTags.ROUTINE).performScrollTo().performClick()
        compose.onNodeWithTag(HomeStartTags.routine(fixture.routineId)).performScrollTo().assertIsDisplayed()
        captureWindow("home-routine")
        compose.onNodeWithTag(HomeStartTags.routine(fixture.routineId)).performClick()

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

        // Read each rendered prefill before typing different actual numbers. Confirming a
        // numeric dialog fills the draft; it must not create a saved set or choose effort.
        enterNumbers(87.5, 4, mustDiffer = true)
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsNotEnabled()
        assertTrue(session().sets.isEmpty())
        chooseEffort(8)
        awaitEnabledLog()
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

        // A distinct unsaved draft travels away and back. Neither the switch nor a later
        // correction may turn that draft into a second saved set.
        enterNumbers(92.5, 6)
        chooseEffort(7)
        switchTo(fixture.second)
        switchTo(fixture.first)
        assertDraftNumbers(92.5, 6)
        scrollFloorTo(WorkoutTestTags.rpeChoice(7))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(7)).assertIsSelected()
        assertEquals(listOf(original.id), session().sets.map { it.id })

        scrollFloorTo(WorkoutTestTags.setChip(original.id))
        compose.onNodeWithTag(WorkoutTestTags.setChip(original.id)).performClick()
        compose.onNodeWithText(SetRowCopy.revise(SetOrdinalCopy.working(1, TARGET_SETS))).performClick()
        enterNumbers(85.0, 3)
        chooseEffort(9)
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

        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsEnabled().performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        compose.onNodeWithText("Save as is").performClick()
        awaitTag(SummaryTags.DONE)
        assertNoBlockingDialogs()
        compose.onNodeWithText("WORKOUT COMPLETE").assertIsDisplayed()
        compose.onNodeWithText(fixture.routineName).assertIsDisplayed()
        compose.onNodeWithContentDescription("Total volume 255 kg").assertIsDisplayed()
        captureWindow("summary")
        val finished = session()
        assertNotNull(finished.finishedAt)
        assertEquals(corrected, finished.sets.single())
        assertEquals(255.0, finished.work().volumeKg, EPSILON)
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
        assertFalse(container.restTimerStore.current().running)
        val setLine = SetCopy.setLine(85.0, 3, LoadClass.LOADED, WeightUnit.KG)
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("Top set $setLine"))
        compose.onNodeWithText("Top set $setLine").assertIsDisplayed()

        // This is production AppNav: Done must return to Home, then the real History tab
        // must lead to the same finished session rather than mounting a test-host screen.
        compose.onNodeWithTag(SummaryTags.DONE).performClick()
        awaitTag("navigation-home")
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        compose.onNodeWithTag(SummaryTags.DONE).assertDoesNotExist()
        compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).assertDoesNotExist()
        compose.onNodeWithTag("navigation-history").performClick()
        compose.onNodeWithTag("navigation-history").assertIsSelected()
        awaitScrollableContent()
        val historyRow = hasTestTag(SessionLogTags.ROW) and hasContentDescription(fixture.routineName, substring = true)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(historyRow)
        val row = compose.onNode(historyRow).assertIsDisplayed()
        val spoken = row.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(spoken, spoken.contains("1 sets, 255 kg"))
        val historical = runBlocking(Dispatchers.IO) {
            container.workoutRepository.observeHistory().first().single { it.id == sessionId }
        }
        assertEquals(finished, historical)
        row.performClick()
        awaitTag(SessionDetailTestTags.CONTENT)
        compose.onNodeWithText(fixture.routineName).assertIsDisplayed()
        compose.onNodeWithText("255").assertIsDisplayed()
        compose.onNodeWithText("Working volume", ignoreCase = true).assertIsDisplayed()
        val tableRow = SetTableLine.fromLog(corrected, LoadClass.LOADED, WeightUnit.KG)
        val rowDescription = "${tableRow.extras}, ${tableRow.line}"
        compose.onNodeWithTag(SessionDetailTestTags.CONTENT)
            .performScrollToNode(hasContentDescription(rowDescription))
        compose.onNodeWithContentDescription(rowDescription).assertIsDisplayed()
        assertEquals(corrected, session().sets.single())
        captureWindow("history-detail")
        compose.onNodeWithTag(SessionDetailTestTags.BACK).performClick()
        compose.onNodeWithTag("navigation-history").assertIsSelected()
        assertNull(runBlocking(Dispatchers.IO) { container.workoutRepository.getInProgress() })
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
        val first = createExercise("$PREFIX squat $suffix")
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

    private fun assertSet(set: SetLog, setId: String, weightKg: Double, reps: Int, effort: Int) {
        assertEquals(sessionId, set.sessionId)
        assertEquals(setId, set.id)
        assertEquals(fixture.first.id, set.exerciseId)
        assertEquals(weightKg, set.weightKg, EPSILON)
        assertEquals(reps, set.reps)
        assertEquals(effort, set.rpe)
        assertFalse(set.isWarmup)
    }

    private fun enterNumbers(weightKg: Double, reps: Int, mustDiffer: Boolean = false) {
        assertNoBlockingDialogs()
        scrollFloorTo(WorkoutTestTags.WEIGHT_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        val weight = compose.onNodeWithTag(NumberEntryTags.FIELD)
        if (mustDiffer) {
            val prefill = NumericEntry.parseWeightKg(weight.fetchSemanticsNode().config[SemanticsProperties.EditableText].text, WeightUnit.KG)
            assertNotNull(prefill)
            assertTrue("manual weight differs from the rendered prefill", prefill != weightKg)
        }
        weight.performTextReplacement(weightKg.toString())
        compose.onNodeWithText("Set").performClick()
        scrollFloorTo(WorkoutTestTags.REPS_STEPPER)
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
        scrollFloorTo(WorkoutTestTags.WEIGHT_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        val weight = compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
        assertEquals(weightKg, checkNotNull(NumericEntry.parseWeightKg(weight.config[SemanticsProperties.EditableText].text, WeightUnit.KG)), EPSILON)
        compose.onNodeWithText("Cancel").performClick()
        scrollFloorTo(WorkoutTestTags.REPS_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).performClick()
        val repetitions = compose.onNodeWithTag(NumberEntryTags.FIELD).fetchSemanticsNode()
        assertEquals(reps, NumericEntry.parseReps(repetitions.config[SemanticsProperties.EditableText].text))
        compose.onNodeWithText("Cancel").performClick()
    }

    private fun chooseEffort(value: Int) {
        assertNoBlockingDialogs()
        scrollFloorTo(WorkoutTestTags.rpeChoice(value))
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

    private fun clickVisibleAndroidText(label: String) {
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
                root.packageName?.toString() == "com.sinura.personaltrainer.debug" && click(root)
            } finally { root.recycle() }
        }
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
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.startsWith("Android/sdk_")) {
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
