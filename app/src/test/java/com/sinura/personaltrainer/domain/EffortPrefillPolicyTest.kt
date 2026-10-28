package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortPrefillPolicyTest {
    @Test
    fun automaticSeedSkipsWarmupsAndHolds() {
        assertNull(
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = true,
                isHold = false,
                exerciseId = "sq",
                sessionSets = emptyList(),
                priorWorkingSets = emptyList(),
            ),
        )
        assertNull(
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = true,
                exerciseId = "plank",
                sessionSets = emptyList(),
                priorWorkingSets = emptyList(),
            ),
        )
    }

    @Test
    fun automaticSeedWithNoHistoryIsNull() {
        assertNull(
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = false,
                exerciseId = "sq",
                sessionSets = emptyList(),
                priorWorkingSets = emptyList(),
            ),
        )
    }

    @Test
    fun automaticSeedPrefersThisSessionOverPrior() {
        val sessionSets = listOf(
            setLog(id = "1", exerciseId = "sq", rpe = 7, completedAt = 100L),
            setLog(id = "2", exerciseId = "sq", rpe = 8, completedAt = 200L),
        )
        val prior = listOf(
            ExerciseSetRecord(
                setId = "p",
                sessionId = "old",
                weightKg = 100.0,
                reps = 5,
                completedAt = 300L,
                rpe = 9,
            ),
        )
        assertEquals(
            8,
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = false,
                exerciseId = "sq",
                sessionSets = sessionSets,
                priorWorkingSets = prior,
            ),
        )
    }

    @Test
    fun automaticSeedIgnoresWarmupsInSession() {
        val sessionSets = listOf(
            setLog(id = "w", exerciseId = "sq", rpe = 6, completedAt = 100L, isWarmup = true),
            setLog(id = "1", exerciseId = "sq", rpe = 7, completedAt = 200L),
        )
        assertEquals(
            7,
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = false,
                exerciseId = "sq",
                sessionSets = sessionSets,
                priorWorkingSets = emptyList(),
            ),
        )
    }

    @Test
    fun automaticSeedFallsBackToLatestPriorWorkingSet() {
        val prior = listOf(
            ExerciseSetRecord(
                setId = "a",
                sessionId = "s1",
                weightKg = 100.0,
                reps = 5,
                completedAt = 100L,
                rpe = 6,
            ),
            ExerciseSetRecord(
                setId = "b",
                sessionId = "s2",
                weightKg = 100.0,
                reps = 5,
                completedAt = 300L,
                rpe = 9,
            ),
            ExerciseSetRecord(
                setId = "c",
                sessionId = "s3",
                weightKg = 100.0,
                reps = 5,
                completedAt = 200L,
                rpe = 7,
            ),
        )
        assertEquals(
            9,
            EffortPrefillPolicy.effortForAutomaticSeed(
                isWarmup = false,
                isHold = false,
                exerciseId = "sq",
                sessionSets = emptyList(),
                priorWorkingSets = prior,
            ),
        )
    }

    @Test
    fun coachApplyCopiesValidSuggestionForWorkingSets() {
        assertEquals(8, EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, 8))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, 5))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, null))
    }

    @Test
    fun coachApplySkipsWarmupsAndHolds() {
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(true, false, 8))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, true, 9))
    }

    private fun setLog(
        id: String,
        exerciseId: String,
        rpe: Int?,
        completedAt: Long,
        isWarmup: Boolean = false,
    ) = SetLog(
        id = id,
        sessionId = "live",
        exerciseId = exerciseId,
        exerciseName = "Squat",
        setNumber = 1,
        weightKg = 100.0,
        reps = 5,
        rpe = rpe,
        isWarmup = isWarmup,
        completedAt = completedAt,
    )
}
