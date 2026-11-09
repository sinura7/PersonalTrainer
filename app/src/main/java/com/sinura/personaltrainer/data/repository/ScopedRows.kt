package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.GymScope
import com.sinura.personaltrainer.domain.HistoryScope

/** Rows that carry session gym and equipment for Kotlin-side history scoping. */
interface GymScopedRow {
    val sessionGymId: String?
    val equipment: String?
}

object ScopedRows {
    fun keep(rows: List<GymScopedRow>, scope: HistoryScope): List<GymScopedRow> =
        when (scope) {
            HistoryScope.Shared -> rows
            is HistoryScope.AtGym -> rows.filter { row ->
                val equipment = row.equipment?.let(EquipmentType::fromStorage)
                equipment != null && GymScope.isGymSpecific(equipment) && row.sessionGymId == scope.gymId
            }
        }

    fun <T : GymScopedRow> keepTyped(rows: List<T>, scope: HistoryScope): List<T> =
        @Suppress("UNCHECKED_CAST")
        keep(rows as List<GymScopedRow>, scope) as List<T>
}
