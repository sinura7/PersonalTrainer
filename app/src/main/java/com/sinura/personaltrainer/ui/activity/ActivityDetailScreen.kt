package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold { padding ->
        when {
            state.isLoading -> ScreenLoading(modifier = Modifier.padding(padding))
            state.missing || state.session == null -> EmptyState(
                title = "Session gone",
                body = "That activity is no longer on this phone.",
                actionLabel = "Back",
                onAction = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Metrics.gutter),
            )
            else -> {
                val session = state.session!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = Metrics.gutter),
                    contentPadding = PaddingValues(bottom = Metrics.space8),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    item {
                        Text(session.title, style = InstrumentType.title, color = TextPrimary)
                        Text(
                            when {
                                session.isMixed -> "Strength and cardio, kept separate."
                                session.isCardioOnly -> "Cardio. No invented lift rows."
                                else -> "Strength."
                            },
                            style = InstrumentType.body,
                            color = TextSecondary,
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                            if (state.strengthSetCount > 0) {
                                MetricCluster(value = state.strengthSetCount.toString(), label = "sets")
                                MetricCluster(value = state.volumeKg.toInt().toString(), label = "kg")
                            }
                            if (state.cardioMinutes > 0) {
                                MetricCluster(value = state.cardioMinutes.toString(), label = "min")
                            }
                        }
                    }
                    if (session.strengthBlocks.isNotEmpty()) {
                        item { Kicker("Strength") }
                        items(session.strengthBlocks, key = { it.id }) { block ->
                            StrengthRows(block)
                        }
                    }
                    if (session.cardioBlocks.isNotEmpty()) {
                        item { Kicker("Cardio") }
                        items(session.cardioBlocks, key = { it.id }) { block ->
                            CardioRows(block)
                        }
                    }
                    item { TextButton(onClick = onBack) { Text("Done") } }
                }
            }
        }
    }
}

@Composable
private fun StrengthRows(block: StrengthBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(block.exerciseName, style = InstrumentType.bodyStrong, color = TextPrimary)
        block.sets.forEach { set ->
            InstrumentRow(
                title = "Set ${set.setNumber}",
                subtitle = "${set.reps} reps · ${set.weightKg} kg",
            )
        }
    }
}

@Composable
private fun CardioRows(block: CardioBlock) {
    val minutes = ((block.elapsedSeconds + 30) / 60).toInt()
    val distance = block.distanceMeters?.let { " · ${it / 1000.0} km" }.orEmpty()
    InstrumentRow(
        title = block.type.name.lowercase().replaceFirstChar { it.uppercase() },
        subtitle = "$minutes min$distance",
    )
}
