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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
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
        topBar = { TopAppBar(title = { Text("Body") }) },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    WindowPicker(selected = state.window, onSelect = viewModel::setWindow)
                    EmptyState(
                        title = "Couldn’t load the map",
                        body = "Switch the 7 / 14 / week window and try again.",
                        actionLabel = "Start workout",
                        onAction = onStartWorkout,
                    )
                }
            }
            snapshot == null || !snapshot.hasAnyWorkingSets -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    WindowPicker(selected = state.window, onSelect = viewModel::setWindow)
                    EmptyState(
                        title = "See what you trained",
                        body = "Working-set volume lights the map for the window you pick.",
                        actionLabel = "Start workout",
                        onAction = onStartWorkout,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    item {
                        WindowPicker(selected = state.window, onSelect = viewModel::setWindow)
                    }
                    if (!snapshot.hasWindowWorkingSets) {
                        item {
                            Text(
                                "No working sets in ${state.window.label.lowercase()}. Recency still shows below.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
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
                        item { GymSectionHeader("Recommended") }
                        itemsIndexed(state.recommendations, key = { _, rec -> rec.id }) { index, rec ->
                            RecommendationCard(
                                recommendation = rec,
                                rank = index + 1,
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
                    item { GymSectionHeader("Muscles") }
                    items(snapshot.mapLoads, key = { it.muscle.name }) { load ->
                        MuscleHeatRow(
                            load = load,
                            selected = selected == load.muscle,
                            onClick = { selectedName = load.muscle.name },
                            volumeLabel = load.volumeKg.toWeightLabel(unit),
                        )
                    }
                    snapshot.load(CanonicalMuscle.OTHER).takeIf { it.workingSets > 0 }?.let { other ->
                        item {
                            MuscleHeatRow(
                                load = other,
                                selected = selected == CanonicalMuscle.OTHER,
                                onClick = { selectedName = CanonicalMuscle.OTHER.name },
                                volumeLabel = other.volumeKg.toWeightLabel(unit),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeatWindow.entries.forEach { window ->
            FilterChip(
                selected = selected == window,
                onClick = { onSelect(window) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        when (window) {
                            HeatWindow.LAST_7_DAYS -> "7"
                            HeatWindow.LAST_14_DAYS -> "14"
                            HeatWindow.CURRENT_WEEK -> "Week"
                        },
                    )
                },
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
                .padding(horizontal = GymMetrics.screenPadding)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(load.muscle.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${load.band.legendLabel} load",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            DetailLine("Volume", "${load.volumeKg.toWeightLabel(unit)}  ·  $windowLabel")
            DetailLine("Working sets", "${load.workingSets}")
            DetailLine("Last trained", recencyLabel(load))
            DetailLine("Sessions", "${load.sessionCount}")
            if (load.exercises.isEmpty()) {
                Text(
                    "No working sets mapped here in this window.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Contributors", style = MaterialTheme.typography.titleLarge)
                load.exercises.forEach { exercise ->
                    GymCard {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${exercise.volumeKg.toWeightLabel(unit)} · ${exercise.workingSets} working sets",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            SecondaryGymButton(
                text = "Find ${load.muscle.displayName.lowercase()} lifts",
                onClick = onFindLifts,
            )
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
