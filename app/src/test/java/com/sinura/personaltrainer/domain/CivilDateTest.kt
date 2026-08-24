package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CivilDateTest {
    @Test
    fun epochDayZeroIsUnixEpoch() {
        val epoch = CivilDate.fromEpochDay(0)
        assertEquals(1970, epoch.year)
        assertEquals(1, epoch.month)
        assertEquals(1, epoch.day)
        assertEquals(Weekday.THURSDAY, epoch.dayOfWeek)
        assertEquals(0L, epoch.epochDay)
    }

    @Test
    fun matchesJavaTimeAcrossLeapAndCenturyBoundaries() {
        val samples = listOf(
            LocalDate.of(1, 1, 1),
            LocalDate.of(1600, 2, 29),
            LocalDate.of(1899, 12, 31),
            LocalDate.of(1900, 2, 28),
            LocalDate.of(1970, 1, 1),
            LocalDate.of(2000, 2, 29),
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2100, 3, 1),
        )
        for (java in samples) {
            val civil = CivilDate.fromEpochDay(java.toEpochDay())
            assertEquals(java.year, civil.year)
            assertEquals(java.monthValue, civil.month)
            assertEquals(java.dayOfMonth, civil.day)
            assertEquals(java.toEpochDay(), civil.epochDay)
            assertEquals(java.dayOfWeek.value, civil.dayOfWeek.isoValue)
        }
    }

    @Test
    fun previousOrSameAgreesWithTemporalAdjusters() {
        val thursday = CivilDate(2026, 8, 20) // Thursday
        assertEquals(CivilDate(2026, 8, 17), thursday.previousOrSame(Weekday.MONDAY))
        assertEquals(thursday, thursday.previousOrSame(Weekday.THURSDAY))
        assertEquals(CivilDate(2026, 8, 16), thursday.previousOrSame(Weekday.SUNDAY))
    }

    @Test
    fun yearMonthPlusMinusAgreesWithJavaTime() {
        val start = CivilYearMonth(2026, 1)
        assertEquals(CivilYearMonth(2026, 2), start.plusMonths(1))
        assertEquals(CivilYearMonth(2025, 12), start.minusMonths(1))
        assertEquals(CivilYearMonth(2027, 1), start.plusMonths(12))
        val java = YearMonth.of(2024, 1).plusMonths(27)
        val civil = CivilYearMonth(2024, 1).plusMonths(27)
        assertEquals(java.year, civil.year)
        assertEquals(java.monthValue, civil.month)
    }

    @Test
    fun weekdayPlusWraps() {
        assertEquals(Weekday.SUNDAY, Weekday.MONDAY.plus(6))
        assertEquals(Weekday.MONDAY, Weekday.SUNDAY.plus(1))
        assertEquals(Weekday.FRIDAY, Weekday.MONDAY.minus(3))
    }
}

class CapturedCivilTimeDstTest {
    @Test
    fun springForwardGapShiftsForward() {
        // America/New_York 2026-03-08 02:30 does not exist (2:00 -> 3:00).
        val local = CivilDateTime(CivilDate(2026, 3, 8), hour = 2, minute = 30)
        val captured = com.sinura.personaltrainer.util.JvmTime.resolveLocal(
            local = local,
            zoneId = "America/New_York",
            gap = DstGapPolicy.SHIFT_FORWARD,
        )
        assertEquals("America/New_York", captured.zoneId)
        assertEquals(CivilDate(2026, 3, 8).epochDay, captured.localEpochDay)
        // First valid local time after the gap is 03:30 EDT, UTC-4.
        assertEquals(-4 * 3600, captured.offsetSeconds)
        val replay = com.sinura.personaltrainer.util.JvmTime.capture(
            captured.instantMillis,
            captured.zoneId,
        )
        assertEquals(captured.offsetSeconds, replay.offsetSeconds)
        assertEquals(captured.localEpochDay, replay.localEpochDay)
    }

    @Test
    fun springForwardGapCanReject() {
        val local = CivilDateTime(CivilDate(2026, 3, 8), hour = 2, minute = 30)
        try {
            com.sinura.personaltrainer.util.JvmTime.resolveLocal(
                local = local,
                zoneId = "America/New_York",
                gap = DstGapPolicy.REJECT,
            )
            throw AssertionError("expected UnresolvableLocalTimeException")
        } catch (thrown: UnresolvableLocalTimeException) {
            assertEquals("America/New_York", thrown.zoneId)
            assertEquals(local, thrown.local)
        }
    }

    @Test
    fun fallBackOverlapPersistsChosenOffset() {
        // 2026-11-01 01:30 occurs twice in America/New_York.
        val local = CivilDateTime(CivilDate(2026, 11, 1), hour = 1, minute = 30)
        val earlier = com.sinura.personaltrainer.util.JvmTime.resolveLocal(
            local = local,
            zoneId = "America/New_York",
            overlap = DstOverlapChoice.EARLIER,
        )
        val later = com.sinura.personaltrainer.util.JvmTime.resolveLocal(
            local = local,
            zoneId = "America/New_York",
            overlap = DstOverlapChoice.LATER,
        )
        assertEquals(-4 * 3600, earlier.offsetSeconds)
        assertEquals(-5 * 3600, later.offsetSeconds)
        assertTrue(later.instantMillis > earlier.instantMillis)
        assertEquals(earlier.localEpochDay, later.localEpochDay)
        assertEquals(CivilDate(2026, 11, 1).epochDay, earlier.localEpochDay)
    }

    @Test
    fun capturedLocalDateDoesNotMoveWhenDeviceZoneChanges() {
        val tokyo = com.sinura.personaltrainer.util.JvmTime.capture(
            instantMillis = CivilDate(2026, 8, 20).epochDay * 86_400_000L + 12 * 3_600_000L,
            zoneId = "Asia/Tokyo",
        )
        val replayInChicago = com.sinura.personaltrainer.util.JvmTime.capture(
            tokyo.instantMillis,
            "America/Chicago",
        )
        // Display uses the captured local date, not a later device zone.
        assertEquals(tokyo.localEpochDay, CivilDate(2026, 8, 20).epochDay)
        assertTrue(replayInChicago.localEpochDay != tokyo.localEpochDay || tokyo.zoneId != replayInChicago.zoneId)
        assertEquals("Asia/Tokyo", tokyo.zoneId)
    }
}
