package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.HomeStartCopy
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.MoveToToday
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.DayBlockCopy
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.ui.components.GymCard
import com.sinura.personaltrainer.domain.sessionLifts
import com.sinura.personaltrainer.domain.sessionMinutes
import com.sinura.personaltrainer.ui.components.ConfirmActionDialog
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * Today's occurrences — Home's only today-surface when the planner
 * generated any row (P7.5). ThisWeekCard is the empty-agenda leftover.
 *
 * Planned rows start that session (confirm). The filled Volt is
 * Start a workout and opens the start sheet (free / routine / cardio /
 * Extra). Plan still adds sessions. Still open leftovers can skip (ADR-021).
 *
 * Each session is its own [DayBlock] — still beside number and name,
 * estimate, Start on the foot — rather than a row in one grouped list: a
 * day's sessions are separate things, and the block is the control
 * (ADR-021 §1).
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
    quietStart: Boolean = false,
    canEditDay: Boolean = false,
    onMoveOccurrence: (String, Int) -> Unit = { _, _ -> },
    onSkipOccurrence: (String) -> Unit = {},
    confirmOccurrenceId: String? = null,
    onConfirmOccurrenceConsumed: () -> Unit = {},
    summaries: List<SessionSummary> = emptyList(),
    unlinkedRecords: List<SessionSummary> = emptyList(),
    onOpenRecord: (SessionSummary) -> Unit = {},
) {
    val catalog = items + stillOpen
    var pendingOccurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
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

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        Kicker(kicker)
        if (items.isEmpty() && stillOpen.isEmpty() && unlinkedRecords.isEmpty()) {
            Text(
                PlanDayCopy.EMPTY,
                style = InstrumentType.body,
                color = TextPrimary,
            )
            Text(
                HomeStartCopy.EMPTY_BODY,
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
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)) {
                items.forEachIndexed { index, item ->
                    if (item.occurrence.status == OccurrenceStatus.DONE) {
                        val record = summaries.firstOrNull { it.id == item.occurrence.completedActivityId }
                        if (record != null) {
                            HomeRecordedSession(
                                session = record,
                                onOpen = onOpenRecord,
                                scheduledTitle = item.title.takeIf { record.localEpochDay != item.occurrence.localEpochDay },
                            )
                        } else {
                            GymCard(modifier = Modifier.testTag(HomeTags.agendaRow(item.occurrence.id))) {
                                Kicker("Completed")
                                Text(item.title, style = InstrumentType.title, color = TextPrimary)
                                Text(
                                    if (item.occurrence.completedActivityId == null) "Marked complete; no linked session."
                                    else "Saved session unavailable.",
                                    style = InstrumentType.body, color = TextSecondary,
                                )
                            }
                        }
                    } else {
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
        }
        unlinkedRecords.forEach { session -> HomeRecordedSession(session, onOpenRecord) }
        if (stillOpen.isNotEmpty()) {
            Kicker(MoveToToday.STILL_OPEN)
            Text(
                MoveToToday.STILL_OPEN_BODY,
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            Column(
                modifier = Modifier.testTag(HomeTags.STILL_OPEN),
                verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
            ) {
                stillOpen.forEachIndexed { index, item ->
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
                    height = Metrics.touchMin,
                )
            } else {
                PrimaryGymButton(
                    text = SessionOrderCopy.FREE_WORKOUT,
                    onClick = onStartFree,
                    modifier = startModifier,
                    height = Metrics.touchMin,
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
    val routineId = item.rule?.routineId
    val lifts = sessionLifts(routineId, routines)
    val title = if (leftoverLabel) {
        "${Weekday.fromEpochDay(item.occurrence.localEpochDay).titleLabel()}  ·  ${item.title}"
    } else {
        item.title
    }
    val openable = DailyAgenda.canOpenStart(item, todayEpochDay)
    val pack = ScheduleKind.auxPackId(item.rule?.templateId)?.let { AuxiliaryPacks.byId(it) }
    val lines = DayBlockCopy.lines(
        names = lifts.map { it.name },
        status = item.occurrence.status,
        modality = item.rule?.modality ?: ScheduleModality.STRENGTH,
        minutes = sessionMinutes(routineId, routines),
        caption = pack?.caption,
        startable = openable,
    )
    val canStart = openable && !sessionLive
    val leftover = MoveToToday.isLeftover(item.occurrence, todayEpochDay)
    val showSkip = onSkip != null && leftover && !sessionLive
    DayBlock(
        title = title,
        lines = lines,
        exercises = lifts,
        action = when {
            !canStart -> null
            leftover -> MoveToToday.DO_IT_TODAY
            else -> SessionOrderCopy.START_ROW
        },
        onOpen = if (canStart) onOpen else null,
        modifier = Modifier.testTag(HomeTags.agendaRow(item.occurrence.id)),
        controls = if (!showSkip && !showReorder) {
            null
        } else {
            {
                if (showSkip && onSkip != null) {
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
        },
    )
}
