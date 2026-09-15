package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftSwitcherPresentationTest {
    @Test
    fun oneCurrentLiftOpensSessionSwitcherNotLibrary() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.oneCurrentLiftOnFloor())
        val screen = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertTrue(screen.contains("CurrentLiftCard("))
        assertTrue(screen.contains("onOpenSwitcher = { liftSwitcherOpen = true }"))
        assertTrue(screen.contains("LiftSwitcherSheet("))
        assertFalse(screen.contains("itemsIndexed("))
        assertFalse(screen.contains("items = session.exercises"))
        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("ModalBottomSheet("))
        assertTrue(switcher.contains("CurrentLiftCopy.SWITCHER_TITLE"))
        assertTrue(switcher.contains("WorkoutTestTags.LIFT_SWITCHER"))
        assertFalse(switcher.contains("ExercisePickerSheet"))
        assertFalse(switcher.contains("Library"))
        assertTrue(readOwned("ui/workout/CurrentLiftCard.kt").contains("CurrentLiftCopy.CURRENT"))
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
        val overflow = readOwned("ui/workout/CurrentLiftCard.kt")
        assertTrue(overflow.contains("CurrentLiftCopy.SESSION_NOTES"))
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
