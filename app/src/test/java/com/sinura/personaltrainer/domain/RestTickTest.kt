package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTickTest {
    private val endsAt = 100_000L

    @Test
    fun boundariesSitOneSecondApartBeforeTheDeadline() {
        assertEquals(95_000L, RestTick.tickAt(endsAt, 5))
        assertEquals(99_000L, RestTick.tickAt(endsAt, 1))
    }

    @Test
    fun theNextTickIsTheFirstBoundaryStrictlyAhead() {
        assertEquals(5, RestTick.nextTick(endsAt, nowElapsedRealtime = 10_000L))
        assertEquals(5, RestTick.nextTick(endsAt, nowElapsedRealtime = 94_999L))
        // Exactly on the five-second boundary: that tick is firing now, so ask for four.
        assertEquals(4, RestTick.nextTick(endsAt, nowElapsedRealtime = 95_000L))
        assertEquals(1, RestTick.nextTick(endsAt, nowElapsedRealtime = 98_500L))
        assertNull(RestTick.nextTick(endsAt, nowElapsedRealtime = 99_000L))
        assertNull(RestTick.nextTick(endsAt, nowElapsedRealtime = 120_000L))
    }

    @Test
    fun walkingTheBoundariesTicksFiveFourThreeTwoOneAndStops() {
        val seen = mutableListOf<Int>()
        var now = 0L
        while (true) {
            val next = RestTick.nextTick(endsAt, now) ?: break
            now = RestTick.tickAt(endsAt, next)
            seen += next
        }
        assertEquals(listOf(5, 4, 3, 2, 1), seen)
    }

    /** A +15 s between the post and the fire moves the deadline; the old tick is no longer due. */
    @Test
    fun aTickIsDueOnlyDuringItsOwnSecond() {
        assertTrue(RestTick.isDue(endsAt, 5, nowElapsedRealtime = 95_000L))
        assertTrue(RestTick.isDue(endsAt, 5, nowElapsedRealtime = 95_999L))
        assertFalse(RestTick.isDue(endsAt, 5, nowElapsedRealtime = 96_000L))
        assertFalse(RestTick.isDue(endsAt, 5, nowElapsedRealtime = 94_999L))
        assertFalse(RestTick.isDue(endsAt + 15_000L, 5, nowElapsedRealtime = 95_000L))
    }
}
