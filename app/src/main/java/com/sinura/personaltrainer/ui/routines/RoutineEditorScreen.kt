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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AddDefaults
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.ExerciseRow
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
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
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
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
            RoutineEditorHeader(
                saved = state.saved,
                onBack = { viewModel.leave() },
                onSave = viewModel::saveDetails,
            )
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
            verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
        ) {
            item(key = "name") {
                RoutineTitleField(
                    name = state.name,
                    onNameChange = viewModel::onNameChange,
                    // A name complaint belongs under the name, not in the screen's error slot.
                    error = state.error?.takeIf { it.contains("name", ignoreCase = true) },
                )
            }
            if (state.saved) {
                item(key = "saved") { GymStatusBanner("Routine saved") }
            }
            state.error
                ?.takeUnless { it.contains("name", ignoreCase = true) }
                ?.let { message -> item(key = "error") { GymErrorBanner(message) } }

            if (exercises.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Add your first lift",
                        body = "Targets stay on the routine. Start it from Home when you’re in the gym.",
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
                    RoutineExerciseCard(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < exercises.lastIndex,
                        onMoveUp = { viewModel.moveExercise(item.id, -1) },
                        onMoveDown = { viewModel.moveExercise(item.id, 1) },
                        onRemove = { pendingRemoveId = item.id },
                        // Hidden rather than disabled when the lift stands alone in its family:
                        // a permanently greyed-out button is a promise the app cannot keep.
                        onSwap = if (state.swapCandidates(item.exercise.id).isNotEmpty()) {
                            { viewModel.requestSwap(item.id) }
                        } else {
                            null
                        },
                        onSaveTargets = { sets, reps, weight, rest ->
                            viewModel.updateExercise(item.id, sets, reps, weight, rest)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            // One quiet action, after the program rather than ahead of it.
            item(key = "add") {
                SecondaryGymButton(
                    text = "Add exercise",
                    onClick = { viewModel.setPickerVisible(true) },
                    modifier = Modifier.padding(top = Metrics.space2),
                    height = Metrics.touchMin,
                )
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
            onSelect = { exercise ->
                // Per lift class now, not one literal for all of them: 3x5 at 90s was a squat's
                // scheme applied to cable lateral raises.
                val defaults = AddDefaults.forExercise(exercise)
                viewModel.addExercise(
                    exercise = exercise,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
            },
            onCreate = { name, muscle ->
                // A lift being invented in the picker has no load type yet, so it takes the
                // fallback row deliberately rather than by accident.
                val defaults = AddDefaults.forExercise(loadType = null, isCompound = false)
                viewModel.createAndAddExercise(
                    customName = name,
                    muscleGroup = muscle,
                    targetSets = defaults.sets,
                    targetReps = defaults.reps,
                    targetWeightKg = null,
                    restSeconds = defaults.restSeconds,
                )
            },
            onDismiss = { viewModel.setPickerVisible(false) },
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
 * Back, and the only save this screen has.
 *
 * Save used to be a 64dp hero button sitting above the program it saves, competing with a
 * second identical hero that added lifts. It is the same call — there is no autosave here —
 * but as a header action it stops outranking the training itself. It goes quiet once the
 * details are committed, so the button also reports whether there is anything to save.
 */
@Composable
private fun RoutineEditorHeader(
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
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
        TextButton(onClick = onSave, enabled = !saved) {
            Text(
                "Save",
                style = InstrumentType.bodyStrong,
                color = if (saved) TextTertiary else Volt,
            )
        }
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

/**
 * One lift of the program.
 *
 * The prescription is stated once, as a numeral line in the same voice the workout screen
 * speaks — "3 × 5 · 100 kg · 1:30". The four fields under it are the edit mechanism, not the
 * readout: reading targets off admin text boxes made this screen speak a different numeric
 * language from the floor.
 */
@Composable
private fun RoutineExerciseCard(
    item: RoutineExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    /** Null when this lift has no variants, so the button is absent rather than disabled. */
    onSwap: (() -> Unit)?,
    onSaveTargets: (Int, Int, Double?, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    var sets by rememberSaveable(item.id) { mutableStateOf(item.targetSets.toString()) }
    var reps by rememberSaveable(item.id) { mutableStateOf(item.targetReps.toString()) }
    var weight by rememberSaveable(item.id, unit) {
        mutableStateOf(
            item.targetWeightKg?.let {
                WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(it, unit))
            }.orEmpty(),
        )
    }
    var rest by rememberSaveable(item.id) { mutableStateOf(item.restSeconds.toString()) }

    GymCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.exercise.name,
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.exercise.muscleGroup,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) TextSecondary else TextTertiary,
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) TextSecondary else TextTertiary,
                )
            }
        }
        Text(
            prescriptionLabel(item, unit),
            style = InstrumentType.numeralSm,
            color = TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            SmallNumberField("Sets", sets, Modifier.weight(1f)) { sets = it.filter(Char::isDigit) }
            SmallNumberField("Reps", reps, Modifier.weight(1f)) { reps = it.filter(Char::isDigit) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            SmallNumberField("Target ${unit.suffix}", weight, Modifier.weight(1f)) { value ->
                weight = value.filter { it.isDigit() || it == '.' }
            }
            SmallNumberField("Rest (s)", rest, Modifier.weight(1f)) { rest = it.filter(Char::isDigit) }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRemove) {
                Text("Remove", style = InstrumentType.bodyStrong, color = Danger)
            }
            if (onSwap != null) {
                TextButton(onClick = onSwap) {
                    Text("Swap", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
            TextButton(
                onClick = {
                    onSaveTargets(
                        sets.toIntOrNull() ?: item.targetSets,
                        reps.toIntOrNull() ?: item.targetReps,
                        WeightConverter.parseDisplayToKg(weight, unit, item.targetWeightKg),
                        rest.toIntOrNull() ?: item.restSeconds,
                    )
                },
            ) { Text("Update targets", style = InstrumentType.bodyStrong, color = TextPrimary) }
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
        label = { Text(label, style = InstrumentType.caption) },
        modifier = modifier,
        singleLine = true,
        textStyle = InstrumentType.numeralSm,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

private fun prescriptionLabel(item: RoutineExercise, unit: WeightUnit): String = buildString {
    append(item.targetSets)
    append(" × ")
    append(item.targetReps)
    item.targetWeightKg?.takeIf { it > 0.0 }?.let { kg ->
        append(" · ")
        append(kg.toWeightLabel(unit))
    }
    append(" · ")
    append(RestTimer.formatClock(item.restSeconds))
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
                            name = exercise.name,
                            muscleGroup = exercise.muscleGroup,
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
