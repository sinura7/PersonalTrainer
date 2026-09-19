package com.sinura.personaltrainer.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Waiting for a local read. Ghost cards, not a spinner (D-08).
 *
 * The delay lives on [ScreenSkeleton]: an unconditional skeleton would
 * flash for a frame on every navigation, which is worse than nothing.
 */
@Composable
fun ScreenLoading(
    modifier: Modifier = Modifier,
    cards: Int = 3,
) {
    ScreenSkeleton(modifier = modifier.fillMaxSize(), cards = cards)
}
