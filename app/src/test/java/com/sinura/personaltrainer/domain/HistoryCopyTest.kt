package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCopyTest {
    @Test
    fun horizonCaptionDoesNotClaimAViewSwitch() {
        val caption = HistoryCopy.HORIZON_CAPTION
        assertTrue(caption.contains("Totals"))
        assertFalse(caption.contains("filter", ignoreCase = true))
        assertTrue(caption.contains("all history"))
        assertTrue(HistoryCopy.CALENDAR_HEAT.contains("sets that month"))
        assertFalse(HistoryCopy.CALENDAR_HEAT.contains("muscle", ignoreCase = true))
        assertTrue(BodyHeatCopy.LEGEND_CAPTION.contains("muscle load"))
        assertTrue(BodyHeatCopy.LEGEND_CAPTION.contains("not calendar"))
    }
}
