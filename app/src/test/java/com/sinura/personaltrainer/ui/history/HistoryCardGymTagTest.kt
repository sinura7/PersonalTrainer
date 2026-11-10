package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.HistoryCardCopy
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.toHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCardGymTagTest {
    @Test
    fun metaPrefixForStrengthSessions() {
        val gym = Gym("id", "Anytime Weston", "Weston", "WS", 0, false)
        assertEquals("Weston", HistoryCardCopy.metaPrefix(gym, workingSets = 5, showGym = true))
        assertNull(HistoryCardCopy.metaPrefix(gym, workingSets = 0, showGym = false))
    }

    @Test
    fun summaryCarriesGymIntoHistoryEntry() {
        val summary = SessionSummary(
            id = "s",
            routineId = null,
            routineName = "Push",
            date = 1L,
            finishedAt = 2L,
            durationMinutes = 30,
            workingSets = 3,
            volumeKg = 100.0,
            localEpochDay = 0L,
            gymId = "gym-af-weston",
        )
        assertEquals("gym-af-weston", summary.toHistoryEntry().gymId)
        assertEquals(HistoryKind.WORKOUT, summary.toHistoryEntry().kind)
    }
}
