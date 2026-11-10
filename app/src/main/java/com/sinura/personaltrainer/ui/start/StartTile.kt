package com.sinura.personaltrainer.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
enum class StartTileKind { SessionRoutine, Empty, Cardio, WarmUp, Stretch }

enum class StartTileState { Ready, Waiting }

@Composable
fun StartTile(
    kind: StartTileKind,
    title: String,
    caption: String,
    state: StartTileState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onWaitingTap: (() -> Unit)? = null,
    testTag: String,
) {
    val shape = RoundedCornerShape(Radius.md)
    val glyphInk = when (kind) {
        StartTileKind.WarmUp, StartTileKind.Stretch -> RestCyan
        else -> TextPrimary
    }
    val view = LocalView.current
    val waiting = state == StartTileState.Waiting
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.startTileMin)
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, SectionEdge, shape)
            .clickable(role = Role.Button) {
                if (waiting) {
                    Haptics.reject(view)
                    onWaitingTap?.invoke()
                } else {
                    Haptics.tickLight(view)
                    onClick()
                }
            }
            .testTag(testTag)
            .padding(horizontal = Metrics.startTilePaddingH, vertical = Metrics.startTilePaddingV),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Box(
            modifier = Modifier
                .size(Metrics.tileIcon)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Surface3)
                .border(Metrics.hairline, HairlineStrong, RoundedCornerShape(Radius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Kicker(
                startTileGlyph(kind),
                color = glyphInk,
                asHeading = false,
            )
        }
        Text(
            title,
            style = InstrumentType.title,
            color = if (waiting) TextSecondary else TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            caption,
            style = InstrumentType.caption,
            color = TextTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun startTileGlyph(kind: StartTileKind): String = when (kind) {
    StartTileKind.SessionRoutine -> "RT"
    StartTileKind.Empty -> "EM"
    StartTileKind.Cardio -> "CD"
    StartTileKind.WarmUp -> "WU"
    StartTileKind.Stretch -> "ST"
}
