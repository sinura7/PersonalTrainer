package com.sinura.personaltrainer.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.CapabilityState
import com.sinura.personaltrainer.domain.PermissionsCopy
import com.sinura.personaltrainer.domain.PhoneCapability
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot
import com.sinura.personaltrainer.domain.PhoneCapabilityStatus
import com.sinura.personaltrainer.timer.RestTimerOverlayController
import com.sinura.personaltrainer.timer.exactAlarmSettingsIntent
import com.sinura.personaltrainer.timer.fullScreenIntentSettingsIntent
import com.sinura.personaltrainer.timer.overlaySettingsIntent
import com.sinura.personaltrainer.timer.ignoreBatteryOptimizationSettingsIntent
import com.sinura.personaltrainer.timer.requestIgnoreBatteryOptimizationsIntent
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.reminders.openAppNotificationSettings
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.Warn

@Composable
internal fun PermissionsSection(
    snapshot: PhoneCapabilitySnapshot,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE")
    val unusedRefresh = refreshTick
    var askedPostNotifications by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        openAppNotificationSettings(context)
    }

    Column(
        modifier = modifier.testTag(SettingsTags.PERMISSIONS),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        snapshot.states.forEach { status ->
            PermissionCapabilityCard(
                status = status,
                onFix = {
                    when (status.capability) {
                        PhoneCapability.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= 33 && !askedPostNotifications &&
                                snapshot.postNotificationsGranted.not()
                            ) {
                                askedPostNotifications = true
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        }
                        PhoneCapability.EXACT_REST_ALARM -> {
                            exactAlarmSettingsIntent(context.packageName)?.let { intent ->
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        PhoneCapability.LOCK_SCREEN_ALERT -> {
                            fullScreenIntentSettingsIntent(context.packageName)?.let { intent ->
                                context.startActivity(intent)
                            }
                        }
                        PhoneCapability.DISPLAY_OVERLAY -> {
                            context.startActivity(overlaySettingsIntent(context.packageName))
                        }
                        PhoneCapability.BATTERY -> {
                            runCatching {
                                context.startActivity(
                                    requestIgnoreBatteryOptimizationsIntent(context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure {
                                context.startActivity(ignoreBatteryOptimizationSettingsIntent())
                            }
                        }
                        PhoneCapability.VIBRATION -> Unit
                    }
                },
            )
        }
        Text(
            PermissionsCopy.LOCK_WIDGET_SETUP,
            style = InstrumentType.caption,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = Metrics.space1),
        )
        Text(
            PermissionsCopy.INSTALL_TIME_LINE,
            style = InstrumentType.caption,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = Metrics.space1),
        )
    }
}

@Composable
private fun PermissionCapabilityCard(
    status: PhoneCapabilityStatus,
    onFix: () -> Unit,
) {
    GymCard(modifier = Modifier.testTag(SettingsTags.permissionRow(status.capability))) {
        Column(
            modifier = Modifier.padding(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    PermissionsCopy.title(status.capability),
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                PermissionStateChip(state = status.state)
            }
            Text(
                PermissionsCopy.why(status.capability),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            if (status.capability == PhoneCapability.DISPLAY_OVERLAY &&
                status.state == CapabilityState.GRANTED
            ) {
                RestTimerOverlayController.lastFailureReason?.let { reason ->
                    Text(
                        "Overlay still failed: $reason",
                        style = InstrumentType.caption,
                        color = Warn,
                    )
                }
            }
            if (status.state == CapabilityState.MISSING) {
                SecondaryGymButton(
                    text = PermissionsCopy.fixLabel(status.capability),
                    onClick = onFix,
                    height = Metrics.touchMin,
                    modifier = Modifier.testTag(SettingsTags.permissionFix(status.capability)),
                )
            }
        }
    }
}

@Composable
private fun PermissionStateChip(state: CapabilityState) {
    val label = PermissionsCopy.stateLabel(state)
    val tint = when (state) {
        CapabilityState.GRANTED -> Volt
        CapabilityState.MISSING -> Warn
        CapabilityState.NOT_ON_THIS_PHONE -> TextSecondary
    }
    val icon = when (state) {
        CapabilityState.GRANTED -> TemperIcons.Check
        CapabilityState.MISSING -> TemperIcons.More
        CapabilityState.NOT_ON_THIS_PHONE -> TemperIcons.Chevron
    }
    InstrumentChip(
        label = label,
        selected = state == CapabilityState.GRANTED,
        onClick = {},
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        },
        modifier = Modifier.testTag(SettingsTags.permissionChip(state)),
    )
}
