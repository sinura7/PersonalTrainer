package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One increment table, three readers, and the defect that proved they were not one.
 */
class IncrementTableTest {
    @Test
    fun kilogramUsersStepInKilograms() {
        assertEquals(2.5, IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.KG)!!, 0.001)
        assertEquals(2.5, IncrementTable.stepKg(LoadType.EXTERNAL, WeightUnit.KG)!!, 0.001)
        assertEquals("2.5 kg", IncrementTable.stepLabel(LoadType.EXTERNAL, WeightUnit.KG))
    }

    @Test
    fun poundUsersStepInPoundsAndTheStoredKilogramsRoundTrip() {
        assertEquals(5.0, IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)!!, 0.001)
        assertEquals("5 lbs", IncrementTable.stepLabel(LoadType.EXTERNAL, WeightUnit.LBS))

        // The recorded defect said "+5.5 lbs" — 2.5 kg converted. What goes into the database is
        // now the pound step converted the other way, so it comes back out as exactly 5.
        val stepKg = IncrementTable.stepKg(LoadType.EXTERNAL, WeightUnit.LBS)!!
        assertEquals(5.0, WeightConverter.toDisplayValue(stepKg, WeightUnit.LBS), 0.05)
    }

    @Test
    fun everyLoadableClassStepsTheSameWay() {
        listOf(LoadType.EXTERNAL, LoadType.STACK, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED)
            .forEach { loadType ->
                assertNotNull("$loadType must have a step", IncrementTable.displayStep(loadType, WeightUnit.KG))
                assertEquals(2.5, IncrementTable.displayStep(loadType, WeightUnit.KG)!!, 0.001)
            }
    }

    @Test
    fun bodyweightHasNoStepInAnyUnit() {
        // Not zero — absent. A "+0 kg" suggestion is as useless as a "+2.5 kg" one on a push-up,
        // and only null forces the caller to say something else.
        WeightUnit.entries.forEach { unit ->
            assertNull(IncrementTable.displayStep(LoadType.BODYWEIGHT, unit))
            assertNull(IncrementTable.stepKg(LoadType.BODYWEIGHT, unit))
            assertNull(IncrementTable.stepLabel(LoadType.BODYWEIGHT, unit))
        }
    }

    @Test
    fun theStepperAndTheCalculatorReadTheSameTable() {
        // The whole point of the table: these were two systems, and they disagreed.
        WeightUnit.entries.forEach { unit ->
            assertEquals(
                "WeightUnit.step must be the table's EXTERNAL step for $unit",
                IncrementTable.displayStep(LoadType.EXTERNAL, unit)!!,
                unit.step,
                0.001,
            )
        }
    }

    @Test
    fun theWeightFieldKeepsSteppingForBodyweightLifts() {
        // WeightUnit.step is the FIELD's increment, not a lift's. A weighted dip must still be
        // able to type in its added load, so the stepper never inherits the bodyweight null.
        assertEquals(2.5, WeightUnit.KG.step, 0.001)
        assertEquals(5.0, WeightUnit.LBS.step, 0.001)
    }
}

class AddDefaultsTest {
    private fun lift(loadType: LoadType, vararg secondaries: Double) = Exercise(
        id = "x", name = "X", muscleGroup = "Chest", notes = "", isCustom = false,
        loadType = loadType,
        muscles = listOf(MuscleCredit("chest", 1.0)) +
            secondaries.mapIndexed { i, w -> MuscleCredit("triceps$i", w) },
    )

    @Test
    fun everyRowOfTheTable() {
        assertEquals(TargetDefaults(3, 5, 150), AddDefaults.forExercise(LoadType.EXTERNAL, true))
        assertEquals(TargetDefaults(3, 10, 90), AddDefaults.forExercise(LoadType.EXTERNAL, false))
        assertEquals(TargetDefaults(3, 10, 90), AddDefaults.forExercise(LoadType.STACK, true))
        assertEquals(TargetDefaults(3, 12, 60), AddDefaults.forExercise(LoadType.STACK, false))
        assertEquals(TargetDefaults(3, 6, 120), AddDefaults.forExercise(LoadType.BODYWEIGHT_PLUS, true))
        assertEquals(TargetDefaults(3, 6, 120), AddDefaults.forExercise(LoadType.BODYWEIGHT_PLUS, false))
        assertEquals(TargetDefaults(3, 8, 90), AddDefaults.forExercise(LoadType.BODYWEIGHT, true))
        assertEquals(TargetDefaults(3, 12, 60), AddDefaults.forExercise(LoadType.BODYWEIGHT, false))
        assertEquals(TargetDefaults(3, 8, 90), AddDefaults.forExercise(LoadType.ASSISTED, true))
        assertEquals(TargetDefaults(3, 8, 90), AddDefaults.forExercise(LoadType.ASSISTED, false))
    }

    @Test
    fun anAccessoryIsTheSameLiftDoneLater() {
        // Derived from the primary row, so there is one table to argue with, not two.
        assertEquals(
            TargetDefaults(3, 8, 90),
            AddDefaults.forExercise(LoadType.EXTERNAL, isCompound = true, role = LiftRole.ACCESSORY),
        )
        assertEquals(
            TargetDefaults(3, 12, 60),
            AddDefaults.forExercise(LoadType.EXTERNAL, isCompound = false, role = LiftRole.ACCESSORY),
        )
    }

    @Test
    fun anAccessoryNeverAsksForFewerRepsOrMoreRest() {
        LoadType.entries.forEach { loadType ->
            listOf(true, false).forEach { compound ->
                val primary = AddDefaults.forExercise(loadType, compound, LiftRole.PRIMARY)
                val accessory = AddDefaults.forExercise(loadType, compound, LiftRole.ACCESSORY)
                assertTrue("$loadType/$compound reps", accessory.reps >= primary.reps)
                assertTrue("$loadType/$compound rest", accessory.restSeconds <= primary.restSeconds)
                assertTrue("$loadType/$compound floor", accessory.restSeconds >= 60)
                assertTrue("$loadType/$compound cap", accessory.reps <= 12)
            }
        }
    }

    @Test
    fun aHandAddedLiftIsUnchangedByTheRoleParameter() {
        // PRIMARY is the default, and PRIMARY is exactly what shipped before roles existed.
        LoadType.entries.forEach { loadType ->
            listOf(true, false).forEach { compound ->
                assertEquals(
                    AddDefaults.forExercise(loadType, compound),
                    AddDefaults.forExercise(loadType, compound, LiftRole.PRIMARY),
                )
            }
        }
    }

    @Test
    fun landingCopyNamesThisLiftNotAUniversalThreeByFive() {
        assertEquals("3 × 5 · 2:30", TargetDefaults(3, 5, 150).previewLine())
        assertEquals("3 × 12 · 1:00", TargetDefaults(3, 12, 60).previewLine())
        val squat = lift(LoadType.EXTERNAL, 0.5)
        assertEquals(
            "Lands at 3 × 5 · 2:30 — editable on the routine.",
            AddDefaults.landingCopy(squat),
        )
        val raise = lift(LoadType.STACK, 0.0)
        assertEquals(
            "Lands at 3 × 12 · 1:00 — editable on the routine.",
            AddDefaults.landingCopy(raise),
        )
    }

    @Test
    fun anUnknownLoadTypeTakesTheIsolationFallback() {
        // A custom the user typed in. Too many reps at too little rest is a bad set; too few
        // reps at too much rest is a wasted afternoon — so the fallback errs toward isolation.
        assertEquals(TargetDefaults(3, 10, 90), AddDefaults.forExercise(loadType = null, isCompound = false))
    }

    @Test
    fun compoundnessComesFromTheJunctionCredits() {
        assertEquals(true, AddDefaults.isCompound(lift(LoadType.EXTERNAL, 0.5)))
        assertEquals(false, AddDefaults.isCompound(lift(LoadType.EXTERNAL, 0.25)))
        assertEquals(false, AddDefaults.isCompound(lift(LoadType.EXTERNAL)))
    }

    @Test
    fun theCatalogsOwnLiftsLandWhereTheOwnerWillExpect() {
        val catalog = DefaultExercises.catalog().associateBy { it.id }
        fun defaultsFor(id: String): TargetDefaults {
            val seed = catalog.getValue(id)
            return AddDefaults.forExercise(
                Exercise(
                    id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "",
                    isCustom = false, equipment = seed.equipment, loadType = seed.loadType,
                    movementKey = seed.movementKey, muscles = seed.credits,
                ),
            )
        }
        // The three the owner's device checklist reads off.
        assertEquals(TargetDefaults(3, 10, 90), defaultsFor("ex-machine-chest-press"))
        assertEquals(TargetDefaults(3, 6, 120), defaultsFor("ex-dip"))
        assertEquals(TargetDefaults(3, 5, 150), defaultsFor("ex-barbell-back-squat"))
    }
}

class ProgressionCopyTest {
    private fun hint(
        loadType: LoadType?,
        action: ProgressionAction = ProgressionAction.INCREASE,
        rpeHold: Boolean = false,
    ) = ProgressionHint(
        exerciseId = "x", exerciseName = "X", lastWeightKg = 100.0, lastReps = 5, targetReps = 5,
        suggestedWeightKg = 102.5, action = action, rpeHold = rpeHold, loadType = loadType,
    )

    @Test
    fun aLoadedLiftIsToldWhatToAdd() {
        assertEquals("Hit target. Add 2.5 kg.", ProgressionCopy.stripReason(hint(LoadType.EXTERNAL), WeightUnit.KG))
        assertEquals("Hit target. Add 5 lbs.", ProgressionCopy.stripReason(hint(LoadType.EXTERNAL), WeightUnit.LBS))
    }

    @Test
    fun aBodyweightLiftIsToldToAddARepNotAKilogram() {
        val strip = ProgressionCopy.stripReason(hint(LoadType.BODYWEIGHT), WeightUnit.KG)
        assertEquals(IncrementTable.REP_PROGRESSION_COPY, strip)
        // Both surfaces agree, which is the point of putting the sentence here.
        val coach = ProgressionCopy.coachReason(hint(LoadType.BODYWEIGHT), WeightUnit.KG)
        assertEquals(true, coach.endsWith(IncrementTable.REP_PROGRESSION_COPY))
    }

    @Test
    fun aWeightedPullUpIsStillALoadedLift() {
        // Pull-Up ships as BODYWEIGHT_PLUS, so the owner should be told to add weight to it.
        assertEquals(
            "Hit target. Add 2.5 kg.",
            ProgressionCopy.stripReason(hint(LoadType.BODYWEIGHT_PLUS), WeightUnit.KG),
        )
    }

    @Test
    fun anRpeHoldExplainsItself() {
        val strip = ProgressionCopy.stripReason(
            hint(LoadType.EXTERNAL, ProgressionAction.HOLD, rpeHold = true),
            WeightUnit.KG,
        )
        assertEquals("Top set at RPE 9+. Hold 100 kg.", strip)
    }

    @Test
    fun aBodyweightLiftThatMissedIsToldAboutRepsToo() {
        val strip = ProgressionCopy.stripReason(
            hint(LoadType.BODYWEIGHT, ProgressionAction.DECREASE),
            WeightUnit.KG,
        )
        // Never "Drop 2.5 kg" on a push-up.
        assertEquals("Keep 5 reps.", strip)
    }

    @Test
    fun anUnknownLoadTypeIsTreatedAsLoadable() {
        // Refusing to suggest anything for a custom is worse than suggesting the common step.
        assertEquals("Hit target. Add 2.5 kg.", ProgressionCopy.stripReason(hint(null), WeightUnit.KG))
    }
}
