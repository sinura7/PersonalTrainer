package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Packet 4 floor field mark. The drawn path *is* the label — it does not
 * sit beside a kicker that already said "weight". TalkBack still hears
 * [spoken] when the neighbouring control does not already name the field.
 */
@Composable
fun FloorFieldGlyph(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = TextSecondary,
    spoken: String? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = spoken,
        tint = tint,
        modifier = modifier.size(Metrics.icon),
    )
}
