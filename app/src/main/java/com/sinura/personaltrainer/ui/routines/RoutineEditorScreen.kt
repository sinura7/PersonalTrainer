package com.sinura.personaltrainer.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.ExerciseRow
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var expandedLiftId by rememberSaveable { mutableStateOf<String?>(null) }
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()

    // Back is state, not a callback: leaving first deletes the empty stub routine, and if the
    // Activity is recreated in that window the captured NavController is already gone. Acks
    // BEFORE popping — a duplicate pop would eat an extra screen.
    LaunchedEffect(exitRequested) {
        if (!exitRequested) return@LaunchedEffect
        viewModel.onExitHandled()
        onBack()
    }

    BackHandler { viewModel.leave() }

    Scaffold(
        topBar = {
            RoutineEditorHeader(onBack = { viewModel.leave() })
        },
    ) { padding ->
        if (state.isLoading) {
            ScreenLoading(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        if (state.missing) {
            EmptyState(
                title = "Routine missing",
                body = "This routine was deleted. Create a new one from the list.",
                actionLabel = "Back to routines",
                onAction = { viewModel.leave() },
                modifier = Modifier
                    .padding(padding)
                    .padding(Metrics.gutter),
            )
            return@Scaffold
        }

        val exercises = state.routine?.exercises.orEmpty()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space7,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            item(key = "name") {
                RoutineTitleField(
                    name = state.name,
                    onNameChange = viewModel::onNameChange,
                    // A name complaint belongs under the name, not in the screen's error slot.
                    error = state.error?.takeIf { it.contains("name", ignoreCase = true) },
                )
            }
            state.error
                ?.takeUnless { it.contains("name", ignoreCase = true) }
                ?.let { message -> item(key = "error") { GymErrorBanner(message) } }

            if (exercises.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Add your first lift",
                        body = "Targets stay on the routine. Start it from Home when you’re in the gym.",
                        actionLabel = "Add lifts",
                        onAction = { viewModel.setPickerVisible(true) },
                        compact = true,
                        modifier = Modifier.padding(top = Metrics.space4),
                    )
                }
            } else {
                item(key = "lifts-label") {
                    Kicker(
                        "Lifts",
                        modifier = Modifier.padding(top = Metrics.space4, bottom = Metrics.space1),
                    )
                }
                itemsIndexed(exercises, key = { _, item -> item.id }) { index, item ->
                    CompactLiftRow(
                        exercise = item.exercise,
                        sets = item.targetSets,
                        reps = item.targetReps,
                        restSeconds = item.restSeconds,
                        canMoveUp = index > 0,
                        canMoveDown = index < exercises.lastIndex,
                        expanded = expandedLiftId == item.id,
                        onToggle = { expandedLiftId = if (expandedLiftId == item.id) null else item.id },
                        onMoveUp = { viewModel.moveExercise(item.id, -1) },
                        onMoveDown = { viewModel.moveExercise(item.id, 1) },
                        onRemove = { pendingRemoveId = item.id },
                        onSwap = if (state.swapCandidates(item.exercise.id).isNotEmpty()) {
                            { viewModel.requestSwap(item.id) }
                        } else {
                            null
                        },
                        onStageTargets = { sets, reps, rest ->
                            viewModel.stageTargets(item.id, sets, reps, item.targetWeightKg, rest)
                        },
                        onCommitTargets = { viewModel.commitTargets(item.id) },
                        modifier = Modifier.animateItem(),
                        rowKey = item.id,
                    )
                }
            }
            if (exercises.isNotEmpty()) {
                // Quiet, after the program. The empty state's filled action is the first add.
                item(key = "add") {
                    SecondaryGymButton(
                        text = "Add lifts",
                        onClick = { viewModel.setPickerVisible(true) },
                        modifier = Modifier.padding(top = Metrics.space2),
                        height = Metrics.touchMin,
                    )
                }
            }
            item(key = "notes") {
                NotesBlock(
                    notes = state.notes,
                    expanded = notesOpen,
                    onToggle = { notesOpen = !notesOpen },
                    onChange = viewModel::onNotesChange,
                    modifier = Modifier.padding(top = Metrics.space4),
                )
            }
        }
    }

    if (state.showExercisePicker) {
        ExercisePickerSheet(
            query = state.searchQuery,
            results = state.searchResults,
            onQueryChange = viewModel::onSearchQuery,
            onSelect = { },
            onCreate = viewModel::createAndSelect,
            onDismiss = { viewModel.setPickerVisible(false) },
            title = "Add lifts",
            selectedIds = state.pendingAddIds,
            onToggle = viewModel::togglePendingAdd,
            onConfirmAdd = viewModel::confirmPendingAdd,
        )
    }

    state.swapItemId?.let { itemId ->
        val row = state.routine?.exercises?.firstOrNull { it.id == itemId }
        if (row != null) {
            SwapExerciseSheet(
                current = row.exercise,
                siblings = state.swapCandidates(row.exercise.id),
                onSelect = viewModel::swapExercise,
                onDismiss = viewModel::dismissSwap,
            )
        }
    }

    pendingRemoveId?.let { itemId ->
        val name = state.routine?.exercises?.firstOrNull { it.id == itemId }?.exercise?.name ?: "this lift"
        ConfirmActionDialog(
            title = "Remove $name?",
            body = "This takes it off the routine. Workout history stays saved.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.removeExercise(itemId)
                pendingRemoveId = null
            },
            onDismiss = { pendingRemoveId = null },
        )
    }
}

/**
 * Back and a label, and nothing else.
 *
 * There used to be a Save here. It wrote the name and the notes, announced "Routine saved",
 * and touched none of the four target fields on the cards below — which had a second Save of
 * their own, one per card. Two buttons named after the same verb, with different scopes, and
 * the prominent one claiming the broader result: typing new targets and pressing it lost them.
 * Both are gone. Every edit on this screen writes itself through, and each card's prescription
 * line reads back what was stored, which is a truer confirmation than a banner.
 */
@Composable
private fun RoutineEditorHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space2, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
            )
        }
        Kicker("Routine", modifier = Modifier.weight(1f))
    }
}

/**
 * The routine's name as the screen's title instead of the first field of a form.
 *
 * Still a plain text field bound to the same name flow — the underline and the volt caret are
 * what say so, since a title with a box drawn around it is form chrome again.
 */
@Composable
private fun RoutineTitleField(
    name: String,
    onNameChange: (String) -> Unit,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = InstrumentType.display.copy(color = TextPrimary),
            singleLine = true,
            cursorBrush = SolidColor(Volt),
            decorationBox = { field ->
                Box {
                    if (name.isEmpty()) {
                        Text(
                            "Name this routine",
                            style = InstrumentType.display,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
        HairlineDivider(startIndent = 0.dp)
        if (error != null) {
            Text(error, style = InstrumentType.caption, color = Danger)
        }
    }
}

/** Notes are a programming aside, not the second thing on the screen. */
@Composable
private fun NotesBlock(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
            )
            Text(
                when {
                    expanded -> "Hide notes"
                    notes.isBlank() -> "Add notes"
                    else -> "Notes"
                },
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
                modifier = Modifier.padding(start = Metrics.space2),
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = notes,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes", style = InstrumentType.caption) },
                textStyle = InstrumentType.body,
                minLines = 2,
            )
        }
    }
}

/**
 * Same movement, different kit.
 *
 * Deliberately not the full picker: this is not "add a lift", it is "the bench is taken". A
 * search field would invite the user to leave the family, which is what Add exercise is for,
 * and the whole value here is that the list is already the four or five right answers.
 *
 * Targets and position stay put, and the sheet says so — the alternative reading, that a swap
 * resets the row, is the one that would stop people using it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapExerciseSheet(
    current: Exercise,
    siblings: List<Exercise>,
    onSelect: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Metrics.gutter)
                    .padding(bottom = Metrics.space3),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text("Swap ${current.name}", style = InstrumentType.title, color = TextPrimary)
                Text(
                    "Same movement, different kit. Sets, reps, rest and position stay as they are.",
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
            HairlineDivider(startIndent = 0.dp)
            LazyColumn(contentPadding = PaddingValues(bottom = Metrics.space7)) {
                itemsIndexed(siblings, key = { _, exercise -> exercise.id }) { index, exercise ->
                    Column {
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onSelect(exercise) },
                            tag = exercise.equipment.label,
                        )
                        if (index < siblings.lastIndex) HairlineDivider()
                    }
                }
            }
        }
    }
}
