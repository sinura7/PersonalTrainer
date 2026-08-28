package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.domain.MissedWorkCopy
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
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
            SecondaryGymButton(
                text = MissedWorkCopy.MOVE,
                onClick = onMoveRemaining,
            )
            SecondaryGymButton(
                text = MissedWorkCopy.ADAPT,
                onClick = onAdaptWeek,
            )
            SecondaryGymButton(
                text = MissedWorkCopy.SKIP,
                onClick = onSkipMissed,
            )
        }
    }
}
