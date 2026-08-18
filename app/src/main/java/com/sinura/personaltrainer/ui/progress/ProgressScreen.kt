package com.sinura.personaltrainer.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onOpenLibrary: (String?) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenRoutines: () -> Unit,
    viewModel: ProgressViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var bodyView by rememberSaveable { mutableStateOf(BodyView.FRONT) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { CanonicalMuscle.valueOf(it) }.getOrNull() }
    val snapshot = state.snapshot

    Scaffold(
        topBar = { TopAppBar(title = { Text("Body map") }) },
    ) { padding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                EmptyState(
                    title = "Couldn’t load the map",
                    body = state.error ?: "Try switching the 7 / 14 / week window.",
                    actionLabel = "Start workout",
                    onAction = onStartWorkout,
                    modifier = Modifier.padding(padding),
                )
            }
            snapshot == null || !snapshot.hasAnyWorkingSets -> {
                EmptyState(
                    title = "Log work to heat the map",
                    body = "The map uses finished working sets. After a few sessions you’ll see which muscles are loaded.",
                    actionLabel = "Start workout",
                    onAction = onStartWorkout,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            "Training load from your logged working sets. Tap a muscle for detail.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        WindowPicker(selected = state.window, onSelect = viewModel::setWindow)
                    }
                    if (!snapshot.hasWindowWorkingSets) {
                        item {
                            Text(
                                "No working sets in ${state.window.label.lowercase()}. Lifetime recency is still shown below.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        BodyMapCard(
                            snapshot = snapshot,
                            view = bodyView,
                            onViewChange = { bodyView = it },
                            selected = selected,
                            onSelect = { selectedName = it.name },
                        )
                    }
                    if (state.recommendations.isNotEmpty()) {
                        item { Text("Recommendations", style = MaterialTheme.typography.titleLarge) }
                        items(state.recommendations, key = { it.id }) { rec ->
                            RecommendationCard(
                                recommendation = rec,
                                onClick = {
                                    dispatchRecommendation(
                                        recommendation = rec,
                                        onOpenLibrary = onOpenLibrary,
                                        onStartWorkout = onStartWorkout,
                                        onOpenRoutines = onOpenRoutines,
                                        onOpenProgress = { selectedName = rec.actionMuscle?.name },
                                    )
                                },
                            )
                        }
                    }
                    item { Text("Muscles", style = MaterialTheme.typography.titleLarge) }
                    items(snapshot.mapLoads, key = { it.muscle.name }) { load ->
                        MuscleHeatRow(
                            load = load,
                            selected = selected == load.muscle,
                            onClick = { selectedName = load.muscle.name },
                        )
                    }
                    snapshot.load(CanonicalMuscle.OTHER).takeIf { it.workingSets > 0 }?.let { other ->
                        item {
                            MuscleHeatRow(
                                load = other,
                                selected = selected == CanonicalMuscle.OTHER,
                                onClick = { selectedName = CanonicalMuscle.OTHER.name },
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { muscle ->
        snapshot?.let { snap ->
            MuscleDetailSheet(
                load = snap.load(muscle),
                unit = unit,
                windowLabel = state.window.label,
                onDismiss = { selectedName = null },
                onFindLifts = {
                    selectedName = null
                    onOpenLibrary(muscle.catalogLabel)
                },
            )
        }
    }
}

@Composable
private fun WindowPicker(
    selected: HeatWindow,
    onSelect: (HeatWindow) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HeatWindow.entries.forEach { window ->
            FilterChip(
                selected = selected == window,
                onClick = { onSelect(window) },
                label = { Text(window.shortLabel) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuscleDetailSheet(
    load: MuscleLoadSummary,
    unit: WeightUnit,
    windowLabel: String,
    onDismiss: () -> Unit,
    onFindLifts: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(load.muscle.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${load.band.legendLabel} · ${recencyLabel(load)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            DetailLine("Volume", "${load.volumeKg.toWeightLabel(unit)}  ·  $windowLabel")
            DetailLine("Working sets", "${load.workingSets}")
            DetailLine("Sessions", "${load.sessionCount}")
            if (load.exercises.isEmpty()) {
                Text(
                    "No working sets mapped here in this window.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Exercises", style = MaterialTheme.typography.titleLarge)
                load.exercises.forEach { exercise ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${exercise.volumeKg.toWeightLabel(unit)} volume · ${exercise.workingSets} working sets",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onFindLifts) {
                Text("Find ${load.muscle.displayName.lowercase()} lifts")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
