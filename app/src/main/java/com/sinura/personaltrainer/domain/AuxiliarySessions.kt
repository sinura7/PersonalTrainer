package com.sinura.personaltrainer.domain

/**
 * Extra warm-up and stretch packs mint a normal strength session. Until F2 tags
 * block roles in the schema, those sessions are recognized by the routine name
 * [RoutineRepository.ensureAuxiliaryRoutine] gives the pack ([AuxiliaryPack.title]).
 */
object AuxiliarySessions {
    private val titles: Set<String> = AuxiliaryPacks.all
        .map { it.title.trim().lowercase() }
        .toSet()

    fun isAuxiliaryRoutineName(name: String?): Boolean {
        val trimmed = name?.trim()?.lowercase().orEmpty()
        return trimmed.isNotEmpty() && trimmed in titles
    }

    fun isAuxiliarySession(session: WorkoutSession): Boolean = isAuxiliaryRoutineName(session.routineName)

    /** Strength blocks inside a past or mixed activity are never auxiliary packs. */
    fun countsAsWorkingVolume(session: WorkoutSession): Boolean = !isAuxiliarySession(session)
}
