package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.EquipmentChip
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim

/**
 * Packet C: the one lift on the gym floor.
 *
 * Picture, name, kit / load meaning, `Lift n/m`, working `a/b`. Tap opens
 * the switcher. Overflow is 48 dp and never a second Volt.
 */
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
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val maxHeight = if (fontScale >= 2f) Metrics.currentLiftMaxLargeType else Metrics.currentLiftMax
    val meaning = LoadClass.of(lift.exercise.loadType).weightMeaning
    val spoken = CurrentLiftCopy.cardSpoken(
        name = lift.exercise.name,
        number = number,
        total = total,
        workingLogged = workingLogged,
        targetSets = lift.targetSets,
        equipmentLabel = lift.exercise.equipment.label,
        meaning = meaning,
    )
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.commit, max = maxHeight)
            .clip(shape)
            .background(VoltDim)
            .border(Metrics.emphasisBorder, Volt, shape)
            .testTag(WorkoutTestTags.CURRENT_LIFT)
            .padding(Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpenSwitcher)
                .testTag(WorkoutTestTags.liftCard(lift.exercise.id))
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    selected = true
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EquipmentChip(lift.exercise.equipment)
                    Text(
                        CurrentLiftCopy.secondaryLine(lift.exercise.equipment.label, meaning),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrentLiftCopy.liftOrdinal(number, total),
                    style = InstrumentType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Text(
                    CurrentLiftCopy.workingProgress(workingLogged, lift.targetSets),
                    modifier = Modifier.testTag(WorkoutTestTags.liftSets(lift.exercise.id)),
                    style = InstrumentType.numeralSm,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
        }
        LiftOverflowMenu(
            liftId = lift.id,
            canEdit = canEdit,
            onSwap = onSwap,
            onRemove = onRemove,
            onNotes = onNotes,
        )
    }
}

@Composable
internal fun LiftOverflowMenu(
    liftId: String,
    canEdit: Boolean,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onNotes: () -> Unit,
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
                Icons.Outlined.MoreVert,
                contentDescription = "Lift options",
                tint = TextSecondary,
            )
        }
        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            if (canEdit) {
                DropdownMenuItem(
                    text = {
                        Text(
                            CurrentLiftCopy.SWAP,
                            style = InstrumentType.bodyStrong,
                            color = TextPrimary,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onSwap()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            CurrentLiftCopy.REMOVE,
                            style = InstrumentType.bodyStrong,
                            color = Danger,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}
