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
        assertTrue(screen.contains("CurrentLiftCard(") || screen.contains("ExerciseHero("))
        assertTrue(screen.contains("onOpenSwitcher = { liftSwitcherOpen = true }"))
        assertTrue(screen.contains("LiftSwitcherSheet("))
        assertFalse(screen.contains("itemsIndexed("))
        assertFalse(screen.contains("items = session.exercises"))
        val switcher = readOwned("ui/workout/LiftSwitcherSheet.kt")
        assertTrue(switcher.contains("ModalBottomSheet("))
        assertTrue(switcher.contains("CurrentLiftCopy.SWITCHER_TITLE"))
        assertTrue(switcher.contains("WorkoutTestTags.LIFT_SWITCHER"))
        assertTrue(switcher.contains("WorkoutTestTags.SWITCHER_ADD_LIFT"))
        assertTrue(switcher.contains("Add exercise"))
        assertTrue(screen.contains("onAddLift"))
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.addLiftLivesInSwitcher())
        val lazy = screen.indexOf("LazyColumn(")
        val switcherAt = screen.indexOf("if (liftSwitcherOpen")
        assertTrue(lazy >= 0 && switcherAt > lazy)
        assertFalse(
            "Add a lift must not sit in the set loop once a lift exists",
            screen.substring(lazy, switcherAt).contains("SecondaryGymButton"),
        )
        assertTrue(
            readOwned("ui/workout/CurrentLiftCard.kt").contains("CurrentLiftCopy.cardSpoken") ||
                readOwned("ui/workout/CurrentLiftCard.kt").contains("CurrentLiftCopy.heroSpoken"),
        )
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
