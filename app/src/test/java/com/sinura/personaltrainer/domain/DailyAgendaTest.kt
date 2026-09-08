package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAgendaTest {
    @Test
    fun sortsMorningCardioBeforeEveningStrength() {
        val day = 20_000L
        val cardio = occ("c", "r-c", day, 7)
        val lift = occ("s", "r-s", day, 18)
        val other = occ("x", "r-x", day + 1, 7)
        val items = DailyAgenda.forDay(
            day,
            listOf(lift, other, cardio),
            listOf(
                rule("r-c", ScheduleModality.CARDIO),
                rule("r-s", ScheduleModality.STRENGTH),
            ),
        )
        assertEquals(listOf("c", "s"), items.map { it.occurrence.id })
        assertEquals("Cardio", items[0].title)
        assertEquals("Strength", items[1].title)
        assertEquals("07:00", items[0].timeLabel)
    }

    @Test
    fun typedCardioTitleUsesTheCardioName() {
        val day = 20_000L
        val items = DailyAgenda.forDay(
            day,
            listOf(occ("c", "r-c", day, 7)),
            listOf(
                rule("r-c", ScheduleModality.CARDIO)
                    .copy(templateId = ScheduleKind.cardio(CardioType.WALK)),
            ),
        )
        assertEquals("Walk", items.single().title)
    }

    @Test
    fun auxiliaryTitleUsesThePackName() {
        val day = 20_000L
        val items = DailyAgenda.forDay(
            day,
            listOf(occ("a", "r-a", day, 20)),
            listOf(
                rule("r-a", ScheduleModality.STRENGTH)
                    .copy(templateId = ScheduleKind.aux("stretch")),
            ),
        )
        assertEquals("Stretch", items.single().title)
    }

    @Test
    fun startableKeepsOnlyPlanned() {
        val day = 20_000L
        val planned = occ("p", "r", day, 7)
        val done = planned.copy(id = "d", status = OccurrenceStatus.DONE)
        val items = DailyAgenda.forDay(day, listOf(planned, done), listOf(rule("r", ScheduleModality.CARDIO)))
        assertEquals(listOf("p"), DailyAgenda.startable(items).map { it.occurrence.id })
    }

    @Test
    fun todayMissedIsStartable() {
        val day = 20_000L
        val missed = occ("m", "r", day, 19).copy(status = OccurrenceStatus.MISSED)
        val leftover = occ("y", "r-y", day - 1, 18).copy(status = OccurrenceStatus.MISSED)
        val done = occ("d", "r-d", day, 7).copy(status = OccurrenceStatus.DONE)
        val future = occ("f", "r-f", day + 1, 18).copy(status = OccurrenceStatus.MISSED)
        val items = DailyAgenda.forDay(
            day,
            listOf(missed, done),
            listOf(rule("r", ScheduleModality.STRENGTH), rule("r-d", ScheduleModality.STRENGTH)),
        )
        assertTrue(DailyAgenda.canOpenStart(items.first { it.occurrence.id == "m" }, day))
        assertTrue(
            DailyAgenda.canOpenStart(
                DailyAgenda.forDay(
                    day - 1,
                    listOf(leftover),
                    listOf(rule("r-y", ScheduleModality.STRENGTH)),
                ).single(),
                day,
            ),
        )
        assertTrue(!DailyAgenda.canOpenStart(items.first { it.occurrence.id == "d" }, day))
        assertTrue(
            !DailyAgenda.canOpenStart(
                DailyAgenda.forDay(
                    day + 1,
                    listOf(future),
                    listOf(rule("r-f", ScheduleModality.STRENGTH)),
                ).single(),
                day,
            ),
        )
        assertTrue(DailyAgenda.startable(items).isEmpty())
    }

    /**
     * Skipping is a decision about a day, not about the day's existence.
     * Home used to drop the click silently the moment a row was skipped, so
     * changing your mind meant there was no way back to the session at all.
     */
    @Test
    fun todaySkippedIsStartableButAnEarlierSkipIsNot() {
        val day = 20_000L
        val skippedToday = occ("s", "r-s", day, 18).copy(status = OccurrenceStatus.SKIPPED)
        val skippedBefore = occ("b", "r-b", day - 1, 18).copy(status = OccurrenceStatus.SKIPPED)
        val today = DailyAgenda.forDay(
            day,
            listOf(skippedToday),
            listOf(rule("r-s", ScheduleModality.STRENGTH)),
        ).single()
        val earlier = DailyAgenda.forDay(
            day - 1,
            listOf(skippedBefore),
            listOf(rule("r-b", ScheduleModality.STRENGTH)),
        ).single()

        assertTrue(DailyAgenda.canOpenStart(today, day))
        assertTrue(!DailyAgenda.canOpenStart(earlier, day))
        // isLeftover counts only PLANNED and MISSED, so a skip never returns
        // through Still open; the row is reachable on its own day or not at all.
        assertTrue(!MoveToToday.isLeftover(skippedBefore, day))
        // startable() is the planned-only list and must not widen with this.
        assertTrue(DailyAgenda.startable(listOf(today)).isEmpty())
    }

    /**
     * A done row keeps a finished session behind it. Starting it again would
     * bind a second session to one occurrence, so it stays closed on its own
     * day too — this is the guard for that, not an accident of ordering.
     */
    @Test
    fun todayDoneStaysClosed() {
        val day = 20_000L
        val done = occ("d", "r-d", day, 18).copy(status = OccurrenceStatus.DONE)
        val item = DailyAgenda.forDay(
            day,
            listOf(done),
            listOf(rule("r-d", ScheduleModality.STRENGTH)),
        ).single()
        assertTrue(!DailyAgenda.canOpenStart(item, day))
    }

    @Test
    fun strengthTitlePrefersTheRoutineName() {
        val day = 20_000L
        val planned = occ("s", "r-s", day, 18)
        val items = DailyAgenda.forDay(
            day,
            listOf(planned),
            listOf(rule("r-s", ScheduleModality.STRENGTH).copy(routineId = "routine-1")),
            mapOf("routine-1" to "Monday"),
        )
        assertEquals("Monday", items.single().title)
    }

    @Test
    fun twoADayMarksOnlyDaysWithTwoOccurrences() {
        val day = 20_000L
        val marked = DailyAgenda.twoADayEpochDays(
            listOf(
                occ("c", "r-c", day, 7),
                occ("s", "r-s", day, 18),
                occ("x", "r-x", day + 1, 7),
            ),
        )
        assertEquals(setOf(day), marked)
    }

    @Test
    fun dayStackSortsCardioThenMainThenLaterStrength() {
        val day = 20_000L
        val cardio = occ("c", "r-c", day, 7)
        val main = occ("s", "r-s", day, 18)
        val extra = occ("e", "r-e", day, 20)
        val items = DailyAgenda.forDay(
            day,
            listOf(extra, cardio, main),
            listOf(
                rule("r-c", ScheduleModality.CARDIO),
                rule("r-s", ScheduleModality.STRENGTH),
                rule("r-e", ScheduleModality.STRENGTH).copy(routineId = "routine-extra"),
            ),
            mapOf("routine-extra" to "Monday extra"),
        )
        assertEquals(listOf("c", "s", "e"), items.map { it.occurrence.id })
        assertEquals(listOf("Cardio", "Strength", "Monday extra"), items.map { it.title })
        assertEquals(setOf(day), DailyAgenda.twoADayEpochDays(listOf(cardio, main, extra)))
    }

    @Test
    fun stillOpenListsEarlierPlannedAndMissed() {
        val friday = 20_000L
        val saturday = friday + 1
        val weekStart = friday - 4
        val leftover = occ("fri", "r-fri", friday, 18)
        val missed = leftover.copy(id = "miss", status = OccurrenceStatus.MISSED)
        val today = occ("sat", "r-sat", saturday, 18)
        val skipped = leftover.copy(id = "skip", status = OccurrenceStatus.SKIPPED)
        val open = DailyAgenda.stillOpen(
            saturday,
            weekStart,
            listOf(leftover, missed, today, skipped),
            listOf(
                rule("r-fri", ScheduleModality.STRENGTH).copy(routineId = "r-friday"),
                rule("r-sat", ScheduleModality.STRENGTH),
            ),
            mapOf("r-friday" to "Friday"),
        )
        assertEquals(listOf("fri", "miss"), open.map { it.occurrence.id })
        assertEquals("Friday", open.first().title)
    }

    @Test
    fun stillOpenIncludesPreviousWeekOnMonday() {
        val thisMonday = 20_000L
        val lastSunday = thisMonday - 1
        val leftover = occ("sun", "r-sun", lastSunday, 18)
        val open = DailyAgenda.stillOpen(
            thisMonday,
            thisMonday,
            listOf(leftover),
            listOf(rule("r-sun", ScheduleModality.STRENGTH).copy(routineId = "r-sunday")),
            mapOf("r-sunday" to "Sunday"),
        )
        assertEquals(listOf("sun"), open.map { it.occurrence.id })
        assertEquals("Sunday", open.single().title)
    }

    private fun rule(id: String, modality: ScheduleModality) = ScheduleRule(
        id = id,
        weekday = Weekday.MONDAY,
        hour = 7,
        minute = 0,
        modality = modality,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private fun occ(id: String, ruleId: String, day: Long, hour: Int) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = OccurrenceStatus.PLANNED,
        captured = CapturedCivilTime(1L, "UTC", 0, day),
        hour = hour,
        minute = 0,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )
}
