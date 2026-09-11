package com.sinura.personaltrainer.domain

/**
 * How a History list card pictures the session.
 *
 * I-01: the log used to be a title, a date, and three numbers. The stills
 * are the first [STILL_LIMIT] lifts in session order, the same count the
 * start sheet uses ([RoutineCardCopy.STILL_LIMIT]). Cardio-only sessions
 * have none; they stay a title and numbers. The weekly chart is not
 * planned. PRs already live in the Records section under the log.
 */
object HistoryCardCopy {
    const val STILL_LIMIT = 3

    fun stills(exercises: List<Exercise>, limit: Int = STILL_LIMIT): List<Exercise> =
        exercises.take(limit.coerceAtLeast(0))

    /**
     * Strength blocks in tap order, pictured as catalog lifts. Cardio
     * blocks are skipped: a run is not a still. Snapshot fields stand in
     * when the catalog row is gone (soft FK on activity blocks).
     */
    fun stillsFromBlocks(blocks: List<ActivityBlock>): List<Exercise> =
        stills(
            blocks.filterIsInstance<StrengthBlock>()
                .sortedBy { it.sortOrder }
                .map { block ->
                    Exercise(
                        id = block.exerciseId,
                        name = block.exerciseName,
                        muscleGroup = "",
                        notes = "",
                        isCustom = false,
                        equipment = block.equipment,
                        loadType = block.loadType,
                        muscles = block.muscles,
                    )
                },
        )
}
