package com.sinura.personaltrainer.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.ui.components.EmptyState

@Composable
fun SessionScreen(
    workoutId: String,
    viewModel: TrainerViewModel,
    onFinished: () -> Unit,
    onExit: () -> Unit,
) {
    val workout = viewModel.workoutById(workoutId)
    var step by rememberSaveable { mutableIntStateOf(0) }

    if (workout == null) {
        EmptyState(
            title = "Session unavailable",
            body = "This workout could not be loaded.",
        )
        TextButton(onClick = onExit, modifier = Modifier.padding(20.dp)) {
            Text("Go back")
        }
        return
    }

    val total = workout.items.size
    val current = workout.items.getOrNull(step)
    val progress = if (total == 0) 0f else (step.toFloat() / total).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(workout.name, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onExit) { Text("Exit") }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

        if (current == null) {
            Text("Session complete", style = MaterialTheme.typography.headlineMedium)
            Text("Nice work. This workout is now on your progress board.")
            Button(
                onClick = {
                    viewModel.completeWorkout(workout)
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save and finish")
            }
        } else {
            Text("Exercise ${step + 1} of $total", style = MaterialTheme.typography.labelLarge)
            Text(current.exercise.name, style = MaterialTheme.typography.headlineMedium)
            Text("${current.sets} sets · ${current.reps} · ${current.restSeconds}s rest")
            Text(
                "${current.exercise.muscleGroup} · ${current.exercise.equipment}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current.exercise.instructions.forEachIndexed { index, line ->
                Text("${index + 1}. $line", style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = { step += 1 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (step + 1 >= total) "Finish last exercise" else "Next exercise")
            }
            if (step > 0) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Previous")
                }
            }
        }
    }
}
