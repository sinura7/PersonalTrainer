package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
internal fun DisplayPrefsSection(
    selectedUnit: WeightUnit,
    clockFormat: ClockFormat,
    onSelectUnit: (WeightUnit) -> Unit,
    onSelectClock: (ClockFormat) -> Unit,
) {
    SettingsGroup(
        title = "Display",
        caption = "Hours change History, session stamps, and backup times. " +
            "Distance follows weight: pounds show miles, kilograms show kilometres. " +
            "Stored weights stay kilograms. Stored distance stays metres.",
        modifier = Modifier.testTag(SettingsTags.DISPLAY),
    ) {
        GymCard {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Weight",
                        modifier = Modifier.width(DISPLAY_LABEL_WIDTH),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        listOf(WeightUnit.LBS, WeightUnit.KG).forEach { unit ->
                            InstrumentChip(
                                label = unit.suffix,
                                selected = selectedUnit == unit,
                                onClick = { onSelectUnit(unit) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Hours",
                        modifier = Modifier.width(DISPLAY_LABEL_WIDTH),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        ClockFormat.entries.forEach { format ->
                            InstrumentChip(
                                label = format.displayName,
                                selected = clockFormat == format,
                                onClick = { onSelectClock(format) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DISPLAY_LABEL_WIDTH = 56.dp
