package com.sinura.personaltrainer.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two long names that share a prefix have to stay readable before a tap.
 * A one-line ellipsis would hide the only words that differ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp", fontScale = 2f)
class ExerciseRowLongNameTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun twoLongSimilarNamesStayFullyVisibleAndReachable() {
        val close = lift(id = "close", name = "Barbell bench press with a close grip")
        val wide = lift(id = "wide", name = "Barbell bench press with a wide grip")
        compose.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExerciseRow(exercise = close, onClick = {}, modifier = Modifier.testTag("row-close"))
                ExerciseRow(exercise = wide, onClick = {}, modifier = Modifier.testTag("row-wide"))
            }
        }
        assertWhole(tag = "row-close", name = close.name)
        assertWhole(tag = "row-wide", name = wide.name)
    }

    private fun assertWhole(tag: String, name: String) {
        val row = compose.onNodeWithTag(testTag = tag)
        val minPx = with(compose.density) { Metrics.touchMin.toPx() }
        assertTrue(row.fetchSemanticsNode().size.height >= minPx)
        val layouts = mutableListOf<TextLayoutResult>()
        val textNode = compose.onNode(
            matcher = hasText(text = name) and hasAnyAncestor(matcher = hasTestTag(testTag = tag)),
            useUnmergedTree = true,
        )
        val action = textNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action?.invoke(layouts) == true)
        val layout = layouts.single()
        for (line in 0 until layout.lineCount) {
            assertFalse("$name line $line", layout.isLineEllipsized(line))
        }
    }

    private fun lift(id: String, name: String) = Exercise(
        id = id,
        name = name,
        muscleGroup = "Chest",
        notes = "",
        isCustom = false,
    )
}
