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
        assertTrue(card.contains("LiftCard("))
        assertTrue(card.contains("SetTable("))
        assertTrue(card.contains("SetTableLine.fromLog"))
        assertTrue(card.contains("SessionOrderCopy.WORK"))
        assertTrue(card.contains("SessionOrderCopy.REST"))
        assertTrue(card.contains("SessionOrderCopy.LOAD"))
        assertTrue(card.contains("SessionDetailTestTags.EDIT_SET"))
        assertFalse(card.contains("InstrumentRow("))

        val liftCard = readOwned("ui/components/LiftCard.kt")
        assertTrue(liftCard.contains("fun LiftCard("))
        assertTrue(liftCard.contains("CountBadge("))
        assertTrue(liftCard.contains("ExerciseThumb("))
        assertTrue(liftCard.contains("ThumbSize.header"))
        assertTrue(liftCard.contains("EquipmentChip("))
        assertTrue(liftCard.contains("Surface2"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
