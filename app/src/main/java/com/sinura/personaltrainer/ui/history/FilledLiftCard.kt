package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.FilledSessionLift
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.LiftCard
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.components.StepperButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

internal object FilledLiftCardTags {
    fun planned(exerciseId: String) = "session-detail-planned-$exerciseId"
    fun plannedEntry(prescriptionId: String) = "session-detail-planned-entry-$prescriptionId"
    fun recorded(exerciseId: String) = "session-detail-recorded-$exerciseId"
    fun setRow(setId: String) = "session-detail-set-$setId"
    fun warmupMark(setId: String) = "session-detail-warmup-$setId"
    fun addSet(exerciseId: String) = "session-detail-add-set-$exerciseId"
}

/**
 * A finished lift in the same chrome as the program card and the floor card:
 * numbered badge, still, complete name, muscle, kit chip, working-set count,
 * then every original planned Work / Rest / Load entry and the recorded sets once.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FilledLiftCard(
    lift: FilledSessionLift,
    loadClass: LoadClass,
    unit: WeightUnit,
    onOpen: () -> Unit,
    onEditSet: (SetLog) -> Unit,
    onAddSet: () -> Unit,
    /** v4 V5: read mode shows saved sets only; Edit mode adds Edit, Delete and Add set. */
    editMode: Boolean = true,
    onDeleteSet: (SetLog) -> Unit = {},
    /** v4 V5: inline weight and reps steppers in Edit mode; the screen debounces the write. */
    onNudgeSet: (SetLog, Double, Int) -> Unit = { _, _, _ -> },
) {
    val repeated = lift.prescriptions.size > 1
    val single = lift.prescriptions.singleOrNull()?.row
    // Sets reference an exercise, so multiple prescriptions cannot each claim their completion.
    val countTarget = single?.targetSets ?: 0
    val spoken = if (repeated) {
        SessionOrderCopy.filledGroupSpoken(
            number = lift.number,
            name = lift.exercise.name,
            muscleGroup = lift.exercise.muscleGroup,
            workingLogged = lift.workingLogged,
            plannedEntries = lift.prescriptions.map { entry ->
                val row = entry.row
                SessionOrderCopy.filledPrescriptionSpoken(
                    originalPosition = entry.originalPosition,
                    sets = row.targetSets,
                    reps = row.targetReps,
                    restClock = RestTimer.formatClock(row.restSeconds),
                    load = row.targetWeightKg?.let { WeightConverter.formatLabel(it, unit) },
                    holdSeconds = row.targetSeconds,
                    holdSecondsMax = row.targetSecondsMax,
                )
            },
        )
    } else {
        SessionOrderCopy.filledSpoken(
            number = lift.number,
            name = lift.exercise.name,
            muscleGroup = lift.exercise.muscleGroup,
            workingLogged = lift.workingLogged,
            targetSets = countTarget,
            targetReps = single?.targetReps ?: 0,
            restClock = single?.let { RestTimer.formatClock(it.restSeconds) }
                .takeIf { lift.hasPrescription },
            load = single?.targetWeightKg?.takeIf { it > 0.0 }
                ?.let { WeightConverter.formatLabel(it, unit) },
            holdSeconds = single?.targetSeconds,
            holdSecondsMax = single?.targetSecondsMax,
        )
    }
    val headerTrailing = @Composable {
        Column {
            Text(
                SessionOrderCopy.filledCount(lift.workingLogged, countTarget),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            Text(
                if (countTarget > 0) "Working sets: recorded / planned" else "Recorded working sets",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
    }
    LiftCard(
        exercise = lift.exercise,
        number = lift.number,
        spoken = spoken,
        onClick = onOpen,
        cardTag = SessionDetailTestTags.liftCard(lift.exercise.id),
        completeName = true,
        trailing = headerTrailing,
    ) {
        if (lift.hasPrescription || repeated) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.space3)
                    .testTag(FilledLiftCardTags.planned(lift.exercise.id)),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                lift.prescriptions.forEach { entry ->
                    val row = entry.row
                    val loadKg = row.targetWeightKg?.takeIf { repeated || it > 0.0 }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .testTag(FilledLiftCardTags.plannedEntry(row.id)),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        DetailSectionLabel(
                            if (repeated) "${SessionOrderCopy.PLANNED} · ${entry.originalPosition}"
                            else SessionOrderCopy.PLANNED,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
                            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                        ) {
                            if (repeated || row.targetSets > 0 || row.targetSeconds != null) {
                                PlannedMetric(
                                    value = SessionOrderCopy.workValue(
                                        row.targetSets,
                                        if (repeated) row.targetReps else row.targetReps.coerceAtLeast(1),
                                        row.targetSeconds,
                                        row.targetSecondsMax,
                                    ),
                                    label = SessionOrderCopy.WORK,
                                )
                            }
                            PlannedMetric(value = RestTimer.formatClock(row.restSeconds), label = SessionOrderCopy.REST)
                            if (loadKg != null) {
                                PlannedMetric(
                                    value = WeightConverter.formatDisplayNumber(
                                        WeightConverter.toDisplayValue(loadKg, unit),
                                    ),
                                    label = SessionOrderCopy.LOAD,
                                    unit = unit.suffix,
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.testTag(FilledLiftCardTags.recorded(lift.exercise.id)).padding(
                start = Metrics.space3,
                end = Metrics.space3,
                bottom = Metrics.space3,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
        ) {
            DetailSectionLabel(SessionOrderCopy.RECORDED_SETS)
            if (lift.sets.isEmpty()) {
                Text(
                    SessionOrderCopy.NO_RECORDED_SETS,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            } else {
                GroupedList {
                    lift.sets.forEachIndexed { index, set ->
                        if (index > 0) HairlineDivider()
                        // D8: the shown number follows order, so a delete never leaves a gap.
                        // The stored setNumber is not rewritten.
                        val shown = index + 1
                        // Shown at once; the write follows the 400 ms pause in the view model.
                        var nudged by remember(set.id, set.weightKg, set.reps) { mutableStateOf(set.weightKg to set.reps) }
                        val hold = set.reps < 1 && (set.durationSeconds ?: 0) > 0
                        val shownSet = set.copy(weightKg = nudged.first, reps = nudged.second)
                        RecordedSetRow(
                            row = SetTableLine.fromLog(shownSet, loadClass, unit).copy(
                                number = shown,
                                extras = SetCopy.tableExtras("Set $shown", set.rpe),
                            ),
                            onEdit = { onEditSet(set) }.takeIf { editMode },
                            onDelete = { onDeleteSet(set) }.takeIf { editMode },
                        )
                        if (editMode && !hold) InlineSetSteppers(
                            setId = set.id,
                            showWeight = loadClass.weightMeaning != WeightMeaning.NONE,
                            onWeight = { direction ->
                                val kg = FloorStepper.nextWeightKg(
                                    nudged.first, unit, direction, lift.exercise.loadType, lift.exercise.equipment,
                                )
                                nudged = kg to nudged.second
                                onNudgeSet(set, kg, nudged.second)
                            },
                            onReps = { direction ->
                                val reps = FloorStepper.nextReps(nudged.second, direction)
                                nudged = nudged.first to reps
                                onNudgeSet(set, nudged.first, reps)
                            },
                        )
                    }
                }
            }
            if (editMode) TextButton(
                onClick = onAddSet,
                modifier = Modifier.heightIn(min = Metrics.touchMin)
                    .testTag(FilledLiftCardTags.addSet(lift.exercise.id)),
            ) {
                Text("Add set", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun DetailSectionLabel(label: String) {
    Text(
        label,
        style = InstrumentType.bodyStrong,
        color = TextSecondary,
        modifier = Modifier.semantics { heading() },
    )
}

/** Detail values may wrap; the shared compact metric keeps its existing line cap. */
@Composable
private fun PlannedMetric(value: String, label: String, unit: String? = null) {
    val displayed = buildAnnotatedString {
        append(value)
        if (unit != null) withStyle(InstrumentType.unit.toSpanStyle().copy(color = TextSecondary)) {
            append(" $unit")
        }
    }
    Column(
        modifier = Modifier.width(IntrinsicSize.Max).semantics(mergeDescendants = true) {
            contentDescription = "${SessionOrderCopy.PLANNED} $label $displayed"
        },
    ) {
        // Sets × reps is an ordered mathematical expression even in an RTL card.
        Text(
            displayed,
            style = InstrumentType.numeralSm.copy(textDirection = TextDirection.Ltr),
            color = TextPrimary,
        )
        Text(label.uppercase(), style = InstrumentType.kicker, color = TextSecondary)
    }
}

/** Keep every saved value and the exact correction action readable at large system text. */
@Composable
private fun RecordedSetRow(row: SetTableLine, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(Metrics.space3)
            .testTag(FilledLiftCardTags.setRow(row.id)),
    ) {
        val stack = fontScale >= 1.6f || maxWidth < 280.dp
        val values = @Composable {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Recorded ${if (row.isWarmup) "warm-up" else "working"} ${row.extras}, ${row.line}"
                },
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalAlignment = Alignment.CenterVertically) {
                    // A25: warm-ups carry a W mark in RestCyan, the rest colour, never Volt.
                    if (row.isWarmup) {
                        val warmupLabel = stringResource(R.string.history_warmup_label)
                        Text(
                            stringResource(R.string.history_warmup_mark),
                            style = InstrumentType.bodyStrong,
                            color = RestCyan,
                            modifier = Modifier
                                .testTag(FilledLiftCardTags.warmupMark(row.id))
                                .clearAndSetSemantics { contentDescription = warmupLabel },
                        )
                    }
                    Text(row.line, style = InstrumentType.numeralSm, color = TextPrimary)
                }
                Text(row.extras, style = InstrumentType.caption, color = TextSecondary)
            }
        }
        val edit = @Composable {
            if (onEdit != null) TextButton(
                onClick = onEdit,
                modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                    .testTag(SessionDetailTestTags.EDIT_SET),
            ) {
                Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
            if (onDelete != null) {
                val spoken = stringResource(R.string.session_delete_set, row.number)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(Metrics.touchMin)
                        .testTag(SessionDetailTestTags.sessionSetDelete(row.id)),
                ) {
                    Icon(TemperIcons.Delete, contentDescription = spoken, tint = TextSecondary)
                }
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                values()
                Row(verticalAlignment = Alignment.CenterVertically) { edit() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) { values() }
                edit()
            }
        }
    }
}

/** v4 V5: weight and reps nudges on one saved set, 48dp targets. */
@Composable
private fun InlineSetSteppers(
    setId: String,
    showWeight: Boolean,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Metrics.space3),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showWeight) {
            StepperButton(
                label = stringResource(R.string.session_weight_down),
                onClick = { onWeight(-1) },
                modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                    .testTag(SessionDetailTestTags.sessionSetWeightDown(setId)),
            )
            StepperButton(
                label = stringResource(R.string.session_weight_up),
                onClick = { onWeight(1) },
                modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                    .testTag(SessionDetailTestTags.sessionSetWeightUp(setId)),
            )
        }
        StepperButton(
            label = stringResource(R.string.session_reps_down),
            onClick = { onReps(-1) },
            modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                .testTag(SessionDetailTestTags.sessionSetRepsDown(setId)),
        )
        StepperButton(
            label = stringResource(R.string.session_reps_up),
            onClick = { onReps(1) },
            modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                .testTag(SessionDetailTestTags.sessionSetRepsUp(setId)),
        )
    }
}
