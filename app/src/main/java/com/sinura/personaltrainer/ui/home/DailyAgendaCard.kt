package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.plan.DayAddPicker
import com.sinura.personaltrainer.ui.plan.DayPicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * Today's occurrences — Home's only today-surface when the planner
 * generated any row (P7.5). ThisWeekCard is the empty-agenda leftover.
 *
 * Planned rows start that session (confirm). The filled Volt is
 * Start a workout (freestyle). Add sits under the last planned row.
 * Still open leftovers can skip (ADR-021).
 */
@Composable
fun DailyAgendaCard(
    items: List<AgendaItem>,
    sessionLive: Boolean,
    onStartOccurrence: (String) -> Unit,
    onStartFree: () -> Unit,
    routines: List<Routine> = emptyList(),
    kicker: String = "Today",
    stillOpen: List<AgendaItem> = emptyList(),
    today: Long = com.sinura.personaltrainer.ui.units.LocalTodayEpochDay.current,
    epochDay: Long = today,
    quietStart: Boolean = false,
    canEditDay: Boolean = false,
    onMoveOccurrence: (String, Int) -> Unit = { _, _ -> },
    onSkipOccurrence: (String) -> Unit = {},
    onAddWorkout: (String, Boolean) -> Unit = { _, _ -> },
    onNewWorkout: (Boolean) -> Unit = {},
    onAddCardio: (CardioType, Boolean) -> Unit = { _, _ -> },
    onAddAux: (String, Boolean) -> Unit = { _, _ -> },
    confirmOccurrenceId: String? = null,
    onConfirmOccurrenceConsumed: () -> Unit = {},
) {
    val catalog = items + stillOpen
    var pendingOccurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable(epochDay) { mutableStateOf(DayPicker.NONE.name) }
    val picker = runCatching { DayPicker.valueOf(picking) }.getOrNull() ?: DayPicker.NONE
    val pendingItem = catalog.firstOrNull { item ->
        item.occurrence.id == pendingOccurrenceId && DailyAgenda.canOpenStart(item, today)
    }?.takeUnless { sessionLive }

    LaunchedEffect(pendingOccurrenceId, items, stillOpen, sessionLive, today) {
        val id = pendingOccurrenceId ?: return@LaunchedEffect
        if (sessionLive) {
            pendingOccurrenceId = null
            return@LaunchedEffect
        }
        val still = catalog.any { it.occurrence.id == id && DailyAgenda.canOpenStart(it, today) }
        if (!still) pendingOccurrenceId = null
    }
    LaunchedEffect(confirmOccurrenceId, items, stillOpen, sessionLive, today) {
        val id = confirmOccurrenceId ?: return@LaunchedEffect
        if (sessionLive) {
            onConfirmOccurrenceConsumed()
            return@LaunchedEffect
        }
        val still = catalog.any { it.occurrence.id == id && DailyAgenda.canOpenStart(it, today) }
        if (!still) return@LaunchedEffect
        pendingOccurrenceId = id
        onConfirmOccurrenceConsumed()
    }

    if (pendingItem != null) {
        val confirm = HomeToday.startConfirm(pendingItem, routines, today)
        ConfirmActionDialog(
            title = confirm.heading,
            body = confirm.body,
            confirmLabel = confirm.confirmLabel,
            onConfirm = {
                val id = pendingItem.occurrence.id
                pendingOccurrenceId = null
                onStartOccurrence(id)
            },
            onDismiss = { pendingOccurrenceId = null },
        )
    }

    val weekday = Weekday.fromEpochDay(epochDay)
    val hasStrength = items.any { item ->
        val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
        modality == ScheduleModality.STRENGTH && !ScheduleKind.isAux(item.rule?.templateId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        Kicker(kicker)
        if (items.isEmpty() && stillOpen.isEmpty() && picker == DayPicker.NONE) {
            Text(
                com.sinura.personaltrainer.domain.PlanDayCopy.EMPTY,
                style = InstrumentType.body,
                color = TextPrimary,
            )
            Text(
                com.sinura.personaltrainer.domain.PlanDayCopy.EMPTY_BODY,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else if (items.size > 1) {
            Text(
                SessionOrderCopy.AGENDA_SEPARATE,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        }
        if (items.isNotEmpty()) {
            GroupedList {
                items.forEachIndexed { index, item ->
                    if (index > 0) HairlineDivider()
                    AgendaRow(
                        item = item,
                        routines = routines,
                        todayEpochDay = today,
                        sessionLive = sessionLive,
                        leftoverLabel = false,
                        showReorder = canEditDay && items.size > 1,
                        index = index,
                        lastIndex = items.lastIndex,
                        onMove = onMoveOccurrence,
                        onOpen = { pendingOccurrenceId = item.occurrence.id },
                    )
                }
            }
        }
        if (canEditDay && !sessionLive) {
            if (picker != DayPicker.NONE) {
                DayAddPicker(
                    picking = picker,
                    weekday = weekday,
                    hasStrength = hasStrength,
                    occurrences = items,
                    routines = routines,
                    askKeep = true,
                    onPickKind = { picking = it.name },
                    onCancel = { picking = DayPicker.NONE.name },
                    onAddWorkout = { id, once ->
                        picking = DayPicker.NONE.name
                        onAddWorkout(id, once)
                    },
                    onNewWorkout = { once ->
                        picking = DayPicker.NONE.name
                        onNewWorkout(once)
                    },
                    onAddCardio = { type, once ->
                        picking = DayPicker.NONE.name
                        onAddCardio(type, once)
                    },
                    onAddAux = { packId, once ->
                        picking = DayPicker.NONE.name
                        onAddAux(packId, once)
                    },
                )
            } else {
                GroupedList {
                    InstrumentRow(
                        title = PlanDayCopy.ADD,
                        subtitle = PlanDayCopy.ADD_SUBTITLE,
                        modifier = Modifier
                            .testTag(HomeTags.ADD)
                            .semantics { contentDescription = PlanDayCopy.ADD },
                        leading = {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        },
                        onClick = { picking = DayPicker.KIND.name },
                    )
                }
            }
        }
        if (stillOpen.isNotEmpty()) {
            Kicker(MoveToToday.STILL_OPEN)
            Text(
                MoveToToday.STILL_OPEN_BODY,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            GroupedList(
                modifier = Modifier.testTag(HomeTags.STILL_OPEN),
            ) {
                stillOpen.forEachIndexed { index, item ->
                    if (index > 0) HairlineDivider()
                    AgendaRow(
                        item = item,
                        routines = routines,
                        todayEpochDay = today,
                        sessionLive = sessionLive,
                        leftoverLabel = true,
                        showReorder = false,
                        index = index,
                        lastIndex = stillOpen.lastIndex,
                        onMove = { _, _ -> },
                        onOpen = { pendingOccurrenceId = item.occurrence.id },
                        onSkip = { onSkipOccurrence(item.occurrence.id) },
                    )
                }
            }
        }
        if (sessionLive) {
            Text(
                "Finish or discard the live session first.",
                style = InstrumentType.body,
                color = TextPrimary,
            )
        } else {
            val startModifier = Modifier
                .testTag(HomeTags.START)
                .semantics { contentDescription = SessionOrderCopy.FREE_WORKOUT }
            if (quietStart) {
                SecondaryGymButton(
                    text = SessionOrderCopy.FREE_WORKOUT,
                    onClick = onStartFree,
                    modifier = startModifier,
                )
            } else {
                PrimaryGymButton(
                    text = SessionOrderCopy.FREE_WORKOUT,
                    onClick = onStartFree,
                    modifier = startModifier,
                )
            }
        }
    }
}

@Composable
private fun AgendaRow(
    item: AgendaItem,
    routines: List<Routine>,
    todayEpochDay: Long,
    sessionLive: Boolean,
    leftoverLabel: Boolean,
    showReorder: Boolean,
    index: Int,
    lastIndex: Int,
    onMove: (String, Int) -> Unit,
    onOpen: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val names = sessionLiftNames(item.rule?.routineId, routines)
    val title = if (leftoverLabel) {
        "${Weekday.fromEpochDay(item.occurrence.localEpochDay).titleLabel()}  ·  ${item.title}"
    } else {
        item.title
    }
    val subtitle = SessionOrderCopy.occurrenceLine(
        item.occurrence.status,
        names,
        item.rule?.modality ?: ScheduleModality.STRENGTH,
    )
    val canStart = DailyAgenda.canOpenStart(item, todayEpochDay) && !sessionLive
    val leftover = MoveToToday.isLeftover(item.occurrence, todayEpochDay)
    Column {
        InstrumentRow(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.testTag(HomeTags.agendaRow(item.occurrence.id)),
            onClick = if (canStart) onOpen else null,
            trailing = if (canStart) {
                {
                    Text(
                        if (leftover) MoveToToday.DO_IT_TODAY else SessionOrderCopy.START_ROW,
                        style = InstrumentType.bodyStrong,
                        color = Volt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
        )
        if (onSkip != null && leftover && !sessionLive) {
            TextButton(
                onClick = onSkip,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Metrics.touchMin)
                    .testTag(HomeTags.skipRow(item.occurrence.id))
                    .semantics { contentDescription = "${MoveToToday.SKIP} ${item.title}" },
            ) {
                Text(
                    MoveToToday.SKIP,
                    style = InstrumentType.bodyStrong,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showReorder) {
            com.sinura.personaltrainer.ui.plan.ReorderRow(
                title = item.title,
                occurrenceId = item.occurrence.id,
                index = index,
                lastIndex = lastIndex,
                onMove = onMove,
            )
        }
    }
}
