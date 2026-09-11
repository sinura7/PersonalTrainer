package com.sinura.personaltrainer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.ui.components.CountBadge
import com.sinura.personaltrainer.ui.components.DangerGymButton
import com.sinura.personaltrainer.ui.components.EmptyIllustration
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.Numeral
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

const val ComponentGalleryTag = "component-state-gallery"

/**
 * H3: every public component this packet named, in idle and selected/on
 * states, through GoldenCapture at 360×800. Feature screens are not here.
 */
@Composable
fun ComponentStateGallery(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Pit)
            .testTag(ComponentGalleryTag)
            .padding(Metrics.space4),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Kicker("Component gallery")
                Text(
                    "Public atoms in idle and selected states.",
                    style = InstrumentType.title,
                    color = TextPrimary,
                )
            }
        }
        item {
            GymCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Row")
                    InstrumentRow(title = "Idle row", subtitle = "Subtitle")
                    InstrumentRow(
                        title = "Selected row",
                        subtitle = "On",
                        selected = true,
                        onClick = {},
                    )
                    InstrumentRow(
                        title = "Switch row",
                        checked = true,
                        onCheckedChange = {},
                        trailing = { InstrumentSwitch(checked = true, onCheckedChange = null) },
                    )
                }
            }
        }
        item {
            GymCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Acts")
                    PrimaryGymButton(text = "Primary", onClick = {}, hapticFeedback = false)
                    SecondaryGymButton(text = "Secondary", onClick = {})
                    DangerGymButton(text = "Danger", onClick = {})
                }
            }
        }
        item {
            GymCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Empty")
                    EmptyScene.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                            row.forEach { scene ->
                                EmptyIllustration(scene = scene, size = Metrics.control)
                            }
                        }
                    }
                }
            }
        }
        item {
            GymCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    Kicker("Readout")
                    Numeral(value = "185.0", unit = "lbs")
                    CountBadge(number = 3, selected = false)
                    CountBadge(number = 3, selected = true)
                    InstrumentChip(label = "Idle", selected = false, onClick = {})
                    InstrumentChip(label = "Selected", selected = true, onClick = {})
                    Text(
                        "Switch off / on",
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                    InstrumentSwitch(checked = false, onCheckedChange = {})
                    InstrumentSwitch(checked = true, onCheckedChange = {})
                }
            }
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun ComponentGalleryPreview() {
    PersonalTrainerTheme {
        ComponentStateGallery(modifier = Modifier.fillMaxWidth())
    }
}
