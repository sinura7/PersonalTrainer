package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date

@Composable
fun ProgressScreen(viewModel: TrainerViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val weekMs = 7L * 24L * 60L * 60L * 1000L
    val now = System.currentTimeMillis()
    val completedThisWeek = history.count { now - it.completedAtMillis <= weekMs }
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Progress", style = MaterialTheme.typography.headlineMedium)
            Text(
                "$completedThisWeek / ${profile.weeklyWorkoutGoal} workouts this week",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                EmptyState(
                    title = "No history yet",
                    body = "Complete a session and it will show up here.",
                )
            }
        } else {
            items(history, key = { it.id }) { session ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
                        Text(dateFormat.format(Date(session.completedAtMillis)))
                        Text("${session.durationMinutes} minutes")
                    }
                }
            }
        }
    }
}
