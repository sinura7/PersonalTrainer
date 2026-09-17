package com.sinura.personaltrainer.ui.workout


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import java.text.DateFormat
import java.util.Date
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.FloorCompactChrome
import com.sinura.personaltrainer.domain.ProgressionCopy
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogReceipt
import com.sinura.personaltrainer.domain.WarmupRamp
import com.sinura.personaltrainer.domain.WarmupSet
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.InstrumentChoiceGroup
import com.sinura.personaltrainer.ui.components.InstrumentChoiceChip
import com.sinura.personaltrainer.ui.components.InstrumentPreset
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.SetEntryPanel
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.util.JvmTime

internal data class WorkoutLiftCardState(
    val lift: SessionExercise,
    val number: Int,
    val selected: Boolean,
    val loggedSets: List<SetLog>,
    val latestSetId: String?,
    val editingSetId: String?,
    val lastPerformance: ExerciseSessionSummary?,
    val hint: ProgressionHint?,
    val draftWeightKg: Double,
    val draftReps: Int,
    val draftWarmup: Boolean,
    val draftRpe: Int?,
    val microRec: SetMicroRec?,
    val recommendedRpe: Int? = null,
    val unit: WeightUnit,
    val canEdit: Boolean,
    val showAddSet: Boolean,
    val hold: Boolean = false,
    val holdSeconds: Int? = null,
    val holdRunning: Boolean = false,
    val holdRemainingSeconds: Int = 0,
    val receipt: LogReceipt? = null,
)

internal data class WorkoutLiftCardEvents(
    val onWeightKgChange: (Double) -> Unit,
    val onRepsAdjust: (Int) -> Unit,
    val onRepsChange: (Int) -> Unit,
    val onSecondsAdjust: (Int) -> Unit = {},
    val onSecondsChange: (Int) -> Unit = {},
    val onApplyLastTime: (Double, Int) -> Unit,
    val onWarmup: (Boolean) -> Unit,
    val onRpe: (Int?) -> Unit,
    val onApplyWarmupRamp: (Double) -> Unit = {},
    val onApplySuggested: () -> Unit,
    val onEditSet: (String) -> Unit,
    val onDeleteSet: (String) -> Unit,
    val onAddSet: () -> Unit,
    val onApplyMicroRec: () -> Unit = {},
)

/**
 * Packet C: entry surface for the current lift only.
 *
 * Identity lives on [CurrentLiftCard]. Logging keeps its viewport; only an
 * explicit edit brings the entry back into view. Complete history is a sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkoutLiftCard(
    card: WorkoutLiftCardState,
    events: WorkoutLiftCardEvents,
) {
    val lift = card.lift
    val loggedSets = card.loggedSets
    val latestSetId = card.latestSetId
    val editingSetId = card.editingSetId
    val lastPerformance = card.lastPerformance
    val draftWeightKg = card.draftWeightKg
    val draftReps = card.draftReps
    val draftWarmup = card.draftWarmup
    val draftRpe = card.draftRpe
    val unit = card.unit
    val showAddSet = card.showAddSet
    val hold = card.hold
    val holdSeconds = card.holdSeconds
    val holdRunning = card.holdRunning
    val holdRemainingSeconds = card.holdRemainingSeconds
    val onWeightKgChange = events.onWeightKgChange
    val onRepsAdjust = events.onRepsAdjust
    val onRepsChange = events.onRepsChange
    val onSecondsAdjust = events.onSecondsAdjust
    val onSecondsChange = events.onSecondsChange
    val onApplyLastTime = events.onApplyLastTime
    val onWarmup = events.onWarmup
    val onRpe = events.onRpe
    val onEditSet = events.onEditSet
    val onDeleteSet = events.onDeleteSet
    val onAddSet = events.onAddSet
    val workingLogged = loggedSets.count { !it.isWarmup }
    val warmupLogged = loggedSets.count { it.isWarmup }
    val targetSets = lift.targetSets
    val setContext = SetOrdinalCopy.draftLine(
        isWarmup = draftWarmup,
        warmupLogged = warmupLogged,
        workingLogged = workingLogged,
        targetSets = targetSets,
    )
    val showRpe = FloorCompactChrome.showOptionalLogOptions(isWarmup = draftWarmup)
    val workingKg = WarmupRamp.workingWeightKg(
        draftKg = draftWeightKg,
        draftIsWarmup = draftWarmup,
        workingLogged = workingLogged,
        targetKg = lift.targetWeightKg,
        suggestedKg = card.hint?.suggestedWeightKg,
        lastKg = card.hint?.lastWeightKg ?: lastPerformance?.topSet?.weightKg,
        loadType = lift.exercise.loadType,
        equipment = lift.exercise.equipment,
        movementKey = lift.exercise.movementKey,
    )
    val ramp = if (workingLogged == 0) {
        WarmupRamp.sets(
            workingWeightKg = workingKg,
            loadType = lift.exercise.loadType,
            unit = unit,
            equipment = lift.exercise.equipment,
        )
    } else {
        emptyList()
    }
    val rampEmphasis = WarmupRamp.nextUnusedIndex(
        ramp = ramp,
        loggedWarmupKg = loggedSets.filter { it.isWarmup }.map { it.weightKg },
    )
    val entryRequester = remember { BringIntoViewRequester() }
    var setsOpen by rememberSaveable(lift.id) { mutableStateOf(false) }
    LaunchedEffect(lift.id, editingSetId) {
        if (editingSetId != null) entryRequester.bringIntoView()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Text(
            text = if (editingSetId != null) "Editing saved set" else setContext,
            modifier = Modifier.testTag(WorkoutTestTags.SET_CONTEXT),
            style = InstrumentType.caption,
            color = TextSecondary,
        )
        InstrumentChoiceGroup(modifier = Modifier.fillMaxWidth()) {
            InstrumentChoiceChip(
                label = "Working",
                selected = !draftWarmup,
                onClick = { onWarmup(false) },
                modifier = Modifier.testTag("workout-working-choice"),
            )
            InstrumentChoiceChip(
                label = "Warm-up",
                selected = draftWarmup,
                onClick = { onWarmup(true) },
                modifier = Modifier.testTag(WorkoutTestTags.WARMUP_CHIP),
            )
        }
        if (draftWarmup) {
            WarmupRampRow(
                ramp = ramp,
                emphasisIndex = rampEmphasis,
                unit = unit,
                onApplyRamp = events.onApplyWarmupRamp,
            )
        }
        SetEntryPanel(
            weightKg = draftWeightKg,
            reps = draftReps,
            onWeightKgChange = onWeightKgChange,
            onRepsAdjust = onRepsAdjust,
            onRepsChange = onRepsChange,
            unit = unit,
            loadClass = LoadClass.of(lift.exercise.loadType),
            plated = lift.exercise.equipment == EquipmentType.BARBELL,
            hold = hold,
            durationSeconds = holdSeconds,
            holdRunning = holdRunning,
            remainingSeconds = holdRemainingSeconds,
            onSecondsAdjust = onSecondsAdjust,
            onSecondsChange = onSecondsChange,
            compact = true,
            loadType = lift.exercise.loadType,
            equipment = lift.exercise.equipment,
            movementKey = lift.exercise.movementKey,
            plannedKg = lift.targetWeightKg,
            lastKg = card.hint?.lastWeightKg ?: lastPerformance?.topSet?.weightKg,
            suggestedKg = card.hint?.suggestedWeightKg,
            modifier = Modifier
                .testTag(LogLoopBringIntoView.ANCHOR_TAG)
                .bringIntoViewRequester(entryRequester),
        )
        SecondaryLogOptions(
            warmup = draftWarmup,
            rpe = draftRpe,
            recommendedRpe = card.recommendedRpe,
            showRpe = showRpe,
            onRpe = onRpe,
        )
        if (!draftWarmup) card.microRec?.let { rec ->
            MicroRecLine(
                rec = rec,
                loadClass = LoadClass.of(lift.exercise.loadType),
                unit = unit,
                onApply = events.onApplyMicroRec,
            )
        }
        if (loggedSets.isNotEmpty()) {
            LatestWorkoutSet(
                sets = loggedSets,
                latestSetId = latestSetId,
                targetSets = targetSets,
                loadClass = LoadClass.of(lift.exercise.loadType),
                unit = unit,
                receipt = card.receipt,
                onViewSets = { setsOpen = true },
            )
        }
        lastPerformance?.let { last ->
            LastTimeStrip(summary = last, unit = unit, loadClass = LoadClass.of(lift.exercise.loadType), onApplySet = onApplyLastTime)
        }
    }
    if (setsOpen) {
        WorkoutSetsSheet(
            exerciseName = lift.exercise.name,
            sets = loggedSets,
            latestSetId = latestSetId,
            editingSetId = editingSetId,
            targetSets = targetSets,
            loadClass = LoadClass.of(lift.exercise.loadType),
            unit = unit,
            showAddSet = showAddSet,
            onEdit = { setsOpen = false; onEditSet(it) },
            onDelete = { setsOpen = false; onDeleteSet(it) },
            onAddSet = { setsOpen = false; onAddSet() },
            onDismiss = { setsOpen = false },
        )
    }
}

@Composable
private fun WarmupRampRow(
    ramp: List<WarmupSet>,
    emphasisIndex: Int,
    unit: WeightUnit,
    onApplyRamp: (Double) -> Unit,
) {
    if (ramp.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = ramp.maxOf {
        measurer.measure("Use ${it.weightKg.toWeightLabel(unit)}", style = InstrumentType.bodyStrong, softWrap = false).size.width
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimum = with(density) { widest.toDp() } + Metrics.space3 * 2
        val columns = ((maxWidth + Metrics.space2) / (minimum + Metrics.space2)).toInt().coerceIn(1, ramp.size)
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.WARMUP_RAMP),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            ramp.forEachIndexed { index, step ->
                InstrumentPreset(
                    label = "Use ${step.weightKg.toWeightLabel(unit)}",
                    compact = true,
                    supporting = "${step.percent}%" + if (index == emphasisIndex) " · Suggested" else "",
                    onClick = { onApplyRamp(step.weightKg) },
                    modifier = Modifier.weight(1f).testTag("workout-warmup-preset-$index"),
                )
            }
        }
    }
}

/**
 * What this lift looked like last time, in full.
 *
 * Complementary to [ProgressionStrip], not a duplicate of it: the strip states the decision
 * ("top set 100 kg x 5, add 2.5"), this states the evidence — every working set of the last
 * session, so a lifter can see that the top set came after two easy ones or at the end of a
 * grind, which is the difference between adding weight and repeating it.
 */
@Composable
internal fun LastTimeStrip(
    summary: ExerciseSessionSummary,
    unit: WeightUnit,
    loadClass: LoadClass,
    onApplySet: (weightKg: Double, reps: Int) -> Unit,
) {
    val view = LocalView.current
    val relative = remember(summary.performedAtMs) {
        DayLabel.relative(summary.performedAtMs, JvmTime.nowMillis(), JvmTime)
    }
    val absolute = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    Column(
        modifier = Modifier.testTag(WorkoutTestTags.LAST_TIME),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker("Last time · ${relative ?: absolute}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(summary.sets, key = { it.setId }) { set ->
                val line = SetCopy.setLine(set.weightKg, set.reps, loadClass, unit)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Surface1)
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .clickable(role = Role.Button, onClick = {
                            Haptics.tick(view)
                            onApplySet(set.weightKg, set.reps)
                        })
                        .testTag(WorkoutTestTags.lastTimeChip(set.setId))
                        .semantics {
                            contentDescription = "Use last time $line"
                        }
                        .padding(horizontal = Metrics.space3, vertical = Metrics.space2),
                ) {
                    Text(
                        line,
                        style = InstrumentType.numeralSm,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProgressionStrip(
    hint: ProgressionHint,
    unit: WeightUnit,
    onApply: () -> Unit,
) {
    // The sentence lives in the domain so this strip and the coach card cannot disagree about
    // the same lift, and so a bodyweight lift is told to add a rep rather than a kilogram.
    val reason = ProgressionCopy.stripReason(hint, unit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            // Labelled "Top set" because that is now literally what these numbers
            // are: the heaviest working set of the last session, not the last logged.
            Text(
                "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  →  ${hint.suggestedWeightKg.toWeightLabel(unit)}",
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reason,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onApply,
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
            Text("Use", style = InstrumentType.bodyStrong, color = Volt)
        }
    }
}
