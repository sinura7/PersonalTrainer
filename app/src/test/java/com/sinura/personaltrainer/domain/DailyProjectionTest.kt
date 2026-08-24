package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyProjectionTest {
    @Test
    fun twoSessionsOnOneDateRollUpWithoutSets() {
        val monday = CivilDate(2026, 8, 17).epochDay
        val projections = DailyProjectionBuilder.project(
            listOf(
                summary("cardio", monday, duration = 40, sets = 0, cardioSeconds = 2400, distance = 5000.0),
                summary("lift", monday, duration = 55, sets = 20, volume = 4000.0),
            ),
        )
        assertEquals(1, projections.size)
        val day = projections.single()
        assertEquals(2, day.sessionCount)
        assertEquals(20, day.workingSets)
        assertEquals(4000.0, day.volumeKg, 0.01)
        assertEquals(95, day.activeMinutes)
        assertEquals(2400L, day.cardioSeconds)
        assertEquals(5000.0, day.cardioDistanceMeters, 0.01)
    }

    @Test
    fun emptySummariesProjectToEmpty() {
        assertTrue(DailyProjectionBuilder.project(emptyList()).isEmpty())
    }

    private fun summary(
        id: String,
        day: Long,
        duration: Int,
        sets: Int,
        volume: Double = 0.0,
        cardioSeconds: Long = 0L,
        distance: Double? = null,
    ) = SessionSummary(
        id = id,
        routineId = null,
        routineName = id,
        date = day * 86_400_000L,
        finishedAt = day * 86_400_000L + 1,
        durationMinutes = duration,
        workingSets = sets,
        volumeKg = volume,
        localEpochDay = day,
        cardioSeconds = cardioSeconds,
        cardioDistanceMeters = distance,
    )
}
