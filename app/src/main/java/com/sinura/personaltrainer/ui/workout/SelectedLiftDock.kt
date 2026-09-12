package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.sinura.personaltrainer.domain.LiftChipCopy
import com.sinura.personaltrainer.domain.NextLiftCopy
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import androidx.compose.ui.unit.dp

/**
 * The selected lift as a box that cannot hide under the rest dock.
 *
 * Picture + name + planned work + rest, idle rest as a label (G-05).
 */
@Composable
internal fun SelectedLiftDock(
    lift: SessionExercise,
    workingLogged: Int,
    unit: WeightUnit,
    restSeconds: Int,
    restRunning: Boolean,
    restRemainingSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val marks = LiftChipCopy.marks(
        workingLogged = workingLogged,
        targetSets = lift.targetSets,
        restSeconds = restSeconds,
        restRunningOnThisLift = restRunning,
        remainingSeconds = restRemainingSeconds,
    )
    val planned = NextLiftCopy.plannedWork(
        workingLogged = workingLogged,
        targetSets = lift.targetSets,
        targetReps = lift.targetReps,
        targetWeightLabel = lift.targetWeightKg?.takeIf { it > 0.0 }?.toWeightLabel(unit),
    )
    val spoken = NextLiftCopy.spoken(
        name = lift.exercise.name,
        plannedWork = planned,
        restClock = marks.restClock,
        restLive = marks.restLive,
    )
    HairlineDivider(startIndent = 0.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface1)
            .testTag(WorkoutTestTags.SELECTED_LIFT)
            .semantics { contentDescription = spoken }
            .padding(horizontal = Metrics.space4, vertical = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        ExerciseThumb(
            exercise = lift.exercise,
            size = ThumbSize.header,
        )
        Column(modifier = Modifier.weight(1f)) {
            Kicker(NextLiftCopy.KICKER)
            Text(
                lift.exercise.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                planned,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                marks.setProgress,
                style = InstrumentType.numeralSm,
                color = TextPrimary,
                maxLines = 1,
            )
            marks.restClock?.let { clock ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Kicker(
                        text = LiftChipCopy.REST,
                        color = if (marks.restLive) RestCyan else TextSecondary,
                        asHeading = false,
                    )
                    Text(
                        clock,
                        style = if (marks.restLive) InstrumentType.numeralSm else InstrumentType.caption,
                        color = if (marks.restLive) RestCyan else TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
