package com.sinura.personaltrainer.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Warn
import com.sinura.personaltrainer.update.DebugUpdateInstall
import com.sinura.personaltrainer.update.DebugUpdatePort
import com.sinura.personaltrainer.update.DebugUpdateUi
import com.sinura.personaltrainer.update.DisabledDebugUpdate

object DebugUpdateTags {
    const val BANNER = "debug-update-banner"
    const val PROGRESS = "debug-update-progress"
}

@Composable
internal fun rememberDebugUpdatePort(): DebugUpdatePort {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as? PersonalTrainerApp)?.container?.debugUpdate
            ?: DisabledDebugUpdate
    }
}

@Composable
internal fun DebugUpdateBanner(
    ui: DebugUpdateUi,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(DebugUpdateTags.BANNER),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        GymNoticeBanner(
            title = DebugUpdateCopy.TITLE,
            body = debugUpdateBody(ui),
            actionLabel = debugUpdateAction(ui),
            onAction = onInstall.takeIf { debugUpdateAction(ui) != null },
            onDismiss = onDismiss,
        )
        if (ui.install == DebugUpdateInstall.Downloading) {
            DownloadProgressRule(ui.downloadPercent)
        }
    }
}

internal fun debugUpdateBody(ui: DebugUpdateUi): String = when (ui.install) {
    DebugUpdateInstall.Idle -> DebugUpdateCopy.BANNER_BODY
    DebugUpdateInstall.NeedsPermission -> DebugUpdateCopy.NEEDS_PERMISSION
    DebugUpdateInstall.Downloading -> DebugUpdateCopy.downloading(ui.downloadPercent)
    DebugUpdateInstall.Installing -> DebugUpdateCopy.INSTALLING
    DebugUpdateInstall.Failed -> DebugUpdateCopy.FAILED
}

internal fun debugUpdateAction(ui: DebugUpdateUi): String? = when (ui.install) {
    DebugUpdateInstall.Idle -> DebugUpdateCopy.ACTION
    DebugUpdateInstall.NeedsPermission -> DebugUpdateCopy.ACTION_ALLOW
    DebugUpdateInstall.Failed -> DebugUpdateCopy.ACTION_RETRY
    DebugUpdateInstall.Downloading, DebugUpdateInstall.Installing -> null
}

internal fun debugUpdateSettingsSummary(ui: DebugUpdateUi): String? {
    val offer = ui.offer ?: return null
    return when (ui.install) {
        DebugUpdateInstall.Idle -> DebugUpdateCopy.settingsSummary(offer.versionCode)
        DebugUpdateInstall.NeedsPermission -> DebugUpdateCopy.NEEDS_PERMISSION
        DebugUpdateInstall.Downloading -> DebugUpdateCopy.downloading(ui.downloadPercent)
        DebugUpdateInstall.Installing -> DebugUpdateCopy.INSTALLING
        DebugUpdateInstall.Failed -> DebugUpdateCopy.FAILED
    }
}

@Composable
private fun DownloadProgressRule(percent: Int?) {
    val fill = ((percent ?: 0) / 100f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Metrics.space1)
            .clip(RoundedCornerShape(Radius.xs))
            .background(Hairline)
            .testTag(DebugUpdateTags.PROGRESS),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (percent == null) 0.15f else fill)
                .fillMaxHeight()
                .background(Warn),
        )
    }
}
