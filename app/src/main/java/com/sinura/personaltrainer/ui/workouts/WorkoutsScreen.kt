package com.sinura.personaltrainer.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.data.WorkoutCategory
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.WorkoutCard
import com.sinura.personaltrainer.ui.components.label

@Composable
fun WorkoutsScreen(
    viewModel: TrainerViewModel,
    onOpenWorkout: (String) -> Unit,
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf("ALL") }
    val filtered = workouts.filter { selected == "ALL" || it.category.name == selected }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Workouts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selected == "ALL",
                    onClick = { selected = "ALL" },
                    label = { Text("All") },
                )
            }
            items(WorkoutCategory.entries) { category ->
                FilterChip(
                    selected = selected == category.name,
                    onClick = { selected = category.name },
                    label = { Text(category.label()) },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = "No workouts in this filter",
                body = "Try another category or add a new program later.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { workout ->
                    WorkoutCard(workout = workout, onClick = { onOpenWorkout(workout.id) })
                }
            }
        }
    }
}
