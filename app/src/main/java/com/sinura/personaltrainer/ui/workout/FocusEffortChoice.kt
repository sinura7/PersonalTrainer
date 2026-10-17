package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import androidx.compose.foundation.background

@Composable
internal fun FocusEffortChoice(
    value: Int,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .widthIn(min = Metrics.touchMin)
            .heightIn(min = Metrics.touchMin)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected && enabled) VoltDim else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (selected) Metrics.emphasisBorder else Metrics.hairline,
                color = if (selected && enabled) Volt else Hairline,
                shape = RoundedCornerShape(Radius.sm),
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = {
                Haptics.tick(view)
                onSelect()
            })
            .testTag(WorkoutTestTags.rpeChoice(value))
            .semantics { contentDescription = RpeCopy.spoken(value = value, selected = selected) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            modifier = Modifier.clearAndSetSemantics { },
            style = InstrumentType.bodyStrong,
            color = if (!enabled) TextDisabled else if (selected) TextPrimary else TextSecondary,
        )
    }
}
