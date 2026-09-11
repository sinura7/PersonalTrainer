package com.sinura.personaltrainer.ui.workout


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Asks for POST_NOTIFICATIONS once, then reports whether rest notifications can actually
 * be shown.
 *
 * The result used to be discarded. On Android 13+ a denial silently removes BOTH off-screen
 * rest surfaces — the countdown and the "Rest done" alert — so a pocketed phone shows nothing
 * at all, with no way back: after two denials the system dialog stops appearing entirely.
 * The system dialog has no gym why, so an in-app sentence runs first. Continue launches
 * the permission prompt; Not now leaves the compact recovery row as the way back.
 * The returned flag drives that row (deep link to app notification settings) and
 * re-checks on every resume so it disappears the moment the user grants.
 */
@Composable
internal fun rememberRestNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var showWhy by rememberSaveable { mutableStateOf(false) }
    var decided by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !decided) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                showWhy = true
            }
        }
    }

    if (showWhy) {
        ConfirmActionDialog(
            title = RestNotificationCopy.TITLE,
            body = RestNotificationCopy.SENTENCE,
            confirmLabel = RestNotificationCopy.CONTINUE,
            dismissLabel = RestNotificationCopy.NOT_NOW,
            onConfirm = {
                decided = true
                showWhy = false
                if (Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                decided = true
                showWhy = false
            },
        )
    }

    // Returning from system settings is a resume, not a recomposition — re-read there.
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

/**
 * Persistent recovery after the one explanation. One identity line and one
 * act — not a viewport-dominating banner (FND-015).
 */
@Composable
internal fun RestNotificationRecoveryRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InstrumentRow(
        title = RestNotificationCopy.RECOVERY_TITLE,
        modifier = modifier.testTag(WorkoutTestTags.NOTIF_RECOVERY),
        onClick = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (thrown: Exception) {
                AppLog.w(TAG, "App notification settings unavailable; falling back", thrown)
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        trailing = {
            Text(
                RestNotificationCopy.RECOVERY_ACTION,
                style = InstrumentType.bodyStrong,
                color = Volt,
                maxLines = 1,
            )
        },
    )
}

private const val TAG = "PT/RestNotificationGate"
