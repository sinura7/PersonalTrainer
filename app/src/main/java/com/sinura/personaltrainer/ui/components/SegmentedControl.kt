package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SectionEdge
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
data class SegmentOption(
    val key: String,
    val label: String,
)

@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.segmentTarget)
            .selectableGroup()
            .clip(trackShape)
            .background(Surface1)
            .border(Metrics.hairline, SectionEdge, trackShape)
            .padding(Metrics.segmentTrackInset),
    ) {
        val view = LocalView.current
        options.forEach { option ->
            val selected = option.key == selectedKey
            val segmentShape = RoundedCornerShape(Radius.xs)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(segmentShape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(Surface3)
                                .border(Metrics.hairline, HairlineStrong, segmentShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab) {
                        Haptics.tickLight(view)
                        onSelect(option.key)
                    }
                    .testTag("segment-${option.key}")
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = InstrumentType.bodyStrong,
                    color = if (selected) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Metrics.space1),
                )
            }
        }
    }
}
