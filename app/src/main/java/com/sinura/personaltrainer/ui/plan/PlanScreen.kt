package com.sinura.personaltrainer.ui.plan

import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.workout.StartSheetOpener
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.InstrumentType
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
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(
                if (complete) "Block complete" else "Week $week of ${block.weeks}",
                modifier = Modifier.weight(1f),
                color = TextSecondary,
            )
            if (complete) {
                TextButton(
                    onClick = onStartNext,
                    modifier = Modifier.heightIn(min = Metrics.touchMin),
                    contentPadding = PaddingValues(horizontal = Metrics.space2, vertical = 0.dp),
                ) {
                    Text(
                        "Start the next twelve",
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                    .background(TextTertiary),
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
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.workingSets.toString(),
                label = "sets",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            val column = SetCopy.workColumn(review.work, unit)
            MetricCluster(
                value = column.value,
                label = column.label,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            )
            MetricCluster(
                value = review.recordsBroken.toString(),
                label = "PRs",
                modifier = Modifier.weight(1f),
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
                        color = TextSecondary,
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
    onOpenRoutine: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenDay: (Long, Boolean) -> Unit,
    onOpenStartSheet: () -> Unit = {},
    onBuildWeek: () -> Unit = {},
    onOpenLive: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: PlanViewModel = viewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val missedWork by viewModel.missedWork.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.missedWork.readRetained() }
    val navigateToEditor by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    val today = LocalTodayEpochDay.current

    LaunchedEffect(navigateToEditor) {
        val id = navigateToEditor ?: return@LaunchedEffect
        onOpenRoutine(id)
        viewModel.onEditorNavigationHandled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit),
    ) {
        if (state.isLoading) {
            PlanWeekLoading(onOpenRoutines = onOpenRoutines, onOpenStartSheet = onOpenStartSheet)
            return@Column
        }

        val week = state.week
        val weekStart = week?.weekStartEpochDay
            ?: WeekBoard.weekStartEpochDay(today, state.preferences.weekStart)
        val names = remember(state.routines) { state.routines.associate { it.id to it.name } }
        val routinesById = remember(state.routines) { state.routines.associateBy { it.id } }
        val cells = remember(weekStart, state.occurrences, state.rules, names) {
            WeekBoard.forWeek(weekStart, state.occurrences, state.rules, names)
        }
        val proposalsByDay = remember(state.proposals) {
            state.proposals.associateBy { it.epochDay }
        }
        val hasProposals = state.proposals.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(PlanTags.CONTENT),
            contentPadding = PaddingValues(
                start = Metrics.gutter,
                end = Metrics.gutter,
                top = Metrics.space2,
                bottom = Metrics.space7,
            ),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            item(key = "header") {
                // At large text sizes, fixed chrome can leave less room than one complete
                // dated target. Keep these full labels in the same scrollable page.
                PlanHeader(
                    onOpenRoutines = onOpenRoutines,
                    onOpenStartSheet = onOpenStartSheet,
                    horizontalPadding = 0.dp,
                )
            }
            state.error?.let { message ->
                item(key = "error") {
                    if (state.readFailed) {
                        PlanWeekError(message = message, onRetry = viewModel::retry)
                    } else {
                        GymErrorBanner(message = message, onDismiss = viewModel::dismissError)
                    }
                }
            }
            val liveId = state.inProgress?.id ?: state.liveActivity?.id
            if (liveId != null) {
                item(key = "live-row") {
                    PlanLiveRow(onOpen = { onOpenLive(liveId, state.inProgress == null) })
                }
            }
            if (PlanWeekState.isEmpty(state)) {
                item(key = "week-empty") { PlanWeekEmpty(onBuildWeek = onBuildWeek) }
            }
            if (state.proposals.isNotEmpty()) {
                item(key = "suggested") { PlanSuggestedCaption() }
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

            if (missedWork.retained || missedWork.error != null || missedWork.receipt != null) {
                item(key = "retained-missed-work") { MissedWorkRetainedCard(viewModel.missedWork) }
            }
            if (state.missedWorkPrompt && !missedWork.retained) {
                item(key = "missed-work") {
                    MissedWorkCard(
                        overdueCount = state.overdueCount,
                        mutationEnabled = !missedWork.busy && !state.isLoading,
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

            item(key = "summary") {
                Text(
                    WeekBoard.summary(cells),
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
            // Blueprint 4.2: the week is seven day rows, Monday to Sunday (or the owner's
            // week start), each showing what that civil day holds. No strip to scrub and
            // no hidden selected day: every planned row is visible at once.
            items(items = cells, key = { cell -> "day:${cell.epochDay}" }) { cell ->
                val dayItems = remember(cell.epochDay, state.occurrences, state.rules, names) {
                    DailyAgenda.forDay(cell.epochDay, state.occurrences, state.rules, names)
                }
                PlanWeekDayRow(
                    cell = cell,
                    items = dayItems,
                    routines = routinesById,
                    proposal = proposalsByDay[cell.epochDay],
                    isToday = cell.epochDay == today,
                    onOpenDay = { onOpenDay(cell.epochDay, false) },
                    // Add follows the row it sits on, never today (R6 Critic). Past days take no add.
                    onAddToDay = { onOpenDay(cell.epochDay, true) }.takeIf { cell.epochDay >= today },
                )
            }
        }
    }

    MissedWorkPreviewSheet(form = viewModel.missedWork,
        routines = state.routines, onReviewCurrent = viewModel::reviewMissedWork)
}

@Composable
internal fun PlanHeader(
    onOpenRoutines: () -> Unit,
    onOpenStartSheet: () -> Unit = {},
    horizontalPadding: Dp = Metrics.gutter,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = Metrics.space2,
                bottom = Metrics.space3,
            ),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Text(
            "Plan",
            modifier = Modifier.testTag(PlanTags.TITLE).semantics { heading() },
            style = InstrumentType.display,
            color = TextPrimary,
        )
        PlanHeaderActions(
            onOpenRoutines = onOpenRoutines,
            onOpenStartSheet = onOpenStartSheet,
        )
    }
}

@Composable
private fun PlanHeaderActions(
    onOpenRoutines: () -> Unit,
    onOpenStartSheet: () -> Unit,
) {
    StartSheetOpener(
        onOpen = onOpenStartSheet,
        modifier = Modifier.testTag(PlanTags.START_SHEET),
    )
    val routinesLabel = stringResource(R.string.routines_title)
    TextButton(
        onClick = onOpenRoutines,
        modifier = Modifier
            .heightIn(min = Metrics.touchMin)
            .testTag(PlanTags.ROUTINES_ENTRY)
            .semantics { contentDescription = routinesLabel },
    ) {
        Text(
            routinesLabel,
            style = InstrumentType.bodyStrong,
            color = TextSecondary,
        )
    }
}

@Composable
internal fun PlanLighterChip(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    InstrumentChip(
        label = LighterWeek.TUNE_LABEL,
        selected = enabled,
        onClick = { onToggle(!enabled) },
        modifier = Modifier.testTag(PlanTags.LIGHTER),
    )
}

@Composable
internal fun PlanAgendaRow(item: AgendaItem, exercise: Exercise?, onOpen: () -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touchMin)
            .testTag(PlanTags.agenda(item.occurrence.id))
            .clickable(role = Role.Button, onClickLabel = "Edit this day") {
                Haptics.tickLight(view)
                onOpen()
            }
            .padding(vertical = Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        exercise?.let {
            Box(modifier = Modifier.testTag(PlanTags.agendaThumb(item.occurrence.id))) {
                ExerciseThumb(exercise = it)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(text = item.title, style = InstrumentType.title, color = TextPrimary)
            Text(text = item.kindCaption, style = InstrumentType.caption, color = TextSecondary)
        }
        Icon(imageVector = TemperIcons.Chevron, contentDescription = null, tint = TextSecondary,
            modifier = Modifier.size(Metrics.chevron))
    }
}

/**
 * A reusable routine, shown directly beside its management actions.
 *
 * Tap edits the reusable routine. A quiet menu exposes management without requiring
 * a guessed gesture; long-press remains a shortcut to the same delete confirmation.
 */
@OptIn(ExperimentalFoundationApi::class)
/**
 * FND-031: one Volt command at a time. Replay recovers an empty week that
 * already has routines. Suggest fills holes. Use this week confirms a
 * proposal. Add session is the fill Volt; recovery stays quiet while that
 * act is up. Lighter is a chip on the week, not a second filled button.
 */
@Composable
internal fun PlanRecoveryCommands(
    hasPins: Boolean,
    hasRoutines: Boolean,
    hasOpenDay: Boolean,
    hasProposals: Boolean,
    onReplay: () -> Unit,
    onSuggest: () -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        when {
            hasProposals -> {
                Kicker("Suggested")
                Text(
                    "Nothing is pinned until you confirm.",
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                RecoveryCommand(
                    text = "Use this week",
                    onClick = onAccept,
                    quiet = quiet,
                    modifier = Modifier.testTag(PlanTags.USE_WEEK),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(PlanTags.DISMISS),
                ) {
                    Text("Dismiss", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
            !hasPins && hasRoutines -> {
                RecoveryCommand(
                    text = WeekTwoCopy.VOLT,
                    onClick = onReplay,
                    quiet = quiet,
                    spoken = WeekTwoCopy.VOLT,
                    modifier = Modifier.testTag(PlanTags.REPLAY),
                )
                Text(
                    WeekTwoCopy.CAPTION,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
                TextButton(
                    onClick = onSuggest,
                    modifier = Modifier.testTag(PlanTags.SUGGEST),
                ) {
                    Text(
                        "Suggest a week",
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
            hasOpenDay && !hasPins -> {
                RecoveryCommand(
                    text = "Suggest a week",
                    onClick = onSuggest,
                    quiet = quiet,
                    spoken = "Suggest a week",
                    modifier = Modifier.testTag(PlanTags.SUGGEST),
                )
            }
            hasOpenDay -> {
                TextButton(
                    onClick = onSuggest,
                    modifier = Modifier.testTag(PlanTags.SUGGEST),
                ) {
                    Text(
                        "Suggest a week",
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Missed-work Keep-the-dates is the Volt while that prompt is up.
 * Replay / Suggest / Use this week stay named, just not filled.
 */
@Composable
private fun RecoveryCommand(
    text: String,
    onClick: () -> Unit,
    quiet: Boolean,
    modifier: Modifier = Modifier,
    spoken: String? = null,
) {
    val tagged = if (spoken != null) {
        modifier.semantics { contentDescription = spoken }
    } else {
        modifier
    }
    if (quiet) {
        TextButton(onClick = onClick, modifier = tagged) {
            Text(text, style = InstrumentType.bodyStrong, color = TextSecondary)
        }
    } else {
        PrimaryGymButton(text = text, onClick = onClick, modifier = tagged)
    }
}

object PlanTags {
    const val CONTENT = "plan-content"
    const val TITLE = "plan-heading"
    const val SELECTED_DATE = "plan-selected-date"
    fun day(epochDay: Long): String = "plan-day-$epochDay"
    fun dayHeader(epochDay: Long): String = "plan-day-header-$epochDay"
    const val LIBRARY = "plan-library"
    const val START_SHEET = "plan-start-sheet"
    const val REPLAY = "plan-replay"
    const val SUGGEST = "plan-suggest"
    const val USE_WEEK = "plan-use-week"
    const val DISMISS = "plan-dismiss"
    fun dayAdd(epochDay: Long): String = "plan-day-add-$epochDay"
    const val LIGHTER = "plan-lighter"
    /** The Routines screen heading (v4 V3: routines left Plan for their own screen). */
    const val ROUTINES = "plan-routines"
    /** The Plan header button that pushes the Routines screen (v4 V3). */
    const val ROUTINES_ENTRY = "plan-routines-entry"
    fun agenda(occurrenceId: String): String = "plan-agenda-$occurrenceId"
    fun agendaThumb(occurrenceId: String): String = "plan-agenda-thumb-$occurrenceId"
    fun routine(id: String): String = "plan-routine-$id"
    fun routineName(id: String): String = "plan-routine-name-$id"
    fun routineThumb(id: String, exerciseId: String): String = "plan-routine-thumb-$id-$exerciseId"
    fun routineOptions(id: String): String = "plan-routine-options-$id"
    fun routineEdit(id: String): String = "plan-routine-edit-$id"
    fun routineDelete(id: String): String = "plan-routine-delete-$id"
}

/** Thicker than a hairline so the filled portion reads as a position, thin enough not to be a bar. */
private val BLOCK_RULE_HEIGHT = 3.dp
