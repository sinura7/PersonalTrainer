package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.SettingsHomeCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp", fontScale = 2f)
class SettingsUnitsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel.clearAndJoinForTest() }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun displayRestAndBackupStayReachableAndDistanceCanDifferFromWeight() {
        compose.setContent {
            PersonalTrainerTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.HOME).performScrollToNode(hasTestTag(SettingsTags.ROW_REST))
        compose.onNodeWithTag(SettingsTags.ROW_REST).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HOME).performScrollToNode(hasTestTag(SettingsTags.ROW_BACKUP))
        compose.onNodeWithTag(SettingsTags.ROW_BACKUP).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.HOME).performScrollToNode(hasTestTag(SettingsTags.ROW_DISPLAY))
        compose.onNodeWithTag(SettingsTags.ROW_DISPLAY).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Kilometres").assertIsDisplayed()
        compose.onNodeWithText("Miles").assertIsDisplayed()
        compose.onNodeWithText(DistanceUnit.CHOICE_CAPTION, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Miles").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Miles").assertIsDisplayed()
    }

    @Test
    fun aFailedSaveStaysVisibleWhenThePageIsOpenedAgain() {
        runBlocking { viewModel.runSave { error("disk") } }
        viewModel.rememberPage(SettingsPage.REST)
        compose.setContent {
            PersonalTrainerTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(SETTINGS_SAVE_FAILED).assertIsDisplayed()
        compose.onNodeWithText(SettingsHomeCopy.REST).assertIsDisplayed()
        runBlocking {
            viewModel.runSave { }
            assertNull(viewModel.settingsError.value)
        }
    }

    @Test
    fun theDebugUpdateRowIsASeparateScreenFromTheEverydayList() {
        compose.setContent {
            PersonalTrainerTheme {
                SettingsHome(
                    displaySummary = "lb · mi · Regular",
                    remindersSummary = SettingsHomeCopy.OFF,
                    generatorSummary = "3 days",
                    restSummary = "1:30",
                    permissionsSummary = "Ready",
                    bodyweightSummary = "Not set",
                    savePostureSummary = "On this phone",
                    accountSummary = SettingsHomeCopy.ACCOUNT_SUMMARY,
                    onOpen = {},
                    updateSummary = null,
                )
            }
        }
        compose.onNodeWithText(SettingsHomeCopy.UPDATE).assertDoesNotExist()
        compose.onNodeWithText(SettingsHomeCopy.DISPLAY).assertIsDisplayed()
    }

    @Test
    fun theDebugUpdateRowIsShownOnlyOnTheDebugList() {
        compose.setContent {
            PersonalTrainerTheme {
                SettingsHome(
                    displaySummary = "lb · mi · Regular",
                    remindersSummary = SettingsHomeCopy.OFF,
                    generatorSummary = "3 days",
                    restSummary = "1:30",
                    permissionsSummary = "Ready",
                    bodyweightSummary = "Not set",
                    savePostureSummary = "On this phone",
                    accountSummary = SettingsHomeCopy.ACCOUNT_SUMMARY,
                    onOpen = {},
                    updateSummary = "Ready to install",
                )
            }
        }
        compose.onNodeWithText(SettingsHomeCopy.UPDATE).assertIsDisplayed()
        compose.onNodeWithText("Ready to install").assertIsDisplayed()
    }
}
