package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.ProgressionCalculator
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.TrainingRecommendation
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymNumericStyle
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SessionLogRow
import com.sinura.personaltrainer.ui.progress.dispatchRecommendation
import com.sinura.personaltrainer.ui.progress.heatFill
import com.sinura.personaltrainer.ui.schedule.ThisWeekHomeCard
import com.sinura.personaltrainer.ui.schedule.todayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenLibraryMuscle: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemainingSeconds.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val inProgress = state.inProgress

    if (state.isLoading) {
        ScreenLoading()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(GymMetrics.screenPadding),
        verticalArrangement = Arrangement.spacedBy(GymMetrics.sectionGap),
    ) {
        item {
            HomeHeader(
                unit = unit,
                onOpenSettings = onOpenSettings,
            )
        }
        state.error?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        if (restRemaining > 0 && inProgress != null) {
            item {
                RestRemainingStrip(
                    remainingSeconds = restRemaining,
                    onResume = { onResumeWorkout(inProgress.id) },
                )
            }
        }
        item {
            PrimaryGymButton(
                text = if (inProgress != null) "Resume workout" else "Start workout",
                onClick = {
                    if (inProgress != null) onResumeWorkout(inProgress.id) else onStartWorkout()
                },
            )
        }
        item {
            val plan = state.weekPlan
            val today = todayEpochDay()
            val todayDay = plan?.dayOn(today)
            ThisWeekHomeCard(
                day = todayDay,
                nextDay = plan?.nextTrainingOnOrAfter(today),
                thinHistory = plan?.thinHistory == true,
                loggedToday = state.recentSessions.any { session ->
                    todayEpochDay(session.date) == today
                },
                inProgress = inProgress != null,
                onOpenSchedule = onOpenSchedule,
                onStart = {
                    val target = todayDay?.takeUnless { it.isRest } ?: plan?.nextTrainingOnOrAfter(today)
                    if (target != null && !target.isRest) {
                        viewModel.startSuggestedDay(target, onResumeWorkout)
                    } else {
                        onOpenSchedule()
                    }
                },
            )
        }
        item {
            TrainingBalanceCard(
                hasWork = state.heatSnapshot?.hasAnyWorkingSets == true,
                recommendations = state.recommendations.take(1),
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
                snapshotHighlights = state.heatSnapshot,
            )
        }
        if (state.readyToProgress.isNotEmpty()) {
            item {
                GymSectionHeader("Ready to progress")
            }
            items(state.readyToProgress, key = { it.exerciseId }) { hint ->
                ProgressCard(
                    hint = hint,
                    unit = unit,
                    onClick = onStartWorkout,
                )
            }
        }
        item {
            GymSectionHeader(
                title = "Recent",
                actionLabel = if (state.recentSessions.isNotEmpty()) "History" else null,
                onAction = if (state.recentSessions.isNotEmpty()) onOpenHistory else null,
            )
        }
        if (state.recentSessions.isEmpty()) {
            item {
                EmptyState(
                    title = "No sessions yet",
                    body = "Finish a workout and it lands here.",
                    compact = true,
                )
            }
        } else {
            items(state.recentSessions, key = { it.id }) { session ->
                SessionLogRow(
                    title = session.routineName ?: "Workout",
                    dateLabel = dateFormat.format(Date(session.date)),
                    workingSets = session.sets.count { !it.isWarmup },
                    volumeLabel = session.workingVolumeKg().toWeightLabel(unit),
                    durationMinutes = session.durationMinutes,
                    onClick = { onOpenSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun RestRemainingStrip(
    remainingSeconds: Int,
    onResume: () -> Unit,
) {
    GymCard(
        onClick = onResume,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "REST",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                RestTimer.formatClock(remainingSeconds),
                style = GymNumericStyle.copy(fontSize = 28.sp, lineHeight = 32.sp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
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
            Text(
                dayGreeting(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Personal Trainer", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Weights in ${unit.suffix}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onOpenSettings),
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressCard(
    hint: ProgressionHint,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    GymCard(onClick = onClick) {
        Text(hint.exerciseName, style = MaterialTheme.typography.titleMedium)
        Text(
            hint.suggestedWeightKg.toWeightLabel(unit),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Last ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}  ·  +${ProgressionCalculator.INCREMENT_KG.toWeightLabel(unit)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrainingBalanceCard(
    hasWork: Boolean,
    recommendations: List<TrainingRecommendation>,
    onOpenProgress: () -> Unit,
    onRecommendation: (TrainingRecommendation) -> Unit,
    snapshotHighlights: BodyHeatSnapshot?,
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
    Column(verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap)) {
        GymSectionHeader(
            title = "Training",
            actionLabel = "Body",
            onAction = onOpenProgress,
        )
        GymCard(onClick = onOpenProgress) {
            if (!hasWork) {
                Text(
                    "Finish a few sessions to see which muscles are loaded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Last 7 days",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    highlights.forEach { muscle ->
                        val load = snapshotHighlights?.load(muscle)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(heatFill(load?.heat ?: 0.0, dark)),
                            )
                            Text(
                                muscle.shortLabel,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        recommendations.firstOrNull()?.let { rec ->
            GymCard(onClick = { onRecommendation(rec) }) {
                Text(
                    rec.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
