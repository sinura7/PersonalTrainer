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
                items = listOf(WeightUnit.LBS, WeightUnit.KG).map { SettingsRadioOption(it.displayName) },
                selectedIndex = listOf(WeightUnit.LBS, WeightUnit.KG).indexOf(selectedUnit),
                onSelect = { onSelectUnit(listOf(WeightUnit.LBS, WeightUnit.KG)[it]) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            GymSectionHeader(title = "Hours", compact = true)
            SettingsRadioList(
                items = ClockFormat.entries.map { SettingsRadioOption(it.displayName, it.shortLabel) },
                selectedIndex = ClockFormat.entries.indexOf(clockFormat),
                onSelect = { onSelectClock(ClockFormat.entries[it]) },
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
