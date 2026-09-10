package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routine editor's target boxes, read as typed. UX06: a box that cannot be stored as
 * written is a complaint under that box, never a rewritten value and never "leave it alone".
 */
class TargetEntryTest {
    @Test
    fun cleanBoxesReadAsTheirValues() {
        val entry = TargetEntry.read("4", "8", "90", "102,5", WeightUnit.KG)
        assertFalse(entry.hasError)
        assertNull(entry.firstError)
        assertEquals(4, entry.typedSets)
        assertEquals(8, entry.typedReps)
        assertEquals(90, entry.typedRest)
        assertEquals(102.5, entry.typedWeightKg!!, 0.0001)
    }

    @Test
    fun emptyBoxesMeanLeaveAloneExceptTheWeight() {
        val entry = TargetEntry.read("", "", "", "", WeightUnit.KG)
        assertFalse(entry.hasError)
        assertNull(entry.typedSets)
        assertNull(entry.typedReps)
        assertNull(entry.typedRest)
        // A cleared weight is "no target" — the same answer a typed zero has always given.
        assertNull(entry.typedWeightKg)
        assertNull(TargetEntry.read("3", "5", "60", "0", WeightUnit.KG).typedWeightKg)
    }

    @Test
    fun aDecimalRepCountIsAComplaintNotEightyFiveAndNotLeaveAlone() {
        val entry = TargetEntry.read("3", "8.5", "60", "", WeightUnit.KG)
        assertTrue(entry.hasError)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, entry.repsError)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, entry.firstError)
        assertNull(entry.setsError)
        assertNull(entry.restError)
        assertNull(entry.weightError)
        assertNull(entry.typedReps)
    }

    @Test
    fun everyBoxCanComplainAtOnceInReadingOrder() {
        val entry = TargetEntry.read("0", "x", "1:30", "-50", WeightUnit.LBS)
        assertEquals(NumericEntry.SETS_RULE, entry.setsError)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, entry.repsError)
        assertEquals(NumericEntry.REST_RULE, entry.restError)
        assertEquals(NumericEntry.WEIGHT_NEGATIVE, entry.weightError)
        assertEquals(NumericEntry.SETS_RULE, entry.firstError)
    }

    /** Storage never capped a target's reps; a stored 3×120 card must stay editable. */
    @Test
    fun aHighRepTargetIsNotRefused() {
        val entry = TargetEntry.read("3", "120", "60", "", WeightUnit.KG)
        assertFalse(entry.hasError)
        assertEquals(120, entry.typedReps)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, TargetEntry.read("3", "0", "60", "", WeightUnit.KG).repsError)
    }

    @Test
    fun weightIsReadInTheDisplayUnit() {
        val entry = TargetEntry.read("3", "5", "60", "225", WeightUnit.LBS)
        assertEquals(225.0, WeightConverter.kgToLbs(entry.typedWeightKg!!), 0.5)
        assertTrue(TargetEntry.read("3", "5", "60", "1.2.3", WeightUnit.LBS).hasError)
    }

    /**
     * A box that cannot be read issues no instruction — the rule the routine editor lost.
     *
     * Weight is the one column where null carries a meaning of its own: it clears the stored
     * target. [TargetEntry.typedWeightKg] hands back null for an unreadable box exactly as it
     * does for a deliberately emptied one, so a caller that takes it at face value turns "-50"
     * into "no target". [TargetEntry.weightToStage] is the accessor that keeps them apart.
     */
    @Test
    fun anUnreadableWeightBoxStagesTheStoredTargetRatherThanClearingIt() {
        for (unreadable in listOf("-50", "6o", "1.2.3", "1,000", "62.555")) {
            val entry = TargetEntry.read("3", "5", "90", unreadable, WeightUnit.KG)
            assertTrue(unreadable, entry.weightUnreadable)
            assertNull(unreadable, entry.typedWeightKg)
            assertEquals(unreadable, 100.0, entry.weightToStage(100.0)!!, 0.0001)
        }
    }

    /** A weight the owner really did clear still clears the target; so does a typed zero. */
    @Test
    fun aClearedOrZeroWeightBoxStillClearsTheStoredTarget() {
        for (cleared in listOf("", "  ", "0")) {
            val entry = TargetEntry.read("3", "5", "90", cleared, WeightUnit.KG)
            assertFalse(cleared, entry.weightUnreadable)
            assertNull(cleared, entry.weightToStage(100.0))
        }
    }

    /** A weight that reads cleanly is staged as typed, whatever is stored. */
    @Test
    fun aReadableWeightIsStagedAsTyped() {
        val entry = TargetEntry.read("3", "5", "90", "62.5", WeightUnit.KG)
        assertEquals(62.5, entry.weightToStage(100.0)!!, 0.0001)
        assertEquals(62.5, entry.weightToStage(null)!!, 0.0001)
    }

    /** With no target stored, an unreadable box still writes nothing — null compares equal. */
    @Test
    fun anUnreadableWeightBoxOnACardWithNoTargetStagesNothing() {
        assertNull(TargetEntry.read("3", "5", "90", "-50", WeightUnit.KG).weightToStage(null))
    }
}
