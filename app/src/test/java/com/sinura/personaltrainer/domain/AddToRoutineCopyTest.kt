package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddToRoutineCopyTest {
    @Test
    fun landingUsesThisLiftsDefaultsNotAUniversalThreeByFive() {
        val squat = lift("squat", "Barbell Back Squat", EquipmentType.BARBELL, LoadType.EXTERNAL, 0.5)
        val raise = lift("raise", "Cable Lateral Raise", EquipmentType.CABLE, LoadType.STACK)
        val squatLanding = AddToRoutineCopy.landing(squat)
        val raiseLanding = AddToRoutineCopy.landing(raise)
        assertEquals(TargetDefaults(3, 5, 150), squatLanding)
        assertEquals(TargetDefaults(3, 12, 60), raiseLanding)
        assertEquals("3 × 5", AddToRoutineCopy.workValue(squatLanding))
        assertEquals("2:30", AddToRoutineCopy.restClock(squatLanding))
        assertEquals("3 × 12", AddToRoutineCopy.workValue(raiseLanding))
        assertEquals("1:00", AddToRoutineCopy.restClock(raiseLanding))
        assertEquals(
            "Barbell Back Squat, 3 × 5, rest 2:30",
            AddToRoutineCopy.spokenLanding(squat),
        )
    }

    @Test
    fun destinationPicturesTheFirstThreeLiftsAndNamesTheKit() {
        val squat = lift("squat", "Squat", EquipmentType.BARBELL, LoadType.EXTERNAL)
        val press = lift("press", "Chest Press", EquipmentType.MACHINE, LoadType.STACK)
        val row = lift("row", "Seated Row", EquipmentType.CABLE, LoadType.STACK)
        val fly = lift("fly", "Cable Fly", EquipmentType.CABLE, LoadType.STACK)
        val upper = routine("Upper", squat, press, row, fly)
        val destination = AddToRoutineCopy.destination(upper, "fly")
        assertEquals(listOf(squat, press, row), AddToRoutineCopy.stills(destination.lifts))
        assertEquals("Barbell · Machine · Cable", AddToRoutineCopy.mix(destination.lifts))
        assertTrue(destination.alreadyHolds)
        assertEquals(AddToRoutineCopy.ALREADY, AddToRoutineCopy.destinationSubtitle(destination))
        assertEquals(
            "Upper, Already in this routine, Barbell · Machine · Cable",
            AddToRoutineCopy.spokenDestination(destination),
        )
    }

    @Test
    fun aRoutineThatDoesNotHoldTheLiftStaysPressableAndCountsItsLifts() {
        val squat = lift("squat", "Squat", EquipmentType.BARBELL, LoadType.EXTERNAL)
        val push = routine("Push", squat)
        val destinations = AddToRoutineCopy.destinations(listOf(push), "bench")
        val destination = destinations.single()
        assertFalse(destination.alreadyHolds)
        assertEquals("1 lift", AddToRoutineCopy.destinationSubtitle(destination))
        assertEquals("Push, 1 lift, Barbell", AddToRoutineCopy.spokenDestination(destination))
    }

    @Test
    fun anEmptyRoutineHasNoStillsAndNoMix() {
        val empty = routine("New")
        val destination = AddToRoutineCopy.destination(empty, "squat")
        assertEquals(emptyList<Exercise>(), AddToRoutineCopy.stills(destination.lifts))
        assertNull(AddToRoutineCopy.mix(destination.lifts))
        assertEquals("0 lifts", AddToRoutineCopy.destinationSubtitle(destination))
        assertEquals(3, AddToRoutineCopy.STILL_LIMIT)
        assertEquals(RoutineCardCopy.STILL_LIMIT, AddToRoutineCopy.STILL_LIMIT)
    }

    @Test
    fun titleNamesTheLiftBeingAdded() {
        assertEquals("Add Barbell Back Squat", AddToRoutineCopy.title("Barbell Back Squat"))
    }

    private fun lift(
        id: String,
        name: String,
        equipment: EquipmentType,
        loadType: LoadType,
        vararg secondaries: Double,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = "Chest",
        notes = "",
        isCustom = false,
        equipment = equipment,
        loadType = loadType,
        muscles = listOf(MuscleCredit("chest", 1.0)) +
            secondaries.mapIndexed { i, w -> MuscleCredit("triceps$i", w) },
    )

    private fun routine(name: String, vararg lifts: Exercise) = Routine(
        id = name.lowercase(),
        name = name,
        notes = "",
        createdAt = 0L,
        updatedAt = 0L,
        exercises = lifts.mapIndexed { index, exercise ->
            RoutineExercise(
                id = "re-$index",
                routineId = name.lowercase(),
                exercise = exercise,
                sortOrder = index,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            )
        },
    )
}
