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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.FilledSessionLift
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.LiftCard
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

internal object FilledLiftCardTags {
    fun planned(exerciseId: String) = "session-detail-planned-$exerciseId"
    fun recorded(exerciseId: String) = "session-detail-recorded-$exerciseId"
    fun setRow(setId: String) = "session-detail-set-$setId"
    fun addSet(exerciseId: String) = "session-detail-add-set-$exerciseId"
}

/**
 * A finished lift in the same chrome as the program card and the floor card:
 * numbered badge, still, complete name, muscle, kit chip, working-set count,
 * then explicitly planned Work / Rest / Load and the recorded sets.
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
) {
    val restClock = RestTimer.formatClock(lift.restSeconds)
    val loadKg = lift.targetWeightKg?.takeIf { it > 0.0 }
    val loadDisplay = loadKg?.let { kg -> WeightConverter.formatLabel(kg, unit) }
    val spoken = SessionOrderCopy.filledSpoken(
        number = lift.number,
        name = lift.exercise.name,
        muscleGroup = lift.exercise.muscleGroup,
        workingLogged = lift.workingLogged,
        targetSets = lift.targetSets,
        targetReps = lift.targetReps,
        restClock = restClock.takeIf { lift.hasPrescription },
        load = loadDisplay,
        holdSeconds = lift.targetSeconds,
        holdSecondsMax = lift.targetSecondsMax,
    )
    val headerTrailing = @Composable {
        Column {
            Text(
                SessionOrderCopy.filledCount(lift.workingLogged, lift.targetSets),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
            )
            Text(
                if (lift.targetSets > 0) "Working sets: recorded / planned" else "Recorded working sets",
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
        if (lift.hasPrescription) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.space3)
                    .testTag(FilledLiftCardTags.planned(lift.exercise.id)),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                DetailSectionLabel(SessionOrderCopy.PLANNED)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space4),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    if (lift.targetSets > 0 || lift.targetSeconds != null) {
                        PlannedMetric(
                            value = SessionOrderCopy.workValue(
                                lift.targetSets,
                                lift.targetReps.coerceAtLeast(1),
                                lift.targetSeconds,
                                lift.targetSecondsMax,
                            ),
                            label = SessionOrderCopy.WORK,
                        )
                    }
                    PlannedMetric(value = restClock, label = SessionOrderCopy.REST)
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
                        RecordedSetRow(
                            row = SetTableLine.fromLog(set, loadClass, unit),
                            onEdit = { onEditSet(set) },
                        )
                    }
                }
            }
            TextButton(
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
private fun RecordedSetRow(row: SetTableLine, onEdit: () -> Unit) {
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
                Text(row.line, style = InstrumentType.numeralSm, color = TextPrimary)
                Text(row.extras, style = InstrumentType.caption, color = TextSecondary)
                if (row.isWarmup) Text("Warm-up", style = InstrumentType.caption, color = RestCyan)
            }
        }
        val edit = @Composable {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                    .testTag(SessionDetailTestTags.EDIT_SET),
            ) {
                Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                values()
                edit()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) { values() }
                edit()
            }
        }
    }
}
