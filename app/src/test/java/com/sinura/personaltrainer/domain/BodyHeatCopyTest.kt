package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHeatCopyTest {
    @Test
    fun factsNameTheWindowInSessionsAndTheLastFinish() {
        assertEquals(
            "3 sessions this week · last finished yesterday",
            BodyHeatCopy.facts(HeatWindow.CURRENT_WEEK, windowSessions = 3, daysSinceLastFinished = 1),
        )
        assertEquals(
            "1 session today · last finished today",
            BodyHeatCopy.facts(HeatWindow.DAY, windowSessions = 1, daysSinceLastFinished = 0),
        )
        assertEquals(
            "No sessions this month · last finished 40 days ago",
            BodyHeatCopy.facts(HeatWindow.CURRENT_MONTH, windowSessions = 0, daysSinceLastFinished = 40),
        )
    }

    @Test
    fun nothingFinishedYetHasNoFactsLine() {
        // EMPTY_LOG already says it; a line of zeros under it would say it twice.
        assertNull(BodyHeatCopy.facts(HeatWindow.CURRENT_WEEK, windowSessions = 0, daysSinceLastFinished = null))
    }

    @Test
    fun emptyLogTeachesTheNextTap() {
        assertTrue(BodyHeatCopy.EMPTY_LOG.startsWith("Tap a muscle"))
        assertTrue(BodyHeatCopy.EMPTY_LOG.contains("lifts that train it"))
        assertEquals("Lifts that train the figure", BodyHeatCopy.FIRST_LIFTS)
        assertEquals("See lifts", BodyHeatCopy.SEE_LIFTS)
        assertEquals("Lifts that train chest", BodyHeatCopy.liftsThatTrain(CanonicalMuscle.CHEST))
        assertEquals(
            "Find the lifts that train chest.",
            BodyHeatCopy.findLiftsInLibrary(CanonicalMuscle.CHEST),
        )
    }
}
