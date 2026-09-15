package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestPrescriptionTest {
    @Test
    fun aHeavyTripleRestsLongerThanASetOfFifteen() {
        val heavy = RestPrescription.seconds(
            reasonCode = SetMicroRecCalculator.QUALITY,
            loadType = LoadType.EXTERNAL,
            reps = 3,
        )
        val highRep = RestPrescription.seconds(
            reasonCode = SetMicroRecCalculator.QUALITY,
            loadType = LoadType.EXTERNAL,
            reps = 15,
        )
        assertEquals(RestPrescription.HEAVY_SECONDS, heavy)
        assertEquals(RestPrescription.SHORT_SECONDS, highRep)
        assertTrue(heavy > highRep)
    }

    @Test
    fun aGrindAddsRestAndAnEasySetTakesSomeAway() {
        val grind = RestPrescription.seconds(
            reasonCode = SetMicroRecCalculator.TOP_SET,
            loadType = LoadType.EXTERNAL,
            reps = 5,
        )
        val easy = RestPrescription.seconds(
            reasonCode = SetMicroRecCalculator.IN_TANK,
            loadType = LoadType.EXTERNAL,
            reps = 5,
        )
        assertEquals(RestPrescription.HEAVY_SECONDS + RestPrescription.GRIND_EXTRA, grind)
        assertEquals(RestPrescription.HEAVY_SECONDS - RestPrescription.GRIND_EXTRA, easy)
    }
}
