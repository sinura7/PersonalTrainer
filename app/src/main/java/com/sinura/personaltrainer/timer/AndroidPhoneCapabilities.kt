package com.sinura.personaltrainer.timer

import android.Manifest
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

class AndroidPhoneCapabilities(
    context: Context,
) : PhoneCapabilityPort {
    private val appContext = context.applicationContext

    override fun read(): PhoneCapabilitySnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val restDoneChannelEnabled = RestTimerNotifications.isRestDoneChannelEnabled(appContext)
        val postNotificationsGranted = if (sdkInt >= 33) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val canScheduleExactAlarms = if (sdkInt < 31) {
            true
        } else {
            appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        }
        val canUseFullScreenIntent = RestTimerNotifications.canUseFullScreenIntent(appContext)
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
            batteryUnrestricted = batteryUnrestricted,
            hasVibrator = hasVibrator,
        )
    }
}

fun requestIgnoreBatteryOptimizationsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }

fun ignoreBatteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** API 34+. Null below. */
fun fullScreenIntentSettingsIntent(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent? {
    if (sdkInt < 34) return null
    return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
