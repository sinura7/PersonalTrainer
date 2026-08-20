package com.sinura.personaltrainer.ui.routines

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import java.text.DateFormat
import java.util.Date

@Composable
fun RoutinesScreen(
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: RoutinesViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    Scaffold(
        topBar = {
            RoutinesHeader(
                // While the list is empty its own call to action is the one obvious target;
                // a second create action in the header would compete with it.
                canCreate = state.routines.isNotEmpty(),
                onCreate = onCreateRoutine,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.routines.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Metrics.gutter, vertical = Metrics.space2),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { message -> GymErrorBanner(message) }
                    EmptyState(
                        title = "Build your first plan",
                        body = "Name a routine, add lifts and targets, then start it from Home.",
                        actionLabel = "Create a routine",
                        onAction = onCreateRoutine,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space2,
                        bottom = Metrics.space7,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { message ->
                        item(key = "error") { GymErrorBanner(message) }
                    }
                    // One panel of routines rather than one card each: these are instances of
                    // a single thing, and a card apiece reads as a stack of unrelated objects.
                    item(key = "routines") {
                        GroupedList {
                            state.routines.forEachIndexed { index, routine ->
                                if (index > 0) HairlineDivider()
                                RoutineRow(
                                    routine = routine,
                                    updatedLabel = routineUpdatedLabel(routine, dateFormat),
                                    onOpen = { onOpenRoutine(routine.id) },
                                    onDelete = { pendingDeleteId = routine.id },
                                )
                            }
                        }
                    }
                    item(key = "delete-hint") {
                        Text(
                            "Long-press a routine to delete it.",
                            style = InstrumentType.caption,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val pendingName = state.routines.firstOrNull { it.id == id }?.name ?: "this routine"
        ConfirmActionDialog(
            title = "Delete $pendingName?",
            body = "This cannot be undone. Past workout history stays saved.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun RoutinesHeader(canCreate: Boolean, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(
                start = Metrics.gutter,
                end = Metrics.space2,
                top = Metrics.space2,
                bottom = Metrics.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Routines",
            modifier = Modifier.weight(1f),
            style = InstrumentType.display,
            color = TextPrimary,
        )
        if (canCreate) {
            TextButton(onClick = onCreate) {
                Text("New", style = InstrumentType.bodyStrong, color = Volt)
            }
        }
    }
}

/**
 * A routine as a program rather than as a document.
 *
 * The three lines used to be a title over two typographically identical grey ones, so the
 * lift preview — the only line that says what this routine actually is — carried exactly the
 * weight of its filing metadata. Here the preview keeps reading weight, "updated" drops to a
 * kicker, and the lift count becomes a numeral on the trailing edge.
 *
 * Deleting moved to a long press. As a trailing icon it sat inside the row's own tap target,
 * one slip away from destroying a program, and it was the only accent on the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineRow(
    routine: Routine,
    updatedLabel: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val preview = routine.exercises.take(PREVIEW_LIFTS).joinToString(" · ") { it.exercise.name }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .combinedClickable(
                onLongClickLabel = "Delete ${routine.name}",
                onLongClick = {
                    Haptics.tick(view)
                    onDelete()
                },
                onClick = onOpen,
            )
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                routine.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview.ifEmpty { "No lifts yet" },
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (updatedLabel != null) {
                Kicker(updatedLabel, color = TextTertiary)
            }
        }
        MetricCluster(value = routine.exercises.size.toString(), label = "lifts")
    }
}

private fun routineUpdatedLabel(routine: Routine, dateFormat: DateFormat): String? {
    if (routine.updatedAt <= 0L) return null
    return "Updated ${dateFormat.format(Date(routine.updatedAt))}"
}

private const val PREVIEW_LIFTS = 3
