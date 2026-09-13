package com.sinura.personaltrainer.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.ui.components.GymNoticeBanner
import com.sinura.personaltrainer.update.DebugUpdatePort
import com.sinura.personaltrainer.update.DisabledDebugUpdate

object DebugUpdateTags {
    const val BANNER = "debug-update-banner"
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
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GymNoticeBanner(
        title = DebugUpdateCopy.TITLE,
        body = DebugUpdateCopy.BANNER_BODY,
        actionLabel = DebugUpdateCopy.ACTION,
        onAction = onOpen,
        onDismiss = onDismiss,
        modifier = modifier.testTag(DebugUpdateTags.BANNER),
    )
}
