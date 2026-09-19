package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLiftCopyTest {
    @Test
    fun ordinalAndTalkBackNameTheCurrentLift() {
        assertEquals("Lift 1/6", CurrentLiftCopy.liftOrdinal(1, 6))
        assertEquals("Lift 1 of 6", CurrentLiftCopy.heroOrdinal(1, 6))
        assertEquals("2/4", CurrentLiftCopy.workingProgress(2, 4))
        assertEquals("2 of 4 done", CurrentLiftCopy.heroProgress(2, 4))
        assertEquals("3 done", CurrentLiftCopy.heroProgress(3, 0))
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
        assertTrue(spoken.contains("2 of 4 done"))
        // ADR-027: the identity button is this line, then the set context, then Switch exercise.
        assertEquals("Current. Back Squat. Lift 1 of 6. 2 of 4 done. Barbell", spoken)
    }

    @Test
    fun headerActionsAreNamedNotGuessedFromIcons() {
        assertEquals("Current", CurrentLiftCopy.CURRENT)
        assertEquals("Switch exercise", CurrentLiftCopy.SWITCH)
        assertEquals("Details", CurrentLiftCopy.DETAILS)
        assertEquals("Session notes", CurrentLiftCopy.SESSION_NOTES)
        assertEquals("Swap lift…", CurrentLiftCopy.SWAP)
        assertEquals("Remove lift", CurrentLiftCopy.REMOVE)
        assertEquals("Skip for now", CurrentLiftCopy.SKIP)
        assertEquals("Delete its sets first", CurrentLiftCopy.EDIT_BLOCKED_REASON)
    }

    @Test
    fun equipmentKickerKeepsTheWeightMeaning() {
        // The header's kicker is the equipment; Added weight and Assistance are said next to
        // it, and a lift with no weight to record is Bodyweight only when no kit is named.
        assertEquals("Barbell", CurrentLiftCopy.secondaryLine("Barbell", WeightMeaning.LIFTED))
        assertEquals("Weight", CurrentLiftCopy.secondaryLine("", WeightMeaning.LIFTED))
        assertEquals("Bodyweight", CurrentLiftCopy.secondaryLine("", WeightMeaning.NONE))
        assertEquals("Pull-up bar", CurrentLiftCopy.secondaryLine("Pull-up bar", WeightMeaning.NONE))
        assertEquals("Dip belt · Added weight", CurrentLiftCopy.secondaryLine("Dip belt", WeightMeaning.ADDED))
        assertEquals("Added weight", CurrentLiftCopy.secondaryLine("", WeightMeaning.ADDED))
        assertEquals("Machine · Assistance", CurrentLiftCopy.secondaryLine("Machine", WeightMeaning.ASSISTANCE))
    }

    @Test
    fun heroSpokenAppendsTelemetryAfterTheIdentity() {
        val identity = CurrentLiftCopy.heroSpoken(
            name = "Back Squat",
            number = 1,
            total = 6,
            workingLogged = 2,
            targetSets = 4,
            equipmentLabel = "Barbell",
            meaning = WeightMeaning.LIFTED,
        )
        assertEquals("Current. Back Squat. Lift 1 of 6. 2 of 4 done. Barbell", identity)
        val withTelemetry = CurrentLiftCopy.heroSpoken(
            name = "Back Squat",
            number = 1,
            total = 6,
            workingLogged = 2,
            targetSets = 4,
            equipmentLabel = "Barbell",
            meaning = WeightMeaning.LIFTED,
            telemetry = " Set 3 of 4 ",
        )
        assertEquals("$identity. Set 3 of 4", withTelemetry)
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
        assertTrue(spoken.contains("Working 0/3"))
        assertTrue(spoken.contains("Rest 1:30"))
        val live = CurrentLiftCopy.switcherSpoken(
            name = "Seated row",
            number = 2,
            total = 6,
            workingLogged = 1,
            targetSets = 3,
            restClock = "0:45",
            restLive = true,
            current = false,
        )
        assertEquals("Seated row. Lift 2 of 6. Working 1/3. Rest remaining 0:45", live)
    }
}
