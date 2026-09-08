package com.sinura.personaltrainer.ui.history

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStrongerTest {
    @Test
    fun horizonPickerLivesInTheListAndCalendarStartsAsAWeek() {
        val screen = readOwned("ui/history/HistoryScreen.kt")
        val lazy = screen.indexOf("LazyColumn(")
        val picker = screen.indexOf("HorizonPicker(")
        assertTrue("LazyColumn must still be composed", lazy >= 0)
        assertTrue("HorizonPicker must still be composed", picker >= 0)
        assertTrue("HorizonPicker must sit inside the scrolling list", picker > lazy)
        assertTrue(screen.contains("instrumentAnimateItem()"))
        assertTrue(screen.contains("monthExpanded"))
        assertTrue(screen.contains("showMonth = monthExpanded"))
        assertTrue(screen.contains("progress = state.horizonProgress"))
        assertTrue(screen.contains("HistoryTags.MOVED_MOST"))

        val calendar = readOwned("ui/history/TrainingCalendarCard.kt")
        assertTrue(calendar.contains("showMonth"))
        assertTrue(calendar.contains("HistoryCopy.CALENDAR_MONTH"))
        assertTrue(calendar.contains("weekContaining("))
        assertTrue(calendar.contains("weeksToShow"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
