package com.sinura.personaltrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.ui.activity.ElapsedReadout
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailHeader
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.routines.RoutineEditorHeader
import com.sinura.personaltrainer.ui.summary.SummaryActions
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit

/**
 * P9.6 remaining pages for FND-043. Real pieces, not a second layout.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun RemainingEditorsPreview() {
    PersonalTrainerTheme {
        RemainingPreviewColumn {
            RoutineEditorHeader(onBack = {})
            SecondaryGymButton(text = "Add lifts", onClick = {})
            PrimaryGymButton(text = CustomWeekPolicy.confirmCta(3), onClick = {})
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun RemainingSessionPagesPreview() {
    PersonalTrainerTheme {
        RemainingPreviewColumn {
            SummaryActions(onDone = {}, onOpenSession = {})
            ExerciseDetailHeader(name = "Bench press", exercise = null, onBack = {})
            SecondaryGymButton(text = "Add to a routine", onClick = {})
            ElapsedReadout(elapsedSeconds = 462)
            PrimaryGymButton(text = "Finish", onClick = {})
            PrimaryGymButton(text = "Save", onClick = {})
            TextButton(onClick = {}) { Text("Done") }
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun RemainingPushedPagesPreview() {
    PersonalTrainerTheme {
        RemainingPreviewColumn {
            PrimaryGymButton(text = "Use this plan", onClick = {})
            SecondaryGymButton(text = "Export to file", onClick = {})
            SecondaryGymButton(text = "Share diagnostics", onClick = {})
        }
    }
}

@Composable
private fun RemainingPreviewColumn(content: @Composable () -> Unit) {
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
