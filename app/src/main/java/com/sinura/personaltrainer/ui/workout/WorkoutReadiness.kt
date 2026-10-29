package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Explains the existing action beside the draft, without creating another action or write. */
@Composable
internal fun WorkoutReadiness(action: WorkoutPrimaryAction, effortMissing: Boolean, resumed: Boolean = false, keepEntry: Boolean = false) {
    val text = when (action.kind) {
        WorkoutPrimaryKind.CHECKING -> stringResource(R.string.workout_readiness_checking_save)
        WorkoutPrimaryKind.SAVING -> stringResource(R.string.workout_readiness_saving)
        WorkoutPrimaryKind.UPDATING -> stringResource(R.string.workout_readiness_updating)
        WorkoutPrimaryKind.RETRY_SAVE -> stringResource(R.string.workout_readiness_retry_save)
        WorkoutPrimaryKind.REVIEW_SAVE -> stringResource(R.string.workout_readiness_review_save)
        WorkoutPrimaryKind.SAVE_CHANGES -> if (effortMissing) {
            stringResource(R.string.workout_readiness_save_changes_effort)
        } else {
            stringResource(R.string.workout_readiness_save_changes)
        }
        WorkoutPrimaryKind.NEXT_EXERCISE -> stringResource(R.string.workout_readiness_next_exercise)
        WorkoutPrimaryKind.FINISH -> stringResource(R.string.workout_readiness_finish)
        WorkoutPrimaryKind.START_HOLD -> stringResource(R.string.workout_readiness_start_hold)
        WorkoutPrimaryKind.GET_READY -> stringResource(R.string.workout_readiness_get_ready)
        WorkoutPrimaryKind.LOG_HOLD -> stringResource(R.string.workout_readiness_log_hold)
        WorkoutPrimaryKind.LOG_WARMUP -> stringResource(R.string.workout_readiness_log_warmup)
        WorkoutPrimaryKind.LOG_SET -> if (effortMissing) {
            stringResource(R.string.workout_readiness_log_set_effort)
        } else if (!action.enabled) {
            stringResource(R.string.workout_readiness_log_set_waiting)
        } else {
            stringResource(R.string.workout_readiness_log_set_ready)
        }
        WorkoutPrimaryKind.UNAVAILABLE -> stringResource(R.string.workout_readiness_unavailable)
        WorkoutPrimaryKind.ADD_EXERCISE -> stringResource(R.string.workout_readiness_add_exercise)
        WorkoutPrimaryKind.START_SET_NOW -> stringResource(R.string.workout_readiness_start_set_now)
    }
    Text(
        text = when {
            keepEntry -> stringResource(R.string.workout_readiness_keep_entry)
            resumed && action.kind in setOf(WorkoutPrimaryKind.LOG_SET, WorkoutPrimaryKind.LOG_WARMUP,
                WorkoutPrimaryKind.START_HOLD, WorkoutPrimaryKind.LOG_HOLD, WorkoutPrimaryKind.SAVE_CHANGES) ->
                stringResource(R.string.workout_readiness_draft_resumed, text)
            else -> text
        },
        style = InstrumentType.caption,
        color = TextSecondary,
        modifier = Modifier
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1)
            .testTag(WorkoutTestTags.LOG_READINESS),
    )
}
