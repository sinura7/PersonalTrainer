package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * One missed-work prompt (FND-017). Never guilt-repeats. Recurrence stays put.
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
                if (overdueCount == 1) {
                    "One planned session was not done. Recurrence does not change."
                } else {
                    "$overdueCount planned sessions were not done. Recurrence does not change."
                },
                style = InstrumentType.body,
                color = TextPrimary,
            )
            Text(
                "Choose once. This week will not ask again.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            TextButton(onClick = onMoveRemaining) {
                Text("Move remaining", style = InstrumentType.bodyStrong)
            }
            TextButton(onClick = onAdaptWeek) {
                Text("Adapt week", style = InstrumentType.bodyStrong)
            }
            TextButton(onClick = onKeepDates) {
                Text("Keep dates", style = InstrumentType.bodyStrong)
            }
            TextButton(onClick = onSkipMissed) {
                Text("Skip missed", style = InstrumentType.bodyStrong)
            }
        }
    }
}
