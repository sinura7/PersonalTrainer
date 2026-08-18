package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.data.Difficulty
import com.sinura.personaltrainer.data.Workout
import com.sinura.personaltrainer.data.WorkoutCategory

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(workout.name, style = MaterialTheme.typography.titleLarge)
            Text(workout.description, style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(onClick = {}, enabled = false, label = { Text("${workout.durationMinutes} min") })
                AssistChip(onClick = {}, enabled = false, label = { Text(workout.difficulty.label()) })
                AssistChip(onClick = {}, enabled = false, label = { Text(workout.category.label()) })
            }
        }
    }
}

fun Difficulty.label(): String = when (this) {
    Difficulty.BEGINNER -> "Beginner"
    Difficulty.INTERMEDIATE -> "Intermediate"
    Difficulty.ADVANCED -> "Advanced"
}

fun WorkoutCategory.label(): String = when (this) {
    WorkoutCategory.STRENGTH -> "Strength"
    WorkoutCategory.CARDIO -> "Cardio"
    WorkoutCategory.MOBILITY -> "Mobility"
    WorkoutCategory.FULL_BODY -> "Full body"
}
