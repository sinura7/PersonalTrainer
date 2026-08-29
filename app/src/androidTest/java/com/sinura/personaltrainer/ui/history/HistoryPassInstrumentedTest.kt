package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.components.SessionLogTags
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 History: day / week / month / year / all chips and identity-first
 * rows stay named at 360 dp / font 2.0. History has no Start Volt.
 */
@RunWith(AndroidJUnit4::class)
class HistoryPassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun horizonsAndSessionStayNamedAt360Font2() {
        setConstrainedContent(2f) {
            HorizonPicker(
                horizon = AnalyticsHorizon.YEAR,
                totals = TOTALS,
                onSelect = {},
            )
            SessionLogRow(
                title = "Upper strength",
                dateLabel = "Mon 2 Jan",
                workingSets = 16,
                work = SetWork(volumeKg = 8_000.0, bodyweightReps = 0),
                durationMinutes = 48,
                onClick = {},
                unit = WeightUnit.KG,
            )
        }
        compose.onNodeWithTag(HistoryTags.DAY).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.WEEK).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.MONTH).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.YEAR).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.ALL).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.READOUT).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.READOUT)
            .assertContentDescriptionEquals("This year, 80 sessions")
        compose.onNodeWithTag(SessionLogTags.ROW).assertIsDisplayed()
        compose.onNodeWithTag(SessionLogTags.ROW)
            .assertContentDescriptionEquals(
                "Upper strength, Mon 2 Jan, 16 sets, 8000 kg, 48 min",
            )
        compose.onNodeWithText("Start a workout").assertDoesNotExist()
    }

    @Test
    fun emptyReadoutHasNoStartVolt() {
        setConstrainedContent(1f) {
            HorizonPicker(
                horizon = AnalyticsHorizon.MONTH,
                totals = EMPTY_MONTH,
                onSelect = {},
            )
        }
        compose.onNodeWithTag(HistoryTags.DAY).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTags.READOUT)
            .assertContentDescriptionEquals("This month, 0 sessions")
        compose.onNodeWithText("Start a workout").assertDoesNotExist()
        compose.onNodeWithText("No sessions yet").assertDoesNotExist()
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.KG,
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

    private companion object {
        val TOTALS = HorizonTotals(
            horizon = AnalyticsHorizon.YEAR,
            startEpochDay = 1,
            endEpochDay = 365,
            sessionCount = 80,
            trainedDays = 70,
            workingSets = 1_200,
            volumeKg = 400_000.0,
            activeMinutes = 4_000,
            cardioSeconds = 0,
            cardioDistanceMeters = 0.0,
        )
        val EMPTY_MONTH = HorizonTotals(
            horizon = AnalyticsHorizon.MONTH,
            startEpochDay = 1,
            endEpochDay = 31,
            sessionCount = 0,
            trainedDays = 0,
            workingSets = 0,
            volumeKg = 0.0,
            activeMinutes = 0,
            cardioSeconds = 0,
            cardioDistanceMeters = 0.0,
        )
    }
}
