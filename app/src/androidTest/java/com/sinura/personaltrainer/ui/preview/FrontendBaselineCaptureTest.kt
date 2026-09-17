package com.sinura.personaltrainer.ui.preview

import android.content.Intent
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.AppContainer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.testutil.NativeArtifacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Real MainActivity/NavHost, populated Room, all five tabs and a live session.
 * These captures document the native baseline, including system and app chrome.
 * They are observation artifacts (real civil clock), not deterministic goldens.
 * Pixel comparisons use the separately pinned GoldenCapture fixtures.
 */
@RunWith(AndroidJUnit4::class)
class FrontendBaselineCaptureTest {
    private lateinit var container: AppContainer
    private val seedRule = object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in setOf("ranchu", "goldfish") &&
                Build.FINGERPRINT.startsWith("Android/sdk_")) {
                "Frontend fixtures run only on an emulator, never the owner's phone."
            }
            container = ApplicationProvider.getApplicationContext<PersonalTrainerApp>().container
            runBlocking { withTimeout(60_000) { withContext(Dispatchers.IO) { seed() } } }
        }

        override fun after() {
            if (::container.isInitialized) {
                runBlocking { withTimeout(30_000) { withContext(Dispatchers.IO) { clean() } } }
            }
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
    fun populatedTabsKeepLiveSessionAndCaptureCompleteChrome() {
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (label in listOf("Home", "Body", "Plan", "History", "Settings")) {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag(LiveSessionBarTestTags.ROOT)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // Startup preferences load asynchronously. Dismiss any first-open
            // explanation through its actual UI before documenting the page.
            repeat(3) {
                compose.waitForIdle()
                if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithText("Not now").performClick()
                }
            }
            compose.onNode(hasText(label.uppercase()) and tabRole).performClick()
            val loaded = when (label) {
                "Home" -> hasText("TRAINED TODAY")
                "Body" -> hasTestTag("body-map")
                "Plan" -> hasTestTag("plan-add-session")
                "History" -> hasTestTag("history-horizon-readout")
                else -> hasText("Display")
            }
            compose.waitUntil(30_000) {
                compose.onAllNodes(loaded).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(loaded).assertIsDisplayed()
            compose.waitForIdle()
            compose.onNodeWithText("Not now").assertDoesNotExist()
            compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).assertIsDisplayed()
            capture(label.lowercase())
        }
    }

    private suspend fun seed() {
        println("FRONTEND_FIXTURE cleaning")
        clean()
        println("FRONTEND_FIXTURE preferences")
        container.preferencesRepository.setOnboardingComplete(true)
        container.preferencesRepository.setLaunchPermissionsAsked(true)
        container.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        println("FRONTEND_FIXTURE awaiting catalog")
        val catalog = container.exerciseRepository.observeAll().first { items ->
            items.any { it.id == "ex-barbell-back-squat" }
        }
        val squat = catalog.first { it.id == "ex-barbell-back-squat" }
        val row = catalog.first { it.id == "ex-barbell-row" }
        println("FRONTEND_FIXTURE creating sessions")
        for ((index, name) in listOf("Upper A", "Lower A").withIndex()) {
            val routine = container.routineRepository.create("$PREFIX$name")
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
            if (index == 0) container.workoutRepository.finishSession(session.id, "")
        }
    }

    private suspend fun clean() {
        container.restTimerController.stop()
        container.workoutRepository.getInProgress()?.let { container.discardWorkout(it.id) }
        container.activityRepository.getLive()?.let { container.discardActivity(it.id) }
        container.workoutRepository.observeHistory().first()
            .filter { it.routineName?.startsWith(PREFIX) == true }
            .forEach { container.workoutRepository.deleteFinishedSession(it.id) }
        container.routineRepository.observeAll().first()
            .filter { it.name.startsWith(PREFIX) }
            .forEach { container.routineRepository.delete(it.id) }
    }

    private fun capture(page: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = "frontend-baseline-$page-api${Build.VERSION.SDK_INT}"
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val path = NativeArtifacts.write(name, bitmap)
            println("FRONTEND_CAPTURE $path ${bitmap.width}x${bitmap.height}")
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val PREFIX = "Frontend baseline "
    }
}
