package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.BodyView
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperReducedMotionPreview
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun BodyPopulatedPreview() {
    PersonalTrainerTheme {
        BodyPreviewColumn {
            BodyMapCard(
                snapshot = BodyPreviewFixtures.snapshot,
                view = BodyView.FRONT,
                onViewChange = {},
                selected = CanonicalMuscle.CHEST,
                onSelect = {},
            )
            MuscleHeatRow(
                load = BodyPreviewFixtures.chest,
                selected = true,
                onClick = {},
                unit = WeightUnit.KG,
            )
        }
    }
}

@TemperWidthPreviews
@Composable
private fun BodyEmptyPreview() {
    PersonalTrainerTheme {
        BodyPreviewColumn {
            BodyMapCard(
                snapshot = BodyPreviewFixtures.empty,
                view = BodyView.FRONT,
                onViewChange = {},
                selected = null,
                onSelect = {},
            )
        }
    }
}

@TemperReducedMotionPreview
@Composable
private fun BodyReducedMotionPreview() {
    PersonalTrainerTheme(reduceMotion = true) {
        BodyPreviewColumn {
            MuscleHeatRow(
                load = BodyPreviewFixtures.chest,
                selected = false,
                onClick = {},
                unit = WeightUnit.KG,
            )
        }
    }
}

@Composable
private fun BodyPreviewColumn(content: @Composable () -> Unit) {
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

internal object BodyPreviewFixtures {
    val chest = MuscleLoadSummary(
        muscle = CanonicalMuscle.CHEST,
        volumeKg = 3_200.0,
        workingSets = 8,
        sessionCount = 2,
        lastTrainedAtMs = 1_700_000_000_000L,
        daysSinceLastTrained = 2,
        weeklySets = 8.0,
        heat = 0.5,
        exercises = emptyList(),
    )

    val snapshot = BodyHeatSnapshot(
        window = HeatWindow.CURRENT_WEEK,
        windowStartMs = 1L,
        generatedAtMs = 2L,
        loads = listOf(chest),
        hasAnyWorkingSets = true,
        hasWindowWorkingSets = true,
    )

    val empty = BodyHeatSnapshot(
        window = HeatWindow.DAY,
        windowStartMs = 1L,
        generatedAtMs = 2L,
        loads = emptyList(),
        hasAnyWorkingSets = false,
        hasWindowWorkingSets = false,
    )
}
