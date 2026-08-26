package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTraceCopyTest {
    @Test
    fun whyNeverPrintsMachineKeys() {
        val rec = TrainingRecommendation(
            id = "imbalance-CHEST-BACK",
            kicker = RecommendationEngine.KICKER_BALANCE,
            title = "Add back work",
            reason = "Back is behind chest in the last 14 days.",
            priority = RecommendationPriority.HIGH,
            action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
            rankScore = 10,
        )
        val trace = RuleTrace.forRecommendation(
            rec,
            nowMs = 9L,
            evidenceStartEpochDay = 1,
            evidenceEndEpochDay = 14,
        )
        val lines = RuleTraceCopy.lines(trace)
        assertEquals("Looked at 14 days of training.", lines.first())
        assertTrue(lines.any { it == "Balance" })
        assertTrue(lines.any { it.startsWith("Because:") })
        lines.forEach { line ->
            assertFalse(line, line.contains("reasonCodes"))
            assertFalse(line, line.contains("lastWeightKg"))
            assertFalse(line, "title:" in line)
            assertFalse(line, line.contains(RecommendationEngine.KICKER_BALANCE) && line == RecommendationEngine.KICKER_BALANCE)
        }
        assertFalse(lines.any { it.startsWith("title:") })
        assertFalse(lines.joinToString().contains("BALANCE"))
    }

    @Test
    fun hintTraceSpeaksHoldAndLift() {
        val hint = ProgressionHint(
            exerciseId = "ex-1",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = 5,
            targetReps = 5,
            suggestedWeightKg = 100.0,
            action = ProgressionAction.HOLD,
            rpeHold = true,
        )
        val lines = RuleTraceCopy.lines(RuleTrace.forHint(hint, nowMs = 3L, todayEpochDay = 20))
        assertTrue(lines.any { it.contains("Held for RPE") })
        assertTrue(lines.any { it.startsWith("Lift:") })
        assertTrue(lines.any { it.startsWith("Last weight (kg):") })
        assertTrue(lines.any { it.startsWith("Target reps:") })
        assertFalse(lines.joinToString().contains("RPE_HOLD"))
        assertFalse(lines.joinToString().contains("lastWeightKg"))
        assertFalse(lines.joinToString().contains("targetReps:"))
    }
}
