package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-launch Body names catalog lifts. Not ownership — there is none yet —
 * and not a Start button. The gym wall, filtered by the kit the owner said
 * they have.
 */
class BodyExplorerTest {
    private val catalog = DefaultExercises.catalog().map { it.toExercise() }

    @Test
    fun coverageNamesThePosterCompoundsOnAFullGym() {
        val lifts = BodyExplorer.coverage(catalog)
        assertEquals(
            listOf(
                "ex-barbell-back-squat",
                "ex-barbell-bench-press",
                "ex-barbell-row",
                "ex-romanian-deadlift",
            ),
            lifts.map { it.id },
        )
    }

    @Test
    fun coverageOmitsHyperProOnTheDefaultGym() {
        val lifts = BodyExplorer.coverage(catalog)
        assertTrue(lifts.none { it.equipment == EquipmentType.HYPER_PRO })
        assertTrue(catalog.any { it.equipment == EquipmentType.HYPER_PRO })
    }

    @Test
    fun dumbbellOnlyGymDoesNotNameABarbell() {
        val prefs = CoachPreferences(availableEquipment = setOf(EquipmentType.DUMBBELL.name))
        val lifts = BodyExplorer.coverage(catalog, prefs)
        assertTrue(lifts.isNotEmpty())
        assertFalse(lifts.any { it.equipment == EquipmentType.BARBELL })
        assertEquals("ex-goblet-squat", lifts.first { it.id.contains("squat") }.id)
        assertEquals("ex-dumbbell-bench-press", lifts.first { it.id.contains("bench") }.id)
    }

    @Test
    fun chestSheetLeadsWithBenchAndDoesNotListFourBenches() {
        val lifts = BodyExplorer.forMuscle(CanonicalMuscle.CHEST, catalog)
        assertEquals("ex-barbell-bench-press", lifts.first().id)
        assertEquals(lifts.size, lifts.map { it.movementKey ?: it.id }.distinct().size)
        assertTrue(lifts.size in 2..BodyExplorer.SHEET_LIMIT)
        assertTrue(lifts.any { it.movementKey == "chest-fly" || it.movementKey == "push-up" })
    }

    @Test
    fun emptyCatalogIsEmptyNotInvented() {
        assertTrue(BodyExplorer.coverage(emptyList()).isEmpty())
        assertTrue(
            BodyExplorer.forMuscle(CanonicalMuscle.CHEST, emptyList()).isEmpty(),
        )
    }

    @Test
    fun otherIsNotOnTheGymWall() {
        assertTrue(
            BodyExplorer.forMuscle(CanonicalMuscle.OTHER, catalog).isEmpty(),
        )
    }

    @Test
    fun everyEquipmentTypeHasAFloorRank() {
        assertEquals(EquipmentType.entries.size, 10)
        EquipmentType.entries.forEach { type ->
            val rank = BodyExplorer.equipmentRank(type)
            assertTrue("$type rank $rank", rank in 0..9)
        }
        assertEquals(0, BodyExplorer.equipmentRank(EquipmentType.BARBELL))
        assertEquals(9, BodyExplorer.equipmentRank(EquipmentType.HYPER_PRO))
    }

    private fun SeedExercise.toExercise(): Exercise = Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        notes = "",
        isCustom = false,
        equipment = equipment,
        loadType = loadType,
        movementKey = movementKey,
        imageKey = imageKey,
        muscles = credits,
    )
}
