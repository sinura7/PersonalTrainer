package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class LighterWeekTest {
    @Test
    fun aPastMarkIsInert() {
        val thisWeek = LighterWeek.weekStartEpochDay(
            LocalDate.of(2026, 8, 22),
            DayOfWeek.MONDAY,
        )
        assertFalse(LighterWeek.isCurrent(thisWeek - 7, thisWeek))
        assertTrue(LighterWeek.isCurrent(thisWeek, thisWeek))
        assertFalse(LighterWeek.isCurrent(null, thisWeek))
    }
}

class LighterWeekModifierTest {
    private fun hint(action: ProgressionAction, suggested: Double = 102.5) = ProgressionHint(
        exerciseId = "x",
        exerciseName = "X",
        lastWeightKg = 100.0,
        lastReps = 5,
        targetReps = 5,
        suggestedWeightKg = suggested,
        action = action,
        loadType = LoadType.EXTERNAL,
    )

    @Test
    fun offLeavesTheHintAlone() {
        val increase = hint(ProgressionAction.INCREASE)
        assertEquals(increase, LighterWeekModifier.apply(increase, lighter = false))
    }

    @Test
    fun increaseBecomesHoldAtLastLoad() {
        val held = LighterWeekModifier.apply(hint(ProgressionAction.INCREASE), lighter = true)
        assertEquals(ProgressionAction.HOLD, held.action)
        assertEquals(100.0, held.suggestedWeightKg, 0.001)
        assertTrue(held.lighterHold)
    }

    @Test
    fun decreaseIsNotOverridden() {
        val drop = hint(ProgressionAction.DECREASE, suggested = 97.5)
        val applied = LighterWeekModifier.apply(drop, lighter = true)
        assertEquals(ProgressionAction.DECREASE, applied.action)
        assertEquals(97.5, applied.suggestedWeightKg, 0.001)
        assertFalse(applied.lighterHold)
    }

    @Test
    fun anExistingHoldNamesTheLighterWeek() {
        val held = LighterWeekModifier.apply(hint(ProgressionAction.HOLD, suggested = 100.0), true)
        assertEquals(ProgressionAction.HOLD, held.action)
        assertTrue(held.lighterHold)
    }
}
