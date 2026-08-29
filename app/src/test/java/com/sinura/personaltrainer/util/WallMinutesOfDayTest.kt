package com.sinura.personaltrainer.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the DST behavior of TimePort.wallMinutesOfDay. The old
 * elapsed-since-midnight arithmetic read 17:01 on a 25-hour fall-back day
 * as minute 1081 — an 18:00 session went overdue at 17:00, and Move
 * marked it MISSED before it was due.
 */
class WallMinutesOfDayTest {
    private fun instantAt(zone: String, dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test
    fun ordinaryDayReadsTheClock() {
        val ms = instantAt("America/New_York", LocalDateTime.of(2026, 8, 29, 18, 0))
        assertEquals(18 * 60, JvmTime.wallMinutesOfDay(ms, "America/New_York"))
    }

    @Test
    fun fallBackDayStillReads1701AsMinute1021() {
        // Sunday 1 Nov 2026, America/New_York: clocks fall back at 02:00,
        // making a 25-hour day. Elapsed-since-midnight arithmetic reads
        // 17:01 as 18:01; the wall clock does not.
        val ms = instantAt("America/New_York", LocalDateTime.of(2026, 11, 1, 17, 1))
        assertEquals(17 * 60 + 1, JvmTime.wallMinutesOfDay(ms, "America/New_York"))
    }

    @Test
    fun springForwardDayStillReadsTheClock() {
        // Sunday 8 Mar 2026: 23-hour day. Elapsed arithmetic read evening
        // times an hour early; the wall clock does not.
        val ms = instantAt("America/New_York", LocalDateTime.of(2026, 3, 8, 18, 0))
        assertEquals(18 * 60, JvmTime.wallMinutesOfDay(ms, "America/New_York"))
    }

    @Test
    fun negativeUtcOffsetsStayInRange() {
        val ms = instantAt("Pacific/Auckland", LocalDateTime.of(2026, 8, 29, 0, 5))
        assertEquals(5, JvmTime.wallMinutesOfDay(ms, "Pacific/Auckland"))
    }
}
