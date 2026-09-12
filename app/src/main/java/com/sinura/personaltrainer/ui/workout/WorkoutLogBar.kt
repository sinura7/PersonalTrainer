package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogBarCopy
import com.sinura.personaltrainer.domain.RpeCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The one action that matters, and the values it is about to commit.
 *
 * The button is pinned while the entry panel scrolls, so after reviewing the set list a
 * lifter could face a full-width commit button whose payload was nowhere on screen. Echoing
 * the draft in the label means the tap is never blind. W-11: the Warm-up chip changes the
 * verb (`Log warm-up` vs `Log set`) through [LogBarCopy.commit].
 *
 * Scaffold's bottomBar draws edge-to-edge. The tab bar is gone on this route, so this
 * dock owns the system-nav inset the same way the tab bar and live bar already do —
 * otherwise Log sits under the three-button nav / gesture pill.
 */
@Composable
internal fun LogBar(
    editing: Boolean,
    logging: Boolean,
    error: String?,
    draftLabel: String,
    warmup: Boolean,
    microRec: SetMicroRec?,
    loadClass: LoadClass,
    unit: WeightUnit,
    showNext: Boolean,
    onLog: () -> Unit,
    onNext: () -> Unit,
    onCancelEdit: () -> Unit,
    onApplyMicroRec: () -> Unit,
) {
    PinnedDock(
        prelude = {
            error?.let {
                Text(it, style = InstrumentType.body, color = Danger)
            }
            if (editing) {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = Metrics.touchMin),
                ) {
                    Text("Cancel edit", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
            microRec?.let { rec ->
                MicroRecLine(
                    rec = rec,
                    loadClass = loadClass,
                    unit = unit,
                    onApply = onApplyMicroRec,
                )
            }
        },
        volt = {
            val nextAct = showNext && !editing
            PrimaryGymButton(
                text = LogBarCopy.commit(
                    editing = editing,
                    next = showNext,
                    warmup = warmup,
                    draftLabel = draftLabel,
                ),
                onClick = if (nextAct) onNext else onLog,
                enabled = !logging,
                modifier = Modifier.testTag(
                    if (nextAct) WorkoutTestTags.NEXT else WorkoutTestTags.LOG_SET,
                ),
                height = Metrics.commit,
                hapticFeedback = nextAct || editing,
            )
        },
    )
}

@Composable
internal fun MicroRecLine(
    rec: SetMicroRec,
    loadClass: LoadClass,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    var showWhy by rememberSaveable(rec.reasonCode, rec.nextWeightKg, rec.nextReps, rec.nextRpe) {
        mutableStateOf(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        SetMicroRecCopy.caption(rec)?.let { caption ->
            Text(
                caption,
                style = InstrumentType.caption,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(
                SetMicroRecCopy.line(rec, loadClass, unit),
                modifier = Modifier
                    .weight(1f)
                    .testTag(WorkoutTestTags.MICRO_REC),
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = { showWhy = true },
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(WorkoutTestTags.MICRO_REC_WHY),
            ) {
                Text(
                    "Why",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
            if (rec.showApply && !rec.previewOnly) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.MICRO_REC_APPLY),
                ) {
                    Text("Use", style = InstrumentType.bodyStrong, color = Volt)
                }
            }
        }
    }
    if (showWhy) {
        ConfirmActionDialog(
            title = "Why",
            body = SetMicroRecCopy.whyLines(rec).joinToString("\n"),
            confirmLabel = "OK",
            onConfirm = { showWhy = false },
            onDismiss = { showWhy = false },
            dismissLabel = null,
        )
    }
}

@Composable
internal fun SecondaryLogOptions(
    warmup: Boolean,
    rpe: Int?,
    onWarmup: (Boolean) -> Unit,
    onRpe: (Int?) -> Unit,
    recommendedRpe: Int? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "warmup") {
                InstrumentChip(
                    label = "Warm-up",
                    selected = warmup,
                    onClick = { onWarmup(!warmup) },
                )
            }
            item(key = "rpe-label") {
                Kicker("RPE", modifier = Modifier.padding(horizontal = Metrics.space2))
            }
            items((6..10).toList(), key = { it }) { value ->
                InstrumentChip(
                    label = value.toString(),
                    selected = rpe == value,
                    recommended = recommendedRpe == value && rpe != value,
                    onClick = { onRpe(if (rpe == value) null else value) },
                )
            }
        }
        Text(
            RpeCopy.blurb(recommendedRpe),
            style = InstrumentType.caption,
            color = TextTertiary,
        )
    }
}
