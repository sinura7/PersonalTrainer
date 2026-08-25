package com.sinura.personaltrainer.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.GoalCopy
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.GoalSnapshot
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        GoalsHeader(
            onBack = onBack,
            onToggleAdd = { adding = !adding },
            adding = adding,
        )
        when {
            state.isLoading -> ScreenLoading()
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space2,
                        bottom = Metrics.space7,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { message ->
                        item(key = "error") {
                            GymErrorBanner(message)
                        }
                    }
                    if (adding) {
                        item(key = "add") {
                            AddGoalCard(
                                exercises = state.exercises,
                                unit = state.unit,
                                onAdd = { kind, target, period, exerciseId, exerciseName ->
                                    viewModel.add(
                                        kind = kind,
                                        targetValue = target,
                                        period = period,
                                        exerciseId = exerciseId,
                                        exerciseName = exerciseName,
                                    )
                                    adding = false
                                },
                            )
                        }
                    }
                    if (state.snapshots.isEmpty() && !adding) {
                        item(key = "empty") {
                            EmptyState(
                                title = "No goals yet",
                                body = "Set a session count, lift target, or cardio mark. Nothing here is a streak.",
                                actionLabel = "Add a goal",
                                onAction = { adding = true },
                            )
                        }
                    } else {
                        items(state.snapshots, key = { it.goal.id }) { snapshot ->
                            GoalCard(
                                snapshot = snapshot,
                                unit = state.unit,
                                onPause = {
                                    viewModel.setPaused(
                                        id = snapshot.goal.id,
                                        paused = !snapshot.goal.paused,
                                    )
                                },
                                onDelete = { viewModel.delete(snapshot.goal.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsHeader(
    onBack: () -> Unit,
    onToggleAdd: () -> Unit,
    adding: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.space2, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag(GoalsTags.BACK),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
            )
        }
        Text(
            "Goals",
            modifier = Modifier.weight(1f),
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 1,
        )
        TextButton(
            onClick = onToggleAdd,
            modifier = Modifier
                .testTag(GoalsTags.ADD)
                .semantics {
                    contentDescription = if (adding) "Cancel add goal" else "Add a goal"
                },
        ) {
            Text(
                if (adding) "Cancel" else "Add",
                style = InstrumentType.bodyStrong,
                color = if (adding) Volt else TextSecondary,
            )
        }
    }
}

@Composable
private fun GoalCard(
    snapshot: GoalSnapshot,
    unit: WeightUnit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val goal = snapshot.goal
    GymCard {
        Kicker(goal.period.label)
        Text(
            goal.exerciseName?.takeIf { it.isNotBlank() } ?: goal.kind.label,
            style = InstrumentType.title,
            color = TextPrimary,
        )
        Text(
            GoalCopy.progressLine(snapshot, unit),
            style = InstrumentType.body,
            color = if (snapshot.met) Volt else TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onPause) {
                Text(
                    if (goal.paused) "Resume" else "Pause",
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
            TextButton(onClick = onDelete) {
                Text("Delete", style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoalCard(
    exercises: List<com.sinura.personaltrainer.domain.Exercise>,
    unit: WeightUnit,
    onAdd: (GoalKind, Double, GoalPeriod, String?, String?) -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf(GoalKind.SESSION_COUNT.name) }
    var periodName by rememberSaveable { mutableStateOf(GoalPeriod.WEEK.name) }
    var targetText by rememberSaveable { mutableStateOf("") }
    var exerciseQuery by rememberSaveable { mutableStateOf("") }
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseName by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = GoalKind.entries.first { it.name == kindName }
    val period = GoalPeriod.entries.first { it.name == periodName }
    GymCard {
        GymSectionHeader("New goal")
        Text(
            "A target for a week, month, year, or all time. Rest days are not a miss.",
            style = InstrumentType.caption,
            color = TextSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            GoalKind.entries.forEach { entry ->
                InstrumentChip(
                    label = entry.label,
                    selected = kind == entry,
                    onClick = {
                        kindName = entry.name
                        if (entry != GoalKind.LIFT_TARGET) {
                            exerciseId = null
                            exerciseName = null
                        }
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            GoalPeriod.entries.forEach { entry ->
                InstrumentChip(
                    label = entry.label,
                    selected = period == entry,
                    onClick = { periodName = entry.name },
                )
            }
        }
        OutlinedTextField(
            value = targetText,
            onValueChange = { targetText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(targetHint(kind, unit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        if (kind == GoalKind.LIFT_TARGET) {
            OutlinedTextField(
                value = exerciseQuery,
                onValueChange = { exerciseQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Lift") },
                singleLine = true,
            )
            val matches = exercises
                .filter { exercise ->
                    exerciseQuery.isBlank() ||
                        exercise.name.contains(exerciseQuery, ignoreCase = true)
                }
                .take(8)
            matches.forEach { exercise ->
                InstrumentChip(
                    label = exercise.name,
                    selected = exerciseId == exercise.id,
                    onClick = {
                        exerciseId = exercise.id
                        exerciseName = exercise.name
                        exerciseQuery = exercise.name
                    },
                )
            }
        }
        val target = parseTarget(kind, targetText, unit)
        TextButton(
            onClick = {
                if (target != null) {
                    onAdd(kind, target, period, exerciseId, exerciseName)
                }
            },
            enabled = target != null && (kind != GoalKind.LIFT_TARGET || exerciseId != null),
        ) {
            Text("Save goal", style = InstrumentType.bodyStrong, color = Volt)
        }
    }
}

private fun targetHint(kind: GoalKind, unit: WeightUnit): String = when (kind) {
    GoalKind.ADHERENCE -> "Adherence (1 = every training day)"
    GoalKind.SESSION_COUNT -> "Sessions"
    GoalKind.ACTIVE_MINUTES -> "Minutes"
    GoalKind.LIFT_TARGET -> "Target ${unit.suffix}"
    GoalKind.CARDIO_DURATION -> "Minutes"
    GoalKind.CARDIO_DISTANCE -> "Metres"
    GoalKind.BODYWEIGHT -> "Target ${unit.suffix}"
}

private fun parseTarget(kind: GoalKind, raw: String, unit: WeightUnit): Double? {
    val parsed = raw.trim().toDoubleOrNull() ?: return null
    if (!parsed.isFinite() || parsed <= 0.0) return null
    return when (kind) {
        GoalKind.LIFT_TARGET, GoalKind.BODYWEIGHT ->
            com.sinura.personaltrainer.domain.WeightConverter.toKg(parsed, unit)
        else -> parsed
    }
}

object GoalsTags {
    const val BACK = "goals-back"
    const val ADD = "goals-add"
}
