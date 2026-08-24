package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceGeneratorTest {
    private val weekStart = CivilDate(2026, 8, 17) // Monday
    private val zone = "UTC"

    @Test
    fun generatesPlannedOccurrencesWithoutShifting() {
        val rules = listOf(
            rule("r-mon", Weekday.MONDAY, hour = 18),
            rule("r-wed", Weekday.WEDNESDAY, hour = 7, modality = ScheduleModality.CARDIO),
        )
        val week = OccurrenceGenerator.generateWeek(weekStart, rules, emptyList(), JvmTime, zone, NOW)
        assertEquals(2, week.size)
        assertEquals(weekStart.epochDay, week[0].localEpochDay)
        assertEquals(18, week[0].hour)
        assertEquals(weekStart.plusDays(2).epochDay, week[1].localEpochDay)
        assertEquals(7, week[1].hour)
        assertTrue(week.all { it.status == OccurrenceStatus.PLANNED })
    }

    @Test
    fun keepsExistingDoneAndDoesNotInventAShift() {
        val rules = listOf(rule("r-mon", Weekday.MONDAY, hour = 18))
        val done = occ("occ-r-mon-${weekStart.epochDay}", "r-mon", weekStart.epochDay, OccurrenceStatus.DONE)
        val week = OccurrenceGenerator.generateWeek(weekStart, rules, listOf(done), JvmTime, zone, NOW)
        assertEquals(1, week.size)
        assertEquals(OccurrenceStatus.DONE, week.single().status)
        assertEquals(weekStart.epochDay, week.single().localEpochDay)
    }

    @Test
    fun disabledRulesDoNotGenerate() {
        val rules = listOf(rule("r-mon", Weekday.MONDAY, hour = 18, enabled = false))
        val week = OccurrenceGenerator.generateWeek(weekStart, rules, emptyList(), JvmTime, zone, NOW)
        assertTrue(week.isEmpty())
    }

    @Test
    fun twoRulesOnOneWeekdayStayIndependent() {
        val rules = listOf(
            rule("r-cardio", Weekday.FRIDAY, hour = 7, modality = ScheduleModality.CARDIO),
            rule("r-lift", Weekday.FRIDAY, hour = 18),
        )
        val week = OccurrenceGenerator.generateWeek(weekStart, rules, emptyList(), JvmTime, zone, NOW)
        val friday = week.filter { it.localEpochDay == weekStart.plusDays(4).epochDay }
        assertEquals(2, friday.size)
        assertEquals(listOf(7, 18), friday.map { it.hour })
    }

    private fun rule(
        id: String,
        weekday: Weekday,
        hour: Int,
        modality: ScheduleModality = ScheduleModality.STRENGTH,
        enabled: Boolean = true,
    ) = ScheduleRule(
        id = id,
        weekday = weekday,
        hour = hour,
        minute = 0,
        modality = modality,
        enabled = enabled,
        createdAtMs = NOW,
        updatedAtMs = NOW,
    )

    private fun occ(
        id: String,
        ruleId: String,
        epochDay: Long,
        status: OccurrenceStatus,
    ) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = status,
        captured = CapturedCivilTime(NOW, zone, 0, epochDay),
        hour = 18,
        minute = 0,
        createdAtMs = NOW,
        updatedAtMs = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
