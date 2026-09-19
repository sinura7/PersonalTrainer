package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The next set, as the deterministic coach calls it ([SetMicroRec], ADR-008).
 *
 * Numbers on the left, the change and its one-line reason on the right, Why and Apply
 * above. Apply copies the numbers into the entry and nothing else — it never logs. Once
 * the entry already matches, the control says Applied and stands down, so the same
 * suggestion cannot be "taken" twice. Why opens the rule trace, the honest reasoning the
 * engine actually ran, not a paragraph written for the card.
 */
@Composable
internal fun NextSetRecommendation(
    rec: SetMicroRec,
    loadClass: LoadClass,
    unit: WeightUnit,
    applied: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!SetMicroRecCopy.visibleOnEntry(rec)) return
    val view = LocalView.current
    var showWhy by rememberSaveable(rec.reasonCode, rec.nextWeightKg, rec.nextReps, rec.nextRpe) {
        mutableStateOf(false)
    }
    val canUse = rec.showApply && !rec.previewOnly
    val numbers = SetMicroRecCopy.numbers(rec, loadClass, unit)
    val delta = SetMicroRecCopy.deltaLine(rec, loadClass, unit)
    val reason = SetMicroRecCopy.ruleLine(rec.reasonCode)
    val target = rec.nextRpe?.let { "Target RPE $it" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.NEXT_SET),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Kicker(text = NEXT_SET_KICKER, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { showWhy = true },
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(WorkoutTestTags.MICRO_REC_WHY),
            ) {
                Text("Why?", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
            if (canUse) {
                QuietButton(
                    text = if (applied) "Applied" else "Apply",
                    onClick = {
                        Haptics.warn(view)
                        onApply()
                    },
                    modifier = Modifier.testTag(WorkoutTestTags.MICRO_REC_APPLY),
                    enabled = enabled && !applied,
                    leading = if (applied) TemperIcons.Check else null,
                    accent = applied,
                    spoken = if (applied) "Suggestion applied, $numbers" else "Apply suggestion, $numbers",
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                numbers,
                modifier = Modifier
                    .testTag(WorkoutTestTags.MICRO_REC)
                    .semantics { contentDescription = "Next set, $numbers" },
                style = InstrumentType.numeralMd,
                color = TextPrimary,
                maxLines = 2,
            )
            Box(
                modifier = Modifier
                    .width(Metrics.hairline)
                    .fillMaxHeight()
                    .padding(vertical = Metrics.space1)
                    .background(Hairline),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                if (delta != null) {
                    Text(delta, style = InstrumentType.bodyStrong, color = Volt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(reason, target).joinToString(" · "),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SetMicroRecCopy.caption(rec)?.let { caption ->
            Text(caption, style = InstrumentType.caption, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (showWhy) {
        ConfirmActionDialog(
            title = "Why this set",
            body = SetMicroRecCopy.whyLines(rec).joinToString("\n"),
            confirmLabel = if (canUse && !applied) SetMicroRecCopy.USE_SUGGESTION else SetMicroRecCopy.KEEP_MY_NUMBERS,
            dismissLabel = if (canUse && !applied) SetMicroRecCopy.KEEP_MY_NUMBERS else null,
            onConfirm = {
                if (canUse && !applied) {
                    Haptics.warn(view)
                    onApply()
                }
                showWhy = false
            },
            onDismiss = { showWhy = false },
        )
    }
}

private const val NEXT_SET_KICKER = "Next set"
