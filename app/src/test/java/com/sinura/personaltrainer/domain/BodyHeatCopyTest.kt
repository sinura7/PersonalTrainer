package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
