package com.sinura.personaltrainer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * A deterministic state wall for preview and screenshot infrastructure.
 *
 * It is intentionally debug-only. Feature previews map [PreviewFixtures] to
 * real UiState instances; this wall proves the profiles and golden pipeline
 * before every page owns fixtures.
 */
@Composable
fun FoundationStateGallery(
    modifier: Modifier = Modifier,
    accent: Color = Volt,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Pit)
            .testTag(FoundationGalleryTag)
            .padding(Metrics.space4),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Kicker("Preview substrate")
                Text(
                    "Every state has an identity, metric, and next act.",
                    style = InstrumentType.title,
                    color = TextPrimary,
                )
                Text(
                    if (LocalReducedMotion.current) "Reduced motion" else "Standard motion",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(accent),
                )
            }
        }
        items(PreviewFixtures.all, key = { it.kind }) { fixture ->
            FixtureCard(fixture)
        }
    }
}

@Composable
private fun FixtureCard(fixture: FoundationPreviewFixture) {
    GymCard {
        Kicker(fixture.kind.name.replace('_', ' '))
        Text(fixture.title, style = InstrumentType.title, color = TextPrimary)
        Text(fixture.body, style = InstrumentType.body, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            MetricCluster(
                value = fixture.primaryMetric,
                label = "primary",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = fixture.secondaryMetric,
                label = "secondary",
            )
        }
        fixture.actionLabel?.let { label ->
            PrimaryGymButton(
                text = label,
                onClick = {},
                hapticFeedback = false,
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun FoundationGalleryPreview() {
    PersonalTrainerTheme {
        FoundationStateGallery()
    }
}

@TemperReducedMotionPreview
@Composable
private fun FoundationGalleryReducedMotionPreview() {
    PersonalTrainerTheme(reduceMotion = true) {
        FoundationStateGallery()
    }
}

const val FoundationGalleryTag = "foundation-preview-gallery"
