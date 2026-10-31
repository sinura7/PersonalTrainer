package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.EmptyScene
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentMenuItem
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.plan.PlanTags
import com.sinura.personaltrainer.ui.plan.PlanViewModel
import com.sinura.personaltrainer.ui.plan.routineDeletionBody
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Date

/** v4 blueprint 3.3 tags. Row tags keep the PlanTags.routine* values so existing flows only add a step. */
object RoutinesTags {
    const val CONTENT = "routines-content"
    const val BACK = "routines-back"
    const val LIBRARY = PlanTags.LIBRARY
    const val NEW_ROUTINE = "routines-new"
    const val EMPTY = "routines-empty"
}

/**
 * v4 blueprint 3.3 / 4.3: the routine library, moved off Plan (F2). Pushed from the Plan header.
 * Pick mode ([onPicked] set) hands the tapped routine back to the caller and hides options.
 */
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onOpenRoutine: (routineId: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onPicked: ((routineId: String) -> Unit)? = null,
    viewModel: PlanViewModel = viewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val navigateToEditor by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.routines_title)
    val newLabel = stringResource(R.string.routines_new)
    val newName = stringResource(R.string.routines_new_name)

    LaunchedEffect(key1 = navigateToEditor) {
        val id = navigateToEditor ?: return@LaunchedEffect
        onOpenRoutine(id)
        viewModel.onEditorNavigationHandled()
    }

    Column(modifier = Modifier.fillMaxSize().background(Pit)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Pit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenHeader(
                title = title,
                modifier = Modifier.weight(1f),
                onBack = onBack,
                backTag = RoutinesTags.BACK,
                titleModifier = Modifier.testTag(PlanTags.ROUTINES).semantics { heading() },
            )
            TextButton(
                onClick = onOpenLibrary,
                modifier = Modifier
                    .padding(end = Metrics.space2, bottom = Metrics.space2)
                    .heightIn(min = Metrics.touchMin)
                    .testTag(RoutinesTags.LIBRARY)
                    .semantics { contentDescription = PlanTags.LIBRARY_SPOKEN },
            ) {
                Text(text = PlanTags.LIBRARY_SPOKEN, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
        if (state.isLoading) return@Column
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(RoutinesTags.CONTENT),
            contentPadding = PaddingValues(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space7,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            if (state.routines.isEmpty()) {
                item(key = "routines-empty") {
                    Box(modifier = Modifier.testTag(RoutinesTags.EMPTY)) {
                        EmptyState(
                            scene = EmptyScene.RACK,
                            title = stringResource(R.string.routines_empty_title),
                            body = stringResource(R.string.routines_empty_body),
                            actionLabel = newLabel,
                            onAction = { viewModel.newRoutine(newName) },
                        )
                    }
                }
            } else {
                if (onPicked == null) {
                    item(key = "routines-new") {
                        SecondaryGymButton(
                            text = newLabel,
                            onClick = { viewModel.newRoutine(newName) },
                            height = Metrics.rowMin,
                            modifier = Modifier.fillMaxWidth().testTag(RoutinesTags.NEW_ROUTINE),
                        )
                    }
                }
                itemsIndexed(items = state.routines, key = { _, routine -> "routine:${routine.id}" }) { index, routine ->
                    if (index > 0) HairlineDivider(startIndent = 0.dp)
                    RoutineRow(
                        routine = routine,
                        updatedLabel = routineUpdatedLabel(routine, dateFormat),
                        onOpen = { if (onPicked != null) onPicked(routine.id) else onOpenRoutine(routine.id) },
                        onDelete = { if (onPicked == null) pendingDeleteId = routine.id },
                        showOptions = onPicked == null,
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val pendingName = state.routines.firstOrNull { it.id == id }?.name ?: "this routine"
        ConfirmActionDialog(
            title = "Delete $pendingName?",
            body = routineDeletionBody(state, id),
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteRoutine(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
internal fun RoutineRow(
    routine: Routine,
    updatedLabel: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    showOptions: Boolean = true,
) {
    val view = LocalView.current
    var menuOpen by remember(routine.id) { mutableStateOf(false) }
    val preview = SessionOrderCopy.numberedPreview(routine.exercises.map { it.exercise.name })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .testTag(PlanTags.routine(routine.id))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Edit ${routine.name}",
                onLongClickLabel = "Delete ${routine.name}",
                onLongClick = {
                    Haptics.tick(view)
                    onDelete()
                },
                onClick = onOpen,
            )
            .padding(vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                text = routine.name,
                modifier = Modifier.testTag(PlanTags.routineName(routine.id)),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
            if (routine.exercises.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                    routine.exercises.take(3).forEach { lift ->
                        Box(modifier = Modifier.testTag(PlanTags.routineThumb(routine.id, lift.exercise.id))) {
                            ExerciseThumb(exercise = lift.exercise)
                        }
                    }
                }
            }
            Text(
                text = preview,
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
            updatedLabel?.let { label ->
                Text(text = label, style = InstrumentType.caption, color = TextTertiary)
            }
        }
        if (showOptions) Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(Metrics.touchMin)
                    .testTag(PlanTags.routineOptions(routine.id)),
            ) {
                Icon(
                    OutlinedMarks.MoreVert,
                    contentDescription = "Options for ${routine.name}",
                    tint = TextSecondary,
                )
            }
            InstrumentMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                InstrumentMenuItem(
                    spokenLabel = "Edit routine",
                    leadingIcon = TemperIcons.Edit,
                    modifier = Modifier.testTag(PlanTags.routineEdit(routine.id)),
                    onClick = {
                        menuOpen = false
                        onOpen()
                    },
                )
                InstrumentMenuItem(
                    spokenLabel = "Delete routine",
                    leadingIcon = TemperIcons.Delete,
                    textColor = Danger,
                    iconTint = Danger,
                    modifier = Modifier.testTag(PlanTags.routineDelete(routine.id)),
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

private fun routineUpdatedLabel(routine: Routine, dateFormat: DateFormat): String? {
    if (routine.updatedAt <= 0L) return null
    return "Updated ${dateFormat.format(Date(routine.updatedAt))}"
}

