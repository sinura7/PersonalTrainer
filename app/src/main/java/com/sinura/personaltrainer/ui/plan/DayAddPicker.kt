package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

enum class DayPicker { NONE, KIND, WORKOUT, CARDIO, AUX, KEEP }

private enum class PendingKeep { NONE, NEW_WORKOUT, WORKOUT, CARDIO, AUX }

/**
 * Shared add picker for Plan day (always weekly) and Home (asks just-today
 * vs every this weekday). [askKeep] is the Home path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayAddPicker(
    picking: DayPicker,
    weekday: Weekday,
    hasStrength: Boolean,
    occurrences: List<AgendaItem>,
    routines: List<Routine>,
    askKeep: Boolean,
    onPickKind: (DayPicker) -> Unit,
    onCancel: () -> Unit,
    onAddWorkout: (String, Boolean) -> Unit,
    onNewWorkout: (Boolean) -> Unit,
    onAddCardio: (CardioType, Boolean) -> Unit,
    onAddAux: (String, Boolean) -> Unit,
) {
    var pendingKind by rememberSaveable { mutableStateOf(PendingKeep.NONE.name) }
    var pendingWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCardio by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAux by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(picking) {
        if (picking == DayPicker.NONE || picking == DayPicker.KIND) {
            pendingKind = PendingKeep.NONE.name
            pendingWorkoutId = null
            pendingCardio = null
            pendingAux = null
        }
    }
    fun keepOr(runWeekly: () -> Unit, arm: () -> Unit) {
        if (askKeep) {
            arm()
            onPickKind(DayPicker.KEEP)
        } else {
            runWeekly()
        }
    }
    fun commit(once: Boolean) {
        when (runCatching { PendingKeep.valueOf(pendingKind) }.getOrNull() ?: PendingKeep.NONE) {
            PendingKeep.NEW_WORKOUT -> onNewWorkout(once)
            PendingKeep.WORKOUT -> pendingWorkoutId?.let { onAddWorkout(it, once) }
            PendingKeep.CARDIO -> pendingCardio
                ?.let { runCatching { CardioType.valueOf(it) }.getOrNull() }
                ?.let { onAddCardio(it, once) }
            PendingKeep.AUX -> pendingAux?.let { onAddAux(it, once) }
            PendingKeep.NONE -> Unit
        }
    }

    val hasCardio = occurrences.any { it.rule?.modality == ScheduleModality.CARDIO }
    val usedAux = occurrences.mapNotNull { ScheduleKind.auxPackId(it.rule?.templateId) }.toSet()
    val usedRoutineIds = occurrences.mapNotNull { it.rule?.routineId }.toSet()
    val laterChoices = routines.filter { it.id !in usedRoutineIds }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        if (picking != DayPicker.AUX && picking != DayPicker.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker(
                    when (picking) {
                        DayPicker.KIND -> PlanDayCopy.PICK_KIND
                        DayPicker.CARDIO -> PlanDayCopy.PICK_CARDIO
                        DayPicker.AUX -> PlanDayCopy.PICK_AUX
                        DayPicker.WORKOUT -> PlanDayCopy.PICK_WORKOUT
                        DayPicker.KEEP -> PlanDayCopy.KEEP
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
                                onClick = {
                                    keepOr(
                                        runWeekly = { onAddCardio(type, false) },
                                        arm = {
                                            pendingKind = PendingKeep.CARDIO.name
                                            pendingCardio = type.name
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            DayPicker.AUX -> AuxiliaryPackList(
                usedPackIds = usedAux,
                onPick = { packId ->
                    keepOr(
                        runWeekly = { onAddAux(packId, false) },
                        arm = {
                            pendingKind = PendingKeep.AUX.name
                            pendingAux = packId
                        },
                    )
                },
                onCancel = onCancel,
            )
            DayPicker.WORKOUT -> {
                GroupedList {
                    InstrumentRow(
                        title = PlanDayCopy.NEW_WORKOUT,
                        subtitle = if (hasStrength) {
                            SessionOrderCopy.COMPOSE_LATER
                        } else {
                            PlanDayCopy.BUILD_WEEKDAY
                        },
                        onClick = {
                            keepOr(
                                runWeekly = { onNewWorkout(false) },
                                arm = { pendingKind = PendingKeep.NEW_WORKOUT.name },
                            )
                        },
                    )
                }
                if (laterChoices.isEmpty()) {
                    Text(
                        if (hasStrength) {
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
                                onClick = {
                                    keepOr(
                                        runWeekly = { onAddWorkout(routine.id, false) },
                                        arm = {
                                            pendingKind = PendingKeep.WORKOUT.name
                                            pendingWorkoutId = routine.id
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            DayPicker.KEEP -> GroupedList {
                InstrumentRow(
                    title = PlanDayCopy.JUST_TODAY,
                    subtitle = PlanDayCopy.JUST_TODAY_BODY,
                    onClick = { commit(once = true) },
                )
                HairlineDivider()
                InstrumentRow(
                    title = PlanDayCopy.everyWeekday(weekday),
                    subtitle = PlanDayCopy.EVERY_WEEKDAY_BODY,
                    onClick = { commit(once = false) },
                )
            }
        }
    }
}
