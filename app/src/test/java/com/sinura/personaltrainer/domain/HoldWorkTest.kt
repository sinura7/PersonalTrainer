package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldWorkTest {
    @Test
    fun namedStaticHoldsAreTimeNotReps() {
        val catalog = WorkoutPaste.catalogExercises()
        listOf(
            "ex-dead-hang",
            "ex-scapular-hang",
            "ex-wall-sit",
            "ex-side-plank",
            "ex-deep-squat-hold",
            "ex-y-hold",
            "ex-plank",
            "ex-doorway-chest-stretch",
            "ex-couch-stretch",
            "ex-pigeon-stretch",
            "ex-calf-stretch",
            "ex-90-90-hips",
            "ex-hamstring-stretch",
            "ex-hyper-pro-incline-pigeon",
        ).forEach { id ->
            val lift = catalog.first { it.id == id }
            assertTrue("$id must be a hold", HoldWork.isHold(lift))
        }
        val weighted = Exercise(
            id = "custom-weighted-plank",
            name = "Weighted Plank",
            muscleGroup = "Core",
            notes = "",
            isCustom = true,
            equipment = EquipmentType.BODYWEIGHT,
            loadType = LoadType.BODYWEIGHT_PLUS,
        )
        assertTrue(HoldWork.isHold(weighted))
        val isometric = Exercise(
            id = "custom-iso",
            name = "Iso hold",
            muscleGroup = "Core",
            notes = "",
            isCustom = true,
            movementKey = "isometric",
        )
        assertTrue(HoldWork.isHold(isometric))
        listOf("ex-ankle-rocks", "ex-joint-circles", "ex-floor-woodchop").forEach { id ->
            val lift = catalog.first { it.id == id }
            assertFalse("$id must stay reps", HoldWork.isHold(lift))
        }
    }

    @Test
    fun hangingLegRaiseStaysReps() {
        val raise = WorkoutPaste.catalogExercises().first { it.id == "ex-hanging-leg-raise" }
        assertFalse(HoldWork.isHold(raise))
        assertFalse(HoldWork.looksLikeHold("ex-hanging-leg-raise", "Hanging Leg Raise"))
    }

    @Test
    fun parseRangeReadsSecondsAndEnDash() {
        assertEquals(HoldWork.HoldRange(20, null), HoldWork.parseRange("20"))
        assertEquals(HoldWork.HoldRange(20, null), HoldWork.parseRange("20s"))
        assertEquals(HoldWork.HoldRange(30, null), HoldWork.parseRange("0:30"))
        assertEquals(HoldWork.HoldRange(20, 40), HoldWork.parseRange("20-40"))
        assertEquals(HoldWork.HoldRange(20, 40), HoldWork.parseRange("20–40s"))
        assertNull(HoldWork.parseRange("2"))
        assertNull(HoldWork.parseRange(""))
    }

    @Test
    fun elapsedNeverLogsZeroAndCountdownHitsThePlan() {
        assertEquals(1, HoldWork.elapsedSeconds(totalSeconds = 30, remainingSeconds = 30))
        assertEquals(10, HoldWork.elapsedSeconds(totalSeconds = 30, remainingSeconds = 20))
        assertEquals(30, HoldWork.elapsedSeconds(totalSeconds = 30, remainingSeconds = 0))
        assertEquals(30, HoldWork.countdownSeconds(30))
        assertEquals(HoldWork.DEFAULT_SECONDS, HoldWork.countdownSeconds(null))
        assertEquals("20–40s", HoldWork.formatRange(20, 40))
        assertEquals("2 × 30s", HoldWork.workLine(2, 30))
    }

    @Test
    fun catalogHoldsUseTheHoldDefaults() {
        val hang = WorkoutPaste.catalogExercises().first { it.id == "ex-dead-hang" }
        val defaults = AddDefaults.forExercise(hang)
        assertEquals(2, defaults.sets)
        assertEquals(HoldWork.HOLD_REPS_PLACEHOLDER, defaults.reps)
        assertEquals(HoldWork.DEFAULT_SECONDS, defaults.seconds)
        assertEquals(WorkoutPasteRest.ACCESSORY_SECONDS, defaults.restSeconds)
        assertNotNull(defaults.seconds)
        assertEquals("2 × 30s · 1:15", defaults.previewLine())
    }
}
