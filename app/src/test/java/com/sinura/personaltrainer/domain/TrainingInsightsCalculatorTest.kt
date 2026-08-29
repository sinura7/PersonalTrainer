package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sinura.personaltrainer.domain.Weekday
import java.time.Instant
import java.time.ZoneOffset

/**
 * Home, Schedule and Progress each ran their own copy of this pipeline and the copies had
 * drifted. These tests pin the two places they disagreed — the exercise catalog and the
 * week-start preference — plus the failure semantics that let a screen say which part broke.
 */
class TrainingInsightsCalculatorTest {
    private val zone = ZoneOffset.UTC

    // A Wednesday, so a Monday week-start and a Sunday week-start bracket different days.
    private val now = Instant.parse("2026-08-19T12:00:00Z").toEpochMilli()

    private fun input(
        history: List<WorkoutSession> = emptyList(),
        routines: List<Routine> = emptyList(),
        catalog: Map<String, Exercise> = emptyMap(),
        hints: List<ProgressionHint>? = emptyList(),
        preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
        window: HeatWindow = HeatWindow.CURRENT_MONTH,
        includeWeekPlan: Boolean = true,
        slots: List<ScheduleSlot> = emptyList(),
    ) = TrainingInsightsInput(
        history = history,
        routines = routines,
        exerciseCatalog = catalog,
        hints = hints,
        preferences = preferences,
        slots = slots,
        unit = WeightUnit.KG,
        window = window,
        nowMs = now,
        zone = zone,
        includeWeekPlan = includeWeekPlan,
    )

    /** A set whose exercise is no longer attached to its session — the only case the catalog covers. */
    private fun detachedSet(at: Long) = session(
        id = "s1",
        finishedAt = at,
        sets = listOf(set("a", "s1", "ex-squat", "Squat", 100.0, 5, at = at)),
        exercises = emptyList(),
    )

    @Test
    fun detachedSetIsAttributedThroughTheExerciseCatalog() {
        val insights = TrainingInsightsCalculator.compute(
            input(
                history = listOf(detachedSet(now - 1L * 24 * 60 * 60 * 1000)),
                catalog = mapOf("ex-squat" to Exercise("ex-squat", "Squat", "Quads", "", false)),
            ),
        )
        val quads = insights.snapshot!!.load(CanonicalMuscle.QUADRICEPS)
        assertEquals(500.0, quads.volumeKg, 0.001)
    }

    @Test
    fun detachedSetIsLostWithoutTheCatalog() {
        // Home and Schedule used to pass no catalog at all, so this was their real behaviour:
        // volume that Progress counted toward quads went nowhere on the other two screens.
        val insights = TrainingInsightsCalculator.compute(
            input(history = listOf(detachedSet(now - 1L * 24 * 60 * 60 * 1000))),
        )
        assertEquals(0.0, insights.snapshot!!.load(CanonicalMuscle.QUADRICEPS).volumeKg, 0.001)
    }

    @Test
    fun currentWeekWindowHonoursTheWeekStartPreference() {
        val monday = TrainingInsightsCalculator.compute(
            input(
                window = HeatWindow.CURRENT_WEEK,
                preferences = SchedulePreferences.DEFAULT.copy(weekStart = Weekday.MONDAY),
            ),
        ).snapshot!!.windowStartMs
        val sunday = TrainingInsightsCalculator.compute(
            input(
                window = HeatWindow.CURRENT_WEEK,
                preferences = SchedulePreferences.DEFAULT.copy(weekStart = Weekday.SUNDAY),
            ),
        ).snapshot!!.windowStartMs

        assertEquals(
            Instant.parse("2026-08-17T00:00:00Z").toEpochMilli(),
            monday,
        )
        assertEquals(
            Instant.parse("2026-08-16T00:00:00Z").toEpochMilli(),
            sunday,
        )
    }

    @Test
    fun nullHintsReportAFailureRatherThanLookingLikeNothingIsReady() {
        val failed = TrainingInsightsCalculator.compute(input(hints = null))
        assertTrue(failed.failed(InsightFailure.PROGRESSION))
        assertTrue(failed.hints.isEmpty())

        val empty = TrainingInsightsCalculator.compute(input(hints = emptyList()))
        assertFalse(empty.failed(InsightFailure.PROGRESSION))
        assertTrue(empty.hints.isEmpty())
    }

    @Test
    fun lifetimeSummariesKeepTheBodyMapFromLookingUntrained() {
        val old = SessionSummary(
            id = "ancient",
            routineId = null,
            routineName = "Pull",
            date = now - 80L * 24 * 60 * 60 * 1000,
            finishedAt = now - 80L * 24 * 60 * 60 * 1000,
            durationMinutes = 40,
            workingSets = 12,
            volumeKg = 4_000.0,
            localEpochDay = 19_920L,
        )
        val insights = TrainingInsightsCalculator.compute(
            input(history = emptyList()).copy(summaries = listOf(old)),
        )
        assertTrue(insights.snapshot!!.hasAnyWorkingSets)
        assertFalse(insights.snapshot!!.hasWindowWorkingSets)
    }

    @Test
    fun suppliedSummariesAreEchoedAndMissingOnesAreDerived() {
        val history = listOf(detachedSet(now - 1L * 24 * 60 * 60 * 1000))
        val supplied = SessionSummary(
            id = "supplied",
            routineId = null,
            routineName = "Supplied",
            date = now,
            finishedAt = now,
            durationMinutes = 30,
            workingSets = 3,
            volumeKg = 300.0,
            localEpochDay = 20_000L,
        )
        val echoed = TrainingInsightsCalculator.compute(
            input(history = history).copy(summaries = listOf(supplied)),
        )
        assertEquals(listOf("supplied"), echoed.summaries.map { it.id })
        val derived = TrainingInsightsCalculator.compute(input(history = history))
        assertEquals(listOf("s1"), derived.summaries.map { it.id })
        assertTrue(derived.recommendations.isNotEmpty())
        assertTrue(derived.recommendations.all { it.trace != null })
    }

    @Test
    fun hintsCarryALocalTrace() {
        val hint = ProgressionHint(
            exerciseId = "ex-1",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = 5,
            targetReps = 5,
            suggestedWeightKg = 102.5,
            action = ProgressionAction.INCREASE,
        )
        val insights = TrainingInsightsCalculator.compute(input(hints = listOf(hint)))
        assertEquals(1, insights.hints.size)
        assertNotNull(insights.hints.single().trace)
        assertEquals("progression-ex-1", insights.hints.single().trace!!.ruleId)
    }

    @Test
    fun skippingTheWeekPlanIsNotAFailure() {
        val insights = TrainingInsightsCalculator.compute(input(includeWeekPlan = false))
        assertNull(insights.weekPlan)
        assertFalse(insights.failed(InsightFailure.PLAN))
        assertTrue(insights.failures.isEmpty())
    }

    @Test
    fun weekPlanIsBuiltWhenRequested() {
        val insights = TrainingInsightsCalculator.compute(input(includeWeekPlan = true))
        assertNotNull(insights.weekPlan)
        assertTrue(insights.failures.isEmpty())
    }

    @Test
    fun inputsAreEchoedBackSoCallersNeedNotReadThemTwice() {
        val history = listOf(detachedSet(now - 1L * 24 * 60 * 60 * 1000))
        val routines = listOf(
            Routine(
                id = "r1",
                name = "Push",
                notes = "",
                createdAt = now,
                updatedAt = now,
                exercises = emptyList(),
            ),
        )
        val insights = TrainingInsightsCalculator.compute(
            input(history = history, routines = routines),
        )
        assertSame(history, insights.history)
        assertSame(routines, insights.routines)
    }

    @Test
    fun everyStageIsReportedIndependently() {
        val insights = TrainingInsights(failures = setOf(InsightFailure.PLAN))
        assertTrue(insights.failed(InsightFailure.PLAN))
        assertFalse(insights.failed(InsightFailure.HEAT))
        assertFalse(insights.failed(InsightFailure.PROGRESSION))
        assertFalse(insights.failed(InsightFailure.RECOMMENDATIONS))
    }

    // ---- the week is read, not invented ----

    @Test
    fun weekPlanComesFromPinnedSlotsNotThePlanner() {
        // With nothing pinned, the week is empty and says so. Before this, the planner ran here
        // on every emission and produced a full week nobody had asked for, which is why the
        // plan reshuffled whenever anything was logged.
        val empty = TrainingInsightsCalculator.compute(input()).weekPlan
        assertNotNull(empty)
        assertEquals(7, empty!!.days.size)
        assertTrue("an unpinned week must be all rest", empty.days.all { it.isRest })
        assertEquals("No sessions pinned yet.", empty.summary)

        val push = Routine(
            id = "r-push",
            name = "Push",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf(
                RoutineExercise(
                    id = "re-1",
                    routineId = "r-push",
                    exercise = Exercise("ex-1", "Bench", "Chest", "", false),
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = null,
                    restSeconds = 90,
                ),
            ),
        )
        val slot = ScheduleSlot(
            id = "slot-0",
            position = 0,
            routineId = "r-push",
            focusKind = null,
            anchorDay = Weekday.FRIDAY,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val pinned = TrainingInsightsCalculator
            .compute(input(routines = listOf(push), slots = listOf(slot)))
            .weekPlan
        assertNotNull(pinned)
        val friday = pinned!!.days.first { it.dayOfWeek == Weekday.FRIDAY }
        assertEquals("r-push", friday.routineId)
        assertEquals("slot-0", friday.slotId)
        assertEquals("1 pinned · 0 logged this week", pinned.summary)
        assertEquals(
            "only the pinned day is a training day",
            1,
            pinned.days.count { !it.isRest },
        )
    }

}
