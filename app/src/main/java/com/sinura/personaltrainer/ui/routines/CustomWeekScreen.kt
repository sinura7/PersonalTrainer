package com.sinura.personaltrainer.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CustomWeekDayMark
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.ExercisePickerEvent
import com.sinura.personaltrainer.domain.ExercisePickerMode
import com.sinura.personaltrainer.domain.ExercisePickerState
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.OnboardingPreviewCopy
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import com.sinura.personaltrainer.domain.Weekday

@Composable
fun CustomWeekScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    preferredDays: Set<Weekday> = emptySet(),
    answers: OnboardingAnswers? = null,
    pendingWeightUnit: WeightUnit? = null,
    viewModel: CustomWeekViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFullWeek by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val restDays = CustomWeekPolicy.restDayCount(state.days)
    val restCaption = CustomWeekPolicy.restCaption(restDays)

    LaunchedEffect(preferredDays, answers, pendingWeightUnit) {
        viewModel.seedFromGuided(preferredDays, answers, pendingWeightUnit)
    }
    LaunchedEffect(finished) {
        if (finished) onFinished()
    }
    // A five-day draft lives only in this ViewModel; a back-swipe used to destroy
    // it with no confirmation while the composer next door guards the same case.
    val leave = {
        if (state.days.values.any { it.isNotEmpty() } && !finished) {
            confirmLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = leave)

    if (confirmLeave) {
        ConfirmActionDialog(
            title = "Leave the week builder?",
            body = "The days you built here are not saved. Leaving discards them.",
            confirmLabel = "Discard and leave",
            destructive = true,
            onConfirm = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Your week",
                onBack = leave,
                backTag = CustomWeekTags.BACK,
                kickerTitle = true,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text("Build the week", style = InstrumentType.display, color = TextPrimary)
            Text(
                "Pick a day, add the lifts, set the work. Confirm when the week is yours.",
                style = InstrumentType.body,
                color = TextSecondary,
            )
            WeekDayStrip(
                weekStart = state.weekStart,
                selected = state.selectedDay,
                filled = state.days.filter { it.value.isNotEmpty() }.keys,
                preferred = state.preferredDays,
                onSelect = { day ->
                    expandedId = null
                    viewModel.selectDay(day)
                },
            )
            if (!state.showPicker) {
                state.error?.let { GymErrorBanner(it, onDismiss = viewModel::dismissError) }
            }

            val lifts = state.selectedLifts
            LaunchedEffect(state.selectedDay, lifts.map { it.id }) {
                if (expandedId != null && lifts.none { it.id == expandedId }) {
                    expandedId = null
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Metrics.space4),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                if (lifts.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No lifts on ${state.selectedDay.shortLabel()}",
                            body = SessionOrderCopy.EMPTY_WEEK_BODY,
                            actionLabel = "Add lifts",
                            onAction = { viewModel.setPickerVisible(true) },
                            compact = true,
                            actionEnabled = !state.applying,
                            actionTag = CustomWeekTags.ADD_LIFTS,
                            modifier = Modifier.padding(top = Metrics.space4),
                        )
                    }
                } else {
                    item { Kicker(SessionOrderCopy.daySection(state.selectedDay.shortLabel(), lifts.size)) }
                    item {
                        SessionLiftStrip(
                            lifts = lifts.map { item ->
                                SessionLiftItem(
                                    id = item.id,
                                    exercise = item.exercise,
                                    sets = item.targetSets,
                                    reps = item.targetReps,
                                    restSeconds = item.restSeconds,
                                    targetWeightKg = item.targetWeightKg,
                                )
                            },
                            selectedId = expandedId,
                            onSelect = { id ->
                                expandedId = if (expandedId == id) null else id
                            },
                            onMoveEarlier = { id -> viewModel.moveLift(id, -1) },
                            onMoveLater = { id -> viewModel.moveLift(id, 1) },
                            onRemove = { id ->
                                if (expandedId == id) expandedId = null
                                viewModel.removeLift(id)
                            },
                            onStageTargets = { id, sets, reps, rest, kg ->
                                viewModel.stageTargets(id, sets, reps, rest, kg)
                            },
                            onCommitTargets = { },
                        )
                    }
                    item {
                        SecondaryGymButton(
                            text = "Add lifts",
                            onClick = { viewModel.setPickerVisible(true) },
                            modifier = Modifier
                                .padding(top = Metrics.space2)
                                .testTag(CustomWeekTags.ADD_LIFTS),
                            enabled = !state.applying,
                            height = Metrics.touchMin,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                PrimaryGymButton(
                    text = if (state.applying) {
                        "Saving…"
                    } else if (state.trainingDays == 0) {
                        "Add a lift to confirm"
                    } else {
                        CustomWeekPolicy.confirmCta(state.trainingDays)
                    },
                    onClick = {
                        if (CustomWeekPolicy.isFullWeek(state.days)) {
                            pendingFullWeek = true
                        } else {
                            viewModel.confirm()
                        }
                    },
                    modifier = Modifier.testTag(CustomWeekTags.CONFIRM),
                    enabled = state.canConfirm && !state.applying,
                )
                if (restCaption != null && state.trainingDays > 0) {
                    Text(restCaption, style = InstrumentType.caption, color = TextSecondary)
                }
            }
        }
    }

    if (pendingFullWeek) {
        ConfirmActionDialog(
            title = OnboardingPreviewCopy.FULL_WEEK_TITLE,
            body = OnboardingPreviewCopy.FULL_WEEK_BODY,
            confirmLabel = OnboardingPreviewCopy.FULL_WEEK_CONFIRM_WEEK,
            onConfirm = {
                pendingFullWeek = false
                viewModel.confirm()
            },
            onDismiss = { pendingFullWeek = false },
        )
    }

    if (state.showPicker) {
        ExercisePickerSheet(
            state = ExercisePickerState(
                query = state.searchQuery,
                results = state.searchResults,
                title = "Add lifts",
                mode = ExercisePickerMode.MULTI_ADD,
                selectedOrder = state.pendingAddIds,
                catalog = state.catalog,
                error = state.error,
            ),
            onEvent = { event ->
                when (event) {
                    is ExercisePickerEvent.QueryChanged -> viewModel.onSearchQuery(event.query)
                    is ExercisePickerEvent.Selected -> Unit
                    is ExercisePickerEvent.Created ->
                        viewModel.createAndSelect(event.name, event.muscleGroup)
                    is ExercisePickerEvent.Toggled -> viewModel.togglePendingAdd(event.exercise)
                    ExercisePickerEvent.Confirmed -> viewModel.confirmPendingAdd()
                    ExercisePickerEvent.Dismissed -> viewModel.setPickerVisible(false)
                    ExercisePickerEvent.ErrorDismissed -> viewModel.dismissError()
                }
            },
        )
    }
}

@Composable
private fun WeekDayStrip(
    weekStart: Weekday,
    selected: Weekday,
    filled: Set<Weekday>,
    preferred: Set<Weekday>,
    onSelect: (Weekday) -> Unit,
) {
    val ordered = (0 until 7).map { weekStart.plus(it.toLong()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        ordered.forEach { day ->
            val on = day == selected
            val mark = CustomWeekPolicy.dayMark(day, filled, preferred)
            val shape = RoundedCornerShape(Radius.xs)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) VoltDim else Surface2)
                    .border(
                        if (on) Metrics.emphasisBorder else Metrics.hairline,
                        if (on) Volt else Hairline,
                        shape,
                    )
                    .heightIn(min = Metrics.touchMin)
                    .clickable { onSelect(day) }
                    .padding(vertical = Metrics.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.shortLabel().take(2),
                    style = InstrumentType.title,
                    color = if (on) Volt else TextPrimary,
                )
                when (mark) {
                    CustomWeekDayMark.FILLED -> Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(top = Metrics.space1)
                            .size(CUSTOM_WEEK_MARK),
                    )
                    CustomWeekDayMark.PREFERRED -> Box(
                        modifier = Modifier
                            .padding(top = Metrics.space1)
                            .size(CUSTOM_WEEK_MARK)
                            .clip(CircleShape)
                            .background(Hairline),
                    )
                    CustomWeekDayMark.EMPTY -> Box(
                        modifier = Modifier
                            .padding(top = Metrics.space1)
                            .size(CUSTOM_WEEK_MARK),
                    )
                }
            }
        }
    }
}

object CustomWeekTags {
    const val BACK = "custom-week-back"
    const val ADD_LIFTS = "custom-week-add-lifts"
    const val CONFIRM = "custom-week-confirm"
}

private val CUSTOM_WEEK_MARK = 12.dp
