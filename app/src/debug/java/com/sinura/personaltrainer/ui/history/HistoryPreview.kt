package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HorizonTotals
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun HistoryPopulatedPreview() {
    PersonalTrainerTheme {
        HistoryPreviewColumn {
            HorizonPicker(
                horizon = AnalyticsHorizon.YEAR,
                totals = HistoryPreviewFixtures.totals,
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
    }
}

@TemperWidthPreviews
@Composable
private fun HistoryEmptyPreview() {
    PersonalTrainerTheme {
        HistoryPreviewColumn {
            HorizonPicker(
                horizon = AnalyticsHorizon.MONTH,
                totals = HistoryPreviewFixtures.emptyMonth,
                onSelect = {},
            )
        }
    }
}

@Composable
private fun HistoryPreviewColumn(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit)
                .padding(Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
            content()
        }
    }
}

internal object HistoryPreviewFixtures {
    val totals = HorizonTotals(
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
    val emptyMonth = HorizonTotals(
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
