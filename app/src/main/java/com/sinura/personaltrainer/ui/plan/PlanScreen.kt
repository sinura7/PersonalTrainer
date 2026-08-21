package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ResumeOrDiscardDialog
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

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
    viewModel: PlanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
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
                    Text(week.summary, style = InstrumentType.caption, color = TextTertiary)
                }
            }

            if (tuning) {
                item(key = "tune") {
                    PreferenceBlock(
                        preferences = state.preferences,
                        onDays = viewModel::setTrainingDays,
                        onSplit = viewModel::setSplit,
                        onWeekStart = viewModel::setWeekStart,
                    )
                }
            }

            if (state.proposals.isEmpty()) {
                item(key = "suggest") {
                    TextButton(onClick = viewModel::suggestFills) {
                        Text("Suggest a week", style = InstrumentType.bodyStrong, color = TextSecondary)
                    }
                }
            } else {
                item(key = "accept") {
                    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        Kicker("Suggested")
                        Text(
                            "Nothing is saved until you accept it.",
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                        PrimaryGymButton(text = "Accept fills", onClick = viewModel::acceptFills)
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
            onUnpin = {
                openDay = null
                sheetDay.slotId?.let(viewModel::unpin)
            },
            onDismiss = { openDay = null },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(
                start = Metrics.gutter,
                end = Metrics.space1,
                top = Metrics.space2,
                bottom = Metrics.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Plan",
            modifier = Modifier.weight(1f),
            style = InstrumentType.display,
            color = TextPrimary,
        )
        TextButton(onClick = onToggleTune) {
            Text(
                if (tuning) "Done" else "Tune",
                style = InstrumentType.bodyStrong,
                color = if (tuning) Volt else TextSecondary,
            )
        }
        // Library's unfiltered door, now that it is not a tab. It belongs beside the routines
        // it feeds: everywhere else you reach Library, you arrive already filtered.
        TextButton(onClick = onOpenLibrary) {
            Text("Library", style = InstrumentType.bodyStrong, color = TextSecondary)
        }
        if (canCreate) {
            TextButton(onClick = onCreate) {
                Text("New", style = InstrumentType.bodyStrong, color = Volt)
            }
        }
        // Settings' named second home. It used to be reachable only from Home, which made it a
        // stack screen with no obvious way back to the thing you were configuring.
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}

/**
 * Seven days, side by side, never scrolling.
 *
 * A week you have to scroll is not a week you can see. Each cell is the same width and carries
 * the same five things in the same order, so the row reads down a shared baseline: today's
 * marker, the day letter, the date, what is on it, and whether it happened.
 */
@Composable
private fun WeekStrip(
    days: List<SuggestedTrainingDay>,
    proposals: Map<Long, SuggestedTrainingDay>,
    loggedEpochDays: Set<Long>,
    today: Long,
    onOpenDay: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        days.forEach { day ->
            WeekCell(
                day = day,
                proposal = proposals[day.epochDay],
                isToday = day.epochDay == today,
                logged = day.epochDay in loggedEpochDays,
                onClick = { onOpenDay(day.epochDay) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekCell(
    day: SuggestedTrainingDay,
    proposal: SuggestedTrainingDay?,
    isToday: Boolean,
    logged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayOfMonth = remember(day.epochDay) { LocalDate.ofEpochDay(day.epochDay).dayOfMonth }
    val pinned = !day.isRest
    val shown = if (pinned) day else proposal
    val label = when {
        pinned -> day.routineName ?: day.focusTitle
        proposal != null -> proposal.routineName ?: proposal.focusTitle
        else -> "Rest"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .heightIn(min = Metrics.touchMin)
            .padding(vertical = Metrics.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        // The same 3dp accent rule the nav bar uses for the selected tab, so "here" means the
        // same thing in both places. It is the strip's only accent.
        Box(
            modifier = Modifier
                .size(width = TODAY_MARKER_WIDTH, height = TODAY_MARKER_HEIGHT)
                .background(if (isToday) Volt else Color.Transparent),
        )
        Kicker(
            day.dayOfWeek.shortLabel().take(1),
            color = if (isToday) Volt else TextTertiary,
        )
        Text(
            dayOfMonth.toString(),
            style = InstrumentType.numeralSm,
            color = if (shown != null) TextPrimary else TextTertiary,
        )
        Text(
            label,
            style = InstrumentType.caption,
            // A proposal is quieter than a pin, so a previewed week never looks like a decided
            // one: the difference is visible before you read a word of it.
            color = when {
                pinned -> TextSecondary
                proposal != null -> TextTertiary
                else -> TextTertiary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (logged) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Logged",
                tint = TextSecondary,
                modifier = Modifier.size(LOGGED_TICK),
            )
        } else {
            Box(modifier = Modifier.size(LOGGED_TICK))
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
private val TODAY_MARKER_WIDTH = 16.dp
private val TODAY_MARKER_HEIGHT = 3.dp
private val LOGGED_TICK = 12.dp
