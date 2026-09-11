package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import kotlinx.coroutines.delay

@Composable
fun EmptyState(
    title: String,
    body: String,
    scene: EmptyScene,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    actionTag: String? = null,
    actionEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        EmptyIllustration(
            scene = scene,
            size = if (compact) EmptyArtCompactSize else EmptyArtSize,
        )
        Text(
            title,
            style = if (compact) InstrumentType.title else InstrumentType.display,
            color = TextPrimary,
        )
        Text(body, style = InstrumentType.body, color = TextSecondary)
        if (actionLabel != null && onAction != null) {
            val actionModifier =
                if (actionTag != null) Modifier.testTag(actionTag) else Modifier
            if (compact) {
                TextButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = actionModifier,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        actionLabel,
                        style = InstrumentType.bodyStrong,
                        color = if (actionEnabled) Volt else TextDisabled,
                    )
                }
            } else {
                PrimaryGymButton(
                    text = actionLabel,
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = actionModifier,
                )
            }
        }
    }
}

/**
 * A spinner that only appears if the wait is real.
 *
 * Every screen in this app reads from a local database, where a query resolves in single
 * digit milliseconds — so an unconditional spinner exists just long enough to flash for a
 * frame or two on every single navigation, which is worse than showing nothing at all.
 * Below the threshold the screen simply stays empty and the content arrives.
 */
@Composable
fun ScreenLoading(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        visible = true
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) {
            CircularProgressIndicator(color = Volt, strokeWidth = SPINNER_STROKE)
        }
    }
}

private val SPINNER_STROKE = 3.dp
private const val SPINNER_DELAY_MS = 250L
