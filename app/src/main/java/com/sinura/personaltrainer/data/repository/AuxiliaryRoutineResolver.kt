package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.AppRoomDatabase
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.domain.AuxiliarySessions
import com.sinura.personaltrainer.domain.WorkoutSession

/** Resolves which saved routines are Extra pack shapes (F1 volume and PR exclusion). */
internal class AuxiliaryRoutineResolver(private val database: AppRoomDatabase) {
    private var cachedIds: Set<String>? = null
    private var cachedRevision: Int = -1

    suspend fun auxiliaryRoutineIds(): Set<String> {
        val routines = database.routineDao().getAllRoutines()
        val revision = routines.sumOf { it.updatedAt.toInt() } xor routines.size
        if (cachedIds != null && cachedRevision == revision) return cachedIds!!
        val ids = routines.mapNotNull { entity ->
            val routine = database.routineDao().getById(entity.id)?.toDomain() ?: return@mapNotNull null
            if (AuxiliarySessions.isAuxiliaryRoutine(routine)) entity.id else null
        }.toSet()
        cachedIds = ids
        cachedRevision = revision
        return ids
    }

    suspend fun isAuxiliarySession(session: WorkoutSession): Boolean {
        val routine = session.routineId?.let { database.routineDao().getById(it)?.toDomain() }
        return AuxiliarySessions.isAuxiliarySession(session, routine)
    }

    suspend fun isAuxiliaryRoutineId(routineId: String?): Boolean {
        if (routineId.isNullOrBlank()) return false
        return routineId in auxiliaryRoutineIds()
    }
}
