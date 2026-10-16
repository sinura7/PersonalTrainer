package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.LibraryFilter
import com.sinura.personaltrainer.domain.LiftCart
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Choosing a lift, and the row anatomy the library screen shares with this sheet.
 *
 * [ExerciseRow] and [ExerciseSearchField] live here rather than in a file of their own
 * because the picker and the library are the same job on two surfaces — the audit's finding
 * was precisely that they had drifted into two different-looking lists — and one definition
 * is what keeps them from drifting again.
 */

/** Results and creation are separate steps; writes belong to the calling ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    canDismiss: () -> Boolean = { !state.progress.busy },
) {
    PickerSheet(state = state, onEvent = onEvent, canDismiss = canDismiss, recovery = null)
}

@Composable
internal fun RecoverableExercisePickerSheet(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    recovery: OwnedPickerRecovery?,
    canDismiss: () -> Boolean = { !state.progress.busy },
) {
    if (recovery == null) ExercisePickerSheet(state = state, onEvent = onEvent, canDismiss = canDismiss)
    else OwnedExercisePickerSheet(state = state, onEvent = onEvent, recovery = recovery, canDismiss = canDismiss)
}

@Composable
internal fun OwnedExercisePickerSheet(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    recovery: OwnedPickerRecovery,
    canDismiss: () -> Boolean,
) {
    val currentEvent by rememberUpdatedState(onEvent)
    DisposableEffect(recovery) {
        recovery.view.bindDeparture { currentEvent(ExercisePickerEvent.Dismissed) }
        recovery.view.open()
        onDispose { recovery.view.closeVisit(); recovery.create.closeVisit() }
    }
    val view by recovery.view.status.collectAsState()
    val fields by recovery.view.fields.collectAsState()
    val custom by recovery.create.status.collectAsState()
    LaunchedEffect(view.opening, fields.creating, recovery.view.activeOwner) {
        if (!view.opening && fields.creating && custom.phase == PickerCreatePhase.CLOSED) recovery.openCreate()
    }
    PickerSheet(state = state, onEvent = onEvent, canDismiss = { canDismiss() && recovery.canLeave() }, recovery = recovery)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    canDismiss: () -> Boolean,
    recovery: OwnedPickerRecovery?,
) {
    val mayDismiss by rememberUpdatedState(canDismiss)
    val event by rememberUpdatedState(onEvent)
    val dismiss: () -> Unit = { if (mayDismiss()) {
        if (recovery == null) event(ExercisePickerEvent.Dismissed)
        else recovery.leave { event(ExercisePickerEvent.Dismissed) }
    } }
    val currentDismiss by rememberUpdatedState(dismiss)
    val canChange = remember(recovery) { { value: SheetValue ->
        if (value != SheetValue.Hidden) true else if (recovery == null) mayDismiss()
        else { currentDismiss(); false }
    } }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = canChange,
    )
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheet,
    ) {
        ExercisePickerContent(
            state = state,
            onEvent = { action -> when {
                action == ExercisePickerEvent.Dismissed -> dismiss()
                recovery != null && action is ExercisePickerEvent.Selected -> recovery.leave { onEvent(action) }
                else -> onEvent(action)
            } },
            modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_SHARE),
            recovery = recovery,
        )
    }
}

/** The same native content is used by the sheet and its constrained-layout render tests. */
private data class PickerPositionRequest(
    val query: String,
    val muscle: CanonicalMuscle?,
    val creating: Boolean,
    val opening: Boolean?,
    val error: String?,
)

@Composable
internal fun ExercisePickerContent(
    state: ExercisePickerState,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
    recovery: OwnedPickerRecovery? = null,
) {
    var selectedMuscleName by rememberSaveable { mutableStateOf("") }
    var localCreating by rememberSaveable { mutableStateOf(false) }
    var localCreateName by rememberSaveable { mutableStateOf("") }
    var localGroup by rememberSaveable { mutableStateOf("") }
    var localLoadTypeName by rememberSaveable { mutableStateOf(LoadType.EXTERNAL.name) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val retained = recovery?.view?.fields?.collectAsState()?.value
    val viewStatus = recovery?.view?.status?.collectAsState()?.value
    val createStatus = recovery?.create?.status?.collectAsState()?.value
    val creating = if (recovery == null) localCreating else retained?.creating == true ||
        createStatus?.phase in setOf(PickerCreatePhase.OPENING, PickerCreatePhase.READ_FAILED)
    val createName = createStatus?.fields?.name ?: localCreateName
    val group = createStatus?.fields?.muscle ?: localGroup
    val loadTypeName = createStatus?.fields?.loadType?.name ?: localLoadTypeName
    val editable = recovery?.canSelect() ?: true
    val customEditable = createStatus?.editable ?: true
    var consumedCreation by rememberSaveable { mutableStateOf(state.progress.createdGeneration) }
    val selectedMuscle = if (recovery == null) CanonicalMuscle.bodyMapOrder.firstOrNull { it.name == selectedMuscleName } else retained?.muscle
    val visibleResults = state.visibleFor(selectedMuscle)
    val needle = state.query.trim()
    val canCreate = needle.isNotEmpty() &&
        (state.catalog + state.results).none { it.name.equals(needle, ignoreCase = true) }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val problem = state.progress.error ?: state.error
    val recoveringSelection = recovery != null && !editable && state.progress.canRetry
    val continuingSavedCreate = recovery != null && createStatus?.phase == PickerCreatePhase.SAVED &&
        viewStatus?.busy == false && !viewStatus.changedElsewhere
    val hasRecoveryStatus = viewStatus?.let { it.busy || it.resumed || it.error != null } == true
    val keys = pickerContentKeys(state = state, muscle = selectedMuscle, creating = creating,
        hasError = problem != null, hasRecoveryStatus = hasRecoveryStatus, hasRecovery = recovery != null)
    var restoredPosition by remember(recovery) { mutableStateOf(false) }
    var restoredRequest by remember(recovery) { mutableStateOf<PickerPositionRequest?>(null) }
    val currentViewStatus by rememberUpdatedState(viewStatus)
    val positionRequest = PickerPositionRequest(query = state.query, muscle = selectedMuscle,
        creating = creating, opening = viewStatus?.opening, error = viewStatus?.error ?: problem)
    LaunchedEffect(recovery, positionRequest, keys, state.catalog.isNotEmpty()) {
        if (restoredRequest != positionRequest) {
            restoredPosition = false
            restoredRequest = positionRequest
            if (positionRequest.error != null || creating) list.scrollToItem(0)
        }
        if (recovery != null && viewStatus?.opening == false && viewStatus.error == null && !creating && !restoredPosition && keys.isNotEmpty()) {
            val value = checkNotNull(retained)
            val anchor = value.anchor?.let(keys::indexOf)?.takeIf { it >= 0 }
            if (value.anchor == null || anchor != null || state.catalog.isNotEmpty()) {
                list.scrollToItem((anchor ?: value.index).coerceIn(0, keys.lastIndex), with(density) { value.offsetDp.dp.roundToPx() })
                restoredPosition = true
            }
        }
    }
    LaunchedEffect(list, recovery, density) {
        if (recovery != null) snapshotFlow {
            if (!restoredPosition || currentViewStatus?.busy == true || currentViewStatus?.error != null) null else Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String)
        }.distinctUntilChanged().collect { position ->
            position?.let { (index, offset, anchor) ->
                recovery.view.positionChanged(index = index, offsetDp = with(density) { offset.toDp().value }, anchor = anchor)
            }
        }
    }
    LaunchedEffect(state.progress.createdGeneration) {
        if (state.progress.createdGeneration > consumedCreation) {
            localCreating = false
        }
        // A new ViewModel starts at zero after process recreation; retain the form,
        // then consume its future successes against that new owner's counter.
        consumedCreation = state.progress.createdGeneration
    }
    val choose: (Exercise) -> Unit = { exercise ->
        if (editable && createStatus?.busy != true && !state.progress.creating && (state.multiSelect || !state.progress.busy)) {
            onEvent(if (state.multiSelect) ExercisePickerEvent.Toggled(exercise) else ExercisePickerEvent.Selected(exercise))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space3),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (creating) {
                TextButton(onClick = { if (recovery == null) localCreating = false else recovery.backToList() }, enabled = !state.progress.busy && createStatus?.busy != true) {
                    Text("Back to lifts", style = InstrumentType.bodyStrong)
                }
                Text("Create custom lift", style = InstrumentType.title, color = TextPrimary)
            } else {
                Text(state.title, style = InstrumentType.title, color = TextPrimary)
                ExerciseSearchField(
                    value = state.query,
                    onValueChange = { if (editable) {
                        if (recovery == null) onEvent(ExercisePickerEvent.QueryChanged(it))
                        else recovery.change(recovery.view.fields.value.copy(query = it, anchor = null, index = 0, offsetDp = 0f))
                    } },
                    placeholder = "Search lifts",
                    modifier = Modifier.testTag("picker-search"),
                    enabled = editable,
                )
            }
        }
        HairlineDivider(startIndent = 0.dp)
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).testTag("picker-content"),
            contentPadding = PaddingValues(bottom = Metrics.space4),
        ) {
            if (hasRecoveryStatus) {
                item(key = "view-status") {
                    Column(modifier = Modifier.padding(Metrics.gutter)) {
                        if (viewStatus?.busy == true) Text(if (viewStatus.opening) "Opening picker…" else "Keeping picker…",
                            style = InstrumentType.caption, color = TextSecondary)
                        viewStatus?.error?.let { message ->
                            GymErrorBanner(message = message,
                                onRetry = if (viewStatus.changedElsewhere) null else ({ checkNotNull(recovery).view.keep() }))
                            if (viewStatus.changedElsewhere) SecondaryGymButton(text = "Use kept view",
                                onClick = { checkNotNull(recovery).view.useKeptView() }, height = Metrics.touchMin)
                        }
                        if (viewStatus?.resumed == true) Text("Picker resumed", style = InstrumentType.caption, color = TextSecondary)
                    }
                }
            }
            if (problem != null) {
                item(key = "error") {
                    if (recoveringSelection && recovery?.retryLabel?.invoke() == "Continue") {
                        Column(modifier = Modifier.padding(Metrics.gutter), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            Kicker("LIFT SAVED")
                            Text(problem, style = InstrumentType.body, color = TextSecondary)
                        }
                    } else GymErrorBanner(
                        message = problem,
                        modifier = Modifier.padding(Metrics.gutter),
                        onRetry = if (state.progress.canRetry && !state.progress.busy && !recoveringSelection) {
                            { onEvent(ExercisePickerEvent.Retry) }
                        } else null,
                        onDismiss = if (editable) ({ onEvent(ExercisePickerEvent.ErrorDismissed) }) else null,
                    )
                }
            }
            state.progress.existing?.let { existing ->
                item(key = "existing") {
                    ExerciseRow(
                        exercise = existing,
                        nameMaxLines = Int.MAX_VALUE,
                        tag = LoadTypeCopy.rowTag(existing),
                    )
                    TextButton(
                        onClick = { localCreating = false; onEvent(ExercisePickerEvent.Selected(existing)) },
                        enabled = !state.progress.busy,
                        modifier = Modifier.padding(horizontal = Metrics.gutter),
                    ) { Text("Use existing lift: ${existing.name}", style = InstrumentType.bodyStrong) }
                }
            }
            if (creating) {
                item(key = "create-form") {
                    Column(
                        modifier = Modifier.padding(Metrics.gutter).testTag("picker-create-form"),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        if (recovery == null) Text(createName, style = InstrumentType.display, color = TextPrimary)
                        else BasicTextField(value = createName, onValueChange = { if (customEditable) {
                            val fields = checkNotNull(createStatus?.fields)
                            recovery.create.update(name = it, muscle = fields.muscle, loadType = fields.loadType, notes = fields.notes)
                        } }, singleLine = true, enabled = customEditable,
                            textStyle = InstrumentType.title.copy(color = TextPrimary), cursorBrush = SolidColor(Volt),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag("picker-create-name"),
                            decorationBox = { input -> Column { Kicker("LIFT NAME"); input() } })
                        createStatus?.message?.let { Text(it, style = InstrumentType.body, color = TextSecondary) }
                        if (createStatus?.phase in setOf(PickerCreatePhase.UNCERTAIN, PickerCreatePhase.KEEP_FAILED, PickerCreatePhase.READ_FAILED)) {
                            SecondaryGymButton(text = "Check again", onClick = { recovery?.create?.check() }, enabled = createStatus?.busy != true,
                                height = Metrics.touchMin)
                        }
                        Text(
                            "Saved to your Library, then selected here. Cancelling later keeps the Library lift.",
                            style = InstrumentType.caption, color = TextSecondary,
                        )
                        Kicker("MUSCLE GROUP")
                        if (group.isBlank()) Text("Choose a muscle group to continue.", style = InstrumentType.caption, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2), verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            (MuscleGroups.chips + MuscleGroups.OTHER).forEach { muscle ->
                                InstrumentChip(
                                    label = muscle, selected = muscle.equals(group, ignoreCase = true),
                                    onClick = { if (recovery == null) localGroup = muscle else {
                                        val fields = checkNotNull(createStatus?.fields)
                                        recovery.create.update(name = fields.name, muscle = muscle, loadType = fields.loadType, notes = fields.notes)
                                    } }, enabled = !state.progress.busy && customEditable,
                                )
                            }
                        }
                        LoadTypeChipRow(
                            selected = LoadType.fromStorage(loadTypeName),
                            onSelect = { if (recovery == null) localLoadTypeName = it.name else {
                                val fields = checkNotNull(createStatus?.fields)
                                recovery.create.update(name = fields.name, muscle = fields.muscle, loadType = it, notes = fields.notes)
                            } },
                            enabled = !state.progress.busy && customEditable,
                        )
                        if (recovery != null) {
                            TextButton(onClick = { notesOpen = !notesOpen }, modifier = Modifier.heightIn(min = Metrics.touchMin)) {
                                Text(if (notesOpen) "Hide notes" else "Notes (optional)", style = InstrumentType.bodyStrong)
                            }
                            if (notesOpen) BasicTextField(value = createStatus?.fields?.notes.orEmpty(), onValueChange = {
                                val fields = checkNotNull(createStatus?.fields)
                                recovery.create.update(name = fields.name, muscle = fields.muscle, loadType = fields.loadType, notes = it)
                            }, enabled = customEditable, textStyle = InstrumentType.body.copy(color = TextPrimary), cursorBrush = SolidColor(Volt),
                                modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin).testTag("picker-create-notes"))
                        }
                    }
                }
            } else {
                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(Metrics.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        item { InstrumentChip(label = "All", selected = selectedMuscle == null, onClick = {
                            if (recovery == null) selectedMuscleName = "" else recovery.change(recovery.view.fields.value.copy(muscle = null, anchor = null, index = 0, offsetDp = 0f))
                        }, enabled = editable) }
                        items(CanonicalMuscle.bodyMapOrder, key = { it.name }) { muscle ->
                            InstrumentChip(label = muscle.displayName, selected = selectedMuscle == muscle, onClick = {
                                if (recovery == null) selectedMuscleName = if (selectedMuscleName == muscle.name) "" else muscle.name
                                else recovery.change(recovery.view.fields.value.copy(muscle = muscle.takeUnless { it == selectedMuscle }, anchor = null, index = 0, offsetDp = 0f))
                            }, enabled = editable)
                        }
                    }
                }
                if (state.multiSelect) {
                    item(key = "selection") {
                        Text(SessionOrderCopy.PICKER_HINT, style = InstrumentType.caption, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = Metrics.gutter).padding(bottom = Metrics.space3))
                        if (state.cart.isNotEmpty()) {
                            LazyRow(contentPadding = PaddingValues(horizontal = Metrics.gutter), horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                                itemsIndexed(state.cart, key = { _, e -> e.id }) { i, exercise ->
                                    InstrumentChip(label = "${i + 1}  ${exercise.name}", selected = true, onClick = { choose(exercise) }, enabled = !state.progress.creating)
                                }
                            }
                        }
                    }
                }
                if (needle.isEmpty()) {
                    val siblings = if (state.showSiblings) LibraryFilter.apply(state.siblings, selectedMuscle) else emptyList()
                    if (siblings.isNotEmpty()) {
                        item(key = "siblings-header") { Kicker("SAME MOVEMENT", modifier = Modifier.padding(Metrics.gutter)) }
                        items(siblings, key = { "sibling-${it.id}" }) { exercise ->
                            PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) },
                                cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                                enabled = !state.progress.busy, pending = exercise.id in state.progress.pendingIds)
                        }
                    }
                    state.suggestion?.takeIf { selectedMuscle == null || LibraryFilter.matches(it, selectedMuscle) }?.let { exercise ->
                        item(key = "suggestion") {
                            Kicker("SUGGESTED", modifier = Modifier.padding(Metrics.gutter))
                            PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) }, subtitle = state.suggestionReason,
                                cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                                enabled = !state.progress.busy, pending = exercise.id in state.progress.pendingIds)
                        }
                    }
                }
                if (visibleResults.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            scene = EmptyScene.CATALOG,
                            title = "No matching lifts",
                            body = if (selectedMuscle != null) "Try All or a different name." else "Try a different name, or create your own lift.",
                            modifier = Modifier.padding(Metrics.gutter), compact = true,
                        )
                    }
                }
                itemsIndexed(visibleResults, key = { _, e -> e.id }) { i, exercise ->
                    Column(modifier = instrumentAnimateItem()) {
                        PickerLiftRow(exercise = exercise, selected = exercise.id in state.selectedIds, onClick = { choose(exercise) },
                            cartNumber = LiftCart.cartNumber(state.selectedOrder, exercise.id),
                            enabled = editable && !state.progress.creating && (state.multiSelect || !state.progress.busy),
                            pending = exercise.id in state.progress.pendingIds)
                        if (i < visibleResults.lastIndex) HairlineDivider()
                    }
                }
                if (canCreate) {
                    item(key = "create-entry") {
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                if (recovery == null) {
                                    localCreateName = needle
                                    localGroup = MuscleGroups.forNewDraft(selectedMuscle)
                                    localLoadTypeName = LoadType.EXTERNAL.name
                                    localCreating = true
                                } else recovery.openCreate()
                            },
                            enabled = !state.progress.busy && editable,
                            modifier = Modifier.fillMaxWidth().padding(Metrics.gutter).testTag("picker-create-entry"),
                        ) { Text("Create custom lift: $needle", style = InstrumentType.bodyStrong, color = TextSecondary) }
                    }
                }
            }
            if (recovery != null) {
                item(key = "private-reset") {
                    Column(modifier = Modifier.padding(horizontal = Metrics.gutter)) {
                        if (!creating) createStatus?.message?.let { Text(it, style = InstrumentType.body, color = TextSecondary) }
                        TextButton(onClick = { confirmReset = true }, enabled = !state.progress.busy && viewStatus?.busy != true && createStatus?.busy != true,
                            modifier = Modifier.heightIn(min = Metrics.touchMin).testTag("picker-reset")) {
                            Text("Reset picker", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                }
            }
        }
        if (creating || state.multiSelect || state.progress.busy || recoveringSelection) {
            PrimaryGymButton(
                text = when {
                    recoveringSelection -> checkNotNull(recovery).retryLabel()
                    createStatus?.busy == true -> createStatus.message ?: "Saving lift…"
                    createStatus?.phase == PickerCreatePhase.SAVED -> if (recovery?.retryLabel?.invoke() == "Continue") "Continue" else "Use saved lift"
                    createStatus?.phase == PickerCreatePhase.RETRYABLE -> "Retry exact Save"
                    state.progress.creating -> "Creating lift…"
                    state.progress.busy -> "Saving lift…"
                    creating -> "Create and select"
                    state.cart.isEmpty() -> "Done"
                    state.cart.size == 1 -> "Done · 1 lift"
                    else -> "Done · ${state.cart.size} lifts"
                },
                enabled = !state.progress.busy && viewStatus?.busy != true && viewStatus?.changedElsewhere != true && createStatus?.busy != true && (recoveringSelection || continuingSavedCreate || editable && (!creating ||
                    if (recovery == null) MuscleGroups.resolved(group) != null else createStatus?.phase == PickerCreatePhase.SAVED ||
                        createStatus?.phase in setOf(PickerCreatePhase.EDITING, PickerCreatePhase.RETRYABLE) && createName.isNotBlank() && MuscleGroups.resolved(group) != null)),
                onClick = {
                    if (recoveringSelection) onEvent(ExercisePickerEvent.Retry)
                    else if (creating && recovery != null) {
                        if (createStatus?.phase == PickerCreatePhase.SAVED) recovery.useSaved() else recovery.saveCreate()
                    } else if (creating) onEvent(ExercisePickerEvent.Created(createName, group, LoadType.fromStorage(loadTypeName)))
                    else onEvent(ExercisePickerEvent.Dismissed)
                },
                modifier = Modifier.padding(horizontal = Metrics.gutter, vertical = Metrics.space3).testTag("picker-action"),
            )
        }
    }
    if (confirmReset && recovery != null) ConfirmActionDialog(
        title = "Reset this picker?",
        body = "Clear this search, filters and unfinished custom-lift draft. Saved Library lifts and lifts already added here stay saved.",
        confirmLabel = "Reset picker", destructive = true,
        confirmEnabled = !state.progress.busy && viewStatus?.busy != true && createStatus?.busy != true,
        onConfirm = { confirmReset = false; recovery.discardPrivate() }, onDismiss = { confirmReset = false },
    )
}

internal fun pickerContentKeys(state: ExercisePickerState, muscle: CanonicalMuscle?, creating: Boolean,
    hasError: Boolean, hasRecoveryStatus: Boolean = false, hasRecovery: Boolean = false): List<String> = buildList {
    if (hasRecoveryStatus) add("view-status")
    if (hasError) add("error")
    if (state.progress.existing != null) add("existing")
    if (creating) add("create-form") else {
        add("filters")
        if (state.multiSelect) add("selection")
        if (state.query.trim().isEmpty()) {
            val siblings = if (state.showSiblings) LibraryFilter.apply(state.siblings, muscle) else emptyList()
            if (siblings.isNotEmpty()) { add("siblings-header"); siblings.forEach { add("sibling-${it.id}") } }
            if (state.suggestion?.let { muscle == null || LibraryFilter.matches(it, muscle) } == true) add("suggestion")
        }
        val results = state.visibleFor(muscle)
        if (results.isEmpty()) add("empty")
        results.forEach { add(it.id) }
        val needle = state.query.trim()
        if (needle.isNotEmpty() && (state.catalog + state.results).none { it.name.equals(needle, ignoreCase = true) }) add("create-entry")
    }
    if (hasRecovery) add("private-reset")
}

@Composable
private fun PickerLiftRow(
    exercise: Exercise,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    cartNumber: Int? = null,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    val shape = RoundedCornerShape(Radius.xs)
    ExerciseRow(
        exercise = exercise,
        modifier = (if (selected) {
            Modifier
                .clip(shape)
                .background(VoltDim)
                .border(Metrics.emphasisBorder, Volt, shape)
        } else {
            Modifier
        }).semantics {
            this.selected = selected
            if (pending) stateDescription = "Saving lift"
        },
        onClick = if (enabled) onClick else null,
        nameMaxLines = Int.MAX_VALUE,
        tag = LoadTypeCopy.rowTag(exercise),
        subtitle = if (pending) "Saving…" else subtitle,
        trailing = when {
            cartNumber != null -> {
                {
                    PickerCartBadge(cartNumber)
                }
            }
            selected -> {
                {
                    Text("Selected", style = InstrumentType.caption, color = TextTertiary)
                }
            }
            else -> null
        },
    )
}

@Composable
private fun PickerCartBadge(number: Int) {
    val badgeShape = Radius.full
    Box(
        modifier = Modifier
            .heightIn(min = Metrics.space5)
            .widthIn(min = Metrics.space5)
            .clip(badgeShape)
            .background(Volt)
            .padding(horizontal = Metrics.space1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = InstrumentType.caption,
            color = Pit,
        )
    }
}

/**
 * One lift in a list: thumbnail, name, muscle group, and whatever the surface does to it.
 *
 * Takes the [Exercise] rather than a name and a group string, because the thumbnail needs the
 * equipment and the muscle credits too — and every call site already had the object in hand.
 * Passing three fields where one would do is how a row ends up unable to draw its own picture.
 *
 * [trailing] is a slot so the library can hang its actions here without the picker growing
 * a set of buttons it has no use for.
 */
@Composable
fun ExerciseRow(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
    /**
     * Overrides the muscle-group subtitle. Exactly one caller uses it — the coach's pinned
     * suggestion, which says WHY it is suggested rather than what it trains, and would be a
     * worse row if it repeated the group the rows below it already show.
     */
    subtitle: String? = null,
    nameMaxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(
                start = Metrics.gutter,
                // Icon buttons carry their own 48dp of target, so the trailing edge closes up.
                end = if (trailing != null) Metrics.space2 else Metrics.gutter,
            )
            .padding(vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        ExerciseThumb(exercise)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                exercise.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    subtitle ?: exercise.muscleGroup,
                    modifier = Modifier.weight(1f, fill = false),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag != null) EquipmentChip(tag)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * A search bar in the app's own language: one [Surface1] well with a hairline, not a form
 * field with a floating label and an M3 4dp corner.
 *
 * It does not take focus on its own. The sheet opens on the catalog, and a keyboard that
 * covers the thing you came to look at is not a shortcut — which is what the invisible 1dp
 * focus-sink Box that used to sit above this was working around.
 */
@Composable
fun ExerciseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    ExerciseSearchField(value = value, onValueChange = onValueChange, placeholder = placeholder,
        modifier = modifier, enabled = true)
}

@Composable
internal fun ExerciseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.control)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape)
            .padding(start = Metrics.space4, end = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = "Search exercises", tint = TextTertiary)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = InstrumentType.body,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise-search"),
                textStyle = InstrumentType.body.copy(color = TextPrimary),
                singleLine = true,
                cursorBrush = SolidColor(Volt),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, enabled = enabled) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = TextSecondary)
            }
        }
    }
}

/** Enough of the screen that the catalog is the sheet, with the scrim still legible above it. */
private const val SHEET_HEIGHT_SHARE = 0.9f
