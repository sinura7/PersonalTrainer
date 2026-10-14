package com.sinura.personaltrainer.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AddToRoutineCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LibraryFamily
import com.sinura.personaltrainer.domain.LoadTypeCopy
import com.sinura.personaltrainer.domain.MuscleGroups
import com.sinura.personaltrainer.ui.components.AddToRoutineSheet
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.ExerciseSearchField
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.instrumentAnimateItem
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onCreateRoutine: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    initialMuscle: CanonicalMuscle? = null,
    viewModel: ExerciseLibraryViewModel = viewModel(),
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        ExerciseLibraryContent(
            onBack = onBack,
            onCreateRoutine = onCreateRoutine,
            onOpenExercise = onOpenExercise,
            initialMuscle = initialMuscle,
            viewModel = viewModel,
            landscape = maxWidth > maxHeight,
        )
    }
}

@Composable
private fun ExerciseLibraryContent(
    onBack: () -> Unit,
    onCreateRoutine: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    initialMuscle: CanonicalMuscle?,
    viewModel: ExerciseLibraryViewModel,
    landscape: Boolean,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val editorStatus = viewModel.editorStatus.collectAsStateWithLifecycle().value
    // Held as an id rather than the row's own boolean so the sheet survives a rotation, and
    // so it closes by itself if the lift underneath it is deleted.
    var overflowExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var resetRememberedView by rememberSaveable { mutableStateOf(false) }
    val leave: () -> Unit = { viewModel.keepBrowseBeforeLeave(onBack) }
    BackHandler { leave() }
    DisposableEffect(viewModel) {
        viewModel.seedMuscleFromRoute(initialMuscle)
        onDispose { viewModel.closeBrowseVisit() }
    }
    val headerActions: @Composable RowScope.() -> Unit = {
        if (landscape && !state.isLoading && state.browseEditable && state.readState == LibraryReadState.CURRENT && state.visibleExercises.isNotEmpty()) {
            IconButton(
                onClick = viewModel::openCreate,
                modifier = Modifier.size(Metrics.touchMin).testTag(LibraryTags.FAB)
                    .semantics { contentDescription = "Create lift" },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Volt)
            }
        }
    }

    Scaffold(
        topBar = {
            // A back arrow now that Library is pushed rather than a tab. Without one, arriving
            // here from a coach card would be a one-way trip to a screen with no visible exit
            // except the system gesture.
            Column {
            ScreenHeader(
                title = "Library",
                onBack = leave,
                backTag = LibraryTags.BACK,
                kickerTitle = true,
                trailing = headerActions,
            )
            LibraryBrowseNotice(state = state, onRetry = viewModel::retryBrowseKeep,
                onReset = { resetRememberedView = true })
            }
        },
        floatingActionButton = {
            if (!landscape && !state.isLoading && state.browseEditable && state.readState == LibraryReadState.CURRENT && state.visibleExercises.isNotEmpty()) {
                FloatingActionButton(
                    onClick = viewModel::openCreate,
                    modifier = Modifier.testTag(LibraryTags.FAB),
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
                    Icon(Icons.Outlined.Add, contentDescription = "Create lift")
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.readState != LibraryReadState.CURRENT -> {
                EmptyState(
                    title = LibraryReadCopy.TITLE,
                    body = LibraryReadCopy.BODY,
                    scene = EmptyScene.CATALOG,
                    actionLabel = if (state.retryPending) "Retrying…" else "Retry",
                    onAction = { viewModel.retryRead() },
                    actionEnabled = !state.retryPending,
                    actionTag = LibraryTags.READ_RETRY,
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Metrics.gutter, vertical = Metrics.space4)
                        .testTag(LibraryTags.READ_PROBLEM),
                )
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
                        enabled = state.browseEditable,
                        modifier = Modifier
                            .padding(horizontal = Metrics.gutter)
                            .testTag(LibraryTags.SEARCH)
                            .semantics {
                                contentDescription = LibraryTags.SEARCH_SPOKEN
                            },
                    )
                    // Two rows, muscle over equipment, because they answer different questions
                    // and AND-combine: "chest" then "machine" is how someone with a bad shoulder
                    // actually shops for a session. One merged row would have made them look
                    // mutually exclusive.
                    val muscleIndex = state.selectedMuscle?.let { state.muscleFilters.indexOf(it) + 1 } ?: 0
                    val muscleList = rememberLazyListState(initialFirstVisibleItemIndex = muscleIndex)
                    LaunchedEffect(state.selectedMuscle, state.muscleFilters) { muscleList.scrollToItem(muscleIndex) }
                    LazyRow(
                        state = muscleList,
                        modifier = Modifier.padding(top = Metrics.space3).testTag(LibraryTags.MUSCLE_FILTERS),
                        contentPadding = PaddingValues(horizontal = Metrics.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        item(key = "muscle-all") {
                            InstrumentChip(
                                label = "All",
                                selected = state.selectedMuscle == null,
                                onClick = { viewModel.onMuscleSelected(null) },
                                enabled = state.browseEditable,
                            )
                        }
                        items(state.muscleFilters, key = { it.name }) { muscle ->
                            InstrumentChip(
                                label = muscle.displayName,
                                selected = state.selectedMuscle == muscle,
                                onClick = { viewModel.onMuscleSelected(muscle) },
                                enabled = state.browseEditable,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = Metrics.space2, bottom = Metrics.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val equipmentIndex = state.selectedEquipment?.let { state.equipmentFilters.indexOf(it) + 1 } ?: 0
                        val equipmentList = rememberLazyListState(initialFirstVisibleItemIndex = equipmentIndex)
                        LaunchedEffect(state.selectedEquipment, state.equipmentFilters) { equipmentList.scrollToItem(equipmentIndex) }
                        LazyRow(
                            state = equipmentList,
                            modifier = Modifier.weight(1f).testTag(LibraryTags.EQUIPMENT_FILTERS),
                            contentPadding = PaddingValues(horizontal = Metrics.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        ) {
                            item(key = "equipment-all") {
                                InstrumentChip(
                                    label = "Any kit",
                                    selected = state.selectedEquipment == null,
                                    onClick = { viewModel.onEquipmentSelected(null) },
                                    enabled = state.browseEditable,
                                )
                            }
                            items(state.equipmentFilters, key = { it.name }) { equipment ->
                                InstrumentChip(
                                    label = equipment.label,
                                    selected = state.selectedEquipment == equipment,
                                    onClick = { viewModel.onEquipmentSelected(equipment) },
                                    enabled = state.browseEditable,
                                )
                            }
                        }
                        if (state.selectedMuscle != null || state.selectedEquipment != null) {
                            TextButton(
                                onClick = viewModel::clearFilters,
                                enabled = state.browseEditable,
                                modifier = Modifier.testTag(LibraryTags.CLEAR_FILTERS)
                                    .padding(end = Metrics.gutter),
                            ) {
                                Text("Clear filters", style = InstrumentType.caption, color = TextSecondary)
                            }
                        }
                    }
                    state.message?.let { note ->
                        GymStatusBanner(
                            note,
                            modifier = Modifier
                                .padding(horizontal = Metrics.gutter)
                                .padding(bottom = Metrics.space3),
                            onDismissed = viewModel::dismissMessage,
                        )
                    }
                    state.error?.let { err ->
                        GymErrorBanner(
                            err,
                            modifier = Modifier
                                .padding(horizontal = Metrics.gutter)
                                .padding(bottom = Metrics.space3),
                            onDismiss = viewModel::dismissError,
                        )
                    }
                    if (state.visibleExercises.isEmpty() && state.needsAttention.isEmpty()) {
                        val filtered = state.exercises.isNotEmpty()
                        val emptyModifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = Metrics.gutter)
                        if (landscape) {
                            Column(modifier = emptyModifier, verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                                Text(if (filtered) "No matches" else "Library is empty",
                                    style = InstrumentType.title, color = TextPrimary)
                                Text(if (filtered) "Clear search or filters, or add a custom lift."
                                    else "Add a custom lift to start the catalog.",
                                    style = InstrumentType.body, color = TextSecondary)
                                TextButton(onClick = viewModel::openCreate, enabled = state.browseEditable,
                                    modifier = Modifier.testTag(LibraryTags.EMPTY_CREATE)) {
                                    Text("Create lift", style = InstrumentType.bodyStrong, color = Volt)
                                }
                            }
                        } else {
                        EmptyState(
                            scene = EmptyScene.CATALOG,
                            title = if (filtered) "No matches" else "Library is empty",
                            body = if (filtered) {
                                "Clear search or filters, or add a custom lift."
                            } else {
                                "Add a custom lift to start the catalog."
                            },
                            modifier = emptyModifier,
                            actionLabel = "Create lift",
                            actionEnabled = state.browseEditable,
                            actionTag = LibraryTags.EMPTY_CREATE,
                            onAction = viewModel::openCreate,
                        )
                        }
                    } else {
                        HairlineDivider(startIndent = 0.dp)
                        key(state.query, state.selectedMuscle, state.selectedEquipment, state.browseRestoreRevision) {
                        val density = LocalDensity.current.density
                        val resultKeys = remember(state.families, state.visibleExercises,
                            state.needsAttention, state.expandedFamilies) { libraryResultKeys(state) }
                        val rememberedIndex = state.browseAnchor?.let(resultKeys::indexOf)?.takeIf { it >= 0 }
                            ?: state.browseIndex.coerceIn(0, (resultKeys.size - 1).coerceAtLeast(0))
                        val list = rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex,
                            initialFirstVisibleItemScrollOffset = (state.browseOffsetDp * density).toInt())
                        LaunchedEffect(list, state.browseViewRevision, density, resultKeys) {
                            snapshotFlow {
                                val index = list.firstVisibleItemIndex
                                list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index && it.key in resultKeys }?.let {
                                    LibraryVisiblePosition(key = it.key as? String, index = index,
                                        offsetDp = list.firstVisibleItemScrollOffset / density)
                                }
                            }.filterNotNull().collect {
                                viewModel.onBrowsePosition(anchor = it.key, index = it.index,
                                    offsetDp = it.offsetDp, viewRevision = state.browseViewRevision)
                            }
                        }
                        LazyColumn(
                            state = list,
                            modifier = Modifier.weight(1f).testTag(LibraryTags.LIST),
                            // Rows run edge to edge; the screen margin lives inside them.
                            contentPadding = PaddingValues(
                                bottom = if (landscape) Metrics.space4 else Metrics.fabClearance,
                            ),
                        ) {
                            if (state.needsAttention.isNotEmpty()) {
                                item(key = "attention-header") {
                                    Kicker(
                                        "Needs attention",
                                        modifier = Modifier.padding(
                                            start = Metrics.gutter,
                                            end = Metrics.gutter,
                                            top = Metrics.space3,
                                            bottom = Metrics.space2,
                                        ),
                                    )
                                }
                                items(state.needsAttention, key = { "attention-${it.id}" }) { custom ->
                                    Column(modifier = instrumentAnimateItem()) {
                                        CollisionRow(
                                            exercise = custom,
                                            onRename = { viewModel.openEdit(custom) },
                                            onKeepBoth = { viewModel.keepBothNames(custom) },
                                        )
                                        HairlineDivider()
                                    }
                                }
                            }
                            if (state.grouped) {
                                state.families.forEach { family ->
                                    if (family.plain) {
                                        val only = family.members.single()
                                        item(key = only.id) {
                                            Column(modifier = instrumentAnimateItem()) {
                                                LibraryRow(
                                                    exercise = only,
                                                    onOpen = { viewModel.keepBrowseBeforeLeave { onOpenExercise(only.id) } },
                                                    onAddToRoutine = { viewModel.openAddToRoutine(only) },
                                                    onOverflow = { overflowExerciseId = only.id },
                                                )
                                                HairlineDivider()
                                            }
                                        }
                                    } else {
                                        item(key = "family-${family.movementKey}") {
                                            FamilyHeader(
                                                family = family,
                                                expanded = family.movementKey in state.expandedFamilies,
                                                onClick = { viewModel.toggleFamily(family.movementKey) },
                                            )
                                        }
                                        if (family.movementKey in state.expandedFamilies) {
                                            items(family.members, key = { it.id }) { exercise ->
                                                Column(modifier = instrumentAnimateItem()) {
                                                    LibraryRow(
                                                        exercise = exercise,
                                                        onOpen = { viewModel.keepBrowseBeforeLeave { onOpenExercise(exercise.id) } },
                                                        onAddToRoutine = { viewModel.openAddToRoutine(exercise) },
                                                        onOverflow = { overflowExerciseId = exercise.id },
                                                    )
                                                    HairlineDivider()
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    state.visibleExercises,
                                    key = { _, exercise -> exercise.id },
                                ) { index, exercise ->
                                    Column(modifier = instrumentAnimateItem()) {
                                        LibraryRow(
                                            exercise = exercise,
                                            onOpen = { viewModel.keepBrowseBeforeLeave { onOpenExercise(exercise.id) } },
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
        }
    }

    if (resetRememberedView) {
        ConfirmActionDialog(title = "Reset remembered view?",
            body = "This forgets only search, filters and results position. Exercises, custom drafts and workout history stay saved.",
            confirmLabel = "Reset view", destructive = false,
            onConfirm = { resetRememberedView = false; viewModel.resetBrowseView() },
            onDismiss = { resetRememberedView = false })
    }

    state.exercises.firstOrNull { it.id == overflowExerciseId }
        ?.takeIf { state.readState == LibraryReadState.CURRENT }?.let { exercise ->
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
        if (editorStatus.phase != LiftEditorPhase.CLOSED) {
            RecoverableLiftEditorSheet(
                draft = draft, muscleOptions = MuscleGroups.editorOptions(state.exercises), status = editorStatus,
                error = if (state.readState == LibraryReadState.CURRENT) state.error else LibraryReadCopy.BODY,
                libraryReady = state.readState == LibraryReadState.CURRENT && !state.retryPending,
                onDraftChange = viewModel::updateEditor, onSave = viewModel::saveEditor,
                onCheck = viewModel::checkEditorDraft, onEditInstead = viewModel::editUncommittedDraft,
                onDiscard = viewModel::discardEditorDraft, onDismiss = viewModel::dismissEditor,
                onLibraryRetry = if (state.readState == LibraryReadState.UNAVAILABLE || state.readState == LibraryReadState.STALE) ({ viewModel.retryRead(); Unit }) else null,
            )
        } else {
        ExerciseEditorSheet(
            draft = draft,
            muscleOptions = MuscleGroups.editorOptions(state.exercises),
            error = if (state.readState == LibraryReadState.CURRENT) state.error else LibraryReadCopy.BODY,
            saveEnabled = state.readState == LibraryReadState.CURRENT && !state.retryPending,
            onReadRetry = if (state.readState == LibraryReadState.UNAVAILABLE || state.readState == LibraryReadState.STALE) {
                { viewModel.retryRead() }
            } else null,
            readRetryPending = state.retryPending,
            onDraftChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
        }
    }

    state.pendingDelete?.takeIf { state.readState == LibraryReadState.CURRENT }?.let { exercise ->
        ConfirmActionDialog(
            title = "Delete ${exercise.name}?",
            body = "This removes it from your library. Lifts used in routines or history can’t be deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

    state.blockedDelete?.takeIf { state.readState == LibraryReadState.CURRENT }?.let { (exercise, usage) ->
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

    state.addToRoutine?.takeIf { state.readState == LibraryReadState.CURRENT }?.let { exercise ->
        AddToRoutineSheet(
            exercise = exercise,
            destinations = AddToRoutineCopy.destinations(state.routines, exercise.id),
            emptyBody = AddToRoutineCopy.EMPTY_LIBRARY,
            onSelect = viewModel::addToRoutine,
            onDismiss = viewModel::dismissAddToRoutine,
            onCreateRoutine = {
                viewModel.dismissAddToRoutine()
                viewModel.keepBrowseBeforeLeave { onCreateRoutine(exercise.id) }
            },
        )
    }
}

private data class LibraryVisiblePosition(val key: String?, val index: Int, val offsetDp: Float)

internal fun libraryResultKeys(state: ExerciseLibraryUiState): List<String> = buildList {
    if (state.needsAttention.isNotEmpty()) {
        add("attention-header")
        state.needsAttention.forEach { add("attention-${it.id}") }
    }
    if (state.grouped) state.families.forEach { family ->
        if (family.plain) add(family.members.single().id)
        else {
            add("family-${family.movementKey}")
            if (family.movementKey in state.expandedFamilies) family.members.forEach { add(it.id) }
        }
    } else state.visibleExercises.forEach { add(it.id) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryBrowseNotice(state: ExerciseLibraryUiState, onRetry: () -> Unit, onReset: () -> Unit) {
    val text = state.browseError ?: if (state.browseKeeping) "Keeping search…"
        else if (state.browseResumed) "Last search resumed" else return
    Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.gutter, vertical = Metrics.space2)) {
        Text(text, style = InstrumentType.caption, color = TextSecondary,
            modifier = Modifier.testTag(LibraryTags.BROWSE_STATUS))
        if (state.browseError != null) {
            FlowRow {
                TextButton(onClick = onRetry, enabled = !state.browseKeeping,
                    modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                        .testTag(LibraryTags.BROWSE_RETRY)) { Text("Retry Keep") }
                TextButton(onClick = onReset, enabled = !state.browseKeeping,
                    modifier = Modifier.sizeIn(minWidth = Metrics.touchMin, minHeight = Metrics.touchMin)
                        .testTag(LibraryTags.BROWSE_RESET)) { Text("Reset remembered view") }
            }
        }
    }
}

/**
 * A movement family, collapsed.
 *
 * The count is the point: "Bench Press · 8" tells you the app has eight bench variants without
 * showing you eight rows, which is the trade that makes a 98-lift catalog scannable.
 */
@Composable
private fun FamilyHeader(
    family: LibraryFamily,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    InstrumentRow(
        title = family.label,
        subtitle = "${family.members.size} variants",
        onClick = onClick,
    ) {
        Icon(
            if (expanded) OutlinedMarks.ExpandLess else OutlinedMarks.ExpandMore,
            contentDescription = if (expanded) "Collapse ${family.label}" else "Expand ${family.label}",
            tint = TextSecondary,
        )
    }
}

/**
 * One custom lift that shares a name with a built-in.
 *
 * The app will not choose for the owner. It cannot merge them — their history points at their
 * row, and re-pointing foreign keys to "tidy up" is a data migration disguised as a convenience
 * — and it will not rename or delete anything they made. So it says what happened, promises
 * their history is safe, and offers the only two honest options.
 */
@Composable
private fun CollisionRow(
    exercise: Exercise,
    onRename: () -> Unit,
    onKeepBoth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Text(exercise.name, style = InstrumentType.bodyStrong, color = TextPrimary)
        Text(
            "Same name as a built-in lift — your history stays on yours.",
            style = InstrumentType.caption,
            color = TextTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            TextButton(onClick = onRename) {
                Text("Rename", style = InstrumentType.bodyStrong, color = Volt)
            }
            TextButton(onClick = onKeepBoth) {
                Text("Keep both", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
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
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryRow(
    exercise: Exercise,
    onOpen: () -> Unit,
    onAddToRoutine: () -> Unit,
    onOverflow: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.6f
    val identity: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(exercise.name, style = InstrumentType.bodyStrong, color = TextPrimary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                Text(exercise.muscleGroup, style = InstrumentType.caption, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.xs))
                        .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
                ) {
                    Text(
                        LoadTypeCopy.libraryTag(exercise).uppercase(),
                        style = InstrumentType.kicker,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        IconButton(
            onClick = onAddToRoutine,
            modifier = Modifier.size(Metrics.touchMin).testTag(LibraryTags.add(exercise.id)).semantics {
                contentDescription = "Add ${exercise.name} to a routine"
            },
        ) {
            Icon(
                OutlinedMarks.PlaylistAdd,
                contentDescription = null,
                tint = TextSecondary,
            )
        }
        if (exercise.isCustom) {
            IconButton(
                onClick = onOverflow,
                modifier = Modifier.size(Metrics.touchMin).testTag(LibraryTags.options(exercise.id)).semantics {
                    contentDescription = "More for ${exercise.name}"
                },
            ) {
                Icon(
                    OutlinedMarks.MoreVert,
                    contentDescription = null,
                    tint = TextTertiary,
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .testTag(LibraryTags.lift(exercise.id))
            .clickable(role = Role.Button, onClickLabel = "Open lift", onClick = onOpen)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            ExerciseThumb(exercise = exercise)
            if (largeText) Box(modifier = Modifier.weight(1f)) else identity(Modifier.weight(1f))
            actions()
        }
        if (largeText) identity(Modifier.fillMaxWidth())
    }
}

/**
 * Edit and delete, one step back from the row.
 *
 * A sheet rather than a dropdown: two 56 dp rows sit behind overflow so they
 * cannot compete with the lift people came to tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftOverflowSheet(
    exercise: Exercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                SheetActionRow(label = "Edit lift", color = TextPrimary, onClick = onEdit)
                HairlineDivider()
                SheetActionRow(label = "Delete lift", color = Danger, onClick = onDelete)
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
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = InstrumentType.title, color = color)
    }
}

object LibraryTags {
    const val BACK = "library-back"
    const val SEARCH = "library-search"
    const val FAB = "library-create"
    const val EMPTY_CREATE = "library-empty-create"
    const val CLEAR_FILTERS = "library-clear-filters"
    const val EQUIPMENT_FILTERS = "library-equipment-filters"
    const val MUSCLE_FILTERS = "library-muscle-filters"
    const val LIST = "library-list"
    const val READ_PROBLEM = "library-read-problem"
    const val READ_RETRY = "library-read-retry"
    const val EDITOR_READ_RETRY = "library-editor-read-retry"
    const val BROWSE_STATUS = "library-browse-status"
    const val BROWSE_RETRY = "library-browse-retry"
    const val BROWSE_RESET = "library-browse-reset"
    fun lift(id: String) = "library-lift-$id"
    fun add(id: String) = "library-add-$id"
    fun options(id: String) = "library-options-$id"
    const val SEARCH_SPOKEN = "Search lifts by name or muscle"
}
