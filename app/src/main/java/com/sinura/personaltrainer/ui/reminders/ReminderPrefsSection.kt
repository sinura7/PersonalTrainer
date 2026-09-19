package com.sinura.personaltrainer.ui.reminders

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Per-day workout reminder alarms. Quiet hours stay secondary.
 * Rest-timer notifications stay on Rest, unchanged.
 */
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
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
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
                Weekday.entries.forEach { day ->
                    HairlineDivider()
                    val on = day in preferences.dayAlarms
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InstrumentRow(
                            title = day.titleLabel(),
                            subtitle = if (on) {
                                val stored = preferences.dayAlarms.getValue(day)
                                ReminderCopy.timeLabel(stored.hour, stored.minute, clockFormat)
                            } else {
                                ReminderCopy.DAY_OFF
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(reminderDayTag(day)),
                            selected = on && day == selected,
                            onClick = {
                                if (on) {
                                    editing = day
                                } else {
                                    onSetDayAlarm(day, reminder.hour, reminder.minute)
                                    editing = day
                                }
                            },
                        )
                        InstrumentSwitch(
                            checked = on,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onSetDayAlarm(day, reminder.hour, reminder.minute)
                                    editing = day
                                } else {
                                    onClearDayAlarm(day)
                                    if (editing == day) editing = null
                                }
                            },
                            modifier = Modifier.padding(end = Metrics.space4),
                        )
                    }
                }
            }
        }
        if (enabled && preferences.dayAlarms.isEmpty()) {
            Text(
                ReminderCopy.ALARM_EMPTY,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                GroupedList {
                    InstrumentRow(
                        title = "Quiet hours",
                        subtitle = ReminderCopy.quietHoursLine(
                            preferences.quietStartHour,
                            preferences.quietEndHour,
                            clockFormat,
                        ),
                        onClick = { quietOpen = !quietOpen },
                    )
                }
                if (quietOpen) {
                    Text(
                        ReminderCopy.QUIET_CAPTION,
                        style = InstrumentType.caption,
                        color = TextTertiary,
                    )
                    GymSectionHeader(title = ReminderCopy.QUIET_START, compact = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        ReminderCopy.startChoices(preferences.quietStartHour).forEach { hour ->
                            InstrumentChip(
                                label = ReminderCopy.hourLabel(hour, clockFormat),
                                selected = preferences.quietStartHour == hour,
                                onClick = { onQuietHours(hour, preferences.quietEndHour) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    GymSectionHeader(title = ReminderCopy.QUIET_END, compact = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        ReminderCopy.endChoices(preferences.quietEndHour).forEach { hour ->
                            InstrumentChip(
                                label = ReminderCopy.hourLabel(hour, clockFormat),
                                selected = preferences.quietEndHour == hour,
                                onClick = { onQuietHours(preferences.quietStartHour, hour) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            ReminderCopy.REST_STAYS_ON_REST,
            style = InstrumentType.caption,
            color = TextTertiary,
        )
        }
        if (enabled && selected != null && preferences.dayAlarms.isNotEmpty()) {
            val day = selected
            GymCard {
                GymSectionHeader(
                    title = "${ReminderCopy.TIME} · ${day.titleLabel()}",
                    compact = true,
                )
                Text(
                    ReminderCopy.timeLabel(
                        reminder.hour,
                        reminder.minute,
                        clockFormat,
                    ),
                    style = InstrumentType.numeralMd,
                    color = TextPrimary,
                )
                key(day) {
                    ReminderTimeWheel(
                        hour = reminder.hour,
                        minute = reminder.minute,
                        onTime = { hour, minute ->
                            onSetDayAlarm(day, hour, minute)
                        },
                    )
                }
            }
        }
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
const val REMINDER_TIME_WHEEL = "reminder-time-wheel"
const val REMINDER_TIME_HOUR = "reminder-time-hour"
const val REMINDER_TIME_MINUTE = "reminder-time-minute"
const val REMINDER_TIME_PERIOD = "reminder-time-period"

fun reminderDayTag(day: Weekday): String = "reminder-day-${day.name}"
