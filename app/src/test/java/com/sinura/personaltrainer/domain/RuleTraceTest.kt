package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTraceTest {
    @Test
    fun recommendationTraceCarriesRuleIdActionAndEvidenceWindow() {
        val rec = TrainingRecommendation(
            id = "imbalance-CHEST-BACK",
            kicker = RecommendationEngine.KICKER_BALANCE,
            title = "Add back work",
            reason = "the last 14 days",
            priority = RecommendationPriority.HIGH,
            action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
            rankScore = 10,
        )
        val trace = RuleTrace.forRecommendation(rec, nowMs = 9L, evidenceStartEpochDay = 1, evidenceEndEpochDay = 14)
        assertEquals(rec.id, trace.ruleId)
        assertEquals(RuleTrace.VERSION, trace.version)
        assertEquals("OPEN_LIBRARY_MUSCLE", trace.action)
        assertEquals(listOf(RecommendationEngine.KICKER_BALANCE), trace.reasonCodes)
        assertEquals(1L, trace.evidenceStartEpochDay)
        assertEquals(14L, trace.evidenceEndEpochDay)
        assertEquals(9L, trace.generatedAtMs)
        assertTrue(trace.facts.any { it.name == "reason" })
    }

    @Test
    fun recommendationWithoutAnActionRecordsNone() {
        val rec = TrainingRecommendation(
            id = "keep-going",
            kicker = RecommendationEngine.KICKER_RECOVERY,
            title = "Keep going",
            reason = "the last 14 days",
            priority = RecommendationPriority.INFO,
            action = null,
            rankScore = 1,
        )
        val trace = RuleTrace.forRecommendation(rec, nowMs = 1L, evidenceStartEpochDay = 1, evidenceEndEpochDay = 2)
        assertEquals("NONE", trace.action)
        assertTrue(trace.facts.any { it.name == "title" })
        assertTrue(trace.thresholds.isEmpty())
        assertTrue(trace.alternatives.isEmpty())
    }

    @Test
    fun hintTraceNamesTheHoldReason() {
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
        val trace = RuleTrace.forHint(hint, nowMs = 3L, todayEpochDay = 20)
        assertEquals("progression-ex-1", trace.ruleId)
        assertTrue(trace.reasonCodes.contains("RPE_HOLD"))
        assertEquals("HOLD", trace.action)
        val lighter = hint.copy(rpeHold = false, lighterHold = true, action = ProgressionAction.DECREASE)
        val lighterTrace = RuleTrace.forHint(lighter, nowMs = 4L, todayEpochDay = 21)
        assertTrue(lighterTrace.reasonCodes.contains("LIGHTER_HOLD"))
        assertEquals("DECREASE", lighterTrace.action)
        assertTrue(lighterTrace.facts.any { it.name == "exercise" })
        assertEquals("5", lighterTrace.thresholds.single().value)
    }
}
