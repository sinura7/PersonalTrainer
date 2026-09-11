package com.sinura.personaltrainer.ui.history

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

object SessionDetailTestTags {
    const val CONTENT = "session-detail-content"
    const val EDIT_SET = "session-detail-edit-set"
    const val BACK = "session-detail-back"
    const val OPTIONS = "session-detail-options"
    const val DELETE = "session-detail-delete"
}

internal fun sessionDeleteTitle(routineName: String?): String =
    routineName?.takeIf { it.isNotBlank() }?.let { "Delete $it?" } ?: "Delete this session?"

/**
 * A finished session, and — as of this phase — a correctable one.
 *
 * The screen was a receipt: everything it showed was true and none of it could be fixed. A
 * mistyped weight from three weeks ago stayed wrong forever, silently skewing the heat map,
 * the volume trend and the records built on top of it, and the only remedy the app offered
 * was to delete the whole session.
 *
 * What edits here do NOT touch is the point of the design. Set edits keep `completedAt` and
 * `setNumber`; added sets are stamped inside the session's own window; the duration is never
 * recomputed. A repair fixes what was recorded, never when it happened.
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
    val leave = {
        viewModel.persistNotesForExit()
        onBack()
    }

    BackHandler(onBack = leave)
    val session = state.session
    val unit = LocalWeightUnit.current
    val clock = LocalClockFormat.current

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingToExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

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
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "Session options",
                                    tint = TextSecondary,
                                )
                            }
                            InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Repeat workout",
                                            style = InstrumentType.bodyStrong,
                                            color = TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.repeatSession()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete session…",
                                            style = InstrumentType.bodyStrong,
                                            color = TextSecondary,
                                        )
                                    },
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

            when {
                state.isLoading -> {
                    ScreenLoading()
                }
                session == null -> {
                    EmptyState(
                        title = "Session not found",
                        body = "This workout is no longer on this phone.",
                        actionLabel = "Back",
                        onAction = leave,
                        modifier = Modifier.padding(Metrics.gutter),
                    )
                }
                else -> {
                    val exerciseCards = if (session.exercises.isNotEmpty()) {
                        session.exercises.map { it.exercise.id to it.exercise.name }
                    } else {
                        session.sets.map { it.exerciseId to it.exerciseName }.distinctBy { it.first }
                    }
                    val workingSets = session.sets.count { !it.isWarmup }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(SessionDetailTestTags.CONTENT),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
                    ) {
                        item {
                            SessionReceipt(
                                dateLabel = DateCopy.dateTime(session.date, clock),
                                work = session.work(),
                                workingSets = workingSets,
                                durationMinutes = session.durationMinutes,
                                notes = state.notes,
                                notesExpanded = notesOpen,
                                onToggleNotes = { notesOpen = !notesOpen },
                                onNotesChange = viewModel::setNotes,
                                unit = unit,
                            )
                        }
                        if (exerciseCards.isEmpty() && session.sets.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No sets logged",
                                    body = "Nothing was recorded for this workout.",
                                    compact = true,
                                )
                            }
                        }
                        items(exerciseCards, key = { it.first }) { (exerciseId, exerciseName) ->
                            val sets = session.setsFor(exerciseId)
                            val loadClass = session.loadClassOf(exerciseId)
                            val work = SetWork.sum(
                                sets.filterNot { it.isWarmup }
                                    .map { SetWork.of(it.weightKg, it.reps, loadClass) },
                            )
                            ExerciseBlock(
                                name = exerciseName,
                                sets = sets,
                                work = work,
                                loadClass = loadClass,
                                unit = unit,
                                onOpen = { onOpenExercise(exerciseId) },
                                onEditSet = { editingSetId = it.id },
                                onAddSet = { addingToExerciseId = exerciseId },
                            )
                        }
                    }
                }
            }
        }
        error?.let { message ->
            GymErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                onDismiss = { viewModel.onErrorShown() },
            )
        }
        deletedSet?.let { removed ->
            GymStatusBanner(
                message = "Set deleted · ${removed.weightKg.toWeightLabel(unit)} × ${removed.reps}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                actionLabel = "Undo",
                onAction = { viewModel.undoDeleteSet() },
                onDismissed = { viewModel.onUndoOfferHandled() },
            )
        }
    }

    val editing = editingSetId?.let { id -> session?.sets?.firstOrNull { it.id == id } }
    if (editing != null && session != null) {
        SetEditSheet(
            exerciseName = editing.exerciseName,
            initial = editing,
            onSave = { weightKg, reps, rpe, isWarmup ->
                editingSetId = null
                viewModel.updateSet(
                    setId = editing.id,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = isWarmup,
                )
            },
            onDelete = {
                editingSetId = null
                viewModel.deleteSet(editing.id)
            },
            onDismiss = { editingSetId = null },
            loadClass = session.loadClassOf(editing.exerciseId),
            plated = session.isBarbell(editing.exerciseId),
        )
    }

    val adding = addingToExerciseId
    if (adding != null && session != null) {
        // A new set almost always continues the last one, so it opens on those numbers rather
        // than on zero — the same courtesy the live logger extends.
        val previous = session.setsFor(adding).lastOrNull()
        val name = session.exercises.firstOrNull { it.exercise.id == adding }?.exercise?.name
            ?: session.sets.firstOrNull { it.exerciseId == adding }?.exerciseName
            ?: "Exercise"
        SetEditSheet(
            exerciseName = name,
            initial = null,
            onSave = { weightKg, reps, rpe, isWarmup ->
                addingToExerciseId = null
                viewModel.addSet(
                    exerciseId = adding,
                    weightKg = weightKg,
                    reps = reps,
                    rpe = rpe,
                    isWarmup = isWarmup,
                )
            },
            onDelete = null,
            onDismiss = { addingToExerciseId = null },
            prefillWeightKg = previous?.weightKg ?: 0.0,
            prefillReps = previous?.reps ?: DEFAULT_ADD_REPS,
            loadClass = session.loadClassOf(adding),
            plated = session.isBarbell(adding),
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
    notesExpanded: Boolean,
    onToggleNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    unit: WeightUnit,
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
        NotesBlock(
            notes = notes,
            expanded = notesExpanded,
            onToggle = onToggleNotes,
            onChange = onNotesChange,
        )
    }
}

/**
 * One lift, and every set of it.
 *
 * The lift's name and its tonnage head the block; the sets sit in a grouped panel beneath,
 * which is what puts the weights in a column instead of at whatever indent the previous
 * row's sentence happened to end on.
 */
@Composable
private fun ExerciseBlock(
    name: String,
    sets: List<SetLog>,
    work: SetWork,
    loadClass: LoadClass,
    unit: WeightUnit,
    onOpen: () -> Unit,
    onEditSet: (SetLog) -> Unit,
    onAddSet: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.touchMin)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onOpen)
                .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val column = SetCopy.workColumn(work, unit)
            MetricCluster(value = column.value, label = column.label)
        }
        if (sets.isEmpty()) {
            Text(
                "No sets",
                modifier = Modifier.padding(horizontal = Metrics.space2),
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        } else {
            GroupedList {
                sets.forEachIndexed { index, set ->
                    if (index > 0) HairlineDivider()
                    SetRow(set = set, unit = unit, loadClass = loadClass, onEdit = { onEditSet(set) })
                }
            }
        }
        // Tertiary by weight, not by placement: it belongs under the sets it appends to, but a
        // lift you forgot to log is rarer than a lift you want to read.
        TextButton(
            onClick = onAddSet,
            modifier = Modifier.padding(start = Metrics.space2),
        ) {
            Text("Add set", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    }
}

@Composable
private fun SetRow(set: SetLog, unit: WeightUnit, loadClass: LoadClass, onEdit: () -> Unit) {
    val tags = buildList {
        if (set.isWarmup) add("Warm-up")
        set.rpe?.let { add("RPE $it") }
    }
    InstrumentRow(
        title = "Set ${set.setNumber}",
        subtitle = tags.joinToString(" · ").ifEmpty { null },
    ) {
        // Fixed columns, not wrapped content: a 97.5 and a 100 have to land on the same
        // right edge or there is nothing to compare down the list. The weight column keeps its
        // width even for a lift that has no weight — the reps beside it still have to line up
        // with the reps of the loaded lift in the block above.
        MetricCluster(
            value = if (loadClass.weightMeaning == WeightMeaning.NONE) {
                SetCopy.NOTHING_YET
            } else {
                WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(set.weightKg, unit))
            },
            label = if (loadClass.weightMeaning == WeightMeaning.NONE) {
                unit.suffix
            } else {
                "${loadClass.weightMeaning.fieldLabel.lowercase()} ${unit.suffix}"
            },
            modifier = Modifier.width(WEIGHT_COLUMN),
        )
        MetricCluster(
            value = set.reps.toString(),
            label = "reps",
            modifier = Modifier.width(REPS_COLUMN),
        )
        TextButton(
            onClick = onEdit,
            modifier = Modifier.testTag(SessionDetailTestTags.EDIT_SET),
        ) {
            Text("Edit", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    }
}

private fun WorkoutSession.isBarbell(exerciseId: String): Boolean =
    exercises.any { it.exercise.id == exerciseId && it.exercise.equipment == EquipmentType.BARBELL }

private const val DEFAULT_ADD_REPS = 5
private val WEIGHT_COLUMN = 88.dp
private val REPS_COLUMN = 48.dp
