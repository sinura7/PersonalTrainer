package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.ui.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet G: undo tokens stack LIFO with a short cap, and the dwell honours the
 * accessibility timeout without ever dropping below the 6 s base.
 */
class UndoQueueTest {
    @Test
    fun pushAppendsAndPopRevealsTheNextOffer() {
        val stacked = UndoQueue.push(UndoQueue.push(emptyList(), "first"), "second")
        assertEquals(listOf("first", "second"), stacked)
        assertEquals(listOf("first"), UndoQueue.pop(stacked))
        assertEquals(emptyList<String>(), UndoQueue.pop(emptyList<String>()))
    }

    @Test
    fun pushPastMaxDepthDropsTheOldestSilently() {
        var stack = emptyList<Int>()
        repeat(UndoQueue.MAX_DEPTH + 2) { stack = UndoQueue.push(stack, it) }
        assertEquals(UndoQueue.MAX_DEPTH, stack.size)
        assertTrue(stack.contains(UndoQueue.MAX_DEPTH + 1))
        assertEquals((2..(UndoQueue.MAX_DEPTH + 1)).toList(), stack)
    }

    @Test
    fun dwellStaysAtBaseWithoutARecommendation() {
        val base = Motion.STATUS_DWELL_MS
        assertEquals(base, UndoDwell.dwellMs(base, null))
        assertEquals(base, UndoDwell.dwellMs(base, 1_000L))
        assertEquals(base, UndoDwell.dwellMs(base, -1L))
    }

    @Test
    fun dwellExtendsUnderAccessibilityTimeout() {
        val base = Motion.STATUS_DWELL_MS
        assertEquals(20_000L, UndoDwell.dwellMs(base, 20_000L))
        assertEquals(base, UndoDwell.dwellMs(base, base))
    }
}
