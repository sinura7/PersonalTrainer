package com.sinura.personaltrainer.domain

/**
 * Extra warm-up and stretch packs mint a normal strength session. Until F2 tags
 * block roles in the schema, those sessions are recognized by the pack routine
 * [RoutineRepository.ensureAuxiliaryRoutine] prepared, not by a colliding title alone.
 */
object AuxiliarySessions {
    fun isAuxiliaryRoutine(routine: Routine): Boolean {
        val ids = routine.exercises.sortedBy { it.sortOrder }.map { it.exercise.id }
        return AuxiliaryRoutineMatch.matchesEnsureAuxiliaryShape(routine.name, ids)
    }

    fun isAuxiliarySession(session: WorkoutSession, routine: Routine?): Boolean {
        if (routine != null) return isAuxiliaryRoutine(routine)
        val ids = session.exercises.sortedBy { it.sortOrder }.map { it.exercise.id }
        val name = session.routineName?.trim().orEmpty()
        if (session.routineId != null) {
            if (ids.isNotEmpty() && name.isNotEmpty()) {
                return AuxiliaryRoutineMatch.matchesEnsureAuxiliaryShape(name, ids)
            }
            if (ids.isEmpty() && name.isNotEmpty()) {
                return AuxiliaryRoutineMatch.orphanSessionNameMatchesPackTitle(name)
            }
            return false
        }
        return AuxiliaryRoutineMatch.orphanSessionNameMatchesPackTitle(session.routineName)
    }

    /** Strength blocks inside a past or mixed activity are never auxiliary packs. */
    fun countsAsWorkingVolume(session: WorkoutSession, routine: Routine? = null): Boolean =
        !isAuxiliarySession(session, routine)

    @Deprecated(
        message = "Use routine id plus lift shape; name-only matching collides with user routines.",
        replaceWith = ReplaceWith("isAuxiliarySession(session, routine)"),
    )
    fun isAuxiliaryRoutineName(name: String?): Boolean =
        AuxiliaryRoutineMatch.orphanSessionNameMatchesPackTitle(name)
}
