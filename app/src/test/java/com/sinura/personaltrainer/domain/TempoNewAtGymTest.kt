package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TempoNewAtGymTest {
    @Test
    fun newAtGymReasonCodeIsStable() {
        assertEquals("NEW_AT_GYM", SetMicroRecCalculator.NEW_AT_GYM)
    }
}
