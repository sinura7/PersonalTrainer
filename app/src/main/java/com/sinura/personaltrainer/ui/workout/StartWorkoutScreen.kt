package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartWorkoutScreen(
    onBack: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    viewModel: StartWorkoutViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.inProgress?.let { session ->
                item {
                    PrimaryGymButton(
                        text = "Resume ${session.routineName ?: "workout"}",
                        onClick = { onWorkoutStarted(session.id) },
                    )
                }
                item {
                    Text(
                        "Finish or discard the current session before starting another.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.inProgress == null) {
                item {
                    PrimaryGymButton(
                        text = "Free workout",
                        onClick = { viewModel.startFree(onWorkoutStarted) },
                    )
                }
                item {
                    Text("Start a routine", style = MaterialTheme.typography.titleLarge)
                }
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.inProgress == null && state.routines.isEmpty()) {
                item {
                    EmptyState(
                        title = "No routines yet",
                        body = "Start a free workout and add lifts as you go, or build a routine first.",
                    )
                }
            } else if (state.inProgress == null) {
                items(state.routines, key = { it.id }) { routine ->
                    val empty = routine.exercises.isEmpty()
                    Card(
                        onClick = {
                            if (!empty) viewModel.startRoutine(routine.id, onWorkoutStarted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (empty) "Add at least one lift before starting" else "${routine.exercises.size} exercises",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
