package com.sinura.personaltrainer.ui.routines

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blueprint 4.5 (A31): Set targets, one check per state. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h840dp-xhdpi")
class TargetsSheetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val saved = mutableListOf<TargetsValues>()

    private fun show(hold: Boolean = false, saving: Boolean = false, error: String? = null) {
        compose.setContent {
            PersonalTrainerTheme {
                TargetsSheetContent(
                    liftName = if (hold) "Plank" else "Bench press",
                    initial = if (hold) TargetsValues(3, 30, 60) else TargetsValues(3, 8, 120),
                    hold = hold, saving = saving, error = error, onSave = { saved += it },
                )
            }
        }
    }

    @Test fun firstTimePrefillsDefaultsAndSaves() {
        show()
        compose.onNodeWithTag(TargetsSheetTags.LIFT).assertTextEquals("Bench press")
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.REPS)).assertTextEquals("8")
        compose.onNodeWithText("Start weight comes from Tempo after the first session.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sets up").performClick()
        compose.onNodeWithTag(TargetsSheetTags.SAVE).performScrollTo().performClick()
        assertEquals(listOf(TargetsValues(4, 8, 120)), saved)
    }

    @Test fun presetChipSetsBothValues() {
        show()
        compose.onNodeWithTag(TargetsSheetTags.preset(3, 5)).performClick()
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.REPS)).assertTextEquals("5")
    }

    @Test fun holdLiftCountsSeconds() {
        show(hold = true)
        compose.onNodeWithText("Seconds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seconds up").performClick()
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.REPS)).assertTextEquals("35")
    }

    @Test fun loadingDisablesSave() {
        show(saving = true)
        compose.onNodeWithTag(TargetsSheetTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithText(RoutineSaveCopy.SAVING).assertIsDisplayed()
    }

    @Test fun errorShowsUnderTheSheet() {
        show(error = RoutineSaveCopy.TARGETS_REJECTED)
        compose.onNodeWithTag(TargetsSheetTags.ERROR).assertIsDisplayed()
    }

    @Test fun repsNeverGoBelowOne() {
        show()
        repeat(10) { compose.onNodeWithContentDescription("Reps down").performClick() }
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.REPS)).assertTextEquals("1")
        compose.onNodeWithContentDescription("Reps down").assertIsNotEnabled()
    }

    @Test fun recreationKeepsTheEdits() {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            PersonalTrainerTheme {
                TargetsSheetContent("Bench press", TargetsValues(3, 8, 120), hold = false, saving = false,
                    error = null, onSave = { saved += it })
            }
        }
        compose.onNodeWithContentDescription("Rest up").performClick()
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(TargetsSheetTags.value(TargetsSheetTags.REST)).assertTextEquals("2:15")
    }
}
