package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.BodyweightCheckIn
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.domain.Weekday

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodyweightPrefsSection(
    bodyweightKg: Double?,
    unit: WeightUnit,
    weekStart: Weekday,
    daysPerWeek: Int,
    preferredDays: Set<Weekday>,
    checkInOverride: Weekday?,
    onRecordBodyweight: (Double) -> Unit,
    onClearBodyweight: () -> Unit,
    onCheckInDay: (Weekday?) -> Unit,
) {
    var weighingIn by rememberSaveable { mutableStateOf(false) }
    if (weighingIn) {
        NumberEntryDialog(
            title = "Bodyweight",
            unitLabel = unit.suffix,
            initial = bodyweightKg
                ?.let { WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(it, unit)) }
                .orEmpty(),
            decimal = true,
            helper = "Weekly check-in. One a day is kept — the last one you type.",
            parse = { NumericEntry.parseWeightKg(it, unit) },
            onConfirm = {
                weighingIn = false
                onRecordBodyweight(it)
            },
            onDismiss = { weighingIn = false },
        )
    }
    val autoDay = BodyweightCheckIn.dueWeekday(
        preferredDays = preferredDays,
        weekStart = weekStart,
        daysPerWeek = daysPerWeek,
        override = null,
    )
    SettingsGroup(
        title = "Bodyweight",
        caption = if (checkInOverride == null) {
            "Weekly check-in on ${autoDay.shortLabel()} — your first training day. Home asks that morning."
        } else {
            "Weekly check-in on ${checkInOverride.shortLabel()}. Home asks that morning."
        },
        modifier = Modifier.testTag(SettingsTags.BODYWEIGHT),
    ) {
        GymCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bodyweightKg?.toWeightLabel(unit) ?: "Not set",
                    style = InstrumentType.numeralSm,
                    color = if (bodyweightKg != null) TextPrimary else TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    if (bodyweightKg != null) {
                        TextButton(onClick = onClearBodyweight) {
                            Text("Clear", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                    TextButton(onClick = { weighingIn = true }) {
                        Text(
                            if (bodyweightKg != null) "Update" else "Add",
                            style = InstrumentType.bodyStrong,
                            color = Volt,
                        )
                    }
                }
            }
            Kicker("Check-in day")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                InstrumentChip(
                    label = "Auto",
                    selected = checkInOverride == null,
                    onClick = { onCheckInDay(null) },
                )
                (0 until 7).map { weekStart.plus(it.toLong()) }.forEach { day ->
                    InstrumentChip(
                        label = day.shortLabel(),
                        selected = checkInOverride == day,
                        onClick = { onCheckInDay(day) },
                    )
                }
            }
        }
    }
}
