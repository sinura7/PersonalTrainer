package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
class OnboardingReachabilityTest {
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
    fun continueAndSkipStayReachableAtASmallWindowAndLargeText() {
        val model = OnboardingViewModel(
            ApplicationProvider.getApplicationContext(),
            SavedStateHandle(),
            deps,
        )
        viewModel = model
        compose.setContent {
            PersonalTrainerTheme {
                OnboardingScreen(onFinished = {}, onBuildMyOwn = { _, _ -> }, viewModel = model)
            }
        }
        compose.onNodeWithText("Strength").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New to lifting").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Space them out for me").performScrollTo().assertIsDisplayed()
        model.setBodyweight(80.0)
        model.next()
        model.next()
        model.next()
        model.next()
        compose.waitForIdle()
        compose.onNodeWithText("Skip this").performScrollTo().assertIsDisplayed()
    }
}
