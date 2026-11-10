package com.sinura.personaltrainer.ui.exercise

import com.sinura.personaltrainer.data.repository.GymFilters
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.domain.GymScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailGymFilterTest {
    @Test
    fun machineLiftsAreGymScoped() {
        assertTrue(GymScope.isGymSpecific(EquipmentType.MACHINE))
    }

    @Test
    fun filterKeepsOnlyMatchingGymSessions() {
        val entry = ExerciseSetEntry(
            record = ExerciseSetRecord("s1", "sess", 100.0, 5, 1L),
            sessionName = "Push",
            sessionPerformedAtMs = 1L,
            sessionGymId = "gym-af-weston",
        )
        val other = entry.copy(
            record = entry.record.copy(setId = "s2"),
            sessionGymId = "gym-af-majormac",
        )
        val filtered = GymFilters.exerciseEntries(
            listOf(entry, other),
            EquipmentType.MACHINE,
            GymFilter.One("gym-af-weston"),
        )
        assertEquals(1, filtered.size)
        assertEquals("gym-af-weston", filtered.single().sessionGymId)
    }
}
