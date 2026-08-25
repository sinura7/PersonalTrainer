package com.sinura.personaltrainer.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastPolicyTest {
    @Test
    fun loadBearingTokensMeetAaOnInstrumentSurfaces() {
        assertTrue(ContrastPolicy.meetsAA(TextPrimary, Pit))
        assertTrue(ContrastPolicy.meetsAA(TextPrimary, Surface2))
        assertTrue(ContrastPolicy.meetsAA(TextSecondary, Pit))
        assertTrue(ContrastPolicy.meetsAA(TextSecondary, Surface2))
        assertTrue(ContrastPolicy.meetsAA(Volt, Pit))
        assertTrue(ContrastPolicy.isLoadBearing(TextPrimary))
        assertTrue(ContrastPolicy.isLoadBearing(TextSecondary))
    }

    @Test
    fun tertiaryIsDecorativeAndFailsNormalAa() {
        assertFalse(ContrastPolicy.isLoadBearing(TextTertiary))
        assertFalse(ContrastPolicy.meetsAA(TextTertiary, Surface2))
        assertTrue(ContrastPolicy.ratio(TextTertiary, Surface2) < ContrastPolicy.AA_NORMAL)
    }
}
