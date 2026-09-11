package com.sinura.personaltrainer.ui.workout


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.PersonalRecordCopy
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.GymStatusBanner
import com.sinura.personaltrainer.ui.components.LeaveWorkoutDialog
import com.sinura.personaltrainer.ui.components.NotesBlock
import com.sinura.personaltrainer.ui.components.PersonalRecordBanner
import com.sinura.personaltrainer.ui.components.RestDock
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import kotlinx.coroutines.delay

/** Stable semantics for the critical device journey; copy remains free to improve. */
object WorkoutTestTags {
    const val CONTENT = "workout-content"
    const val LOG_SET = "workout-log-set"
    const val FINISH = "workout-finish"
    const val NOTIF_RECOVERY = "workout-notif-recovery"
    const val CURRENT_LIFT = "workout-current-lift"
    const val SET_ENTRY = "workout-set-entry"
    const val REST_BAR = "workout-rest-bar"
    const val REST_IDLE = "workout-rest-idle"
    const val REST_BATTERY = "workout-rest-battery"
    const val MICRO_REC = "workout-micro-rec"
    const val MICRO_REC_APPLY = "workout-micro-rec-apply"
    const val MICRO_REC_WHY = "workout-micro-rec-why"
    const val NEXT = "workout-next"
    const val ADD_SET = "workout-add-set"
    const val LAST_TIME = "workout-last-time"
    fun liftCard(exerciseId: String) = "workout-lift-card-$exerciseId"
    fun lastTimeChip(setId: String) = "workout-last-time-$setId"
}

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    onOpenRest: (String) -> Unit = {},
    viewModel: ActiveWorkoutViewModel = viewModel(),
    restNotificationsEnabledOverride: Boolean? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    val microRec by viewModel.microRec.collectAsStateWithLifecycle()
    val extraSetRequested by viewModel.extraSetRequested.collectAsStateWithLifecycle()
    val windowDp = LocalWindowInfo.current.containerDpSize
    val landscape = LandscapeChrome.isLandscape(
        widthDp = windowDp.width.value.roundToInt(),
        heightDp = windowDp.height.value.roundToInt(),
    )
    val exitRequested by viewModel.exitRequested.collectAsStateWithLifecycle()
    val personalRecord by viewModel.personalRecord.collectAsStateWithLifecycle()
    val deletedSet by viewModel.deletedSet.collectAsStateWithLifecycle()
    val pendingAdvance by viewModel.pendingAdvance.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveLift by rememberSaveable { mutableStateOf(false) }
    val restNotificationsEnabled = restNotificationsEnabledOverride
        ?: rememberRestNotificationsEnabled()
    val session = state.session
    val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
    val afterWarmup = selected?.let { lift ->
        session?.setsFor(lift.exercise.id)?.maxByOrNull { it.completedAt }?.isWarmup == true
    } == true
    val advance = remember(session, state.selectedExerciseId, extraSetRequested, state.editingSetId) {
        WorkoutAdvance.forSelection(
            session = session,
            selectedExerciseId = state.selectedExerciseId,
            wantAnother = extraSetRequested,
            editing = state.editingSetId != null,
        )
    }
    val workingLogged = advance.workingLogged
    val liftComplete = advance.liftComplete
    val nextExerciseId = advance.nextExerciseId
    val showNext = advance.showNext
    // Keyed on the session, not recomputed per frame: the header below it redraws every second
    // as the elapsed clock ticks, and this walks every set of the workout.
    val sessionWork = remember(session) { session?.work() ?: SetWork.NONE }
    val unit = LocalWeightUnit.current
    val view = LocalView.current
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Exit is state, not a callback captured into a coroutine: finishing writes to the database
    // first, and if the Activity is recreated in that window the captured NavController is dead
    // and the user is left staring at a workout that no longer exists. Acks BEFORE navigating —
    // for a pop, a duplicate would eat an extra screen, which is worse than the narrow window
    // where a recomposition between ack and pop drops the request.
    LaunchedEffect(exitRequested) {
        val reason = exitRequested ?: return@LaunchedEffect
        viewModel.onExitHandled()
        when (reason) {
            is WorkoutExit.Finished -> onFinished(reason.sessionId)
            WorkoutExit.Discarded -> onExit()
        }
    }

    // One leave path: system back behaves exactly like the top-bar X. Previously back popped
    // silently, skipping the notes flush in persistDraftForExit, so the two exits from the
    // same screen did different things. Only armed while a session is actually loaded, so
    // back still works normally on the loading and missing states.
    BackHandler(enabled = state.session != null) { confirmLeave = true }

    // Always composed, unlike the bottom bar. An error raised while no lift is selected —
    // a failed create from the picker in an empty free workout — previously had no reader at
    // all: it was written to state and rendered nowhere.
    val logBarVisible = session != null && selected != null

    // The record's haptics and its acknowledgement live here rather than inside the banner:
    // the banner is a list item, and logging the set that breaks a record also scrolls the
    // list, so the item is disposed within a second. This screen is always composed, so the
    // beats fire once and the record is always cleared.
    LaunchedEffect(personalRecord) {
        if (personalRecord == null) return@LaunchedEffect
        Haptics.celebrate(view)
        delay(PERSONAL_RECORD_DWELL_MS)
        viewModel.onPersonalRecordShown()
    }

    Scaffold(
        snackbarHost = {
            val errorBanner = state.error != null && !logBarVisible
            if (errorBanner || deletedSet != null || pendingAdvance != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Metrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                ) {
                    if (errorBanner) {
                        GymErrorBanner(message = state.error!!)
                    }
                    // The dwell, the way out and the fade are the banner's own: it waits
                    // Motion.STATUS_DWELL_MS, calls onDismissed only if the action was not
                    // taken, and animates through instrumentTween, which snaps under reduced
                    // motion. So the offer to move on is a banner, not a bespoke timer.
                    pendingAdvance?.let { advance ->
                        GymStatusBanner(
                            message = "${advance.finishedName} done · next ${advance.nextName}",
                            actionLabel = "Stay here",
                            onAction = { viewModel.stayOnCurrentExercise() },
                            onDismissed = { viewModel.advanceNow() },
                        )
                    }
                    deletedSet?.let { removed ->
                        GymStatusBanner(
                            message = "Set deleted · " + SetCopy.setLine(
                                removed.weightKg,
                                removed.reps,
                                LoadClass.of(selected?.exercise?.loadType),
                                unit,
                            ),
                            actionLabel = "Undo",
                            onAction = { viewModel.undoDeleteSet() },
                            onDismissed = { viewModel.onUndoOfferHandled() },
                        )
                    }
                }
            }
        },
        topBar = {
            WorkoutHeader(
                routineName = session?.routineName ?: "Workout",
                startedAt = session?.startedAt,
                workingSets = session?.sets?.count { !it.isWarmup } ?: 0,
                work = sessionWork,
                unit = unit,
                canFinish = session != null && session.sets.isNotEmpty(),
                compact = LandscapeChrome.compactHeader(landscape),
                onExit = { confirmLeave = true },
                onFinish = {
                    Haptics.commit(view)
                    viewModel.finishWorkout()
                },
            )
        },
        bottomBar = {
            // G-02: rest Start/Skip and Log set share the lower dock so a
            // one-handed thumb can hit both. Finish stays in the header —
            // it is not a mid-set act. The list above is readout and entry.
            val showRest = session != null
            if (showRest || logBarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (logBarVisible) Modifier
                            else Modifier.navigationBarsPadding(),
                        ),
                ) {
                    if (showRest) {
                        RestDock(
                            remainingSeconds = rest.remainingSeconds,
                            totalSeconds = rest.totalSeconds,
                            running = rest.running,
                            completedTimerId = rest.completedTimerId,
                            hideWhenIdle = LandscapeChrome.hideIdleRest(landscape),
                            afterWarmup = afterWarmup,
                            batteryHint = rest.batteryHint,
                            onDismissBatteryHint = viewModel::acknowledgeRestBatteryHint,
                            onSkip = viewModel::skipRest,
                            onStart = viewModel::startSelectedRest,
                            onOpenRest = { session.id.let(onOpenRest) },
                        )
                    }
                    if (logBarVisible) {
                        LogBar(
                            editing = state.editingSetId != null,
                            logging = state.logging,
                            error = state.error,
                            draftLabel = SetCopy.setLine(state.draft.weightKg, state.draft.reps, LoadClass.of(selected?.exercise?.loadType), unit),
                            microRec = microRec.takeUnless {
                                showNext || LandscapeChrome.foldMicroRecIntoCard(landscape)
                            },
                            loadClass = LoadClass.of(selected?.exercise?.loadType),
                            unit = unit,
                            showNext = showNext,
                            onLog = {
                                Haptics.commit(view)
                                viewModel.logSet()
                            },
                            onNext = {
                                nextExerciseId?.let(viewModel::advanceToNextLift)
                            },
                            onCancelEdit = viewModel::cancelEdit,
                            onApplyMicroRec = viewModel::applyMicroRec,
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ScreenLoading(modifier = Modifier.padding(padding))
            }

            session == null -> {
                // Terminal state, reached when the session flow has emitted null for a real
                // id: the workout was discarded, restored over, or the id came from a stale
                // notification. It must offer a way out — this used to be an unreachable
                // branch behind a spinner that never resolved.
                EmptyState(
                    title = "Workout missing",
                    body = "This session was finished, discarded, or replaced by a restore. " +
                        "Nothing was lost from your history.",
                    actionLabel = "Back to home",
                    onAction = onExit,
                    compact = true,
                    modifier = Modifier.padding(padding).padding(Metrics.gutter),
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Notification recovery stays above the list: it is a
                    // banner, not a gym-floor act. Rest sits in the lower dock.
                    if (!restNotificationsEnabled) {
                        RestNotificationRecoveryRow()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(WorkoutTestTags.CONTENT),
                        contentPadding = PaddingValues(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space3,
                            bottom = Metrics.space7,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        personalRecord?.let { moment ->
                            item(key = "pr-moment") {
                                PersonalRecordBanner(
                                    headline = PersonalRecordCopy.headline(moment.kinds),
                                    detail = "${moment.exerciseName.ifBlank { "This lift" }} · " +
                                        SetCopy.setLine(moment.weightKg, moment.reps, LoadClass.of(selected?.exercise?.loadType), unit),
                                    onDismiss = viewModel::onPersonalRecordShown,
                                )
                            }
                        }
                        if (!session.hasLifts()) {
                            item(key = "empty-lifts") {
                                EmptyState(
                                    title = "Add a lift",
                                    body = SessionOrderCopy.EMPTY_SESSION_BODY,
                                    actionLabel = "Add a lift",
                                    onAction = { viewModel.setPickerVisible(true) },
                                    compact = true,
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = session.exercises,
                                key = { _, lift -> "lift-${lift.id}" },
                            ) { index, lift ->
                                val isSelected = lift.exercise.id == state.selectedExerciseId
                                val logged = session.setsFor(lift.exercise.id)
                                WorkoutLiftCard(
                                    card = WorkoutLiftCardState(
                                        lift = lift,
                                        number = index + 1,
                                        selected = isSelected,
                                        loggedSets = logged,
                                        latestSetId = WorkoutAdvance.latestSetId(logged),
                                        editingSetId = state.editingSetId.takeIf { isSelected },
                                        lastPerformance = state.lastPerformance.takeIf { isSelected },
                                        hint = state.hint.takeIf { isSelected },
                                        draftWeightKg = state.draft.weightKg,
                                        draftReps = state.draft.reps,
                                        draftWarmup = state.draft.isWarmup,
                                        draftRpe = state.draft.rpe,
                                        microRec = microRec.takeIf { isSelected },
                                        unit = unit,
                                        canEdit = logged.isEmpty(),
                                        showAddSet = isSelected &&
                                            WorkoutAdvance.cardOffersAnotherSet(logged, lift.targetSets),
                                    ),
                                    events = WorkoutLiftCardEvents(
                                        onSelect = { viewModel.selectExercise(lift.exercise.id) },
                                        onSwap = viewModel::requestSwap,
                                        onRemove = { confirmRemoveLift = true },
                                        onWeightKgChange = viewModel::setWeight,
                                        onRepsAdjust = viewModel::adjustReps,
                                        onRepsChange = viewModel::setReps,
                                        onApplyLastTime = viewModel::applyLastTimeSet,
                                        onWarmup = viewModel::setWarmup,
                                        onRpe = viewModel::setRpe,
                                        onApplySuggested = viewModel::applySuggestedWeight,
                                        onEditSet = viewModel::editSet,
                                        onDeleteSet = viewModel::deleteSet,
                                        onAddSet = viewModel::requestExtraSet,
                                    ),
                                )
                            }
                            item(key = "add-lift") {
                                SecondaryGymButton(
                                    text = "Add a lift",
                                    onClick = { viewModel.setPickerVisible(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item(key = "notes") {
                            NotesBlock(
                                notes = state.notes,
                                expanded = notesOpen,
                                onToggle = { notesOpen = !notesOpen },
                                onChange = viewModel::setNotes,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showExercisePicker) {
        ExercisePickerSheet(
            state = ExercisePickerState(
                query = state.searchQuery,
                results = state.searchResults,
                title = if (state.swapping) "Swap lift" else "Add a lift",
                mode = if (state.swapping) ExercisePickerMode.SWAP else ExercisePickerMode.SINGLE_ADD,
                suggestion = state.suggestion,
                suggestionReason = state.suggestionReason,
                siblings = state.swapSiblings,
            ),
            onEvent = { event ->
                when (event) {
                    is ExercisePickerEvent.QueryChanged -> viewModel.onSearchQuery(event.query)
                    is ExercisePickerEvent.Selected -> viewModel.addExercise(event.exercise)
                    is ExercisePickerEvent.Created ->
                        viewModel.createAndAddExercise(event.name, event.muscleGroup, event.loadType)
                    is ExercisePickerEvent.Toggled -> Unit
                    ExercisePickerEvent.Dismissed -> viewModel.setPickerVisible(false)
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissError()
                }
            },
        )
    }

    if (confirmLeave) {
        // X, system back, and Finish-disabled empty sessions all land here. Keep is the
        // gym-floor leave; Discard still opens the named confirm below.
        LeaveWorkoutDialog(
            onKeepAndExit = {
                confirmLeave = false
                viewModel.persistDraftForExit()
                onExit()
            },
            onStay = { confirmLeave = false },
            onDiscardInstead = {
                confirmLeave = false
                confirmDiscard = true
            },
            onDismiss = { confirmLeave = false },
        )
    }

    if (confirmRemoveLift) {
        val name = selected?.exercise?.name ?: "this lift"
        ConfirmActionDialog(
            title = "Remove $name?",
            body = "It comes out of this session's plan. Nothing logged is affected — this lift " +
                "has no sets yet.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmRemoveLift = false
                viewModel.removeSelectedLift()
            },
            onDismiss = { confirmRemoveLift = false },
        )
    }

    if (confirmDiscard) {
        val loggedSets = session?.sets?.size ?: 0
        ConfirmActionDialog(
            title = "Discard this workout?",
            body = if (loggedSets > 0) {
                "This deletes the session and its $loggedSets logged " +
                    (if (loggedSets == 1) "set" else "sets") + ". This cannot be undone."
            } else {
                "This deletes the session. This cannot be undone."
            },
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discardWorkout()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}


private const val PERSONAL_RECORD_DWELL_MS = 6_000L
