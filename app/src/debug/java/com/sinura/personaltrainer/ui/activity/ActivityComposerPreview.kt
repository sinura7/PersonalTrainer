package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.ComposerCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * Activity composer states for the MP-11 golden fan-out. Real Save / error / rows.
 * Save is the Volt. Add is secondary. Remove is a named control, not a row tap.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ActivityComposerPopulatedPreview() {
    PersonalTrainerTheme {
        CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
            ComposerPreviewColumn {
                Text("Log a past workout", style = InstrumentType.title, color = TextPrimary)
                Text(
                    ComposerCopy.LOGGED_AT_NOON,
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                Kicker("Strength")
                InstrumentRow(
                    title = "Bench press",
                    subtitle = ComposerCopy.strengthLineSubtitle(5, 80.0, WeightUnit.KG),
                    trailing = {
                        TextButton(onClick = {}) {
                            Text(ComposerCopy.REMOVE, style = InstrumentType.bodyStrong, color = Danger)
                        }
                    },
                )
                SecondaryGymButton(text = ComposerCopy.ADD_SET, onClick = {})
                PrimaryGymButton(text = ComposerCopy.SAVE, onClick = {})
                TextButton(onClick = {}) { Text(ComposerCopy.CANCEL) }
            }
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
            SecondaryGymButton(text = ComposerCopy.ADD_SET, onClick = {})
            PrimaryGymButton(text = ComposerCopy.SAVE, onClick = {})
            TextButton(onClick = {}) { Text(ComposerCopy.CANCEL) }
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
            PrimaryGymButton(text = ComposerCopy.SAVE, onClick = {})
            TextButton(onClick = {}) { Text(ComposerCopy.CANCEL) }
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
