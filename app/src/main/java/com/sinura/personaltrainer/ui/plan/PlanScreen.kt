package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.domain.WeeklySchedulePlanner
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ResumeOrDiscardDialog
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.domain.BlockReview
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Volt
import java.text.DateFormat
import java.util.Date

/**
 * Where you are in the block, and — at the end of it — what to do about that.
 *
 * A line and a rule, not a card. The block is context for the week below it, and a card would
 * put a box around the context and leave the actual plan looking like the second thing on the
 * screen. It says nothing about load: the app decides progression from what was logged, and a
 * calendar with its own opinion would be a second voice contradicting the first in the weeks
 * they disagreed.
 *
 * The completed state is the one worth having. Twelve weeks with no end is just a week
 * repeating; twelve weeks with an end is a thing you finished, and a moment to decide what the
 * next twelve are for. Nothing is deleted when the next one starts — a block is a horizon, not
 * a container.
 */
@Composable
private fun BlockLine(
    block: TrainingBlock,
    today: Long,
    /** Present only once the block is over — see PlanUiState.blockReview. */
    review: BlockReview?,
    onStartNext: () -> Unit,
) {
    val complete = block.isCompleteOn(today)
    val week = block.displayWeekOn(today)
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(
                if (complete) "Block complete" else "Week $week of ${block.weeks}",
                color = if (complete) Volt else TextSecondary,
            )
            if (complete) {
                TextButton(onClick = onStartNext, contentPadding = PaddingValues(0.dp)) {
                    Text("Start the next twelve", style = InstrumentType.bodyStrong, color = Volt)
                }
            }
        }
        // A rule rather than a progress bar: this is a position in a plan, not a download.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BLOCK_RULE_HEIGHT)
                .clip(RoundedCornerShape(Radius.xs))
                .background(Hairline),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(block.progressOn(today).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (complete) Volt else TextTertiary),
            )
        }
        if (complete) {
            if (review != null && !review.isEmpty) {
                BlockReviewPanel(review = review)
            }
            Text(
                "Your routines, your week and every session stay exactly as they are — " +
                    "starting the next block only moves the marker.",
                style = InstrumentType.caption,
                color = TextTertiary,
            )
        }
    }
}

/**
 * What the twelve weeks came to.
 *
 * Without this the completed state is a label and a button — the app noticing a date passed and
 * asking whether you would like another one — and someone who trained hard for three months
 * gets no more acknowledgement than someone who did nothing.
 *
 * The movers are the point, and they are deliberately what *moved* rather than what was
 * biggest: "most volume" names whatever lift happens to be a squat, while "moved most" names
 * the lift you actually got better at. Each reads in its own unit, so a pull-up going eight to
 * fifteen sits on the same list as a squat going 100 to 120.
 */
@Composable
private fun BlockReviewPanel(review: BlockReview) {
    // Read here rather than threaded down: this is the only thing on the screen that needs it.
    val unit = LocalWeightUnit.current
    GymCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space5),
        ) {
            MetricCluster(
                value = review.daysTrained.toString(),
                label = "days",
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.workingSets.toString(),
                label = "sets",
                horizontalAlignment = Alignment.Start,
            )
            val column = SetCopy.workColumn(review.work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.recordsBroken.toString(),
                label = "PRs",
                horizontalAlignment = Alignment.Start,
            )
        }
        SetCopy.bodyweightLine(review.bodyweight, unit)?.let { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Bodyweight", style = InstrumentType.body, color = TextSecondary)
                Text(line, style = InstrumentType.numeralSm, color = TextSecondary)
            }
        }
        if (review.movers.isNotEmpty()) {
            HairlineDivider(startIndent = 0.dp)
            Kicker("Moved most")
            review.movers.forEach { mover ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        mover.exerciseName,
                        modifier = Modifier.weight(1f),
                        style = InstrumentType.body,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${mover.fromLabel}  →  ${mover.toLabel}",
                        style = InstrumentType.numeralSm,
                        color = Volt,
                    )
                }
            }
        }
    }
}

/**
 * The week, and the routines it is made of, on one tab.
 *
 * These used to be two places that could not see each other: a Routines tab that listed
 * programs, and a Schedule screen pushed from Home — and from a row in Settings — that showed a
 * week regenerated from history every time you opened it. Editing what you train and deciding
 * when you train it were a navigation apart, and the week itself was not something you could
 * own.
 *
 * Here the week sits above the routines that fill it. Everything on the strip is either
 * something you pinned or something the app is visibly proposing; nothing on it appeared by
 * itself.
 */
@Composable
fun PlanScreen(
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogActivity: (String) -> Unit = {},
    onOpenLiveCardio: (String) -> Unit = {},
    viewModel: PlanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val navigateToCardio by viewModel.navigateToCardio.collectAsStateWithLifecycle()
    val navigateToComposer by viewModel.navigateToComposer.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedByInProgress.collectAsStateWithLifecycle()
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val today = remember { todayEpochDay() }

    var tuning by rememberSaveable { mutableStateOf(false) }
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        onWorkoutStarted(target)
        viewModel.onSessionNavigationHandled()
    }
    LaunchedEffect(navigateToCardio) {
        val target = navigateToCardio ?: return@LaunchedEffect
        onOpenLiveCardio(target)
        viewModel.onCardioNavigationHandled()
    }
    LaunchedEffect(navigateToComposer) {
        val mode = navigateToComposer ?: return@LaunchedEffect
        onLogActivity(mode)
        viewModel.onComposerNavigationHandled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        PlanHeader(
            tuning = tuning,
            canCreate = state.routines.isNotEmpty(),
            onToggleTune = { tuning = !tuning },
            onCreate = onCreateRoutine,
            onOpenLibrary = onOpenLibrary,
            onOpenSettings = onOpenSettings,
        )

        if (state.isLoading) {
            ScreenLoading()
            return@Column
        }

        val week = state.week
        // Proposals are drawn into the same cells as pins, so the strip always answers the
        // same question — "what is this day?" — and the answer is styled by where it came from.
        val proposalsByDay = state.proposals.associateBy { it.epochDay }

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
                item(key = "error") { GymErrorBanner(message) }
            }

            state.block?.let { block ->
                item(key = "block") {
                    BlockLine(
                        block = block,
                        today = today,
                        review = state.blockReview,
                        onStartNext = viewModel::startNextBlock,
                    )
                }
            }

            if (state.missedWorkPrompt) {
                item(key = "missed-work") {
                    MissedWorkCard(
                        overdueCount = state.overdueCount,
                        onMoveRemaining = {
                            viewModel.applyMissedWork(
                                com.sinura.personaltrainer.domain.MissedWorkChoice.MOVE_REMAINING,
                            )
                        },
                        onAdaptWeek = {
                            viewModel.applyMissedWork(
                                com.sinura.personaltrainer.domain.MissedWorkChoice.ADAPT_WEEK,
                            )
                        },
                        onKeepDates = {
                            viewModel.applyMissedWork(
                                com.sinura.personaltrainer.domain.MissedWorkChoice.KEEP_DATES,
                            )
                        },
                        onSkipMissed = {
                            viewModel.applyMissedWork(
                                com.sinura.personaltrainer.domain.MissedWorkChoice.SKIP_MISSED,
                            )
                        },
                    )
                }
            }

            if (week != null) {
                item(key = "strip") {
                    WeekStrip(
                        days = week.days,
                        proposals = proposalsByDay,
                        loggedEpochDays = state.loggedEpochDays,
                        today = today,
                        onOpenDay = { openDay = it },
                    )
                }
                item(key = "summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                        if (state.lighterWeek) {
                            Text(
                                LighterWeek.CAPTION,
                                style = InstrumentType.caption,
                                color = Volt,
                            )
                        }
                        Text(week.summary, style = InstrumentType.caption, color = TextTertiary)
                    }
                }
            }

            if (tuning) {
                item(key = "tune") {
                    PreferenceBlock(
                        preferences = state.preferences,
                        onDays = viewModel::setTrainingDays,
                        onSplit = viewModel::setSplit,
                        onWeekStart = viewModel::setWeekStart,
                        lighterWeek = state.lighterWeek,
                        onLighterWeek = viewModel::setLighterWeek,
                    )
                }
            }

            if (state.proposals.isEmpty()) {
                val hasOpenDay = week?.hasOpenTrainingSlot(
                    todayEpochDay = today,
                    trainingDayIndices = WeeklySchedulePlanner.trainingDayIndices(
                        state.preferences.trainingDaysPerWeek,
                    ),
                ) == true
                val hasPins = week?.days?.any { !it.isRest } == true
                val hasRoutines = state.routines.isNotEmpty()
                if (!hasPins && hasRoutines) {
                    item(key = "replay") {
                        // Empty week with programs already here: replay is the recovery.
                        // Suggest stays, quiet — heat-shaped fills are the other path.
                        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            PrimaryGymButton(
                                text = WeekTwoCopy.VOLT,
                                onClick = viewModel::replayStoredAnswers,
                            )
                            Text(
                                WeekTwoCopy.CAPTION,
                                style = InstrumentType.caption,
                                color = TextSecondary,
                            )
                            TextButton(onClick = viewModel::suggestFills) {
                                Text(
                                    "Suggest a week",
                                    style = InstrumentType.bodyStrong,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                } else if (hasOpenDay) {
                    item(key = "suggest") {
                        // Empty week, no routines: same words and weight as Home.
                        // Week with pins: quiet — filling holes is not the page's one act.
                        if (!hasPins) {
                            PrimaryGymButton(
                                text = "Suggest a week",
                                onClick = viewModel::suggestFills,
                            )
                        } else {
                            TextButton(onClick = viewModel::suggestFills) {
                                Text(
                                    "Suggest a week",
                                    style = InstrumentType.bodyStrong,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "accept") {
                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        Kicker("Suggested")
                        Text(
                            "Nothing is pinned until you confirm.",
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                        PrimaryGymButton(text = "Use this week", onClick = viewModel::acceptFills)
                        TextButton(onClick = viewModel::dismissFills) {
                            Text("Dismiss", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                }
            }

            if (state.routines.isEmpty()) {
                item(key = "routines-empty") {
                    EmptyState(
                        title = "Build your first plan",
                        body = "Name a routine, add lifts and targets, then pin it to a day above.",
                        actionLabel = "Create a routine",
                        onAction = onCreateRoutine,
                    )
                }
            } else {
                item(key = "routines-header") { Kicker("Routines") }
                // One panel of routines rather than one card each: these are instances of a
                // single thing, and a card apiece reads as a stack of unrelated objects.
                item(key = "routines") {
                    GroupedList {
                        state.routines.forEachIndexed { index, routine ->
                            if (index > 0) HairlineDivider()
                            RoutineRow(
                                routine = routine,
                                updatedLabel = routineUpdatedLabel(routine, dateFormat),
                                onOpen = { onOpenRoutine(routine.id) },
                                onDelete = { pendingDeleteId = routine.id },
                            )
                        }
                    }
                }
                item(key = "delete-hint") {
                    Text(
                        "Long-press a routine to delete it.",
                        style = InstrumentType.caption,
                        color = TextTertiary,
                    )
                }
            }
        }
    }

    val sheetDay = openDay?.let { epochDay ->
        state.week?.days?.firstOrNull { it.epochDay == epochDay }
            ?: state.proposals.firstOrNull { it.epochDay == epochDay }
    }
    if (sheetDay != null) {
        PlanDaySheet(
            day = sheetDay,
            routines = state.routines,
            isPast = sheetDay.epochDay < today,
            logged = sheetDay.epochDay in state.loggedEpochDays,
            onStart = {
                openDay = null
                viewModel.startDay(sheetDay)
            },
            onPinRoutine = { routineId ->
                openDay = null
                viewModel.pinRoutine(sheetDay.epochDay, routineId)
            },
            onPinFocus = { kind ->
                openDay = null
                viewModel.pinFocus(sheetDay.epochDay, kind)
            },
            onSwapRoutine = { routineId ->
                openDay = null
                sheetDay.slotId?.let { viewModel.swapRoutine(it, routineId) }
            },
            onEditRoutine = sheetDay.routineId?.let { routineId ->
                {
                    openDay = null
                    onOpenRoutine(routineId)
                }
            },
            onUnpin = {
                openDay = null
                sheetDay.slotId?.let(viewModel::unpin)
            },
            onDismiss = { openDay = null },
            occurrences = viewModel.agendaFor(sheetDay.epochDay),
            onStartOccurrence = { occurrenceId ->
                openDay = null
                viewModel.startOccurrence(occurrenceId)
            },
            onAddMorningCardio = {
                openDay = null
                viewModel.addMorningCardio(sheetDay.epochDay)
            },
        )
    }

    if (blocked != null) {
        ResumeOrDiscardDialog(
            onResume = viewModel::resumeBlocked,
            onDiscardAndStart = viewModel::discardBlockedAndStart,
            onDismiss = viewModel::dismissBlockedStart,
        )
    }

    pendingDeleteId?.let { id ->
        val pendingName = state.routines.firstOrNull { it.id == id }?.name ?: "this routine"
        ConfirmActionDialog(
            title = "Delete $pendingName?",
            body = "This cannot be undone. Past workout history stays saved, and any day it " +
                "was pinned to becomes open.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteRoutine(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun PlanHeader(
    tuning: Boolean,
    canCreate: Boolean,
    onToggleTune: () -> Unit,
    onCreate: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val stacked = LogLoopScale.stackEntryWells(LocalDensity.current.fontScale)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(
                start = Metrics.gutter,
                end = Metrics.space1,
                top = Metrics.space2,
                bottom = Metrics.space3,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Plan",
                modifier = Modifier.weight(1f),
                style = InstrumentType.display,
                color = TextPrimary,
            )
            if (!stacked) {
                PlanHeaderActions(
                    tuning = tuning,
                    canCreate = canCreate,
                    onToggleTune = onToggleTune,
                    onCreate = onCreate,
                    onOpenLibrary = onOpenLibrary,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }
        if (stacked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlanHeaderActions(
                    tuning = tuning,
                    canCreate = canCreate,
                    onToggleTune = onToggleTune,
                    onCreate = onCreate,
                    onOpenLibrary = onOpenLibrary,
                )
            }
        }
    }
}

@Composable
private fun PlanHeaderActions(
    tuning: Boolean,
    canCreate: Boolean,
    onToggleTune: () -> Unit,
    onCreate: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    TextButton(onClick = onToggleTune) {
        Text(
            if (tuning) "Done" else "Tune",
            style = InstrumentType.bodyStrong,
            color = if (tuning) Volt else TextSecondary,
        )
    }
    TextButton(onClick = onOpenLibrary) {
        Text("Library", style = InstrumentType.bodyStrong, color = TextSecondary)
    }
    if (canCreate) {
        TextButton(onClick = onCreate) {
            Text("New", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    }
}

/**
 * A routine as a program rather than as a document.
 *
 * Moved from the Routines tab unchanged. Deleting is a long press: as a trailing icon it sat
 * inside the row's own tap target, one slip away from destroying a program.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineRow(
    routine: Routine,
    updatedLabel: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    val preview = routine.exercises.take(PREVIEW_LIFTS).joinToString(" · ") { it.exercise.name }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.rowMin)
            .combinedClickable(
                onLongClickLabel = "Delete ${routine.name}",
                onLongClick = {
                    Haptics.tick(view)
                    onDelete()
                },
                onClick = onOpen,
            )
            .padding(horizontal = Metrics.space4, vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(
                routine.name,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview.ifEmpty { "No lifts yet" },
                style = InstrumentType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (updatedLabel != null) {
                Kicker(updatedLabel, color = TextTertiary)
            }
        }
        MetricCluster(value = routine.exercises.size.toString(), label = "lifts")
    }
}

private fun routineUpdatedLabel(routine: Routine, dateFormat: DateFormat): String? {
    if (routine.updatedAt <= 0L) return null
    return "Updated ${dateFormat.format(Date(routine.updatedAt))}"
}

private const val PREVIEW_LIFTS = 3

/** Thicker than a hairline so the filled portion reads as a position, thin enough not to be a bar. */
private val BLOCK_RULE_HEIGHT = 3.dp
