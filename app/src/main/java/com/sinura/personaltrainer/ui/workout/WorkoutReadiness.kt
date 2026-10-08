package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Explains the existing action beside the draft, without creating another action or write. */
@Composable
internal fun WorkoutReadiness(action: WorkoutPrimaryAction, effortMissing: Boolean) {
    val text = when (action.kind) {
        WorkoutPrimaryKind.CHECKING -> "Checking whether your set saved…"
        WorkoutPrimaryKind.SAVING -> "Saving your entry…"
        WorkoutPrimaryKind.UPDATING -> "Updating your workout…"
        WorkoutPrimaryKind.RETRY_SAVE -> "Your entry is kept. Retry save below."
        WorkoutPrimaryKind.REVIEW_SAVE -> "Your entry needs review. Review save below."
        WorkoutPrimaryKind.SAVE_CHANGES -> if (effortMissing) {
            "Choose effort to save this correction."
        } else {
            "Save changes updates this saved set."
        }
        WorkoutPrimaryKind.NEXT_EXERCISE -> "Planned sets complete. Continue when you’re ready."
        WorkoutPrimaryKind.FINISH -> "Planned sets complete. Finish when you’re ready."
        WorkoutPrimaryKind.START_HOLD -> "Start hold when you’re ready."
        WorkoutPrimaryKind.GET_READY -> "Get ready. Your hold starts after the countdown."
        WorkoutPrimaryKind.LOG_HOLD -> "Log hold saves the time shown below."
        WorkoutPrimaryKind.LOG_WARMUP -> "Ready to log your warm-up."
        WorkoutPrimaryKind.LOG_SET -> if (effortMissing) {
            "Choose effort to log this set."
        } else if (!action.enabled) {
            "Waiting for this exercise’s entry…"
        } else {
            "Ready to log your entry."
        }
        WorkoutPrimaryKind.UNAVAILABLE -> "Waiting for your workout…"
        WorkoutPrimaryKind.ADD_EXERCISE -> "Add an exercise to begin."
    }
    Text(
        text = text,
        style = InstrumentType.caption,
        color = TextSecondary,
        modifier = Modifier
            .padding(horizontal = Metrics.space2, vertical = Metrics.space1)
            .testTag(WorkoutTestTags.LOG_READINESS),
    )
}
