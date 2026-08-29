package com.sinura.personaltrainer.ui.reminders

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.ReminderCopy
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Session reminder opt-out and quiet hours. Lives on Settings (ADR-017).
 * Session hours stay on the Plan day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPrefsSection(
    preferences: ReminderPreferences,
    clockFormat: ClockFormat,
    notificationsEnabled: Boolean,
    onOptOut: (Boolean) -> Unit,
    onQuietHours: (Int, Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !preferences.optOut
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
                trailing = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on -> onOptOut(!on) },
                    )
                },
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
                Text(
                    ReminderCopy.quietHoursLine(
                        preferences.quietStartHour,
                        preferences.quietEndHour,
                        clockFormat,
                    ),
                    style = InstrumentType.body,
                    color = TextSecondary,
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
        Text(
            "Best-effort. Not exact alarms. Times for each session are on the day page.",
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
