package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextTertiary

@Composable
internal fun DisplayPrefsSection(
    selectedUnit: WeightUnit,
    clockFormat: ClockFormat,
    onSelectUnit: (WeightUnit) -> Unit,
    onSelectClock: (ClockFormat) -> Unit,
) {
    Column(
        modifier = Modifier.testTag(SettingsTags.DISPLAY),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Weight", compact = true)
            SettingsRadioList(
                items = listOf(WeightUnit.LBS, WeightUnit.KG),
                selected = selectedUnit,
                title = { it.displayName },
                onSelect = onSelectUnit,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Hours", compact = true)
            SettingsRadioList(
                items = ClockFormat.entries,
                selected = clockFormat,
                title = { it.displayName },
                subtitle = { it.shortLabel },
                onSelect = onSelectClock,
            )
        }
        Text(
            "Hours change History, session stamps, and backup times. " +
                "Distance follows weight: pounds show miles, kilograms show kilometres. " +
                "Stored weights stay kilograms. Stored distance stays metres.",
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}
