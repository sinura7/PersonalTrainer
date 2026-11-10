package com.sinura.personaltrainer.ui.start

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
        val changeTag = WorkoutPreviewTags.GYM_CHANGE
        val previewRoot = WorkoutPreviewTags.ROOT
        if (compose.onAllNodes(hasTestTag(previewRoot)).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(hasTestTag(gymTag)).fetchSemanticsNodes().isNotEmpty()
        ) {
            runCatching {
                compose.onNode(hasTestTag(previewRoot)).performScrollToNode(hasTestTag(gymTag))
            }
        }
        val deadline = System.currentTimeMillis() + 30_000
        compose.waitUntil(30_000) {
            if (System.currentTimeMillis() > deadline) return@waitUntil false
            val begins = compose.onAllNodes(hasTestTag(beginTag)).fetchSemanticsNodes()
            if (begins.any { !it.config.contains(SemanticsProperties.Disabled) }) {
                return@waitUntil true
            }
            when {
                compose.onAllNodes(hasTestTag(gymTag)).fetchSemanticsNodes().isNotEmpty() -> {
                    compose.onNodeWithTag(gymTag).performClick()
                    compose.waitForIdle()
                }
                compose.onAllNodes(hasText("Weston", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() -> {
                    compose.onNode(hasText("Weston", substring = true) and hasClickAction()).performClick()
                    compose.waitForIdle()
                }
                compose.onAllNodes(hasTestTag(changeTag)).fetchSemanticsNodes().isNotEmpty() -> {
                    compose.onNodeWithTag(changeTag).performClick()
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
