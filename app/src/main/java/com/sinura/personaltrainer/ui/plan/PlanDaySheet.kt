package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.HomeToday
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SlotRuleImport
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.sessionLiftNames
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One weekday of the plan: build it, or start what is already on it.
 *
 * Open days are built here — named after the weekday, lifts and targets
 * in the editor. Morning cardio and later sessions (accessory / Hyper
 * Pro) sit as extra occurrences on the same weekday. Home then shows
 * that day's stack. A free session on the same date does not consume
 * the plan. One live activity still blocks a second start.
 *
 * Past days are a record and get no actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDaySheet(
    day: SuggestedTrainingDay,
    routines: List<Routine>,
    isPast: Boolean,
    logged: Boolean,
    onPinRoutine: (String) -> Unit,
    onPinFocus: (SessionFocusKind) -> Unit,
    onSwapRoutine: (String) -> Unit,
    onUnpin: () -> Unit,
    onEditRoutine: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    occurrences: List<AgendaItem> = emptyList(),
    onStartOccurrence: (String) -> Unit = {},
    onAddMorningCardio: () -> Unit = {},
    onAddLaterSession: (String) -> Unit = {},
    onComposeLaterSession: () -> Unit = {},
    onRemoveTimedRule: (String) -> Unit = {},
    onBuildDay: () -> Unit = {},
    onStartFree: () -> Unit = {},
    sessionLive: Boolean = false,
) {
    var picking by rememberSaveable(day.epochDay) { mutableStateOf(Picker.NONE) }
    val pinned = !day.isRest
    val extras = occurrences.filter { item ->
        item.rule?.id?.let { SlotRuleImport.isUserTimedRule(it) } == true
    }
    val usedRoutineIds = occurrences.mapNotNull { it.rule?.routineId }.toSet()
    val laterChoices = routines.filter { it.id !in usedRoutineIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
                Kicker(dateLabel(day.epochDay))
                Text(
                    when {
                        pinned -> day.routineName ?: day.focusTitle
                        isPast -> "Rest day"
                        else -> "Open day"
                    },
                    style = InstrumentType.title,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        logged -> "Logged. The plan for later today still stands."
                        pinned -> day.reason
                        isPast -> "No session logged."
                        else -> "Build ${CustomWeekPolicy.routineName(day.dayOfWeek)} — lifts, sets, reps. More sessions can sit on the same day."
                    },
                    style = InstrumentType.caption,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (occurrences.isNotEmpty()) {
                val startTagId = HomeToday.startTagOccurrenceId(occurrences)
                GroupedList {
                    occurrences.forEachIndexed { index, item ->
                        if (index > 0) HairlineDivider()
                        val canStart = !isPast &&
                            !sessionLive &&
                            item.occurrence.status == OccurrenceStatus.PLANNED
                        val names = sessionLiftNames(item.rule?.routineId, routines)
                        val tagged = item.occurrence.id == startTagId
                        InstrumentRow(
                            title = "${item.timeLabel}  ·  ${item.title}",
                            subtitle = SessionOrderCopy.occurrenceLine(
                                item.occurrence.status,
                                names,
                                item.rule?.modality ?: ScheduleModality.STRENGTH,
                            ),
                            onClick = if (canStart && !tagged) {
                                { onStartOccurrence(item.occurrence.id) }
                            } else {
                                null
                            },
                        )
                    }
                }
                val taggedItem = occurrences.firstOrNull { it.occurrence.id == startTagId }
                if (
                    taggedItem != null &&
                    !isPast &&
                    !sessionLive &&
                    taggedItem.occurrence.status == OccurrenceStatus.PLANNED
                ) {
                    PrimaryGymButton(
                        text = "Start ${taggedItem.title}",
                        onClick = { onStartOccurrence(taggedItem.occurrence.id) },
                    )
                }
            }

            if (isPast) return@Column

            if (sessionLive) {
                Text(
                    "Finish or discard the live session first.",
                    style = InstrumentType.body,
                    color = TextPrimary,
                )
            } else if (!pinned) {
                PrimaryGymButton(
                    text = "Build ${CustomWeekPolicy.routineName(day.dayOfWeek)}",
                    onClick = onBuildDay,
                )
                GroupedList {
                    InstrumentRow(
                        title = "Pin a routine…",
                        subtitle = "Use one you already named.",
                        onClick = { picking = if (picking == Picker.ROUTINE) Picker.NONE else Picker.ROUTINE },
                    )
                    HairlineDivider()
                    InstrumentRow(
                        title = "Pin a focus…",
                        subtitle = "For a day you know the shape of but not the lifts.",
                        onClick = { picking = if (picking == Picker.FOCUS) Picker.NONE else Picker.FOCUS },
                    )
                }
            } else {
                val hasCardio = occurrences.any { it.rule?.modality == ScheduleModality.CARDIO }
                GroupedList {
                    if (!hasCardio) {
                        InstrumentRow(
                            title = "Add morning cardio",
                            subtitle = SessionOrderCopy.CARDIO_ON_THIS_DAY,
                            onClick = onAddMorningCardio,
                        )
                        HairlineDivider()
                    }
                    InstrumentRow(
                        title = "Add another session…",
                        subtitle = SessionOrderCopy.LATER_SESSION,
                        onClick = { picking = if (picking == Picker.LATER) Picker.NONE else Picker.LATER },
                    )
                    if (extras.isNotEmpty()) {
                        HairlineDivider()
                        InstrumentRow(
                            title = "Remove a session…",
                            subtitle = SessionOrderCopy.REMOVE_SESSION,
                            onClick = { picking = if (picking == Picker.REMOVE) Picker.NONE else Picker.REMOVE },
                        )
                    }
                }
                GroupedList {
                    if (onEditRoutine != null) {
                        InstrumentRow(
                            title = "Edit lifts",
                            subtitle = SessionOrderCopy.EDIT_LIFTS_SUBTITLE,
                            onClick = onEditRoutine,
                        )
                        HairlineDivider()
                    }
                    InstrumentRow(
                        title = "Swap routine…",
                        onClick = { picking = if (picking == Picker.SWAP) Picker.NONE else Picker.SWAP },
                    )
                    HairlineDivider()
                    InstrumentRow(
                        title = "Unpin this day",
                        subtitle = "Re-pin it any time; nothing logged is affected.",
                        onClick = onUnpin,
                    )
                }
            }

            if (!sessionLive) {
                TextButton(
                    onClick = onStartFree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Metrics.touchMin),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        SessionOrderCopy.FREE_WORKOUT,
                        style = InstrumentType.bodyStrong,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (picking) {
                Picker.NONE -> Unit
                Picker.ROUTINE, Picker.SWAP -> RoutinePicker(
                    routines = routines,
                    onPick = { routineId ->
                        picking = Picker.NONE
                        if (pinned) onSwapRoutine(routineId) else onPinRoutine(routineId)
                    },
                )
                Picker.FOCUS -> FocusPicker(
                    onPick = { kind ->
                        picking = Picker.NONE
                        onPinFocus(kind)
                    },
                )
                Picker.LATER -> LaterSessionPicker(
                    routines = laterChoices,
                    onCompose = {
                        picking = Picker.NONE
                        onComposeLaterSession()
                    },
                    onPick = { routineId ->
                        picking = Picker.NONE
                        onAddLaterSession(routineId)
                    },
                )
                Picker.REMOVE -> RemoveSessionPicker(
                    extras = extras,
                    onPick = { ruleId ->
                        picking = Picker.NONE
                        onRemoveTimedRule(ruleId)
                    },
                )
            }
        }
    }
}

@Composable
private fun LaterSessionPicker(
    routines: List<Routine>,
    onCompose: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Kicker("Later session")
        GroupedList {
            InstrumentRow(
                title = "New session…",
                subtitle = SessionOrderCopy.COMPOSE_LATER,
                onClick = onCompose,
            )
        }
        if (routines.isEmpty()) {
            Text(
                "Or create a named routine on this tab, then pick it here.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else {
            RoutinePicker(routines = routines, onPick = onPick)
        }
    }
}

@Composable
private fun RemoveSessionPicker(
    extras: List<AgendaItem>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Kicker("Remove")
        GroupedList {
            extras.forEachIndexed { index, item ->
                val ruleId = item.rule?.id ?: return@forEachIndexed
                if (index > 0) HairlineDivider()
                InstrumentRow(
                    title = "${item.timeLabel}  ·  ${item.title}",
                    subtitle = SessionOrderCopy.REMOVE_SESSION,
                    onClick = { onPick(ruleId) },
                )
            }
        }
    }
}

@Composable
private fun RoutinePicker(routines: List<Routine>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Kicker("Routines")
        if (routines.isEmpty()) {
            Text(
                "Build the day above, or create a routine on this tab first.",
                style = InstrumentType.caption,
                color = TextSecondary,
            )
        } else {
            GroupedList {
                routines.forEachIndexed { index, routine ->
                    if (index > 0) HairlineDivider()
                    InstrumentRow(
                        title = routine.name,
                        subtitle = "${routine.exercises.size} " +
                            if (routine.exercises.size == 1) "lift" else "lifts",
                        onClick = { onPick(routine.id) },
                    )
                }
            }
        }
    }
}

/**
 * The six pinnable focuses.
 *
 * `RECOVERY` is deliberately absent: it is a thing the planner infers about a week, not a
 * session anyone sets out to do, and offering it as a pin would invite someone to schedule
 * "Recovery lean" as if it were a workout.
 */
@Composable
private fun FocusPicker(onPick: (SessionFocusKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap)) {
        Kicker("Focus")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            items(PINNABLE_FOCUS) { kind ->
                InstrumentChip(label = kind.label, selected = false, onClick = { onPick(kind) })
            }
        }
    }
}

private fun dateLabel(epochDay: Long): String =
    DATE_FORMAT.format(LocalDate.ofEpochDay(epochDay))

private enum class Picker { NONE, ROUTINE, FOCUS, SWAP, LATER, REMOVE }

private val PINNABLE_FOCUS = listOf(
    SessionFocusKind.PUSH,
    SessionFocusKind.PULL,
    SessionFocusKind.LEGS,
    SessionFocusKind.UPPER,
    SessionFocusKind.LOWER,
    SessionFocusKind.FULL_BODY,
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE '·' d MMM")
