package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextLiftCopyTest {
    @Test
    fun plannedWorkIsTheSetProgressLine() {
        assertEquals(
            "Set 1 of 3 · target 3 × 12 @ 55 lbs",
            NextLiftCopy.plannedWork(
                workingLogged = 0,
                targetSets = 3,
                targetReps = 12,
                targetWeightLabel = "55 lbs",
            ),
        )
        val spoken = NextLiftCopy.spoken(
            name = "Seated Dumbbell Press",
            plannedWork = "Set 1 of 3 · target 3 × 12 @ 110 lbs",
            restClock = "2:00",
            restLive = false,
        )
        assertTrue(spoken, spoken.startsWith("Seated Dumbbell Press."))
        assertTrue(spoken, spoken.contains("Set 1 of 3"))
        assertTrue(spoken, spoken.contains("Rest 2:00"))
        assertTrue(spoken, !spoken.contains("Sets Set"))
    }
}
