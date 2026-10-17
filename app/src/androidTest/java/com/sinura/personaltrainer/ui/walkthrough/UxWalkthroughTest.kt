package com.sinura.personaltrainer.ui.walkthrough

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.EndWorkoutCopy
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
import com.sinura.personaltrainer.ui.activity.ActivityDetailTags
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.activity.CardioTags
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.history.HistoryTags
import com.sinura.personaltrainer.ui.home.HomeStartTags
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.plan.CardioPickTags
import com.sinura.personaltrainer.ui.intro.ColdStartIntroTags
import com.sinura.personaltrainer.ui.onboarding.CATALOG_MISSING_MESSAGE
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

    /** Every Compose wait dismisses system ANR overlays so CI navigation is not blocked mid-poll. */
    private fun harnessWaitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis) {
            dismissSystemNotRespondingDialogs()
            condition()
        }
    }

    private fun flowColdStartIntro() {
        val introVisible = runCatching {
            harnessWaitUntil(5_000) {
                compose.onAllNodesWithTag(ColdStartIntroTags.ROOT).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (introVisible) {
            shot("cold-start-intro")
            compose.onNodeWithTag(ColdStartIntroTags.ROOT).performClick()
            harnessWaitUntil(15_000) {
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

    private suspend fun seedCatalogAndLog(label: String) {
        val wallStartMs = System.currentTimeMillis()
        val elapsedStart = SystemClock.elapsedRealtime()
        logHarness("UX_WALKTHROUGH_CATALOG_SEED_START label=$label wallMs=$wallStartMs")
        runCatching { container.dbMaintenance.seedCatalog() }
        val durationMs = SystemClock.elapsedRealtime() - elapsedStart
        logHarness("UX_WALKTHROUGH_CATALOG_SEED_END label=$label durationMs=$durationMs wallMs=${System.currentTimeMillis()}")
    }

    private suspend fun awaitCatalogReady() {
        val elapsedStart = SystemClock.elapsedRealtime()
        logHarness("UX_WALKTHROUGH_CATALOG_AWAIT_START")
        val rows = withTimeout(90_000) {
            container.exerciseRepository.observeAll().first { items ->
                items.any { it.id == "ex-barbell-back-squat" }
            }
        }
        logHarness(
            "UX_WALKTHROUGH_CATALOG_AWAIT_END durationMs=${SystemClock.elapsedRealtime() - elapsedStart} " +
                "rowCount=${rows.size}",
        )
    }

    private fun clickOnboardingPrimaryAction(label: String) {
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    /** Real taps through setup so [com.sinura.personaltrainer.ui.onboarding.OnboardingViewModel.next] runs. */
    private fun walkOnboardingSetupAsUser() {
        harnessWaitUntil(120_000) {
            compose.onAllNodesWithText("What do you want to record?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingLabel(TrainingFocus.STRENGTH.displayName)
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("How much lifting have you done?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingLabel(TrainingAge.NEW.displayName)
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("How many days a week can you train?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingPrimaryAction("Continue")
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("Which days suit you?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingPrimaryAction("Space them out for me")
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("Where will you train?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingLabel(TrainingPlace.FULL_GYM.displayName)
        clickOnboardingPrimaryAction("Continue")
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("What are you training for?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingLabel(TrainingGoal.GENERAL.displayName)
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("Where do you want the work?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingLabel(TrainingEmphasis.BALANCED.displayName)
        harnessWaitUntil(60_000) {
            compose.onAllNodesWithText("Roughly what do you weigh?").fetchSemanticsNodes().isNotEmpty()
        }
        clickOnboardingPrimaryAction("Skip this")
    }

    private fun tapCatalogRetryIfBannerVisible() {
        if (compose.onAllNodesWithText(CATALOG_MISSING_MESSAGE).fetchSemanticsNodes().isEmpty()) return
        runCatching { compose.onNodeWithText("Retry").performClick() }
        compose.waitForIdle()
        runBlocking(Dispatchers.IO) {
            seedCatalogAndLog("retry-banner")
            awaitCatalogReady()
        }
    }

    /**
     * Waits for [OnboardingTags.USE_PLAN] to become enabled, recovering from catalog banners and
     * logging how long the preview button stayed disabled after it first appeared.
     */
    private fun logHarnessCatalogSnapshot(label: String) {
        runBlocking(Dispatchers.IO) {
            val rows = runCatching { container.exerciseRepository.observeAll().first() }.getOrDefault(emptyList())
            logHarness(
                "UX_WALKTHROUGH_CATALOG_SNAPSHOT label=$label rowCount=${rows.size} " +
                    "hasSquat=${rows.any { it.id == "ex-barbell-back-squat" }}",
            )
        }
    }

    private fun logOnboardingPreviewHeadline() {
        val headline = when {
            compose.onAllNodesWithText(OnboardingPreviewCopy.PREPARING_PLAN).fetchSemanticsNodes().isNotEmpty() ->
                "preparing-plan"
            compose.onAllNodesWithText(CATALOG_MISSING_MESSAGE).fetchSemanticsNodes().isNotEmpty() ->
                "catalog-error"
            compose.onAllNodesWithText("Building it…").fetchSemanticsNodes().isNotEmpty() ->
                "building"
            compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN) and isEnabled()).fetchSemanticsNodes().isNotEmpty() ->
                "use-plan-enabled"
            compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN)).fetchSemanticsNodes().isNotEmpty() ->
                "use-plan-disabled"
            compose.onAllNodesWithText("Here's your block").fetchSemanticsNodes().isNotEmpty() ->
                "preview-title"
            else -> "unknown"
        }
        logHarness("UX_WALKTHROUGH_ONBOARDING_HEADLINE=$headline")
    }

    private fun logFlow2Diagnostics(label: String) {
        logHarness("UX_WALKTHROUGH_FLOW2_DIAG=$label")
        logHarnessCatalogSnapshot(label)
        logOnboardingPreviewHeadline()
    }

    /**
     * On preview: "Preparing your plan" may flash; [OnboardingTags.USE_PLAN] must enable within 30s.
     * The accept button sits below the week list — scroll the preview [LazyColumn] before waiting on the tag.
     */
    private fun assertUsePlanReadyWithin30Seconds() {
        harnessWaitUntil(120_000) {
            dismissSystemNotRespondingDialogs()
            tapCatalogRetryIfBannerVisible()
            when {
                compose.onAllNodesWithText("Here's your block").fetchSemanticsNodes().isNotEmpty() -> {
                    scrollPreviewToUsePlan()
                    usePlanPresentInTree()
                }
                usePlanPresentInTree() -> true
                compose.onAllNodesWithText(OnboardingPreviewCopy.PREPARING_PLAN).fetchSemanticsNodes().isNotEmpty() ->
                    true
                compose.onAllNodesWithText("Building it…").fetchSemanticsNodes().isNotEmpty() -> true
                else -> false
            }
        }
        scrollPreviewToUsePlan()
        logFlow2Diagnostics("preview-visible")
        var preparingSeen = false
        var disabledSinceMs: Long? = null
        try {
            harnessWaitUntil(30_000) {
                dismissSystemNotRespondingDialogs()
                tapCatalogRetryIfBannerVisible()
                scrollPreviewToUsePlan()
                if (compose.onAllNodesWithText(OnboardingPreviewCopy.PREPARING_PLAN).fetchSemanticsNodes().isNotEmpty()) {
                    preparingSeen = true
                }
                logOnboardingPreviewHeadline()
                val previewVisible = usePlanPresentInTree()
                if (previewVisible && disabledSinceMs == null) {
                    disabledSinceMs = SystemClock.elapsedRealtime()
                }
                compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN) and isEnabled(), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty() ||
                    compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN) and isEnabled()).fetchSemanticsNodes()
                        .isNotEmpty()
            }
        } catch (timeout: Throwable) {
            logFlow2Diagnostics("use-plan-timeout-30s")
            shot("flow2-use-plan-timeout")
            val catalogLoaded = runBlocking(Dispatchers.IO) {
                container.exerciseRepository.observeAll().first().any { it.id == "ex-barbell-back-squat" }
            }
            if (catalogLoaded) {
                logHarness("UX_WALKTHROUGH_FLOW2_POSSIBLE_APP_BUG=catalog-loaded-use-plan-still-disabled")
            }
            throw timeout
        }
        logHarness("UX_WALKTHROUGH_PREPARING_PLAN_SEEN=$preparingSeen")
        disabledSinceMs?.let {
            logHarness("UX_WALKTHROUGH_PLAN_PREVIEW_DISABLED_MS=${SystemClock.elapsedRealtime() - it}")
        }
        logFlow2Diagnostics("use-plan-enabled")
    }

    private fun leaveOnboardingScreen() {
        repeat(3) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            if (compose.onAllNodesWithTag(SettingsTags.HOME).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty()
            ) {
                return
            }
        }
        awaitHomeReady()
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
            seedCatalogAndLog("flow2-pm-clear")
            awaitCatalogReady()
        }
        scenarioRule.scenario.recreate()
        ensureHarnessPermissions()
        dismissTransientOverlays()
        awaitHomeReady()
        openGuidedSetup()
        shot("onboarding-start")
        walkOnboardingSetupAsUser()
        assertUsePlanReadyWithin30Seconds()
        shot("onboarding-plan-preview")
        scrollPreviewToUsePlan()
        compose.onNodeWithTag(OnboardingTags.USE_PLAN, useUnmergedTree = true).performClick()
        if (compose.onAllNodesWithText(OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN).performClick()
        }
        harnessWaitUntil(120_000) {
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
            harnessWaitUntil(30_000) {
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
        harnessWaitUntil(30_000) {
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
        harnessWaitUntil(15_000) {
            compose.onAllNodesWithTag(HomeStartTags.CARDIO_PAGE).fetchSemanticsNodes().isNotEmpty()
        }
        shot("cardio-picker")
        val runCard = hasTestTag(CardioPickTags.card(CardioType.RUN))
        compose.onNodeWithTag(HomeStartTags.CARDIO_PAGE).performScrollToNode(runCard)
        compose.onNode(runCard).performClick()
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag(CardioTags.ELAPSED).fetchSemanticsNodes().isNotEmpty()
        }
        shot("cardio-live")
        SystemClock.sleep(3_000)
        dismissSoftKeyboard()
        harnessWaitUntil(45_000) {
            compose.onAllNodes(hasTestTag(CardioTags.FINISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(CardioTags.FINISH).performClick()
        harnessWaitUntil(120_000) { cardioSessionFinished() }
        if (compose.onAllNodesWithText(CardioCopy.FINISHED_TITLE).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Back").performClick()
        }
        harnessWaitUntil(90_000) {
            compose.onAllNodesWithTag(ActivityDetailTags.CONTENT).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(ActivityDetailTags.DONE).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(HomeTags.START).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag(ActivityDetailTags.DONE).fetchSemanticsNodes().isNotEmpty()) {
            logHarness("UX_WALKTHROUGH_POST_CARDIO_ROUTE=activity-summary")
            compose.onNodeWithTag(ActivityDetailTags.DONE).performClick()
        } else {
            logHarness("UX_WALKTHROUGH_POST_CARDIO_ROUTE=finished-or-home")
        }
        if (compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag(HomeTags.START).fetchSemanticsNodes().isEmpty()
        ) {
            goToTab("home")
        }
        awaitHomeReady()
        shot("cardio-finished")
    }

    private fun flowPastMixedSave() {
        preparePopulatedApp()
        goToTab("history")
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(HistoryTags.START_SHEET).performScrollTo().performClick()
        clickLabel("Mixed session")
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag("composer-title") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        shot("mixed-composer-open")
        composerScrollToTag(ComposerTags.CHOOSE_LIFT)
        tapComposerTag(ComposerTags.CHOOSE_LIFT)
        awaitComposerPicker()
        composerPickerSearch().performTextReplacement("squat")
        composerPickerSearch().performImeAction()
        clickComposerPickerResult("Barbell Back Squat")
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isEmpty()
        }
        harnessWaitUntil(90_000) {
            if (compose.onAllNodesWithTag(ComposerTags.ADD_SET).fetchSemanticsNodes().isEmpty()) {
                runCatching { composerScrollToTag(ComposerTags.ADD_SET) }
            }
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 &&
                compose.onAllNodesWithTag(ComposerTags.ADD_SET).fetchSemanticsNodes().isNotEmpty()
        }
        composerScrollToTag(ComposerTags.ADD_SET)
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("60")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("5")
        dismissSoftKeyboard()
        compose.onNodeWithTag(ComposerTags.ADD_SET).performClick()
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag(ComposerTags.SAVE).fetchSemanticsNodes().isNotEmpty()
        }
        shot("mixed-composer-filled")
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag(ComposerTags.SAVE)).fetchSemanticsNodes().isNotEmpty()
        }
        dismissSoftKeyboard()
        performHarnessClickOnTag(ComposerTags.SAVE)
        waitForComposerClosedOrHistoryShown()
        val autoHistory = runCatching {
            harnessWaitUntil(15_000) {
                compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (!autoHistory) {
            harnessWaitUntil(90_000) {
                compose.onAllNodesWithTag(ActivityDetailTags.CONTENT).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag(ActivityDetailTags.DONE).fetchSemanticsNodes().isNotEmpty()
            }
            logHarness("UX_WALKTHROUGH_POST_SAVE_ROUTE=activity-summary")
            if (compose.onAllNodesWithTag(ActivityDetailTags.DONE).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag(ActivityDetailTags.DONE).performClick()
            } else {
                goToTab("home")
            }
            goToTab("history")
        } else {
            logHarness("UX_WALKTHROUGH_POST_SAVE_ROUTE=history-list")
        }
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "History screen (${HistoryTags.LIST}) should be visible after Save",
            compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty(),
        )
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag("history-horizon-readout").fetchSemanticsNodes().isNotEmpty()
        }
        shot("mixed-saved")
    }

    private fun flowBodyMapAndMeasurements() {
        preparePopulatedApp()
        goToTab("progress")
        harnessWaitUntil(30_000) {
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
        harnessWaitUntil(15_000) {
            compose.onAllNodesWithTag(SettingsTags.DISPLAY).fetchSemanticsNodes().isNotEmpty()
        }
        shot("settings-units")
        pressBackToSettingsHome()
        compose.onNodeWithTag(SettingsTags.ROW_BACKUP).performScrollTo().performClick()
        harnessWaitUntil(15_000) {
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
        harnessWaitUntil(60_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.SET_ENTRY)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("strength-live-font-1-3")
        dismissSoftKeyboard()
        // Leave through the UI (W-14); repository discard alone leaves the workout screen up on CI.
        navigateToHome()
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
            awaitCatalogReady()
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
        harnessWaitUntil(15_000) {
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
        harnessWaitUntil(30_000) {
            val next = compose.onAllNodes(hasTestTag(WorkoutTestTags.NEXT) and isEnabled()).fetchSemanticsNodes()
            val finish = compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled()).fetchSemanticsNodes()
            next.isNotEmpty() || finish.isNotEmpty()
        }
        if (compose.onAllNodes(hasTestTag(WorkoutTestTags.NEXT) and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(WorkoutTestTags.NEXT).performClick()
            harnessWaitUntil(30_000) {
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
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun finishLiveWorkout() {
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WorkoutTestTags.FINISH).performClick()
        compose.onNodeWithText("End workout?").assertIsDisplayed()
        compose.onNodeWithText("Save as is").performClick()
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithText("WORKOUT COMPLETE").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openGuidedSetup() {
        goToTab("settings")
        clickLabel(SettingsHomeCopy.PLAN)
        clickLabel(PlanSetupCopy.ROW_TITLE)
        harnessWaitUntil(120_000) {
            compose.onAllNodesWithText("What do you want to record?").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun composerPickerSearch() =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")), useUnmergedTree = true)

    private fun awaitComposerPicker() {
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("picker-search")) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickComposerPickerResult(label: String) {
        val target = hasText(label) and hasAnyAncestor(hasTestTag("picker-content"))
        harnessWaitUntil(30_000) {
            runCatching {
                compose.onNodeWithTag("picker-content").performScrollToNode(target)
            }.isSuccess
        }
        compose.onNode(target).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun composerScrollToTag(tag: String) {
        if (runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess) return
        harnessWaitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasTestTag(tag))
            }.isSuccess
        }
    }

    private fun tapComposerTag(tag: String) {
        harnessWaitUntil(30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun openSettingsRow(title: String) {
        dismissSystemNotRespondingDialogs()
        navigateToSettingsHome()
        scrollToText(title)
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun goToTab(path: String) {
        dismissSystemNotRespondingDialogs()
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
        harnessWaitUntil(120_000) {
            compose.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(tab).performClick()
        compose.waitForIdle()
        dismissSystemNotRespondingDialogs()
    }

    private fun awaitHomeOrOnboardingEntry() {
        harnessWaitUntil(120_000) {
            compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(HomeTags.START).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("What do you want to record?").fetchSemanticsNodes().isNotEmpty()
        }
        dismissTransientOverlays()
    }

    private fun awaitHomeReady() {
        harnessWaitUntil(120_000) {
            compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(HomeTags.START).fetchSemanticsNodes().isNotEmpty()
        }
        dismissTransientOverlays()
    }

    private fun dismissTransientOverlays() {
        dismissBlockingUi()
    }

    private fun dismissBlockingUi() {
        dismissSystemNotRespondingDialogs()
        repeat(5) {
            compose.waitForIdle()
            dismissSystemNotRespondingDialogs()
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
        harnessWaitUntil(60_000) {
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
        harnessWaitUntil(60_000) {
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
        harnessWaitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasText(text))
            }.isSuccess || compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToTag(tag: String) {
        if (tag == OnboardingTags.USE_PLAN) {
            scrollPreviewToUsePlan()
            return
        }
        if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
            return
        }
        harnessWaitUntil(30_000) {
            runCatching {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasTestTag(tag))
            }.isSuccess ||
                compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
        runCatching { compose.onNodeWithTag(tag).performScrollTo() }
    }

    private fun usePlanPresentInTree(): Boolean =
        compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN), useUnmergedTree = true).fetchSemanticsNodes()
            .isNotEmpty() ||
            compose.onAllNodes(hasTestTag(OnboardingTags.USE_PLAN)).fetchSemanticsNodes().isNotEmpty()

    /** Scroll onboarding preview until [OnboardingTags.USE_PLAN] is composed (below the week list). */
    private fun scrollPreviewToUsePlan() {
        dismissSystemNotRespondingDialogs()
        if (usePlanPresentInTree()) return
        val usePlanMatcher = hasTestTag(OnboardingTags.USE_PLAN)
        repeat(16) {
            dismissSystemNotRespondingDialogs()
            val scrolled =
                runCatching {
                    compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(usePlanMatcher)
                }.isSuccess ||
                    runCatching {
                        compose.onNode(
                            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                            useUnmergedTree = true,
                        ).performScrollToNode(usePlanMatcher)
                    }.isSuccess ||
                    runCatching {
                        compose.onNode(
                            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                        ).performScrollToNode(usePlanMatcher)
                    }.isSuccess
            if (usePlanPresentInTree()) return
            if (!scrolled) {
                runCatching {
                    compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
                }
            }
            compose.waitForIdle()
        }
    }

    /** System ANR dialogs (e.g. Pixel Launcher) sit outside Compose; dismiss via accessibility. */
    private fun dismissSystemNotRespondingDialogs() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(2) {
            val root = uiAutomation.rootInActiveWindow ?: return@repeat
            if (!accessibilityTreeContainsNotResponding(root)) return@repeat
            val waitNode = findAccessibilityNodeWithExactText(root, "Wait")
            waitNode?.let { node ->
                val target =
                    when {
                        node.isClickable -> node
                        node.parent?.isClickable == true -> node.parent
                        else -> node
                    }
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(300)
            }
        }
    }

    private fun accessibilityTreeContainsNotResponding(node: AccessibilityNodeInfo): Boolean {
        val blob =
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
        if (blob.contains("isn't responding", ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (accessibilityTreeContainsNotResponding(child)) return true
            }
        }
        return false
    }

    private fun findAccessibilityNodeWithExactText(
        node: AccessibilityNodeInfo,
        text: String,
    ): AccessibilityNodeInfo? {
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findAccessibilityNodeWithExactText(child, text)?.let { return it }
            }
        }
        return null
    }

    private fun awaitPicker() {
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag("picker-search")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBackToSettingsHome() {
        navigateToSettingsHome()
    }

    private fun settingsHomeVisible(): Boolean {
        runCatching { scrollToTag(SettingsTags.HOME) }
        return compose.onAllNodesWithTag(SettingsTags.HOME).fetchSemanticsNodes().isNotEmpty()
    }

    /** Subpages keep [SettingsTags.HOME] out of the tree until the header Back or system Back runs. */
    private fun navigateToSettingsHome() {
        dismissSoftKeyboard()
        dismissSystemNotRespondingDialogs()
        if (settingsHomeVisible()) return
        repeat(8) {
            dismissSystemNotRespondingDialogs()
            if (settingsHomeVisible()) return
            val backNodes = compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes()
            if (backNodes.isNotEmpty()) {
                runCatching { compose.onAllNodes(hasContentDescription("Back")).onFirst().performClick() }
                compose.waitForIdle()
                if (settingsHomeVisible()) return
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            if (settingsHomeVisible()) return
        }
        goToTab("settings")
        runCatching { scrollToTag(SettingsTags.HOME) }
        harnessWaitUntil(120_000) { settingsHomeVisible() }
    }

    private fun performHarnessClickOnTag(tag: String, enabledTimeoutMs: Long = 90_000) {
        dismissSoftKeyboard()
        dismissSystemNotRespondingDialogs()
        if (tag.startsWith("activity-composer") || tag == ComposerTags.SAVE) {
            composerScrollToTag(tag)
        } else {
            scrollToTag(tag)
        }
        harnessWaitUntil(30_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
        val enabledReady = runCatching {
            harnessWaitUntil(enabledTimeoutMs) {
                dismissSoftKeyboard()
                compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        if (!enabledReady) {
            logHarness("UX_WALKTHROUGH_HARNESS_CLICK tag=$tag enabled-wait-fallback")
        }
        safeClickOnTag(tag)
    }

    private fun safeClickOnTag(tag: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { node.performScrollTo() }
        val clicked =
            runCatching { node.performClick() }.isSuccess ||
                runCatching { node.performTouchInput { click(center) } }.isSuccess
        check(clicked) { "Could not click tag=$tag" }
        compose.waitForIdle()
    }

    private fun waitForComposerClosedOrHistoryShown(timeoutMs: Long = 120_000) {
        dismissSoftKeyboard()
        dismissTransientOverlays()
        harnessWaitUntil(timeoutMs) {
            dismissSoftKeyboard()
            if (compose.onAllNodesWithTag("composer-title").fetchSemanticsNodes().isEmpty()) {
                return@harnessWaitUntil true
            }
            if (compose.onAllNodesWithTag(HistoryTags.LIST).fetchSemanticsNodes().isNotEmpty()) {
                return@harnessWaitUntil true
            }
            if (compose.onAllNodesWithTag(ActivityDetailTags.DONE).fetchSemanticsNodes().isNotEmpty()) {
                return@harnessWaitUntil true
            }
            if (compose.onAllNodesWithTag(ActivityDetailTags.CONTENT).fetchSemanticsNodes().isNotEmpty()) {
                return@harnessWaitUntil true
            }
            retryComposerSaveIfStuck()
            false
        }
    }

    private fun retryComposerSaveIfStuck() {
        if (compose.onAllNodes(hasTestTag(ComposerTags.SAVE) and isEnabled()).fetchSemanticsNodes().isEmpty()) {
            return
        }
        logHarness("UX_WALKTHROUGH_COMPOSER_SAVE_RETRY")
        runCatching { performHarnessClickOnTag(ComposerTags.SAVE) }
    }

    private fun pressBackToHome() {
        navigateToHome()
    }

    private fun homeScreenVisible(): Boolean =
        compose.onAllNodesWithTag("navigation-home").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag(HomeTags.START).fetchSemanticsNodes().isNotEmpty()

    private fun navigateToHome() {
        dismissSoftKeyboard()
        dismissSystemNotRespondingDialogs()
        if (homeScreenVisible()) return
        runCatching { goToTab("home") }
        if (homeScreenVisible()) return
        repeat(8) {
            dismissSystemNotRespondingDialogs()
            dismissWorkoutLeaveDialogsIfVisible()
            if (homeScreenVisible()) return
            if (compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty()) {
                runCatching { compose.onAllNodes(hasContentDescription("Back")).onFirst().performClick() }
                compose.waitForIdle()
                if (homeScreenVisible()) return
            }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            dismissWorkoutLeaveDialogsIfVisible()
            if (homeScreenVisible()) return
            runCatching { goToTab("home") }
            if (homeScreenVisible()) return
        }
        harnessWaitUntil(120_000) { homeScreenVisible() }
    }

    private fun dismissWorkoutLeaveDialogsIfVisible() {
        if (compose.onAllNodesWithText(EndWorkoutCopy.TITLE).fetchSemanticsNodes().isEmpty()) return
        runCatching { compose.onNodeWithText(EndWorkoutCopy.DISCARD).performClick() }
        compose.waitForIdle()
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
