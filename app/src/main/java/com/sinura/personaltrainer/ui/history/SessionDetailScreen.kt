package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.sessionLogMeta
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.routineName ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            session == null -> {
                EmptyState(
                    title = "Session not found",
                    body = "This workout is no longer on this phone.",
                    modifier = Modifier
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
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
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    item {
                        GymCard {
                            Text(
                                dateFormat.format(Date(session.date)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                sessionLogMeta(
                                    workingSets = workingSets,
                                    volumeLabel = session.workingVolumeKg().toWeightLabel(unit),
                                    durationMinutes = session.durationMinutes,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (session.notes.isNotBlank()) {
                                Text(session.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
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
                    items(exerciseCards) { (exerciseId, exerciseName) ->
                        val sets = session.setsFor(exerciseId)
                        val volume = sets.filterNot { it.isWarmup }.sumOf { it.weightKg * it.reps }
                        GymCard {
                            Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                volume.toWeightLabel(unit),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (sets.isEmpty()) {
                                Text(
                                    "No sets",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    sets.forEach { set ->
                                        val tag = buildString {
                                            append("Set ${set.setNumber}  ·  ${set.weightKg.toWeightLabel(unit)} × ${set.reps}")
                                            if (set.isWarmup) append("  ·  WU")
                                            set.rpe?.let { append("  ·  RPE $it") }
                                        }
                                        Text(tag, style = MaterialTheme.typography.bodyMedium)
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
