package com.sinura.personaltrainer.ui.walkthrough

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.ColdStartIntroCopy
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingPreviewCopy
import com.sinura.personaltrainer.domain.PlanSetupCopy
import com.sinura.personaltrainer.domain.RestNudgeCopy
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SettingsHomeCopy
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.isDisposableSdkEmulator
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.activity.CardioTags
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.home.HomeStartTags
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.plan.CardioPickTags
import com.sinura.personaltrainer.ui.intro.ColdStartIntroTags
import com.sinura.personaltrainer.ui.onboarding.OnboardingTags
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.progress.BodyTags
import com.sinura.personaltrainer.ui.settings.SettingsTags
import com.sinura.personaltrainer.ui.workout.NativeWorkoutFixtureEnvironment
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import com.sinura.personaltrainer.ui.workout.revealFloorControlAboveTempo
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-only UX walkthrough: numbered PNGs land in `/sdcard/Download/` via [NativeArtifacts].
 * Each flow is isolated; failures capture a FAIL frame and the run continues.
 */
@RunWith(AndroidJUnit4::class)
class UxWalkthroughTest {
    private val environment = NativeWorkoutFixtureEnvironment(PREFIX)
    private lateinit var container: AppContainer
    private lateinit var app: PersonalTrainerApp
    private var step = 1
    private lateinit var walkthroughRoutineId: String
    private lateinit var walkthroughRoutineName: String
    private lateinit var primaryLift: Exercise
    private lateinit var secondaryLift: Exercise
    private var environmentPrepared = false
    private val flowOutcomes = linkedMapOf<String, String>()

    private val launchIntent = Intent(
        ApplicationProvider.getApplicationContext(),
        MainActivity::class.java,
    )
    private val scenarioRule = ActivityScenarioRule<MainActivity>(launchIntent)
    private val compose = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    }

    @get:Rule
    val composeRule = compose

    @Before
    fun prepare() {
        check(isDisposableSdkEmulator()) {
            "UX walkthrough runs only on an SDK emulator (hardware=${Build.HARDWARE}, fingerprint=${Build.FINGERPRINT})."
        }
        app = ApplicationProvider.getApplicationContext()
        container = app.container
        ensureHarnessPermissions()
    }

    @After
    fun restore() {
        runCatching {
            runBlocking(Dispatchers.IO) { withTimeout(30_000) { cleanSyntheticData() } }
        }
        if (environmentPrepared) {
            environment.restore()
        }
        shell("settings put system font_scale 1.0")
    }

    @Test
    fun captureProductWalkthrough() {
        when (val flow = InstrumentationRegistry.getArguments().getString("uxFlow")) {
            null, "", "all" -> runAllFlows()
            "1" -> runFlow("01-cold-start-intro") { flowColdStartIntro() }
            "2" -> runFlow("02-onboarding-preview-accept") { flowOnboardingAcceptPlan() }
            "3" -> runFlow("03-main-tabs") { flowMainTabs() }
            "4" -> runFlow("04-live-strength") { flowLiveStrengthWorkout() }
            "5" -> runFlow("05-live-cardio") { flowLiveCardio() }
            "6" -> runFlow("06-past-mixed-save") { flowPastMixedSave() }
            "7" -> runFlow("07-body-map-measurements") { flowBodyMapAndMeasurements() }
            "8" -> runFlow("08-settings-units-backup") { flowSettingsUnitsAndBackup() }
            "9" -> runFlow("09-font-scale-live-workout") { flowLargeFontLiveWorkout() }
            else -> error("Unknown uxFlow argument: $flow")
        }
        emitFlowSummary()
        println("UX_WALKTHROUGH_STEPS=$step")
    }

    private fun runAllFlows() {
        runFlow("01-cold-start-intro") { flowColdStartIntro() }
        runFlow("02-onboarding-preview-accept") { flowOnboardingAcceptPlan() }
        runFlow("03-main-tabs") { flowMainTabs() }
        runFlow("04-live-strength") { flowLiveStrengthWorkout() }
        runFlow("05-live-cardio") { flowLiveCardio() }
        runFlow("06-past-mixed-save") { flowPastMixedSave() }
        runFlow("07-body-map-measurements") { flowBodyMapAndMeasurements() }
        runFlow("08-settings-units-backup") { flowSettingsUnitsAndBackup() }
        runFlow("09-font-scale-live-workout") { flowLargeFontLiveWorkout() }
        shot("99-complete")
        val failed = flowOutcomes.filterValues { it.startsWith("FAIL") }
        if (failed.isNotEmpty()) {
            fail("Flows failed: ${failed.keys.joinToString()}")
        }
    }

    private fun runFlow(name: String, block: () -> Unit) {
        val rethrow = shouldRethrowFlowFailures()
        try {
            block()
            flowOutcomes[name] = "PASS"
            logHarness("UX_WALKTHROUGH_FLOW_RESULT $name PASS")
        } catch (failure: Throwable) {
            val detail = failure.message?.take(160) ?: failure.javaClass.simpleName
            flowOutcomes[name] = "FAIL:$detail"
            logHarness("UX_WALKTHROUGH_FLOW_RESULT $name FAIL $detail")
            failure.printStackTrace()
            shot("FAIL-$name")
            if (rethrow) throw failure
        }
    }

    private fun shouldRethrowFlowFailures(): Boolean {
        val flow = InstrumentationRegistry.getArguments().getString("uxFlow")
        return !flow.isNullOrEmpty() && flow != "all"
    }

    private fun emitFlowSummary() {
        if (flowOutcomes.isEmpty()) return
        val summary = flowOutcomes.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        logHarness("UX_WALKTHROUGH_FLOW_SUMMARY $summary")
    }

    private fun ensureHarnessPermissions() {
        val pkg = app.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }
        shell("cmd appops set $pkg SCHEDULE_EXACT_ALARM allow")
        shell("dumpsys deviceidle whitelist +$pkg")
    }

    private fun logHarness(message: String) {
        println(message)
        Log.i(HARNESS_LOG_TAG, message)
    }

    private fun flowColdStartIntro() {
        val introVisible = runCatching {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag(ColdStartIntroTags.ROOT).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (introVisible) {
            shot("cold-start-intro")
            compose.onNodeWithTag(ColdStartIntroTags.ROOT).performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag(ColdStartIntroTags.ROOT).fetchSemanticsNodes().isEmpty()
            }
            app.markColdStartIntroShown()
            shot("cold-start-dismissed")
        } else {
            shot("cold-start-already-advanced")
            app.markColdStartIntroShown()
        }
        ensureEnvironmentPrepared()
        runBlocking(Dispatchers.IO) {
            container.preferencesRepository.setLaunchPermissionsAsked(true)
            container.preferencesRepository.markRestAlertsAsked()
            container.preferencesRepository.markRestBatteryHintShown()
            container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
        }
        scenarioRule.scenario.recreate()
        ensureHarnessPermissions()
        dismissBlockingUi()
        awaitHomeReady()
    }

    private fun ensureEnvironmentPrepared() {
        if (environmentPrepared) return
        environment.prepare(container)
        environmentPrepared = true
    }

    private suspend fun awaitCatalogReady() {
        withTimeout(90_000) {
            container.exerciseRepository.observeAll().first { rows ->
                rows.any { it.id == "ex-barbell-back-squat" }
            }
        }
    }

    private fun advanceOnboardingWizardSteps() {
        val labels = listOf(
            TrainingFocus.STRENGTH.displayName,
            TrainingAge.NEW.displayName,
            "Continue",
            "Space them out for me",
            "Continue",
            TrainingGoal.GENERAL.displayName,
            TrainingEmphasis.BALANCED.displayName,
            "Skip this",
        )
        for (label in labels) {
            runBlocking(Dispatchers.IO) { awaitCatalogReady() }
            clickOnboardingLabel(label)
        }
        runBlocking(Dispatchers.IO) { awaitCatalogReady() }
    }

    private suspend fun seedOnboardingAnswersForPreview() {
        awaitCatalogReady()
        runCatching { container.dbMaintenance.seedCatalog() }
        awaitCatalogReady()
        container.preferencesRepository.setTrainingFocus(TrainingFocus.STRENGTH)
        container.preferencesRepository.setTrainingAge(TrainingAge.NEW)
        container.preferencesRepository.setTrainingDaysPerWeek(SchedulePreferences.DEFAULT_DAYS)
        container.preferencesRepository.setPreferredDays(emptySet())
        container.preferencesRepository.setTrainingPlaces(setOf(TrainingPlace.FULL_GYM))
        container.preferencesRepository.setTrainingGoal(TrainingGoal.GENERAL)
        container.preferencesRepository.setTrainingEmphasis(TrainingEmphasis.BALANCED)
    }

    private fun cardioSessionFinished(): Boolean {
        if (compose.onAllNodesWithText(CardioCopy.FINISHED_TITLE).fetchSemanticsNodes().isNotEmpty()) {
            return true
        }
        if (compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()) {
            return true
        }
        if (compose.onAllNodesWithText(CardioCopy.FINISHING).fetchSemanticsNodes().isNotEmpty()) {
            return false
        }
        return compose.onAllNodesWithTag(CardioTags.ELAPSED).fetchSemanticsNodes().isEmpty()
    }

    private fun dismissSoftKeyboard() {
        compose.waitForIdle()
        runCatching { compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction() }
        compose.waitForIdle()
    }

    private fun flowOnboardingAcceptPlan() {
        app.markColdStartIntroShown()
        runBlocking(Dispatchers.IO) {
            cleanSyntheticData()
            container.preferencesRepository.setOnboardingComplete(false)
            container.preferencesRepository.setLaunchPermissionsAsked(true)
            container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
            container.preferencesRepository.markRestBatteryHintShown()
            container.preferencesRepository.markRestAlertsAsked()
            awaitCatalogReady()
            runCatching { container.dbMaintenance.seedCatalog() }
            awaitCatalogReady()
        }
        scenarioRule.scenario.recreate()
        ensureHarnessPermissions()
        dismissTransientOverlays()
        awaitHomeReady()
        openGuidedSetup()
        shot("onboarding-start")
        runBlocking(Dispatchers.IO) { seedOnboardingAnswersForPreview() }
        scenarioRule.scenario.recreate()
        ensureHarnessPermissions()
        dismissTransientOverlays()
        awaitHomeReady()
        openGuidedSetup()
        advanceOnboardingWizardSteps()
        compose.waitUntil(90_000) {
            compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        shot("onboarding-plan-preview")
        scrollToTag(OnboardingTags.USE_PLAN)
        compose.onNodeWithTag(OnboardingTags.USE_PLAN).performClick()
        if (compose.onAllNodesWithText(OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN).performClick()
        }
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag(OnboardingTags.USE_PLAN).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
        }
        awaitHomeReady()
        shot("onboarding-accepted-home")
    }

    private fun flowMainTabs() {
        preparePopulatedApp()
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (label in listOf("Home", "Plan", "Body", "History", "Settings")) {
            dismissTransientOverlays()
            compose.onNode(hasText(label.uppercase()) and tabRole).performClick()
            val loaded = when (label) {
                "Home" -> hasTestTag(HomeTags.START)
                "Body" -> hasTestTag(BodyTags.MAP)
                "Plan" -> hasTestTag(PlanTags.ADD_SESSION)
                "History" -> hasTestTag("history-horizon-readout")
                else -> hasTestTag(SettingsTags.HOME)
            }
            compose.waitUntil(30_000) {
                compose.onAllNodes(loaded).fetchSemanticsNodes().isNotEmpty()
            }
            shot("tab-${label.lowercase()}")
        }
    }

    private fun flowLiveStrengthWorkout() {
        preparePopulatedApp()
        seedWalkthroughRoutine()
        awaitHomeReady()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithTag(HomeStartTags.ROUTINE).performScrollTo().performClick()
        compose.onNodeWithTag(HomeStartTags.routine(walkthroughRoutineId)).performScrollTo().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.SET_ENTRY)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.CONTENT)
            .performScrollToNode(hasTestTag(WorkoutTestTags.liftCard(primaryLift.id)))
        shot("strength-live-entry")
        repeat(3) { index ->
            logWorkingSet(weightKg = 60.0 + index, reps = 5 + index, rpe = 7 + index)
            shot("strength-set-${index + 1}-logged")
            if (index < 2) {
                skipRestIfVisible()
                shot("strength-rest-after-set-${index + 1}")
            }
        }
        advanceToNextExerciseIfShown()
        repeat(3) { index ->
            logWorkingSet(weightKg = 50.0 + index, reps = 6 + index, rpe = 7 + index)
            shot("strength-set-lift2-${index + 1}-logged")
            if (index < 2) skipRestIfVisible()
        }
        finishLiveWorkout()
        shot("strength-finished-summary")
    }

    private fun flowLiveCardio() {
        preparePopulatedApp()
        awaitHomeReady()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithTag(HomeStartTags.CARDIO).performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(HomeStartTags.CARDIO_PAGE).fetchSemanticsNodes().isNotEmpty()
        }
        shot("cardio-picker")
        val runCard = hasTestTag(CardioPickTags.card(CardioType.RUN))
        compose.onNodeWithTag(HomeStartTags.CARDIO_PAGE).performScrollToNode(runCard)
        compose.onNode(runCard).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(CardioTags.ELAPSED).fetchSemanticsNodes().isNotEmpty()
        }
        shot("cardio-live")
        SystemClock.sleep(3_000)
        dismissSoftKeyboard()
        compose.waitUntil(45_000) {
            compose.onAllNodes(hasTestTag(CardioTags.FINISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(CardioTags.FINISH).performClick()
        compose.waitUntil(120_000) { cardioSessionFinished() }
        if (compose.onAllNodesWithText(CardioCopy.FINISHED_TITLE).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Back").performClick()
        }
        awaitHomeReady()
        shot("cardio-finished")
    }

    private fun flowPastMixedSave() {
        preparePopulatedApp()
        goToTab("history")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(HistoryTags.START_SHEET).performScrollTo().performClick()
        clickLabel("Mixed session")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("composer-title") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        shot("mixed-composer-open")
        runCatching { compose.onNodeWithTag("composer-title").performScrollTo() }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("composer-title") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("composer-title").performClick()
        compose.onNodeWithTag("composer-title").performTextInput("UX walkthrough mixed")
        dismissSoftKeyboard()
        scrollToTag(ComposerTags.CHOOSE_LIFT)
        compose.onNodeWithTag(ComposerTags.CHOOSE_LIFT).performClick()
        awaitPicker()
        val query = hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search"))
        compose.onNode(query).performTextReplacement("squat")
        compose.onNode(query).performImeAction()
        val result = hasText("Barbell Back Squat") and hasAnyAncestor(hasTestTag("picker-content"))
        compose.onNodeWithTag("picker-content").performScrollToNode(result)
        compose.onNode(result).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isEmpty()
        }
        scrollToTag(ComposerTags.ADD_SET)
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3
        }
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("60")
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement("5")
        dismissSoftKeyboard()
        compose.onNodeWithTag(ComposerTags.ADD_SET).performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(ComposerTags.SAVE).fetchSemanticsNodes().isNotEmpty()
        }
        shot("mixed-composer-filled")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(ComposerTags.SAVE)).fetchSemanticsNodes().isNotEmpty()
        }
        dismissSoftKeyboard()
        runCatching { compose.onNodeWithTag(ComposerTags.SAVE).performScrollTo() }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(ComposerTags.SAVE) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(ComposerTags.SAVE).performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("composer-title").fetchSemanticsNodes().isEmpty() &&
                (
                    compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty() ||
                        compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
                    )
        }
        if (compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isEmpty()) {
            goToTab("history")
        }
        shot("mixed-saved")
    }

    private fun flowBodyMapAndMeasurements() {
        preparePopulatedApp()
        goToTab("progress")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(BodyTags.MAP).fetchSemanticsNodes().isNotEmpty()
        }
        shot("body-map")
        if (compose.onAllNodesWithTag(BodyTags.MUSCLES).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(BodyTags.MUSCLES).performScrollTo()
            shot("body-muscles")
        }
        goToTab("settings")
        openSettingsRow(SettingsHomeCopy.BODYWEIGHT)
        shot("bodyweight-measurements")
        pressBackToSettingsHome()
    }

    private fun flowSettingsUnitsAndBackup() {
        preparePopulatedApp()
        goToTab("settings")
        openSettingsRow(SettingsHomeCopy.DISPLAY)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(SettingsTags.DISPLAY).fetchSemanticsNodes().isNotEmpty()
        }
        shot("settings-units")
        pressBackToSettingsHome()
        compose.onNodeWithTag(SettingsTags.ROW_BACKUP).performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(SettingsTags.EXPORT_FILE).fetchSemanticsNodes().isNotEmpty()
        }
        shot("settings-backup-view")
        pressBackToSettingsHome()
    }

    private fun flowLargeFontLiveWorkout() {
        shell("settings put system font_scale 1.3")
        preparePopulatedApp()
        ensureHarnessPermissions()
        seedWalkthroughRoutine()
        awaitHomeReady()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithTag(HomeStartTags.ROUTINE).performScrollTo().performClick()
        compose.onNodeWithTag(HomeStartTags.routine(walkthroughRoutineId)).performScrollTo().performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.SET_ENTRY)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("strength-live-font-1-3")
        runBlocking(Dispatchers.IO) {
            container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }
        }
        pressBackToHome()
    }

    private fun preparePopulatedApp() {
        ensureEnvironmentPrepared()
        runBlocking(Dispatchers.IO) {
            cleanSyntheticData()
            container.preferencesRepository.setOnboardingComplete(true)
            container.preferencesRepository.setLaunchPermissionsAsked(true)
            container.preferencesRepository.setSavePosture(SavePosture.LOCAL)
            container.preferencesRepository.setWeightUnit(WeightUnit.KG)
            container.preferencesRepository.markRestBatteryHintShown()
            container.preferencesRepository.markRestAlertsAsked()
            seedPopulatedHome()
        }
        app.markColdStartIntroShown()
        dismissTransientOverlays()
        awaitHomeReady()
    }

    private suspend fun seedPopulatedHome() {
        val catalog = container.exerciseRepository.observeAll().first { items ->
            items.any { it.id == "ex-barbell-back-squat" }
        }
        val squat = catalog.first { it.id == "ex-barbell-back-squat" }
        val row = catalog.first { it.id == "ex-barbell-row" }
        val routine = container.routineRepository.create("$PREFIX seed")
        for (exercise in listOf(squat, row)) {
            container.routineRepository.addExercise(
                routineId = routine.id,
                exercise = exercise,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 60.0,
                restSeconds = 90,
            )
        }
        val session = container.workoutRepository.startRoutine(
            checkNotNull(container.routineRepository.getById(routine.id)),
        )
        container.workoutRepository.logSet(session.id, squat.id, 60.0, 8, null, false)
        container.workoutRepository.finishSession(session.id, "")
    }

    private fun seedWalkthroughRoutine() = runBlocking(Dispatchers.IO) {
        withTimeout(60_000) {
            val catalog = container.exerciseRepository.observeAll().first { rows ->
                rows.any { it.id == "ex-barbell-back-squat" }
            }
            primaryLift = catalog.first { it.id == "ex-barbell-back-squat" }
            secondaryLift = catalog.first { it.id == "ex-barbell-row" }
            val suffix = UUID.randomUUID().toString().take(6)
            walkthroughRoutineName = "$PREFIX $suffix"
            val routine = container.routineRepository.create(walkthroughRoutineName)
            walkthroughRoutineId = routine.id
            for (exercise in listOf(primaryLift, secondaryLift)) {
                container.routineRepository.addExercise(
                    routineId = routine.id,
                    exercise = exercise,
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 60.0,
                    restSeconds = 90,
                )
            }
        }
    }

    private suspend fun cleanSyntheticData() {
        container.restTimerController.stop()
        container.workoutRepository.getInProgress()?.let { live ->
            if (live.routineName?.startsWith(PREFIX) == true) container.discardWorkout(live.id)
        }
        container.activityRepository.getLive()?.let { container.discardActivity(it.id) }
        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(PREFIX) == true || it.routineName == "UX walkthrough mixed" }
            .forEach { container.workoutRepository.deleteFinishedSession(it.id) }
        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(PREFIX) }
            .forEach { container.routineRepository.delete(it.id) }
    }

    private fun logWorkingSet(weightKg: Double, reps: Int, rpe: Int) {
        enterNumbers(weightKg, reps)
        compose.revealFloorControlAboveTempo(WorkoutTestTags.rpeChoice(rpe))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(rpe)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.LOG_SET) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).performClick()
        compose.waitForIdle()
    }

    private fun enterNumbers(weightKg: Double, reps: Int) {
        compose.onNodeWithTag(WorkoutTestTags.CONTENT).performScrollToNode(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER))
        compose.revealFloorControlAboveTempo(WorkoutTestTags.WEIGHT_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(weightKg.toString())
        compose.onNodeWithText("Set").performClick()
        compose.revealFloorControlAboveTempo(WorkoutTestTags.REPS_STEPPER)
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).performClick()
        compose.onNodeWithTag(NumberEntryTags.FIELD).performTextReplacement(reps.toString())
        compose.onNodeWithText("Set").performClick()
    }

    private fun advanceToNextExerciseIfShown() {
        compose.waitUntil(30_000) {
            val next = compose.onAllNodes(hasTestTag(WorkoutTestTags.NEXT) and isEnabled()).fetchSemanticsNodes()
            val finish = compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled()).fetchSemanticsNodes()
            next.isNotEmpty() || finish.isNotEmpty()
        }
        if (compose.onAllNodes(hasTestTag(WorkoutTestTags.NEXT) and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasTestTag(WorkoutTestTags.SET_ENTRY)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(WorkoutTestTags.CONTENT)
                .performScrollToNode(hasTestTag(WorkoutTestTags.liftCard(secondaryLift.id)))
        }
    }

    private fun skipRestIfVisible() {
        val autoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            if (compose.onAllNodesWithTag(WorkoutTestTags.REST_SKIP).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
            } else if (compose.onAllNodesWithText(RestNudgeCopy.SKIP).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText(RestNudgeCopy.SKIP).performClick()
            }
        } finally {
            compose.mainClock.autoAdvance = autoAdvance
        }
        compose.waitForIdle()
    }

    private fun openSwapPicker() {
        compose.onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
        compose.onNodeWithText(CurrentLiftCopy.SWAP).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun finishLiveWorkout() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        compose.onNodeWithText("Save as is").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("WORKOUT COMPLETE").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openGuidedSetup() {
        goToTab("settings")
        clickLabel(SettingsHomeCopy.PLAN)
        clickLabel(PlanSetupCopy.ROW_TITLE)
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("What do you want to record?").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettingsRow(title: String) {
        scrollToText(title)
        compose.onNodeWithText(title).performClick()
    }

    private fun goToTab(path: String) {
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val label = when (path) {
            "home" -> "Home"
            "progress" -> "Body"
            "routines" -> "Plan"
            "history" -> "History"
            "settings" -> "Settings"
            else -> error("Unknown tab $path")
        }
        val tab = hasText(label.uppercase()) and tabRole
        compose.waitUntil(60_000) {
            compose.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(tab).performClick()
        compose.waitForIdle()
    }

    private fun awaitHomeReady() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
        }
        dismissTransientOverlays()
    }

    private fun dismissTransientOverlays() {
        dismissBlockingUi()
    }

    private fun dismissBlockingUi() {
        repeat(5) {
            compose.waitForIdle()
            listOf("Not now", "Wait", "Close", "OK", "Dismiss").forEach { label ->
                if (compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()) {
                    runCatching { compose.onNodeWithText(label).performClick() }
                }
            }
            if (compose.onAllNodesWithText(ColdStartIntroCopy.SKIP).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag(ColdStartIntroTags.ROOT).fetchSemanticsNodes().isNotEmpty()
            ) {
                compose.onNodeWithTag(ColdStartIntroTags.ROOT).performClick()
                app.markColdStartIntroShown()
            }
        }
    }

    private fun clickLabel(label: String) {
        compose.waitUntil(60_000) {
            if (compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isEmpty()) {
                runCatching {
                    compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                        .performScrollToNode(hasText(label))
                }
            }
            compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    /** Onboarding rows are not always reported as enabled to the test matcher; scroll and click. */
    private fun clickOnboardingLabel(label: String) {
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        runCatching {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasText(label))
        }
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    private fun scrollToText(text: String) {
        compose.waitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasText(text))
            }.isSuccess || compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToTag(tag: String) {
        compose.waitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasTestTag(tag))
            }.isSuccess
        }
    }

    private fun awaitPicker() {
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBackToSettingsHome() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(SettingsTags.HOME).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBackToHome() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitHomeReady()
    }

    private fun shot(label: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val safeLabel = label.replace(Regex("[^A-Za-z0-9-]"), "-")
        val name = "step-${step.toString().padStart(3, '0')}-$safeLabel"
        step += 1
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot() ?: automation.executeShellCommand("screencap -p").use { pipe ->
            FileInputStream(pipe.fileDescriptor).use(BitmapFactory::decodeStream)
        }
        checkNotNull(bitmap) { "Screenshot failed for $name" }
        try {
            val path = NativeArtifacts.write(name, bitmap)
            println("UX_WALKTHROUGH_CAPTURE $path ${bitmap.width}x${bitmap.height}")
            assertTrue("Capture must be non-trivial", bitmap.width > 100 && bitmap.height > 100)
        } finally {
            bitmap.recycle()
        }
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe ->
            FileInputStream(pipe.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private companion object {
        const val PREFIX = "UX walkthrough"
        const val HARNESS_LOG_TAG = "UXWalkthrough"
    }
}
