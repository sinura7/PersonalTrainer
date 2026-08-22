package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateMathTest {
    @Test
    fun hundredKilosUsesTheLargestPlatesThatFit() {
        val load = PlateMath.load(100.0, WeightUnit.KG)!!
        assertEquals("20 kg bar + 1×25 + 1×15 / side", load.caption())
    }

    @Test
    fun twoFortyFivesASideOnAPoundBar() {
        val targetKg = WeightConverter.toKg(225.0, WeightUnit.LBS)
        val load = PlateMath.load(targetKg, WeightUnit.LBS)!!
        assertEquals("45 lbs bar + 2×45 / side", load.caption())
    }

    @Test
    fun barOnlyHasNoPlates() {
        val load = PlateMath.load(PlateMath.BAR_KG, WeightUnit.KG)!!
        assertEquals("20 kg bar", load.caption())
        assertEquals(emptyList<PlateCount>(), load.sides)
    }

    @Test
    fun leftoverThatCannotBePlatedIsNamed() {
        val targetKg = WeightConverter.toKg(47.0, WeightUnit.LBS)
        val load = PlateMath.load(targetKg, WeightUnit.LBS)!!
        assertEquals("45 lbs bar, and 2 leftover", load.caption())
    }

    @Test
    fun belowTheBarIsSilent() {
        assertNull(PlateMath.load(10.0, WeightUnit.KG))
        assertNull(PlateMath.load(0.0, WeightUnit.LBS))
    }
}
