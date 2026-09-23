package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full labelled list the set history's Edit opens: every saved set named in words,
 * a visible 48 dp overflow per row with Edit set / Delete set, and the foot's Add another
 * set once the plan is met.
 *
 * WorkoutSavedSets.kt was held by its source (`if (showAddSet) item(key = "add-set")`,
 * `.size(Metrics.touchMin).testTag(…)`, the "Edit set" / "Delete set" literals). W1a keeps
 * one "Add set" on the floor and may move this sheet's, so the sheet is described here by
 * what it shows and what its controls do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutSetsSheetRenderTest {
    @get:Rule val compose = createComposeRule()

    private var edited: String? = null
    private var deleted: String? = null
    private var added = 0
    private var dismissed = 0

    private val warmup = floorSet(1, FLOOR_KG70, 8, warmup = true)
    private val working = (2..5).map { floorSet(it, FLOOR_KG70, 10, rpe = 8) }

    private fun showSheet(sets: List<SetLog> = listOf(warmup) + working, showAddSet: Boolean = false, editingSetId: String? = null) {
        compose.showFloor {
            WorkoutSetsSheet(
                exerciseName = "Leg Extension",
                sets = sets,
                latestSetId = sets.lastOrNull()?.id,
                editingSetId = editingSetId,
                targetSets = 3,
                loadClass = LoadClass.LOADED,
                unit = FLOOR_UNIT,
                showAddSet = showAddSet,
                onEdit = { edited = it },
                onDelete = { deleted = it },
                onAddSet = { added += 1 },
                onDismiss = { dismissed += 1 },
            )
        }
    }

    @Test
    fun everySavedSetIsNamedInWordsWithItsTypeAndPlace() {
        showSheet(editingSetId = "set-3")
        compose.onNodeWithTag(WorkoutTestTags.SAVED_SETS_SHEET).assertIsDisplayed()
        compose.onNodeWithText("Saved sets").assertIsDisplayed()
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        // Warm-ups and working sets count separately; past the plan a set is an extra.
        compose.onNodeWithText("Warm-up 1").assertIsDisplayed()
        compose.onNodeWithText("Working set 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Working set 3 of 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Extra working set 1 · Latest").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Editing").performScrollTo().assertIsDisplayed()
        // Each row's set is the full labelled line, at the precision the entry uses.
        val line = SetCopy.setLine(FLOOR_KG70, 10, LoadClass.LOADED, FLOOR_UNIT, durationSeconds = null, entryPrecision = true)
        assertTrue(compose.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("RPE 8").onFirst().assertIsDisplayed()
    }

    @Test
    fun eachRowHasAFullSizeOverflowWithEditAndDelete() {
        showSheet()
        val options = compose.onNodeWithTag(WorkoutTestTags.setOptions("set-2"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assertWidthIsAtLeast(Metrics.touchMin)
        compose.onNodeWithContentDescription("Working set 1 of 3 actions").assertIsDisplayed()
        options.performClick()
        compose.onNodeWithText("Edit set").assertIsDisplayed()
        compose.onNodeWithText("Delete set").assertIsDisplayed().performClick()
        assertEquals("set-2", deleted)
        compose.onNodeWithTag(WorkoutTestTags.setOptions("set-1")).performClick()
        compose.onNodeWithText("Edit set").performClick()
        assertEquals("set-1", edited)
    }

    @Test
    fun addAnotherSetSitsAtTheFootOnceThePlanIsMet() {
        showSheet(showAddSet = true)
        // The sheet covers the dock, so it repeats the dock's "Add another set" in the same
        // words; the set history's own "Add set" chip is gone (W1a).
        compose.onNodeWithText("Add another set").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, added)
    }

    @Test
    fun addAnotherSetStaysAwayUntilThePlanIsMet() {
        showSheet(sets = listOf(warmup) + working.take(2), showAddSet = false)
        // Like the dock's, it stays away until the plan is met.
        compose.onNodeWithText("Add another set").assertDoesNotExist()
    }

    @Test
    fun doneClosesTheSheet() {
        showSheet()
        compose.onNodeWithText("Done").assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(1, dismissed)
    }
}
