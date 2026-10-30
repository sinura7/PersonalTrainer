package com.sinura.personaltrainer.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.workout.WorkoutPreview
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.UndoHostCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentMenuItem
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.CompletedReceiptNotes
import com.sinura.personaltrainer.ui.components.CompletedNotesConflict
import com.sinura.personaltrainer.ui.components.NotesLeaveDialog
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

object SessionDetailTestTags {
    const val CONTENT = "session-detail-content"
    const val EDIT_SET = "session-detail-edit-set"
    const val BACK = "session-detail-back"
    const val OPTIONS = "session-detail-options"
    const val DELETE = "session-detail-delete"
    const val RETRY = "session-detail-retry"
    const val REPEAT = "session-detail-repeat"
    const val EFFORT_HINT = "session-detail-effort-hint"
    fun liftCard(exerciseId: String) = "session-detail-lift-$exerciseId"
}

internal fun sessionDeleteTitle(routineName: String?): String =
    routineName?.takeIf { it.isNotBlank() }?.let { "Delete $it?" } ?: "Delete this session?"

/**
 * A finished session as a filled program sheet.
 *
 * The header is the same readout as the summary shown the moment it ended. Each
 * exact exercise is the same card as the floor / program: still, number, name,
 * every original prescription and one list of saved sets. Repeated prescriptions
 * keep their original program positions. Repair still lives
 * here — Edit opens the set sheet — but the page is no longer a grouped text
 * receipt.
 *
 * Set edits keep `completedAt` and `setNumber`; added sets are stamped inside
 * the session's own window; the duration is never recomputed. A repair fixes
 * what was recorded, never when it happened.
 */
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenActiveSession: (String) -> Unit,
    viewModel: SessionDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val deletedSet by viewModel.deletedSet.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val blockedRepeat by viewModel.blockedRepeat.collectAsStateWithLifecycle()
    val notesExitRequested by viewModel.notesExitRequested.collectAsStateWithLifecycle()
    val notesExitBlocked by viewModel.notesExitBlocked.collectAsStateWithLifecycle()
    val notesRecovery by viewModel.notesRecovery.collectAsStateWithLifecycle()
    val setSave by viewModel.setSave.collectAsStateWithLifecycle()
    val setEditorExit by viewModel.setEditorExit.collectAsStateWithLifecycle()
    val editorOriginal by viewModel.editorOriginal.collectAsStateWithLifecycle()
    val finishedDraft by viewModel.finishedSetState.collectAsStateWithLifecycle()
    val leave = { viewModel.requestNotesExit() }

    BackHandler(onBack = leave)
    val session = state.session
    val unit = LocalWeightUnit.current
    val clock = LocalClockFormat.current

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // R5 / 4.15: Repeat goes through the pre-workout preview; only Begin starts the session.
    var repeatPreview by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingToExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val addDrafts = rememberSaveableStateHolder()
    val discardAddDraft = {
        val exerciseId = addingToExerciseId
        val sessionId = addingSessionId
        if (exerciseId != null && sessionId != null) {
            addDrafts.removeState(addSetDraftKey(sessionId, exerciseId))
        }
        addingToExerciseId = null
        addingSessionId = null
    }

    LaunchedEffect(finishedDraft.visible, finishedDraft.draft?.original, setSave.pending) {
        if (viewModel.finishedSetForm == null) return@LaunchedEffect
        val retained = finishedDraft.draft?.original
        if (finishedDraft.visible && retained != null) {
            if (retained.editing) editingSetId = retained.setId else {
                addingSessionId = retained.sessionId
                addingToExerciseId = retained.exerciseId
            }
        } else if (!setSave.pending) {
            editingSetId = null
            addingToExerciseId = null
            addingSessionId = null
        }
    }
    LaunchedEffect(setEditorExit) {
        val completed = setEditorExit ?: return@LaunchedEffect
        if (completed.editing && editingSetId == completed.setId) editingSetId = null
        if (!completed.editing && addingSessionId == completed.sessionId && addingToExerciseId == completed.exerciseId) {
            discardAddDraft()
        }
        viewModel.onSetEditorExitHandled(completed)
    }
    // A restored pending command reopens its own editor, never the last selected row.
    LaunchedEffect(setSave.command) {
        val pending = setSave.command ?: return@LaunchedEffect
        if (pending.editing) editingSetId = pending.setId else {
            addingSessionId = pending.sessionId
            addingToExerciseId = pending.exerciseId
        }
    }

    LaunchedEffect(notesExitRequested) {
        if (notesExitRequested) {
            viewModel.onNotesExitHandled()
            onBack()
        }
    }

    // Navigation is state, not a captured callback: the repeat writes a session row first, and
    // an Activity recreated in that window would leave the lambda pointing at a dead
    // NavController. Ack after navigating, matching the forward navigations elsewhere.
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }
    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        onOpenActiveSession(target)
        viewModel.onNavigationHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit),
        ) {
            ScreenHeader(
                title = session?.routineName ?: "Session",
                onBack = leave,
                backTag = SessionDetailTestTags.BACK,
                paintBackground = false,
                trailing = {
                    if (session != null) {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.testTag(SessionDetailTestTags.OPTIONS),
                            ) {
                                Icon(
                                    OutlinedMarks.MoreVert,
                                    contentDescription = "Session options",
                                    tint = TextSecondary,
                                )
                            }
                            InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                InstrumentMenuItem(
                                    spokenLabel = "Repeat workout",
                                    leadingIcon = OutlinedMarks.PlaylistAdd,
                                    onClick = {
                                        menuOpen = false
                                        repeatPreview = true
                                    },
                                )
                                InstrumentMenuItem(
                                    spokenLabel = "Delete session…",
                                    leadingIcon = TemperIcons.Delete,
                                    textColor = TextSecondary,
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                    modifier = Modifier.testTag(SessionDetailTestTags.DELETE),
                                )
                            }
                        }
                    }
                },
            )

            val retained = finishedDraft.draft?.original
            val absentEditor = retained != null && (session == null ||
                (retained.editing && session.sets.none { it.id == retained.setId }) ||
                (!retained.editing && session.filledLifts().none { it.exercise.id == retained.exerciseId }))
            if (viewModel.finishedSetForm != null && (finishedDraft.message != null || absentEditor ||
                    (!finishedDraft.visible && finishedDraft.draft != null))) {
                HistoricalSetRecovery(formState = finishedDraft, enabled = !setSave.busy && !state.notesExiting && !finishedDraft.keeping,
                    onRead = viewModel::retryHistoricalDraft,
                    onDiscard = { viewModel.discardHistoricalEditor {} },
                    onResume = { viewModel.finishedSetForm?.showKept() })
            }
            when {
                state.isLoading -> {
                    ScreenLoading()
                }
                // Before the missing branch on purpose: a failed read also has no session,
                // and it must not be shown as a deleted one.
                state.failed -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    EmptyState(
                        scene = EmptyScene.RETRY,
                        title = DataHealthCopy.SESSION_TITLE,
                        body = DataHealthCopy.SESSION_BODY,
                        actionLabel = DataHealthCopy.RETRY,
                        onAction = viewModel::retry,
                        actionTag = SessionDetailTestTags.RETRY,
                    )
                    TextButton(onClick = leave) { Text("Back") }
                    CompletedNotesConflict(raw = state.notes, state = notesRecovery, onRetry = viewModel::retryPrivateNotes,
                        onDiscard = viewModel::discardPrivateNotes, allowDiscard = !state.notesExiting && !state.notesSave.busy && !setSave.pending)
                }
                state.missing || session == null -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    EmptyState(
                        scene = EmptyScene.GONE,
                        title = "Session not found",
                        body = "This workout is no longer on this phone.",
                        actionLabel = "Back",
                        onAction = leave,
                    )
                    CompletedNotesConflict(raw = state.notes, state = notesRecovery, onRetry = viewModel::retryPrivateNotes,
                        onDiscard = viewModel::discardPrivateNotes, allowDiscard = !state.notesExiting && !state.notesSave.busy && !setSave.pending)
                }
                else -> {
                    val lifts = session.filledLifts()
                    val workingSets = session.workingSetCount()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(SessionDetailTestTags.CONTENT),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                    ) {
                        item {
                            SessionReceipt(
                                dateLabel = DateCopy.dateTime(session.date, clock),
                                work = session.work(),
                                workingSets = workingSets,
                                durationMinutes = session.durationMinutes,
                                notes = state.notes,
                                notesSave = state.notesSave,
                                notesExpanded = notesOpen,
                                notesEnabled = !state.notesExiting && (session.isFinished.not() || notesRecovery.ready),
                                onToggleNotes = { notesOpen = !notesOpen },
                                onNotesChange = viewModel::setNotes,
                                onRetryNotes = viewModel::retryNotesSave,
                                unit = unit,
                                notesContent = if (session.isFinished) {
                                    {
                                        CompletedReceiptNotes(
                                            notes = state.notes, state = notesRecovery, save = state.notesSave, expanded = notesOpen,
                                            onToggle = { notesOpen = !notesOpen }, onChange = viewModel::setNotes,
                                            onRetrySave = viewModel::retryNotesSave, onRetryPrivate = viewModel::retryPrivateNotes,
                                            onDiscard = viewModel::discardPrivateNotes, allowActions = !state.notesExiting,
                                        )
                                    }
                                } else null,
                            )
                        }
                        if (lifts.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.history_effort_hint),
                                    style = InstrumentType.caption,
                                    color = TextSecondary,
                                    modifier = Modifier.testTag(SessionDetailTestTags.EFFORT_HINT),
                                )
                            }
                        }
                        if (lifts.isEmpty()) {
                            item {
                                EmptyState(
                                    scene = EmptyScene.LOG,
                                    title = "No sets logged",
                                    body = "Nothing was recorded for this workout.",
                                    compact = true,
                                )
                            }
                        }
                        items(lifts, key = { it.exercise.id }) { lift ->
                            FilledLiftCard(
                                lift = lift,
                                loadClass = session.loadClassOf(lift.exercise.id),
                                unit = unit,
                                onOpen = { onOpenExercise(lift.exercise.id) },
                                onEditSet = {
                                    if (viewModel.beginSetEdit(it)) editingSetId = it.id
                                },
                                onAddSet = {
                                    if (viewModel.beginAdditionalSet(lift.exercise.id)) {
                                        discardAddDraft()
                                        addingSessionId = session.id
                                        addingToExerciseId = lift.exercise.id
                                    }
                                },
                            )
                        }
                    }
                    if (session.isFinished && lifts.isNotEmpty()) {
                        HairlineDivider(startIndent = 0.dp)
                        PrimaryGymButton(
                            text = stringResource(R.string.history_repeat),
                            onClick = { repeatPreview = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(Metrics.gutter)
                                .testTag(SessionDetailTestTags.REPEAT),
                            enabled = !state.notesExiting,
                            height = Metrics.commit,
                        )
                    }
                }
            }
        }
        error?.takeIf { editingSetId == null && addingToExerciseId == null }?.let { message ->
            GymErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                onDismiss = { viewModel.onErrorShown() },
            )
        }
        deletedSet?.let { removed ->
            GymUndoHost(
                message = UndoHostCopy.setDeleted(
                    SetCopy.setLine(
                        weightKg = removed.weightKg,
                        reps = removed.reps,
                        loadClass = session?.loadClassOf(removed.exerciseId) ?: LoadClass.LOADED,
                        unit = unit,
                        durationSeconds = removed.durationSeconds,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                onUndo = { viewModel.undoDeleteSet() },
                onDismissed = { viewModel.onUndoOfferHandled() },
            )
        }
    }

    if (notesExitBlocked) {
        NotesLeaveDialog(
            saveState = state.notesSave.copy(busy = state.notesSave.busy || state.notesExiting),
            liveDraft = false,
            keepDraftOnLeave = viewModel.durableCompletedNotes,
            onRetry = { viewModel.requestNotesExit() },
            onKeepEditing = {
                viewModel.keepEditingNotes()
                notesOpen = true
            },
            onLeave = { viewModel.requestNotesExit(leaveWithoutChanges = true) },
        )
    }

    val editing = editingSetId?.let { id -> session?.sets?.firstOrNull { it.id == id } }?.let { row ->
        val snapshot = (finishedDraft.draft?.original ?: editorOriginal)?.takeIf { it.setId == row.id && it.exerciseId == row.exerciseId }
        if (snapshot == null) row else row.copy(weightKg = snapshot.values.weightKg, reps = snapshot.values.reps,
            rpe = snapshot.values.rpe, isWarmup = snapshot.values.isWarmup, completedAt = snapshot.completedAt,
            durationSeconds = snapshot.values.durationSeconds)
    }
    if (editing != null && session != null) {
        CompositionLocalProvider(LocalFinishedSetForm provides viewModel.finishedSetForm,
            LocalFinishedSetDiscard provides { viewModel.discardHistoricalEditor {} },
            LocalFinishedSetReadAgain provides viewModel::retryHistoricalDraft) {
            SetEditSheet(
            exercise = session.filledLifts().first { it.exercise.id == editing.exerciseId }.exercise.let { projected ->
                projected.copy(name = editing.exerciseName.ifBlank { projected.name })
            },
            initial = editing,
            onSave = { weightKg, reps, rpe, isWarmup, durationSeconds ->
                viewModel.saveCorrection(
                    setId = editing.id,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = isWarmup,
                    durationSeconds = durationSeconds,
                )
            },
            onDelete = {
                viewModel.discardHistoricalEditor {
                    viewModel.dismissSetEdit(editing.id)
                    editingSetId = null
                    viewModel.deleteSet(editing.id)
                }
            },
            onDismiss = {
                if (setSave.pending && viewModel.finishedSetForm == null) viewModel.releaseSetSave(cancel = true) else {
                    viewModel.closeHistoricalEditor {
                        viewModel.dismissSetEdit(editing.id)
                        editingSetId = null
                    }
                }
            },
            loadClass = session.loadClassOf(editing.exerciseId),
            plated = session.isBarbell(editing.exerciseId),
            saveState = setSave,
            validationMessage = error,
            onRetry = viewModel::retrySetSave,
            onEditValues = { viewModel.releaseSetSave(cancel = false) },
        )
        }
    }

    val adding = addingToExerciseId
    val addingExercise = session?.filledLifts()?.firstOrNull { it.exercise.id == adding }?.exercise
    LaunchedEffect(adding, addingSessionId, addingExercise?.id, session?.id, state.isLoading, state.failed) {
        // A missing row is not proof the lift was removed. Retry publishes a loading
        // snapshot with no session before the row returns; clearing here would drop the
        // open Add sheet and it would not come back.
        if (!keepAddSelection(
                adding = adding,
                sessionId = session?.id,
                addingSessionId = addingSessionId,
                liftPresent = addingExercise != null,
                loading = state.isLoading,
                failed = state.failed,
                savePending = setSave.pending,
            )
        ) {
            discardAddDraft()
        }
    }
    // A degraded read can retain the last session. Expose Retry while unavailable,
    // retaining the exact owner and its draft until a successful read confirms removal.
    val openAdding = adding
    val openSession = session
    val openExercise = addingExercise
    val addReadable = openAdding != null && openSession != null && openSession.id == addingSessionId &&
        openExercise != null && !state.isLoading && !state.failed
    val dismissEpoch = remember { intArrayOf(0) }
    val addSheetHiddenForRead = remember { booleanArrayOf(false) }
    if (!addReadable && openAdding != null) {
        if (!addSheetHiddenForRead[0]) dismissEpoch[0] += 1
        addSheetHiddenForRead[0] = true
    }
    if (addReadable && addSheetHiddenForRead[0]) addSheetHiddenForRead[0] = false
    if (openAdding != null && openSession != null && openExercise != null && addReadable) {
        // A new set almost always continues the last one, so it opens on those numbers rather
        // than on zero — the same courtesy the live logger extends.
        val previous = openSession.setsFor(openAdding).lastOrNull()
        val name = openSession.exercises.firstOrNull { it.exercise.id == openAdding }?.exercise?.name
            ?: openSession.sets.firstOrNull { it.exerciseId == openAdding }?.exerciseName
            ?: "Exercise"
        val openedAt = dismissEpoch[0]
        addDrafts.SaveableStateProvider(key = addSetDraftKey(openSession.id, openAdding)) {
            CompositionLocalProvider(LocalFinishedSetForm provides viewModel.finishedSetForm,
            LocalFinishedSetDiscard provides { viewModel.discardHistoricalEditor {} },
            LocalFinishedSetReadAgain provides viewModel::retryHistoricalDraft) {
                SetEditSheet(
                exercise = openExercise.copy(name = name),
                initial = null,
                onSave = { weightKg, reps, rpe, isWarmup, _ ->
                    viewModel.saveAdditionalSet(
                        exerciseId = openAdding,
                        weightKg = weightKg,
                        reps = reps,
                        rpe = rpe,
                        isWarmup = isWarmup,
                    )
                },
                onDelete = null,
                onDismiss = {
                    // Removing the sheet because the session could not be read is not the
                    // lifter closing it. A late dismiss from that removal must not drop the Add.
                    if (dismissEpoch[0] == openedAt) {
                        if (setSave.pending && viewModel.finishedSetForm == null) viewModel.releaseSetSave(cancel = true)
                        else viewModel.closeHistoricalEditor(discardAddDraft)
                    }
                },
                prefillWeightKg = previous?.weightKg ?: 0.0,
                prefillReps = previous?.reps ?: DEFAULT_ADD_REPS,
                loadClass = openSession.loadClassOf(openAdding),
                plated = openSession.isBarbell(openAdding),
                saveState = setSave,
                validationMessage = error,
                onRetry = viewModel::retrySetSave,
                onEditValues = { viewModel.releaseSetSave(cancel = false) },
            )
            }
        }
    }

    // If a successful read proves the edited row/lift gone, its frozen operation
    // still needs an explicit recovery path. Read failure itself cannot discard it.
    if (setSave.pending && !state.isLoading && !state.failed &&
        (session == null || (setSave.command?.editing == true && editing == null) ||
            (setSave.command?.editing == false && addingExercise == null))) {
        ConfirmActionDialog(
            title = "Review saved set",
            body = setSave.message ?: "Checking the saved set. Your submitted values are retained.",
            confirmLabel = "Check save",
            onConfirm = viewModel::retrySetSave,
            confirmEnabled = !setSave.busy && setSave.phase != com.sinura.personaltrainer.ui.workout.WorkoutSavePhase.CONFLICT,
            dismissLabel = if (viewModel.finishedSetForm == null) "Cancel" else "Close · keep edit",
            recoveryContent = if (viewModel.finishedSetForm != null) ({
                HistoricalSetRecovery(formState = finishedDraft, enabled = !setSave.busy && !finishedDraft.keeping,
                    onRead = viewModel::retryHistoricalDraft, onDiscard = { viewModel.discardHistoricalEditor {} }, onResume = {})
            }) else null,
            onDismiss = {
                if (viewModel.finishedSetForm == null) viewModel.releaseSetSave(cancel = true)
                else viewModel.closeHistoricalEditor { editingSetId = null; discardAddDraft() }
            },
        )
    }

    if (confirmDelete && session != null) {
        val total = session.sets.size
        ConfirmActionDialog(
            title = sessionDeleteTitle(session.routineName),
            body = "This deletes the session and its $total logged " +
                (if (total == 1) "set" else "sets") + " from history. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSession()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }

    if (repeatPreview && session != null) {
        WorkoutPreviewScreen(
            model = WorkoutPreview.fromSession(session = session),
            unit = unit,
            onBegin = {
                repeatPreview = false
                viewModel.repeatSession()
            },
            onBack = { repeatPreview = false },
            onReorder = {},
            showReorder = false,
            onAddLift = { repeatPreview = false },
            beginState = if (state.notesExiting) ActionState.Disabled else ActionState.Enabled,
        )
    }

    if (blockedRepeat != null) {
        ConfirmActionDialog(
            title = "Session in progress",
            body = "Finish or discard the current session before starting another.",
            confirmLabel = LiveBarCopy.resumeLabel(LiveBarKind.WORKOUT),
            onConfirm = viewModel::resumeBlockedSession,
            onDismiss = viewModel::dismissBlockedRepeat,
        )
    }
}

/**
 * The session as a readout, in the same shape as the summary shown the moment it ended — so
 * "just finished" and "last March" are the same instrument.
 *
 * The three numbers used to be one sentence in body text, where the word "working" carried
 * the same weight as the tonnage beside it and nothing lined up between two sessions.
 *
 * The notes tail is now writable. A session you finished last week saying nothing about how
 * it went, with no way to add that, was the same defect as an uncorrectable set.
 */
@Composable
private fun SessionReceipt(
    dateLabel: String,
    work: SetWork,
    workingSets: Int,
    durationMinutes: Int,
    notes: String,
    notesSave: NotesSaveState,
    notesExpanded: Boolean,
    notesEnabled: Boolean,
    onToggleNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    onRetryNotes: () -> Unit,
    unit: WeightUnit,
    notesContent: (@Composable () -> Unit)? = null,
) {
    // The headline is whichever unit this session was actually done in. A calisthenics day
    // reading a giant "0 kg" would be the bodyweight stand-in's failure inverted: instead of
    // inventing work that did not happen, erasing work that did.
    val column = SetCopy.workColumn(work, unit)
    GymCard {
        Text(dateLabel, style = InstrumentType.caption, color = TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                column.value,
                modifier = Modifier.alignByBaseline(),
                style = InstrumentType.numeralXl,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                column.label,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = Metrics.space1),
                style = InstrumentType.unit,
                color = TextSecondary,
            )
        }
        Kicker("Working volume")
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
        ) {
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = durationMinutes.toString(),
                label = "min",
                horizontalAlignment = Alignment.Start,
            )
        }
        HairlineDivider(startIndent = 0.dp)
        if (notesContent != null) notesContent() else NotesBlock(
            notes = notes,
            expanded = notesExpanded,
            onToggle = onToggleNotes,
            onChange = onNotesChange,
            saveState = notesSave,
            onRetryNotes = onRetryNotes,
            enabled = notesEnabled,
        )
    }
}

private fun WorkoutSession.isBarbell(exerciseId: String): Boolean =
    exercises.any { it.exercise.id == exerciseId && it.exercise.equipment == EquipmentType.BARBELL }

// Length-prefix the session so arbitrary imported IDs cannot alias another owner.
private fun addSetDraftKey(sessionId: String, exerciseId: String): String =
    "${sessionId.length}:$sessionId:$exerciseId"

private const val DEFAULT_ADD_REPS = 5

/**
 * Whether an open Add must stay selected.
 *
 * A successful session that no longer contains the lift is the only reason to drop it.
 * Loading, a failed read, or a snapshot with no session yet are not that proof: Retry
 * shows those on the way back to the same row, and dropping the Add there leaves the
 * sheet unable to open again.
 */
internal fun keepAddSelection(
    adding: String?,
    sessionId: String?,
    addingSessionId: String?,
    liftPresent: Boolean,
    loading: Boolean,
    failed: Boolean,
    savePending: Boolean,
): Boolean {
    if (adding == null || savePending || loading || failed || sessionId == null) return true
    return sessionId == addingSessionId && liftPresent
}
