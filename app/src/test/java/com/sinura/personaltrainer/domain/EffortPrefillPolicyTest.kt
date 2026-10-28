package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortPrefillPolicyTest {
    @Test
    fun automaticSeedNeverPrefillsEffort() {
        assertNull(EffortPrefillPolicy.effortForAutomaticSeed(isWarmup = false, isHold = false))
        assertNull(EffortPrefillPolicy.effortForAutomaticSeed(isWarmup = true, isHold = false))
        assertNull(EffortPrefillPolicy.effortForAutomaticSeed(isWarmup = false, isHold = true))
    }

    @Test
    fun applyCopiesValidWorkingSetEffort() {
        assertEquals(8, EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, 8))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, 5))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, false, null))
    }

    @Test
    fun applyNeverCopiesEffortForWarmupOrHold() {
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(true, false, 8))
        assertNull(EffortPrefillPolicy.effortWhenApplyingCoachRecommendation(false, true, 9))
    }
}
