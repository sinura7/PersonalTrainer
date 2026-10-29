package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer

enum class SetPillState {
    Done,
    Now,
    Next,
    WarmUp,
    Extra,
    Selected,
}

@Composable
fun SetPill(
    state: SetPillState,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val labelsOnly = fontScale >= 1.3f
    val shape = RoundedCornerShape(Radius.xs)
    val borderColor = when (state) {
        SetPillState.Done -> Color.Transparent
        SetPillState.Now -> TextPrimary
        SetPillState.Next -> Color.Transparent
        SetPillState.WarmUp -> Color.Transparent
        SetPillState.Extra -> TextTertiary
        SetPillState.Selected -> Volt
    }
    val borderWidth = when (state) {
        SetPillState.Done -> 0.dp
        SetPillState.Now -> Metrics.emphasisBorder
        SetPillState.Next -> 0.dp
        SetPillState.WarmUp -> 0.dp
        SetPillState.Extra -> Metrics.hairline
        SetPillState.Selected -> Metrics.emphasisBorder
    }
    val background = when (state) {
        SetPillState.Done -> VoltContainer
        SetPillState.Now -> Surface2
        SetPillState.Next -> Surface2
        SetPillState.WarmUp -> Surface2
        SetPillState.Extra -> Surface2
        SetPillState.Selected -> Surface2
    }
    val labelColor = when (state) {
        SetPillState.Done -> Volt
        SetPillState.Now -> TextPrimary
        SetPillState.Next -> TextSecondary
        SetPillState.WarmUp -> TextTertiary
        SetPillState.Extra -> TextPrimary
        SetPillState.Selected -> TextPrimary
    }
    val valueColor = when (state) {
        SetPillState.Done -> Volt
        SetPillState.Now -> TextPrimary
        SetPillState.Next -> TextPrimary
        SetPillState.WarmUp -> TextPrimary
        SetPillState.Extra -> TextPrimary
        SetPillState.Selected -> TextPrimary
    }
    Column(
        modifier = modifier
            .widthIn(min = Metrics.touchMin + Metrics.space1)
            .heightIn(min = Metrics.touchMin)
            .clip(shape)
            .then(
                if (state == SetPillState.Extra) {
                    Modifier.border(
                        width = borderWidth,
                        color = borderColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                        .background(background)
                        .border(borderWidth, borderColor, shape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1)
            .semantics {
                this.contentDescription = contentDescription ?: buildString {
                    append(label)
                    if (!value.isNullOrBlank()) append(" $value")
                }
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = InstrumentType.kicker,
            color = labelColor,
            maxLines = 1,
        )
        if (!labelsOnly && !value.isNullOrBlank()) {
            Text(
                text = value,
                style = InstrumentType.numeralSm,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
fun SetPillRow(
    pills: List<SetPillModel>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Metrics.gutter),
) {
    LazyRow(
        modifier = modifier.testTag("workout-set-pill-row"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        items(pills, key = { pill -> pill.key }) { pill ->
            SetPill(
                state = pill.state,
                label = pill.label,
                value = pill.value,
                onClick = pill.onClick,
                enabled = pill.enabled,
                contentDescription = pill.contentDescription,
                modifier = Modifier.testTag(pill.testTag),
            )
        }
    }
}

data class SetPillModel(
    val key: String,
    val state: SetPillState,
    val label: String,
    val value: String?,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val testTag: String = "workout-set-pill-$key",
    val contentDescription: String? = null,
)
