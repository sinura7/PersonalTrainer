package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
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
 * One day of the week, and everything you can do to it.
 *
 * All pin management lives here rather than on the strip. The strip is seven cells wide on a
 * phone; hanging long-press menus off cells that size makes destructive actions reachable by
 * accident and puts the app's most consequential controls in its smallest touch targets. A tap
 * opens this, and this has room to name what each action does.
 *
 * What the sheet offers is a function of one thing: whether the day is behind you. A past day
 * is a record and gets no actions at all — the way to change what happened is History, not the
 * plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDaySheet(
    day: SuggestedTrainingDay,
    routines: List<Routine>,
    isPast: Boolean,
    logged: Boolean,
    onStart: () -> Unit,
    onPinRoutine: (String) -> Unit,
    onPinFocus: (SessionFocusKind) -> Unit,
    onSwapRoutine: (String) -> Unit,
    onUnpin: () -> Unit,
    onEditRoutine: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var picking by rememberSaveable(day.epochDay) { mutableStateOf(Picker.NONE) }
    val pinned = !day.isRest

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                )
                Text(
                    when {
                        logged -> "Logged."
                        pinned -> day.reason
                        isPast -> "No session logged."
                        else -> "Nothing pinned here yet."
                    },
                    style = InstrumentType.caption,
                    color = TextSecondary,
                )
            }

            if (isPast) return@Column

            if (pinned) {
                PrimaryGymButton(
                    // "Train again" rather than "Start" once the day is done: the button still
                    // works, and pretending the session has not happened would be the lie.
                    text = if (logged) "Train again" else "Start ${day.routineName ?: day.focusTitle}",
                    onClick = onStart,
                )
                GroupedList {
                    if (onEditRoutine != null) {
                        InstrumentRow(
                            title = "Edit lifts",
                            subtitle = "Swap, change sets and reps, or reorder.",
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
            } else {
                GroupedList {
                    InstrumentRow(
                        title = "Pin a routine…",
                        onClick = { picking = if (picking == Picker.ROUTINE) Picker.NONE else Picker.ROUTINE },
                    )
                    HairlineDivider()
                    InstrumentRow(
                        title = "Pin a focus…",
                        subtitle = "For a day you know the shape of but not the lifts.",
                        onClick = { picking = if (picking == Picker.FOCUS) Picker.NONE else Picker.FOCUS },
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
                "Create a routine on this tab first, then pin it here.",
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

private enum class Picker { NONE, ROUTINE, FOCUS, SWAP }

private val PINNABLE_FOCUS = listOf(
    SessionFocusKind.PUSH,
    SessionFocusKind.PULL,
    SessionFocusKind.LEGS,
    SessionFocusKind.UPPER,
    SessionFocusKind.LOWER,
    SessionFocusKind.FULL_BODY,
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE '·' d MMM")
