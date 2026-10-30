package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WarmupRamp
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

object WorkoutPreviewTags {
    const val ROOT = "workout-preview"
    const val BEGIN = "preview_begin"
    const val ADD_LIFT = "preview_add_lift"
    const val REORDER = "preview_reorder"
    const val BACK = "preview_back"
    const val LOADING = "preview_loading"
    const val LIFT = "preview_lift"
}

/** What each lift last looked like. Null map: still loading. A missing id: first time. */
sealed interface PreviewHint {
    data object FirstTime : PreviewHint
    data class Last(val weightKg: Double) : PreviewHint
}

data class PreviewLift(
    val name: String,
    val sets: Int,
    val reps: Int,
    val seconds: Int?,
    val startWeightKg: Double?,
    val hint: PreviewHint?,
    val warmup: Boolean,
)

/** Per-lift weights behind the preview, keyed by exercise id. */
data class PreviewWeights(
    val lastKg: Map<String, Double>,
    val suggestedKg: Map<String, Double>,
)

data class WorkoutPreviewModel(
    val title: String,
    val minutes: Int,
    val lifts: List<PreviewLift>,
    val hintsLoading: Boolean,
)

/**
 * Screen 06 (blueprint 4.6). Pure: the preview is rebuilt from the routine and the last-time
 * hints. A failed hint read is passed as an empty map, so every lift reads "First time" and
 * nothing blocks Begin.
 */
object WorkoutPreview {
    fun build(
        title: String,
        routine: Routine?,
        minutes: Int,
        lastWeightKg: Map<String, Double>?,
        unit: WeightUnit,
        suggestedWeightKg: Map<String, Double> = emptyMap(),
    ): WorkoutPreviewModel {
        val lifts = routine?.exercises.orEmpty().sortedBy { it.sortOrder }.map { planned ->
            val last = lastWeightKg?.get(planned.exercise.id)
            val hint = when {
                lastWeightKg == null -> null
                last != null && last > 0.0 -> PreviewHint.Last(last)
                else -> PreviewHint.FirstTime
            }
            // The live Log's own prefill order (spec section 6): the suggestion, then the
            // planned target. Last time stays a hint, never the start weight.
            val start = suggestedWeightKg[planned.exercise.id]?.takeIf { it > 0.0 }
                ?: planned.targetWeightKg?.takeIf { it > 0.0 }
            val warmup = start != null && planned.targetSeconds == null && WarmupRamp.sets(
                workingWeightKg = start,
                loadType = planned.exercise.loadType,
                unit = unit,
                equipment = planned.exercise.equipment,
            ).isNotEmpty()
            PreviewLift(
                name = planned.exercise.name,
                sets = planned.targetSets,
                reps = planned.targetReps,
                seconds = planned.targetSeconds,
                startWeightKg = start,
                hint = hint,
                warmup = warmup,
            )
        }
        return WorkoutPreviewModel(
            title = title, minutes = minutes, lifts = lifts, hintsLoading = lastWeightKg == null,
        )
    }

    fun target(lift: PreviewLift, unit: WeightUnit): String {
        val volume = lift.seconds?.let { "${lift.sets} × ${it}s" } ?: "${lift.sets} × ${lift.reps}"
        return lift.startWeightKg?.let { "$volume · ${it.toWeightLabel(unit)}" } ?: volume
    }
}

/**
 * Begin calls the caller's existing start path; the preview itself never creates a session, so
 * Back leaves nothing behind (D6). [beginState] lets the host show its in-flight or failed start.
 */
@Composable
fun WorkoutPreviewScreen(
    model: WorkoutPreviewModel,
    unit: WeightUnit,
    onBegin: () -> Unit,
    onBack: () -> Unit,
    onReorder: () -> Unit,
    onAddLift: () -> Unit,
    beginState: ActionState = ActionState.Enabled,
    beginLabel: String? = null,
    contextContent: (@Composable () -> Unit)? = null,
    actionKey: Any? = null,
    backEnabled: Boolean = true,
) {
    Dialog(
        onDismissRequest = { if (backEnabled) onBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        WorkoutPreviewContent(
            model = model, unit = unit, onBegin = onBegin, onBack = onBack, onReorder = onReorder,
            onAddLift = onAddLift, beginState = beginState, beginLabel = beginLabel,
            contextContent = contextContent, actionKey = actionKey, backEnabled = backEnabled,
        )
    }
}

@Composable
internal fun WorkoutPreviewContent(
    model: WorkoutPreviewModel,
    unit: WeightUnit,
    onBegin: () -> Unit,
    onBack: () -> Unit,
    onReorder: () -> Unit,
    onAddLift: () -> Unit,
    beginState: ActionState = ActionState.Enabled,
    beginLabel: String? = null,
    contextContent: (@Composable () -> Unit)? = null,
    actionKey: Any? = null,
    backEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit)
            .systemBarsPadding()
            .testTag(WorkoutPreviewTags.ROOT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                enabled = backEnabled,
                modifier = Modifier.size(Metrics.touchMin).testTag(WorkoutPreviewTags.BACK),
            ) {
                Icon(TemperIcons.Back, contentDescription = stringResource(R.string.preview_back), tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.title, style = InstrumentType.title, color = TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (model.lifts.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.preview_subtitle, model.lifts.size, model.lifts.size, model.minutes),
                        style = InstrumentType.caption, color = TextSecondary,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            if (model.hintsLoading && model.lifts.isNotEmpty()) {
                ScreenLoading(
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutPreviewTags.LOADING),
                    cards = model.lifts.size.coerceAtMost(3),
                )
            } else {
                model.lifts.forEachIndexed { index, lift -> PreviewLiftCard(number = index + 1, lift = lift, unit = unit) }
            }
            contextContent?.invoke()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter, vertical = Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            val first = model.lifts.firstOrNull()
            // A new start attempt rebuilds the actions, so a tap queued on an older frame
            // cannot land on the next attempt (same contract as the confirm dialog).
            key(actionKey) {
            if (first == null) {
                PrimaryButton(
                    label = stringResource(R.string.preview_add_lift),
                    onClick = onAddLift,
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutPreviewTags.ADD_LIFT),
                )
            } else {
                val target = WorkoutPreview.target(first, unit)
                PrimaryButton(
                    label = beginLabel ?: stringResource(R.string.preview_begin, first.name),
                    subLabel = if (first.warmup) {
                        stringResource(R.string.preview_begin_sub, target)
                    } else {
                        stringResource(R.string.preview_begin_sub_working, target)
                    },
                    onClick = onBegin,
                    state = beginState,
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutPreviewTags.BEGIN),
                )
                GhostButton(
                    label = stringResource(R.string.preview_reorder),
                    onClick = onReorder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutPreviewTags.REORDER),
                )
            }
            }
        }
    }
}

@Composable
private fun PreviewLiftCard(number: Int, lift: PreviewLift, unit: WeightUnit) {
    GymCard(modifier = Modifier.fillMaxWidth().testTag(WorkoutPreviewTags.LIFT)) {
        Column(
            modifier = Modifier.padding(Metrics.space4),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                "$number  ${lift.name}", style = InstrumentType.bodyStrong, color = TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(WorkoutPreview.target(lift, unit), style = InstrumentType.caption, color = TextSecondary)
            when (val hint = lift.hint) {
                is PreviewHint.Last -> Text(
                    stringResource(R.string.preview_last, hint.weightKg.toWeightLabel(unit)),
                    style = InstrumentType.caption, color = TextSecondary,
                )
                PreviewHint.FirstTime -> Text(
                    stringResource(R.string.preview_first_time),
                    style = InstrumentType.caption, color = TextSecondary,
                )
                null -> Unit
            }
        }
    }
}
