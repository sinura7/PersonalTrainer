package com.sinura.personaltrainer.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sinura.personaltrainer.domain.PhoneCapabilityPort
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot

/** String permission id so minSdk 26 does not reference [android.Manifest.permission.POST_NOTIFICATIONS]. */
private const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

class AndroidPhoneCapabilities(
    context: Context,
) : PhoneCapabilityPort {
    private val appContext = context.applicationContext

    override fun read(): PhoneCapabilitySnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val restDoneChannelEnabled = RestTimerNotifications.isRestDoneChannelEnabled(appContext)
        val postNotificationsGranted = if (Build.VERSION.SDK_INT < 33) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                PERMISSION_POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        val canScheduleExactAlarms = if (sdkInt < 31 || Build.VERSION.SDK_INT < 31) {
            true
        } else {
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            alarmManager?.canScheduleExactAlarms() == true
        }
        val canUseFullScreenIntent = RestTimerNotifications.canUseFullScreenIntent(appContext)
        val canDrawOverlays = RestTimerOverlayController.canDrawOverlays(appContext)
        val batteryUnrestricted = appContext.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        val hasVibrator = if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)?.hasVibrator() == true
        }
        return PhoneCapabilitySnapshot(
            sdkInt = sdkInt,
            notificationsEnabled = notificationsEnabled,
            restDoneChannelEnabled = restDoneChannelEnabled,
            postNotificationsGranted = postNotificationsGranted,
            canScheduleExactAlarms = canScheduleExactAlarms,
            canUseFullScreenIntent = canUseFullScreenIntent,
            canDrawOverlays = canDrawOverlays,
            batteryUnrestricted = batteryUnrestricted,
            hasVibrator = hasVibrator,
        )
    }
}

/**
 * Personal sideload / Temper Debug only. Play store builds must drop or justify this path
 * (see docs/COMMERCIAL_BOUNDARY.md).
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizationsIntent(packageName: String): Intent =
    Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }

fun ignoreBatteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** API 34+. Null below. Inlined action string for minSdk 26. */
fun fullScreenIntentSettingsIntent(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent? {
    if (sdkInt < 34 || Build.VERSION.SDK_INT < 34) return null
    return Intent(ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

fun overlaySettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
    "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"

private const val ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT =
    "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
