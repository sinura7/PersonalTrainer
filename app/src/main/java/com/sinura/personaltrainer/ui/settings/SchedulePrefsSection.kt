package com.sinura.personaltrainer.ui.settings


import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.plan.PreferenceBlock
import com.sinura.personaltrainer.domain.Weekday

@Composable
internal fun SchedulePrefsSection(
    preferences: SchedulePreferences,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (Weekday) -> Unit,
) {
    SettingsGroup(
        title = "Schedule",
        // The week itself moved to its own tab. What is left here shapes what a suggestion
        // looks like, which is a preference; the week is a decision, and decisions belong
        // where you can see the thing you are deciding about.
        caption = "Days, split and week start. Pin the week itself on the Plan tab.",
    ) {
        GymCard {
            PreferenceBlock(
                preferences = preferences,
                onDays = onDays,
                onSplit = onSplit,
                onWeekStart = onWeekStart,
            )
        }
    }
}
