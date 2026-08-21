package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseRow
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenExercise: (String) -> Unit,
    initialMuscle: String? = null,
    viewModel: ExerciseLibraryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Held as an id rather than the row's own boolean so the sheet survives a rotation, and
    // so it closes by itself if the lift underneath it is deleted.
    var overflowExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialMuscle) {
        if (!initialMuscle.isNullOrBlank()) {
            viewModel.applyMuscleFilter(initialMuscle)
        }
    }

    Scaffold(
        topBar = {
            // A back arrow now that Library is pushed rather than a tab. Without one, arriving
            // here from a coach card would be a one-way trip to a screen with no visible exit
            // except the system gesture.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pit)
                    .padding(end = Metrics.gutter, bottom = Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                    )
                }
                Text(
                    "Library",
                    modifier = Modifier.weight(1f),
                    style = InstrumentType.display,
                    color = TextPrimary,
                )
            }
        },
        floatingActionButton = {
            if (state.visibleExercises.isNotEmpty()) {
                FloatingActionButton(
                    onClick = viewModel::openCreate,
                    containerColor = Volt,
                    contentColor = Pit,
                    // Depth is the surface ladder here, and a shadow on near-black is
                    // invisible anyway.
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create exercise")
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Search and filters stay put while the catalog moves under them, and the
                    // two banners cannot be scrolled away from — an error injected into the
                    // list can be read only if you happen to be at the top of it.
                    ExerciseSearchField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = "Name or muscle",
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                    )
                    LazyRow(
                        modifier = Modifier.padding(top = Metrics.space3, bottom = Metrics.space4),
                        contentPadding = PaddingValues(horizontal = Metrics.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        item(key = "all") {
                            InstrumentChip(
                                label = "All",
                                selected = state.selectedGroup == null,
                                onClick = { viewModel.onGroupSelected(null) },
                            )
                        }
                        items(state.muscleFilters, key = { it }) { group ->
                            InstrumentChip(
                                label = group,
                                selected = state.selectedGroup == group,
                                onClick = { viewModel.onGroupSelected(group) },
                            )
                        }
                    }
                    state.message?.let { note ->
                        GymStatusBanner(
                            note,
                            modifier = Modifier
                                .padding(horizontal = Metrics.gutter)
                                .padding(bottom = Metrics.space3),
                        )
                    }
                    state.error?.let { err ->
                        GymErrorBanner(
                            err,
                            modifier = Modifier
                                .padding(horizontal = Metrics.gutter)
                                .padding(bottom = Metrics.space3),
                        )
                    }
                    if (state.visibleExercises.isEmpty()) {
                        val filtered = state.exercises.isNotEmpty()
                        EmptyState(
                            title = if (filtered) "No matches" else "Library is empty",
                            body = if (filtered) {
                                "Clear search or filters, or add a custom lift."
                            } else {
                                "Add a custom lift to start the catalog."
                            },
                            modifier = Modifier.padding(horizontal = Metrics.gutter),
                            actionLabel = "Create exercise",
                            onAction = viewModel::openCreate,
                        )
                    } else {
                        HairlineDivider(startIndent = 0.dp)
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            // Rows run edge to edge; the screen margin lives inside them.
                            contentPadding = PaddingValues(bottom = Metrics.fabClearance),
                        ) {
                            itemsIndexed(
                                state.visibleExercises,
                                key = { _, exercise -> exercise.id },
                            ) { index, exercise ->
                                Column(modifier = Modifier.animateItem()) {
                                    LibraryRow(
                                        exercise = exercise,
                                        onOpen = { onOpenExercise(exercise.id) },
                                        onAddToRoutine = { viewModel.openAddToRoutine(exercise) },
                                        onOverflow = { overflowExerciseId = exercise.id },
                                    )
                                    if (index < state.visibleExercises.lastIndex) HairlineDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.exercises.firstOrNull { it.id == overflowExerciseId }?.let { exercise ->
        LiftOverflowSheet(
            exercise = exercise,
            onEdit = {
                overflowExerciseId = null
                viewModel.openEdit(exercise)
            },
            onDelete = {
                overflowExerciseId = null
                viewModel.requestDelete(exercise)
            },
            onDismiss = { overflowExerciseId = null },
        )
    }

    state.editor?.let { draft ->
        ExerciseEditorSheet(
            draft = draft,
            muscleOptions = MuscleGroups.editorOptions(state.exercises),
            error = state.error,
            onDraftChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.pendingDelete?.let { exercise ->
        ConfirmActionDialog(
            title = "Delete ${exercise.name}?",
            body = "This removes it from your library. Lifts used in routines or history can’t be deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

    state.blockedDelete?.let { (exercise, usage) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Can’t delete ${exercise.name}", style = InstrumentType.title) },
            text = {
                Text(
                    "This lift is used in ${usage.reason()}. Remove it from those routines first, or keep it so your history stays intact.",
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("OK", style = InstrumentType.bodyStrong, color = Volt)
                }
            },
        )
    }

    state.addToRoutine?.let { exercise ->
        AddToRoutineSheet(
            exercise = exercise,
            routines = state.routines,
            onSelect = viewModel::addToRoutine,
            onCreateRoutine = {
                viewModel.dismissAddToRoutine()
                onCreateRoutine()
            },
            onDismiss = viewModel::dismissAddToRoutine,
        )
    }
}

/**
 * A catalog entry, with one action on it.
 *
 * A custom lift used to carry three 48dp icon buttons — add, edit, delete — all tinted the
 * same grey as the metadata beside them, so a third of the row was administration and
 * deleting a lift sat one mistap away from adding it to a routine. Adding to a routine is
 * the thing anyone comes here to do; managing the lift itself is rare, and rare belongs
 * behind an overflow.
 */
@Composable
private fun LibraryRow(
    exercise: Exercise,
    onOpen: () -> Unit,
    onAddToRoutine: () -> Unit,
    onOverflow: () -> Unit,
) {
    ExerciseRow(
        name = exercise.name,
        muscleGroup = exercise.muscleGroup,
        onClick = onOpen,
        tag = if (exercise.isCustom) "Custom" else null,
    ) {
        IconButton(onClick = onAddToRoutine) {
            Icon(
                Icons.Outlined.PlaylistAdd,
                contentDescription = "Add ${exercise.name} to a routine",
                tint = TextSecondary,
            )
        }
        if (exercise.isCustom) {
            IconButton(onClick = onOverflow) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More for ${exercise.name}",
                    tint = TextTertiary,
                )
            }
        }
    }
}

/**
 * Edit and delete, one step back from the row.
 *
 * A sheet rather than a dropdown menu: Material's menu draws on `surfaceContainer`, which
 * in this theme is the window colour, and it separates itself from the screen behind with a
 * shadow — which on near-black is nothing at all. It also buys 56dp rows for two actions
 * that used to be 48dp icons sitting beside the one people actually came for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftOverflowSheet(
    exercise: Exercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(
                exercise.name,
                modifier = Modifier.padding(horizontal = Metrics.gutter),
                style = InstrumentType.title,
                color = TextPrimary,
            )
            Column {
                HairlineDivider(startIndent = 0.dp)
                SheetActionRow(label = "Edit exercise", color = TextPrimary, onClick = onEdit)
                HairlineDivider()
                SheetActionRow(label = "Delete exercise", color = Danger, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun SheetActionRow(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .clickable(onClick = onClick)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = InstrumentType.title, color = color)
    }
}

/**
 * Picking a routine is a task, so it gets a sheet.
 *
 * It used to be an [AlertDialog] whose options were bare left-aligned text buttons — an
 * alert asking a question it had no business asking. Alerts confirm; sheets do work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToRoutineSheet(
    exercise: Exercise,
    routines: List<Routine>,
    onSelect: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Metrics.gutter),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text("Add ${exercise.name}", style = InstrumentType.title, color = TextPrimary)
                Text(
                    if (routines.isEmpty()) {
                        "No routines yet. Create one first, then add this lift."
                    } else {
                        "Lands in the routine at 3 × 5, editable there."
                    },
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
            }
            if (routines.isEmpty()) {
                PrimaryGymButton(
                    text = "Create routine",
                    onClick = onCreateRoutine,
                    modifier = Modifier.padding(horizontal = Metrics.gutter),
                )
            } else {
                Column {
                    HairlineDivider(startIndent = 0.dp)
                    routines.forEachIndexed { index, routine ->
                        InstrumentRow(
                            title = routine.name,
                            subtitle = liftCountLabel(routine.exercises.size),
                            onClick = { onSelect(routine.id) },
                        )
                        if (index < routines.lastIndex) HairlineDivider()
                    }
                }
            }
        }
    }
}

private fun liftCountLabel(count: Int): String =
    if (count == 1) "1 lift" else "$count lifts"
