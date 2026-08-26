package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        when {
            state.isLoading -> ScreenLoading()
            state.missing || state.session == null -> EmptyState(
                title = "Session gone",
                body = "That activity is no longer on this phone.",
                actionLabel = "Back",
                onAction = onBack,
                actionTag = ActivityDetailTags.DONE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Metrics.gutter),
            )
            else -> {
                val session = state.session!!
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Metrics.gutter),
                    contentPadding = PaddingValues(
                        top = Metrics.space4,
                        bottom = Metrics.space6,
                    ),
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
                }
                HairlineDivider(startIndent = 0.dp)
                ActivityDoneBar(onDone = onBack)
            }
        }
    }
}

/**
 * Pinned Done, same job as strength [com.sinura.personaltrainer.ui.summary.SummaryActions].
 * This route hides the tab bar, so the dock owns the system-nav inset.
 */
@Composable
internal fun ActivityDoneBar(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .navigationBarsPadding()
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        PrimaryGymButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier.testTag(ActivityDetailTags.DONE),
        )
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
        title = CardioCopy.name(block.type),
        subtitle = "$minutes min$distance",
    )
}

object ActivityDetailTags {
    const val DONE = "activity-detail-done"
}
