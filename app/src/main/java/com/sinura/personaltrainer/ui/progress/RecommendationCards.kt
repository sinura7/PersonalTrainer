package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.RecommendationIntents
import com.sinura.personaltrainer.domain.RuleTraceCopy
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * A recommendation, as coaching rather than as a lint entry.
 *
 * Three things made these read like a numbered warning list. The rank was a display-sized
 * numeral in the accent colour that carried no information at all — it was the list index,
 * so it said only where the row happened to sit. The reason, which is the entire coaching
 * content, was clipped at two lines mid-sentence. And the tap dispatched to four different
 * destinations with nothing on the card to say which, so acting on a suggestion was a guess.
 */
@Composable
fun RecommendationCard(
    recommendation: TrainingRecommendation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not tappable when there is nowhere to go — see TrainingRecommendation.hasDestination.
    // A card that lights up under the finger and then does nothing is worse than a flat one.
    GymCard(onClick = onClick.takeIf { recommendation.hasDestination }, modifier = modifier) {
        // The category first, as a word. A stack of cards is skimmable by kind before any of
        // them is read, and the kind is never carried by colour alone.
        Kicker(recommendation.kicker)
        Text(recommendation.title, style = InstrumentType.title, color = TextPrimary)
        Text(recommendation.reason, style = InstrumentType.body, color = TextSecondary)
        recommendation.trace?.let { trace ->
            var showWhy by remember(recommendation.id) { mutableStateOf(false) }
            TextButton(onClick = { showWhy = !showWhy }) {
                Text(
                    if (showWhy) "Hide why" else "Why",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
            if (showWhy) {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                    RuleTraceCopy.lines(trace).forEach { line ->
                        Text(line, style = InstrumentType.caption, color = TextSecondary)
                    }
                }
            }
        }
        if (recommendation.hasDestination) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "${RecommendationIntents.actionLabel(recommendation)}  →",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
    }
}

