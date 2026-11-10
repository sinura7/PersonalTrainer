package com.sinura.personaltrainer.domain

/**
 * Same shape test [com.sinura.personaltrainer.data.repository.RoutineRepository.ensureAuxiliaryRoutine]
 * uses: pack title plus the ordered catalog lift ids written for that pack.
 */
object AuxiliaryRoutineMatch {
    fun packExerciseIds(pack: AuxiliaryPack): List<String> = pack.lifts.map { it.exerciseId }

    fun matchesEnsureAuxiliaryShape(name: String, exerciseIdsInOrder: List<String>): Boolean =
        AuxiliaryPacks.all.any { pack ->
            pack.title.equals(name.trim(), ignoreCase = true) &&
                packExerciseIds(pack) == exerciseIdsInOrder
        }

    /** Only when the routine row is gone: session snapshot name alone. */
    fun orphanSessionNameMatchesPackTitle(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        return trimmed.isNotEmpty() &&
            AuxiliaryPacks.all.any { pack -> pack.title.equals(trimmed, ignoreCase = true) }
    }
}
