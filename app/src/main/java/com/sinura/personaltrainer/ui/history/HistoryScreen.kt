package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenActiveSession: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val blockedRepeat by viewModel.blockedRepeat.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val today = remember { LocalDate.now() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        onOpenActiveSession(target)
        viewModel.onNavigationHandled()
    }
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit),
        ) {
            Text(
                "History",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
                style = InstrumentType.display,
                color = TextPrimary,
            )

            when {
                state.isLoading -> {
                    ScreenLoading()
                }
                state.sessions.isEmpty() -> {
                    EmptyState(
                        title = "No sessions yet",
                        body = "Finish a workout and it lands here.",
                        actionLabel = "Start workout",
                        onAction = onStartWorkout,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Metrics.gutter),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                            bottom = Metrics.space7,
                        ),
                    ) {
                        item(key = "calendar") {
                            TrainingCalendarCard(
                                month = state.calendar,
                                weekStart = state.weekStart,
                                today = today,
                                onPreviousMonth = viewModel::showPreviousMonth,
                                onNextMonth = viewModel::showNextMonth,
                                // Two sessions in a day is rare; opening the first is the useful
                                // default and the list below reaches the rest.
                                onOpenDay = { day -> day.sessionIds.firstOrNull()?.let(onOpenSession) },
                                unit = unit,
                                modifier = Modifier.padding(bottom = Metrics.sectionGap),
                            )
                        }
                        item(key = "sessions-header") {
                            GymSectionHeader(
                                "All sessions",
                                modifier = Modifier.padding(bottom = Metrics.kickerGap),
                            )
                        }
                        itemsIndexed(state.sessions, key = { _, session -> session.id }) { index, session ->
                            Column(
                                modifier = Modifier
                                    .animateItem()
                                    .clip(groupedRowShape(index, state.sessions.size))
                                    .background(Surface1),
                            ) {
                                if (index > 0) HairlineDivider()
                                SessionLogRow(
                                    title = session.routineName ?: "Workout",
                                    dateLabel = dateFormat.format(Date(session.date)),
                                    workingSets = session.sets.count { !it.isWarmup },
                                    volumeKg = session.workingVolumeKg(),
                                    durationMinutes = session.durationMinutes,
                                    onClick = { onOpenSession(session.id) },
                                    onRepeat = { viewModel.repeatSession(session.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (blockedRepeat != null) {
        // Verbatim the copy Start workout uses for the same situation. Two different
        // explanations of one rule is how a rule stops reading as a rule.
        ConfirmActionDialog(
            title = "Session in progress",
            body = "Finish or discard the current session before starting another.",
            confirmLabel = "Resume workout",
            onConfirm = viewModel::resumeBlockedSession,
            onDismiss = viewModel::dismissBlockedRepeat,
        )
    }
}

/**
 * The sessions read as one grouped panel, but stay individual lazy items.
 *
 * A history is unbounded — every finished session ever, in one query — so pouring the rows
 * into a single `GroupedList` would compose all of them the moment the tab opens. The
 * container is assembled from the rows instead: rounded ends, square middles, a hairline
 * between.
 */
private fun groupedRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(Radius.sm)
    index == 0 -> RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm)
    index == count - 1 -> RoundedCornerShape(bottomStart = Radius.sm, bottomEnd = Radius.sm)
    else -> RectangleShape
}
