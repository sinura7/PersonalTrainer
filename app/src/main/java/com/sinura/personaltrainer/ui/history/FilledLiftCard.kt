package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.FilledSessionLift
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.CountBadge
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * A finished lift in the same chrome as the program card and the floor card:
 * numbered badge, still, name, muscle, `3/3`, then the Work / Rest / Load
 * prescription, then the sets written in.
 *
 * History used to be a grouped text list titled "Set N". That is a receipt,
 * not the sheet the session was logged on.
 */
@Composable
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
    )
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, Hairline, shape)
            .testTag(SessionDetailTestTags.liftCard(lift.exercise.id)),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            CountBadge(number = lift.number, selected = false)
            ExerciseThumb(
                exercise = lift.exercise,
                size = ThumbSize.header,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lift.exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lift.exercise.muscleGroup.isNotBlank()) {
                    Text(
                        lift.exercise.muscleGroup,
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                SessionOrderCopy.filledCount(lift.workingLogged, lift.targetSets),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
            )
        }
        if (lift.hasPrescription) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.space3),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                if (lift.targetSets > 0) {
                    MetricCluster(
                        value = SessionOrderCopy.workValue(lift.targetSets, lift.targetReps.coerceAtLeast(1)),
                        label = SessionOrderCopy.WORK,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
                MetricCluster(
                    value = restClock,
                    label = SessionOrderCopy.REST,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                )
                if (loadKg != null) {
                    MetricCluster(
                        value = WeightConverter.formatDisplayNumber(
                            WeightConverter.toDisplayValue(loadKg, unit),
                        ),
                        label = SessionOrderCopy.LOAD,
                        unit = unit.suffix,
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(
                start = Metrics.space3,
                end = Metrics.space3,
                bottom = Metrics.space3,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
        ) {
            if (lift.sets.isEmpty()) {
                Text(
                    "No sets",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            } else {
                GroupedList {
                    lift.sets.forEachIndexed { index, set ->
                        if (index > 0) HairlineDivider()
                        FilledSetRow(
                            set = set,
                            loadClass = loadClass,
                            unit = unit,
                            onEdit = { onEditSet(set) },
                        )
                    }
                }
            }
            TextButton(
                onClick = onAddSet,
                modifier = Modifier.heightIn(min = Metrics.touchMin),
            ) {
                Text("Add set", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FilledSetRow(
    set: SetLog,
    loadClass: LoadClass,
    unit: WeightUnit,
    onEdit: () -> Unit,
) {
    val extras = buildList {
        add("Set ${set.setNumber}")
        if (set.isWarmup) add("Warm-up")
        set.rpe?.let { add("RPE $it") }
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Metrics.space3, top = Metrics.space3, bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                SetCopy.setLine(set.weightKg, set.reps, loadClass, unit),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                extras,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onEdit,
            modifier = Modifier.testTag(SessionDetailTestTags.EDIT_SET),
        ) {
            Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    }
}
