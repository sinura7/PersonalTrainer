package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliarySessionsTest {
    @Test
    fun recognizesPackTitle() {
        assertTrue(AuxiliarySessions.isAuxiliaryRoutineName(AuxiliaryPacks.LowerBody.title))
        assertFalse(AuxiliarySessions.isAuxiliaryRoutineName("Push day"))
    }
}
