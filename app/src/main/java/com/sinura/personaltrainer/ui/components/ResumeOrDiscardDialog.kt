package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * What to do when you tap Start and something is already running.
 *
 * The app used to answer this for you: starting a day while a session was open silently
 * returned that session, so tapping Thursday's Pull dropped you into Tuesday's half-finished
 * Legs with nothing on screen explaining the substitution. Both real answers are here instead,
 * named, with the destructive one carrying the cost in its own words.
 *
 * Stacked full-width buttons rather than the usual confirm/dismiss pair: these are two
 * alternatives, not an action and its cancel, and side-by-side text buttons would make the
 * longer one look like an afterthought. Tapping outside cancels.
 */
@Composable
fun ResumeOrDiscardDialog(
    onResume: () -> Unit,
    onDiscardAndStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A workout is already in progress", style = InstrumentType.title) },
        text = {
            Text(
                "Pick one — this one, or the session you were in the middle of.",
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
                    text = LiveBarCopy.resumeLabel(LiveBarKind.WORKOUT),
                    onClick = onResume,
                )
                DangerGymButton(text = "Discard it and start this", onClick = onDiscardAndStart)
            }
        },
    )
}
