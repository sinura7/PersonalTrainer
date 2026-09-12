package com.sinura.personaltrainer.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.LaunchPermissionCopy
import com.sinura.personaltrainer.domain.LaunchPermissionStep
import com.sinura.personaltrainer.domain.LaunchPermissions
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog

/**
 * First-open walk for notifications, exact alarms, and unrestricted
 * battery. Grant and deny both count. A second session does not re-spam.
 */
@Composable
fun LaunchPermissionsHost(
    alreadyAsked: Boolean,
    onAsked: () -> Unit,
) {
    var walking by rememberSaveable { mutableStateOf(false) }
    if (alreadyAsked && !walking) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactGranted by remember { mutableStateOf(exactAlarmsGranted(context)) }
    var batteryUnrestricted by remember { mutableStateOf(batteryUnrestricted(context)) }

    fun refresh() {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactGranted = exactAlarmsGranted(context)
        batteryUnrestricted = batteryUnrestricted(context)
    }

    LaunchedEffect(alreadyAsked) {
        if (!alreadyAsked && !walking) {
            walking = true
            onAsked()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var skippedNotifications by rememberSaveable { mutableStateOf(false) }
    var skippedExact by rememberSaveable { mutableStateOf(false) }
    var skippedBattery by rememberSaveable { mutableStateOf(false) }

    val step = LaunchPermissions.nextStep(
        notificationsGranted = notificationsGranted || skippedNotifications,
        exactAlarmsGranted = exactGranted || skippedExact,
        batteryUnrestricted = batteryUnrestricted || skippedBattery,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh()
        if (!granted) skippedNotifications = true
    }

    if (step == LaunchPermissionStep.DONE) return

    val title: String
    val body: String
    val onContinue: () -> Unit
    when (step) {
        LaunchPermissionStep.NOTIFICATIONS -> {
            title = LaunchPermissionCopy.NOTIFICATIONS_TITLE
            body = LaunchPermissionCopy.NOTIFICATIONS_BODY
            onContinue = {
                if (Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    skippedNotifications = true
                }
            }
        }
        LaunchPermissionStep.EXACT_ALARM -> {
            title = LaunchPermissionCopy.EXACT_TITLE
            body = LaunchPermissionCopy.EXACT_BODY
            onContinue = {
                exactAlarmSettingsIntent(context.packageName)?.let { intent ->
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                skippedExact = true
            }
        }
        LaunchPermissionStep.BATTERY -> {
            title = LaunchPermissionCopy.BATTERY_TITLE
            body = LaunchPermissionCopy.BATTERY_BODY
            onContinue = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                skippedBattery = true
            }
        }
        LaunchPermissionStep.DONE -> return
    }

    ConfirmActionDialog(
        title = title,
        body = body,
        confirmLabel = LaunchPermissionCopy.CONTINUE,
        dismissLabel = LaunchPermissionCopy.NOT_NOW,
        onConfirm = {
            onContinue()
            refresh()
        },
        onDismiss = {
            when (step) {
                LaunchPermissionStep.NOTIFICATIONS -> skippedNotifications = true
                LaunchPermissionStep.EXACT_ALARM -> skippedExact = true
                LaunchPermissionStep.BATTERY -> skippedBattery = true
                LaunchPermissionStep.DONE -> Unit
            }
            refresh()
        },
    )
}

private fun exactAlarmsGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val manager = context.getSystemService(AlarmManager::class.java) ?: return true
    return manager.canScheduleExactAlarms()
}

private fun batteryUnrestricted(context: android.content.Context): Boolean {
    val manager = context.getSystemService(PowerManager::class.java) ?: return true
    return manager.isIgnoringBatteryOptimizations(context.packageName)
}
