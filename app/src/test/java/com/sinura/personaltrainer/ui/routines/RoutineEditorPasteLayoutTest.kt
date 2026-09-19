package com.sinura.personaltrainer.ui.routines

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paste failures must quote the written line and the reason. A generic
 * "could not import" banner is not enough.
 */
class RoutineEditorPasteLayoutTest {
    @Test
    fun unmatchedRowsQuoteTheLineAndTheReason() {
        val screen = readOwned("ui/routines/RoutineEditorScreen.kt")
        assertTrue(screen.contains("title = item.displayLine()"))
        assertTrue(screen.contains("subtitle = item.reason"))
        assertTrue(screen.contains("WorkoutPasteCopy.ISSUE_TITLE"))
        assertTrue(screen.contains("item.canPick"))
        assertFalse(screen.contains("Temper did not find these in the library"))
    }

    @Test
    fun wholePasteFailuresQuoteWhyNotAGenericEmptyParse() {
        val vm = readOwned("ui/routines/RoutineEditorViewModel.kt")
        assertTrue(vm.contains("WorkoutPaste.unreadableReason"))
        assertFalse(vm.contains("Could not read a workout in that text"))
        assertFalse(vm.contains("WorkoutPasteCopy.NOTHING"))
        val copy = readOwned("domain/WorkoutPasteCopy.kt")
        assertTrue(copy.contains("NO_SESSION_NAME"))
        assertTrue(copy.contains("NO_NUMBERED_LIFTS"))
        assertTrue(copy.contains("IGNORED_STARS"))
        assertFalse(copy.contains("Could not read a workout in that text"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
