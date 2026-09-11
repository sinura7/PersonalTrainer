package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.RestTick
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.ui.components.CustomRestDialog
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.RestPresetChips
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
internal fun RestTimerPrefsSection(
    preferences: RestTimerPreferences,
    offerExactAlarmAccess: Boolean,
    onAllowPreciseRestAlerts: () -> Unit,
    onSound: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onTick: (Boolean) -> Unit,
    onDefaultRest: (Int) -> Unit,
    onCustomDefault: (String) -> Boolean,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(
        title = "Rest timer",
        caption = "The cue plays when rest ends. The last five seconds tick while the phone " +
            "is awake. Default rest is used after a working set if the lift has none and " +
            "you haven't picked a preset.",
    ) {
        if (offerExactAlarmAccess) {
            GymNoticeBanner(
                title = "Rest alerts may be delayed",
                body = "This phone has not allowed precise rest alarms. The timer still runs, " +
                    "but the cue can arrive late if the screen is off.",
                actionLabel = "Allow precise rest alerts",
                onAction = onAllowPreciseRestAlerts,
            )
        }
        GroupedList {
            InstrumentRow(
                title = "Sound",
                checked = preferences.soundEnabled,
                onCheckedChange = onSound,
                trailing = { InstrumentSwitch(checked = preferences.soundEnabled, onCheckedChange = null) },
            )
            HairlineDivider()
            InstrumentRow(
                title = "Vibration",
                checked = preferences.vibrationEnabled,
                onCheckedChange = onVibrate,
                trailing = { InstrumentSwitch(checked = preferences.vibrationEnabled, onCheckedChange = null) },
            )
            HairlineDivider()
            InstrumentRow(
                title = RestTick.TITLE,
                subtitle = RestTick.CAPTION,
                checked = preferences.tickEnabled,
                onCheckedChange = onTick,
                trailing = { InstrumentSwitch(checked = preferences.tickEnabled, onCheckedChange = null) },
            )
            HairlineDivider()
            Column(
                modifier = Modifier.padding(
                    start = Metrics.space4,
                    end = Metrics.space4,
                    top = Metrics.space3,
                    bottom = Metrics.space4,
                ),
                verticalArrangement = Arrangement.spacedBy(Metrics.space3),
            ) {
                Kicker("Default rest")
                RestPresetChips(
                    selectedSeconds = preferences.defaultRestSeconds,
                    onSelect = onDefaultRest,
                    onCustom = { showCustom = true },
                )
            }
        }
    }
    if (showCustom) {
        CustomRestDialog(
            title = "Default rest",
            confirmLabel = "Save",
            onConfirm = { input ->
                val ok = onCustomDefault(input)
                if (ok) showCustom = false
                ok
            },
            onDismiss = { showCustom = false },
        )
    }
}
