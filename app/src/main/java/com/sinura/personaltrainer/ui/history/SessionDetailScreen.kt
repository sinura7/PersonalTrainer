package com.sinura.personaltrainer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: SessionDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    val unit = LocalWeightUnit.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = Metrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary,
                )
            }
            Text(
                session?.routineName ?: "Session",
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            state.isLoading -> {
                ScreenLoading()
            }
            session == null -> {
                EmptyState(
                    title = "Session not found",
                    body = "This workout is no longer on this phone.",
                    modifier = Modifier.padding(Metrics.gutter),
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Metrics.gutter,
                        end = Metrics.gutter,
                        top = Metrics.space2,
                        bottom = Metrics.space7,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
                ) {
                    item {
                        SessionReceipt(
                            dateLabel = dateFormat.format(Date(session.date)),
                            volumeKg = session.workingVolumeKg(),
                            workingSets = workingSets,
                            durationMinutes = session.durationMinutes,
                            notes = session.notes,
                            unit = unit,
                        )
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
                        // Same per-set rule as the session headline above and the body map; a
                        // plain weight x reps here scored bodyweight sets at zero.
                        val volume = sets
                            .filterNot { it.isWarmup }
                            .sumOf { MuscleLoadCalculator.setVolumeKg(it.weightKg, it.reps) }
                        ExerciseBlock(
                            name = exerciseName,
                            sets = sets,
                            volumeKg = volume,
                            unit = unit,
                            onOpen = { onOpenExercise(exerciseId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The session as a readout, in the same shape as the summary shown the moment it ended — so
 * "just finished" and "last March" are the same instrument.
 *
 * The three numbers used to be one sentence in body text, where the word "working" carried
 * the same weight as the tonnage beside it and nothing lined up between two sessions.
 */
@Composable
private fun SessionReceipt(
    dateLabel: String,
    volumeKg: Double,
    workingSets: Int,
    durationMinutes: Int,
    notes: String,
    unit: WeightUnit,
) {
    GymCard {
        Text(dateLabel, style = InstrumentType.caption, color = TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                WeightConverter.formatGroupedNumber(WeightConverter.toDisplayValue(volumeKg, unit)),
                modifier = Modifier.alignByBaseline(),
                style = InstrumentType.numeralXl,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                unit.suffix,
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = Metrics.space1),
                style = InstrumentType.unit,
                color = TextSecondary,
            )
        }
        Kicker("Working volume")
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space6),
        ) {
            MetricCluster(
                value = workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = durationMinutes.toString(),
                label = "min",
                horizontalAlignment = Alignment.Start,
            )
        }
        if (notes.isNotBlank()) {
            HairlineDivider(startIndent = 0.dp)
            Text(notes, style = InstrumentType.body, color = TextSecondary)
        }
    }
}

/**
 * One lift, and every set of it.
 *
 * The lift's name and its tonnage head the block; the sets sit in a grouped panel beneath,
 * which is what puts the weights in a column instead of at whatever indent the previous
 * row's sentence happened to end on.
 */
@Composable
private fun ExerciseBlock(
    name: String,
    sets: List<SetLog>,
    volumeKg: Double,
    unit: WeightUnit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Metrics.touchMin)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onOpen)
                .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetricCluster(
                value = WeightConverter.formatGroupedNumber(
                    WeightConverter.toDisplayValue(volumeKg, unit),
                ),
                label = unit.suffix,
            )
        }
        if (sets.isEmpty()) {
            Text(
                "No sets",
                modifier = Modifier.padding(horizontal = Metrics.space2),
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        } else {
            GroupedList {
                sets.forEachIndexed { index, set ->
                    if (index > 0) HairlineDivider()
                    SetRow(set = set, unit = unit)
                }
            }
        }
    }
}

@Composable
private fun SetRow(set: SetLog, unit: WeightUnit) {
    val tags = buildList {
        if (set.isWarmup) add("Warm-up")
        set.rpe?.let { add("RPE $it") }
    }
    InstrumentRow(
        title = "Set ${set.setNumber}",
        subtitle = tags.joinToString(" · ").ifEmpty { null },
    ) {
        // Fixed columns, not wrapped content: a 97.5 and a 100 have to land on the same
        // right edge or there is nothing to compare down the list.
        MetricCluster(
            value = WeightConverter.formatDisplayNumber(
                WeightConverter.toDisplayValue(set.weightKg, unit),
            ),
            label = unit.suffix,
            modifier = Modifier.width(WEIGHT_COLUMN),
        )
        MetricCluster(
            value = set.reps.toString(),
            label = "reps",
            modifier = Modifier.width(REPS_COLUMN),
        )
    }
}

private val WEIGHT_COLUMN = 88.dp
private val REPS_COLUMN = 48.dp
