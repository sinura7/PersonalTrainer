package com.sinura.personaltrainer.ui.history

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I-04: session detail is the filled program sheet, not a grouped-text receipt.
 */
class SessionDetailSheetTest {
    @Test
    fun sessionDetailUsesTheFloorAndProgramCardLanguage() {
        val screen = readOwned("ui/history/SessionDetailScreen.kt")
        assertTrue(screen.contains("FilledLiftCard("))
        assertTrue(screen.contains("session.filledLifts()"))
        assertFalse(screen.contains("InstrumentRow("))
        assertFalse(screen.contains("private fun ExerciseBlock("))

        val card = readOwned("ui/history/FilledLiftCard.kt")
        assertTrue(card.contains("CountBadge("))
        assertTrue(card.contains("ExerciseThumb("))
        assertTrue(card.contains("ThumbSize.header"))
        assertTrue(card.contains("SessionOrderCopy.WORK"))
        assertTrue(card.contains("SessionOrderCopy.REST"))
        assertTrue(card.contains("SessionOrderCopy.LOAD"))
        assertTrue(card.contains("SetCopy.setLine"))
        assertTrue(card.contains("Surface2"))
        assertTrue(card.contains("SessionDetailTestTags.EDIT_SET"))
        assertFalse(card.contains("InstrumentRow("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
