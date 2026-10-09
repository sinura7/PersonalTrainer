package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.sessionRowSpoken
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRowSpokenTest {
    @Test fun historyRowSpeaksIdentityBeforeMetrics() {
        assertEquals(
            "Upper strength, Mon 2 Jan, 16 sets, 8000 kg, 48 min",
            sessionRowSpoken("Upper strength", "Mon 2 Jan", 16,
                SetWork(volumeKg = 8_000.0, bodyweightReps = 0), 48, WeightUnit.KG),
        )
    }

    @Test fun selectedHistoryRowSpeaksTheSameFullDateAndHoursAsItsVisibleValues() {
        assertEquals(
            "Upper strength, 2 January 2026, 16 sets, 8000 kg, 1 h 20 min",
            sessionRowSpoken("Upper strength", "2 January 2026", 16,
                SetWork(volumeKg = 8_000.0, bodyweightReps = 0), 80, WeightUnit.KG,
                durationLabel = HistoryCopy.activeDuration(80)),
        )
    }
}
