package com.sinura.personaltrainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.ui.activity.ActivityDetailTags
import com.sinura.personaltrainer.ui.activity.CardioTags
import com.sinura.personaltrainer.ui.activity.ComposerTags
import com.sinura.personaltrainer.ui.activity.ElapsedReadout
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailHeader
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailTags
import com.sinura.personaltrainer.ui.history.SessionDetailTestTags
import com.sinura.personaltrainer.ui.history.sessionDeleteTitle
import com.sinura.personaltrainer.ui.onboarding.OnboardingTags
import com.sinura.personaltrainer.ui.routines.CustomWeekTags
import com.sinura.personaltrainer.ui.routines.RoutineEditorHeader
import com.sinura.personaltrainer.ui.routines.RoutineEditorTags
import com.sinura.personaltrainer.ui.settings.SettingsTags
import com.sinura.personaltrainer.ui.summary.SummaryActions
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 remaining pages: Volt acts and named exits stay reachable at
 * 360 dp through font 2.0. Physical TalkBack stays a P9.7 gate.
 */
@RunWith(AndroidJUnit4::class)
class RemainingPagesPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun routineEditorBackAndAddStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            RoutineEditorHeader(onBack = {})
            SecondaryGymButton(
                text = "Add lifts",
                onClick = {},
                modifier = Modifier.testTag(RoutineEditorTags.ADD_LIFTS),
            )
        }
        compose.onNodeWithTag(RoutineEditorTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag(RoutineEditorTags.ADD_LIFTS).assertIsDisplayed()
        compose.onNodeWithText("Add lifts").assertIsDisplayed()
    }

    @Test
    fun customWeekConfirmStaysNamedAt360Font2() {
        val cta = CustomWeekPolicy.confirmCta(3)
        setConstrainedContent(2f) {
            PrimaryGymButton(
                text = cta,
                onClick = {},
                modifier = Modifier.testTag(CustomWeekTags.CONFIRM),
            )
        }
        compose.onNodeWithTag(CustomWeekTags.CONFIRM).assertIsDisplayed()
        compose.onNodeWithText(cta).assertIsDisplayed()
    }

    @Test
    fun summaryDoneAndSessionStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            SummaryActions(onDone = {}, onOpenSession = {})
        }
        compose.onNodeWithTag(SummaryTags.DONE).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.OPEN_SESSION).assertIsDisplayed()
        compose.onNodeWithText("See full session").assertIsDisplayed()
    }

    @Test
    fun sessionDetailNamesTheSessionAndKeepsBack() {
        setConstrainedContent(2f) {
            IconButton(
                onClick = {},
                modifier = Modifier.testTag(SessionDetailTestTags.BACK),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            IconButton(
                onClick = {},
                modifier = Modifier.testTag(SessionDetailTestTags.OPTIONS),
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Session options")
            }
            Text(sessionDeleteTitle("Upper strength"))
        }
        compose.onNodeWithTag(SessionDetailTestTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag(SessionDetailTestTags.OPTIONS).assertIsDisplayed()
        compose.onNodeWithContentDescription("Session options").assertIsDisplayed()
        compose.onNodeWithText("Delete Upper strength?").assertIsDisplayed()
    }

    @Test
    fun exerciseDetailBackAndAddStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            ExerciseDetailHeader(name = "Bench press", exercise = null, onBack = {})
            SecondaryGymButton(
                text = "Add to a routine",
                onClick = {},
                modifier = Modifier.testTag(ExerciseDetailTags.ADD_TO_ROUTINE),
            )
        }
        compose.onNodeWithTag(ExerciseDetailTags.BACK).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Bench press").assertIsDisplayed()
        compose.onNodeWithTag(ExerciseDetailTags.ADD_TO_ROUTINE).assertIsDisplayed()
    }

    @Test
    fun liveCardioElapsedIsAReadoutNotALiveStream() {
        setConstrainedContent(2f) {
            ElapsedReadout(elapsedSeconds = 462)
            PrimaryGymButton(
                text = "Finish",
                onClick = {},
                modifier = Modifier.testTag(CardioTags.FINISH),
            )
        }
        compose.onNodeWithTag(CardioTags.ELAPSED).assertIsDisplayed()
        compose.onNodeWithTag(CardioTags.ELAPSED)
            .assertContentDescriptionEquals("Elapsed 7:42")
        compose.onNodeWithTag(CardioTags.FINISH).assertIsDisplayed()
        compose.onNodeWithText("Finish").assertIsDisplayed()
    }

    @Test
    fun activityDetailAndComposerStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            TextButton(
                onClick = {},
                modifier = Modifier.testTag(ActivityDetailTags.DONE),
            ) { Text("Done") }
            PrimaryGymButton(
                text = "Save",
                onClick = {},
                modifier = Modifier.testTag(ComposerTags.SAVE),
            )
            TextButton(
                onClick = {},
                modifier = Modifier.testTag(ComposerTags.CANCEL),
            ) { Text("Cancel") }
        }
        compose.onNodeWithTag(ActivityDetailTags.DONE).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithTag(ComposerTags.SAVE).assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithTag(ComposerTags.CANCEL).assertIsDisplayed()
    }

    @Test
    fun onboardingAndSettingsStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            PrimaryGymButton(
                text = "Use this plan",
                onClick = {},
                modifier = Modifier.testTag(OnboardingTags.USE_PLAN),
            )
            SecondaryGymButton(
                text = "Export to file",
                onClick = {},
                modifier = Modifier.testTag(SettingsTags.EXPORT_FILE),
            )
            SecondaryGymButton(
                text = "Share diagnostics",
                onClick = {},
                modifier = Modifier.testTag(SettingsTags.SHARE_DIAGNOSTICS),
            )
        }
        compose.onNodeWithTag(OnboardingTags.USE_PLAN).assertIsDisplayed()
        compose.onNodeWithText("Use this plan").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.EXPORT_FILE).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.SHARE_DIAGNOSTICS).assertIsDisplayed()
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            Column { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
