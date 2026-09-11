package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.FilledSessionLift
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.LiftCard
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.SetTable
import com.sinura.personaltrainer.ui.components.SetTableLine
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * A finished lift in the same chrome as the program card and the floor card:
 * numbered badge, still, name, muscle, kit chip, `3/3`, then the Work / Rest / Load
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
    val setsById = lift.sets.associateBy { it.id }
    LiftCard(
        exercise = lift.exercise,
        number = lift.number,
        spoken = spoken,
        onClick = onOpen,
        cardTag = SessionDetailTestTags.liftCard(lift.exercise.id),
        trailing = {
            Text(
                SessionOrderCopy.filledCount(lift.workingLogged, lift.targetSets),
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
            )
        },
    ) {
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
                SetTable(
                    rows = lift.sets.map { set ->
                        SetTableLine.fromLog(set, loadClass, unit)
                    },
                    trailing = { row ->
                        val set = setsById[row.id]
                        if (set != null) {
                            TextButton(
                                onClick = { onEditSet(set) },
                                modifier = Modifier.testTag(SessionDetailTestTags.EDIT_SET),
                            ) {
                                Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
                            }
                        }
                    },
                )
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
