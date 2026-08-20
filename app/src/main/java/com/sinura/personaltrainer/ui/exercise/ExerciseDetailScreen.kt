package com.sinura.personaltrainer.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.DayLabel
import com.sinura.personaltrainer.domain.ExerciseHistory
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.PersonalRecord
import com.sinura.personaltrainer.domain.PersonalRecordKind
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toVolumeLabel
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymNumericStyle
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.LabelledTrend
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val history = state.history

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.exercise?.name ?: "Exercise",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> ScreenLoading(modifier = Modifier.padding(padding))

            state.missing -> {
                EmptyState(
                    title = "Exercise missing",
                    body = "This lift was deleted from the library. Your logged sets are still in history.",
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                )
            }

            !history.hasHistory -> {
                EmptyState(
                    title = "Nothing logged yet",
                    body = "Records and trends appear here once you have finished a session with this lift.",
                    actionLabel = "Back",
                    onAction = onBack,
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
                    item { LifetimeCard(history = history, unit = unit) }

                    if (history.records.isNotEmpty()) {
                        item { GymSectionHeader("Records", compact = true) }
                        items(history.records.entries.toList(), key = { it.key.name }) { entry ->
                            RecordCard(kind = entry.key, record = entry.value, unit = unit)
                        }
                    }

                    if (history.weeklyTonnage.size >= MIN_POINTS_FOR_TREND) {
                        item {
                            GymCard {
                                LabelledTrend(
                                    title = "Weekly volume",
                                    values = history.weeklyTonnage.map { it.volumeKg },
                                    startLabel = weekLabel(history.weeklyTonnage.first().weekStart),
                                    endLabel = weekLabel(history.weeklyTonnage.last().weekStart),
                                )
                            }
                        }
                    }

                    val estimates = history.sessions
                        .asReversed()
                        .mapNotNull { it.estimatedOneRepMaxKg }
                    if (estimates.size >= MIN_POINTS_FOR_TREND) {
                        item {
                            GymCard {
                                LabelledTrend(
                                    title = "Estimated 1RM",
                                    values = estimates,
                                    startLabel = estimates.first().toWeightLabel(unit),
                                    endLabel = estimates.last().toWeightLabel(unit),
                                )
                            }
                        }
                    }

                    item { GymSectionHeader("Every session", compact = true) }
                    items(history.sessions, key = { it.sessionId }) { summary ->
                        SessionSummaryCard(
                            summary = summary,
                            unit = unit,
                            onClick = { onOpenSession(summary.sessionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifetimeCard(history: ExerciseHistory, unit: WeightUnit) {
    GymCard {
        Text("All time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(value = history.sessions.size.toString(), label = "sessions")
            Stat(value = history.lifetimeWorkingSets.toString(), label = "working sets")
            Stat(value = history.lifetimeVolumeKg.toVolumeLabel(unit), label = "volume")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = GymNumericStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordCard(kind: PersonalRecordKind, record: PersonalRecord, unit: WeightUnit) {
    val title = when (kind) {
        PersonalRecordKind.WEIGHT -> "Heaviest set"
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Best estimated 1RM"
        PersonalRecordKind.REPS_AT_WEIGHT -> "Most reps at ${record.weightKg.toWeightLabel(unit)}"
    }
    val headline = when (kind) {
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> record.value.toWeightLabel(unit)
        else -> "${record.weightKg.toWeightLabel(unit)} × ${record.reps}"
    }
    val detail = when (kind) {
        // The estimate is derived, so show the set it was derived from; a number with no
        // visible working behind it is not something to train off.
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX ->
            "from ${record.weightKg.toWeightLabel(unit)} × ${record.reps}"
        else -> null
    }
    GymCard {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(detail, dateLabel(record.achievedAt)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionSummaryCard(
    summary: ExerciseSessionSummary,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    GymCard(onClick = onClick) {
        Text(
            dateLabel(summary.performedAtMs),
            style = MaterialTheme.typography.titleMedium,
        )
        summary.topSet?.let { top ->
            Text(
                "Top set ${top.weightKg.toWeightLabel(unit)} × ${top.reps}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            summary.sets.joinToString("   ") { "${it.weightKg.toWeightLabel(unit)} × ${it.reps}" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${summary.workingSets} working ${if (summary.workingSets == 1) "set" else "sets"} · " +
                summary.volumeKg.toVolumeLabel(unit),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun dateLabel(atMs: Long): String {
    val absolute = remember(atMs) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atMs)) }
    val relative = remember(atMs) { DayLabel.relative(atMs, System.currentTimeMillis()) }
    return relative ?: absolute
}

private val weekFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun weekLabel(weekStart: java.time.LocalDate): String = weekFormatter.format(weekStart)

private const val MIN_POINTS_FOR_TREND = 2
