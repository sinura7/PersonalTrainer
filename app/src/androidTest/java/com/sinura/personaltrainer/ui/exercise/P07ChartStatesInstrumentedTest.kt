package com.sinura.personaltrainer.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Native chart edge states and equivalent readouts; actual series construction is separately tested. */
class P07ChartStatesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val days = listOf(LocalDate.of(2026, 1, 5).toEpochDay(), LocalDate.of(2026, 1, 12).toEpochDay())

    @Test fun emptyEstimateAndTrainingWeekDoNotInventZeroMeasurements() {
        compose.setContent {
            PersonalTrainerTheme {
                Column(Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())) {
                    ExerciseTrendCard(title = "Estimated 1RM", values = emptyList(), epochDays = emptyList(),
                        pointNoun = "session", hint = "No estimable loaded result", measure = "kg", line = true,
                        grouped = false, tag = "empty-estimates")
                    ExerciseTrendCard(title = "Weekly reps", values = emptyList(), epochDays = emptyList(),
                        pointNoun = "week", hint = "No recorded work", measure = "reps", line = false,
                        grouped = true, tag = "empty-weeks")
                }
            }
        }
        compose.onNodeWithText("No recorded estimate").assertIsDisplayed()
        compose.onNodeWithText("No recorded training week").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("empty-estimates-values").assertDoesNotExist()
        compose.onNodeWithTag("empty-weeks-values").assertDoesNotExist()
        compose.onNodeWithText("0 kg").assertDoesNotExist()
        compose.onNodeWithText("0 reps").assertDoesNotExist()
        capture("empty-estimate-and-weeks")
    }

    @Test fun flatBodyweightSeriesUsesRepsAndRetainsEachRecordedWeek() {
        val tag = "flat-reps"
        compose.setContent {
            PersonalTrainerTheme {
                Column(Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())) {
                    ExerciseTrendCard(title = "Weekly reps", values = listOf(15.0, 15.0), epochDays = days,
                        pointNoun = "week", hint = "Recorded weeks", measure = "reps", line = false,
                        grouped = true, tag = tag)
                }
            }
        }
        compose.onNodeWithContentDescription("Weekly reps, 2 weeks, latest 15 reps, best 15 reps").assertIsDisplayed()
        compose.onNodeWithText("+0 reps").assertDoesNotExist()
        compose.onNodeWithText("0 kg").assertDoesNotExist()
        compose.onNodeWithTag("$tag-values").performScrollTo().performClick()
        assertWeeks(tag = tag, value = "15 reps")
        captureData(name = "flat-reps-data", tag = tag)
        compose.onNodeWithTag("$tag-close").performClick()
    }

    @Test fun flatLoadedEstimateUsesSelectedKgAndLbWithoutDoubleConversionOrZeroGain() {
        var unit by mutableStateOf(WeightUnit.KG)
        val tag = "flat-loaded"
        compose.setContent {
            PersonalTrainerTheme {
                Column(Modifier.fillMaxSize().background(Pit).verticalScroll(rememberScrollState())) {
                    val value = WeightConverter.toDisplayValue(100.0, unit)
                    ExerciseTrendCard(title = "Estimated 1RM", values = listOf(value, value), epochDays = days,
                        pointNoun = "session", hint = "Recorded estimates", measure = unit.suffix, line = true,
                        grouped = false, tag = tag)
                }
            }
        }
        for (displayUnit in listOf(WeightUnit.KG, WeightUnit.LBS)) {
            compose.runOnIdle { unit = displayUnit }
            val value = if (displayUnit == WeightUnit.KG) "100 kg" else "220.5 lb"
            compose.onNodeWithContentDescription("Estimated 1RM, 2 sessions, latest $value, best $value").assertIsDisplayed()
            compose.onNodeWithText("+0 ${displayUnit.suffix}").assertDoesNotExist()
            compose.onNodeWithTag("$tag-values").performScrollTo().performClick()
            for ((index, date) in listOf("5 January 2026", "12 January 2026").withIndex()) {
                compose.onNodeWithTag("$tag-data-list").performScrollToNode(hasText(date))
                compose.onNode(hasText(date) and hasAnyAncestor(hasTestTag("$tag-point-$index"))).assertIsDisplayed()
                compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("$tag-point-$index"))).assertIsDisplayed()
            }
            captureData(name = "flat-loaded-${displayUnit.suffix}-data", tag = tag)
            compose.onNodeWithTag("$tag-close").performClick()
        }
    }

    private fun assertWeeks(tag: String, value: String) {
        val dates = listOf("5 January 2026 – 11 January 2026", "12 January 2026 – 18 January 2026")
        for ((index, date) in dates.withIndex()) {
            compose.onNodeWithTag("$tag-data-list").performScrollToNode(hasText(date))
            compose.onNode(hasText(date) and hasAnyAncestor(hasTestTag("$tag-point-$index"))).assertIsDisplayed()
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("$tag-point-$index"))).assertIsDisplayed()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        NativeArtifacts.write("notes-chart-$name", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun captureData(name: String, tag: String) {
        compose.waitForIdle()
        NativeArtifacts.write("notes-chart-$name", compose.onNodeWithTag("$tag-data").captureToImage().asAndroidBitmap())
    }
}
