package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentMenuItem
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * The session's secondary actions, behind one ⋮ in the header.
 *
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
    onSwitch: () -> Unit,
    /** The lift's own screen, also on its picture; here too so a menu reader finds it. */
    onDetails: () -> Unit,
    onSkip: () -> Unit = {},
    enabled: Boolean = true,
) {
    var menuOpen by rememberSaveable(liftId) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) menuOpen = false }
    Box {
        IconButton(
            enabled = enabled,
            onClick = { menuOpen = true },
            modifier = Modifier
                .size(Metrics.touchMin)
                .testTag(WorkoutTestTags.LIFT_OPTIONS),
        ) {
            Icon(
                TemperIcons.More,
                contentDescription = "Workout options",
                tint = TextSecondary,
            )
        }
        InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.SWITCH,
                leadingIcon = TemperIcons.ChevronDown,
                onClick = { menuOpen = false; onSwitch() },
            )
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.DETAILS_SPOKEN,
                leadingIcon = TemperIcons.Body,
                onClick = { menuOpen = false; onDetails() },
            )
            InstrumentMenuItem(
                spokenLabel = "Session summary",
                leadingIcon = TemperIcons.History,
                onClick = { menuOpen = false; onSummary() },
            )
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.SESSION_NOTES,
                leadingIcon = TemperIcons.Log,
                onClick = {
                    menuOpen = false
                    onNotes()
                },
            )
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.SKIP,
                leadingIcon = TemperIcons.Chevron,
                onClick = {
                    menuOpen = false
                    onSkip()
                },
            )
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.SWAP,
                leadingIcon = OutlinedMarks.PlaylistAdd,
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onSwap()
                },
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
            )
            InstrumentMenuItem(
                spokenLabel = CurrentLiftCopy.REMOVE,
                leadingIcon = TemperIcons.Delete,
                iconTint = Danger,
                enabled = canEdit,
                onClick = {
                    menuOpen = false
                    onRemove()
                },
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
            )
        }
    }
}
