package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun LibraryChromePreview() {
    PersonalTrainerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit)
                .padding(Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                    )
                }
                Text("Library", style = InstrumentType.display, color = TextPrimary)
            }
            ExerciseSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Name or muscle",
            )
            EmptyState(
                title = "No lifts match",
                body = "Clear the search or add a custom lift.",
                actionLabel = "Create lift",
                onAction = {},
                compact = true,
            )
        }
    }
}
