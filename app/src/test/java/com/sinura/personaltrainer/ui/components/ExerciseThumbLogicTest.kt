package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleCredit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the thumbnail: which way the figure faces, which glyph goes on the badge,
 * and which muscles light up. No Android runtime is involved, so these run on the JVM lane
 * under Gradle. They are not in `domain/` on purpose — a presentation mapping does not belong
 * there just to reach the jar test lane.
 */
class ExerciseThumbLogicTest {
    @Test
    fun everyMuscleGetsTheSettledSide() {
        val expected = mapOf(
            CanonicalMuscle.CHEST to BodyView.FRONT,
            CanonicalMuscle.BICEPS to BodyView.FRONT,
            CanonicalMuscle.CORE to BodyView.FRONT,
            CanonicalMuscle.QUADRICEPS to BodyView.FRONT,
            CanonicalMuscle.SHOULDERS to BodyView.FRONT,
            CanonicalMuscle.OTHER to BodyView.FRONT,
            CanonicalMuscle.BACK to BodyView.BACK,
            CanonicalMuscle.TRICEPS to BodyView.BACK,
            CanonicalMuscle.GLUTES to BodyView.BACK,
            CanonicalMuscle.HAMSTRINGS to BodyView.BACK,
            CanonicalMuscle.CALVES to BodyView.BACK,
        )
        // The map is asserted whole, so adding a muscle without deciding its side fails here
        // rather than silently defaulting to the front.
        assertEquals(CanonicalMuscle.entries.toSet(), expected.keys)
        expected.forEach { (muscle, view) ->
            assertEquals("$muscle faces the wrong way", view, thumbViewFor(muscle))
        }
    }

    @Test
    fun thePrimaryMuscleAlwaysHasAHotspotOnTheViewItChose() {
        // The load-bearing property of the front/back table: if a primary landed on a view
        // with no plate for it, the thumb would draw a body with nothing lit and no error.
        CanonicalMuscle.entries.filter { it != CanonicalMuscle.OTHER }.forEach { muscle ->
            val view = thumbViewFor(muscle)
            assertTrue(
                "$muscle has no hotspot on $view",
                hotspotsFor(view).any { it.muscle == muscle },
            )
        }
    }

    @Test
    fun otherDrawsAPlainFigureRatherThanGuessing() {
        val view = thumbViewFor(CanonicalMuscle.OTHER)
        assertEquals(BodyView.FRONT, view)
        assertTrue(
            "OTHER must light nothing",
            hotspotsFor(view).none { it.muscle == CanonicalMuscle.OTHER },
        )
    }

    @Test
    fun everyEquipmentTypeHasItsOwnGlyph() {
        EquipmentType.entries.forEach { equipment ->
            assertEquals(
                "$equipment should map to its own glyph",
                equipment.name,
                glyphFor(equipment).name,
            )
        }
        // One-to-one in both directions: no glyph is unreachable.
        assertEquals(
            EquipmentGlyph.entries.toSet(),
            EquipmentType.entries.map { glyphFor(it) }.toSet(),
        )
    }

    @Test
    fun unknownEquipmentFallsBackToOther() {
        assertEquals(EquipmentGlyph.OTHER, glyphFor(EquipmentType.fromStorage("nonsense")))
        assertEquals(EquipmentGlyph.OTHER, glyphFor(EquipmentType.fromStorage(null)))
    }

    @Test
    fun musclesComeFromTheJunctionCreditsHeaviestFirst() {
        val bench = Exercise(
            id = "ex-barbell-bench-press", name = "Barbell Bench Press", muscleGroup = "Chest",
            notes = "", isCustom = false,
            muscles = listOf(
                MuscleCredit("chest", 1.0),
                MuscleCredit("triceps", 0.5),
                MuscleCredit("shoulders", 0.25),
            ),
        )
        val (primary, secondaries) = thumbMuscles(bench)
        assertEquals(CanonicalMuscle.CHEST, primary)
        assertEquals(setOf(CanonicalMuscle.TRICEPS, CanonicalMuscle.SHOULDERS), secondaries)
    }

    @Test
    fun aCustomWithNoCreditsFallsBackToItsMuscleGroupText() {
        val mine = Exercise(
            id = "custom-1", name = "My Own Lift", muscleGroup = "quads", notes = "",
            isCustom = true, muscles = emptyList(),
        )
        assertEquals(CanonicalMuscle.QUADRICEPS, thumbMuscles(mine).first)
    }

    @Test
    fun anUnplaceableLiftDrawsAPlainFigureRatherThanThrowing() {
        val mine = Exercise(
            id = "custom-2", name = "Thing", muscleGroup = "vibes", notes = "",
            isCustom = true, muscles = emptyList(),
        )
        assertEquals(CanonicalMuscle.OTHER, thumbMuscles(mine).first)
    }

    @Test
    fun everyBuiltInLiftResolvesToARealMuscleAndALitRegion() {
        // The catalog-wide check: 98 lifts, and not one of them may render as a blank body.
        DefaultExercises.catalog().forEach { seed ->
            val exercise = Exercise(
                id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "",
                isCustom = false, equipment = seed.equipment, loadType = seed.loadType,
                movementKey = seed.movementKey, muscles = seed.credits,
            )
            val (primary, _) = thumbMuscles(exercise)
            assertTrue("${seed.id} resolved to OTHER", primary != CanonicalMuscle.OTHER)
            assertTrue(
                "${seed.id}'s primary $primary has no plate on ${thumbViewFor(primary)}",
                hotspotsFor(thumbViewFor(primary)).any { it.muscle == primary },
            )
        }
    }
}
