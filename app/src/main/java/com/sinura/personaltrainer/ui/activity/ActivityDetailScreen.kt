package com.sinura.personaltrainer.ui.activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.ActivityEditCopy
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentMenu
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.PinnedDock
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val leave = {
        viewModel.persistNotesForExit()
        onBack()
    }
    BackHandler(onBack = leave)

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Pit),
        ) {
            when {
                state.isLoading -> ScreenLoading()
                // Before the missing branch on purpose: a failed read also has no session, and
                // it must not be shown as a deleted one.
                state.failed -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    EmptyState(
                        title = DataHealthCopy.ACTIVITY_TITLE,
                        body = DataHealthCopy.ACTIVITY_BODY,
                        actionLabel = DataHealthCopy.RETRY,
                        onAction = viewModel::retry,
                        actionTag = ActivityDetailTags.RETRY,
                    )
                    TextButton(
                        onClick = leave,
                        modifier = Modifier.testTag(ActivityDetailTags.DONE),
                    ) {
                        Text(ActivityDetailCopy.missingAction(celebration))
                    }
                }
                state.missing || state.session == null -> EmptyState(
                    title = ActivityDetailCopy.MISSING_TITLE,
                    body = ActivityDetailCopy.MISSING_BODY,
                    actionLabel = ActivityDetailCopy.missingAction(celebration),
                    onAction = leave,
                    actionTag = ActivityDetailTags.DONE,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Metrics.gutter),
                )
                else -> {
                    val session = state.session!!
                    if (!celebration) {
                        ActivityDetailHeader(
                            title = session.title,
                            onBack = leave,
                            onDelete = { confirmDelete = true },
                            menuOpen = menuOpen,
                            onMenuOpenChange = { menuOpen = it },
                        )
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
                        item {
                            NotesBlock(
                                notes = state.notes,
                                expanded = notesOpen,
                                onToggle = { notesOpen = !notesOpen },
                                onChange = viewModel::setNotes,
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
                                CardioRows(block, unit)
                            }
                        }
                    }
                    if (celebration) {
                        HairlineDivider(startIndent = 0.dp)
                        ActivityDoneBar(onDone = leave)
                    }
                }
            }
        }
        error?.let { message ->
            GymErrorBanner(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Metrics.gutter),
                onDismiss = { viewModel.onErrorShown() },
            )
        }
    }

    if (confirmDelete && state.session != null) {
        val session = state.session!!
        ConfirmActionDialog(
            title = ActivityEditCopy.deleteTitle(session.title),
            body = ActivityEditCopy.deleteBody(
                setCount = state.strengthSetCount,
                hasCardio = session.cardioBlocks.isNotEmpty(),
            ),
            confirmLabel = ActivityEditCopy.DELETE_CONFIRM,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSession()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun ActivityDetailHeader(
    title: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
) {
    ScreenHeader(
        title = title,
        onBack = onBack,
        backTag = ActivityDetailTags.BACK,
        backDescription = ActivityDetailCopy.BACK,
        paintBackground = false,
        trailing = {
            Box {
                IconButton(
                    onClick = { onMenuOpenChange(true) },
                    modifier = Modifier.testTag(ActivityDetailTags.OPTIONS),
                ) {
                    Icon(
                        OutlinedMarks.MoreVert,
                        contentDescription = ActivityEditCopy.OPTIONS,
                        tint = TextSecondary,
                    )
                }
                InstrumentMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                ActivityEditCopy.DELETE,
                                style = InstrumentType.bodyStrong,
                                color = TextSecondary,
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onDelete()
                        },
                        modifier = Modifier.testTag(ActivityDetailTags.DELETE),
                    )
                }
            }
        },
    )
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
    val stack = LogLoopScale.stackTiles(LocalDensity.current.fontScale)
    if (stack) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
            if (showSets) {
                StatTile(
                    label = "sets",
                    value = strengthSetCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showVolume) {
                StatTile(
                    label = "volume",
                    value = ActivityDetailCopy.volumeLabel(volumeKg, unit),
                    unit = unit.suffix,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showMinutes) {
                StatTile(
                    label = "duration",
                    value = (if (cardioMinutes > 0) cardioMinutes else durationMinutes).toString(),
                    unit = "min",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
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
}

/**
 * Pinned Done, same job as strength [com.sinura.personaltrainer.ui.summary.SummaryActions].
 * This route hides the tab bar, so the dock owns the system-nav inset.
 */
@Composable
internal fun ActivityDoneBar(onDone: () -> Unit) {
    PinnedDock(
        volt = {
            PrimaryGymButton(
                text = ActivityDetailCopy.DONE,
                onClick = onDone,
                modifier = Modifier.testTag(ActivityDetailTags.DONE),
            )
        },
    )
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
private fun CardioRows(block: CardioBlock, unit: WeightUnit) {
    GroupedList {
        InstrumentRow(
            title = CardioCopy.name(block.type),
            subtitle = ActivityDetailCopy.cardioSubtitle(
                block,
                DistanceUnit.fromWeight(unit),
            ),
        )
    }
}

object ActivityDetailTags {
    const val DONE = "activity-detail-done"
    const val BACK = "activity-detail-back"
    const val RETRY = "activity-detail-retry"
    const val OPTIONS = "activity-detail-options"
    const val DELETE = "activity-detail-delete"
}
