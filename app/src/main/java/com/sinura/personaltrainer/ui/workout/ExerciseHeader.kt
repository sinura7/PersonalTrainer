package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.QuietButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

/**
 * The current exercise: picture, equipment, name, where its sets stand, and the
 * set type about to be logged. Tapping the identity opens the switcher, as before;
 * Details opens the lift's own screen. Session totals stay out of here.
 */
@Composable
internal fun ExerciseHeader(
    lift: SessionExercise,
    number: Int,
    total: Int,
    workingLogged: Int,
    setContext: String,
    draftWarmup: Boolean,
    onWarmup: (Boolean) -> Unit,
    onOpenSwitcher: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val meaning = LoadClass.of(lift.exercise.loadType).weightMeaning
    val equipment = CurrentLiftCopy.secondaryLine(lift.exercise.equipment.label, meaning)
    val spoken = CurrentLiftCopy.cardSpoken(
        name = lift.exercise.name,
        number = number,
        total = total,
        workingLogged = workingLogged,
        targetSets = lift.targetSets,
        equipmentLabel = lift.exercise.equipment.label,
        meaning = meaning,
    )
    val progressLine = "${CurrentLiftCopy.workingProgress(workingLogged, lift.targetSets)} working sets"
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val identityModifier = Modifier
        .clip(RoundedCornerShape(Radius.sm))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = CurrentLiftCopy.SWITCH, onClick = onOpenSwitcher)
        .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
        .semantics(mergeDescendants = true) {
            contentDescription = "$spoken. $setContext. ${CurrentLiftCopy.SWITCH}"
            selected = true
        }
    val words: @Composable () -> Unit = {
        Kicker(text = equipment, color = TextTertiary, asHeading = false)
        Text(
            lift.exercise.name,
            style = InstrumentType.heroTitle,
            color = TextPrimary,
            maxLines = 3,
        )
        Text(
            setContext,
            modifier = Modifier.testTag(WorkoutTestTags.SET_CONTEXT),
            style = InstrumentType.bodyStrong,
            color = TextPrimary,
        )
        Text(
            progressLine,
            modifier = Modifier.testTag(WorkoutTestTags.liftSets(lift.exercise.id)),
            style = InstrumentType.caption,
            color = TextSecondary,
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkoutTestTags.CURRENT_LIFT),
    ) {
        val detailsWidth = with(density) {
            measurer.measure(CurrentLiftCopy.DETAILS, style = InstrumentType.bodyStrong, softWrap = false).size.width.toDp()
        } + Metrics.space3 * 2 + Metrics.chevron + Metrics.space1
        val wordsWidth = with(density) {
            (maxWidth - Metrics.exerciseHeroImage - Metrics.space3 - detailsWidth - Metrics.space2).roundToPx()
        }.coerceAtLeast(1)
        val titleLines = measurer.measure(
            lift.exercise.name,
            style = InstrumentType.heroTitle,
            constraints = Constraints(maxWidth = wordsWidth),
        ).lineCount
        val stacked = density.fontScale >= 1.6f || titleLines > 2
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
            if (stacked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = identityModifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ExerciseThumb(
                            exercise = lift.exercise,
                            size = Metrics.workoutIdentityImage,
                            showBadge = false,
                            artPadding = Metrics.space1,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) { words() }
                    }
                    QuietButton(
                        text = CurrentLiftCopy.DETAILS,
                        onClick = onDetails,
                        trailing = TemperIcons.Chevron,
                        enabled = enabled,
                        modifier = Modifier.testTag(WorkoutTestTags.DETAILS),
                        spoken = "Exercise details",
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = identityModifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ExerciseThumb(
                            exercise = lift.exercise,
                            size = Metrics.exerciseHeroImage,
                            showBadge = false,
                            artPadding = Metrics.space2,
                        )
                        Column(
                            modifier = Modifier.padding(top = Metrics.space1),
                            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                        ) { words() }
                    }
                    QuietButton(
                        text = CurrentLiftCopy.DETAILS,
                        onClick = onDetails,
                        trailing = TemperIcons.Chevron,
                        enabled = enabled,
                        modifier = Modifier.testTag(WorkoutTestTags.DETAILS),
                        spoken = "Exercise details",
                    )
                }
            }
            SetTypeToggle(
                enabled = enabled,
                warmup = draftWarmup,
                onWarmup = onWarmup,
            )
        }
    }
}

/**
 * Working | Warm-up as one two-way radio. Selected is a Volt outline on a dim Volt
 * tint, never a filled block, so the one filled act on the floor stays Log set.
 */
@Composable
internal fun SetTypeToggle(
    enabled: Boolean,
    warmup: Boolean,
    onWarmup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag(WorkoutTestTags.SET_TYPE),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        InstrumentChip(
            label = "Working",
            selected = !warmup,
            onClick = { onWarmup(false) },
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.WORKING_CHIP),
            role = Role.RadioButton,
            spoken = if (warmup) "Working set, not selected" else "Working set, selected",
            enabled = enabled,
        )
        InstrumentChip(
            label = "Warm-up",
            selected = warmup,
            onClick = { onWarmup(true) },
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.WARMUP_CHIP),
            role = Role.RadioButton,
            spoken = if (warmup) "Warm-up set, selected" else "Warm-up set, not selected",
            enabled = enabled,
        )
    }
}
