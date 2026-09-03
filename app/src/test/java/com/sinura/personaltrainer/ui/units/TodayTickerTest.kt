package com.sinura.personaltrainer.ui.units

import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.testutil.FrozenTime
import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTickerTest {
    @Test
    fun tickerCrossesMidnightOnFrozenTime() {
        val zone = "UTC"
        val day = CivilDate(2026, 9, 3)
        val almostMidnight = JvmTime.resolveLocal(
            CivilDateTime(day, 23, 59),
            zone,
        ).instantMillis + 30_000L
        val frozen = FrozenTime(almostMidnight, zone)
        val before = todayEpochDay(frozen.nowMillis(), frozen, zone)
        val wait = millisUntilNextLocalMidnight(frozen.nowMillis(), frozen, zone)
        assertTrue(wait in 1L..(60_000L))
        val after = FrozenTime(frozen.nowMillis() + wait, zone)
        assertEquals(before + 1, todayEpochDay(after.nowMillis(), after, zone))
        assertEquals(day.plusDays(1).epochDay, todayEpochDay(after.nowMillis(), after, zone))
    }
}
