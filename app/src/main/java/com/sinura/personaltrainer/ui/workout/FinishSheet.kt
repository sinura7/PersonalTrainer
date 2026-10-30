package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.EndWorkoutCopy
import com.sinura.personaltrainer.ui.components.DestructiveButton
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.ActionState
import com.sinura.personaltrainer.ui.components.PrimaryButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.components.NotesSaveState
import com.sinura.personaltrainer.ui.components.NotesSaveStatus
import com.sinura.personaltrainer.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinishSheet(
    loggedSets: Int,
    plannedWorkingSets: Int,
    durationMinutes: Int,
    onFinish: () -> Unit,
    onKeepTraining: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    editOpen: Boolean,
    notesSave: NotesSaveState = NotesSaveState(),
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onRetryNotes: () -> Unit = {},
    savePending: Boolean = false,
) {
    // Only a failed notes write blocks Finish. A pending or saving note is waited for by
    // finishWorkout itself, so the sheet does not hold the lifter back for it.
    val notesBlocking = notesSave.status == NotesSaveStatus.FAILED
    // A set save still in flight must land before the session can be finished (N6).
    val canSave = EndWorkoutCopy.canSave(loggedSets) && !notesBlocking && !savePending
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            onKeepTraining()
        },
        sheetState = sheetState,
        containerColor = Surface2,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space6)
                .testTag(WorkoutTestTags.FINISH_SHEET),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(
                stringResource(R.string.finish_title),
                style = InstrumentType.heroTitle,
                color = TextPrimary,
            )
            if (editOpen) {
                Text(
                    EndWorkoutCopy.EDIT_OPEN,
                    style = InstrumentType.body,
                    color = TextSecondary,
                    modifier = Modifier.testTag(EndWorkoutTags.EDIT_OPEN),
                )
                SecondaryGymButton(
                    text = EndWorkoutCopy.BACK_TO_EDIT,
                    onClick = onKeepTraining,
                    modifier = Modifier.testTag(EndWorkoutTags.BACK_TO_EDIT),
                )
            } else if (notesBlocking) {
                Text(
                    EndWorkoutCopy.BAR_NOTES_PENDING,
                    style = InstrumentType.body,
                    color = TextSecondary,
                    modifier = Modifier.testTag(EndWorkoutTags.NOTES_PENDING),
                )
                GhostButton(
                    label = stringResource(R.string.finish_retry_notes),
                    onClick = onRetryNotes,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(EndWorkoutTags.NOTES_RETRY),
                )
            } else {
                Text(
                    stringResource(
                        R.string.finish_counts,
                        loggedSets,
                        plannedWorkingSets.coerceAtLeast(loggedSets),
                        durationMinutes.coerceAtLeast(1),
                    ),
                    style = InstrumentType.body,
                    color = TextSecondary,
                )
                if (plannedWorkingSets > loggedSets) {
                    Text(
                        stringResource(R.string.finish_incomplete, plannedWorkingSets - loggedSets),
                        style = InstrumentType.caption,
                        color = TextSecondary,
                    )
                }
            }
            error?.let {
                Text(it, style = InstrumentType.caption, color = TextSecondary)
                GhostButton(
                    label = stringResource(R.string.finish_try_again),
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = Metrics.touchMin),
                )
            }
            PrimaryButton(
                label = stringResource(R.string.finish_cta),
                onClick = onFinish,
                state = when {
                    loading -> ActionState.Loading
                    !canSave -> ActionState.Disabled
                    else -> ActionState.Enabled
                },
                modifier = Modifier.testTag(EndWorkoutTags.SAVE),
            )
            if (loggedSets == 0) {
                DestructiveButton(
                    label = stringResource(R.string.finish_discard),
                    onClick = onDiscard,
                    modifier = Modifier.testTag(EndWorkoutTags.DISCARD),
                )
            } else {
                GhostButton(
                    label = stringResource(R.string.finish_keep),
                    onClick = onKeepTraining,
                    modifier = Modifier
                        .heightIn(min = Metrics.touchMin)
                        .testTag(WorkoutTestTags.FINISH_KEEP),
                )
            }
        }
    }
}
