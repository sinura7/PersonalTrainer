package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Live cardio states for the MP-11 golden fan-out. Real empty / clock / finish pieces.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun LiveCardioActivePreview() {
    PersonalTrainerTheme {
        CardioPreviewColumn {
            Text("Morning run", style = InstrumentType.title, color = TextPrimary)
            ElapsedReadout(elapsedSeconds = 462)
            Text(
                "Process death keeps this clock. Reboot keeps the last honest elapsed.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            Kicker("Type")
            Text("• RUN", style = InstrumentType.body, color = TextPrimary)
            PrimaryGymButton(text = "Finish", onClick = {})
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun LiveCardioMissingPreview() {
    PersonalTrainerTheme {
        CardioPreviewColumn {
            EmptyState(
                title = "No live cardio",
                body = "That session is gone. Start a new one from Home.",
                actionLabel = "Back",
                onAction = {},
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun LiveCardioErrorPreview() {
    PersonalTrainerTheme {
        CardioPreviewColumn {
            Text("Morning run", style = InstrumentType.title, color = TextPrimary)
            ElapsedReadout(elapsedSeconds = 90)
            GymErrorBanner("Could not finish this session. Retry.")
            PrimaryGymButton(text = "Finish", onClick = {})
        }
    }
}

@Composable
private fun CardioPreviewColumn(content: @Composable () -> Unit) {
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
