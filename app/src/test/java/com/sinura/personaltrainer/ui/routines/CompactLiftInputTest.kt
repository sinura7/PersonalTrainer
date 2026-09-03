package com.sinura.personaltrainer.ui.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactLiftInputTest {
    @Test
    fun decimalDigitsKeepsOnePoint() {
        assertEquals("", decimalDigits(""))
        assertEquals("80", decimalDigits("80"))
        assertEquals("80.5", decimalDigits("80.5"))
        assertEquals("80.55", decimalDigits("80.5.5"))
        assertEquals(".5", decimalDigits(".5"))
        assertEquals("12.5", decimalDigits("12.5kg"))
        assertEquals("102,5", decimalDigits("102,5"))
        assertEquals("102,5", decimalDigits("102,5kg"))
    }
}
