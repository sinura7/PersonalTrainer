package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.ui.components.GymDialog
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * Optional effort for a working set: five equal choices, 6–10, with the ends named.
 *
 * Selected is a Volt outline on a dim Volt tint; a history recommendation is an outline
 * with the word "recommended" for TalkBack, never a selection. Tapping the selected
 * value again, or Clear, removes it. Warm-ups hide the track with the reason, not a
 * missing row. Help is one tap away and never sits on the floor as prose.
 */
@Composable
internal fun RpeSelector(
    enabled: Boolean,
    warmup: Boolean,
    rpe: Int?,
    recommendedRpe: Int?,
    onRpe: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Kicker(RpeCopy.LABEL)
            Box(
                modifier = Modifier
                    .size(Metrics.touchMin)
                    .clip(Radius.full)
                    .clickable(role = Role.Button, onClick = { helpOpen = true })
                    .testTag(WorkoutTestTags.RPE_HELPER)
                    .semantics { contentDescription = RpeCopy.HELP_SPOKEN },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(Metrics.helpMark)
                        .border(Metrics.hairline, TextSecondary, Radius.full),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = InstrumentType.caption, color = TextSecondary)
                }
            }
            Spacer(Modifier.weight(1f))
            if (!warmup && rpe != null) {
                TextButton(
                    enabled = enabled,
                    onClick = { onRpe(null) },
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.RPE_CLEAR),
                ) {
                    Text("Clear", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
        if (warmup) {
            Text(
                "Effort is recorded for working sets. Warm-ups leave RPE blank.",
                modifier = Modifier.testTag(WorkoutTestTags.RPE_WARMUP_REASON),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val optionWidth = measurer.measure("10", style = InstrumentType.bodyStrong).size.width +
                    with(density) { (Metrics.space2 * 2 + Metrics.space2).roundToPx() }
                val gaps = with(density) { (Metrics.space2 * 4).roundToPx() }
                val singleRow = optionWidth * RpeCopy.VALUES.count() + gaps <= with(density) { maxWidth.roundToPx() }
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().selectableGroup().testTag(WorkoutTestTags.RPE_TRACK),
                        maxItemsInEachRow = if (singleRow) RpeCopy.VALUES.count() else 3,
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        RpeCopy.VALUES.forEach { value ->
                            val selected = rpe == value
                            val recommended = recommendedRpe == value && !selected
                            InstrumentChip(
                                label = value.toString(),
                                selected = selected,
                                onClick = { onRpe(if (selected) null else value) },
                                modifier = Modifier.weight(1f).testTag(WorkoutTestTags.rpeChoice(value)),
                                recommended = recommended,
                                compact = true,
                                role = Role.RadioButton,
                                spoken = RpeCopy.spoken(value = value, selected = selected, recommended = recommended),
                                enabled = enabled,
                            )
                        }
                    }
                    // Under the track, whether it fits one row or wraps: the chosen value's
                    // meaning once there is one, the track's ends until then. One row either way.
                    val chosen = rpe?.let { RpeCopy.selectedLine(it) }
                    if (chosen != null) {
                        Text(
                            text = chosen,
                            // The chosen chip already says this aloud; the line is for the eyes,
                            // not a second stop for TalkBack on every set.
                            modifier = Modifier.testTag(WorkoutTestTags.RPE_MEANING).semantics { hideFromAccessibility() },
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(RpeCopy.EASY_END, style = InstrumentType.caption, color = TextTertiary)
                            Spacer(Modifier.weight(1f))
                            Text(RpeCopy.MAX_END, style = InstrumentType.caption, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
    if (helpOpen) {
        GymDialog(
            title = RpeCopy.HELP_TITLE,
            body = RpeCopy.helpBody(),
            confirmLabel = "Done",
            onConfirm = { helpOpen = false },
            onDismiss = { helpOpen = false },
            dismissLabel = null,
        )
    }
}
