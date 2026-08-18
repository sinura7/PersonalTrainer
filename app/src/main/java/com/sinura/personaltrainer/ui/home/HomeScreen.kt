package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.draw.clip
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.ui.progress.RecommendationCard
import com.sinura.personaltrainer.ui.progress.dispatchRecommendation
import com.sinura.personaltrainer.ui.progress.heatFill
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenLibraryMuscle: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            HomeHeader(
                unit = unit,
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            PrimaryGymButton(
                text = if (state.inProgress != null) "Resume workout" else "Start workout",
                onClick = {
                    val current = state.inProgress
                    if (current != null) onResumeWorkout(current.id) else onStartWorkout()
                },
            )
        }
        item {
            QuickActionsRow(
                onOpenRoutines = onOpenRoutines,
                onOpenLibrary = onOpenLibrary,
                onOpenHistory = onOpenHistory,
            )
        }
        item {
            TrainingBalanceCard(
                snapshot = state.heatSnapshot,
                recommendations = state.recommendations.take(2),
                onOpenProgress = onOpenProgress,
                onRecommendation = { rec ->
                    dispatchRecommendation(
                        recommendation = rec,
                        onOpenLibrary = onOpenLibraryMuscle,
                        onStartWorkout = onStartWorkout,
                        onOpenRoutines = onOpenRoutines,
                        onOpenProgress = onOpenProgress,
                    )
                },
            )
        }
        state.inProgress?.let { session ->
            item {
                SectionTitle("In progress")
                Spacer(Modifier.height(8.dp))
                InProgressCard(
                    session = session,
                    onResume = { onResumeWorkout(session.id) },
                )
            }
        }
        item {
            SectionTitle("Ready to progress")
        }
        if (state.readyToProgress.isEmpty()) {
            item {
                EmptyState(
                    title = "No increases queued",
                    body = "Hit every target rep on a working set. The next load shows up here in ${unit.suffix}.",
                )
            }
        } else {
            items(state.readyToProgress, key = { it.exerciseId }) { hint ->
                ProgressCard(
                    hint = hint,
                    unit = unit,
                    onClick = onStartWorkout,
                )
            }
        }
        item {
            SectionTitle("Recent activity")
        }
        if (state.recentSessions.isEmpty()) {
            item {
                EmptyState(
                    title = "No recent workouts",
                    body = "Log a set and finish the session. Your last workouts will land here.",
                    actionLabel = if (state.inProgress == null) "Start workout" else null,
                    onAction = if (state.inProgress == null) onStartWorkout else null,
                )
            }
        } else {
            items(state.recentSessions, key = { it.id }) { session ->
                RecentSessionCard(
                    session = session,
                    unit = unit,
                    dateLabel = dateFormat.format(Date(session.date)),
                    onClick = { onOpenSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    unit: WeightUnit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dayGreeting(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Personal Trainer", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Weights shown in ${unit.suffix}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Units · ${unit.suffix}")
        }
    }
}

@Composable
private fun QuickActionsRow(
    onOpenRoutines: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickAction(
            title = "Routines",
            icon = Icons.Outlined.FitnessCenter,
            onClick = onOpenRoutines,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            title = "Library",
            icon = Icons.Outlined.MenuBook,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            title = "History",
            icon = Icons.Outlined.History,
            onClick = onOpenHistory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InProgressCard(
    session: WorkoutSession,
    onResume: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "WORKOUT IN PROGRESS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                session.routineName ?: "Workout",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Pick up where you left off. Rest timer and last set are still there.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onResume) { Text("Resume") }
        }
    }
}

@Composable
private fun ProgressCard(
    hint: ProgressionHint,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hint.exerciseName, style = MaterialTheme.typography.titleMedium)
            Text(
                hint.suggestedWeightKg.toWeightLabel(unit),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Last ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  ·  +${ProgressionCalculator.INCREMENT_KG.toWeightLabel(unit)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Tap to start a workout", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecentSessionCard(
    session: WorkoutSession,
    unit: WeightUnit,
    dateLabel: String,
    onClick: () -> Unit,
) {
    val workingSets = session.sets.count { !it.isWarmup }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.routineName ?: "Workout", style = MaterialTheme.typography.titleMedium)
            Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$workingSets working sets · ${session.workingVolumeKg().toWeightLabel(unit)} volume · ${session.durationMinutes} min",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TrainingBalanceCard(
    snapshot: BodyHeatSnapshot?,
    recommendations: List<TrainingRecommendation>,
    onOpenProgress: () -> Unit,
    onRecommendation: (TrainingRecommendation) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val highlights = listOf(
        CanonicalMuscle.CHEST,
        CanonicalMuscle.BACK,
        CanonicalMuscle.SHOULDERS,
        CanonicalMuscle.QUADRICEPS,
        CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.CORE,
    )
    Card(onClick = onOpenProgress, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Training balance", style = MaterialTheme.typography.titleLarge)
            }
            if (snapshot == null || !snapshot.hasAnyWorkingSets) {
                Text(
                    "The body map fills in as you finish workouts. Tap to see the full view.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Last 7 days · tap for the full map",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    highlights.forEach { muscle ->
                        val load = snapshot.load(muscle)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(heatFill(load.heat, dark)),
                            )
                            Text(
                                muscle.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            recommendations.forEach { rec ->
                RecommendationCard(
                    recommendation = rec,
                    compact = true,
                    onClick = { onRecommendation(rec) },
                )
            }
            TextButton(onClick = onOpenProgress) { Text("Open body map") }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

private fun dayGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Train when you’re ready"
    }
}
