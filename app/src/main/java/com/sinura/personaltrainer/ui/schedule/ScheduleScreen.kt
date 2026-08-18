package com.sinura.personaltrainer.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
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
            state.plan == null -> {
                EmptyState(
                    title = "Could not build a week",
                    body = "Try again, or log a workout first. Suggestions get sharper with history.",
                    actionLabel = "Regenerate",
                    onAction = viewModel::regenerate,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                val plan = state.plan!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(plan.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        item { Text(err, color = MaterialTheme.colorScheme.error) }
                    }
                    items(plan.days, key = { it.epochDay }) { day ->
                        ScheduleDayCard(
                            day = day,
                            isToday = day.epochDay == today,
                            logged = day.epochDay in state.loggedEpochDays,
                            inProgress = state.inProgress != null,
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
        Text("Training days", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((SchedulePreferences.MIN_DAYS..SchedulePreferences.MAX_DAYS).toList()) { days ->
                FilterChip(
                    selected = preferences.trainingDaysPerWeek == days,
                    onClick = { onDays(days) },
                    label = { Text("$days") },
                )
            }
        }
        Text("Split", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SplitStyle.entries) { style ->
                FilterChip(
                    selected = preferences.splitStyle == style,
                    onClick = { onSplit(style) },
                    label = { Text(style.displayName) },
                )
            }
        }
        Text(preferences.splitStyle.blurb, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Week starts", style = MaterialTheme.typography.titleMedium)
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
    inProgress: Boolean,
    onStart: () -> Unit,
    onOpenRoutine: (() -> Unit)?,
    compact: Boolean = false,
) {
    val colors = when {
        day.isRest -> CardDefaults.cardColors()
        isToday -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else -> CardDefaults.cardColors()
    }
    val dateLabel = DateTimeFormatter.ofPattern("MMM d")
        .format(LocalDate.ofEpochDay(day.epochDay))
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                buildString {
                    append(day.dayOfWeek.shortLabel())
                    append(" · ")
                    append(dateLabel)
                    if (isToday) append(" · Today")
                    if (logged) append(" · Logged")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(day.focusTitle, style = MaterialTheme.typography.titleLarge)
            if (day.routineName != null) {
                Text(day.routineName, style = MaterialTheme.typography.titleMedium)
            }
            if (!compact) {
                Text(day.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!day.isRest) {
                val action = when {
                    inProgress -> "Resume workout"
                    logged -> "Train again"
                    day.routineId != null -> "Start ${day.routineName}"
                    else -> "Start this focus"
                }
                PrimaryGymButton(text = action, onClick = onStart)
                if (onOpenRoutine != null && !compact) {
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
    summary: String,
    loggedToday: Boolean,
    inProgress: Boolean,
    onOpenSchedule: () -> Unit,
    onStart: () -> Unit,
) {
    Card(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (day == null) {
                Text("Open the week plan to set days and split.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (day.isRest) {
                Text("Rest day", style = MaterialTheme.typography.titleMedium)
                nextDay?.let {
                    Text(
                        "Next: ${it.dayOfWeek.shortLabel()} · ${it.focusTitle}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(day.focusTitle, style = MaterialTheme.typography.titleMedium)
                day.routineName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(day.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryGymButton(
                    text = when {
                        inProgress -> "Resume workout"
                        loggedToday -> "Train again"
                        else -> "Start today’s session"
                    },
                    onClick = onStart,
                )
            }
            TextButton(onClick = onOpenSchedule) { Text("Open week plan") }
        }
    }
}

fun todayEpochDay(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
