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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.shortLabel
import com.sinura.personaltrainer.domain.CustomWeekDayMark
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.OnboardingPreviewCopy
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.ExercisePickerSheet
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.VoltDim
import java.time.DayOfWeek

@Composable
fun CustomWeekScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    preferredDays: Set<DayOfWeek> = emptySet(),
    answers: OnboardingAnswers? = null,
    pendingWeightUnit: WeightUnit? = null,
    viewModel: CustomWeekViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFullWeek by rememberSaveable { mutableStateOf(false) }
    val restDays = CustomWeekPolicy.restDayCount(state.days)
    val restCaption = CustomWeekPolicy.restCaption(restDays)

    LaunchedEffect(preferredDays, answers, pendingWeightUnit) {
        viewModel.seedFromGuided(preferredDays, answers, pendingWeightUnit)
    }
    LaunchedEffect(finished) {
        if (finished) onFinished()
    }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pit)
                    .padding(start = Metrics.space2, end = Metrics.space2, bottom = Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                    )
                }
                Kicker("Your week", modifier = Modifier.weight(1f))
            }
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
                onSelect = viewModel::selectDay,
            )
            state.error?.let { GymErrorBanner(it) }

            val lifts = state.selectedLifts
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Metrics.space4),
                verticalArrangement = Arrangement.spacedBy(Metrics.space1),
            ) {
                if (lifts.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No lifts on ${state.selectedDay.shortLabel()}",
                            body = "Add every exercise for this day, then move to the next.",
                            actionLabel = "Add lifts",
                            onAction = { viewModel.setPickerVisible(true) },
                            compact = true,
                            modifier = Modifier.padding(top = Metrics.space4),
                        )
                    }
                } else {
                    item { Kicker("${state.selectedDay.shortLabel()} · ${lifts.size}") }
                    itemsIndexed(lifts, key = { _, item -> item.id }) { index, item ->
                        CompactLiftRow(
                            exercise = item.exercise,
                            sets = item.targetSets,
                            reps = item.targetReps,
                        restSeconds = item.restSeconds,
                        targetWeightKg = item.targetWeightKg,
                        canMoveUp = index > 0,
                            canMoveDown = index < lifts.lastIndex,
                            expanded = expandedId == item.id,
                            onToggle = { expandedId = if (expandedId == item.id) null else item.id },
                            onMoveUp = { viewModel.moveLift(item.id, -1) },
                            onMoveDown = { viewModel.moveLift(item.id, 1) },
                            onRemove = { viewModel.removeLift(item.id) },
                            onSwap = null,
                            onStageTargets = { sets, reps, rest, kg ->
                                viewModel.stageTargets(item.id, sets, reps, rest, kg)
                            },
                            onCommitTargets = { },
                            modifier = Modifier.animateItem(),
                            rowKey = item.id,
                        )
                    }
                    item {
                        SecondaryGymButton(
                            text = "Add lifts",
                            onClick = { viewModel.setPickerVisible(true) },
                            modifier = Modifier.padding(top = Metrics.space2),
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
                    enabled = state.canConfirm && !state.applying,
                )
                if (restCaption != null && state.trainingDays > 0) {
                    Text(restCaption, style = InstrumentType.caption, color = TextTertiary)
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
            query = state.searchQuery,
            results = state.searchResults,
            onQueryChange = viewModel::onSearchQuery,
            onSelect = { },
            onCreate = viewModel::createAndSelect,
            onDismiss = { viewModel.setPickerVisible(false) },
            title = "Add lifts",
            selectedIds = state.pendingAddIds,
            onToggle = viewModel::togglePendingAdd,
            onConfirmAdd = viewModel::confirmPendingAdd,
        )
    }
}

@Composable
private fun WeekDayStrip(
    weekStart: DayOfWeek,
    selected: DayOfWeek,
    filled: Set<DayOfWeek>,
    preferred: Set<DayOfWeek>,
    onSelect: (DayOfWeek) -> Unit,
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
                Box(
                    modifier = Modifier
                        .padding(top = Metrics.space1)
                        .clip(shape)
                        .background(
                            when (mark) {
                                CustomWeekDayMark.FILLED -> Volt
                                CustomWeekDayMark.PREFERRED -> VoltDim
                                CustomWeekDayMark.EMPTY -> Hairline
                            },
                        )
                        .padding(horizontal = Metrics.space2, vertical = Metrics.space1),
                ) {
                    Text(
                        if (mark == CustomWeekDayMark.EMPTY) " " else "•",
                        style = InstrumentType.caption,
                        color = if (mark == CustomWeekDayMark.FILLED) Pit else TextTertiary,
                    )
                }
            }
        }
    }
}
