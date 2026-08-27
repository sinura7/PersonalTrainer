package com.sinura.personaltrainer.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    celebration: Boolean = false,
    viewModel: ActivityDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        when {
            state.isLoading -> ScreenLoading()
            state.missing || state.session == null -> EmptyState(
                title = ActivityDetailCopy.MISSING_TITLE,
                body = ActivityDetailCopy.MISSING_BODY,
                actionLabel = ActivityDetailCopy.missingAction(celebration),
                onAction = onBack,
                actionTag = ActivityDetailTags.DONE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Metrics.gutter),
            )
            else -> {
                val session = state.session!!
                if (!celebration) {
                    ActivityDetailHeader(title = session.title, onBack = onBack)
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Metrics.gutter),
                    contentPadding = PaddingValues(
                        top = if (celebration) Metrics.space4 else Metrics.space2,
                        bottom = Metrics.space6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    item {
                        ActivityReceiptHero(
                            session = session,
                            celebration = celebration,
                            volumeKg = state.volumeKg,
                            durationMinutes = state.durationMinutes,
                            unit = unit,
                        )
                    }
                    item {
                        ActivityMetricTiles(
                            strengthSetCount = state.strengthSetCount,
                            volumeKg = state.volumeKg,
                            cardioMinutes = state.cardioMinutes,
                            durationMinutes = state.durationMinutes,
                            unit = unit,
                        )
                    }
                    if (session.strengthBlocks.isNotEmpty()) {
                        item { GymSectionHeader(title = ActivityDetailCopy.STRENGTH) }
                        items(session.strengthBlocks, key = { it.id }) { block ->
                            StrengthRows(block, unit)
                        }
                    }
                    if (session.cardioBlocks.isNotEmpty()) {
                        item { GymSectionHeader(title = ActivityDetailCopy.CARDIO) }
                        items(session.cardioBlocks, key = { it.id }) { block ->
                            CardioRows(block)
                        }
                    }
                }
                if (celebration) {
                    HairlineDivider(startIndent = 0.dp)
                    ActivityDoneBar(onDone = onBack)
                }
            }
        }
    }
}

@Composable
private fun ActivityDetailHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Metrics.space2, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag(ActivityDetailTags.BACK),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = ActivityDetailCopy.BACK,
                tint = TextSecondary,
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = InstrumentType.title,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityReceiptHero(
    session: ActivitySession,
    celebration: Boolean,
    volumeKg: Double,
    durationMinutes: Int,
    unit: WeightUnit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
        Kicker(ActivityDetailCopy.kicker(celebration, session), color = TextSecondary)
        if (celebration) {
            Text(
                session.title,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                ActivityDetailCopy.modalityCaption(session),
                style = InstrumentType.body,
                color = TextSecondary,
            )
        }
        if (celebration) {
            val cardioLead = session.isCardioOnly || (volumeKg <= 0.0 && durationMinutes > 0)
            if (cardioLead) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        durationMinutes.toString(),
                        modifier = Modifier.alignByBaseline(),
                        style = InstrumentType.numeralXl,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        "min",
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = Metrics.space2),
                        style = InstrumentType.unit,
                        color = TextSecondary,
                    )
                }
            } else if (volumeKg > 0.0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        ActivityDetailCopy.volumeLabel(volumeKg, unit),
                        modifier = Modifier.alignByBaseline(),
                        style = InstrumentType.numeralXl,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        unit.suffix,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = Metrics.space2),
                        style = InstrumentType.unit,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityMetricTiles(
    strengthSetCount: Int,
    volumeKg: Double,
    cardioMinutes: Int,
    durationMinutes: Int,
    unit: WeightUnit,
) {
    val showSets = strengthSetCount > 0
    val showVolume = volumeKg > 0.0
    val showMinutes = cardioMinutes > 0 || (!showSets && durationMinutes > 0)
    if (!showSets && !showVolume && !showMinutes) return
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
        if (showSets) {
            StatTile(
                label = "sets",
                value = strengthSetCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        if (showVolume) {
            StatTile(
                label = "volume",
                value = ActivityDetailCopy.volumeLabel(volumeKg, unit),
                unit = unit.suffix,
                modifier = Modifier.weight(1f),
            )
        }
        if (showMinutes) {
            StatTile(
                label = "duration",
                value = (if (cardioMinutes > 0) cardioMinutes else durationMinutes).toString(),
                unit = "min",
                modifier = Modifier.weight(1f),
            )
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
            text = ActivityDetailCopy.DONE,
            onClick = onDone,
            modifier = Modifier.testTag(ActivityDetailTags.DONE),
        )
    }
}

@Composable
private fun StrengthRows(block: StrengthBlock, unit: WeightUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Kicker(block.exerciseName)
        GroupedList {
            block.sets.forEachIndexed { index, set ->
                if (index > 0) HairlineDivider()
                InstrumentRow(
                    title = "Set ${set.setNumber}",
                    subtitle = ActivityDetailCopy.setLine(block, set, unit),
                )
            }
        }
    }
}

@Composable
private fun CardioRows(block: CardioBlock) {
    GroupedList {
        InstrumentRow(
            title = CardioCopy.name(block.type),
            subtitle = ActivityDetailCopy.cardioSubtitle(block),
        )
    }
}

object ActivityDetailTags {
    const val DONE = "activity-detail-done"
    const val BACK = "activity-detail-back"
}
