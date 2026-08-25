package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.InstrumentRow
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
 * Activity composer states for the MP-11 golden fan-out. Real Save / error / rows.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ActivityComposerPopulatedPreview() {
    PersonalTrainerTheme {
        ComposerPreviewColumn {
            Text("Log a past workout", style = InstrumentType.title, color = TextPrimary)
            Text(
                "Nothing is written until you save. Future dates are refused.",
                style = InstrumentType.body,
                color = TextSecondary,
            )
            Kicker("Strength")
            InstrumentRow(
                title = "Bench press",
                subtitle = "5 reps · 80.0 kg",
                onClick = {},
            )
            PrimaryGymButton(text = "Save", onClick = {})
            TextButton(onClick = {}) { Text("Cancel") }
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ActivityComposerEmptyPreview() {
    PersonalTrainerTheme {
        ComposerPreviewColumn {
            Text("Log a past workout", style = InstrumentType.title, color = TextPrimary)
            Text(
                "Nothing is written until you save. Future dates are refused.",
                style = InstrumentType.body,
                color = TextSecondary,
            )
            Kicker("Strength")
            Text("Add a set before saving.", style = InstrumentType.caption, color = TextSecondary)
            PrimaryGymButton(text = "Save", onClick = {})
            TextButton(onClick = {}) { Text("Cancel") }
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ActivityComposerErrorPreview() {
    PersonalTrainerTheme {
        ComposerPreviewColumn {
            Text("Log a past workout", style = InstrumentType.title, color = TextPrimary)
            GymErrorBanner("That date is in the future.")
            PrimaryGymButton(text = "Save", onClick = {})
            TextButton(onClick = {}) { Text("Cancel") }
        }
    }
}

@Composable
private fun ComposerPreviewColumn(content: @Composable () -> Unit) {
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
