package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One current lift on the floor, and a session switcher (never the library) to change it.
 *
 * Tapping the identity or the header overflow's Switch exercise, and the switcher's Add
 * exercise, are tapped through the screen in FloorScreenWiringRenderTest; what the identity
 * announces is rendered in ExerciseHeaderRenderTest. W1a moves the switch to a visible
 * control, so those are behaviour now rather than source lines.
 */
class LiftSwitcherPresentationTest {
    @Test
    fun oneCurrentLiftOpensSessionSwitcherNotLibrary() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.oneCurrentLiftOnFloor())
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.imageLedHero())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(screen.contains("CurrentLiftCard("))
        assertFalse(screen.contains("itemsIndexed("))
        assertFalse(screen.contains("items = session.exercises"))
        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("ModalBottomSheet("))
        assertTrue(switcher.contains("CurrentLiftCopy.SWITCHER_TITLE"))
        assertTrue(switcher.contains("WorkoutTestTags.LIFT_SWITCHER"))
        assertTrue(switcher.contains("WorkoutTestTags.SWITCHER_ADD_LIFT"))
        assertTrue(switcher.contains("Add exercise"))
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.addLiftLivesInSwitcher())
        val lazy = screen.indexOf("LazyColumn(")
        val switcherAt = screen.indexOf("if (liftSwitcherOpen")
        assertTrue(lazy >= 0 && switcherAt > lazy)
        assertFalse(
            "Add a lift must not sit in the set loop once a lift exists",
            screen.substring(lazy, switcherAt).contains("SecondaryGymButton"),
        )
        val identity = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse("the identity is not a library row", identity.contains("ExercisePickerSheet"))
        val overflow = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(overflow.contains("onSwitch: (() -> Unit)? = null"))
        assertTrue(overflow.contains("if (onSwitch != null)"))
        assertTrue(overflow.contains("CurrentLiftCopy.SWITCH"))
    }

    @Test
    fun notesLeaveTheLogLoop() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.notesLeaveTheLogLoop())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        val lazy = screen.indexOf("LazyColumn(")
        assertTrue(lazy >= 0)
        assertFalse(
            "NotesBlock must not sit in the set loop",
            screen.substring(lazy, screen.indexOf("if (liftSwitcherOpen")).contains("NotesBlock("),
        )
        assertTrue(screen.contains("onNotes = { notesOpen = true }"))
        assertTrue(screen.contains("notes = state.notes"))
        assertTrue(screen.contains("modifier = Modifier.testTag(WorkoutTestTags.SESSION_NOTES)"))
        assertTrue(screen.contains("onNotesChange = viewModel::setNotes"))
        val overflow = readOwned("ui/workout/WorkoutOverflowMenu.kt")
        assertTrue(overflow.contains("CurrentLiftCopy.SESSION_NOTES"))
        assertTrue(overflow.contains("onNotes()"))
        val identity = readOwned("ui/workout/ExerciseHeader.kt")
        assertFalse("notes are overflow, not identity", identity.contains("NotesBlock("))
        assertFalse(identity.contains("SESSION_NOTES"))
        val finish = readOwned("ui/components/EndWorkoutDialog.kt")
        assertTrue(finish.contains("NotesBlock("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
