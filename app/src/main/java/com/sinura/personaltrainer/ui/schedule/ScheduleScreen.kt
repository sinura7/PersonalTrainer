package com.sinura.personaltrainer.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymMetrics
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: ScheduleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("This week") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::regenerate) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Regenerate week")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }
            state.plan == null -> {
                EmptyState(
                    title = "No week plan yet",
                    body = "Generate a week, or log a workout so suggestions can follow your training.",
                    actionLabel = "Generate week",
                    onAction = viewModel::regenerate,
                    modifier = Modifier
                        .padding(padding)
                        .padding(GymMetrics.screenPadding),
                )
            }
            else -> {
                val plan = state.plan!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(GymMetrics.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap),
                ) {
                    item {
                        Text(
                            if (plan.thinHistory) {
                                "Starter week from your split. It follows neglected muscles after a few logged sessions."
                            } else {
                                plan.summary
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.inProgress?.let { session ->
                        item {
                            PrimaryGymButton(
                                text = "Resume ${session.routineName ?: "workout"}",
                                onClick = { onWorkoutStarted(session.id) },
                            )
                        }
                    }
                    item {
                        PreferenceBlock(
                            preferences = state.preferences,
                            onDays = viewModel::setTrainingDays,
                            onSplit = viewModel::setSplit,
                            onWeekStart = viewModel::setWeekStart,
                        )
                    }
                    state.error?.let { err ->
                        item { GymErrorBanner(err) }
                    }
                    items(plan.days, key = { it.epochDay }) { day ->
                        ScheduleDayCard(
                            day = day,
                            isToday = day.epochDay == today,
                            logged = day.epochDay in state.loggedEpochDays,
                            showStart = state.inProgress == null && !day.isRest,
                            onStart = { viewModel.startDay(day, onWorkoutStarted) },
                            onOpenRoutine = day.routineId?.let { id -> { onOpenRoutine(id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceBlock(
    preferences: SchedulePreferences,
    onDays: (Int) -> Unit,
    onSplit: (SplitStyle) -> Unit,
    onWeekStart: (DayOfWeek) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GymSectionHeader("Training days", compact = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((SchedulePreferences.MIN_DAYS..SchedulePreferences.MAX_DAYS).toList()) { days ->
                FilterChip(
                    selected = preferences.trainingDaysPerWeek == days,
                    onClick = { onDays(days) },
                    label = { Text("$days") },
                )
            }
        }
        GymSectionHeader("Split", compact = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SplitStyle.entries) { style ->
                FilterChip(
                    selected = preferences.splitStyle == style,
                    onClick = { onSplit(style) },
                    label = { Text(style.displayName) },
                )
            }
        }
        Text(
            preferences.splitStyle.blurb,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        GymSectionHeader("Week starts", compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                FilterChip(
                    selected = preferences.weekStart == day,
                    onClick = { onWeekStart(day) },
                    label = { Text(if (day == DayOfWeek.MONDAY) "Monday" else "Sunday") },
                )
            }
        }
    }
}

@Composable
fun ScheduleDayCard(
    day: SuggestedTrainingDay,
    isToday: Boolean,
    logged: Boolean,
    showStart: Boolean,
    onStart: () -> Unit,
    onOpenRoutine: (() -> Unit)?,
) {
    val colors = when {
        day.isRest -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        )
        isToday -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else -> CardDefaults.cardColors()
    }
    val dateLabel = DateTimeFormatter.ofPattern("MMM d")
        .format(LocalDate.ofEpochDay(day.epochDay))
    val status = buildString {
        append(day.dayOfWeek.shortLabel())
        append(" · ")
        append(dateLabel)
        if (isToday) append(" · Today")
        if (logged) append(" · Logged")
    }
    GymCard(colors = colors) {
        Text(
            status,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day.isRest) {
            Text("Rest day", style = MaterialTheme.typography.titleMedium)
        } else {
            Text(day.focusTitle, style = MaterialTheme.typography.titleMedium)
            day.routineName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                day.reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showStart) {
                val action = when {
                    logged -> "Train again"
                    day.routineName != null -> "Start ${day.routineName}"
                    else -> "Start this session"
                }
                PrimaryGymButton(text = action, onClick = onStart, height = 52.dp)
                if (onOpenRoutine != null) {
                    TextButton(onClick = onOpenRoutine) { Text("Open routine") }
                }
            }
        }
    }
}

@Composable
fun ThisWeekHomeCard(
    day: SuggestedTrainingDay?,
    nextDay: SuggestedTrainingDay?,
    thinHistory: Boolean,
    loggedToday: Boolean,
    inProgress: Boolean,
    onOpenSchedule: () -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GymMetrics.listGap)) {
        GymSectionHeader(
            title = "This week",
            actionLabel = "Plan",
            onAction = onOpenSchedule,
        )
        GymCard(onClick = onOpenSchedule) {
            if (day == null) {
                Text(
                    "Open the week plan to set days and split.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (day.isRest) {
                Text("Rest day", style = MaterialTheme.typography.titleMedium)
                nextDay?.let {
                    Text(
                        "Next · ${it.dayOfWeek.shortLabel()} · ${it.focusTitle}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                val headline = buildString {
                    append(day.focusTitle)
                    if (loggedToday) append(" · logged")
                }
                Text(headline, style = MaterialTheme.typography.titleMedium)
                day.routineName?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (thinHistory) {
                Text(
                    "Starter week — tightens after a few sessions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (!inProgress && day != null && !day.isRest) {
            SecondaryGymButton(
                text = if (loggedToday) "Train again" else "Start this session",
                onClick = onStart,
            )
        }
    }
}

fun todayEpochDay(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
