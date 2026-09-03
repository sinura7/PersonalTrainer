package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.MastheadCopy
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.ProgressionHint
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.daysSince
import com.sinura.personaltrainer.domain.featuredSession
import com.sinura.personaltrainer.domain.homeWork
import com.sinura.personaltrainer.domain.leftoverLiftNames
import com.sinura.personaltrainer.domain.nextSessionReason
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.components.ResumeOrDiscardDialog
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.StatTile
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onResumeWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenRoutine: (String) -> Unit = {},
    onLogActivity: (String) -> Unit = {},
    onOpenLiveCardio: (String) -> Unit = {},
    onGenerateSchedule: () -> Unit = {},
    onBuildWeek: () -> Unit = {},
    pendingOccurrenceStartId: String? = null,
    onPendingOccurrenceConsumed: () -> Unit = {},
    pendingOccurrenceReviewId: String? = null,
    onPendingOccurrenceReviewConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val cardioId by viewModel.navigateToCardio.collectAsStateWithLifecycle()
    val composerMode by viewModel.navigateToComposer.collectAsStateWithLifecycle()
    val editorId by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        onResumeWorkout(id)
        viewModel.onSessionNavigationHandled()
    }
    LaunchedEffect(cardioId) {
        val id = cardioId ?: return@LaunchedEffect
        onOpenLiveCardio(id)
        viewModel.onCardioNavigationHandled()
    }
    LaunchedEffect(composerMode) {
        val mode = composerMode ?: return@LaunchedEffect
        onLogActivity(mode)
        viewModel.onComposerNavigationHandled()
    }
    LaunchedEffect(editorId) {
        val id = editorId ?: return@LaunchedEffect
        onOpenRoutine(id)
        viewModel.onEditorNavigationHandled()
    }
    LaunchedEffect(pendingOccurrenceStartId) {
        val id = pendingOccurrenceStartId ?: return@LaunchedEffect
        viewModel.startOccurrence(id)
        onPendingOccurrenceConsumed()
    }
    LaunchedEffect(pendingOccurrenceReviewId) {
        val id = pendingOccurrenceReviewId ?: return@LaunchedEffect
        viewModel.reviewOccurrence(id)
        onPendingOccurrenceReviewConsumed()
    }
    val blocked by viewModel.blockedByInProgress.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val inProgress = state.inProgress
    var starterDismissed by rememberSaveable { mutableStateOf(false) }
    val showStarter = !state.setupComplete && !starterDismissed && inProgress == null
    var weighingIn by rememberSaveable { mutableStateOf(false) }

    // Starting a planned day while another session is live is a question, not something the
    // app answers on the user's behalf. Composed before the loading return so it survives a
    // recomposition that briefly reports loading.
    if (blocked != null) {
        ResumeOrDiscardDialog(
            onResume = viewModel::resumeBlocked,
            onDiscardAndStart = viewModel::discardBlockedAndStart,
            onDismiss = viewModel::dismissBlockedStart,
        )
    }

    if (weighingIn) {
        NumberEntryDialog(
            title = "Bodyweight",
            unitLabel = unit.suffix,
            initial = state.latestBodyweightKg
                ?.let {
                    WeightConverter.formatDisplayNumber(
                        WeightConverter.toDisplayValue(it, unit),
                    )
                }
                .orEmpty(),
            decimal = true,
            helper = "Weekly check-in. One a day is kept — the last one you type.",
            parse = { com.sinura.personaltrainer.domain.NumericEntry.parseWeightKg(it, unit) },
            onConfirm = {
                weighingIn = false
                viewModel.recordBodyweight(it)
            },
            onDismiss = { weighingIn = false },
        )
    }

    if (showStarter) {
        GetStartedSheet(
            onGenerate = {
                starterDismissed = true
                onGenerateSchedule()
            },
            onBuild = {
                starterDismissed = true
                onBuildWeek()
            },
            onWorkout = {
                starterDismissed = true
                viewModel.startFreeWorkout()
            },
            onDismiss = { starterDismissed = true },
        )
    }

    if (state.isLoading) {
        ScreenLoading()
        return
    }

    val today = LocalTodayEpochDay.current
    val weekStart = state.weekStartEpochDay.takeIf { it != 0L }
        ?: today
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today) }
    val reviewOccurrenceId by viewModel.reviewOccurrenceId.collectAsStateWithLifecycle()
    val focusEpochDay by viewModel.focusEpochDay.collectAsStateWithLifecycle()
    LaunchedEffect(weekStart, today) {
        val end = weekStart + 6
        if (selectedEpochDay !in weekStart..end) {
            selectedEpochDay = today.coerceIn(weekStart, end)
        }
    }
    LaunchedEffect(focusEpochDay) {
        val day = focusEpochDay ?: return@LaunchedEffect
        selectedEpochDay = day
        viewModel.onFocusEpochDayHandled()
    }
    val plan = state.weekPlan
    val names = remember(state.routines) { state.routines.associate { it.id to it.name } }
    val selectedAgenda = DailyAgenda.forDay(
        selectedEpochDay,
        state.occurrences,
        state.rules,
        names,
    )
    val leftoverSlot = plan?.dayOn(selectedEpochDay)
    val leftoverBelongs = WeekBoard.leftoverBelongsOn(
        selectedEpochDay,
        leftoverSlot,
        state.occurrences,
        state.rules,
    )
    val leftoverDay = leftoverSlot.takeIf { leftoverBelongs }
    val loggedSelected = selectedEpochDay in state.loggedEpochDays
    val hasPlan = plan?.days?.any { !it.isRest } == true
    val liftCount = MastheadCopy.headlineLiftCount(selectedAgenda, leftoverDay, state.routines)
    val nextDay = plan?.nextTrainingOnOrAfter(selectedEpochDay)
    val featured = featuredSession(today = leftoverDay, next = nextDay)
    val mastheadDay = leftoverDay ?: leftoverSlot?.copy(
        isRest = true,
        routineId = null,
        routineName = null,
    )
    val weekCells = remember(weekStart, state.occurrences, state.rules, names) {
        WeekBoard.forWeek(weekStart, state.occurrences, state.rules, names)
    }
    val dayKicker = if (selectedEpochDay == today) {
        "Today"
    } else {
        PlanDayCopy.weekdayTitle(Weekday.fromEpochDay(selectedEpochDay))
    }
    val stillOpen = if (selectedEpochDay == today) {
        DailyAgenda.stillOpen(
            today,
            weekStart,
            state.occurrences,
            state.rules,
            names,
        )
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Metrics.gutter,
            end = Metrics.gutter,
            top = Metrics.space4,
            bottom = Metrics.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                HomeMasthead(
                    epochDay = selectedEpochDay,
                    // The masthead describes the DAY, never the session. The live bar owns
                    // live, and a masthead that switched to narrating the workout would be a
                    // second answer to "where is my workout" on the screen that had three.
                    headline = MastheadCopy.headline(
                        day = mastheadDay,
                        loggedToday = loggedSelected,
                        liftCount = liftCount,
                        hasPlan = hasPlan,
                        agenda = selectedAgenda,
                    ),
                )
                WeekStrip(
                    cells = weekCells,
                    today = today,
                    selected = selectedEpochDay,
                    onSelectDay = { selectedEpochDay = it },
                )
                HomeStatRow(
                    lastSession = state.lastSession,
                    todayEpoch = today,
                    unit = unit,
                )
            }
        }
        state.error?.let { message ->
            item {
                // Dismissable: the flow only cleared this on a later SUCCESSFUL
                // action, so a one-off failure pinned a red card to Home forever.
                GymErrorBanner(message, onDismiss = viewModel::dismissError)
            }
        }
        if (state.missedWorkPrompt) {
            item {
                com.sinura.personaltrainer.ui.plan.MissedWorkCard(
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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                when (HomeToday.surface(selectedAgenda, leftoverBelongs, stillOpen)) {
                    HomeToday.Surface.AGENDA ->                     DailyAgendaCard(
                        items = selectedAgenda,
                        sessionLive = inProgress != null,
                        onStartOccurrence = viewModel::startOccurrence,
                        onStartFree = { viewModel.startFreeWorkout() },
                        routines = state.routines,
                        kicker = dayKicker,
                        stillOpen = stillOpen,
                        today = today,
                        epochDay = selectedEpochDay,
                        quietStart = state.missedWorkPrompt,
                        canEditDay = selectedEpochDay >= today,
                        confirmOccurrenceId = reviewOccurrenceId,
                        onConfirmOccurrenceConsumed = viewModel::onReviewOccurrenceHandled,
                        onMoveOccurrence = { occurrenceId, delta ->
                            viewModel.moveDayBlock(selectedAgenda, occurrenceId, delta)
                        },
                        onSkipOccurrence = viewModel::skipOccurrence,
                        onAddWorkout = { routineId, once ->
                            viewModel.addDaySession(
                                selectedEpochDay,
                                HomeDayAdd.Workout(routineId),
                                once,
                            )
                        },
                        onNewWorkout = { once ->
                            viewModel.addDaySession(
                                selectedEpochDay,
                                HomeDayAdd.NewWorkout,
                                once,
                            )
                        },
                        onAddCardio = { type, once ->
                            viewModel.addDaySession(
                                selectedEpochDay,
                                HomeDayAdd.Cardio(type),
                                once,
                            )
                        },
                        onAddAux = { packId, once ->
                            viewModel.addDaySession(
                                selectedEpochDay,
                                HomeDayAdd.Aux(packId),
                                once,
                            )
                        },
                    )
                    HomeToday.Surface.WEEK_FALLBACK -> ThisWeekCard(
                        day = leftoverDay,
                        nextDay = nextDay,
                        loggedToday = loggedSelected,
                        sessionLive = inProgress != null,
                        hasRoutines = state.routines.isNotEmpty(),
                        lifts = leftoverLiftNames(featured, state.routines),
                        reason = nextSessionReason(featured, state.recommendations),
                        routines = state.routines,
                        quietStart = state.missedWorkPrompt,
                        setupComplete = state.setupComplete,
                        offerSetupActions = !showStarter,
                        onGenerateSchedule = onGenerateSchedule,
                        onBuildWeek = onBuildWeek,
                        onSuggestWeek = {
                            viewModel.requestWeekSuggestion()
                            onOpenPlan()
                        },
                        onReplayAnswers = {
                            viewModel.requestAnswerReplay()
                            onOpenPlan()
                        },
                        onPrimary = {
                            leftoverDay?.takeUnless { it.isRest }?.let(viewModel::startSuggestedDay)
                        },
                        onStartFree = { viewModel.startFreeWorkout() },
                    )
                }
            }
        }
        if (state.bodyweightCheckInDue) {
            item {
                GymCard(modifier = Modifier.testTag(HomeTags.BODYWEIGHT_CHECK_IN)) {
                    Kicker("Weekly weigh-in")
                    Text(
                        "Log this week's weight.",
                        style = InstrumentType.body,
                        color = TextSecondary,
                    )
                    androidx.compose.material3.TextButton(onClick = { weighingIn = true }) {
                        Text(
                            "Log weight",
                            style = InstrumentType.bodyStrong,
                            color = com.sinura.personaltrainer.ui.theme.Volt,
                        )
                    }
                }
            }
        }
        if (state.lighterWeek) {
            item {
                Text(
                    LighterWeek.CAPTION,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
        if (state.readyToProgress.isNotEmpty()) {
            item {
                ReadyToProgressSection(
                    hints = state.readyToProgress,
                    unit = unit,
                    onOpenExercise = onOpenExercise,
                )
            }
        }
    }
}

/**
 * The date, then the one thing the user opened the app to find out.
 *
 * What this replaces led with the app's own name under a greeting, with the unit preference
 * as a third line and a settings gear nested in the masthead. Settings is a tab now.
 * A product's face states today's answer; its name is on the launcher icon.
 */
@Composable
internal fun HomeMasthead(
    epochDay: Long,
    headline: String,
) {
    val dateLine = remember(epochDay) {
        DateTimeFormatter.ofPattern(DATE_LINE_PATTERN).format(LocalDate.ofEpochDay(epochDay))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        Kicker(dateLine)
        Text(
            headline,
            style = InstrumentType.display,
            color = TextPrimary,
            maxLines = LogLoopScale.headlineLines(LocalDensity.current.fontScale),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Two numerals above the fold.
 *
 * Home had none: a fitness tracker whose first screen was a menu of links, where the largest
 * type on the page was the app's own name. These are the last session's working volume and
 * how long ago it was — both exact from all-time summaries, not the 30-day heat graph.
 */
@Composable
internal fun HomeStatRow(
    lastSession: SessionSummary?,
    todayEpoch: Long,
    unit: WeightUnit,
) {
    val column = remember(lastSession, unit) {
        lastSession?.homeWork(unit)
    }
    val daysSince = lastSession?.daysSince(todayEpoch)?.toString()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.cardGap),
    ) {
        StatTile(
            label = "Last session",
            value = column?.value ?: NO_VALUE,
            unit = column?.label,
            valueColor = if (column != null) TextPrimary else TextTertiary,
            modifier = Modifier
                .weight(1f)
                .testTag(HomeTags.LAST_SESSION),
        )
        StatTile(
            label = "Days since",
            value = daysSince ?: NO_VALUE,
            valueColor = if (daysSince != null) TextPrimary else TextTertiary,
            modifier = Modifier
                .weight(1f)
                .testTag(HomeTags.DAYS_SINCE),
        )
    }
}

@Composable
private fun ReadyToProgressSection(
    hints: List<ProgressionHint>,
    unit: WeightUnit,
    onOpenExercise: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        GymSectionHeader("Ready to progress")
        GroupedList {
            hints.forEachIndexed { index, hint ->
                if (index > 0) HairlineDivider()
                InstrumentRow(
                    title = hint.exerciseName,
                    subtitle = "Top set ${hint.lastWeightKg.toWeightLabel(unit)} × ${hint.lastReps}",
                    // The lift, not a start screen. A row that names a lift and opens a menu
                    // was asking the user to find it again themselves.
                    onClick = { onOpenExercise(hint.exerciseId) },
                ) {
                    MetricCluster(
                        value = WeightConverter.formatDisplayNumber(
                            WeightConverter.toDisplayValue(hint.suggestedWeightKg, unit),
                        ),
                        label = "target",
                        unit = unit.suffix,
                    )
                }
            }
        }
    }
}

// The separator is quoted: everything outside quotes in a pattern is a format field.
object HomeTags {
    const val LAST_SESSION = "home-last-session"
    const val DAYS_SINCE = "home-days-since"
    const val START = "home-start"
    const val FREE = "home-free-start"
    const val REPLAY = "home-replay"
    const val GET_STARTED = "home-get-started"
    const val GENERATE = "home-generate-schedule"
    const val BUILD_WEEK = "home-build-week"
    const val STARTER_WORKOUT = "home-starter-workout"
    const val BODYWEIGHT_CHECK_IN = "home-bodyweight-check-in"
    const val STILL_OPEN = "home-still-open"
    const val ADD = "home-add"
    const val ADD_EXTRA = ADD
    const val SESSION = "home-session-start"

    fun agendaRow(occurrenceId: String): String = "home-agenda-$occurrenceId"

    fun skipRow(occurrenceId: String): String = "home-skip-$occurrenceId"
}

private const val DATE_LINE_PATTERN = "EEEE '·' d MMM"
private const val NO_VALUE = "—"
