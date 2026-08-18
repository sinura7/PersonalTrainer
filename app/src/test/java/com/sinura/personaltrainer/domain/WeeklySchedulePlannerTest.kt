package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklySchedulePlannerTest {
    private val zone = ZoneOffset.UTC
    // Friday 2024-01-05 12:00 UTC — week starting Monday is 2024-01-01
    private val now = 1_704_456_000_000L

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
        val recs = RecommendationEngine.recommend(snap, emptyList())
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
        val emptySnap = MuscleLoadCalculator.snapshot(emptyList(), HeatWindow.LAST_7_DAYS, now, zone)
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
        return MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
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
