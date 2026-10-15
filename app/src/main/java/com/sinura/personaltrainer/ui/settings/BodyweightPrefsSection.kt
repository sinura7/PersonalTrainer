package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.domain.Weekday

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
    history: List<BodyweightEntry> = emptyList(),
    onCorrectDay: (Long, Double) -> Unit = { _, _ -> },
    onDeleteDay: (Long) -> Unit = {},
) {
    var weighingIn by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var editingDay by rememberSaveable { mutableStateOf(-1L) }
    var deletingDay by rememberSaveable { mutableStateOf(-1L) }
    val currentLabel = bodyweightKg?.toWeightLabel(unit) ?: "Not set"
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
    if (editingDay >= 0L) {
        val day = editingDay
        val existing = history.firstOrNull { it.epochDay == day }
        NumberEntryDialog(
            title = "Weigh-in on ${dateLabel(day)}",
            unitLabel = unit.suffix,
            initial = existing
                ?.let { WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(it.kg, unit)) }
                .orEmpty(),
            decimal = true,
            helper = "Replaces only ${dateLabel(day)}. Other days stay as they are.",
            parse = { NumericEntry.parseWeightKg(it, unit) },
            onConfirm = {
                editingDay = -1L
                onCorrectDay(day, it)
            },
            onDismiss = { editingDay = -1L },
        )
    }
    if (confirmClear && bodyweightKg != null) {
        GymDialog(
            title = "Clear current bodyweight?",
            body = "This clears $currentLabel as the current figure. Weigh-in history stays until you delete a day.",
            confirmLabel = "Clear $currentLabel",
            destructive = true,
            onConfirm = {
                confirmClear = false
                onClearBodyweight()
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (deletingDay >= 0L) {
        val day = deletingDay
        val existing = history.firstOrNull { it.epochDay == day }
        val weight = existing?.kg?.toWeightLabel(unit) ?: "that weigh-in"
        GymDialog(
            title = "Delete ${dateLabel(day)}?",
            body = "This deletes the $weight weigh-in on ${dateLabel(day)}. Cancel leaves it unchanged.",
            confirmLabel = "Delete $weight",
            destructive = true,
            onConfirm = {
                deletingDay = -1L
                onDeleteDay(day)
            },
            onDismiss = { deletingDay = -1L },
        )
    }
    val autoDay = BodyweightCheckIn.dueWeekday(
        preferredDays = preferredDays,
        weekStart = weekStart,
        daysPerWeek = daysPerWeek,
        override = null,
    )
    val orderedDays = (0 until 7).map { weekStart.plus(it.toLong()) }
    val checkInChoices: List<Weekday?> = buildList {
        add(null)
        addAll(orderedDays)
    }
    SettingsGroup(
        title = "",
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
                    currentLabel,
                    style = InstrumentType.numeralSm,
                    color = if (bodyweightKg != null) TextPrimary else TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    if (bodyweightKg != null) {
                        TextButton(onClick = { confirmClear = true }) {
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
        }
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
            Kicker("Check-in day")
            SettingsRadioList(
                items = checkInChoices.map { day ->
                    if (day == null) {
                        SettingsRadioOption("Auto", "First training day · ${autoDay.shortLabel()}")
                    } else {
                        SettingsRadioOption(day.titleLabel())
                    }
                },
                selectedIndex = checkInChoices.indexOf(checkInOverride),
                onSelect = { onCheckInDay(checkInChoices[it]) },
            )
        }
        if (history.isNotEmpty()) {
            Kicker("History")
            history.sortedByDescending { it.epochDay }.take(8).forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight-day-${entry.epochDay}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${dateLabel(entry.epochDay)} · ${entry.kg.toWeightLabel(unit)}",
                        style = InstrumentType.body,
                        color = TextPrimary,
                    )
                    Row {
                        TextButton(onClick = { editingDay = entry.epochDay }) {
                            Text("Correct", color = Volt, style = InstrumentType.bodyStrong)
                        }
                        TextButton(onClick = { deletingDay = entry.epochDay }) {
                            Text("Delete", color = TextSecondary, style = InstrumentType.bodyStrong)
                        }
                    }
                }
            }
        }
    }
}

private fun dateLabel(epochDay: Long): String {
    val date = CivilDate.fromEpochDay(epochDay)
    return "%04d-%02d-%02d".format(date.year, date.month, date.day)
}
