package com.sinura.personaltrainer.ui.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLiftStripScrollTest {
    @Test
    fun firstBatchScrollsToTheNewestCard() {
        assertEquals(StripScrollTarget.LAST, nextStripScroll(null, 0, "a", 3))
    }

    @Test
    fun addingACardScrollsToTheNewest() {
        assertEquals(StripScrollTarget.LAST, nextStripScroll("a", 2, "a", 3))
    }

    @Test
    fun aNewFirstIdScrollsToTheStart() {
        assertEquals(StripScrollTarget.START, nextStripScroll("a", 3, "b", 2))
    }

    @Test
    fun shrinkingTheSameSessionDoesNotScroll() {
        assertEquals(null, nextStripScroll("a", 3, "a", 2))
    }

    @Test
    fun anEmptyStripDoesNotScroll() {
        assertEquals(null, nextStripScroll("a", 3, null, 0))
    }
}
