package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
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
        assertEquals("Today", HistoryCopy.windowTitle(AnalyticsHorizon.DAY))
        assertEquals("This month", HistoryCopy.windowTitle(AnalyticsHorizon.MONTH))
        assertEquals("All time", HistoryCopy.windowTitle(AnalyticsHorizon.ALL_TIME))
        assertEquals("Day", AnalyticsHorizon.DAY.label)
        assertEquals("All", AnalyticsHorizon.ALL_TIME.label)
        assertEquals("session", HistoryCopy.sessionsLabel(1))
        assertEquals("sessions", HistoryCopy.sessionsLabel(0))
        assertEquals(HistoryCopy.EMPTY_LOG, "Finished sessions land here.")
        assertTrue(BodyHeatCopy.LEGEND_CAPTION.contains("muscle load"))
        assertTrue(BodyHeatCopy.LEGEND_CAPTION.contains("not calendar"))
        assertEquals("Today", BodyHeatCopy.windowTitle(HeatWindow.DAY))
        assertEquals("This week", BodyHeatCopy.windowTitle(HeatWindow.CURRENT_WEEK))
        assertEquals("This month", BodyHeatCopy.windowTitle(HeatWindow.CURRENT_MONTH))
        assertEquals(BodyHeatCopy.EMPTY_LOG, "Finished sets light the figure.")
        assertFalse(BodyHeatCopy.WINDOW_CAPTION.contains("Start", ignoreCase = true))
    }
}
