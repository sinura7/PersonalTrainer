package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.CurrentLiftCopy
import com.sinura.personaltrainer.domain.LiftChipCopy
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.ui.components.ExerciseThumb
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.ThumbSize
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.Volt

internal data class LiftSwitcherRow(
    val lift: SessionExercise,
    val number: Int,
    val workingLogged: Int,
    val restSeconds: Int,
    val restRunning: Boolean,
    val restRemainingSeconds: Int,
    val current: Boolean,
)

/**
 * Packet C: every session lift, with progress and rest state.
 *
 * Not the exercise catalog. Not a tab. Selecting a row restores that lift's draft and
 * never resets the numbers already staged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiftSwitcherSheet(
    lifts: List<LiftSwitcherRow>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddLift: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .testTag(WorkoutTestTags.LIFT_SWITCHER)
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space4),
        ) {
            Text(
                CurrentLiftCopy.SWITCHER_TITLE,
                style = InstrumentType.title,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Metrics.space3),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("workout-switcher-list"),
                verticalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                itemsIndexed(
                    items = lifts,
                    key = { _, row -> row.lift.id },
                ) { _, row ->
                    LiftSwitcherItem(
                        row = row,
                        total = lifts.size,
                        onSelect = { onSelect(row.lift.exercise.id) },
                    )
                }
                item(key = "add-lift") {
                    SecondaryGymButton(
                        text = "Add exercise",
                        onClick = onAddLift,
                        modifier = Modifier.testTag(WorkoutTestTags.SWITCHER_ADD_LIFT),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftSwitcherItem(
    row: LiftSwitcherRow,
    total: Int,
    onSelect: () -> Unit,
) {
    val marks = LiftChipCopy.marks(
        workingLogged = row.workingLogged,
        targetSets = row.lift.targetSets,
        restSeconds = row.restSeconds,
        restRunningOnThisLift = row.restRunning,
        remainingSeconds = row.restRemainingSeconds,
    )
    val spoken = CurrentLiftCopy.switcherSpoken(
        name = row.lift.exercise.name,
        number = row.number,
        total = total,
        workingLogged = row.workingLogged,
        targetSets = row.lift.targetSets,
        restClock = marks.restClock,
        restLive = marks.restLive,
        current = row.current,
    )
    val shape = RoundedCornerShape(Radius.sm)
    val status = when {
        row.current -> "Current"
        row.lift.targetSets > 0 && row.workingLogged >= row.lift.targetSets -> "Complete"
        else -> "Remaining"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.control)
            .clip(shape)
            .background(Surface2)
            .border(
                Metrics.hairline,
                if (row.current) Volt else Hairline,
                shape,
            )
            .clickable(role = Role.Button, onClick = onSelect)
            .testTag(WorkoutTestTags.liftSwitcherRow(row.lift.exercise.id))
            .semantics(mergeDescendants = true) {
                contentDescription = "$spoken. $status"
                selected = row.current
            }
            .padding(Metrics.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        ExerciseThumb(exercise = row.lift.exercise, size = ThumbSize.header)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
            Text(
                row.lift.exercise.name,
                style = InstrumentType.bodyStrong,
                color = TextPrimary,
            )
            Text("${marks.setProgress} working sets · $status", style = InstrumentType.caption,
                color = if (row.current) Volt else TextSecondary)
            marks.restClock?.let { clock ->
                Text(
                    "${if (marks.restLive) "Rest remaining" else "Rest"}: $clock",
                    modifier = Modifier.testTag(WorkoutTestTags.liftRest(row.lift.exercise.id)),
                    style = InstrumentType.caption, color = if (marks.restLive) RestCyan else TextSecondary,
                )
            }
        }
    }
}
