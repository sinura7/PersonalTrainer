package com.sinura.personaltrainer.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.RoutineSaveCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.NotesKind
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.ExerciseRow
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
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
        bottomBar = {
            val lifts = state.routine?.exercises.orEmpty()
            if (!state.isLoading && !state.failed && !state.missing && lifts.isNotEmpty()) {
                RoutineSaveDock(
                    enabled = !state.addingLifts,
                    saving = state.saving,
                    error = state.saveError,
                    onSave = viewModel::saveAndLeave,
                )
            }
        },
    ) { padding ->
        if (state.isLoading) {
            ScreenLoading(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        if (state.failed) {
            // The opening read threw. Retry re-runs hydration; the header's back arrow is the way
            // out — the same shape History uses for an unreadable list.
            EmptyState(
                title = DataHealthCopy.ROUTINE_EDITOR_TITLE,
                body = DataHealthCopy.ROUTINE_EDITOR_BODY,
                actionLabel = DataHealthCopy.RETRY,
                onAction = { viewModel.retryHydration() },
                modifier = Modifier
                    .padding(padding)
                    .padding(Metrics.gutter),
            )
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
        LaunchedEffect(exercises.map { it.id }) {
            if (expandedLiftId != null && exercises.none { it.id == expandedLiftId }) {
                expandedLiftId = null
            }
        }

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
                RoutineTitleField(name = state.name, onNameChange = viewModel::onNameChange)
            }
            // Every complaint this screen can raise goes to the banner, which owns the only
            // dismiss on the screen.
            //
            // This used to route by matching the substring "name" and put the match under the
            // ROUTINE's title field instead. Nothing in the view model raises a complaint about
            // the routine's name, so the match had no true case to serve — but "That name is
            // already in your library" and "Lift name is required." are both about a LIFT being
            // created in the picker, and both matched. Closing the sheet left them printed under
            // the routine's own title, saying the routine was a duplicate, with no dismiss on
            // them at all: the banner that owns `dismissError` had been suppressed by the same
            // match. It cleared only when an add succeeded or the screen was left.
            //
            // If a routine-name complaint is ever added it gets a typed field on the state and
            // is routed by the family that raised it. A message is not a place.
            state.error
                ?.takeUnless { state.showExercisePicker }
                ?.let { message -> item(key = "error") { GymErrorBanner(message, onDismiss = viewModel::dismissError) } }

            if (exercises.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Add your first lift",
                        body = SessionOrderCopy.EMPTY_EDITOR_BODY,
                        actionLabel = "Add lifts",
                        onAction = { viewModel.setPickerVisible(true) },
                        compact = true,
                        actionEnabled = !state.addingLifts,
                        actionTag = RoutineEditorTags.ADD_LIFTS,
                        modifier = Modifier.padding(top = Metrics.space4),
                    )
                }
            } else {
                item(key = "lifts-label") {
                    Kicker(
                        SessionOrderCopy.sectionLabel(exercises.size),
                        modifier = Modifier.padding(top = Metrics.space4, bottom = Metrics.space1),
                    )
                }
                item(key = "lifts-strip") {
                    SessionLiftStrip(
                        lifts = exercises.map { item ->
                            SessionLiftItem(
                                id = item.id,
                                exercise = item.exercise,
                                sets = item.targetSets,
                                reps = item.targetReps,
                                restSeconds = item.restSeconds,
                                targetWeightKg = item.targetWeightKg,
                            )
                        },
                        selectedId = expandedLiftId,
                        onSelect = { id ->
                            expandedLiftId = if (expandedLiftId == id) null else id
                        },
                        onMoveEarlier = { id -> viewModel.moveExercise(id, -1) },
                        onMoveLater = { id -> viewModel.moveExercise(id, 1) },
                        onRemove = { id -> pendingRemoveId = id },
                        canSwap = { id ->
                            val exerciseId = exercises.firstOrNull { it.id == id }?.exercise?.id
                            exerciseId != null && state.swapCandidates(exerciseId).isNotEmpty()
                        },
                        onSwap = { id -> viewModel.requestSwap(id) },
                        onStageTargets = { id, sets, reps, rest, kg, invalid ->
                            viewModel.stageTargets(id, sets, reps, kg, rest, invalid)
                        },
                        onCommitTargets = { id -> viewModel.commitTargets(id) },
                        onForgetTargetRule = { id -> viewModel.forgetTargetRule(id) },
                    )
                }
            }
            if (exercises.isNotEmpty()) {
                // Quiet, after the program. The empty state's filled action is the first add.
                item(key = "add") {
                    SecondaryGymButton(
                        text = if (state.addingLifts) "Adding…" else "Add lifts",
                        onClick = { viewModel.setPickerVisible(true) },
                        enabled = !state.addingLifts,
                        modifier = Modifier
                            .padding(top = Metrics.space2)
                            .testTag(RoutineEditorTags.ADD_LIFTS),
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
                    kind = NotesKind.PROGRAM,
                    modifier = Modifier.padding(top = Metrics.space4),
                )
            }
        }
    }

    if (state.showExercisePicker) {
        ExercisePickerSheet(
            state = ExercisePickerState(
                query = state.searchQuery,
                results = state.searchResults,
                title = "Add lifts",
                mode = ExercisePickerMode.MULTI_ADD,
                selectedOrder = state.pickedIds,
                catalog = state.catalog,
                error = state.error,
            ),
            onEvent = { event ->
                when (event) {
                    is ExercisePickerEvent.QueryChanged -> viewModel.onSearchQuery(event.query)
                    is ExercisePickerEvent.Selected -> Unit
                    is ExercisePickerEvent.Created ->
                        viewModel.createAndSelect(event.name, event.muscleGroup)
                    is ExercisePickerEvent.Toggled -> viewModel.togglePicked(event.exercise)
                    ExercisePickerEvent.Dismissed -> viewModel.setPickerVisible(false)
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissError()
                }
            },
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
                if (expandedLiftId == itemId) expandedLiftId = null
                viewModel.removeExercise(itemId)
                pendingRemoveId = null
            },
            onDismiss = { pendingRemoveId = null },
        )
    }

    // Back could not land something Save is responsible for. Leaving is the destructive
    // choice here — it drops the draft — so it is the red one, and "Try again" is the plain
    // dismissal, which also covers a tap outside the dialog: retrying is never the wrong
    // default when the alternative is losing work.
    state.unsavedOnBack?.let { unsaved ->
        ConfirmActionDialog(
            title = RoutineSaveCopy.UNSAVED_TITLE,
            body = unsaved.backPromptBody,
            confirmLabel = RoutineSaveCopy.LEAVE_ANYWAY,
            destructive = true,
            onConfirm = viewModel::leaveAnyway,
            onDismiss = viewModel::leave,
            dismissLabel = RoutineSaveCopy.TRY_AGAIN,
        )
    }
}

/**
 * Back and a label. Save sits in the dock when the routine has lifts.
 *
 * Lifts, reorder, and targets still write through as they land. Save
 * flushes the name and notes and keeps the program (ADR-021), and stays
 * on the screen if any of that did not land.
 */
@Composable
internal fun RoutineEditorHeader(onBack: () -> Unit) {
    ScreenHeader(
        title = "Routine",
        onBack = onBack,
        backTag = RoutineEditorTags.BACK,
        kickerTitle = true,
    )
}

object RoutineEditorTags {
    const val BACK = "routine-editor-back"
    const val ADD_LIFTS = "routine-editor-add-lifts"
    const val SAVE = "routine-editor-save"
    const val SAVE_ERROR = "routine-editor-save-error"
}

/**
 * The Save dock, and the one place the write-through model is explained.
 *
 * The caption sits above the button because the button's label — "Save" — is otherwise a
 * small lie about the lifts, which are already saved. A failed Save says why directly above
 * the button that was pressed, not in the banner at the top of the list the owner has
 * scrolled past; while the attempt runs the label reads "Saving…" and the button is off,
 * so a second press cannot start a second exit.
 */
@Composable
private fun RoutineSaveDock(
    enabled: Boolean,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
) {
    PinnedDock(
        prelude = {
            Text(
                text = RoutineSaveCopy.WRITE_THROUGH,
                style = InstrumentType.caption,
                color = TextTertiary,
            )
            if (error != null) {
                FieldComplaint(
                    message = error,
                    modifier = Modifier.testTag(RoutineEditorTags.SAVE_ERROR),
                )
            }
        },
        volt = {
            PrimaryGymButton(
                text = RoutineSaveCopy.saveLabel(saving),
                onClick = onSave,
                modifier = Modifier.testTag(RoutineEditorTags.SAVE),
                enabled = enabled && !saving,
            )
        },
    )
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
