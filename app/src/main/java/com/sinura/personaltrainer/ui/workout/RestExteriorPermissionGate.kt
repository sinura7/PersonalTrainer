package com.sinura.personaltrainer.ui.workout

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.PhoneCapability
import com.sinura.personaltrainer.domain.PermissionsCopy
import com.sinura.personaltrainer.domain.RestExteriorPermissionCopy
import com.sinura.personaltrainer.domain.RestExteriorPermissions
import com.sinura.personaltrainer.timer.AndroidPhoneCapabilities
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent
import com.sinura.personaltrainer.timer.overlaySettingsIntent
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.reminders.openAppNotificationSettings

@Immutable
internal class RestExteriorAsk(val asked: Boolean?, val markAsked: () -> Unit)

internal val LocalRestExteriorAsk = compositionLocalOf {
    RestExteriorAsk(asked = true, markAsked = {})
}

internal val LocalRestCooldownActive = compositionLocalOf { false }

/**
 * Rest-exterior permissions (shade, overlay, exact alarm) asked in context on the workout
 * floor when rest is relevant — not as a root-level block on every app resume.
 *
 * Later / Not now still allows the in-app rest clock; only off-app surfaces need the grant.
 */
@Composable
internal fun RestExteriorPermissionGate(
    active: Boolean,
    cooldownActive: Boolean = LocalRestCooldownActive.current,
) {
    val context = LocalContext.current
    val ask = LocalRestExteriorAsk.current
    val probe = remember { AndroidPhoneCapabilities(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(probe.read()) }
    var show by rememberSaveable { mutableStateOf(false) }
    var decided by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, active) {
        if (!active) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = probe.read()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(active, cooldownActive, ask.asked, snapshot) {
        if (!active || cooldownActive || ask.asked == true || decided) {
            show = false
            return@LaunchedEffect
        }
        if (RestExteriorPermissions.missing(snapshot).isNotEmpty()) {
            show = true
        }
    }

    val missing = RestExteriorPermissions.missing(snapshot)
    if (!show || missing.isEmpty() || ask.asked == true) return

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        snapshot = probe.read()
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
        snapshot = probe.read()
    }

    val bodyLines = missing.joinToString("\n\n") { capability ->
        "• ${PermissionsCopy.title(capability)}: ${PermissionsCopy.why(capability)}"
    }

    ConfirmActionDialog(
        title = RestExteriorPermissionCopy.BLOCKING_TITLE,
        body = "${RestExteriorPermissionCopy.BLOCKING_LEAD}\n\n$bodyLines\n\n${PermissionsCopy.MANAGE_IN_SETTINGS}",
        confirmLabel = RestExteriorPermissionCopy.OPEN_SETTINGS,
        dismissLabel = RestExteriorPermissionCopy.LATER,
        onConfirm = {
            decided = true
            show = false
            ask.markAsked()
            openFix(missing.first())
        },
        onDismiss = {
            decided = true
            show = false
            ask.markAsked()
        },
    )
}
