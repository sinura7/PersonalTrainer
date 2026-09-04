package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * What to do when you leave a live session from Active Workout.
 *
 * Keep is the gym-floor leave: the session stays live, rest keeps running, and the bar
 * (or the rest notification) is the way back. Stay is a real control, not a caption.
 * Discard is the expensive alternative — Danger ink, never a second Volt slab — and the
 * caller still routes it through its own named confirm so destroy is never one tap.
 *
 * Stacked full-width buttons, same anatomy as [ResumeOrDiscardDialog]: trailing
 * TextButtons on a dark field read as footnotes, and "Keep and exit" is the act.
 * Tapping outside stays.
 */
@Composable
fun LeaveWorkoutDialog(
    onKeepAndExit: () -> Unit,
    onStay: () -> Unit,
    onDiscardInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave workout?", style = InstrumentType.title) },
        text = {
            Text(
                "Your sets and rest keep running. The bar at the bottom of any other " +
                    "screen brings you back — or tap the rest notification.",
                style = InstrumentType.body,
                color = TextSecondary,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                PrimaryGymButton(text = "Keep and exit", onClick = onKeepAndExit)
                SecondaryGymButton(text = "Stay", onClick = onStay)
                DangerGymButton(text = "Discard this workout instead", onClick = onDiscardInstead)
            }
        },
    )
}


