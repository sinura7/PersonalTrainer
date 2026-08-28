package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.sinura.personaltrainer.domain.RecommendationIntents
import com.sinura.personaltrainer.domain.RuleTraceCopy
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
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
 *
 * Why is a dialog, not an expand inside the card: a GymCard click wrapping a TextButton
 * nested the two taps, and expanding the trace grew the window the card is.
 */
@Composable
fun RecommendationCard(
    recommendation: TrainingRecommendation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showWhy by remember(recommendation.id) { mutableStateOf(false) }
    // Destination tap lives on the identity column, never on the card wrapping Why.
    GymCard(modifier = modifier) {
        Column(
            modifier = if (recommendation.hasDestination) {
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            // The category first, as a word. A stack of cards is skimmable by kind before any of
            // them is read, and the kind is never carried by colour alone.
            Kicker(recommendation.kicker)
            Text(
                recommendation.title,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                recommendation.reason,
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (recommendation.hasDestination) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "${RecommendationIntents.actionLabel(recommendation)}  →",
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        recommendation.trace?.let {
            TextButton(
                onClick = { showWhy = true },
                modifier = Modifier.heightIn(min = Metrics.touchMin),
            ) {
                Text(
                    "Why",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
    }
    if (showWhy) {
        recommendation.trace?.let { trace ->
            ConfirmActionDialog(
                title = "Why",
                body = RuleTraceCopy.lines(trace).joinToString("\n"),
                confirmLabel = "OK",
                onConfirm = { showWhy = false },
                onDismiss = { showWhy = false },
                dismissLabel = null,
            )
        }
    }
}
