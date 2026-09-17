package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.EquipmentGlyphIcon
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.glyphFor
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Exercise identity and progress. Session totals belong to the named summary. */
@Composable
internal fun CurrentLiftCard(
    lift: SessionExercise,
    number: Int,
    total: Int,
    workingLogged: Int,
    canEdit: Boolean,
    onOpenSwitcher: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
    onSummary: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit = {},
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
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val overflow: @Composable () -> Unit = {
        LiftOverflowMenu(liftId = lift.id, canEdit = canEdit, onSkip = onSkip, onSwap = onSwap,
            onRemove = onRemove, onNotes = onNotes, onSummary = onSummary)
    }
    val details: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space1), verticalAlignment = Alignment.CenterVertically) {
            EquipmentGlyphIcon(glyph = glyphFor(lift.exercise.equipment), size = ThumbSize.chipGlyph)
            Text(equipment, style = InstrumentType.caption, color = TextSecondary)
        }
        Text(
            "${CurrentLiftCopy.liftOrdinal(number, total)} · ${CurrentLiftCopy.workingProgress(workingLogged, lift.targetSets)} working sets",
            modifier = Modifier.testTag(WorkoutTestTags.liftSets(lift.exercise.id)),
            style = InstrumentType.caption, color = TextSecondary,
        )
        Text("Switch exercise ›", style = InstrumentType.caption, color = TextPrimary)
    }
    val switchModifier = Modifier.heightIn(min = Metrics.touchMin)
        .clip(RoundedCornerShape(Radius.xs))
        .clickable(role = Role.Button, onClickLabel = "Switch exercise", onClick = onOpenSwitcher)
        .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
        .semantics(mergeDescendants = true) {
            contentDescription = "$spoken. Switch exercise"
            selected = true
        }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface2)
            .testTag(WorkoutTestTags.CURRENT_LIFT)
            .padding(Metrics.space4),
    ) {
        val compactTitleWidth = with(density) {
            (maxWidth - Metrics.workoutIdentityImage - Metrics.touchMin - Metrics.space2 - Metrics.space3).roundToPx()
        }.coerceAtLeast(1)
        val titleLines = measurer.measure(lift.exercise.name, style = InstrumentType.title,
            constraints = Constraints(maxWidth = compactTitleWidth)).lineCount
        if (density.fontScale >= 1.6f || titleLines > 2) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(lift.exercise.name, modifier = Modifier.weight(1f), style = InstrumentType.title, color = TextPrimary)
                    overflow()
                }
                Row(modifier = switchModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                    ExerciseThumb(exercise = lift.exercise, size = Metrics.workoutIdentityImage, showBadge = false, artPadding = Metrics.space1)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) { details() }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalAlignment = Alignment.Top) {
            Row(
                modifier = switchModifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
                verticalAlignment = Alignment.Top,
            ) {
                ExerciseThumb(
                    exercise = lift.exercise,
                    size = Metrics.workoutIdentityImage,
                    showBadge = false,
                    artPadding = Metrics.space1,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space1),
                ) {
                    Text(lift.exercise.name, style = InstrumentType.title, color = TextPrimary)
                    details()
                }
            }
            overflow()
        }
    }
}

/**
 * Packet G: every row stays visible. Skip parks the lift and moves on without deleting;
 * Swap and Remove on a logged lift are disabled with the reason, never silently gone —
 * the lift is part of what happened, and vanishing the control would read as permission.
 */
@Composable
internal fun LiftOverflowMenu(
    liftId: String,
    canEdit: Boolean,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
    onSummary: () -> Unit,
    onSkip: () -> Unit = {},
) {
    var menuOpen by rememberSaveable(liftId) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .size(Metrics.touchMin)
                .testTag(WorkoutTestTags.LIFT_OPTIONS),
        ) {
            Icon(
                TemperIcons.More,
                contentDescription = "Lift options",
                tint = TextSecondary,
            )
        }
        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Session summary", style = InstrumentType.bodyStrong, color = TextPrimary) },
                onClick = { menuOpen = false; onSummary() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        CurrentLiftCopy.SESSION_NOTES,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                },
                onClick = {
                    menuOpen = false
                    onNotes()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        CurrentLiftCopy.SKIP,
                        style = InstrumentType.bodyStrong,
                        color = TextPrimary,
                    )
                },
                onClick = {
                    menuOpen = false
                    onSkip()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            CurrentLiftCopy.SWAP,
                            style = InstrumentType.bodyStrong,
                            color = TextPrimary,
                        )
                        if (!canEdit) {
                            Text(
                                CurrentLiftCopy.EDIT_BLOCKED_REASON,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onSwap()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            CurrentLiftCopy.REMOVE,
                            style = InstrumentType.bodyStrong,
                            color = Danger,
                        )
                        if (!canEdit) {
                            Text(
                                CurrentLiftCopy.EDIT_BLOCKED_REASON,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                        }
                    }
                },
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
