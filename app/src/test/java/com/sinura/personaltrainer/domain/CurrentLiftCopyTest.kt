package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLiftCopyTest {
    @Test
    fun ordinalAndTalkBackNameTheCurrentLift() {
        assertEquals("Lift 1/6", CurrentLiftCopy.liftOrdinal(1, 6))
        assertEquals("2/4", CurrentLiftCopy.workingProgress(2, 4))
        val spoken = CurrentLiftCopy.cardSpoken(
            name = "Back Squat",
            number = 1,
            total = 6,
            workingLogged = 2,
            targetSets = 4,
            equipmentLabel = "Barbell",
            meaning = WeightMeaning.LIFTED,
        )
        assertTrue(spoken.startsWith(CurrentLiftCopy.CURRENT))
        assertTrue(spoken.contains("Back Squat"))
        assertTrue(spoken.contains("Lift 1 of 6"))
        assertTrue(spoken.contains("Working 2/4"))
    }

    @Test
    fun switcherSpokenMarksCurrentAndRest() {
        val spoken = CurrentLiftCopy.switcherSpoken(
            name = "Seated row",
            number = 2,
            total = 6,
            workingLogged = 0,
            targetSets = 3,
            restClock = "1:30",
            restLive = false,
            current = true,
        )
        assertTrue(spoken.contains(CurrentLiftCopy.CURRENT))
        assertTrue(spoken.contains("Lift 2 of 6"))
        assertTrue(spoken.contains("Rest 1:30"))
    }
}
