package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestFloorCopyTest {
    @Test
    fun lastSetLineUsesSetCopyForLoadedLifts() {
        assertEquals(
            "Last set · 100 kg × 5",
            RestFloorCopy.lastSetLine(100.0, 5, LoadClass.LOADED, WeightUnit.KG),
        )
    }

    @Test
    fun sessionTargetLineReusesTheProgressionReason() {
        // ADR-027 retired the in-card ProgressionStrip; the session-grain reason still
        // has one wording, and the rest floor page speaks that same ProgressionCopy line.
        val hint = ProgressionHint(
            exerciseId = "squat",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = 5,
            targetReps = 5,
            suggestedWeightKg = 102.5,
            action = ProgressionAction.INCREASE,
            loadType = LoadType.EXTERNAL,
        )
        assertEquals(
            ProgressionCopy.stripReason(hint, WeightUnit.KG),
            RestFloorCopy.sessionTargetLine(hint, WeightUnit.KG),
        )
    }

    @Test
    fun contextUsesSelectedLiftLastSetNextLineAndPlannedRest() {
        val squat = sessionExercise("squat", "Squat", "Legs")
        val logged = set(
            id = "set-1",
            sessionId = "s1",
            exerciseId = "squat",
            name = "Squat",
            weightKg = 100.0,
            reps = 5,
            at = 1_000L,
        )
        val current = session(
            id = "s1",
            finishedAt = null,
            sets = listOf(logged),
            exercises = listOf(squat),
            date = 1_000L,
        )
        val floor = RestFloorCopy.context(
            session = current,
            selectedExerciseId = "squat",
            unit = WeightUnit.KG,
            nextLine = "Next: 100 kg × 5 · RPE 8",
            prescribedSeconds = 90,
        )
        assertEquals("Squat", floor.exerciseName)
        assertEquals("Last set · 100 kg × 5", floor.lastSetLine)
        assertEquals("Next: 100 kg × 5 · RPE 8", floor.sessionTargetLine)
        assertEquals("Planned rest · 1:30", floor.prescribedRestLine)
        assertEquals(false, floor.afterWarmup)
    }

    @Test
    fun lastWarmupMarksIdleRestAsAfterWarmup() {
        val squat = sessionExercise("squat", "Squat", "Legs")
        val logged = set(
            id = "set-1",
            sessionId = "s1",
            exerciseId = "squat",
            name = "Squat",
            weightKg = 60.0,
            reps = 8,
            warmup = true,
            at = 1_000L,
        )
        val current = session(
            id = "s1",
            finishedAt = null,
            sets = listOf(logged),
            exercises = listOf(squat),
            date = 1_000L,
        )
        val floor = RestFloorCopy.context(
            session = current,
            selectedExerciseId = "squat",
            unit = WeightUnit.KG,
        )
        assertEquals(true, floor.afterWarmup)
        // No planned length was given, so the rest card's Planned caption has nothing to add here.
        assertNull(floor.prescribedRestLine)
    }

    @Test
    fun prescribedRestLineNamesTheClock() {
        // The rest card's words for the planned length, so the page and the dock agree.
        assertEquals("Planned rest · 2:30", RestFloorCopy.prescribedLine(150))
        assertEquals(RestIdleCopy.planned("1:00"), RestFloorCopy.prescribedLine(60))
    }

    @Test
    fun missingSessionHasNoFloorCopy() {
        val floor = RestFloorCopy.context(null, null, WeightUnit.KG)
        assertNull(floor.exerciseName)
        assertNull(floor.lastSetLine)
        assertNull(floor.sessionTargetLine)
        assertNull(floor.prescribedRestLine)
        assertEquals(false, floor.afterWarmup)
    }
}
