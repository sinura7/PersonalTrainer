package com.sinura.personaltrainer.ui.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's session card names lifts as an ordered list, not a wrapping
 * middot sentence. The stills, the count line, and Start stay.
 */
class DayBlockLayoutTest {
    @Test
    fun sessionOrderIsANumberedListNotARunOnSentence() {
        val block = readOwned("ui/home/DayBlock.kt")
        assertTrue(block.contains("SessionOrderList"))
        assertTrue(block.contains("private fun SessionOrderList"))
        assertTrue(block.contains("lines.forEach"))
        assertFalse(block.contains("joinToString(\" · \")"))
        val order = block.substringAfter("if (lines.names.isNotEmpty())")
            .substringBefore("lines.meta")
        assertTrue(order.contains("SessionOrderList("))
        assertFalse(order.contains("maxLines = 2"))
        assertFalse(order.contains("Text(\n            names,"))

        val copy = readOwned("domain/DayBlockCopy.kt")
        assertTrue(copy.contains("shown + \"+\$rest\""))
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
