package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.GymFilter
import com.sinura.personaltrainer.domain.GymScope
import com.sinura.personaltrainer.domain.RecordSet

/** Repository-side gym filter for Body records and weekly volume (packet G3). */
object GymFilters {
    fun exerciseEntries(
        entries: List<ExerciseSetEntry>,
        equipment: EquipmentType?,
        filter: GymFilter,
    ): List<ExerciseSetEntry> {
        if (equipment == null || !GymScope.isGymSpecific(equipment)) return entries
        return when (filter) {
            GymFilter.All -> entries
            GymFilter.Unassigned -> entries.filter { it.sessionGymId == null }
            is GymFilter.One -> entries.filter { it.sessionGymId == filter.gymId }
        }
    }

    fun recordSets(sets: List<RecordSet>, filter: GymFilter): List<RecordSet> = when (filter) {
        GymFilter.All -> sets
        GymFilter.Unassigned -> sets.filter { row ->
            val equipment = row.equipment
            equipment == null || !GymScope.isGymSpecific(equipment) || row.sessionGymId == null
        }
        is GymFilter.One -> sets.filter { row ->
            val equipment = row.equipment
            equipment == null || !GymScope.isGymSpecific(equipment) || row.sessionGymId == filter.gymId
        }
    }

    fun workingSetCounts(rows: List<WorkingSetCountRow>, filter: GymFilter): List<WorkingSetCountRow> =
        when (filter) {
            GymFilter.All -> rows
            GymFilter.Unassigned -> rows.filter { it.sessionGymId == null }
            is GymFilter.One -> rows.filter { it.sessionGymId == filter.gymId }
        }
}
