package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.HistoryScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ScopedRowsTest {

    private data class ScopedRow(
        override val sessionGymId: String?,
        override val equipment: String?,
        val id: String,
    ) : GymScopedRow

    @Test
    fun sharedKeepsEveryRow() {
        val rows = listOf(
            ScopedRow("gym-af-weston", EquipmentType.MACHINE.name, "a"),
            ScopedRow(null, EquipmentType.BARBELL.name, "b"),
        )
        assertEquals(2, ScopedRows.keep(rows, HistoryScope.Shared).size)
    }

    @Test
    fun atGymKeepsOnlyMatchingMachineSets() {
        val weston = "gym-af-weston"
        val scope = HistoryScope.AtGym(weston)
        val rows = listOf(
            ScopedRow(weston, EquipmentType.MACHINE.name, "weston-machine"),
            ScopedRow("gym-af-major-mackenzie", EquipmentType.MACHINE.name, "other-gym"),
            ScopedRow(weston, EquipmentType.BARBELL.name, "free-weight"),
            ScopedRow(null, EquipmentType.MACHINE.name, "unassigned-machine"),
        )
        val kept = ScopedRows.keepTyped(rows, scope)
        assertEquals(listOf("weston-machine"), kept.map { it.id })
    }
}
