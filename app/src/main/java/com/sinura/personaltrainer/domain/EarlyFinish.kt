package com.sinura.personaltrainer.domain

/**
 * How many lifts were planned in the session versus how many received a working set.
 *
 * A routine-backed session lists every lift up front; a free session only counts lifts
 * that were added. Skipped lifts are those with no working set logged.
 */
data class LiftCompletion(
    val loggedLifts: Int,
    val plannedLifts: Int,
    val skippedLiftNames: List<String>,
) {
    val finishedEarly: Boolean get() = plannedLifts > 0 && loggedLifts in 1 until plannedLifts

    /** "4 of 5 lifts". Null when every planned lift was started or nothing was planned. */
    fun progressLabel(): String? {
        if (plannedLifts <= 0 || loggedLifts >= plannedLifts) return null
        return "$loggedLifts of $plannedLifts lifts"
    }
}

object EarlyFinish {
    fun liftCompletion(session: WorkoutSession): LiftCompletion {
        val planned = session.exercises
        if (planned.isEmpty()) {
            return LiftCompletion(loggedLifts = 0, plannedLifts = 0, skippedLiftNames = emptyList())
        }
        val loggedIds = session.sets
            .filterNot { it.isWarmup }
            .map { it.exerciseId }
            .toSet()
        val logged = planned.count { it.exercise.id in loggedIds }
        val skipped = planned
            .filter { it.exercise.id !in loggedIds }
            .map { it.exercise.name }
        return LiftCompletion(loggedLifts = logged, plannedLifts = planned.size, skippedLiftNames = skipped)
    }
}

object EarlyFinishCopy {
    const val FINISHED_EARLY = "Finished early"
    const val SKIPPED_LIFTS = "Not started"
}
