package com.sinura.personaltrainer.ui.start

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.testutil.TestGymIds
import com.sinura.personaltrainer.ui.components.GymTags

object StartChooserTestKit {
    fun pickWestonIfShown(compose: ComposeTestRule) {
        val tag = GymTags.row(TestGymIds.WESTON)
        if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(tag).performClick()
        }
    }

    fun waitForRoutineEnabled(compose: ComposeTestRule, routineId: String) {
        val matcher = hasTestTag(StartTags.routine(routineId))
        compose.waitUntil(5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(StartTags.routine(routineId)).assertIsEnabled()
    }
}
