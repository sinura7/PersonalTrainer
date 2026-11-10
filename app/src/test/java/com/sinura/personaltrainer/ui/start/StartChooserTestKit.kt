package com.sinura.personaltrainer.ui.start

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.sinura.personaltrainer.testutil.TestGymIds
import com.sinura.personaltrainer.ui.components.GymTags
import com.sinura.personaltrainer.ui.workout.WorkoutPreviewTags

object StartChooserTestKit {
    fun pickWestonIfShown(compose: ComposeTestRule) {
        val tag = GymTags.row(TestGymIds.WESTON)
        if (compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
        }
    }

    fun waitForRoutineEnabled(compose: ComposeTestRule, routineId: String) {
        val matcher = hasTestTag(StartTags.routine(routineId))
        compose.waitUntil(5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(StartTags.routine(routineId)).assertIsEnabled()
    }

    /** Gym pick on Home planned/fallback preview before Begin is enabled. */
    fun pickWestonOnStrengthPreview(compose: ComposeTestRule) {
        val gymTag = GymTags.row(TestGymIds.WESTON)
        val beginTag = WorkoutPreviewTags.BEGIN
        val chipTag = GymTags.CHIP
        val deadline = System.currentTimeMillis() + 30_000
        compose.waitUntil(30_000) {
            if (System.currentTimeMillis() > deadline) return@waitUntil false
            val begins = compose.onAllNodes(hasTestTag(beginTag)).fetchSemanticsNodes()
            if (begins.any { !it.config.contains(SemanticsProperties.Disabled) }) {
                return@waitUntil true
            }
            when {
                compose.onAllNodes(hasTestTag(gymTag)).fetchSemanticsNodes().isNotEmpty() -> {
                    val roots = compose.onAllNodes(hasTestTag(WorkoutPreviewTags.ROOT)).fetchSemanticsNodes()
                    if (roots.isNotEmpty()) {
                        compose.onNode(hasTestTag(WorkoutPreviewTags.ROOT)).performScrollToNode(hasTestTag(gymTag))
                    }
                    compose.onNodeWithTag(gymTag).performClick()
                    compose.waitForIdle()
                }
                compose.onAllNodes(hasTestTag(chipTag)).fetchSemanticsNodes().isNotEmpty() -> {
                    compose.onNodeWithTag(chipTag).performClick()
                    compose.waitForIdle()
                    if (compose.onAllNodes(hasTestTag(gymTag)).fetchSemanticsNodes().isNotEmpty()) {
                        compose.onNodeWithTag(gymTag).performClick()
                        compose.waitForIdle()
                    }
                }
            }
            compose.onAllNodes(hasTestTag(beginTag)).fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
    }
}
