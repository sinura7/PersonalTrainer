package com.sinura.personaltrainer.ui.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.LaunchPermissionCopy
import com.sinura.personaltrainer.domain.LaunchPermissionStep
import com.sinura.personaltrainer.domain.LaunchPermissions
import com.sinura.personaltrainer.timer.AndroidPhoneCapabilities
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent
import com.sinura.personaltrainer.timer.ignoreBatteryOptimizationSettingsIntent
import com.sinura.personaltrainer.timer.requestIgnoreBatteryOptimizationsIntent
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
    val probe = remember { AndroidPhoneCapabilities(context) }
    var snapshot by remember { mutableStateOf(probe.read()) }
    fun refresh() {
        snapshot = probe.read()
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

    val step = LaunchPermissions.nextStepWithSkips(
        snapshot = snapshot,
        skippedNotifications = skippedNotifications,
        skippedExact = skippedExact,
        skippedBattery = skippedBattery,
    )
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refresh()
        if (!granted) skippedNotifications = true
    }

    if (step == LaunchPermissionStep.DONE) return

    fun continueNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            skippedNotifications = true
        }
    }

    fun continueExactAlarm() {
        val intent = exactAlarmSettingsIntent(context.packageName)
        if (intent != null) {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        skippedExact = true
    }

    fun continueBattery() {
        runCatching {
            context.startActivity(
                requestIgnoreBatteryOptimizationsIntent(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                context.startActivity(ignoreBatteryOptimizationSettingsIntent())
            }
        }
        skippedBattery = true
    }

    val title: String
    val body: String
    val onContinue: () -> Unit
    when (step) {
        LaunchPermissionStep.NOTIFICATIONS -> {
            title = LaunchPermissionCopy.NOTIFICATIONS_TITLE
            body = LaunchPermissionCopy.NOTIFICATIONS_BODY
            onContinue = ::continueNotifications
        }
        LaunchPermissionStep.EXACT_ALARM -> {
            title = LaunchPermissionCopy.EXACT_TITLE
            body = LaunchPermissionCopy.EXACT_BODY
            onContinue = ::continueExactAlarm
        }
        LaunchPermissionStep.BATTERY -> {
            title = LaunchPermissionCopy.BATTERY_TITLE
            body = LaunchPermissionCopy.BATTERY_BODY
            onContinue = ::continueBattery
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
