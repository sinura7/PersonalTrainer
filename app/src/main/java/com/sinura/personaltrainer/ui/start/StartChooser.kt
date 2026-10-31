package com.sinura.personaltrainer.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.R
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SheetValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.HomeStartCopy
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.ui.home.HomeStartPrompt
import com.sinura.personaltrainer.ui.plan.AuxiliaryPackList
import com.sinura.personaltrainer.ui.plan.CardioPickCard
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.LiveBarCopy
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale


private enum class StartPage { CHOOSER_PAGE, OTHER_PAGE, CARDIO_PAGE, EXTRA_PAGE }

/**
 * v4 blueprint 3.1 (F1): the one Start chooser. Today, Plan, History and Body all open this
 * sheet, and it is the only UI that starts a workout. Groups, top to bottom: today (Planned,
 * Resume, Rest day or No plan), routines, an empty workout, and Other (cardio, Extra kit or a
 * past log). While a workout or activity is live, Resume comes first and every new start is
 * disabled (D2).
 *
 * Every start goes through [gate], the one shared [StartGate], so every tab gets Today's
 * admission: one reserved attempt at a time, callbacks bound to the attempt they rendered,
 * and a start refused by a live workout held until the user resolves it. A routine opens its
 * preview first; Begin is the start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChooser(
    onDismiss: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    gate: StartGate,
    onLogPast: () -> Unit = {},
    onLogCardio: () -> Unit = {},
    onLogMixed: () -> Unit = {},
    onOpenLiveActivity: (String) -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenRoutines: () -> Unit = {},
    onEditRoutine: (String) -> Unit = {},
    suggestedKit: ExtraEquipment = ExtraEquipment.MIXED,
    viewModel: StartOptionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToSession by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val navigateToCardio by viewModel.navigateToCardio.collectAsStateWithLifecycle()
    val navigateToComposer by viewModel.navigateToComposer.collectAsStateWithLifecycle()
    val starting by gate.startState.collectAsStateWithLifecycle()
    val blocked by gate.blockedByInProgress.collectAsStateWithLifecycle()
    val gateSession by gate.navigateToSession.collectAsStateWithLifecycle()
    val gateCardio by gate.navigateToCardio.collectAsStateWithLifecycle()
    var pageName by rememberSaveable { mutableStateOf(StartPage.CHOOSER_PAGE.name) }
    val page = runCatching { StartPage.valueOf(pageName) }.getOrDefault(StartPage.CHOOSER_PAGE)
    var kitName by rememberSaveable { mutableStateOf<String?>(null) }
    var previewRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Capture the attempt this frame presents: a callback from an older frame acts on nothing.
    val renderedAttemptId = starting.attemptId
    val owns = { gate.startState.value.attemptId == renderedAttemptId }
    val startsEnabled = StartGroups.startsEnabled(state) && !starting.pending && starting.error == null &&
        blocked == null
    val mayStart = { owns() && gate.canEditStartChoice() }

    // The sheet is going away; nothing it staged may outlive it.
    DisposableEffect(Unit) {
        onDispose { viewModel.clearPendingNavigation() }
    }
    // A start the gate opened leaves the sheet; the host that owns the route navigates.
    LaunchedEffect(gateSession, gateCardio) {
        if (gateSession != null || gateCardio != null) {
            previewRoutineId = null
            onDismiss()
        }
    }
    LaunchedEffect(navigateToSession) {
        val target = navigateToSession ?: return@LaunchedEffect
        viewModel.onSessionNavigationHandled()
        onDismiss()
        onWorkoutStarted(target)
    }
    LaunchedEffect(navigateToCardio) {
        val target = navigateToCardio ?: return@LaunchedEffect
        viewModel.onCardioNavigationHandled()
        onDismiss()
        onOpenLiveActivity(target)
    }
    LaunchedEffect(navigateToComposer) {
        val mode = navigateToComposer ?: return@LaunchedEffect
        viewModel.onComposerNavigationHandled()
        onDismiss()
        when (mode) {
            "cardio" -> onLogCardio()
            "mixed" -> onLogMixed()
            else -> onLogPast()
        }
    }

    val leave = {
        val held = gate.blockedByInProgress.value
        if (held != null) gate.dismissBlockedStart(expectedBlocked = held, expectedAttemptId = renderedAttemptId)
        if (gate.blockedByInProgress.value == null && gate.dismissStart(expectedAttemptId = renderedAttemptId)) {
            onDismiss()
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || gate.canDismissStart() },
    )
    ModalBottomSheet(
        onDismissRequest = leave,
        containerColor = Surface3,
        sheetState = sheetState,
        modifier = Modifier.testTag(StartTags.SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
        ) {
            Text(
                stringResource(R.string.start_title),
                modifier = Modifier.semantics { heading() }.testTag(StartTags.START_TITLE),
                style = InstrumentType.title,
                color = TextPrimary,
            )
            GateStatus(
                pending = starting.pending,
                error = starting.error ?: state.error,
                canRetry = starting.error != null && starting.canRetry,
                blocked = blocked != null,
                onRetry = {
                    if (gate.startState.value.error != null) {
                        gate.retryStart(expectedAttemptId = renderedAttemptId)
                    } else {
                        viewModel.dismissError()
                    }
                },
                onDismissError = {
                    if (gate.startState.value.error != null) {
                        gate.dismissStart(expectedAttemptId = renderedAttemptId)
                    } else {
                        viewModel.dismissError()
                    }
                },
                onResumeBlocked = {
                    gate.resumeBlocked(expectedBlocked = blocked, expectedAttemptId = renderedAttemptId)
                },
            )
            when (page) {
                StartPage.CHOOSER_PAGE -> MainPage(
                    state = state,
                    enabled = startsEnabled,
                    onResume = {
                        onDismiss()
                        onWorkoutStarted(it)
                    },
                    onResumeActivity = {
                        onDismiss()
                        onOpenLiveActivity(it)
                    },
                    onDiscard = { confirmDiscard = true },
                    onStartToday = {
                        val today = state.todayStart
                        if (today != null && mayStart()) gate.startToday(start = today)
                    },
                    onStartRoutine = { if (mayStart()) previewRoutineId = it },
                    onStartEmpty = { if (mayStart()) gate.startFreeWorkout() },
                    onOther = { if (mayStart()) pageName = StartPage.OTHER_PAGE.name },
                    onOpenPlan = {
                        onDismiss()
                        onOpenPlan()
                    },
                    onOpenRoutines = {
                        onDismiss()
                        onOpenRoutines()
                    },
                )
                StartPage.OTHER_PAGE -> OtherPage(
                    enabled = startsEnabled,
                    onBack = { pageName = StartPage.CHOOSER_PAGE.name },
                    onLogPast = {
                        onDismiss()
                        onLogPast()
                    },
                    // Straight to the host: the composer flow's reader leaves with the sheet.
                    onLogMixed = {
                        onDismiss()
                        onLogMixed()
                    },
                    onLogCardio = {
                        onDismiss()
                        onLogCardio()
                    },
                    onCardio = { if (mayStart()) pageName = StartPage.CARDIO_PAGE.name },
                    onExtra = { if (mayStart()) pageName = StartPage.EXTRA_PAGE.name },
                )
                StartPage.CARDIO_PAGE -> CardioPage(
                    enabled = startsEnabled,
                    onBack = { pageName = StartPage.OTHER_PAGE.name },
                    onStartCardio = { if (mayStart()) gate.startCardio(type = it) },
                )
                StartPage.EXTRA_PAGE -> AuxiliaryPackList(
                    usedPackIds = emptySet(),
                    onPick = { if (mayStart()) gate.startAux(packId = it) },
                    onCancel = { pageName = StartPage.OTHER_PAGE.name },
                    suggestedKit = suggestedKit,
                    modifier = Modifier.testTag(StartTags.EXTRA_PAGE),
                    pickEnabled = startsEnabled,
                    equipment = kitName?.let { ExtraEquipment.fromStorage(it) },
                    onEquipmentChanged = { kitName = it?.name },
                    navigationEnabled = !starting.pending,
                )
            }
        }
    }

    val previewRoutine = previewRoutineId?.let { id -> state.routines.firstOrNull { it.id == id } }
    if (previewRoutine != null) {
        HomeStartPrompt(
            routine = previewRoutine,
            unit = LocalWeightUnit.current,
            loadWeights = gate::previewWeights,
            onEditRoutine = { id ->
                previewRoutineId = null
                onDismiss()
                onEditRoutine(id)
            },
            pending = starting.pending,
            labelOverridden = starting.pending || starting.error != null,
            title = previewRoutine.name,
            body = "",
            confirmLabel = when {
                starting.pending -> stringResource(R.string.home_preview_starting)
                starting.error != null -> stringResource(R.string.home_preview_unavailable)
                else -> stringResource(R.string.home_preview_begin)
            },
            onConfirm = {
                if (previewRoutineId == previewRoutine.id && owns()) gate.startRoutine(routineId = previewRoutine.id)
            },
            onDismiss = { previewRoutineId = null },
            confirmEnabled = !starting.pending && starting.error == null && blocked == null,
            dismissEnabled = !starting.pending,
            canDismiss = { !gate.startState.value.pending },
            canConfirm = owns,
            // The sheet underneath already shows the pending and error lines.
            contextContent = {},
            actionKey = renderedAttemptId,
        )
    }

    val inProgress = state.inProgress
    val liveActivity = state.liveActivity
    if (confirmDiscard && (inProgress != null || liveActivity != null)) {
        val kind = if (inProgress != null) LiveBarKind.WORKOUT else LiveBarKind.ACTIVITY
        ConfirmActionDialog(
            title = LiveBarCopy.discardTitle(kind),
            body = LiveBarCopy.discardBody(kind, state.inProgressSetCount),
            confirmLabel = LiveBarCopy.discard(kind),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discardInProgress()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun GateStatus(
    pending: Boolean,
    error: String?,
    canRetry: Boolean,
    blocked: Boolean,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onResumeBlocked: () -> Unit,
) {
    if (pending) {
        Text(
            stringResource(R.string.home_preview_starting),
            modifier = Modifier.testTag(StartTags.START_PENDING).semantics { liveRegion = LiveRegionMode.Polite },
            style = InstrumentType.caption,
            color = TextSecondary,
        )
    }
    if (blocked) {
        Column(
            modifier = Modifier.testTag(StartTags.START_BLOCKED),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            Text(stringResource(R.string.start_blocked), style = InstrumentType.body, color = TextSecondary)
            GhostButton(label = stringResource(R.string.start_go_to_live), onClick = onResumeBlocked)
        }
    }
    if (error != null) {
        Column(
            modifier = Modifier.testTag(StartTags.START_ERROR),
            verticalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            GymErrorBanner(message = error, onDismiss = onDismissError)
            if (canRetry) {
                GhostButton(
                    label = stringResource(R.string.start_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(StartTags.START_RETRY),
                )
            }
        }
    }
}

@Composable
private fun MainPage(
    state: StartOptionsUiState,
    enabled: Boolean,
    onResume: (String) -> Unit,
    onResumeActivity: (String) -> Unit,
    onDiscard: () -> Unit,
    onStartToday: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartEmpty: () -> Unit,
    onOther: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenRoutines: () -> Unit,
) {
    Group(kicker = stringResource(R.string.start_group_today)) {
        if (state.isLoading) {
            // Never blank: a quiet placeholder holds the row's place until the plan is read.
            Column(modifier = Modifier.fillMaxWidth().height(Metrics.commit).background(Surface2)) {}
        } else {
            TodayRow(state, enabled, onResume, onResumeActivity, onDiscard, onStartToday, onOpenPlan)
        }
    }
    Group(kicker = stringResource(R.string.start_group_routine)) {
        val routines = StartGroups.routines(state.routines)
        if (routines.isEmpty()) {
            Text(
                stringResource(R.string.start_no_routines),
                modifier = Modifier.testTag(StartTags.START_NO_ROUTINES),
                style = InstrumentType.body,
                color = TextSecondary,
            )
            GhostButton(label = stringResource(R.string.start_make_routine), onClick = onOpenRoutines)
        } else {
            GroupedList {
                routines.take(StartGroups.ROUTINE_LIMIT).forEachIndexed { index, routine ->
                    if (index > 0) HairlineDivider()
                    InstrumentRow(
                        title = routine.name,
                        subtitle = pluralStringResource(
                            R.plurals.start_routine_sub,
                            routine.exercises.size,
                            routine.exercises.size,
                        ),
                        onClick = { onStartRoutine(routine.id) },
                        enabled = enabled && routine.exercises.isNotEmpty(),
                        modifier = Modifier.heightIn(min = Metrics.rowMin).testTag(StartTags.routine(routine.id)),
                    )
                }
                if (routines.size > StartGroups.ROUTINE_LIMIT) {
                    HairlineDivider()
                    InstrumentRow(
                        title = stringResource(R.string.start_all_routines),
                        onClick = onOpenRoutines,
                        modifier = Modifier.heightIn(min = Metrics.rowMin).testTag(StartTags.START_ALL_ROUTINES),
                    )
                }
            }
        }
    }
    Group(kicker = stringResource(R.string.start_group_empty)) {
        SecondaryGymButton(
            text = stringResource(R.string.start_empty),
            onClick = onStartEmpty,
            enabled = enabled,
            height = Metrics.rowMin,
            modifier = Modifier.fillMaxWidth().testTag(StartTags.START_EMPTY),
        )
    }
    Group(kicker = stringResource(R.string.start_group_other)) {
        GroupedList {
            InstrumentRow(
                title = stringResource(R.string.start_other),
                onClick = onOther,
                enabled = enabled,
                modifier = Modifier.heightIn(min = Metrics.rowMin).testTag(StartTags.START_OTHER),
            )
        }
    }
}

@Composable
private fun TodayRow(
    state: StartOptionsUiState,
    enabled: Boolean,
    onResume: (String) -> Unit,
    onResumeActivity: (String) -> Unit,
    onDiscard: () -> Unit,
    onStartToday: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    when (StartGroups.today(state)) {
        StartTodayGroup.RESUME_LIVE -> {
            val inProgress = state.inProgress
            val activity = state.liveActivity
            val kind = if (inProgress != null) LiveBarKind.WORKOUT else LiveBarKind.ACTIVITY
            PrimaryGymButton(
                text = if (inProgress != null) {
                    stringResource(R.string.start_resume, inProgress.routineName ?: stringResource(R.string.start_free_workout))
                } else {
                    stringResource(R.string.start_resume_activity)
                },
                onClick = {
                    if (inProgress != null) onResume(inProgress.id) else activity?.let { onResumeActivity(it.id) }
                },
                height = Metrics.commit,
                modifier = Modifier.fillMaxWidth().testTag(StartTags.START_RESUME),
            )
            if (inProgress != null) {
                Text(
                    pluralStringResource(R.plurals.start_resume_sub, state.inProgressSetCount, state.inProgressSetCount),
                    style = InstrumentType.caption,
                    color = TextTertiary,
                )
            }
            Text(stringResource(R.string.start_blocked), style = InstrumentType.body, color = TextSecondary)
            GhostButton(
                label = if (kind == LiveBarKind.WORKOUT) {
                    stringResource(R.string.start_discard_workout)
                } else {
                    stringResource(R.string.start_discard_activity)
                },
                onClick = onDiscard,
                modifier = Modifier.testTag(StartTags.START_DISCARD),
            )
        }
        StartTodayGroup.PLANNED_TODAY -> {
            val today = state.todayStart ?: return
            PrimaryGymButton(
                text = stringResource(R.string.start_today, today.title),
                onClick = onStartToday,
                enabled = enabled,
                height = Metrics.commit,
                modifier = Modifier.fillMaxWidth().testTag(StartTags.START_TODAY),
            )
            today.preview?.let { Text(it, style = InstrumentType.caption, color = TextTertiary) }
        }
        StartTodayGroup.REST_DAY -> Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.rowMin).testTag(StartTags.START_TODAY_REST),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(stringResource(R.string.start_rest_day), style = InstrumentType.bodyStrong, color = TextPrimary)
            state.nextPlanned?.let {
                val weekday = LocalDate.ofEpochDay(it.epochDay).dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                Text(
                    stringResource(R.string.start_next_hint, weekday, it.title),
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
        StartTodayGroup.NO_PLAN -> Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.rowMin).testTag(StartTags.START_TODAY_NONE),
            verticalArrangement = Arrangement.spacedBy(Metrics.space1),
        ) {
            Text(stringResource(R.string.start_no_plan), style = InstrumentType.bodyStrong, color = TextPrimary)
            GhostButton(
                label = stringResource(R.string.start_plan_week),
                onClick = onOpenPlan,
                modifier = Modifier.testTag(StartTags.START_PLAN_WEEK),
            )
        }
    }
}

@Composable
private fun OtherPage(
    enabled: Boolean,
    onBack: () -> Unit,
    onLogPast: () -> Unit,
    onLogMixed: () -> Unit,
    onLogCardio: () -> Unit,
    onCardio: () -> Unit,
    onExtra: () -> Unit,
) {
    GhostButton(
        label = stringResource(R.string.start_other_back),
        onClick = onBack,
        modifier = Modifier.testTag(StartTags.OTHER_BACK),
    )
    Group(kicker = stringResource(R.string.start_live_cardio_kicker)) {
        GroupedList {
            InstrumentRow(
                title = stringResource(R.string.start_live_cardio),
                subtitle = HomeStartCopy.CARDIO_SUBTITLE,
                onClick = onCardio,
                enabled = enabled,
                modifier = Modifier.heightIn(min = Metrics.rowMin).testTag(StartTags.LIVE_CARDIO),
            )
            HairlineDivider()
            InstrumentRow(
                title = HomeStartCopy.EXTRA,
                subtitle = HomeStartCopy.EXTRA_SUBTITLE,
                onClick = onExtra,
                enabled = enabled,
                modifier = Modifier.heightIn(min = Metrics.rowMin).testTag(StartTags.EXTRA),
            )
        }
    }
    Group(kicker = stringResource(R.string.start_log_kicker)) {
        GroupedList {
            InstrumentRow(
                title = stringResource(R.string.start_log_past),
                subtitle = stringResource(R.string.start_log_past_sub),
                onClick = onLogPast,
                modifier = Modifier.testTag(StartTags.LOG_PAST),
            )
            HairlineDivider()
            InstrumentRow(
                title = stringResource(R.string.start_log_mixed),
                subtitle = ActivityDetailCopy.MIXED_CAPTION,
                onClick = onLogMixed,
                modifier = Modifier.testTag(StartTags.LOG_MIXED),
            )
            HairlineDivider()
            InstrumentRow(
                title = stringResource(R.string.start_log_cardio),
                subtitle = stringResource(R.string.start_log_cardio_sub),
                onClick = onLogCardio,
                modifier = Modifier.testTag(StartTags.LOG_CARDIO),
            )
        }
    }
}

/** Home's cardio page, moved here whole: one card per plannable type, one tap starts it. */
@Composable
private fun CardioPage(
    enabled: Boolean,
    onBack: () -> Unit,
    onStartCardio: (CardioType) -> Unit,
) {
    GhostButton(
        label = stringResource(R.string.start_other_back),
        onClick = onBack,
        modifier = Modifier.testTag(StartTags.OTHER_BACK),
    )
    Group(kicker = HomeStartCopy.CARDIO) {
        LazyRow(
            modifier = Modifier.testTag(StartTags.CARDIO_PAGE),
            horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
        ) {
            items(ScheduleKind.planCardioTypes, key = { it.name }) { type ->
                CardioPickCard(
                    type = type,
                    onClick = { onStartCardio(type) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun Group(kicker: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Kicker(kicker)
        content()
    }
}
