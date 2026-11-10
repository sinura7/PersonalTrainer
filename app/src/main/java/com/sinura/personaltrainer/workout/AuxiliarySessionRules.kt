package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.domain.AuxiliarySessions
import com.sinura.personaltrainer.domain.WorkoutSession

object AuxiliarySessionRules {
    suspend fun isAuxiliarySession(deps: AppDependencies, session: WorkoutSession): Boolean {
        val routine = session.routineId?.let { deps.routineRepository.getById(it) }
        return AuxiliarySessions.isAuxiliarySession(session, routine)
    }
}
