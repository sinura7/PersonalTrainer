package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.toVolumeLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.sessions.isEmpty() -> {
                EmptyState(
                    title = "No sessions yet",
                    body = "Finish a workout and it lands here.",
                    actionLabel = "Start workout",
                    onAction = onStartWorkout,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
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
                        )
                    }
                    item(key = "sessions-header") { GymSectionHeader("All sessions", compact = true) }
                    items(state.sessions, key = { it.id }) { session ->
                        SessionLogRow(
                            title = session.routineName ?: "Workout",
                            dateLabel = dateFormat.format(Date(session.date)),
                            workingSets = session.sets.count { !it.isWarmup },
                            volumeLabel = session.workingVolumeKg().toVolumeLabel(unit),
                            durationMinutes = session.durationMinutes,
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}
