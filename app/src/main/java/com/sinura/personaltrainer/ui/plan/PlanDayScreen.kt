package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One weekday as a schedule page. Add and delete blocks here. Start lives
 * on Home (ADR-015).
 */
@Composable
fun PlanDayScreen(
    epochDay: Long,
    startInAdd: Boolean,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    viewModel: PlanViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateToEditor by viewModel.navigateToEditor.collectAsStateWithLifecycle()
    val today = todayEpochDay()
    val isPast = epochDay < today
    val weekday = Weekday.fromEpochDay(epochDay)
    val day = state.week?.days?.firstOrNull { it.epochDay == epochDay }
    val pinned = day?.isRest == false
    val occurrences = viewModel.agendaFor(epochDay)
    var picking by rememberSaveable(epochDay) {
        mutableStateOf(if (startInAdd && !isPast) DayPicker.KIND else DayPicker.NONE)
    }

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
        PlanDayHeader(
            title = PlanDayCopy.weekdayTitle(weekday),
            dateCaption = DATE_CAPTION.format(LocalDate.ofEpochDay(epochDay)),
            onBack = onBack,
        )
        when {
            state.isLoading -> ScreenLoading()
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Metrics.gutter)
                        .padding(bottom = Metrics.space4),
                    verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                ) {
                    state.error?.let { GymErrorBanner(it) }
                    if (isPast) {
                        Text(
                            PlanDayCopy.PAST,
                            style = InstrumentType.caption,
                            color = TextSecondary,
                        )
                    }
                    if (occurrences.isEmpty() && picking == DayPicker.NONE) {
                        EmptyState(
                            title = PlanDayCopy.EMPTY,
                            body = PlanDayCopy.EMPTY_BODY,
                            compact = true,
                        )
                    } else if (occurrences.isNotEmpty()) {
                        SessionBlocks(
                            occurrences = occurrences,
                            routines = state.routines,
                            isPast = isPast,
                            onOpenRoutine = onOpenRoutine,
                            onRemove = { ruleId ->
                                viewModel.deleteSession(epochDay, ruleId)
                            },
                            onMove = { occurrenceId, delta ->
                                viewModel.moveDayBlock(occurrences, occurrenceId, delta)
                            },
                        )
                    }
                    if (!isPast && picking != DayPicker.NONE) {
                        AddPicker(
                            picking = picking,
                            pinned = pinned,
                            occurrences = occurrences,
                            routines = state.routines,
                            onPickKind = { picking = it },
                            onCancel = { picking = DayPicker.NONE },
                            onAddWorkout = { routineId ->
                                picking = DayPicker.NONE
                                if (pinned) {
                                    viewModel.addLaterSession(epochDay, routineId)
                                } else {
                                    viewModel.pinRoutine(epochDay, routineId)
                                }
                            },
                            onNewWorkout = {
                                picking = DayPicker.NONE
                                if (pinned) {
                                    viewModel.composeLaterSession(epochDay)
                                } else {
                                    viewModel.buildDay(epochDay)
                                }
                            },
                            onAddCardio = { type ->
                                picking = DayPicker.NONE
                                viewModel.addCardio(epochDay, type)
                            },
                            onAddAux = { packId ->
                                picking = DayPicker.NONE
                                viewModel.addAuxiliary(epochDay, packId)
                            },
                        )
                    }
                }
                if (!isPast && picking == DayPicker.NONE) {
                    PrimaryGymButton(
                        text = PlanDayCopy.ADD_SESSION,
                        onClick = { picking = DayPicker.KIND },
                        modifier = Modifier
                            .padding(horizontal = Metrics.gutter)
                            .padding(bottom = Metrics.space5)
                            .testTag(PlanDayTags.ADD)
                            .semantics { contentDescription = PlanDayCopy.ADD_SESSION },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanDayHeader(
    title: String,
    dateCaption: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(start = Metrics.space2, end = Metrics.gutter, bottom = Metrics.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag(PlanDayTags.BACK),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = InstrumentType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dateCaption,
                style = InstrumentType.caption,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionBlocks(
    occurrences: List<AgendaItem>,
    routines: List<Routine>,
    isPast: Boolean,
    onOpenRoutine: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    GroupedList {
        occurrences.forEachIndexed { index, item ->
            if (index > 0) HairlineDivider()
            val ruleId = item.rule?.id
            val routineId = item.rule?.routineId
            val names = sessionLiftNames(routineId, routines)
            val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
            val canEdit = routineId != null &&
                modality == ScheduleModality.STRENGTH
            Column(
                modifier = Modifier.padding(bottom = if (!isPast && ruleId != null) Metrics.space3 else 0.dp),
            ) {
                InstrumentRow(
                    title = item.title,
                    subtitle = SessionOrderCopy.occurrenceLine(
                        item.occurrence.status,
                        names,
                        modality,
                    ),
                    onClick = if (canEdit) {
                        { onOpenRoutine(routineId) }
                    } else {
                        null
                    },
                    trailing = if (!isPast && ruleId != null) {
                        {
                            TextButton(
                                onClick = { onRemove(ruleId) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.heightIn(min = Metrics.touchMin),
                            ) {
                                Text(
                                    PlanDayCopy.REMOVE,
                                    style = InstrumentType.bodyStrong,
                                    color = TextSecondary,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (!isPast && occurrences.size > 1) {
                    ReorderRow(
                        title = item.title,
                        occurrenceId = item.occurrence.id,
                        index = index,
                        lastIndex = occurrences.lastIndex,
                        onMove = onMove,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReorderRow(
    title: String,
    occurrenceId: String,
    index: Int,
    lastIndex: Int,
    onMove: (String, Int) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = Metrics.space4),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        if (index > 0) {
            TextButton(
                onClick = { onMove(occurrenceId, -1) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(PlanDayTags.moveUp(occurrenceId))
                    .semantics { contentDescription = PlanDayCopy.moveUp(title) },
            ) {
                Text(
                    PlanDayCopy.UP,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
        if (index < lastIndex) {
            TextButton(
                onClick = { onMove(occurrenceId, 1) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .heightIn(min = Metrics.touchMin)
                    .testTag(PlanDayTags.moveDown(occurrenceId))
                    .semantics { contentDescription = PlanDayCopy.moveDown(title) },
            ) {
                Text(
                    PlanDayCopy.DOWN,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPicker(
    picking: DayPicker,
    pinned: Boolean,
    occurrences: List<AgendaItem>,
    routines: List<Routine>,
    onPickKind: (DayPicker) -> Unit,
    onCancel: () -> Unit,
    onAddWorkout: (String) -> Unit,
    onNewWorkout: () -> Unit,
    onAddCardio: (CardioType) -> Unit,
    onAddAux: (String) -> Unit,
) {
    val hasCardio = occurrences.any { it.rule?.modality == ScheduleModality.CARDIO }
    val usedAux = occurrences.mapNotNull { ScheduleKind.auxPackId(it.rule?.templateId) }.toSet()
    val usedRoutineIds = occurrences.mapNotNull { it.rule?.routineId }.toSet()
    val laterChoices = routines.filter { it.id !in usedRoutineIds }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        if (picking != DayPicker.AUX) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker(
                    when (picking) {
                        DayPicker.KIND -> PlanDayCopy.PICK_KIND
                        DayPicker.CARDIO -> PlanDayCopy.PICK_CARDIO
                        DayPicker.AUX -> PlanDayCopy.PICK_AUX
                        DayPicker.WORKOUT -> PlanDayCopy.PICK_WORKOUT
                        DayPicker.NONE -> PlanDayCopy.ADD_SESSION
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = Metrics.touchMin),
                ) {
                    Text(
                        PlanDayCopy.CANCEL,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                    )
                }
            }
        }
        when (picking) {
            DayPicker.NONE -> Unit
            DayPicker.KIND -> GroupedList {
                InstrumentRow(
                    title = PlanDayCopy.WORKOUT,
                    subtitle = PlanDayCopy.WORKOUT_SUBTITLE,
                    onClick = { onPickKind(DayPicker.WORKOUT) },
                )
                HairlineDivider()
                InstrumentRow(
                    title = PlanDayCopy.CARDIO,
                    subtitle = if (hasCardio) PlanDayCopy.CARDIO_ALREADY else SessionOrderCopy.CARDIO_ON_THIS_DAY,
                    onClick = if (hasCardio) null else ({ onPickKind(DayPicker.CARDIO) }),
                )
                HairlineDivider()
                InstrumentRow(
                    title = PlanDayCopy.AUXILIARY,
                    subtitle = PlanDayCopy.AUX_SUBTITLE,
                    onClick = { onPickKind(DayPicker.AUX) },
                )
            }
            DayPicker.CARDIO -> {
                if (hasCardio) {
                    Text(
                        PlanDayCopy.CARDIO_ALREADY,
                        style = InstrumentType.body,
                        color = TextPrimary,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                        ScheduleKind.planCardioTypes.forEach { type ->
                            InstrumentChip(
                                label = PlanDayCopy.cardioPickLabel(type),
                                selected = false,
                                onClick = { onAddCardio(type) },
                            )
                        }
                    }
                }
            }
            DayPicker.AUX -> AuxiliaryPackList(
                usedPackIds = usedAux,
                onPick = onAddAux,
                onCancel = onCancel,
            )
            DayPicker.WORKOUT -> {
                GroupedList {
                    InstrumentRow(
                        title = PlanDayCopy.NEW_WORKOUT,
                        subtitle = if (pinned) {
                            SessionOrderCopy.COMPOSE_LATER
                        } else {
                            PlanDayCopy.BUILD_WEEKDAY
                        },
                        onClick = onNewWorkout,
                    )
                }
                if (laterChoices.isEmpty()) {
                    Text(
                        if (pinned) {
                            PlanDayCopy.PICK_NAMED_PINNED
                        } else {
                            PlanDayCopy.PICK_NAMED_OPEN
                        },
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                } else {
                    GroupedList {
                        laterChoices.forEachIndexed { index, routine ->
                            if (index > 0) HairlineDivider()
                            InstrumentRow(
                                title = routine.name,
                                subtitle = "${routine.exercises.size} " +
                                    if (routine.exercises.size == 1) "lift" else "lifts",
                                onClick = { onAddWorkout(routine.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class DayPicker { NONE, KIND, WORKOUT, CARDIO, AUX }

object PlanDayTags {
    const val BACK = "plan-day-back"
    const val ADD = "plan-day-add"

    fun moveUp(occurrenceId: String): String = "plan-move-up-$occurrenceId"

    fun moveDown(occurrenceId: String): String = "plan-move-down-$occurrenceId"
}

private val DATE_CAPTION: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
