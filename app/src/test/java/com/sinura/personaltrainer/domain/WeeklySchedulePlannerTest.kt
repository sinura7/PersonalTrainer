package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklySchedulePlannerTest {
    private val zone = ZoneOffset.UTC
    // Monday 2024-01-01 12:00 UTC — the first day of its own week.
    //
    // This used to be the Friday of the same week, which stopped working when the planner
    // learned not to propose sessions for days that have already gone: four of the seven days
    // were behind the clock, so a "4-day week" test saw two proposals. Anchoring the clock to
    // the week start keeps each test about the thing it is named for — split shape, day count,
    // recovery override — and leaves the past-day rule to the tests written for it.
    private val now = 1_704_110_400_000L

    @Test
    fun preferenceDefaultsAreFourDayAutoMonday() {
        val prefs = SchedulePreferences.DEFAULT
        assertEquals(4, prefs.trainingDaysPerWeek)
        assertEquals(SplitStyle.AUTO, prefs.splitStyle)
        assertEquals(DayOfWeek.MONDAY, prefs.weekStart)
        assertEquals(6, SchedulePreferences(trainingDaysPerWeek = 99).sanitized().trainingDaysPerWeek)
        assertEquals(2, SchedulePreferences(trainingDaysPerWeek = 1).sanitized().trainingDaysPerWeek)
        assertEquals(SplitStyle.UPPER_LOWER, SplitStyle.fromStorage("upper_lower"))
        assertEquals(SplitStyle.AUTO, SplitStyle.fromStorage("nope"))
        assertEquals(DayOfWeek.SUNDAY, SchedulePreferences.weekStartFromStorage("sunday"))
        assertEquals(DayOfWeek.MONDAY, SchedulePreferences.weekStartFromStorage(null))
    }

    @Test
    fun respectsTrainingDaysPerWeekLimit() {
        (2..6).forEach { days ->
            val plan = plan(prefs = SchedulePreferences(trainingDaysPerWeek = days, splitStyle = SplitStyle.FULL_BODY))
            assertEquals(7, plan.days.size)
            assertEquals(days, plan.trainingDays.size)
            assertEquals(7 - days, plan.days.count { it.isRest })
        }
    }

    @Test
    fun neverSchedulesSevenHardDays() {
        val plan = plan(prefs = SchedulePreferences(trainingDaysPerWeek = 6, splitStyle = SplitStyle.FULL_BODY))
        assertTrue(plan.days.any { it.isRest })
        assertTrue(plan.trainingDays.size <= 6)
    }

    @Test
    fun upperLowerFourDaySplitAlternates() {
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.UPPER_LOWER),
            sessions = staleSessions(),
        )
        val kinds = plan.trainingDays.map { it.focusKind }
        assertEquals(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
            ),
            kinds,
        )
    }

    @Test
    fun pushPullLegsThreeDayCycle() {
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 3, splitStyle = SplitStyle.PUSH_PULL_LEGS),
            sessions = staleSessions(),
        )
        assertEquals(
            listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, SessionFocusKind.LEGS),
            plan.trainingDays.map { it.focusKind },
        )
    }

    @Test
    fun autoThreeDaysWithPplRoutinesPicksPpl() {
        val routines = listOf(
            routine("r-push", "Push A", listOf("Chest", "Shoulders", "Triceps")),
            routine("r-pull", "Pull A", listOf("Back", "Biceps")),
            routine("r-leg", "Leg Day", listOf("Quads", "Hamstrings", "Glutes")),
        )
        val resolved = WeeklySchedulePlanner.resolveSplit(
            SchedulePreferences(trainingDaysPerWeek = 3, splitStyle = SplitStyle.AUTO),
            routines,
        )
        assertEquals(SplitStyle.PUSH_PULL_LEGS, resolved)
    }

    @Test
    fun autoTwoDaysFallsBackToFullBody() {
        val resolved = WeeklySchedulePlanner.resolveSplit(
            SchedulePreferences(trainingDaysPerWeek = 2, splitStyle = SplitStyle.AUTO),
            emptyList(),
        )
        assertEquals(SplitStyle.FULL_BODY, resolved)
    }

    @Test
    fun imbalancePutsBackEmphasisOnUpperOrPull() {
        val snap = hotChestQuietBack()
        val recs = listOf(
            TrainingRecommendation(
                id = "imbalance-CHEST-BACK",
                kicker = RecommendationEngine.KICKER_BALANCE,
                title = "Back is behind Chest",
                reason = "test fixture",
                priority = RecommendationPriority.HIGH,
                action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
                actionMuscle = CanonicalMuscle.BACK,
                rankScore = 50,
            ),
        )
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.UPPER_LOWER),
            snapshot = snap,
            recommendations = recs,
            sessions = listOf(
                finished("s-chest", now - days(1), "Chest"),
                finished("s-row", now - days(3), "Back"),
                finished("s-squat", now - days(5), "Quads"),
            ),
        )
        assertFalse(plan.thinHistory)
        val upper = plan.trainingDays.first { it.focusKind == SessionFocusKind.UPPER }
        assertTrue(upper.focusTitle.contains("Back"))
        assertTrue(upper.reason.contains("Back"))
        assertTrue(CanonicalMuscle.BACK in upper.emphasisMuscles)
    }

    @Test
    fun prefersMatchingUserRoutine() {
        val routines = listOf(
            routine("r-upper", "Upper Power", listOf("Chest", "Back", "Shoulders")),
            routine("r-lower", "Lower Strength", listOf("Quads", "Hamstrings", "Glutes")),
        )
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.UPPER_LOWER),
            routines = routines,
        )
        val upper = plan.trainingDays.first { it.focusKind == SessionFocusKind.UPPER }
        val lower = plan.trainingDays.first { it.focusKind == SessionFocusKind.LOWER }
        assertEquals("r-upper", upper.routineId)
        assertEquals("Upper Power", upper.routineName)
        assertEquals("r-lower", lower.routineId)
        assertEquals(ScheduleConfidence.HIGH, upper.confidence)
    }

    @Test
    fun customCyclesExistingRoutinesOnly() {
        val routines = listOf(
            routine("r1", "Push", listOf("Chest", "Triceps")),
            routine("r2", "Pull", listOf("Back", "Biceps")),
        )
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.CUSTOM),
            routines = routines,
        )
        assertEquals(listOf("r1", "r2", "r1", "r2"), plan.trainingDays.map { it.routineId })
        assertTrue(plan.trainingDays.all { it.routineId != null })
    }

    @Test
    fun thinHistoryUsesCleanDefaultAndLowConfidence() {
        val emptySnap = MuscleLoadCalculator.snapshot(emptyList(), HeatWindow.LAST_30_DAYS, now, zone)
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 3, splitStyle = SplitStyle.FULL_BODY),
            snapshot = emptySnap,
            sessions = emptyList(),
        )
        assertTrue(plan.thinHistory)
        assertTrue(plan.summary.contains("starter", ignoreCase = true))
        assertTrue(plan.trainingDays.all { it.confidence == ScheduleConfidence.LOW })
        assertTrue(plan.trainingDays.all { it.emphasisMuscles.isEmpty() })
        assertEquals(3, plan.trainingDays.size)
        assertTrue(plan.trainingDays.all { it.focusKind == SessionFocusKind.FULL_BODY })
    }

    @Test
    fun recentUpperSessionRotatesFirstSlotToLower() {
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.UPPER_LOWER),
            sessions = listOf(
                finished("today-upper", now - hours(8), "Chest"),
                finished("s2", now - days(4), "Quads"),
                finished("s3", now - days(6), "Back"),
            ),
        )
        assertEquals(SessionFocusKind.LOWER, plan.trainingDays.first().focusKind)
    }

    @Test
    fun weekCanStartOnSunday() {
        val plan = plan(prefs = SchedulePreferences(weekStart = DayOfWeek.SUNDAY, splitStyle = SplitStyle.FULL_BODY))
        assertEquals(DayOfWeek.SUNDAY, LocalDate.ofEpochDay(plan.weekStartEpochDay).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, plan.days.first().dayOfWeek)
    }

    @Test
    fun trainingSlotsAreSpacedForFourDays() {
        assertEquals(listOf(0, 2, 4, 5), WeeklySchedulePlanner.trainingDayIndices(4))
        assertEquals(listOf(0, 3), WeeklySchedulePlanner.trainingDayIndices(2))
    }

    @Test
    fun classifiesNamedAndMuscleMixRoutines() {
        assertEquals(
            SessionFocusKind.PUSH,
            WeeklySchedulePlanner.classifyRoutine(routine("1", "Heavy Push", listOf("Chest", "Shoulders"))),
        )
        assertEquals(
            SessionFocusKind.LEGS,
            WeeklySchedulePlanner.classifyRoutine(routine("2", "Squat Day", listOf("Quads", "Hamstrings", "Glutes"))),
        )
        assertEquals(
            SessionFocusKind.FULL_BODY,
            WeeklySchedulePlanner.classifyRoutine(routine("3", "Gym", listOf("Chest", "Quads"))),
        )
    }

    // ---- the planner proposes; it no longer decides ----

    @Test
    fun plannerNeverProposesPastDays() {
        // Thursday of the fixture week. The planner used to lay a fresh week over the whole
        // calendar, so on a Thursday it would still tell you to train on Monday.
        val thursday = now + days(3)
        val plan = WeeklySchedulePlanner.plan(
            preferences = SchedulePreferences(trainingDaysPerWeek = 4),
            snapshot = hotChestQuietBack(),
            recommendations = emptyList(),
            routines = emptyList(),
            recentSessions = emptyList(),
            nowMs = thursday,
            zone = zone,
        )
        val todayEpoch = java.time.Instant.ofEpochMilli(thursday).atZone(zone).toLocalDate().toEpochDay()
        plan.trainingDays.forEach { day ->
            assertTrue(
                "proposed a session on a day that has already gone",
                day.epochDay >= todayEpoch,
            )
        }
    }

    @Test
    fun plannerProposalsOnlyOnOpenDays() {
        val pin = ScheduleSlot(
            id = "slot-0",
            position = 0,
            routineId = "r-push",
            focusKind = null,
            anchorDay = DayOfWeek.MONDAY,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val plan = WeeklySchedulePlanner.plan(
            preferences = SchedulePreferences(trainingDaysPerWeek = 4),
            snapshot = hotChestQuietBack(),
            recommendations = emptyList(),
            routines = listOf(routine("r-push", "Push A", listOf("Chest", "Shoulders", "Triceps"))),
            recentSessions = emptyList(),
            nowMs = now,
            zone = zone,
            pinnedSlots = listOf(pin),
        )
        val monday = plan.days.first()
        assertEquals("the pin is echoed, not proposed over", "slot-0", monday.slotId)
        assertEquals("r-push", monday.routineId)
        val proposals = plan.trainingDays.filter { it.slotId == null }
        assertTrue("proposals must exist for the rest of the week", proposals.isNotEmpty())
        assertTrue(
            "a proposal landed on the pinned day",
            proposals.none { it.epochDay == monday.epochDay },
        )
    }

    // ---- adjacency (audit: the fix could create the adjacency it prevents) ----

    @Test
    fun arrangeKindsAvoidsSameFamilyAdjacency() {
        val upperLower = listOf(
            SessionFocusKind.UPPER, SessionFocusKind.LOWER,
            SessionFocusKind.UPPER, SessionFocusKind.LOWER,
        )
        val arranged = WeeklySchedulePlanner.arrangeKinds(upperLower, SessionFocusKind.UPPER)
        assertEquals(
            "rotating is what lets a U/L week start on LOWER and keep alternating",
            listOf(
                SessionFocusKind.LOWER, SessionFocusKind.UPPER,
                SessionFocusKind.LOWER, SessionFocusKind.UPPER,
            ),
            arranged,
        )
        assertTrue(
            "no two same-family days in a row",
            (1 until arranged.size).none { sameFamily(arranged[it - 1], arranged[it]) },
        )
    }

    @Test
    fun arrangeKindsLeavesUnfixableWeeksAlone() {
        // Everything in one family: no arrangement helps, so churning it is noise.
        val allUpper = listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, SessionFocusKind.UPPER)
        assertEquals(allUpper, WeeklySchedulePlanner.arrangeKinds(allUpper, SessionFocusKind.PUSH))

        // The audit's case. The old rule turned this into [LOWER, UPPER, UPPER] — a new
        // back-to-back pair, created by the rule meant to prevent one. Leaving it alone is the
        // better trade: the clash it still carries is against yesterday, not inside the week.
        val threeDay = listOf(SessionFocusKind.UPPER, SessionFocusKind.LOWER, SessionFocusKind.UPPER)
        val arranged = WeeklySchedulePlanner.arrangeKinds(threeDay, SessionFocusKind.UPPER)
        assertEquals(threeDay, arranged)
        assertTrue(
            "whatever it returns must not add an in-week adjacency",
            (1 until arranged.size).none { sameFamily(arranged[it - 1], arranged[it]) },
        )
    }

    @Test
    fun arrangeKindsLeavesAClearWeekUntouched() {
        // Push/Pull/Legs is a deliberate order. With nothing clashing, it must survive intact.
        val ppl = listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, SessionFocusKind.LEGS)
        assertEquals(ppl, WeeklySchedulePlanner.arrangeKinds(ppl, null))
        assertEquals(ppl, WeeklySchedulePlanner.arrangeKinds(ppl, SessionFocusKind.LEGS))
    }

    private fun sameFamily(a: SessionFocusKind, b: SessionFocusKind): Boolean {
        val upper = setOf(SessionFocusKind.UPPER, SessionFocusKind.PUSH, SessionFocusKind.PULL)
        val lower = setOf(SessionFocusKind.LOWER, SessionFocusKind.LEGS)
        return (a in upper && b in upper) || (a in lower && b in lower)
    }

    private fun plan(
        prefs: SchedulePreferences = SchedulePreferences(),
        snapshot: BodyHeatSnapshot = hotChestQuietBack(),
        recommendations: List<TrainingRecommendation> = emptyList(),
        routines: List<Routine> = emptyList(),
        sessions: List<WorkoutSession> = List(4) { finished("s$it", now - days(it.toLong() + 1L), "Chest") },
    ): WeeklySchedulePlan = WeeklySchedulePlanner.plan(
        preferences = prefs,
        snapshot = snapshot,
        recommendations = recommendations,
        routines = routines,
        recentSessions = sessions,
        nowMs = now,
        zone = zone,
    )

    // ---- recovery override (audit: the planner deleted the week's last lower day) ----

    @Test
    fun recoveryOverrideNeverDowngradesALowerDayOnAnUpperLowerSplit() {
        // The audit's exact scenario. The recovery-upper signal only fires when the lower body
        // is COLD, and a 4-day Upper/Lower week is [U, L, U, L] — so the old rule, which took
        // the last slot and exempted only LEGS, converted the final LOWER day to "Recovery
        // lean" precisely because legs were undertrained.
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 4, splitStyle = SplitStyle.UPPER_LOWER),
            snapshot = hotChestQuietBack(),
            recommendations = listOf(recoveryUpper()),
        )
        val kinds = plan.trainingDays.map { it.focusKind }
        assertEquals(
            "a lower day must survive the recovery override",
            2,
            kinds.count { it == SessionFocusKind.LOWER || it == SessionFocusKind.LEGS },
        )
        assertTrue("the recovery day should replace upper work", kinds.contains(SessionFocusKind.RECOVERY))
    }

    @Test
    fun recoveryOverrideClaimsTheLastUpperSlot() {
        val slot = WeeklySchedulePlanner.recoveryOverrideSlot(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
            ),
        )
        assertEquals(2, slot)
    }

    @Test
    fun recoveryOverrideTreatsLegsAndLowerAsTheSameFamily() {
        // LEGS and LOWER are different enum constants but the same training region; guarding
        // on the constant was the whole bug.
        listOf(SessionFocusKind.LEGS, SessionFocusKind.LOWER).forEach { lower ->
            val slot = WeeklySchedulePlanner.recoveryOverrideSlot(
                listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, lower),
            )
            assertEquals("must not claim a $lower slot", 1, slot)
        }
    }

    @Test
    fun recoveryOverrideFallsBackToFullBodyAndOtherwiseDoesNothing() {
        assertEquals(
            1,
            WeeklySchedulePlanner.recoveryOverrideSlot(
                listOf(SessionFocusKind.FULL_BODY, SessionFocusKind.FULL_BODY, SessionFocusKind.LEGS),
            ),
        )
        // An all-lower week has nothing to recover from: convert nothing rather than a leg day.
        assertNull(
            WeeklySchedulePlanner.recoveryOverrideSlot(
                listOf(SessionFocusKind.LEGS, SessionFocusKind.LOWER),
            ),
        )
        assertNull(WeeklySchedulePlanner.recoveryOverrideSlot(emptyList()))
    }

    @Test
    fun aPplWeekStillGetsARecoveryDayOnUpperWork() {
        val plan = plan(
            prefs = SchedulePreferences(trainingDaysPerWeek = 6, splitStyle = SplitStyle.PUSH_PULL_LEGS),
            snapshot = hotChestQuietBack(),
            recommendations = listOf(recoveryUpper()),
        )
        val kinds = plan.trainingDays.map { it.focusKind }
        assertTrue(kinds.contains(SessionFocusKind.RECOVERY))
        assertTrue(
            "legs days must be untouched",
            kinds.count { it == SessionFocusKind.LEGS || it == SessionFocusKind.LOWER } >= 2,
        )
    }

    private fun recoveryUpper() = TrainingRecommendation(
        id = "recovery-upper",
        kicker = RecommendationEngine.KICKER_RECOVERY,
        title = "Upper-body load is very high",
        reason = "test fixture",
        priority = RecommendationPriority.HIGH,
        action = RecommendationAction.OPEN_ROUTINES,
        rankScore = 70,
    )

    private fun hotChestQuietBack(): BodyHeatSnapshot {
        val session = session(
            id = "load",
            finishedAt = now - days(1),
            sets = listOf(
                set("c1", "load", "ex-bench", "Bench", 140.0, 5, at = now - days(1)),
                set("b1", "load", "ex-row", "Row", 40.0, 5, at = now - days(1)),
            ),
            exercises = listOf(
                sessionExercise("ex-bench", "Bench", "Chest"),
                sessionExercise("ex-row", "Row", "Back"),
            ),
        )
        return MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
    }

    private fun finished(id: String, at: Long, muscle: String): WorkoutSession = session(
        id = id,
        finishedAt = at,
        sets = listOf(set("set-$id", id, "ex-$id", "Lift", 80.0, 5, at = at)),
        exercises = listOf(sessionExercise("ex-$id", "Lift", muscle)),
    )

    private fun routine(id: String, name: String, muscles: List<String>): Routine {
        val items = muscles.mapIndexed { index, muscle ->
            val exercise = Exercise("ex-$id-$index", "$name $muscle", muscle, "", false)
            RoutineExercise(
                id = "re-$id-$index",
                routineId = id,
                exercise = exercise,
                sortOrder = index,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            )
        }
        return Routine(id, name, "", 1L, 2L, items)
    }

    private fun staleSessions(): List<WorkoutSession> =
        List(4) { finished("old$it", now - days(it.toLong() + 4L), "Chest") }

    private fun hours(count: Long): Long = count * 60L * 60L * 1000L

    private fun days(count: Long): Long = count * 24L * 60L * 60L * 1000L
}
