package com.sinura.personaltrainer.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.update.DebugUpdateInstall

/**
 * Temper Debug only. Blocks on every resume while a newer live drop exists until
 * the owner starts the in-app update (or finishes installing).
 */
@Composable
fun DebugUpdateRequiredHost() {
    if (!BuildConfig.DEBUG) return
    val port = rememberDebugUpdatePort()
    val ui by port.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) port.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!ui.showRequiredPrompt || ui.offer == null) return
    val offer = ui.offer!!
    val body = when (ui.install) {
        DebugUpdateInstall.Idle -> DebugUpdateCopy.requiredBody(offer.versionCode)
        DebugUpdateInstall.NeedsPermission -> DebugUpdateCopy.NEEDS_PERMISSION
        DebugUpdateInstall.Downloading -> DebugUpdateCopy.downloading(ui.downloadPercent)
        DebugUpdateInstall.Installing -> DebugUpdateCopy.INSTALLING
        DebugUpdateInstall.Failed -> DebugUpdateCopy.FAILED
    }
    val action = debugUpdateAction(ui)
    ConfirmActionDialog(
        title = DebugUpdateCopy.REQUIRED_TITLE,
        body = body,
        confirmLabel = action ?: DebugUpdateCopy.ACTION,
        dismissLabel = null,
        onConfirm = { port.install() },
        onDismiss = {},
    )
}
