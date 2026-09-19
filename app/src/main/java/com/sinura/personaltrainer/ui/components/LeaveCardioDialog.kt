package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * What to do when system back leaves a live cardio session.
 *
 * Same stacked anatomy as [EndWorkoutDialog]: Leave running is the gym-floor leave
 * (the session stays live, the bar is the way back) and is the one Volt. Stay is a
 * real control. Discard is Danger ink, never a second Volt slab, and the caller still
 * routes it through its own named confirm so destroy is never one tap.
 *
 * On the live screen itself, Finish is the Volt — this dialog is only the back/leave
 * fork. Tapping outside stays.
 */
@Composable
fun LeaveCardioDialog(
    onLeaveRunning: () -> Unit,
    onStay: () -> Unit,
    onDiscardInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CardioCopy.LEAVE_TITLE, style = InstrumentType.title) },
        text = {
            Text(
                CardioCopy.LEAVE_BODY,
                style = InstrumentType.body,
                color = TextSecondary,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                PrimaryGymButton(
                    text = CardioCopy.LEAVE_RUNNING,
                    onClick = onLeaveRunning,
                    modifier = Modifier.testTag(LeaveCardioTags.LEAVE_RUNNING),
                )
                SecondaryGymButton(
                    text = CardioCopy.STAY,
                    onClick = onStay,
                    modifier = Modifier.testTag(LeaveCardioTags.STAY),
                )
                SecondaryGymButton(
                    text = CardioCopy.DISCARD_INSTEAD,
                    onClick = onDiscardInstead,
                    modifier = Modifier.testTag(LeaveCardioTags.DISCARD),
                    contentColor = Danger,
                )
            }
        },
    )
}

object LeaveCardioTags {
    const val LEAVE_RUNNING = "leave-cardio-leave-running"
    const val STAY = "leave-cardio-stay"
    const val DISCARD = "leave-cardio-discard"
}
