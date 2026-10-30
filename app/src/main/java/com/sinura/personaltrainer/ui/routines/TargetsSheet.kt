package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.StepperButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.rememberFullSheetState

/** The values one Set targets sheet writes. [reps] holds seconds for a hold lift. */
data class TargetsValues(val sets: Int, val reps: Int, val restSeconds: Int)

/** Bounds and steps for the sheet's steppers. Sets and reps never go below 1 (TARGETS_REJECTED). */
object TargetsSheetRules {
    const val MIN_COUNT = 1
    const val MAX_SETS = 20
    const val MAX_REPS = 100
    const val MAX_SECONDS = 600
    const val SECONDS_STEP = 5
    const val REST_STEP = 15
    const val MAX_REST = 600

    /** Presets: reps × sets for a lift, seconds × sets for a hold. */
    fun presets(hold: Boolean): List<Pair<Int, Int>> =
        if (hold) listOf(3 to 30, 3 to 45, 2 to 60) else listOf(3 to 5, 3 to 8, 3 to 10, 4 to 12)

    fun valid(values: TargetsValues): Boolean = values.sets >= MIN_COUNT && values.reps >= MIN_COUNT
}

/**
 * Set targets (blueprint 4.5, A31). Opens after the picker adds lifts, prefilled from
 * AddDefaults. Back or swipe down drops the sheet's edits without asking; Save targets is the
 * only write. The values are saveable state, so a recreation or process death restores them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsSheet(
    liftName: String,
    initial: TargetsValues,
    hold: Boolean,
    saving: Boolean,
    error: String?,
    onSave: (TargetsValues) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberFullSheetState(LocalReducedMotion.current) { !saving },
    ) {
        TargetsSheetContent(
            liftName = liftName,
            initial = initial,
            hold = hold,
            saving = saving,
            error = error,
            onSave = onSave,
        )
    }
}

@Composable
internal fun TargetsSheetContent(
    liftName: String,
    initial: TargetsValues,
    hold: Boolean,
    saving: Boolean,
    error: String?,
    onSave: (TargetsValues) -> Unit,
) {
    var sets by rememberSaveable(liftName) { mutableIntStateOf(initial.sets) }
    var reps by rememberSaveable(liftName) { mutableIntStateOf(initial.reps) }
    var rest by rememberSaveable(liftName) { mutableIntStateOf(initial.restSeconds) }
    val values = TargetsValues(sets, reps, rest)
    val repsStep = if (hold) TargetsSheetRules.SECONDS_STEP else 1
    val repsMax = if (hold) TargetsSheetRules.MAX_SECONDS else TargetsSheetRules.MAX_REPS
    val invalid = !TargetsSheetRules.valid(values)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TargetsSheetTags.CONTENT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Metrics.gutter)
            .padding(bottom = Metrics.space7),
        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(liftName, style = InstrumentType.title, color = TextPrimary, maxLines = 2,
                modifier = Modifier.testTag(TargetsSheetTags.LIFT))
            Text(stringResource(R.string.targets_subtitle), style = InstrumentType.caption, color = TextSecondary)
        }
        TargetsStepperRow(
            label = stringResource(R.string.targets_sets),
            value = sets.toString(),
            tag = TargetsSheetTags.SETS,
            enabled = !saving,
            canLower = sets > TargetsSheetRules.MIN_COUNT,
            canRaise = sets < TargetsSheetRules.MAX_SETS,
            onLower = { sets -= 1 },
            onRaise = { sets += 1 },
        )
        TargetsStepperRow(
            label = stringResource(if (hold) R.string.targets_seconds else R.string.targets_reps),
            value = reps.toString(),
            tag = TargetsSheetTags.REPS,
            enabled = !saving,
            canLower = reps - repsStep >= TargetsSheetRules.MIN_COUNT,
            canRaise = reps + repsStep <= repsMax,
            onLower = { reps -= repsStep },
            onRaise = { reps += repsStep },
        )
        TargetsStepperRow(
            label = stringResource(R.string.targets_rest),
            value = restLabel(rest),
            tag = TargetsSheetTags.REST,
            enabled = !saving,
            canLower = rest >= TargetsSheetRules.REST_STEP,
            canRaise = rest + TargetsSheetRules.REST_STEP <= TargetsSheetRules.MAX_REST,
            onLower = { rest -= TargetsSheetRules.REST_STEP },
            onRaise = { rest += TargetsSheetRules.REST_STEP },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            TargetsSheetRules.presets(hold).forEach { (presetSets, presetReps) ->
                InstrumentChip(
                    label = "$presetSets × $presetReps",
                    selected = sets == presetSets && reps == presetReps,
                    onClick = { sets = presetSets; reps = presetReps },
                    enabled = !saving,
                    modifier = Modifier.testTag(TargetsSheetTags.preset(presetSets, presetReps)),
                )
            }
        }
        Text(stringResource(R.string.targets_note), style = InstrumentType.caption, color = TextTertiary)
        val complaint = error ?: if (invalid) RoutineSaveCopy.TARGETS_REJECTED else null
        if (complaint != null) {
            FieldComplaint(message = complaint, modifier = Modifier.testTag(TargetsSheetTags.ERROR))
        }
        PrimaryGymButton(
            text = if (saving) RoutineSaveCopy.SAVING else stringResource(R.string.targets_save),
            onClick = { onSave(values) },
            enabled = !saving && !invalid,
            height = Metrics.commit,
            modifier = Modifier.testTag(TargetsSheetTags.SAVE),
        )
    }
}

@Composable
private fun TargetsStepperRow(
    label: String,
    value: String,
    tag: String,
    enabled: Boolean,
    canLower: Boolean,
    canRaise: Boolean,
    onLower: () -> Unit,
    onRaise: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Text(label, style = InstrumentType.bodyStrong, color = TextPrimary, modifier = Modifier.weight(1f))
        val lower = stringResource(R.string.targets_lower, label)
        val raise = stringResource(R.string.targets_raise, label)
        StepperButton(label = stringResource(R.string.targets_minus), onClick = onLower, enabled = enabled && canLower,
            modifier = Modifier.semantics { contentDescription = lower })
        Text(value, style = InstrumentType.numeralMd, color = TextPrimary, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = Metrics.touchMin).testTag(TargetsSheetTags.value(tag)))
        StepperButton(label = stringResource(R.string.targets_plus), onClick = onRaise, enabled = enabled && canRaise,
            modifier = Modifier.semantics { contentDescription = raise })
    }
}

private fun restLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

object TargetsSheetTags {
    const val CONTENT = "targets-sheet"
    const val LIFT = "targets-lift"
    const val SETS = "targets-sets"
    const val REPS = "targets-reps"
    const val REST = "targets-rest"
    const val ERROR = "targets-error"
    const val SAVE = "targets-save"
    fun value(row: String): String = "$row-value"
    fun preset(sets: Int, reps: Int): String = "targets-preset-$sets-$reps"
}
