package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five worked examples from the signed schedule semantics, plus the satisfaction rules
 * underneath them.
 *
 * These are the tests that make the plan a plan. Every one of them asserts something the old
 * planner could not promise: that the week you pinned is the week you get back, that a day you
 * miss shifts rather than vanishing, and that stored slots are never rewritten by the act of
 * looking at them.
 *
 * Fixture week: **Mon 24 – Sun 30 Aug 2026**, week start Monday. Baseline slots are
 * `[0: Push @Mon, 1: Pull (no anchor), 2: Legs @Fri]`, matching the signed document.
 */
class WeekDerivationTest {

    // -----------------------------------------------------------------------
    // The five signed worked examples
    // -----------------------------------------------------------------------

    @Test
    fun missedAnchoredDayShiftsForwardNeverSkips() {
        // Monday passes with nothing logged. Tuesday morning.
        val week = derive(slots = baseline(), today = TUE)

        assertEquals("Push", nameOn(week, TUE_EPOCH))
        assertEquals("Pull", nameOn(week, WED_EPOCH))
        assertEquals("Legs", nameOn(week, FRI_EPOCH))
        assertTrue("Monday must be rest — the shifted slot cannot also stay behind", isRest(week, MON_EPOCH))
        assertEquals(TUE_EPOCH, week.nextUp?.epochDay)
    }

    @Test
    fun missedUnanchoredDayKeepsCycleOrder() {
        // Push logged Monday, nothing since; today is Thursday. Signed example 2.
        val week = derive(
            slots = baseline(),
            today = THU,
            history = listOf(finished(id = "s1", routineId = PUSH, day = MON)),
        )

        assertEquals("Push", nameOn(week, MON_EPOCH))
        assertEquals("s1", week.days.first { it.epochDay == MON_EPOCH }.satisfiedBySessionId)
        assertEquals("Pull", nameOn(week, THU_EPOCH))
        assertEquals("Legs", nameOn(week, FRI_EPOCH))
        assertEquals(THU_EPOCH, week.nextUp?.epochDay)

        // …and by Friday, cycle order takes Legs' own anchor day and Legs shifts to Saturday.
        val friday = derive(
            slots = baseline(),
            today = FRI,
            history = listOf(finished(id = "s1", routineId = PUSH, day = MON)),
        )
        assertEquals("Pull", nameOn(friday, FRI_EPOCH))
        assertEquals("Legs", nameOn(friday, SAT_EPOCH))
    }

    @Test
    fun weekRolloverResetsSatisfaction() {
        val fullyTrained = listOf(
            finished(id = "s1", routineId = PUSH, day = MON),
            finished(id = "s2", routineId = PULL, day = LocalDate.of(2026, 8, 26)),
            finished(id = "s3", routineId = LEGS, day = LocalDate.of(2026, 8, 28)),
        )
        val nextMonday = LocalDate.of(2026, 8, 31)
        val week = derive(slots = baseline(), today = nextMonday, history = fullyTrained)

        // Last week's sessions are outside this week, so nothing is satisfied and the week is
        // the baseline again. An unfinished slot is not owed forward as debt.
        assertTrue(week.days.all { it.satisfiedBySessionId == null })
        assertEquals("Push", nameOn(week, nextMonday.toEpochDay()))
        assertEquals("Pull", nameOn(week, nextMonday.plusDays(1).toEpochDay()))
        assertEquals("Legs", nameOn(week, nextMonday.plusDays(4).toEpochDay()))
        assertEquals(nextMonday.toEpochDay(), week.nextUp?.epochDay)
    }

    @Test
    fun suggestedFillsNeverTouchUserSlots() {
        val plan = WeeklySchedulePlanner.plan(
            preferences = PREFS.copy(trainingDaysPerWeek = 3),
            snapshot = emptySnapshot(),
            recommendations = emptyList(),
            routines = routines(),
            recentSessions = emptyList(),
            nowMs = millis(MON),
            zone = ZoneOffset.UTC,
            pinnedSlots = listOf(slot(0, PUSH, anchor = Weekday.MONDAY)),
        )

        val monday = plan.days.first { it.epochDay == MON_EPOCH }
        assertEquals("the pinned day is echoed, not proposed over", "slot-0", monday.slotId)
        assertEquals(PUSH, monday.routineId)

        plan.days.filterNot { it.isRest }.filter { it.slotId == null }.forEach { proposal ->
            assertTrue("a proposal landed on the pinned day", proposal.epochDay != MON_EPOCH)
            assertTrue("a proposal landed in the past", proposal.epochDay >= MON_EPOCH)
        }
    }

    @Test
    fun routineDeletionHealsDerivedWeek() {
        // The Pull routine is deleted; CASCADE takes its slot with it.
        val week = derive(slots = listOf(baseline()[0], baseline()[2]), today = MON)

        assertEquals("Push", nameOn(week, MON_EPOCH))
        assertTrue(isRest(week, TUE_EPOCH))
        assertEquals("Legs", nameOn(week, FRI_EPOCH))
        assertTrue("no dangling day", week.days.count { !it.isRest } == 2)
    }

    // -----------------------------------------------------------------------
    // Satisfaction
    // -----------------------------------------------------------------------

    @Test
    fun slotSatisfiedByMatchingRoutineSession() {
        val week = derive(
            slots = baseline(),
            today = TUE,
            history = listOf(finished(id = "s1", routineId = PUSH, day = MON)),
        )
        assertEquals("s1", week.days.first { it.epochDay == MON_EPOCH }.satisfiedBySessionId)
        assertEquals("the next undone slot is Pull", TUE_EPOCH, week.nextUp?.epochDay)
        assertEquals("Pull", nameOn(week, TUE_EPOCH))
    }

    @Test
    fun sessionSatisfiesOnlyOneSlot() {
        // Two identical Push slots; one Push session. Exactly one is credited.
        val slots = listOf(
            slot(0, PUSH, anchor = Weekday.MONDAY),
            slot(1, PUSH, anchor = Weekday.THURSDAY),
        )
        val week = derive(
            slots = slots,
            today = MON,
            history = listOf(finished(id = "s1", routineId = PUSH, day = MON)),
        )
        assertEquals(1, week.days.count { it.satisfiedBySessionId != null })
        assertEquals(THU_EPOCH, week.nextUp?.epochDay)
    }

    @Test
    fun focusSlotSatisfiedByCompatibleSession() {
        val slots = listOf(focusSlot(0, SessionFocusKind.PULL, anchor = Weekday.TUESDAY))
        val session = finished(id = "s1", routineId = null, day = MON).copy(routineName = "Pull")
        val week = derive(slots = slots, today = TUE, history = listOf(session))

        assertEquals("s1", week.days.first { it.epochDay == MON_EPOCH }.satisfiedBySessionId)
        assertNull("nothing is left undone", week.nextUp)
    }

    @Test
    fun satisfiedSlotDisplaysOnItsSessionDay() {
        // Pinned to Monday, actually trained on Wednesday. The week shows what happened.
        val week = derive(
            slots = listOf(slot(0, PUSH, anchor = Weekday.MONDAY)),
            today = THU,
            history = listOf(finished(id = "s1", routineId = PUSH, day = WED)),
        )
        assertTrue(isRest(week, MON_EPOCH))
        assertEquals("Push", nameOn(week, WED_EPOCH))
        assertEquals("s1", week.days.first { it.epochDay == WED_EPOCH }.satisfiedBySessionId)
    }

    @Test
    fun unsatisfiedSlotsNeverPlacedBeforeToday() {
        val week = derive(slots = baseline(), today = SAT)
        week.days.filter { it.slot != null && it.satisfiedBySessionId == null }.forEach { day ->
            assertTrue(
                "an undone slot was placed on ${day.dayOfWeek}, before today",
                day.epochDay >= SAT_EPOCH,
            )
        }
        // Two slots fit in [Sat, Sun]; the third simply is not shown. The cycle does not
        // restart mid-week and nothing is owed forward.
        assertEquals(2, week.days.count { it.slot != null })
    }

    @Test
    fun derivedPlanCarriesSlotIdsAndAnHonestSummary() {
        val week = derive(
            slots = baseline(),
            today = TUE,
            history = listOf(finished(id = "s1", routineId = PUSH, day = MON)),
        )
        val plan = WeekDerivation.toWeeklySchedulePlan(week, routines(), PREFS, millis(TUE))

        assertEquals("3 pinned · 1 logged this week", plan.summary)
        assertTrue(plan.days.filterNot { it.isRest }.all { it.slotId != null })
        assertEquals("Logged.", plan.days.first { it.epochDay == MON_EPOCH }.reason)
        assertEquals("Pinned to your week.", plan.days.first { it.epochDay == TUE_EPOCH }.reason)
        assertEquals(false, plan.thinHistory)
    }

    @Test
    fun anEmptyWeekSaysSoRatherThanInventingOne() {
        val week = derive(slots = emptyList(), today = MON)
        val plan = WeekDerivation.toWeeklySchedulePlan(week, routines(), PREFS, millis(MON))

        assertEquals(7, plan.days.size)
        assertTrue(plan.days.all { it.isRest })
        assertEquals("No sessions pinned yet.", plan.summary)
        assertNull(week.nextUp)
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun derive(
        slots: List<ScheduleSlot>,
        today: LocalDate,
        history: List<WorkoutSession> = emptyList(),
    ): DerivedWeek = WeekDerivation.derive(
        slots = slots,
        history = history,
        preferences = PREFS,
        nowMs = millis(today),
        zone = ZoneOffset.UTC,
    )

    private fun nameOn(week: DerivedWeek, epochDay: Long): String? {
        val slot = week.days.first { it.epochDay == epochDay }.slot ?: return null
        return routines().firstOrNull { it.id == slot.routineId }?.name ?: slot.focusKind?.label
    }

    private fun isRest(week: DerivedWeek, epochDay: Long): Boolean =
        week.days.first { it.epochDay == epochDay }.slot == null

    private fun baseline(): List<ScheduleSlot> = listOf(
        slot(0, PUSH, anchor = Weekday.MONDAY),
        slot(1, PULL, anchor = null),
        slot(2, LEGS, anchor = Weekday.FRIDAY),
    )

    private fun slot(position: Int, routineId: String, anchor: Weekday?): ScheduleSlot =
        ScheduleSlot(
            id = "slot-$position",
            position = position,
            routineId = routineId,
            focusKind = null,
            anchorDay = anchor,
            createdAt = 0L,
            updatedAt = 0L,
        )

    private fun focusSlot(position: Int, kind: SessionFocusKind, anchor: Weekday?): ScheduleSlot =
        ScheduleSlot(
            id = "slot-$position",
            position = position,
            routineId = null,
            focusKind = kind,
            anchorDay = anchor,
            createdAt = 0L,
            updatedAt = 0L,
        )

    private fun routines(): List<Routine> = listOf(
        routine(PUSH, "Push", "Chest"),
        routine(PULL, "Pull", "Back"),
        routine(LEGS, "Legs", "Quads"),
    )

    private fun routine(id: String, name: String, muscleGroup: String): Routine = Routine(
        id = id,
        name = name,
        notes = "",
        createdAt = 0L,
        updatedAt = 0L,
        exercises = listOf(
            RoutineExercise(
                id = "$id-1",
                routineId = id,
                exercise = Exercise("$id-ex", "$name lift", muscleGroup, "", false),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        ),
    )

    private fun finished(id: String, routineId: String?, day: LocalDate): WorkoutSession =
        WorkoutSession(
            id = id,
            routineId = routineId,
            routineName = routines().firstOrNull { it.id == routineId }?.name,
            date = millis(day),
            notes = "",
            durationMinutes = 40,
            startedAt = millis(day),
            finishedAt = millis(day) + 2_400_000,
            exercises = emptyList(),
            sets = emptyList(),
        )

    private fun emptySnapshot(): BodyHeatSnapshot = MuscleLoadCalculator.snapshot(
        sessions = emptyList(),
        window = HeatWindow.LAST_30_DAYS,
        nowMs = millis(MON),
        zone = ZoneOffset.UTC,
    )

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 9L * 60 * 60 * 1000

    private companion object {
        const val PUSH = "r-push"
        const val PULL = "r-pull"
        const val LEGS = "r-legs"

        val PREFS = SchedulePreferences(weekStart = Weekday.MONDAY)

        val MON: LocalDate = LocalDate.of(2026, 8, 24)
        val TUE: LocalDate = LocalDate.of(2026, 8, 25)
        val WED: LocalDate = LocalDate.of(2026, 8, 26)
        val THU: LocalDate = LocalDate.of(2026, 8, 27)
        val FRI: LocalDate = LocalDate.of(2026, 8, 28)
        val SAT: LocalDate = LocalDate.of(2026, 8, 29)

        val MON_EPOCH: Long = MON.toEpochDay()
        val TUE_EPOCH: Long = TUE.toEpochDay()
        val WED_EPOCH: Long = WED.toEpochDay()
        val THU_EPOCH: Long = THU.toEpochDay()
        val FRI_EPOCH: Long = FRI.toEpochDay()
        val SAT_EPOCH: Long = SAT.toEpochDay()
    }
}
