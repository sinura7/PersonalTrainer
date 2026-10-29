package com.sinura.personaltrainer.testutil

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags

private fun ComposeTestRule.finishVisibleInOverflow(): Boolean =
    onAllNodesWithTag(WorkoutTestTags.FINISH).fetchSemanticsNodes().isNotEmpty()

/** Opens overflow Finish (D3) and taps the Finish workout row. */
fun ComposeTestRule.openFinishFromOverflow() {
    waitUntil(15_000) {
        if (!finishVisibleInOverflow()) {
            onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
            waitForIdle()
        }
        finishVisibleInOverflow()
    }
    onNodeWithTag(WorkoutTestTags.FINISH).performClick()
}

fun ComposeTestRule.waitUntilFinishEnabledInOverflow(timeoutMillis: Long = 15_000) {
    waitUntil(timeoutMillis) {
        if (!finishVisibleInOverflow()) {
            onNodeWithTag(WorkoutTestTags.LIFT_OPTIONS).performClick()
            waitForIdle()
        }
        finishVisibleInOverflow() &&
            runCatching { onNodeWithTag(WorkoutTestTags.FINISH).assertIsEnabled() }.isSuccess
    }
}
