package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.OnboardingPreviewCopy
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
class OnboardingAcceptanceRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: OnboardingViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun anExistingProgramExplainsTheImpactBeforeUseThisPlan() {
        runBlocking {
            deps.dbMaintenance.seedCatalog()
            deps.routineRepository.create(name = "Upper")
        }
        val model = OnboardingViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(),
            deps,
        )
        viewModel = model
        runBlocking {
            model.setFocus(TrainingFocus.STRENGTH)
            model.setExperience(TrainingAge.RETURNING)
            repeat(6) { model.next() }
            withTimeout(TestWaits.FLOW_MS) {
                model.uiState.first { it.step == OnboardingStep.PREVIEW && it.existingProgram && it.preview != null }
            }
        }
        var builtOwn = false
        compose.setContent {
            PersonalTrainerTheme {
                OnboardingScreen(
                    onFinished = {},
                    onBuildMyOwn = { _, _ -> builtOwn = true },
                    viewModel = model,
                )
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Here's your block").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo(OnboardingPreviewCopy.EXISTING_PROGRAM)
        compose.onNodeWithText(OnboardingPreviewCopy.EXISTING_PROGRAM).assertIsDisplayed()
        scrollTo("Use this plan")
        compose.onNodeWithText("Use this plan").assertIsDisplayed()
        val explanationTop = compose.onNodeWithText(OnboardingPreviewCopy.EXISTING_PROGRAM)
            .fetchSemanticsNode().boundsInRoot.top
        val buttonTop = compose.onNodeWithText("Use this plan").fetchSemanticsNode().boundsInRoot.top
        assertTrue(explanationTop < buttonTop)
        val before = runBlocking { deps.routineRepository.count() }
        scrollTo("I'll build my own")
        compose.onNodeWithText("I'll build my own").performClick()
        compose.waitForIdle()
        assertTrue(builtOwn)
        assertEquals(before, runBlocking { deps.routineRepository.count() })
        assertEquals(0, runBlocking { deps.scheduleRepository.slots().size })
    }

    @Test
    fun aFirstPlanDoesNotShowTheExistingProgramWarning() {
        runBlocking { deps.dbMaintenance.seedCatalog() }
        val model = OnboardingViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(),
            deps,
        )
        viewModel = model
        runBlocking {
            model.setFocus(TrainingFocus.STRENGTH)
            model.setExperience(TrainingAge.NEW)
            repeat(6) { model.next() }
            withTimeout(TestWaits.FLOW_MS) {
                model.uiState.first { it.step == OnboardingStep.PREVIEW && !it.existingProgram && it.preview != null }
            }
        }
        compose.setContent {
            PersonalTrainerTheme {
                OnboardingScreen(onFinished = {}, onBuildMyOwn = { _, _ -> }, viewModel = model)
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Here's your block").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("Use this plan")
        compose.onNodeWithText("Use this plan").assertIsDisplayed()
        compose.onNodeWithText(OnboardingPreviewCopy.EXISTING_PROGRAM).assertDoesNotExist()
        assertFalse(runBlocking { deps.preferencesRepository.onboardingComplete.first() })
    }

    private fun scrollTo(text: String) {
        compose.onNode(matcher = hasScrollAction()).performScrollToNode(matcher = hasText(text))
    }
}
