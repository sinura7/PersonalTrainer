package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnloadedLoadTest {
    @Test
    fun catalogNamesThatAllowZeroAreExactlyTheSeedRows() {
        assertEquals(ZERO_WEIGHT_CATALOG, UnloadedLoad.names())
        assertEquals(64, UnloadedLoad.catalog().size)
        assertTrue("Walking Lunge" in ZERO_WEIGHT_CATALOG)
        assertTrue("Reverse Lunge" in ZERO_WEIGHT_CATALOG)
        assertTrue("Bulgarian Split Squat" in ZERO_WEIGHT_CATALOG)
        assertTrue("Dumbbell Step-Up" in ZERO_WEIGHT_CATALOG)
        assertTrue("Push-Up" in ZERO_WEIGHT_CATALOG)
        assertTrue("Pull-Up" in ZERO_WEIGHT_CATALOG)
        assertTrue("Dip" in ZERO_WEIGHT_CATALOG)
        assertTrue("Plank" in ZERO_WEIGHT_CATALOG)
        assertTrue("Inverted Row" in ZERO_WEIGHT_CATALOG)
        assertFalse("Barbell Back Squat" in ZERO_WEIGHT_CATALOG)
        assertFalse("Barbell Bench Press" in ZERO_WEIGHT_CATALOG)
        assertFalse("Dumbbell Bench Press" in ZERO_WEIGHT_CATALOG)
        assertFalse("Goblet Squat" in ZERO_WEIGHT_CATALOG)
        assertFalse("Leg Press" in ZERO_WEIGHT_CATALOG)
    }

    @Test
    fun everyUnloadedCatalogRowCanLogZeroAndLoadedBarbellDumbbellCannot() {
        UnloadedLoad.catalog().forEach { seed ->
            assertNull(
                "${seed.name} must accept a 0 kg working set",
                SetLogRules.validate(
                    weightKg = 0.0,
                    reps = 8,
                    isWarmup = false,
                    loadType = seed.loadType,
                    equipment = seed.equipment,
                    movementKey = seed.movementKey,
                ),
            )
        }
        val bench = DefaultExercises.catalog().first { it.id == "ex-barbell-bench-press" }
        val dbBench = DefaultExercises.catalog().first { it.id == "ex-dumbbell-bench-press" }
        val squat = DefaultExercises.catalog().first { it.id == "ex-barbell-back-squat" }
        listOf(bench, dbBench, squat).forEach { seed ->
            assertEquals(
                seed.name,
                SetLogRules.ZERO_WORKING_WEIGHT,
                SetLogRules.validate(
                    weightKg = 0.0,
                    reps = 5,
                    isWarmup = false,
                    loadType = seed.loadType,
                    equipment = seed.equipment,
                    movementKey = seed.movementKey,
                ),
            )
        }
    }

    @Test
    fun recategorizedLungesStayDumbbellsWithOptionalAddedLoad() {
        listOf(
            "ex-walking-lunge",
            "ex-reverse-lunge",
            "ex-bulgarian-split-squat",
            "ex-dumbbell-step-up",
        ).forEach { id ->
            val seed = DefaultExercises.catalog().first { it.id == id }
            assertEquals(seed.name, EquipmentType.DUMBBELL, seed.equipment)
            assertEquals(seed.name, LoadType.BODYWEIGHT_PLUS, seed.loadType)
            assertEquals(LoadClass.BODYWEIGHT_ADDED, LoadClass.of(seed.loadType))
            assertTrue(seed.name, UnloadedLoad.sizesLikeExternalCompound(seed))
        }
        val hyper = DefaultExercises.catalog().first { it.id == "ex-hyper-pro-bulgarian-split-squat" }
        assertFalse(UnloadedLoad.sizesLikeExternalCompound(hyper))
        val lunge = DefaultExercises.catalog().first { it.id == "ex-walking-lunge" }
        assertEquals(
            "13 reps",
            SetCopy.setLine(0.0, 13, LoadClass.of(lunge.loadType), WeightUnit.LBS),
        )
        assertEquals(
            "13 reps +5 lbs",
            SetCopy.setLine(
                WeightConverter.toKg(5.0, WeightUnit.LBS),
                13,
                LoadClass.of(lunge.loadType),
                WeightUnit.LBS,
            ),
        )
    }

    @Test
    fun loadedBarbellAndDumbbellStillDefaultToTheSameSteps() {
        assertEquals(2.5, IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.KG)!!, 0.001)
        assertEquals(5.0, IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)!!, 0.001)
        assertEquals(
            5.0,
            IncrementTable.displayStep(
                LoadType.EXTERNAL,
                WeightUnit.LBS,
                EquipmentType.DUMBBELL,
            )!!,
            0.001,
        )
        assertEquals(
            102.5,
            FloorStepper.nextWeightKg(100.0, WeightUnit.KG, 1, LoadType.EXTERNAL),
            0.0001,
        )
        val db = WeightConverter.toKg(50.0, WeightUnit.LBS)
        val heavier = FloorStepper.nextWeightKg(
            db,
            WeightUnit.LBS,
            1,
            LoadType.EXTERNAL,
            EquipmentType.DUMBBELL,
        )
        assertEquals(55.0, WeightConverter.toDisplayValue(heavier, WeightUnit.LBS), 0.001)
    }

    @Test
    fun unsyncedExternalDumbbellLungeStillLogsZero() {
        // Phones that have not yet taken the v10 upsert still store EXTERNAL.
        assertTrue(
            UnloadedLoad.allowsZeroWorkingWeight(
                LoadType.EXTERNAL,
                EquipmentType.DUMBBELL,
                "lunge",
            ),
        )
        assertFalse(
            UnloadedLoad.allowsZeroWorkingWeight(
                LoadType.EXTERNAL,
                EquipmentType.DUMBBELL,
                "bench-press",
            ),
        )
    }

    companion object {
        val ZERO_WEIGHT_CATALOG = listOf(
            "Bulgarian Split Squat",
            "Walking Lunge",
            "Push-Up",
            "Pull-Up",
            "Chin-Up",
            "Plank",
            "Hanging Leg Raise",
            "Dip",
            "Inverted Row",
            "Diamond Push-Up",
            "Bench Dip",
            "Reverse Lunge",
            "Dumbbell Step-Up",
            "Bodyweight Squat",
            "Back Extension",
            "Nordic Ham Curl",
            "Single-Leg Calf Raise",
            "Decline Sit-Up",
            "Side Plank",
            "Ab Wheel Rollout",
            "Dead Bug",
            "Russian Twist",
            "Assisted Pull-Up",
            "Assisted Chin-Up",
            "Assisted Dip",
            "Hyper Pro 45-Degree Back Extension",
            "Hyper Pro 90-Degree Back Extension",
            "Hyper Pro Bicep Curl",
            "Hyper Pro Bulgarian Split Squat",
            "Hyper Pro Calf Stretch",
            "Hyper Pro Couch Stretch",
            "Hyper Pro Elephant Walk",
            "Hyper Pro External Rotator",
            "Hyper Pro Face Pull",
            "Hyper Pro GHD Sit-Up",
            "Hyper Pro Glute Ham Raise",
            "Hyper Pro Incline Pigeon",
            "Hyper Pro KOT Squat",
            "Hyper Pro Leg Raise",
            "Hyper Pro Nordic Curl",
            "Hyper Pro Pullover",
            "Hyper Pro Push-Up",
            "Hyper Pro QL Raise",
            "Hyper Pro Reverse Hyper",
            "Hyper Pro Reverse Nordic",
            "Hyper Pro Russian Twist",
            "Hyper Pro Sit-Up",
            "Hyper Pro Standing Row",
            "Hyper Pro Trap 3 Raise",
            "Hyper Pro Woodchop",
            "Dead Hang",
            "Scapular Hang",
            "Wall Sit",
            "Deep Squat Hold",
            "Y-Hold",
            "Doorway Chest Stretch",
            "Floor Woodchop",
            "Couch Stretch",
            "Pigeon Stretch",
            "Calf Stretch",
            "90/90 Hips",
            "Ankle Rocks",
            "Joint Circles",
            "Hamstring Stretch",
        )
    }
}
