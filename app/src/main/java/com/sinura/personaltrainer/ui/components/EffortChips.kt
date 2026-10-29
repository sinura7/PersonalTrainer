package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltContainer
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role as SemRole

@Composable
fun EffortChips(
    selected: Int?,
    prefilled: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    last: Int? = null,
    /** Coach next-set effort hint when nothing is selected yet (same copy as [RpeCopy.lastHint]). */
    recommended: Int? = null,
    enabled: Boolean = true,
    showError: Boolean = false,
    hiddenForWarmup: Boolean = false,
    warmupCaption: String? = null,
    entryReady: Boolean = true,
) {
    if (hiddenForWarmup) {
        Text(
            text = warmupCaption ?: stringResource(R.string.live_warmup_caption),
            style = InstrumentType.caption,
            color = TextSecondary,
            modifier = modifier
                .padding(vertical = Metrics.space1)
                .testTag(WorkoutTestTags.RPE_WARMUP_REASON),
        )
        return
    }
    if (!entryReady) {
        Text(
            text = "Set weight and reps before choosing effort.",
            style = InstrumentType.caption,
            color = com.sinura.personaltrainer.ui.theme.TextTertiary,
            modifier = modifier
                .padding(vertical = Metrics.space1)
                .testTag(WorkoutTestTags.RPE_WAIT_ENTRY),
        )
        return
    }
    val view = LocalView.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val twoRows = fontScale >= 2f
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                text = if (showError) stringResource(R.string.live_effort_missing) else stringResource(R.string.live_effort_label),
                style = InstrumentType.bodyStrong,
                color = if (showError) Danger else TextSecondary,
            )
            val hintRpe = when {
                selected != null || prefilled != null -> null
                recommended != null -> recommended
                last != null -> last
                else -> null
            }
            hintRpe?.let { rpe ->
                Text(
                    text = "Last: $rpe",
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    modifier = Modifier.testTag(WorkoutTestTags.RPE_LAST_HINT),
                )
            }
            Text(
                text = stringResource(R.string.live_effort_scale),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        val rows = if (twoRows) listOf(listOf(6, 7, 8), listOf(9, 10)) else listOf(listOf(6, 7, 8, 9, 10))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag(WorkoutTestTags.RPE_TRACK),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            rows.forEach { row ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    row.forEach { value ->
                        EffortChip(
                            value = value,
                            selected = selected == value,
                            prefilled = prefilled == value && selected == null,
                            enabled = enabled,
                            onSelect = {
                                Haptics.tick(view)
                                onSelect(value)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(WorkoutTestTags.rpeChoice(value)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EffortChip(
    value: Int,
    selected: Boolean,
    prefilled: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val shape = RoundedCornerShape(Radius.sm)
    Box(
        modifier = modifier
            .widthIn(min = Metrics.touchMin)
            .heightIn(min = if (fontScale >= 1.3f) Metrics.control else Metrics.control)
            .clip(shape)
            .background(
                when {
                    selected -> VoltContainer
                    else -> Surface2
                },
            )
            .then(
                when {
                    selected -> Modifier.border(Metrics.emphasisBorder, Volt, shape)
                    prefilled -> Modifier.drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Volt,
                            cornerRadius = CornerRadius(Radius.sm.toPx()),
                            style = Stroke(
                                width = Metrics.emphasisBorder.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                            ),
                        )
                    }
                    else -> Modifier.border(Metrics.hairline, Hairline, shape)
                },
            )
            .selectable(selected = selected, enabled = enabled, role = SemRole.RadioButton, onClick = onSelect)
            .semantics {
                contentDescription = when {
                    selected -> "Effort $value selected"
                    prefilled -> "Effort $value prefilled"
                    else -> "Effort $value"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            style = if (selected) InstrumentType.bodyStrong else InstrumentType.body,
            color = when {
                !enabled -> TextSecondary
                selected || prefilled -> Volt
                else -> TextPrimary
            },
            textAlign = TextAlign.Center,
        )
    }
}
