package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.RecommendationAction
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

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
    GymCard(onClick = onClick, modifier = modifier) {
        // The category first, as a word. A stack of cards is skimmable by kind before any of
        // them is read, and the kind is never carried by colour alone.
        Kicker(recommendation.kicker)
        Text(recommendation.title, style = InstrumentType.title, color = TextPrimary)
        Text(recommendation.reason, style = InstrumentType.body, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "${actionLabel(recommendation)}  →",
                style = InstrumentType.bodyStrong,
                color = Volt,
            )
        }
    }
}

/**
 * Names the destination before the tap.
 *
 * Derived from the same [RecommendationAction] that [dispatchRecommendation] switches on, so
 * the label cannot describe one destination while the tap goes to another — and the muscle
 * it names is literally the catalogue filter the library will open with.
 */
private fun actionLabel(recommendation: TrainingRecommendation): String =
    when (recommendation.action) {
        RecommendationAction.OPEN_LIBRARY_MUSCLE -> {
            val muscle = recommendation.actionMuscle ?: CanonicalMuscle.OTHER
            "Find ${muscle.catalogLabel.lowercase()} lifts"
        }
        RecommendationAction.OPEN_EXERCISE ->
            recommendation.actionExerciseName?.let { "Open $it" } ?: "Show on the map"
        RecommendationAction.START_WORKOUT -> "Start workout"
        RecommendationAction.OPEN_ROUTINES -> "Open routines"
        RecommendationAction.OPEN_BODY_MAP -> "Show on the map"
        null -> "Show on the map"
    }

fun dispatchRecommendation(
    recommendation: TrainingRecommendation,
    onOpenLibrary: (CanonicalMuscle?) -> Unit,
    onOpenExercise: (String) -> Unit,
    /** Opens the start-options sheet. The interstitial it used to navigate to is gone. */
    onStartOptions: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    when (recommendation.action) {
        // The muscle itself, not its label. Display text used to be the wire format here, so a
        // copy edit could break the filter with nothing failing — the Library would just open
        // showing everything.
        RecommendationAction.OPEN_LIBRARY_MUSCLE -> onOpenLibrary(recommendation.actionMuscle)
        // A card that names a lift opens that lift. Falling back to the muscle filter would
        // undo the whole point of naming it.
        RecommendationAction.OPEN_EXERCISE ->
            recommendation.actionExerciseId?.let(onOpenExercise) ?: onOpenProgress()
        RecommendationAction.START_WORKOUT -> onStartOptions()
        RecommendationAction.OPEN_ROUTINES -> onOpenRoutines()
        RecommendationAction.OPEN_BODY_MAP -> onOpenProgress()
        null -> onOpenProgress()
    }
}
