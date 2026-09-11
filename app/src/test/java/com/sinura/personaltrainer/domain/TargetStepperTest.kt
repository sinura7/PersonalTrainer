package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetStepperTest {
    @Test
    fun setsAndRepsNeverDropBelowOne() {
        assertEquals(1, TargetStepper.nextSets(1, -1))
        assertEquals(4, TargetStepper.nextSets(3, 1))
        assertEquals(1, TargetStepper.nextReps(1, -1))
        assertEquals(6, TargetStepper.nextReps(5, 1))
    }

    @Test
    fun restStepsByTheLiveTimerFloorAndStopsAtZero() {
        assertEquals(15, TargetStepper.REST_STEP_SECONDS)
        assertEquals(RestTimerPreferences.MIN_SECONDS, TargetStepper.REST_STEP_SECONDS)
        assertEquals(75, TargetStepper.nextRestSeconds(90, -1))
        assertEquals(105, TargetStepper.nextRestSeconds(90, 1))
        assertEquals(0, TargetStepper.nextRestSeconds(10, -1))
        assertEquals(0, TargetStepper.nextRestSeconds(0, -1))
        assertEquals(15, TargetStepper.nextRestSeconds(0, 1))
    }

    @Test
    fun aZeroWeightWellIsNoTarget() {
        assertNull(TargetStepper.weightToStage(0.0))
        assertEquals(2.5, TargetStepper.weightToStage(2.5)!!, 0.0001)
        assertEquals(
            2.5,
            WeightConverter.incrementKg(0.0, WeightUnit.KG, 1),
            0.0001,
        )
    }

    @Test
    fun theEditorStripUsesTheWorkoutWellsNotOutlinedBoxes() {
        val strip = readUi("routines/SessionLiftStrip.kt")
        assertTrue(strip.contains("NumeralWell("))
        assertTrue(strip.contains("NumberEntryDialog("))
        assertTrue(strip.contains("TargetStepper."))
        assertFalse(strip.contains("OutlinedTextField"))
        assertFalse(strip.contains("MiniNumberField"))
        assertFalse(strip.contains("ROUTINE_EDITOR_CHAIN"))
    }

    private fun readUi(relative: String): String {
        val roots = listOf(
            java.io.File("app/src/main/java/com/sinura/personaltrainer/ui"),
            java.io.File("../app/src/main/java/com/sinura/personaltrainer/ui"),
        )
        val file = roots.map { java.io.File(it, relative) }.first { it.isFile }
        return file.readText()
    }
}
