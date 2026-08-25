package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.MissedWorkCopy
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * One missed-work prompt (FND-017). Never guilt-repeats. Recurrence stays put;
 * the gym-floor copy never teaches that word. Keep-the-dates is the one Volt.
 */
@Composable
fun MissedWorkCard(
    overdueCount: Int,
    onMoveRemaining: () -> Unit,
    onAdaptWeek: () -> Unit,
    onKeepDates: () -> Unit,
    onSkipMissed: () -> Unit,
) {
    GymCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            Kicker("Missed this week")
            Text(
                MissedWorkCopy.body(overdueCount),
                style = InstrumentType.body,
                color = TextPrimary,
            )
            Text(
                MissedWorkCopy.CAPTION,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            PrimaryGymButton(
                text = MissedWorkCopy.KEEP,
                onClick = onKeepDates,
            )
            TextButton(
                onClick = onMoveRemaining,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(MissedWorkCopy.MOVE, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
            TextButton(
                onClick = onAdaptWeek,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(MissedWorkCopy.ADAPT, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
            TextButton(
                onClick = onSkipMissed,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(MissedWorkCopy.SKIP, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}
