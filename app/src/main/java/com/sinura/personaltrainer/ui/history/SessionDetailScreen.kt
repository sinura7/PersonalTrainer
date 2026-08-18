package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            session == null -> {
                EmptyState(
                    title = "Session not found",
                    body = "This workout is no longer in the local database.",
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                val exerciseCards = if (session.exercises.isNotEmpty()) {
                    session.exercises.map { it.exercise.id to it.exercise.name }
                } else {
                    session.sets.map { it.exerciseId to it.exerciseName }.distinctBy { it.first }
                }
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(dateFormat.format(Date(session.date)))
                        Text("${session.durationMinutes} min · ${session.workingVolumeKg().toWeightLabel(unit)} working volume")
                        if (session.notes.isNotBlank()) {
                            Text(session.notes)
                        }
                    }
                    items(exerciseCards) { (exerciseId, exerciseName) ->
                        val sets = session.setsFor(exerciseId)
                        val volume = sets.filterNot { it.isWarmup }.sumOf { it.weightKg * it.reps }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(exerciseName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "${volume.toWeightLabel(unit)} volume",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (sets.isEmpty()) {
                                    Text("No sets logged")
                                } else {
                                    sets.forEach { set ->
                                        val tag = buildString {
                                            append("Set ${set.setNumber}: ${set.weightKg.toWeightLabel(unit)} × ${set.reps}")
                                            if (set.isWarmup) append(" · WU")
                                            set.rpe?.let { append(" · RPE $it") }
                                        }
                                        Text(tag)
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
