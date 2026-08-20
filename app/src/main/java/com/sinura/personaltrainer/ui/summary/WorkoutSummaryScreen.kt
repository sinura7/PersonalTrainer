package com.sinura.personaltrainer.ui.summary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.SessionHighlight
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.domain.toVolumeLabel
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymNumericStyle
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.util.Date

/**
 * What the workout amounted to, shown once, immediately after finishing.
 *
 * Finishing used to pop silently back to Home. The app knew a session had just set two
 * personal bests and said nothing about it — spending the one moment it has the lifter's full
 * attention on a screen transition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: WorkoutSummaryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val summary = state.summary

    // Back is Done. The workout behind this screen is finished and gone from the stack, so
    // there is nowhere else back could sensibly lead.
    BackHandler(enabled = !state.isLoading) { onDone() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Workout complete") }) },
    ) { padding ->
        when {
            state.isLoading -> ScreenLoading(modifier = Modifier.padding(padding))

            state.missing || !summary.hasWork -> {
                EmptyState(
                    title = "Workout saved",
                    body = "It is in your history. Nothing to summarise from this one.",
                    actionLabel = "Done",
                    onAction = onDone,
                    modifier = Modifier
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = GymMetrics.screenContentPadding,
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    item { HeadlineCard(summary = summary, unit = unit) }

                    if (summary.recordCount > 0) {
                        item {
                            GymCard(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Text(
                                    if (summary.recordCount == 1) "1 personal record" else "${summary.recordCount} personal records",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                summary.highlights
                                    .filter { it.records.isNotEmpty() }
                                    .forEach { highlight ->
                                        Text(
                                            "${highlight.exerciseName} · ${highlight.records.joinToString(", ") { it.label }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                            }
                        }
                    }

                    item { GymSectionHeader("Lifts", compact = true) }
                    items(summary.highlights, key = { it.exerciseId }) { highlight ->
                        HighlightCard(highlight = highlight, unit = unit)
                    }

                    if (summary.notes.isNotBlank()) {
                        item {
                            GymCard {
                                Text(
                                    "Notes",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(summary.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryGymButton(text = "Done", onClick = onDone)
                            SecondaryGymButton(
                                text = "See full session",
                                onClick = { onOpenSession(summary.sessionId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadlineCard(summary: WorkoutSummary, unit: WeightUnit) {
    val dateLabel = remember(summary.performedAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(summary.performedAtMs))
    }
    GymCard {
        Text(summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(summary.workingSets.toString(), "working sets")
            Stat(summary.volumeKg.toVolumeLabel(unit), "volume")
            Stat("${summary.durationMinutes} min", "duration")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = GymNumericStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HighlightCard(highlight: SessionHighlight, unit: WeightUnit) {
    GymCard {
        Text(highlight.exerciseName, style = MaterialTheme.typography.titleMedium)
        highlight.topSet?.let { top ->
            Text(
                "Top set ${top.weightKg.toWeightLabel(unit)} × ${top.reps}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            "${highlight.workingSets} ${if (highlight.workingSets == 1) "set" else "sets"} · " +
                highlight.volumeKg.toVolumeLabel(unit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (highlight.records.isNotEmpty()) {
            Text(
                highlight.records.joinToString(" · ") { it.label },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val PersonalRecordKind.label: String
    get() = when (this) {
        PersonalRecordKind.WEIGHT -> "Heaviest ever"
        PersonalRecordKind.REPS_AT_WEIGHT -> "Most reps at that weight"
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Best estimated 1RM"
    }
