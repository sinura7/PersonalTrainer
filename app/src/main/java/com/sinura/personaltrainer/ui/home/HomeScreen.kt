package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.ui.components.WorkoutCard

@Composable
fun HomeScreen(
    viewModel: TrainerViewModel,
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val weekMs = 7L * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()
    val completedThisWeek = history.count { now - it.completedAtMillis <= weekMs }
    val goal = profile.weeklyWorkoutGoal.coerceAtLeast(1)
    val featured = workouts.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hey ${profile.displayName}", style = MaterialTheme.typography.headlineLarge)
        Text(profile.goal, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("This week", style = MaterialTheme.typography.titleLarge)
                Text("$completedThisWeek of $goal workouts")
                LinearProgressIndicator(
                    progress = { (completedThisWeek.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (history.isEmpty()) {
                    Text(
                        "No sessions logged yet. Start a workout to begin tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Last session: ${history.first().workoutName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (featured == null) {
            Text("No workouts are available yet.")
        } else {
            Text("Today's plan", style = MaterialTheme.typography.titleLarge)
            WorkoutCard(workout = featured, onClick = { onOpenWorkout(featured.id) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStartWorkout(featured.id) }) {
                    Text("Start workout")
                }
                TextButton(onClick = { onOpenWorkout(featured.id) }) {
                    Text("View details")
                }
            }
        }
    }
}
