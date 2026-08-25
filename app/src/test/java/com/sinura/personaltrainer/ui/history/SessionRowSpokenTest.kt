package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.sessionRowSpoken
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRowSpokenTest {
    @Test
    fun historyRowSpeaksIdentityBeforeMetrics() {
        val spoken = sessionRowSpoken(
            title = "Upper strength",
            dateLabel = "Mon 2 Jan",
            workingSets = 16,
            work = SetWork(volumeKg = 8_000.0, bodyweightReps = 0),
            durationMinutes = 48,
            unit = WeightUnit.KG,
        )
        assertEquals("Upper strength, Mon 2 Jan, 16 sets, 8000 kg, 48 min", spoken)
    }
}
