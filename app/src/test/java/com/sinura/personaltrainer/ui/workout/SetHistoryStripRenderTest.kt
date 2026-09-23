package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.FloorStatCopy
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetRowCopy
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's sets for the current lift, as the chips the lifter taps: what each chip says,
 * what a tap opens, how big the targets are, and the two ends of the row (the set being
 * logged, and Add set once the plan is met).
 *
 * SetHistoryStrip.kt was pinned line by line — `contentDescription = "$ordinal, …"`,
 * `onClickLabel = spokenAction`, `private const val ADD_SET = "Add set"`, the state words.
 * W1a fixes the chips' double announcement and keeps one "Add set" on the floor, which
 * rewrites those lines on purpose. What must survive is here, in rendered form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SetHistoryStripRenderTest {
    @get:Rule val compose = createComposeRule()

    private var edited: String? = null
    private var deleted: String? = null
    private var openedAll = 0
    private var added = 0

    private fun showStrip(
        sets: List<SetLog> = listOf(floorSet(1, FLOOR_KG70, 10, rpe = 8), floorSet(2, FLOOR_KG70, 10, rpe = 9)),
        editingSetId: String? = null,
        receiptSetId: String? = null,
        current: CurrentSetMark? = CurrentSetMark(mark = "3", label = "Set 3 of 3"),
        showAddSet: Boolean = false,
        enabled: Boolean = true,
    ) {
        compose.showFloor {
            SetHistoryStrip(
                sets = sets,
                targetSets = 3,
                loadClass = LoadClass.LOADED,
                unit = FLOOR_UNIT,
                editingSetId = editingSetId,
                receiptSetId = receiptSetId,
                current = current,
                showAddSet = showAddSet,
                enabled = enabled,
                onEdit = { edited = it },
                onDelete = { deleted = it },
                onOpenAll = { openedAll += 1 },
                onAddSet = { added += 1 },
            )
        }
    }

    private fun spokenSet(set: SetLog) = FloorStatCopy.spokenSet(
        weightKg = set.weightKg,
        reps = set.reps,
        loadClass = LoadClass.LOADED,
        unit = FLOOR_UNIT,
        rpe = set.rpe,
    )

    @Test
    fun eachChipSpeaksItsOrdinalSetAndStateOnce() {
        val sets = listOf(floorSet(1, FLOOR_KG70, 10, rpe = 8), floorSet(2, FLOOR_KG70, 10, rpe = 9), floorSet(3, FLOOR_KG70, 11))
        showStrip(sets = sets, editingSetId = "set-3", receiptSetId = "set-2", current = null)
        val states = listOf("logged", "saved", "editing")
        sets.forEachIndexed { index, set ->
            val ordinal = SetOrdinalCopy.working(index + 1, 3)
            val chip = compose.onNodeWithTag(WorkoutTestTags.setChip(set.id))
            // W1a changes this: the chip also merges its visible line into what TalkBack
            // reads, the double announcement W1a removes. The sentence itself stays.
            assertEquals(listOf("$ordinal, ${spokenSet(set)}, ${states[index]}"), chip.spokenDescriptions())
            compose.onAllNodes(hasContentDescription("$ordinal, ", substring = true)).assertCountEquals(1)
            // "Double-tap to" names the menu this chip opens, with the chip's own ordinal.
            assertEquals(SetRowCopy.actionsFor(ordinal), chip.clickLabel())
            chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHeightIsAtLeast(Metrics.touchMin)
        }
        // The visible line is the compact set; the state is a word, not only a Volt ring.
        compose.onNodeWithText(
            FloorStatCopy.compactSet(weightKg = FLOOR_KG70, reps = 10, loadClass = LoadClass.LOADED, unit = FLOOR_UNIT, rpe = 8),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("Saved · ${SetOrdinalCopy.working(2, 3)}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Editing", useUnmergedTree = true).assertIsDisplayed()
        // A resting chip does not repeat the number its ring already shows.
        compose.onAllNodesWithText(SetOrdinalCopy.working(1, 3), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun ordinalsAreDerivedSoAWarmupDoesNotTakeAWorkingNumber() {
        val sets = listOf(floorSet(1, FLOOR_KG70, 8, warmup = true), floorSet(2, FLOOR_KG70, 10, rpe = 8))
        showStrip(sets = sets, current = null)
        val lines = SetOrdinalCopy.loggedLines(warmupFlags = listOf(true, false), targetSets = 3)
        assertEquals(listOf(SetOrdinalCopy.warmup(1), SetOrdinalCopy.working(1, 3)), lines)
        assertTrue(compose.onNodeWithTag(WorkoutTestTags.setChip("set-1")).spokenDescriptions().single().startsWith("${lines[0]}, "))
        assertTrue(compose.onNodeWithTag(WorkoutTestTags.setChip("set-2")).spokenDescriptions().single().startsWith("${lines[1]}, "))
        // The rings read W for the warm-up and 1 for the first working set.
        assertEquals(listOf(SetOrdinalCopy.WARMUP_MARK, "1"), SetOrdinalCopy.marks(listOf(true, false)))
        compose.onNodeWithText(SetOrdinalCopy.WARMUP_MARK, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aChipOpensANamedReviseDeleteMenuNotASwipe() {
        showStrip()
        val ordinal = SetOrdinalCopy.working(1, 3)
        compose.onNodeWithTag(WorkoutTestTags.setChip("set-1")).performClick()
        compose.onNodeWithText(SetRowCopy.revise(ordinal)).assertIsDisplayed()
        compose.onNodeWithText(SetRowCopy.delete(ordinal)).assertIsDisplayed().performClick()
        assertEquals("set-1", deleted)
        compose.onNodeWithText(SetRowCopy.delete(ordinal)).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.setChip("set-2")).performClick()
        compose.onNodeWithText(SetRowCopy.revise(SetOrdinalCopy.working(2, 3))).performClick()
        assertEquals("set-2", edited)
    }

    @Test
    fun theCurrentSetChipNamesTheSetBeingLogged() {
        showStrip(current = CurrentSetMark(mark = "3", label = "Set 3 of 3"))
        val current = compose.onNodeWithTag(WorkoutTestTags.CURRENT_SET).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Current set, Set 3 of 3"), current.spokenDescriptions())
        compose.onNodeWithText("Current", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Set 3 of 3", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun editOpensEverySavedSetAndIsHiddenWhenThereAreNone() {
        showStrip()
        compose.onNodeWithText("SET HISTORY").assertIsDisplayed()
        val edit = compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS)
        assertEquals(listOf("Edit saved sets"), edit.spokenDescriptions())
        edit.assertHeightIsAtLeast(Metrics.touchMin).performClick()
        assertEquals(1, openedAll)
    }

    @Test
    fun withNoSetsTheStripShowsOnlyTheCurrentChip() {
        showStrip(sets = emptyList())
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_SET).assertIsDisplayed()
    }

    @Test
    fun anEmptyStripComposesNothing() {
        showStrip(sets = emptyList(), current = null, showAddSet = false)
        compose.onNodeWithTag(WorkoutTestTags.SET_HISTORY).assertDoesNotExist()
    }

    @Test
    fun addSetIsTheLastChipOnceThePlanIsMet() {
        val sets = (1..3).map { floorSet(it, FLOOR_KG70, 10) }
        showStrip(sets = sets, current = null, showAddSet = true)
        // W1a changes this: today the floor has two "Add set" controls, this chip and the
        // dock's "Add another set"; W1a keeps one.
        val add = compose.onNodeWithTag(WorkoutTestTags.ADD_SET)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Add set"), add.spokenDescriptions())
        val addBounds = add.getBoundsInRoot()
        val lastChip = compose.onNodeWithTag(WorkoutTestTags.setChip("set-3")).getBoundsInRoot()
        assertTrue(
            "Add set follows the last saved chip, was $addBounds after $lastChip",
            addBounds.top > lastChip.top || (addBounds.top == lastChip.top && addBounds.left >= lastChip.right),
        )
        add.performClick()
        assertEquals(1, added)
    }

    @Test
    fun addSetStaysAwayUntilThePlanIsMet() {
        showStrip(showAddSet = false)
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).assertDoesNotExist()
    }

    @Test
    fun aLockedEntryLocksEveryChip() {
        showStrip(showAddSet = true, enabled = false)
        compose.onNodeWithTag(WorkoutTestTags.setChip("set-1")).assertIsNotEnabled().performClick()
        compose.onNodeWithText(SetRowCopy.revise(SetOrdinalCopy.working(1, 3))).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).assertIsNotEnabled()
        assertEquals(0, added)
    }

    @Test
    fun theHistoryIsNeverTheLogLoopsScrollAnchor() {
        showStrip(showAddSet = true)
        compose.onNodeWithTag(WorkoutTestTags.SET_HISTORY).assertIsDisplayed()
        compose.onAllNodesWithTag(WorkoutTestTags.SET_ENTRY, useUnmergedTree = true).assertCountEquals(0)
    }
}
