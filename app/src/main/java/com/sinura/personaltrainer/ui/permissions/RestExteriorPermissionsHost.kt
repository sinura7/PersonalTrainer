package com.sinura.personaltrainer.ui.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.PhoneCapability
import com.sinura.personaltrainer.domain.RestExteriorPermissionCopy
import com.sinura.personaltrainer.domain.RestExteriorPermissions
import com.sinura.personaltrainer.domain.PermissionsCopy
import com.sinura.personaltrainer.timer.AndroidPhoneCapabilities
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent
import com.sinura.personaltrainer.timer.overlaySettingsIntent
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.reminders.openAppNotificationSettings

/**
 * Blocks on every resume while rest-exterior permissions are missing.
 */
@Composable
fun RestExteriorPermissionsHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val probe = remember { AndroidPhoneCapabilities(context) }
    var snapshot by remember { mutableStateOf(probe.read()) }
    fun refresh() {
        snapshot = probe.read()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val missing = RestExteriorPermissions.missing(snapshot)
    if (missing.isEmpty()) return

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refresh()
    }

    fun openFix(capability: PhoneCapability) {
        if (capability == PhoneCapability.NOTIFICATIONS) {
            if (Build.VERSION.SDK_INT >= 33 && !snapshot.postNotificationsGranted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings(context)
            }
        } else if (capability == PhoneCapability.DISPLAY_OVERLAY) {
            context.startActivity(overlaySettingsIntent(context.packageName))
        } else if (capability == PhoneCapability.EXACT_REST_ALARM) {
            exactAlarmSettingsIntent(context.packageName)?.let { intent ->
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        refresh()
    }

    val bodyLines = missing.joinToString("\n\n") { capability ->
        "• ${PermissionsCopy.title(capability)}: ${PermissionsCopy.why(capability)}"
    }

    ConfirmActionDialog(
        title = RestExteriorPermissionCopy.BLOCKING_TITLE,
        body = "${RestExteriorPermissionCopy.BLOCKING_LEAD}\n\n$bodyLines\n\n${PermissionsCopy.MANAGE_IN_SETTINGS}",
        confirmLabel = RestExteriorPermissionCopy.OPEN_SETTINGS,
        dismissLabel = RestExteriorPermissionCopy.CHECK_AGAIN,
        onConfirm = { openFix(missing.first()) },
        onDismiss = { refresh() },
    )
}
