package com.sinura.personaltrainer.ui.history

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I-01: History list cards picture the first three lifts. They are not
 * a title-and-numbers receipt.
 */
class HistoryListThumbsTest {
    @Test
    fun historyListRowsPictureTheFirstThreeLifts() {
        val screen = readOwned("ui/history/HistoryScreen.kt")
        assertTrue(screen.contains("stills = entry.stills"))
        assertTrue(screen.contains("stills = summary.stills"))
        assertTrue(screen.contains("SessionLogRow("))

        val row = readOwned("ui/components/GymSurfaces.kt")
        val sessionLog = row.substringAfter("fun SessionLogRow(")
            .substringBefore("\nfun sessionRowSpoken")
        assertTrue(sessionLog.contains("stills: List<Exercise>"))
        assertTrue(sessionLog.contains("ExerciseThumb("))
        assertTrue(sessionLog.contains("SessionLogTags.STILLS"))
        assertFalse(sessionLog.contains("InstrumentRow("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
