package com.sinura.personaltrainer.testutil

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.ui.components.GymTags
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags

object GymStartTestKit {
    fun pickWestonIfShown(compose: ComposeTestRule) {
        val tag = GymTags.row(TestGymIds.WESTON)
        if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
        }
    }

  /** Wait for the workout floor, picking a gym first when the start gate requires one. */
    fun awaitWorkoutContent(compose: ComposeTestRule, timeoutMillis: Long = 20_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            if (compose.onAllNodes(hasTestTag(WorkoutTestTags.CONTENT)).fetchSemanticsNodes().isNotEmpty()) {
                return@waitUntil true
            }
            pickWestonIfShown(compose)
            compose.onAllNodes(hasTestTag(WorkoutTestTags.CONTENT)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun awaitTag(compose: ComposeTestRule, tag: String, timeoutMillis: Long = 20_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
                return@waitUntil true
            }
            pickWestonIfShown(compose)
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
