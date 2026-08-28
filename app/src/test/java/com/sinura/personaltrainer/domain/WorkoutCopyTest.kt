package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCopyTest {
    @Test
    fun countsUpToTheTarget() {
        assertEquals(
            "Set 1 of 5 · target 5 × 8",
            WorkoutCopy.setProgress(workingLogged = 0, targetSets = 5, targetReps = 8),
        )
        assertEquals(
            "Set 5 of 5 · target 5 × 8",
            WorkoutCopy.setProgress(workingLogged = 4, targetSets = 5, targetReps = 8),
        )
    }

    @Test
    fun neverSaysSetSixOfFive() {
        // The whole reason this is a function. A sixth working set on a five-set lift used to
        // read "Set 6 of 5", which is the app getting arithmetic wrong in front of someone who
        // had just done more than they planned.
        assertEquals(
            "Set 6 · target was 5 × 8",
            WorkoutCopy.setProgress(workingLogged = 5, targetSets = 5, targetReps = 8),
        )
        assertEquals(
            "Set 9 · target was 5 × 8",
            WorkoutCopy.setProgress(workingLogged = 8, targetSets = 5, targetReps = 8),
        )
    }

    @Test
    fun theTargetWeightIsAppendedWhenThereIsOne() {
        assertEquals(
            "Set 2 of 3 · target 3 × 5 @ 100 kg",
            WorkoutCopy.setProgress(
                workingLogged = 1,
                targetSets = 3,
                targetReps = 5,
                targetWeightLabel = "100 kg",
            ),
        )
        assertEquals(
            "Set 4 · target was 3 × 5 @ 100 kg",
            WorkoutCopy.setProgress(
                workingLogged = 3,
                targetSets = 3,
                targetReps = 5,
                targetWeightLabel = "100 kg",
            ),
        )
    }

    @Test
    fun nonsensicalTargetsAreFloored() {
        // A routine row with zero sets is not supposed to exist, but "Set 1 of 0" must not be
        // what tells the user it does.
        assertEquals(
            "Set 1 of 1 · target 1 × 1",
            WorkoutCopy.setProgress(workingLogged = 0, targetSets = 0, targetReps = 0),
        )
        assertEquals(
            "Set 1 of 5 · target 5 × 8",
            WorkoutCopy.setProgress(workingLogged = -3, targetSets = 5, targetReps = 8),
        )
    }

    @Test
    fun liveRecRepsAndWeightReplaceThePrescriptionClause() {
        assertEquals(
            "Set 2 of 3 · target 3 × 6 @ 102.5 kg",
            WorkoutCopy.setProgress(
                workingLogged = 1,
                targetSets = 3,
                targetReps = 5,
                targetWeightLabel = "100 kg",
                liveReps = 6,
                liveWeightLabel = "102.5 kg",
            ),
        )
    }
}
