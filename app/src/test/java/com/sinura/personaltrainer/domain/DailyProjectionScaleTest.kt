package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyProjectionScaleTest {
    @Test
    fun fiveHundredSummariesProjectUnderASecond() {
        val summaries = List(500) { index ->
            SessionSummary(
                id = "sum-$index",
                routineId = null,
                routineName = "Scale $index",
                date = 1_700_000_000_000L + index * 86_400_000L,
                finishedAt = 1_700_000_000_000L + index * 86_400_000L + 1,
                durationMinutes = 45,
                workingSets = 30,
                volumeKg = 3_000.0,
                localEpochDay = 20_000L + index.toLong(),
            )
        }
        val started = System.nanoTime()
        val projections = DailyProjectionBuilder.project(summaries)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(500, projections.size)
        assertEquals(500, projections.sumOf { it.sessionCount })
        assertTrue("projection ${elapsedMs}ms exceeded 1000ms", elapsedMs <= 1_000L)
    }
}
