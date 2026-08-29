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
import com.sinura.personaltrainer.domain.ClockCopy
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.SlotRuleImport
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
                            onSetHour = viewModel::setSessionHour,
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
                            onAddWorkout = { routineId, hour ->
                                picking = DayPicker.NONE
                                if (pinned) {
                                    viewModel.addLaterSession(epochDay, routineId, hour)
                                } else {
                                    viewModel.pinRoutine(epochDay, routineId, hour)
                                }
                            },
                            onNewWorkout = { hour ->
                                picking = DayPicker.NONE
                                if (pinned) {
                                    viewModel.composeLaterSession(epochDay, hour)
                                } else {
                                    viewModel.buildDay(epochDay, hour)
                                }
                            },
                            onAddCardio = { type, hour ->
                                picking = DayPicker.NONE
                                viewModel.addCardio(epochDay, type, hour)
                            },
                            onAddAux = { packId, hour ->
                                picking = DayPicker.NONE
                                viewModel.addAuxiliary(epochDay, packId, hour)
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
    onSetHour: (String, Int) -> Unit,
) {
    val clockFormat = com.sinura.personaltrainer.ui.units.LocalClockFormat.current
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
                if (!isPast && ruleId != null) {
                    val currentHour = item.occurrence.hour
                    FlowRow(
                        modifier = Modifier.padding(
                            start = Metrics.space4,
                            end = Metrics.space4,
                            bottom = Metrics.space3,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        com.sinura.personaltrainer.domain.ClockCopy.hourChoices(currentHour).forEach { hour ->
                            InstrumentChip(
                                label = com.sinura.personaltrainer.domain.ClockCopy.hourChip(hour, clockFormat),
                                selected = currentHour == hour,
                                onClick = { onSetHour(ruleId, hour) },
                            )
                        }
                    }
                }
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
    onAddWorkout: (String, Int) -> Unit,
    onNewWorkout: (Int) -> Unit,
    onAddCardio: (CardioType, Int) -> Unit,
    onAddAux: (String, Int) -> Unit,
) {
    val clockFormat = com.sinura.personaltrainer.ui.units.LocalClockFormat.current
    val hasCardio = occurrences.any { it.rule?.modality == ScheduleModality.CARDIO }
    val usedAux = occurrences.mapNotNull { ScheduleKind.auxPackId(it.rule?.templateId) }.toSet()
    val usedRoutineIds = occurrences.mapNotNull { it.rule?.routineId }.toSet()
    val laterChoices = routines.filter { it.id !in usedRoutineIds }
    val occupiedHours = occurrences.map { it.occurrence.hour }
    var hour by rememberSaveable(picking) {
        mutableStateOf(
            when (picking) {
                DayPicker.CARDIO -> SlotRuleImport.DEFAULT_CARDIO_HOUR
                DayPicker.AUX -> SlotRuleImport.nextLaterHour(occupiedHours)
                DayPicker.WORKOUT -> if (pinned) {
                    SlotRuleImport.nextLaterHour(occupiedHours)
                } else {
                    SlotRuleImport.DEFAULT_STRENGTH_HOUR
                }
                else -> SlotRuleImport.DEFAULT_STRENGTH_HOUR
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
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
        if (picking != DayPicker.KIND && picking != DayPicker.NONE) {
            Kicker(PlanDayCopy.WHEN)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                ClockCopy.hourChoices(hour).forEach { choice ->
                    InstrumentChip(
                        label = ClockCopy.hourChip(choice, clockFormat),
                        selected = hour == choice,
                        onClick = { hour = choice },
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
                    onClick = { onPickKind(DayPicker.CARDIO) },
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
                                onClick = { onAddCardio(type, hour) },
                            )
                        }
                    }
                }
            }
            DayPicker.AUX -> {
                val packs = AuxiliaryPacks.all.filter { it.id !in usedAux }
                if (packs.isEmpty()) {
                    Text(
                        PlanDayCopy.AUX_ALREADY,
                        style = InstrumentType.body,
                        color = TextPrimary,
                    )
                } else {
                    GroupedList {
                        packs.forEachIndexed { index, pack ->
                            if (index > 0) HairlineDivider()
                            InstrumentRow(
                                title = pack.title,
                                subtitle = pack.caption,
                                onClick = { onAddAux(pack.id, hour) },
                            )
                        }
                    }
                }
            }
            DayPicker.WORKOUT -> {
                GroupedList {
                    InstrumentRow(
                        title = PlanDayCopy.NEW_WORKOUT,
                        subtitle = if (pinned) {
                            SessionOrderCopy.COMPOSE_LATER
                        } else {
                            PlanDayCopy.BUILD_WEEKDAY
                        },
                        onClick = { onNewWorkout(hour) },
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
                                onClick = { onAddWorkout(routine.id, hour) },
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
}

private val DATE_CAPTION: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
