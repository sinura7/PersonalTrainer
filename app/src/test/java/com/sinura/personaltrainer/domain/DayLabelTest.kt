package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DayLabelTest {
    private val zone = ZoneOffset.UTC
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun countsCalendarDaysNotElapsedHours() {
        // Two hours apart, but the lifter thinks of last night's session as yesterday.
        assertEquals(
            "Yesterday",
            DayLabel.relative(at("2026-08-19T23:00:00Z"), at("2026-08-20T01:00:00Z"), zone),
        )
    }

    @Test
    fun sameDayIsToday() {
        assertEquals("Today", DayLabel.relative(at("2026-08-20T06:00:00Z"), at("2026-08-20T20:00:00Z"), zone))
    }

    @Test
    fun withinAWeekCountsDays() {
        assertEquals("3 days ago", DayLabel.relative(at("2026-08-17T10:00:00Z"), at("2026-08-20T10:00:00Z"), zone))
        assertEquals("7 days ago", DayLabel.relative(at("2026-08-13T10:00:00Z"), at("2026-08-20T10:00:00Z"), zone))
    }

    @Test
    fun pastAWeekTheDateReadsBetter() {
        assertNull(DayLabel.relative(at("2026-08-12T10:00:00Z"), at("2026-08-20T10:00:00Z"), zone))
    }

    @Test
    fun futureAndMissingTimestampsHaveNoRelativeLabel() {
        assertNull(DayLabel.relative(at("2026-08-21T10:00:00Z"), at("2026-08-20T10:00:00Z"), zone))
        assertNull(DayLabel.relative(0L, at("2026-08-20T10:00:00Z"), zone))
    }
}
