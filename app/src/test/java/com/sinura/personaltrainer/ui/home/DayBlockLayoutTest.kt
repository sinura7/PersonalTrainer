package com.sinura.personaltrainer.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's session card pictures each lift beside its number and name.
 * The 4-up still strip is gone; a typical session names every lift.
 */
class DayBlockLayoutTest {
    @Test
    fun sessionOrderIsStillPlusNameRowsNotAStripOrARunOnSentence() {
        val block = readOwned("ui/home/DayBlock.kt")
        assertTrue(block.contains("SessionLiftRows"))
        assertTrue(block.contains("private fun SessionLiftRows"))
        assertTrue(block.contains("private fun SessionLiftRow"))
        assertTrue(block.contains("ExerciseThumb(exercise = exercise)"))
        assertTrue(block.contains("INDEX_WIDTH"))
        assertFalse(block.contains("SessionOrderList"))
        assertFalse(block.contains("joinToString(\" · \")"))
        assertFalse(block.contains("STILL_LIMIT"))
        val order = block.substringAfter("if (lines.names.isNotEmpty())")
            .substringBefore("lines.meta")
        assertTrue(order.contains("SessionLiftRows("))
        assertFalse(order.contains("Row(horizontalArrangement"))
        assertFalse(order.contains("exercises.take("))

        val copy = readOwned("domain/DayBlockCopy.kt")
        assertTrue(copy.contains("const val ROW_LIMIT = 8"))
        assertTrue(copy.contains("shown + \"+\$rest\""))
        assertFalse(copy.contains("STILL_LIMIT"))
        assertFalse(copy.contains("joinToString(\" · \")"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
