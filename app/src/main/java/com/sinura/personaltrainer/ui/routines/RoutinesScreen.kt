package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.ScreenLoading
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: RoutinesViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Routines") })
        },
        floatingActionButton = {
            if (state.routines.isNotEmpty()) {
                FloatingActionButton(onClick = onCreateRoutine) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create routine")
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.routines.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                ) {
                    state.error?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = GymMetrics.listGap),
                        )
                    }
                    EmptyState(
                        title = "Build your first plan",
                        body = "Name a routine, add lifts and targets, then start it from Home.",
                        actionLabel = "Create a routine",
                        onAction = onCreateRoutine,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(
                        start = GymMetrics.screenPadding,
                        end = GymMetrics.screenPadding,
                        top = GymMetrics.screenPadding,
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    state.error?.let { message ->
                        item { GymErrorBanner(message) }
                    }
                    items(state.routines, key = { it.id }) { routine ->
                        RoutineRow(
                            routine = routine,
                            updatedLabel = routineUpdatedLabel(routine, dateFormat),
                            onOpen = { onOpenRoutine(routine.id) },
                            onDelete = { pendingDeleteId = routine.id },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val pendingName = state.routines.firstOrNull { it.id == id }?.name ?: "this routine"
        ConfirmActionDialog(
            title = "Delete $pendingName?",
            body = "This cannot be undone. Past workout history stays saved.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    updatedLabel: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val liftCount = routine.exercises.size
    val liftLabel = when (liftCount) {
        0 -> "No lifts yet"
        1 -> "1 lift"
        else -> "$liftCount lifts"
    }
    GymCard(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(liftLabel)
                        if (updatedLabel != null) {
                            append(" · ")
                            append(updatedLabel)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (routine.exercises.isNotEmpty()) {
                    Text(
                        routine.exercises.take(3).joinToString(" · ") { it.exercise.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${routine.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun routineUpdatedLabel(routine: Routine, dateFormat: DateFormat): String? {
    if (routine.updatedAt <= 0L) return null
    return "Updated ${dateFormat.format(Date(routine.updatedAt))}"
}
