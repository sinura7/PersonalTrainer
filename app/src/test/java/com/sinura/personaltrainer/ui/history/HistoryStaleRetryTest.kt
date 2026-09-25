package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.testutil.ActivityReadGate
import com.sinura.personaltrainer.testutil.FailingObserveCompletedSummariesDao
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.seedTestWorkout
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

/**
 * A History that could read the workouts but not everything beside them says it may be
 * behind. That line had no action, so the only way to read again was to leave the tab; it
 * now carries Retry (audit UI-17).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class HistoryStaleRetryTest {
    @get:Rule val compose = createComposeRule()
    private val gate = ActivityReadGate(shouldFail = true)
    private lateinit var deps: FakeAppDependencies
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            activityDaoDecorator = { FailingObserveCompletedSummariesDao(it, gate) },
        )
        runBlocking {
            seedTestWorkout(deps, loggedSets = listOf(TestSetInput(100.0, 5)), finish = true)
        }
        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        compose.setContent {
            PersonalTrainerTheme {
                HistoryScreen(
                    onOpenSession = {},
                    onOpenExercise = {},
                    onOpenActiveSession = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking { viewModel.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aHistoryThatIsBehindOffersRetryAndRetryReadsAgain() {
        compose.waitUntil(TestWaits.FLOW_MS) { staleLineShown() }
        compose.onNodeWithTag(HistoryTestTags.STALE_RETRY).assertIsDisplayed()

        gate.shouldFail = false
        compose.onNodeWithTag(HistoryTestTags.STALE_RETRY).performClick()

        compose.waitUntil(TestWaits.FLOW_MS) { !staleLineShown() }
        compose.onNodeWithTag(HistoryTestTags.STALE_RETRY).assertDoesNotExist()
    }

    private fun staleLineShown(): Boolean =
        compose.onAllNodesWithText(HistoryCopy.STALE_LIST).fetchSemanticsNodes().isNotEmpty()
}
