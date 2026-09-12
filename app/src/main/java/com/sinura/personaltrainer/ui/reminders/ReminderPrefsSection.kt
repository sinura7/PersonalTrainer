package com.sinura.personaltrainer.ui.reminders

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.DayReminder
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Per-day workout reminder alarms. Quiet hours stay secondary.
 * Rest-timer notifications are unchanged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPrefsSection(
    preferences: ReminderPreferences,
    clockFormat: ClockFormat,
    notificationsEnabled: Boolean,
    onOptOut: (Boolean) -> Unit,
    onQuietHours: (Int, Int) -> Unit,
    onSetDayAlarm: (Weekday, Int, Int) -> Unit,
    onClearDayAlarm: (Weekday) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !preferences.optOut
    var editing by remember {
        mutableStateOf(preferences.dayAlarms.keys.firstOrNull())
    }
    var quietOpen by rememberSaveable { mutableStateOf(false) }
    val selected = editing?.takeIf { it in preferences.dayAlarms }
        ?: preferences.dayAlarms.keys.firstOrNull()
    val reminder = selected?.let { preferences.dayAlarms[it] } ?: DayReminder(7, 0)
    Column(
        modifier = modifier
            .testTag(REMINDERS_TAG)
            .padding(top = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
    ) {
        Kicker("Reminders")
        if (enabled && !notificationsEnabled) {
            GymNoticeBanner(
                title = ReminderCopy.PERMISSION_TITLE,
                body = ReminderCopy.PERMISSION_BODY,
                actionLabel = ReminderCopy.PERMISSION_ACTION,
                onAction = onOpenNotificationSettings,
            )
        }
        GroupedList {
            InstrumentRow(
                title = ReminderCopy.SWITCH_TITLE,
                subtitle = ReminderCopy.SWITCH_SUBTITLE,
                checked = enabled,
                onCheckedChange = { on -> onOptOut(!on) },
                trailing = {
                    InstrumentSwitch(checked = enabled, onCheckedChange = null)
                },
            )
            if (enabled) {
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
                    Kicker(ReminderCopy.DAYS)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        Weekday.entries.forEach { day ->
                            val on = day in preferences.dayAlarms
                            InstrumentChip(
                                label = day.shortLabel(),
                                selected = on,
                                onClick = {
                                    if (on) {
                                        onClearDayAlarm(day)
                                        if (editing == day) editing = null
                                    } else {
                                        onSetDayAlarm(day, reminder.hour, reminder.minute)
                                        editing = day
                                    }
                                },
                                modifier = Modifier.testTag(reminderDayTag(day)),
                            )
                        }
                    }
                    if (preferences.dayAlarms.isEmpty()) {
                        Text(
                            ReminderCopy.ALARM_EMPTY,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                    } else {
                        Text(
                            ReminderCopy.timeLabel(
                                reminder.hour,
                                reminder.minute,
                                clockFormat,
                            ),
                            style = InstrumentType.body,
                            color = TextSecondary,
                        )
                        Kicker(ReminderCopy.TIME)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            (1..12).forEach { twelve ->
                                InstrumentChip(
                                    label = twelve.toString(),
                                    selected = ReminderCopy.twelveHour(reminder.hour) == twelve,
                                    onClick = {
                                        val day = selected
                                        if (day != null) {
                                            val hour = ReminderCopy.toHour24(
                                                twelve,
                                                ReminderCopy.isPm(reminder.hour),
                                            )
                                            onSetDayAlarm(day, hour, reminder.minute)
                                        }
                                    },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            ReminderCopy.minuteChoices.forEach { minute ->
                                InstrumentChip(
                                    label = "%02d".format(minute),
                                    selected = reminder.minute == minute,
                                    onClick = {
                                        val day = selected
                                        if (day != null) onSetDayAlarm(day, reminder.hour, minute)
                                    },
                                )
                            }
                            InstrumentChip(
                                label = "AM",
                                selected = !ReminderCopy.isPm(reminder.hour),
                                onClick = {
                                    val day = selected
                                    if (day != null) {
                                        val hour = ReminderCopy.toHour24(
                                            ReminderCopy.twelveHour(reminder.hour),
                                            pm = false,
                                        )
                                        onSetDayAlarm(day, hour, reminder.minute)
                                    }
                                },
                            )
                            InstrumentChip(
                                label = "PM",
                                selected = ReminderCopy.isPm(reminder.hour),
                                onClick = {
                                    val day = selected
                                    if (day != null) {
                                        val hour = ReminderCopy.toHour24(
                                            ReminderCopy.twelveHour(reminder.hour),
                                            pm = true,
                                        )
                                        onSetDayAlarm(day, hour, reminder.minute)
                                    }
                                },
                            )
                        }
                    }
                    TextButton(onClick = { quietOpen = !quietOpen }) {
                        Text(
                            if (quietOpen) "Hide quiet hours" else "Quiet hours",
                            style = InstrumentType.bodyStrong,
                            color = TextSecondary,
                        )
                    }
                    if (quietOpen) {
                        Text(
                            ReminderCopy.quietHoursLine(
                                preferences.quietStartHour,
                                preferences.quietEndHour,
                                clockFormat,
                            ),
                            style = InstrumentType.body,
                            color = TextSecondary,
                        )
                        Text(
                            ReminderCopy.QUIET_CAPTION,
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                        Kicker(ReminderCopy.QUIET_START)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            ReminderCopy.startChoices(preferences.quietStartHour).forEach { hour ->
                                InstrumentChip(
                                    label = ReminderCopy.hourLabel(hour, clockFormat),
                                    selected = preferences.quietStartHour == hour,
                                    onClick = { onQuietHours(hour, preferences.quietEndHour) },
                                )
                            }
                        }
                        Kicker(ReminderCopy.QUIET_END)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            ReminderCopy.endChoices(preferences.quietEndHour).forEach { hour ->
                                InstrumentChip(
                                    label = ReminderCopy.hourLabel(hour, clockFormat),
                                    selected = preferences.quietEndHour == hour,
                                    onClick = { onQuietHours(preferences.quietStartHour, hour) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Rest alerts are unchanged. Exact alarms when the phone allows them.",
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}

@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

fun openAppNotificationSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

const val REMINDERS_TAG = "plan-reminders"

fun reminderDayTag(day: Weekday): String = "reminder-day-${day.name}"
