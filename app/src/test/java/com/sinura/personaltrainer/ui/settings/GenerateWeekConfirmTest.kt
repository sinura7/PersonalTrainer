package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.PlanSetupCopy
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.ui.components.ConfirmActionTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Week generator → Generate a week wrote at the first tap: a fresh set of routines
 * added to the week and, before R1-4, a new block over the one running (audit UI-3). It now asks
 * first, and only the confirm writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class GenerateWeekConfirmTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var deps: FakeAppDependencies
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
        runBlocking { deps.dbMaintenance.seedCatalog() }
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        compose.setContent {
            PersonalTrainerTheme {
                val notice by viewModel.generateNotice.collectAsState()
                val confirm by viewModel.generateConfirm.collectAsState()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsGeneratorPane(
                        schedulePrefs = SchedulePreferences(),
                        preferredDays = emptySet(),
                        trainingAge = TrainingAge.NEW,
                        trainingPlace = null,
                        coachPrefs = CoachPreferences(),
                        generateNotice = notice,
                        generateConfirm = confirm,
                        viewModel = viewModel,
                    )
                }
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
    fun generateAWeekAsksFirstAndCancelWritesNothing() = runBlocking {
        compose.onNodeWithText("Generate a week").performScrollTo().performClick()
        compose.onNodeWithText(PlanSetupCopy.GENERATE_TITLE).assertIsDisplayed()
        compose.onNodeWithText(PlanSetupCopy.GENERATE_BODY).assertIsDisplayed()

        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText(PlanSetupCopy.GENERATE_TITLE).assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(emptyList<Any>(), deps.routineRepository.observeAll().first())
    }

    @Test
    fun confirmingGeneratesTheWeek() = runBlocking {
        compose.onNodeWithText("Generate a week").performScrollTo().performClick()
        compose.onNodeWithTag(ConfirmActionTags.CONFIRM).performClick()

        viewModel.generateNotice.awaitFirst { it != null }
        compose.onNodeWithText(PlanSetupCopy.GENERATE_TITLE).assertDoesNotExist()
        assertTrue(deps.routineRepository.observeAll().first().isNotEmpty())
    }
}
