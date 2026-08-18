package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.ProgressionAction
import com.sinura.personaltrainer.domain.toKgLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Personal Trainer", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Local strength tracker · kg only",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            if (state.inProgress != null) {
                PrimaryGymButton(
                    text = "Resume workout",
                    onClick = { onResumeWorkout(state.inProgress!!.id) },
                )
            } else {
                PrimaryGymButton(text = "Start workout", onClick = onStartWorkout)
            }
        }
        item {
            TextButton(onClick = onOpenRoutines) { Text("My routines") }
            TextButton(onClick = onOpenHistory) { Text("History") }
        }
        item {
            Text("Ready to progress", style = MaterialTheme.typography.titleLarge)
        }
        if (state.readyToProgress.isEmpty()) {
            item {
                EmptyState(
                    title = "No lifts queued",
                    body = "Finish a working set at target reps and the next suggested load shows up here.",
                )
            }
        } else {
            items(state.readyToProgress, key = { it.exerciseId }) { hint ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(hint.exerciseName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Last ${hint.lastWeightKg.toKgLabel()} × ${hint.lastReps} → next ${hint.suggestedWeightKg.toKgLabel()}",
                        )
                        if (hint.action == ProgressionAction.INCREASE) {
                            Text("+2.5 kg", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item {
            Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
        }
        if (state.recentSessions.isEmpty()) {
            item {
                EmptyState(
                    title = "No history yet",
                    body = "Complete a workout to start building a training log.",
                )
            }
        } else {
            items(state.recentSessions, key = { it.id }) { session ->
                Card(
                    onClick = { onOpenSession(session.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(session.routineName ?: "Workout", style = MaterialTheme.typography.titleMedium)
                        Text(dateFormat.format(Date(session.date)))
                        Text("${session.durationMinutes} min · ${session.workingVolumeKg().toKgLabel()} volume")
                    }
                }
            }
        }
    }
}
