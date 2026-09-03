package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingCalendarTouchTest {
    @Test
    fun calendarCellMeetsTouchMin() {
        assertEquals(Metrics.touchMin, TrainingCalendarMetrics.cellMin)
        assertEquals(48, TrainingCalendarMetrics.cellMin.value.toInt())
    }
}
