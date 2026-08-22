package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.domain.CanonicalMuscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Temper plate tables: every working muscle has a plate on the view the thumbs pick,
 * and no plate leaves the figure box.
 */
class FigureArtTest {
    @Test
    fun everyWorkingMuscleHasAPlateOnItsSettledView() {
        CanonicalMuscle.entries.filter { it != CanonicalMuscle.OTHER }.forEach { muscle ->
            val view = thumbViewFor(muscle)
            assertTrue(
                "$muscle has no plate on $view",
                platesFor(view).any { it.muscle == muscle },
            )
        }
    }

    @Test
    fun otherNeverOwnsAPlate() {
        BodyView.entries.forEach { view ->
            assertTrue(
                "$view must not light OTHER",
                platesFor(view).none { it.muscle == CanonicalMuscle.OTHER },
            )
        }
    }

    @Test
    fun everyPlateStaysInsideTheFigureBox() {
        BodyView.entries.forEach { view ->
            platesFor(view).forEach { plate ->
                plate.points.forEach { (x, y) ->
                    assertTrue("$view $plate x=$x", x in 0f..1f)
                    assertTrue("$view $plate y=$y", y in 0f..1f)
                }
            }
        }
    }

    @Test
    fun theTemperAccentIsTheViewersRightPec() {
        val accents = platesFor(BodyView.FRONT).filter { it.isTemperAccent() }
        assertEquals(1, accents.size)
        assertTrue(accents.single().left > 0.5f)
    }

    @Test
    fun frontAndBackHaveTheSameWorkingMuscleCountAsTheHotspotTables() {
        BodyView.entries.forEach { view ->
            val plated = platesFor(view).mapNotNull { it.muscle }.toSet()
            val spotted = hotspotsFor(view).map { it.muscle }.toSet()
            assertEquals(plated, spotted)
        }
    }
}
