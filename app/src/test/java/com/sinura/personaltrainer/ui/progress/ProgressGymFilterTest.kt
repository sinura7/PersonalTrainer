package com.sinura.personaltrainer.ui.progress

import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.data.repository.GymFilters
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressGymFilterTest {
    @Test
    fun oneGymKeepsFreeWeightAndScopedMachineSets() {
        val machineWeston = RecordSet(
            exerciseId = "pec",
            exerciseName = "Pec",
            loadClass = LoadClass.LOADED,
            equipment = EquipmentType.MACHINE,
            sessionGymId = "gym-af-weston",
            set = ExerciseSetRecord("s1", "ses", 50.0, 10, 1L),
        )
        val machineOther = machineWeston.copy(sessionGymId = "gym-af-majormac", set = machineWeston.set.copy(setId = "s2"))
        val bench = machineWeston.copy(
            equipment = EquipmentType.BARBELL,
            sessionGymId = "gym-af-majormac",
            set = machineWeston.set.copy(setId = "s3"),
        )
        val filtered = GymFilters.recordSets(
            listOf(machineWeston, machineOther, bench),
            GymFilter.One("gym-af-weston"),
        )
        assertEquals(2, filtered.size)
    }
}
