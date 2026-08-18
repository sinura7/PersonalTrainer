package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.PrimaryGymButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingTargets by rememberSaveable { mutableStateOf(TargetDraft()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit routine") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Routine name") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                )
            }
            item {
                PrimaryGymButton(text = "Save routine details", onClick = viewModel::saveDetails)
                if (state.saved) {
                    Text("Saved.", color = MaterialTheme.colorScheme.primary)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                PrimaryGymButton(
                    text = "Add exercise",
                    onClick = { viewModel.setPickerVisible(true) },
                )
            }
            val exercises = state.routine?.exercises.orEmpty()
            if (exercises.isEmpty()) {
                item {
                    EmptyState(
                        title = "No exercises",
                        body = "Search the library or create a custom lift, then set target sets, reps, and kg.",
                    )
                }
            } else {
                itemsIndexed(exercises, key = { _, item -> item.id }) { index, item ->
                    RoutineExerciseCard(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < exercises.lastIndex,
                        onMoveUp = { viewModel.moveExercise(item.id, -1) },
                        onMoveDown = { viewModel.moveExercise(item.id, 1) },
                        onRemove = { viewModel.removeExercise(item.id) },
                        onSaveTargets = { sets, reps, weight, rest ->
                            viewModel.updateExercise(item.id, sets, reps, weight, rest)
                        },
                    )
                }
            }
        }
    }

    if (state.showExercisePicker) {
        Column {
            ExercisePickerSheet(
                query = state.searchQuery,
                results = state.searchResults,
                onQueryChange = viewModel::onSearchQuery,
                onSelect = { exercise ->
                    viewModel.addExercise(
                        exercise = exercise,
                        targetSets = pendingTargets.sets,
                        targetReps = pendingTargets.reps,
                        targetWeightKg = pendingTargets.weightKg,
                        restSeconds = pendingTargets.rest,
                    )
                },
                onCreate = { name, muscle ->
                    viewModel.createAndAddExercise(
                        customName = name,
                        muscleGroup = muscle,
                        targetSets = pendingTargets.sets,
                        targetReps = pendingTargets.reps,
                        targetWeightKg = pendingTargets.weightKg,
                        restSeconds = pendingTargets.rest,
                    )
                },
                onDismiss = { viewModel.setPickerVisible(false) },
            )
        }
    }
}

@Composable
private fun RoutineExerciseCard(
    item: RoutineExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSaveTargets: (Int, Int, Double?, Int) -> Unit,
) {
    var sets by rememberSaveable(item.id) { mutableStateOf(item.targetSets.toString()) }
    var reps by rememberSaveable(item.id) { mutableStateOf(item.targetReps.toString()) }
    var weight by rememberSaveable(item.id) {
        mutableStateOf(item.targetWeightKg?.toString().orEmpty())
    }
    var rest by rememberSaveable(item.id) { mutableStateOf(item.restSeconds.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.exercise.muscleGroup, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Sets", sets, Modifier.weight(1f)) { sets = it.filter(Char::isDigit) }
                SmallNumberField("Reps", reps, Modifier.weight(1f)) { reps = it.filter(Char::isDigit) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Target kg", weight, Modifier.weight(1f)) { value ->
                    weight = value.filter { it.isDigit() || it == '.' }
                }
                SmallNumberField("Rest s", rest, Modifier.weight(1f)) { rest = it.filter(Char::isDigit) }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(
                    onClick = {
                        onSaveTargets(
                            sets.toIntOrNull() ?: item.targetSets,
                            reps.toIntOrNull() ?: item.targetReps,
                            weight.toDoubleOrNull(),
                            rest.toIntOrNull() ?: item.restSeconds,
                        )
                    },
                ) { Text("Update targets") }
            }
        }
    }
}

@Composable
private fun SmallNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private data class TargetDraft(
    val sets: Int = 3,
    val reps: Int = 5,
    val weightKg: Double? = null,
    val rest: Int = 90,
)
