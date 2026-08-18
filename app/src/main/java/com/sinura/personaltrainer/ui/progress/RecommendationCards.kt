package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.RecommendationAction
import com.sinura.personaltrainer.domain.RecommendationPriority
import com.sinura.personaltrainer.domain.TrainingRecommendation

@Composable
fun RecommendationCard(
    recommendation: TrainingRecommendation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = recommendationColors(recommendation.priority)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                recommendation.priority.name.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(recommendation.title, style = MaterialTheme.typography.titleMedium)
            if (!compact) {
                Text(recommendation.reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun recommendationColors(priority: RecommendationPriority): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (priority) {
        RecommendationPriority.HIGH -> scheme.errorContainer to scheme.onErrorContainer
        RecommendationPriority.ATTENTION -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        RecommendationPriority.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
}

fun dispatchRecommendation(
    recommendation: TrainingRecommendation,
    onOpenLibrary: (String?) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    when (recommendation.action) {
        RecommendationAction.OPEN_LIBRARY_MUSCLE ->
            onOpenLibrary(recommendation.actionMuscle?.catalogLabel ?: CanonicalMuscle.OTHER.catalogLabel)
        RecommendationAction.START_WORKOUT -> onStartWorkout()
        RecommendationAction.OPEN_ROUTINES -> onOpenRoutines()
        RecommendationAction.OPEN_BODY_MAP -> onOpenProgress()
        null -> onOpenProgress()
    }
}
