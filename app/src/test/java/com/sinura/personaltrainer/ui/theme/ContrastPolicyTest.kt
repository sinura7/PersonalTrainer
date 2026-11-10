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
        assertTrue(ContrastPolicy.isLoadBearing(TextTertiary))
    }

    @Test
    fun everyLoadBearingTextOnSurfaceClearsAa() {
        for (text in ContrastPolicy.loadBearingText) {
            for (surface in ContrastPolicy.readingSurfaces) {
                val ratio = ContrastPolicy.ratio(text, surface)
                assertTrue(
                    "text=$text surface=$surface ratio=$ratio",
                    ContrastPolicy.meetsAA(text, surface),
                )
            }
        }
    }

    @Test
    fun refreshContrastPairsMeetFloors() {
        assertTrue(ContrastPolicy.meetsAA(RestCyan, RestContainer))
        assertTrue(ContrastPolicy.meetsAA(TextSecondary, Surface3))
        assertTrue(ContrastPolicy.meetsAA(TextTertiary, Surface3))
        assertTrue(ContrastPolicy.meetsAA(TextTertiary, Surface2))
        assertTrue(ContrastPolicy.meetsAA(PrGold, GoldContainer))
        assertTrue(ContrastPolicy.meetsAA(Volt, VoltContainer))
        assertTrue(ContrastPolicy.meetsAA(Pit, Volt))
        assertTrue(ContrastPolicy.meetsAA(PitOnVoltSubtle, Volt))
        assertTrue(ContrastPolicy.meetsAA(Warn, Surface2))
        assertTrue(ContrastPolicy.meetsNonText(VoltEdge, Surface2))
    }

    @Test
    fun disabledIsNotLoadBearingAndMaySitBelowAa() {
        assertFalse(ContrastPolicy.isLoadBearing(TextDisabled))
        assertFalse(ContrastPolicy.meetsAA(TextDisabled, Surface2))
        assertTrue(ContrastPolicy.ratio(TextDisabled, Surface2) < ContrastPolicy.AA_NORMAL)
    }
}
