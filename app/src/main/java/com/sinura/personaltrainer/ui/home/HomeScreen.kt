package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.data.draft.RememberedHomeDay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DayRelation
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.LighterWeek
import com.sinura.personaltrainer.domain.MastheadCopy
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.StartSessionConfirm
import com.sinura.personaltrainer.domain.UndoHostCopy
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.HomeLogged
import com.sinura.personaltrainer.domain.featuredSession
import com.sinura.personaltrainer.domain.leftoverLiftNames
import com.sinura.personaltrainer.domain.nextSessionReason
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.ui.plan.MissedWorkPreviewSheet
import com.sinura.personaltrainer.ui.plan.MissedWorkRetainedCard
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.FieldComplaint
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymUndoHost
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.NumberEntryDialog
import com.sinura.personaltrainer.ui.components.OutlinedMarks
import com.sinura.personaltrainer.ui.components.ResumeOrDiscardDialog
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.WeekStrip
import com.sinura.personaltrainer.ui.components.rememberWeekStripState
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.update.DebugUpdateBanner
import com.sinura.personaltrainer.ui.update.rememberDebugUpdatePort
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Composable
fun HomeScreen(
    onResumeWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenRoutine: (String) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onOpenActivity: (String) -> Unit = {},
    onLogActivity: (String) -> Unit = {},
    onOpenLiveCardio: (String) -> Unit = {},
    pendingOccurrenceStartId: String? = null,
    /** The delivery behind a reminder's Start, marked used only once the start opens. */
    pendingOccurrenceDeliveryId: String? = null,
    /** One intentional reminder tap; retained while the required reads are waiting. */
    pendingOccurrenceRequestId: String? = null,
    onPendingOccurrenceConsumed: () -> Unit = {},
    pendingOccurrenceReviewId: String? = null,
    onPendingOccurrenceReviewConsumed: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val missedWork by viewModel.missedWork.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.missedWork.readRetained() }
    val dayMemory by viewModel.rememberedDay.snapshot.collectAsStateWithLifecycle()
    val starting by viewModel.startState.collectAsStateWithLifecycle()
    // Capture the action that this frame actually presents. A delegated State read
    // inside an old callback would otherwise turn its Start into a later Retry.
    val renderedStartAttemptId = starting.attemptId
    val renderedCanRetry = starting.canRetry
    val renderedOccurrenceId = starting.occurrenceId
    val startedSessionId by viewModel.navigateToSession.collectAsStateWithLifecycle()
    val cardioId by viewModel.navigateToCardio.collectAsStateWithLifecycle()
    val composerMode by viewModel.navigateToComposer.collectAsStateWithLifecycle()
    val editorId by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    LaunchedEffect(editorId) {
        val id = editorId ?: return@LaunchedEffect
        onOpenRoutine(id)
        viewModel.onEditorNavigationHandled()
    }
    LaunchedEffect(
        pendingOccurrenceStartId,
        pendingOccurrenceDeliveryId,
        pendingOccurrenceRequestId,
        state.readState,
        state.retryPending,
    ) {
        val id = pendingOccurrenceStartId ?: return@LaunchedEffect
        if (viewModel.dispatchPendingOccurrenceStart(id, pendingOccurrenceDeliveryId, pendingOccurrenceRequestId)) {
            onPendingOccurrenceConsumed()
        }
    }
    LaunchedEffect(pendingOccurrenceReviewId) {
        val id = pendingOccurrenceReviewId ?: return@LaunchedEffect
        viewModel.reviewOccurrence(id)
        onPendingOccurrenceReviewConsumed()
    }
    val blocked by viewModel.blockedByInProgress.collectAsStateWithLifecycle()
    val blockedOwner = blocked
    val skippedDay by viewModel.skippedDay.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val sessionLive = state.sessionLive
    var weighingIn by rememberSaveable { mutableStateOf(false) }
    val bodyweightSaved by viewModel.bodyweightSaved.collectAsStateWithLifecycle()
    LaunchedEffect(bodyweightSaved) {
        if (bodyweightSaved) {
            weighingIn = false
            viewModel.onBodyweightSaveHandled()
        }
    }
    val today = LocalTodayEpochDay.current
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today) }
    var selectedByUser by rememberSaveable { mutableStateOf(selectedEpochDay != today) }
    var dayMemoryApplied by rememberSaveable { mutableStateOf(false) }
    var dayResumed by rememberSaveable { mutableStateOf(false) }
    var dayPicker by remember { mutableStateOf(false) }
    var resetDayConfirm by remember { mutableStateOf(false) }
    val selectDay: (Long) -> Unit = { day ->
        if (viewModel.canEditStartChoice() &&
            viewModel.rememberedDay.choose(RememberedHomeDay(day, today))
        ) {
            selectedEpochDay = day
            selectedByUser = true
            dayResumed = false
            dayPicker = false
        }
    }
    val weekStripState = rememberWeekStripState()
    var lastToday by rememberSaveable { mutableLongStateOf(today) }
    var startSheet by rememberSaveable { mutableStateOf(false) }
    var startSheetRequestId by rememberSaveable { mutableStateOf("") }
    // Modal ownership must outlive a LazyColumn item leaving composition. Inserting the
    // recovery banner or scrolling the underlying board must never dismiss a pending start.
    var plannedStartPending by rememberSaveable(stateSaver = HomePlannedStartSaver) {
        mutableStateOf<HomePlannedStart?>(null)
    }
    var fallbackStartPending by rememberSaveable(stateSaver = HomeFallbackStartSaver) {
        mutableStateOf<HomeFallbackStart?>(null)
    }
    // Admission keeps the authored surface. Only an actual open/conflict outcome releases it.
    LaunchedEffect(startedSessionId) {
        val id = startedSessionId ?: return@LaunchedEffect
        startSheet = false
        plannedStartPending = null
        fallbackStartPending = null
        onResumeWorkout(id)
        viewModel.onSessionNavigationHandled()
    }
    LaunchedEffect(cardioId) {
        val id = cardioId ?: return@LaunchedEffect
        startSheet = false
        plannedStartPending = null
        fallbackStartPending = null
        onOpenLiveCardio(id)
        viewModel.onCardioNavigationHandled()
    }
    LaunchedEffect(composerMode) {
        val mode = composerMode ?: return@LaunchedEffect
        startSheet = false
        plannedStartPending = null
        fallbackStartPending = null
        onLogActivity(mode)
        viewModel.onComposerNavigationHandled()
    }
    LaunchedEffect(blocked) {
        if (blocked != null) {
            startSheet = false
            plannedStartPending = null
            fallbackStartPending = null
        }
    }
    val reviewOccurrenceId by viewModel.reviewOccurrenceId.collectAsStateWithLifecycle()
    val focusEpochDay by viewModel.focusEpochDay.collectAsStateWithLifecycle()
    val recoveryContent: (@Composable () -> Unit)? = if (state.readProblem != null) {
        { HomeReadRecovery(state = state, onRetry = { viewModel.retryRead() }) }
    } else {
        null
    }
    // Saveable selection and modal state stay mounted even before a usable board exists.
    // Temporary unread/default week values never clamp a chosen day.
    LaunchedEffect(today) {
        if (selectedEpochDay == lastToday) {
            selectedEpochDay = today
            if (dayMemoryApplied && today != lastToday) {
                viewModel.rememberedDay.choose(RememberedHomeDay(today, today))
            }
        }
        lastToday = today
    }
    LaunchedEffect(dayMemory.loaded, dayMemory.restored, dayMemory.error, state.readState, state.weekStartEpochDay) {
        if (dayMemoryApplied || !dayMemory.loaded || dayMemory.error != null ||
            state.readState != HomeReadState.CURRENT
        ) return@LaunchedEffect
        val remembered = dayMemory.restored
        if (!selectedByUser && remembered != null) {
            val restored = if (remembered.selectedEpochDay == remembered.todayEpochDay) today else remembered.selectedEpochDay
            selectedEpochDay = restored.takeIf { it in state.weekStartEpochDay..(state.weekStartEpochDay + 6) }
                ?: today.coerceIn(state.weekStartEpochDay, state.weekStartEpochDay + 6)
            dayResumed = restored == selectedEpochDay
        }
        dayMemoryApplied = true
    }
    LaunchedEffect(dayMemory.resetSerial) {
        if (viewModel.rememberedDay.consumeReset(dayMemory.resetSerial)) {
            selectedEpochDay = today
            selectedByUser = false
            dayResumed = false
        }
    }
    LaunchedEffect(state.readState, state.weekStartEpochDay, today) {
        if (state.readState != HomeReadState.CURRENT) return@LaunchedEffect
        val start = state.weekStartEpochDay
        val end = start + 6
        if (selectedEpochDay !in start..end) {
            selectedEpochDay = today.coerceIn(start, end)
            dayResumed = false
            if (dayMemoryApplied) viewModel.rememberedDay.choose(RememberedHomeDay(selectedEpochDay, today))
        }
    }
    LaunchedEffect(focusEpochDay, starting.pending) {
        if (starting.pending) return@LaunchedEffect
        val day = focusEpochDay ?: return@LaunchedEffect
        selectedEpochDay = day
        selectedByUser = true
        dayResumed = false
        viewModel.rememberedDay.choose(RememberedHomeDay(day, today))
        viewModel.onFocusEpochDayHandled()
    }

    // Starting a planned day while another session is live is a question, not something the
    // app answers on the user's behalf. Composed before the loading return so it survives a
    // recomposition that briefly reports loading.
    if (blockedOwner != null) {
        ResumeOrDiscardDialog(
            onResume = {
                viewModel.resumeBlocked(expectedBlocked = blockedOwner, expectedAttemptId = renderedStartAttemptId)
            },
            onDiscardAndStart = {
                if (viewModel.blockedByInProgress.value == blockedOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) {
                    if (renderedCanRetry) viewModel.retryStart(expectedAttemptId = renderedStartAttemptId)
                    else viewModel.discardBlockedAndStart(
                        expectedBlocked = blockedOwner,
                        expectedAttemptId = renderedStartAttemptId,
                    )
                }
            },
            onDismiss = {
                viewModel.dismissBlockedStart(expectedBlocked = blockedOwner, expectedAttemptId = renderedStartAttemptId)
            },
            discardEnabled = state.mutationEnabled && !starting.pending &&
                (starting.error == null || starting.canRetry),
            recoveryContent = {
                HomeStartRecovery(startStatus = starting, retryEnabled = false, onRetry = {})
                recoveryContent?.invoke()
            },
            title = if (starting.discardedOriginal) "Start the selected workout" else "A workout is already in progress",
            body = if (starting.discardedOriginal) {
                "The previous workout was discarded. The selected workout has not opened yet."
            } else "Pick one — this one, or the session you were in the middle of.",
            discardLabel = when {
                starting.pending -> "Starting…"
                starting.canRetry -> "Retry start"
                starting.discardedOriginal -> "Start unavailable"
                else -> "Discard it and start this"
            },
            resumeVisible = !starting.discardedOriginal,
            dismissEnabled = !starting.pending,
            canDismiss = {
                viewModel.blockedByInProgress.value == blockedOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId && viewModel.canDismissStart()
            },
            canResume = {
                viewModel.blockedByInProgress.value == blockedOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId && viewModel.canResumeBlockedStart()
            },
            actionKey = Triple(blockedOwner, renderedStartAttemptId, renderedCanRetry),
        )
    }

    if (weighingIn) {
        NumberEntryDialog(
            title = "Bodyweight",
            unitLabel = unit.suffix,
            initial = (state.bodyweightDraftKg ?: state.latestBodyweightKg)
                ?.let {
                    WeightConverter.formatDisplayNumber(
                        WeightConverter.toDisplayValue(it, unit),
                    )
                }
                .orEmpty(),
            decimal = true,
            helper = "Weekly check-in. One a day is kept — the last one you type.",
            parse = { com.sinura.personaltrainer.domain.NumericEntry.parseWeightKg(it, unit) },
            onConfirm = {},
            // Admission starts a write. The field closes only when the actual repository
            // call succeeds, so a failed write keeps the exact authored text for another Set.
            onConfirmAccepted = {
                viewModel.recordBodyweight(it)
                false
            },
            confirmEnabled = state.mutationEnabled && !state.bodyweightSavePending && !bodyweightSaved,
            dismissEnabled = !state.bodyweightSavePending && !bodyweightSaved,
            inputEnabled = !state.bodyweightSavePending && !bodyweightSaved,
            onInputAllowed = viewModel::canEditBodyweightDraft,
            recoveryContent = {
                if (state.bodyweightSavePending) {
                    Text(
                        "Saving weight…",
                        modifier = Modifier
                            .testTag(HomeTags.BODYWEIGHT_SAVING)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
                state.bodyweightSaveError?.let { message ->
                    FieldComplaint(message = message, modifier = Modifier.testTag(HomeTags.BODYWEIGHT_SAVE_ERROR))
                }
                recoveryContent?.invoke()
            },
            onDismiss = { if (viewModel.discardBodyweightDraft()) weighingIn = false },
        )
    }

    if (state.isLoading) {
        ScreenLoading()
        return
    }
    if (state.readState == HomeReadState.UNAVAILABLE) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Metrics.gutter),
        ) {
            GymCard {
                HomeReadRecovery(state = state, onRetry = { viewModel.retryRead() })
            }
        }
        return
    }

    val weekStart = state.weekStartEpochDay.takeIf { it != 0L }
        ?: today
    val debugUpdate = rememberDebugUpdatePort()
    val updateUi by debugUpdate.ui.collectAsStateWithLifecycle()
    val plan = state.weekPlan
    val names = remember(state.routines) { state.routines.associate { it.id to it.name } }
    val selectedAgenda = remember(selectedEpochDay, state.occurrences, state.rules, names) {
        DailyAgenda.forDay(
            selectedEpochDay,
            state.occurrences,
            state.rules,
            names,
        )
    }
    val leftoverSlot = plan?.dayOn(selectedEpochDay)
    val leftoverBelongs = remember(selectedEpochDay, leftoverSlot, state.occurrences, state.rules) {
        WeekBoard.leftoverBelongsOn(
            selectedEpochDay,
            leftoverSlot,
            state.occurrences,
            state.rules,
        )
    }
    val leftoverDay = leftoverSlot.takeIf { leftoverBelongs }
    val loggedSelected = selectedEpochDay in state.loggedEpochDays
    val hasPlan = remember(plan) { plan?.days?.any { !it.isRest } == true }
    val liftCount = remember(selectedAgenda, leftoverDay, state.routines) {
        MastheadCopy.headlineLiftCount(selectedAgenda, leftoverDay, state.routines)
    }
    val nextDay = remember(plan, selectedEpochDay) { plan?.nextTrainingOnOrAfter(selectedEpochDay) }
    val featured = remember(leftoverDay, nextDay) {
        featuredSession(today = leftoverDay, next = nextDay)
    }
    val mastheadDay = leftoverDay ?: leftoverSlot?.copy(
        isRest = true,
        routineId = null,
        routineName = null,
    )
    val weekCells = remember(weekStart, state.occurrences, state.rules, names, state.loggedEpochDays) {
        WeekBoard.withLoggedDays(
            WeekBoard.forWeek(weekStart, state.occurrences, state.rules, names),
            state.loggedEpochDays,
        )
    }
    if (dayPicker && !startSheet && plannedStartPending == null && fallbackStartPending == null &&
        blockedOwner == null && !weighingIn && !starting.pending && !resetDayConfirm
    ) {
        HomeDayPicker(
            cells = weekCells, selected = selectedEpochDay, today = today,
            enabled = state.mutationEnabled && !dayMemory.resetting,
            canSelect = { viewModel.canEditStartChoice() && !viewModel.rememberedDay.snapshot.value.resetting },
            onSelect = selectDay, onDismiss = { dayPicker = false },
            recovery = recoveryContent,
        )
    }
    if (resetDayConfirm) {
        ConfirmActionDialog(
            title = "Reset the remembered day?",
            body = "Home will open on today. Your workouts and schedule stay saved.",
            confirmLabel = "Reset day",
            onConfirm = {
                resetDayConfirm = false
                dayPicker = false
                viewModel.rememberedDay.reset()
            },
            onDismiss = { resetDayConfirm = false },
            confirmEnabled = !dayMemory.saving,
            canConfirm = { !viewModel.rememberedDay.snapshot.value.saving },
        )
    }
    val dayKicker = if (selectedEpochDay == today) {
        "Today"
    } else {
        PlanDayCopy.weekdayTitle(Weekday.fromEpochDay(selectedEpochDay))
    }
    val daySummaries = state.loggedByDay[selectedEpochDay].orEmpty()
    val summariesById = remember(state.loggedByDay) {
        state.loggedByDay.values.flatten().associateBy { it.id }
    }
    val offPlanLogged = remember(selectedEpochDay, selectedAgenda, daySummaries) {
        HomeLogged.offPlanForDay(selectedEpochDay, selectedAgenda, daySummaries)
    }
    val stillOpen = remember(selectedEpochDay, today, weekStart, state.occurrences, state.rules, names) {
        if (selectedEpochDay == today) {
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
    }
    val confirmCatalog = selectedAgenda + stillOpen
    val plannedOwner = plannedStartPending
    val pendingItem = confirmCatalog.firstOrNull { item ->
        item.occurrence.id == plannedOwner?.occurrenceId && DailyAgenda.canOpenStart(item, today)
    }?.takeUnless { sessionLive }
    val heldPlanned = plannedOwner?.takeIf { owner ->
        owner.accepted && starting.occurrenceId == owner.occurrenceId && (starting.pending || starting.error != null)
    }
    val confirmFallback = fallbackStartPending?.takeIf { pending ->
        fallbackTargetMatches(state = state, day = pending.day, today = today) ||
            (pending.accepted && (starting.pending || starting.error != null))
    }
    LaunchedEffect(plannedOwner, selectedAgenda, stillOpen, sessionLive, today, state.readState, starting) {
        // Only confirmed current data may invalidate an authored choice. The last complete
        // catalog and the modal remain intact through a failed read and a held Retry.
        if (state.readState == HomeReadState.CURRENT && plannedOwner != null && pendingItem == null &&
            heldPlanned == null && plannedStartPending == plannedOwner
        ) {
            plannedStartPending = null
        }
    }
    LaunchedEffect(fallbackStartPending, state, today, starting) {
        if (fallbackStartPending == null) return@LaunchedEffect
        if (state.readState == HomeReadState.CURRENT && confirmFallback == null) {
            fallbackStartPending = null
        }
    }
    LaunchedEffect(reviewOccurrenceId, selectedAgenda, stillOpen, sessionLive, today, starting.pending) {
        if (starting.pending) return@LaunchedEffect
        val id = reviewOccurrenceId ?: return@LaunchedEffect
        if (sessionLive) {
            viewModel.onReviewOccurrenceHandled()
        } else if (confirmCatalog.any { it.occurrence.id == id && DailyAgenda.canOpenStart(it, today) } &&
            viewModel.canEditStartChoice() && viewModel.dismissStart()
        ) {
            startSheet = false
            fallbackStartPending = null
            plannedStartPending = HomePlannedStart(requestId = UUID.randomUUID().toString(), occurrenceId = id)
            viewModel.onReviewOccurrenceHandled()
        }
    }
    if (plannedOwner != null && (pendingItem != null || heldPlanned != null)) {
        val confirm = heldPlanned?.confirm ?: pendingItem?.let { item ->
            HomeToday.startConfirm(item, state.routines, today)
        }
        if (confirm != null) {
            val plannedDay = heldPlanned?.plannedEpochDay ?: pendingItem?.occurrence?.localEpochDay ?: today
            val previewRoutine = heldPlanned?.routineId?.let { id -> state.routines.firstOrNull { it.id == id } }
                ?: pendingItem?.let { HomeToday.previewRoutine(it, state.routines) }
            HomeStartPrompt(
                routine = previewRoutine,
                unit = unit,
                loadLastWeights = viewModel::previewLastWeights,
                onEditRoutine = onOpenRoutine,
                pending = starting.pending,
                labelOverridden = starting.pending || starting.error != null,
                title = confirm.heading,
                body = confirm.body,
                confirmLabel = when {
                    starting.pending -> "Starting…"
                    starting.canRetry -> "Retry start"
                    starting.error != null -> "Start unavailable"
                    else -> confirm.confirmLabel
                },
                onConfirm = {
                    if (plannedStartPending?.requestId == plannedOwner.requestId &&
                        viewModel.startState.value.attemptId == renderedStartAttemptId
                    ) {
                        val accepted = if (renderedCanRetry && renderedOccurrenceId == plannedOwner.occurrenceId) {
                            viewModel.retryStart(expectedAttemptId = renderedStartAttemptId)
                        } else viewModel.startOccurrence(plannedOwner.occurrenceId)
                        if (accepted && plannedStartPending?.requestId == plannedOwner.requestId) {
                            plannedStartPending = plannedOwner.copy(
                                accepted = true, confirm = confirm, plannedEpochDay = plannedDay,
                                routineId = previewRoutine?.id,
                            )
                        }
                    }
                },
                onDismiss = {
                    if (plannedStartPending?.requestId == plannedOwner.requestId &&
                        viewModel.blockedByInProgress.value == null &&
                        viewModel.dismissStart(expectedAttemptId = renderedStartAttemptId)
                    ) plannedStartPending = null
                },
                confirmEnabled = state.mutationEnabled && (starting.error == null || starting.canRetry),
                dismissEnabled = !starting.pending,
                canDismiss = {
                    plannedStartPending?.requestId == plannedOwner.requestId &&
                        viewModel.startState.value.attemptId == renderedStartAttemptId &&
                        viewModel.blockedByInProgress.value == null && viewModel.canDismissStart()
                },
                canConfirm = {
                    plannedStartPending?.requestId == plannedOwner.requestId &&
                        viewModel.startState.value.attemptId == renderedStartAttemptId && viewModel.canEditStartChoice()
                },
                contextContent = {
                    HomeStartDate(plannedEpochDay = plannedDay, today = today)
                    HomeStartRecovery(startStatus = starting, retryEnabled = false, onRetry = {})
                    recoveryContent?.invoke()
                },
                actionKey = renderedStartAttemptId to renderedCanRetry,
            )
        }
    }
    if (confirmFallback != null) {
        val confirm = confirmFallback.confirm ?: HomeToday.fallbackStartConfirm(confirmFallback.day, state.routines)
        HomeStartPrompt(
            routine = HomeToday.previewRoutine(confirmFallback.day, state.routines),
            unit = unit,
            loadLastWeights = viewModel::previewLastWeights,
            onEditRoutine = onOpenRoutine,
            pending = starting.pending,
            labelOverridden = starting.pending || starting.error != null,
            title = confirm.heading,
            body = confirm.body,
            confirmLabel = when {
                starting.pending -> "Starting…"
                starting.canRetry -> "Retry start"
                starting.error != null -> "Start unavailable"
                else -> confirm.confirmLabel
            },
            onConfirm = {
                // The selected cell and a new healthy plan may change behind this modal.
                // A queued callback must still belong to the exact authored target and owner.
                if (fallbackStartPending?.requestId == confirmFallback.requestId &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) {
                    val accepted = if (confirmFallback.accepted && renderedCanRetry) {
                        viewModel.retryStart(expectedAttemptId = renderedStartAttemptId)
                    } else if (fallbackTargetMatches(state = viewModel.uiState.value, day = confirmFallback.day, today = today)) {
                        viewModel.startSuggestedDay(confirmFallback.day)
                    } else false
                    if (accepted && fallbackStartPending?.requestId == confirmFallback.requestId) {
                        fallbackStartPending = confirmFallback.copy(accepted = true, confirm = confirm)
                    }
                }
            },
            onDismiss = {
                if (fallbackStartPending?.requestId == confirmFallback.requestId &&
                    viewModel.blockedByInProgress.value == null &&
                    viewModel.dismissStart(expectedAttemptId = renderedStartAttemptId)
                ) fallbackStartPending = null
            },
            confirmEnabled = state.mutationEnabled && (starting.error == null || starting.canRetry),
            dismissEnabled = !starting.pending,
            canDismiss = {
                fallbackStartPending?.requestId == confirmFallback.requestId &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId &&
                    viewModel.blockedByInProgress.value == null && viewModel.canDismissStart()
            },
            canConfirm = {
                fallbackStartPending?.requestId == confirmFallback.requestId &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId && viewModel.canEditStartChoice()
            },
            contextContent = {
                HomeStartDate(plannedEpochDay = confirmFallback.day.epochDay, today = today)
                HomeStartRecovery(startStatus = starting, retryEnabled = false, onRetry = {})
                recoveryContent?.invoke()
            },
            actionKey = renderedStartAttemptId to renderedCanRetry,
        )
    }

    if (startSheet) {
        val sheetOwner = startSheetRequestId
        HomeStartSheet(
            routines = state.routines,
            onDismiss = {
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.blockedByInProgress.value == null &&
                    viewModel.dismissStart(expectedAttemptId = renderedStartAttemptId)
                ) startSheet = false
            },
            onStartFree = {
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) viewModel.startFreeWorkout()
            },
            onStartRoutine = { routineId ->
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) viewModel.startRoutine(routineId)
            },
            onStartCardio = { type ->
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) viewModel.startCardio(type)
            },
            onStartExtra = { packId ->
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId
                ) viewModel.startAux(packId)
            },
            suggestedKit = state.suggestedExtraEquipment,
            mutationEnabled = state.mutationEnabled && starting.error == null,
            navigationEnabled = !starting.pending,
            canNavigate = {
                startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId && viewModel.canEditStartChoice()
            },
            canDismiss = {
                startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.startState.value.attemptId == renderedStartAttemptId &&
                    viewModel.blockedByInProgress.value == null && viewModel.canDismissStart()
            },
            onChoiceChangeAccepted = {
                if (startSheet && startSheetRequestId == sheetOwner &&
                    viewModel.dismissStart(expectedAttemptId = renderedStartAttemptId)
                ) {
                    // Keep this sheet's page/equipment state, but retire every callback
                    // from the old page before another one can fire in this frame.
                    startSheetRequestId = UUID.randomUUID().toString()
                    true
                } else false
            },
            recoveryContent = {
                HomeStartRecovery(
                    startStatus = starting,
                    retryEnabled = state.mutationEnabled && starting.canRetry,
                    onRetry = {
                        if (startSheet && startSheetRequestId == sheetOwner) {
                            viewModel.retryStart(expectedAttemptId = renderedStartAttemptId)
                        }
                    },
                )
                recoveryContent?.invoke()
            },
        )
    }

    val daySurface = HomeToday.surface(selectedAgenda, leftoverBelongs, stillOpen, offPlanLogged)
    val trainingToday = leftoverDay?.takeUnless { it.isRest } != null
    val openStartSheet = {
        if (viewModel.canEditStartChoice() && viewModel.dismissStart()) {
            startSheetRequestId = UUID.randomUUID().toString()
            startSheet = true
        }
    }
    val todaySheetStart = if (selectedEpochDay == today && !sessionLive && state.readState == HomeReadState.CURRENT) {
        HomeToday.sheetStart(selectedAgenda, leftoverDay, state.routines)
    } else null
    val todayStartRoutine = todaySheetStart?.let { start ->
        start.occurrenceId?.let { id -> selectedAgenda.firstOrNull { it.occurrence.id == id } }
            ?.let { HomeToday.previewRoutine(it, state.routines) }
            ?: start.leftover?.let { HomeToday.previewRoutine(it, state.routines) }
    }
    val todayDockStart = if (todaySheetStart != null && todayStartRoutine != null) {
        TodayDockStart(
            dayName = todayStartRoutine.name,
            liftCount = todayStartRoutine.exercises.size,
            onStart = {
                if (viewModel.canEditStartChoice() && viewModel.dismissStart()) {
                    val id = todaySheetStart.occurrenceId
                    val day = todaySheetStart.leftover
                    if (id != null) {
                        fallbackStartPending = null
                        plannedStartPending = HomePlannedStart(requestId = UUID.randomUUID().toString(), occurrenceId = id)
                    } else if (day != null) {
                        plannedStartPending = null
                        fallbackStartPending = HomeFallbackStart(requestId = UUID.randomUUID().toString(), day = day)
                    }
                }
            },
        )
    } else null
    val liveSessionId = state.inProgress?.id
    HomeBoardLayout(
        todayStart = todayDockStart,
        onResume = liveSessionId?.let { id -> { onResumeWorkout(id) } },
        startRank = homeStartRank(
            surface = daySurface, sessionLive = sessionLive,
            quietStart = state.missedWorkPrompt, trainingToday = trainingToday,
            hasPlan = trainingToday || nextDay != null, loggedToday = loggedSelected,
            setupComplete = state.setupComplete,
        ),
        onStartFree = openStartSheet,
    ) {
        if (state.readProblem != null) {
            item(key = "home-read-recovery") {
                GymCard { HomeReadRecovery(state = state, onRetry = { viewModel.retryRead() }) }
            }
        }
        item(key = "home-masthead") {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                HomeMasthead(
                    epochDay = selectedEpochDay,
                    // The masthead describes the DAY, never the session. The live bar owns
                    // live, and a masthead that switched to narrating the workout would be a
                    // second answer to "where is my workout" on the screen that had three.
                    headline = MastheadCopy.headline(
                        day = mastheadDay,
                        loggedOnDay = loggedSelected,
                        liftCount = liftCount,
                        hasPlan = hasPlan,
                        agenda = selectedAgenda,
                        relation = DayRelation.of(epochDay = selectedEpochDay, todayEpochDay = today),
                    ),
                    onBackToToday = MastheadCopy.backToTodayTarget(
                        selectedEpochDay = selectedEpochDay,
                        todayEpochDay = today,
                        weekStartEpochDay = weekStart,
                    )?.let { target -> { selectDay(target) } },
                    onChooseDay = { if (viewModel.canEditStartChoice() && !dayMemory.resetting) dayPicker = true },
                    dateSelectionEnabled = state.mutationEnabled && !dayMemory.resetting,
                )
                WeekStrip(
                    cells = weekCells,
                    today = today,
                    selected = selectedEpochDay,
                    onSelectDay = selectDay,
                    stripState = weekStripState,
                    flat = true,
                )
                if (dayResumed) Text("Selected day restored.", style = InstrumentType.caption, color = TextSecondary)
                if (dayMemory.saving) {
                    Text("Keeping your day…", style = InstrumentType.caption, color = TextSecondary,
                        modifier = Modifier.testTag(HomeDayTags.SAVING))
                }
                dayMemory.error?.let { message ->
                    Column(modifier = Modifier.testTag(HomeDayTags.ERROR)) {
                        Text(message, style = InstrumentType.caption, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                            TextButton(onClick = viewModel.rememberedDay::retry, enabled = !dayMemory.saving,
                                modifier = Modifier.testTag(HomeDayTags.RETRY)) {
                                Text("Try again", style = InstrumentType.bodyStrong, color = TextSecondary)
                            }
                            if (dayMemory.needsReset) {
                                TextButton(onClick = { resetDayConfirm = true }, enabled = !dayMemory.saving,
                                    modifier = Modifier.testTag(HomeDayTags.RESET)) {
                                    Text("Reset remembered day", style = InstrumentType.bodyStrong, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { message ->
            item(key = "home-action-error") {
                // Dismissable: the flow only cleared this on a later SUCCESSFUL
                // action, so a one-off failure pinned a red card to Home forever.
                key(renderedStartAttemptId, message) {
                    GymErrorBanner(
                        message = message,
                        onDismiss = {
                            if (viewModel.uiState.value.error == message &&
                                viewModel.startState.value.attemptId == renderedStartAttemptId
                            ) viewModel.dismissError()
                        },
                    )
                }
            }
        }
        skippedDay?.let { skipped ->
            item(key = "home-skip-undo") {
                if (state.mutationEnabled) {
                    GymUndoHost(
                        message = UndoHostCopy.daySkipped(skipped.title),
                        onUndo = { viewModel.undoSkipOccurrence() },
                        onDismissed = viewModel::onUndoOfferHandled,
                        modifier = Modifier.testTag(HomeTags.SKIP_UNDO),
                    )
                } else {
                    GymCard(modifier = Modifier.testTag(HomeTags.SKIP_UNDO)) {
                        Text(UndoHostCopy.daySkipped(skipped.title), style = InstrumentType.body, color = TextSecondary)
                        TextButton(onClick = {}, enabled = false) {
                            Text("Undo", style = InstrumentType.bodyStrong, color = TextSecondary)
                        }
                    }
                }
            }
        }
        if (missedWork.retained || missedWork.error != null || missedWork.receipt != null) {
            item(key = "home-retained-missed-work") { MissedWorkRetainedCard(viewModel.missedWork) }
        }
        if (state.missedWorkPrompt && !missedWork.retained) {
            item(key = "home-missed-work") {
                com.sinura.personaltrainer.ui.plan.MissedWorkCard(
                    overdueCount = state.overdueCount,
                    mutationEnabled = state.mutationEnabled && !missedWork.busy,
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
        if (updateUi.showBanner) {
            item(key = "home-debug-update") {
                DebugUpdateBanner(
                    ui = updateUi,
                    onInstall = debugUpdate::install,
                    onDismiss = debugUpdate::dismissBanner,
                )
            }
        }
        item(key = "home-day-board") {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                when (daySurface) {
                    HomeToday.Surface.AGENDA -> DailyAgendaCard(
                        items = selectedAgenda,
                        sessionLive = sessionLive,
                        onStartOccurrence = { viewModel.startOccurrence(it) },
                        onStartOccurrenceAccepted = { viewModel.startOccurrence(it) },
                        mutationEnabled = state.mutationEnabled,
                        recoveryContent = recoveryContent,
                        onOpenStartConfirm = { id ->
                            if (viewModel.canEditStartChoice() && viewModel.dismissStart()) {
                                fallbackStartPending = null
                                plannedStartPending = HomePlannedStart(requestId = UUID.randomUUID().toString(), occurrenceId = id)
                            }
                        },
                        onStartFree = openStartSheet,
                        showStartAction = false,
                        routines = state.routines,
                        kicker = dayKicker,
                        stillOpen = stillOpen,
                        today = today,
                        quietStart = state.missedWorkPrompt,
                        canEditDay = selectedEpochDay >= today,
                        onMoveOccurrence = { occurrenceId, delta ->
                            viewModel.moveDayBlock(selectedAgenda, occurrenceId, delta)
                        },
                        onSkipOccurrence = { viewModel.skipOccurrence(it) },
                        offPlanLogged = offPlanLogged,
                        summariesById = summariesById,
                        onOpenSession = onOpenSession,
                        onOpenActivity = onOpenActivity,
                    )
                    HomeToday.Surface.WEEK_FALLBACK -> ThisWeekCard(
                        day = leftoverDay,
                        nextDay = nextDay,
                        loggedToday = loggedSelected,
                        sessionLive = sessionLive,
                        hasRoutines = state.routines.isNotEmpty(),
                        lifts = leftoverLiftNames(featured, state.routines),
                        reason = nextSessionReason(featured, state.recommendations),
                        routines = state.routines,
                        quietStart = state.missedWorkPrompt,
                        setupComplete = state.setupComplete,
                        onPrimary = {
                            leftoverDay?.takeUnless { it.isRest }?.let(viewModel::startSuggestedDay)
                        },
                        onPrimaryAccepted = {
                            leftoverDay?.takeUnless { it.isRest }?.let(viewModel::startSuggestedDay) ?: false
                        },
                        mutationEnabled = state.mutationEnabled,
                        recoveryContent = recoveryContent,
                        onOpenStartConfirm = {
                            leftoverDay?.takeUnless { it.isRest }?.let { day ->
                                if (viewModel.canEditStartChoice() && viewModel.dismissStart()) {
                                    plannedStartPending = null
                                    fallbackStartPending = HomeFallbackStart(requestId = UUID.randomUUID().toString(), day = day)
                                }
                            }
                        },
                        onStartFree = openStartSheet,
                        showStartAction = false,
                        onOpenPlan = onOpenPlan,
                    )
                }
            }
        }
        if (state.bodyweightCheckInDue) {
            item(key = "home-bodyweight-check-in") {
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
            item(key = "home-lighter-week") {
                Text(
                    LighterWeek.CAPTION,
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }
        }
    }
    MissedWorkPreviewSheet(form = viewModel.missedWork,
        routines = state.routines, onReviewCurrent = viewModel::reviewMissedWork)
}

/** An explicit target and owner token prevent a selected day or refreshed plan retargeting a modal. */
private data class HomeFallbackStart(
    val requestId: String,
    val day: SuggestedTrainingDay,
    val accepted: Boolean = false,
    val confirm: StartSessionConfirm? = null,
)

private data class HomePlannedStart(
    val requestId: String,
    val occurrenceId: String,
    val accepted: Boolean = false,
    val confirm: StartSessionConfirm? = null,
    val plannedEpochDay: Long? = null,
    /** The routine the preview showed when Begin was accepted; kept through a held Retry. */
    val routineId: String? = null,
)

private val HomePlannedStartSaver = mapSaver<HomePlannedStart?>(
    save = { pending ->
        if (pending == null) emptyMap() else mapOf(
            "requestId" to pending.requestId,
            "occurrenceId" to pending.occurrenceId,
            "accepted" to pending.accepted,
            "plannedEpochDay" to pending.plannedEpochDay,
            "routineId" to pending.routineId,
        ) + saveStartConfirm(pending.confirm)
    },
    restore = { saved ->
        if (saved.isEmpty()) null else HomePlannedStart(
            requestId = saved["requestId"] as String,
            occurrenceId = saved["occurrenceId"] as String,
            accepted = saved["accepted"] as? Boolean ?: false,
            confirm = restoreStartConfirm(saved),
            plannedEpochDay = saved["plannedEpochDay"] as? Long,
            routineId = saved["routineId"] as? String,
        )
    },
)

private val HomeFallbackStartSaver = mapSaver<HomeFallbackStart?>(
    save = { pending ->
        if (pending == null) emptyMap() else with(pending.day) {
            mapOf(
                "requestId" to pending.requestId,
                "epochDay" to epochDay,
                "dayOfWeek" to dayOfWeek.name,
                "isRest" to isRest,
                "focusKind" to focusKind.name,
                "focusTitle" to focusTitle,
                "routineId" to routineId,
                "routineName" to routineName,
                "reason" to reason,
                "emphasisMuscles" to ArrayList(emphasisMuscles.map { it.name }),
                "confidence" to confidence.name,
                "slotId" to slotId,
                "accepted" to pending.accepted,
            ) + saveStartConfirm(pending.confirm)
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) null else HomeFallbackStart(
            requestId = saved["requestId"] as String,
            accepted = saved["accepted"] as? Boolean ?: false,
            confirm = restoreStartConfirm(saved),
            day = SuggestedTrainingDay(
                epochDay = saved["epochDay"] as Long,
                dayOfWeek = Weekday.valueOf(saved["dayOfWeek"] as String),
                isRest = saved["isRest"] as Boolean,
                focusKind = SessionFocusKind.valueOf(saved["focusKind"] as String),
                focusTitle = saved["focusTitle"] as String,
                routineId = saved["routineId"] as String?,
                routineName = saved["routineName"] as String?,
                reason = saved["reason"] as String,
                emphasisMuscles = (saved["emphasisMuscles"] as List<*>).map { CanonicalMuscle.valueOf(it as String) },
                confidence = ScheduleConfidence.valueOf(saved["confidence"] as String),
                slotId = saved["slotId"] as String?,
            ),
        )
    },
)

private fun saveStartConfirm(confirm: StartSessionConfirm?): Map<String, Any?> = if (confirm == null) emptyMap() else mapOf(
    "confirmHeading" to confirm.heading,
    "confirmBody" to confirm.body,
    "confirmLabel" to confirm.confirmLabel,
    "confirmLeftover" to confirm.leftover,
)

private fun restoreStartConfirm(saved: Map<String, Any?>): StartSessionConfirm? {
    val heading = saved["confirmHeading"] as? String ?: return null
    val body = saved["confirmBody"] as? String ?: return null
    val label = saved["confirmLabel"] as? String ?: return null
    return StartSessionConfirm(heading = heading, body = body, confirmLabel = label, leftover = saved["confirmLeftover"] as? Boolean ?: false)
}

@Composable
private fun HomeStartDate(plannedEpochDay: Long, today: Long) {
    Text(
        "Planned: ${DateCopy.civilDate(plannedEpochDay)}\nStarts now · ${DateCopy.civilDate(today)}",
        modifier = Modifier.testTag(HomeStartTags.DATE),
        style = InstrumentType.caption,
        color = TextSecondary,
    )
}

@Composable
internal fun HomeStartRecovery(startStatus: HomeStartState, retryEnabled: Boolean, onRetry: () -> Unit) {
    if (!startStatus.pending && startStatus.error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        if (startStatus.pending) {
            Text(
                "Starting…",
                modifier = Modifier.testTag(HomeStartTags.PENDING).semantics { liveRegion = LiveRegionMode.Polite },
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
            )
        }
        startStatus.error?.let { message ->
            FieldComplaint(
                message = message,
                modifier = Modifier.testTag(HomeStartTags.ERROR).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (retryEnabled) {
            key(startStatus.attemptId) {
                TextButton(
                    onClick = onRetry,
                    enabled = !startStatus.pending,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(HomeStartTags.RETRY),
                ) {
                    Text("Retry start", style = InstrumentType.bodyStrong, color = TextSecondary)
                }
            }
        }
    }
}

private fun fallbackTargetMatches(state: HomeUiState, day: SuggestedTrainingDay, today: Long): Boolean {
    if (day.isRest || state.sessionLive || day.epochDay in state.loggedEpochDays || state.weekPlan?.dayOn(day.epochDay) != day) {
        return false
    }
    val names = state.routines.associate { it.id to it.name }
    val agenda = DailyAgenda.forDay(day.epochDay, state.occurrences, state.rules, names)
    val stillOpen = if (day.epochDay == today) {
        DailyAgenda.stillOpen(today, state.weekStartEpochDay, state.occurrences, state.rules, names)
    } else emptyList()
    val leftover = WeekBoard.leftoverBelongsOn(day.epochDay, day, state.occurrences, state.rules)
    val offPlan = HomeLogged.offPlanForDay(day.epochDay, agenda, state.loggedByDay[day.epochDay].orEmpty())
    return HomeToday.surface(agenda, leftover, stillOpen, offPlan) == HomeToday.Surface.WEEK_FALLBACK
}

/** The same required-read explanation accompanies the board and any authored modal draft. */
@Composable
internal fun HomeReadRecovery(state: HomeUiState, onRetry: () -> Unit) {
    val problem = state.readProblem ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
        Text(
            problem,
            modifier = Modifier
                .testTag(HomeTags.READ_PROBLEM)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = InstrumentType.body,
            color = TextPrimary,
        )
        TextButton(
            onClick = onRetry,
            enabled = !state.retryPending,
            modifier = Modifier
                .heightIn(min = Metrics.touchMin)
                .testTag(HomeTags.RETRY),
        ) {
            Text(
                if (state.retryPending) "Reading again…" else "Retry",
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
            )
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
    onBackToToday: (() -> Unit)? = null,
    onChooseDay: (() -> Unit)? = null,
    dateSelectionEnabled: Boolean = true,
) {
    val dateLine = remember(epochDay) {
        DateCopy.weekdayFullDate(LocalDate.ofEpochDay(epochDay))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        // Browsing another day of the week keeps a one-tap way back (D01). The week strip's
        // Volt bar still marks today; this is the route, not a second marker. A flow row, so
        // at large text sizes the route drops to its own line instead of eliding the date.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val dateText: @Composable (Modifier) -> Unit = { textModifier -> Text(
                text = dateLine.uppercase(Locale.ENGLISH),
                style = InstrumentType.kicker,
                color = TextSecondary,
                modifier = textModifier
                    .testTag(HomeTags.DATE)
                    .semantics {
                        heading()
                        contentDescription = dateLine
                    },
            ) }
            if (onChooseDay == null) dateText(Modifier.align(Alignment.CenterVertically)) else {
                TextButton(
                    onClick = onChooseDay, enabled = dateSelectionEnabled,
                    modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(HomeDayTags.CHOOSE)
                        .semantics { contentDescription = "Choose a day in this week" },
                ) {
                    dateText(Modifier.weight(1f, fill = false))
                    Icon(OutlinedMarks.ExpandMore, contentDescription = null, tint = TextSecondary,
                        modifier = Modifier.padding(start = Metrics.space1).size(Metrics.chevron))
                }
            }
            if (onBackToToday != null) {
                TextButton(
                    onClick = onBackToToday,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .heightIn(min = Metrics.touchMin)
                        .testTag(HomeTags.BACK_TO_TODAY),
                ) {
                    Text(
                        text = MastheadCopy.BACK_TO_TODAY,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = if (epochDay == LocalTodayEpochDay.current) "Your day." else
                "${com.sinura.personaltrainer.domain.Weekday.fromEpochDay(epochDay).titleLabel()}.",
            modifier = Modifier.semantics { heading() },
            style = InstrumentType.display,
            color = TextPrimary,
        )
        Text(text = headline, style = InstrumentType.caption, color = TextSecondary)
    }
}

object HomeTags {
    const val BOARD = "home-board"
    const val TODAY_START = "today_start_cta"
    const val TODAY_RESUME = "today_resume_cta"
    const val DATE = "home-selected-date"
    const val READ_PROBLEM = "home-read-problem"
    const val RETRY = "home-read-retry"
    const val BACK_TO_TODAY = "home-back-to-today"
    const val START = "home-start"
    const val FREE = "home-free-start"
    const val BODYWEIGHT_CHECK_IN = "home-bodyweight-check-in"
    const val BODYWEIGHT_SAVING = "home-bodyweight-saving"
    const val BODYWEIGHT_SAVE_ERROR = "home-bodyweight-save-error"
    const val SKIP_UNDO = "home-skip-undo"
    const val STILL_OPEN = "home-still-open"
    const val SESSION = "home-session-start"

    fun agendaRow(occurrenceId: String): String = "home-agenda-$occurrenceId"

    fun skipRow(occurrenceId: String): String = "home-skip-$occurrenceId"

    fun loggedSession(sessionId: String): String = "home-logged-$sessionId"
}

private const val NO_VALUE = "—"
