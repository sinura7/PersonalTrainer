package com.sinura.personaltrainer.ui.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.testutil.TestWaits
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OnboardingFocusTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: OnboardingViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun guidedPathAsksFocusBeforeExperienceAndWritesNothingUntilApply() = runBlocking {
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val focused = withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.step == OnboardingStep.FOCUS } }
        assertEquals(OnboardingStep.FOCUS, focused.step)
        viewModel!!.setFocus(TrainingFocus.CARDIO)
        val next = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first {
                it.step == OnboardingStep.DAYS_PER_WEEK && it.answers.focus == TrainingFocus.CARDIO
            }
        }
        assertEquals(TrainingFocus.CARDIO, next.answers.focus)
        assertEquals(OnboardingStep.questionsFor(TrainingFocus.CARDIO).size, next.questionCount)
        assertTrue(deps.activityRepository.all().isEmpty())
        assertEquals(false, deps.preferencesRepository.onboardingComplete.first())
    }

    @Test
    fun cardioFocusPreviewDoesNotInventLifts() = runBlocking {
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.setFocus(TrainingFocus.CARDIO)
        val preview = withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.preview != null }.preview!! }
        assertTrue(preview.routines.isEmpty())
        assertEquals(0, preview.trainingDayCount)
    }

    @Test
    fun draftRoundTripCarriesFocus() {
        val original = OnboardingAnswers(focus = TrainingFocus.BOTH)
        val restored = OnboardingAnswers.decodeDraft(OnboardingAnswers.encodeDraft(original))!!
        assertEquals(TrainingFocus.BOTH, restored.focus)
        val old = OnboardingAnswers.encodeDraft(OnboardingAnswers()).substringBeforeLast("|")
        assertEquals(TrainingFocus.STRENGTH, OnboardingAnswers.decodeDraft(old)!!.focus)
    }

    @Test
    fun cardioPathSkipsLiftOnlyQuestions() = runBlocking {
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.step == OnboardingStep.FOCUS } }
        viewModel!!.setFocus(TrainingFocus.CARDIO)
        val days = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { it.step == OnboardingStep.DAYS_PER_WEEK }
        }
        assertEquals(OnboardingStep.DAYS_PER_WEEK, days.step)
        val liftOnly = setOf(OnboardingStep.EXPERIENCE, OnboardingStep.GOAL, OnboardingStep.EMPHASIS)
        assertTrue(OnboardingStep.questionsFor(TrainingFocus.CARDIO).none { it in liftOnly })
        assertEquals(
            OnboardingStep.entries.count { it.isQuestion },
            OnboardingStep.questionsFor(TrainingFocus.STRENGTH).size,
        )
        viewModel!!.back()
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.step == OnboardingStep.FOCUS } }
        assertEquals(OnboardingStep.FOCUS, viewModel!!.uiState.value.step)
    }

    @Test
    fun cardioPreviewDoesNotRaiseACatalogError() = runBlocking {
        viewModel = OnboardingViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.step == OnboardingStep.FOCUS } }
        viewModel!!.setFocus(TrainingFocus.CARDIO)
        repeat(4) { viewModel!!.next() }
        val preview = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { it.step == OnboardingStep.PREVIEW }
        }
        assertEquals(OnboardingStep.PREVIEW, preview.step)
        assertTrue(preview.preview != null)
        assertTrue(preview.error == null)
    }
}
