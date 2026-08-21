package com.sinura.personaltrainer.domain

/** One lift in a repeated session: structure and targets, never the logged sets. */
data class RepeatItem(
    val exerciseId: String,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

/**
 * Turns a finished session into the plan for doing it again.
 *
 * Most gym days are "the same as last time, a bit heavier", and that was the one start path
 * the app did not have. What carries over is the shape of the workout — which lifts, in
 * which order, and how many sets you actually managed. What never carries over is the work
 * itself: no logged sets, no weights. Weight is left to the progression hint, which already
 * knows what to suggest and would only be contradicted by a stale copy.
 */
object RepeatSessionPlan {

    private const val FALLBACK_REPS = 5
    private const val FALLBACK_REST_SECONDS = 90

    fun from(source: WorkoutSession): List<RepeatItem> {
        val planned = source.exercises.sortedBy { it.sortOrder }
        val order: List<String> = if (planned.isNotEmpty()) {
            planned.map { it.exercise.id }
        } else {
            // A free workout has no planned exercises; the sets are the only record of what
            // was done, and of the order it was done in.
            source.sets.sortedBy { it.completedAt }.map { it.exerciseId }.distinct()
        }

        return order.map { exerciseId ->
            val plannedItem = planned.firstOrNull { it.exercise.id == exerciseId }
            val working = source.sets
                .filter { it.exerciseId == exerciseId && !it.isWarmup }
                .sortedBy { it.completedAt }

            RepeatItem(
                exerciseId = exerciseId,
                // What you actually did beats what you planned to do — but a lift that was
                // planned and never touched keeps its plan rather than collapsing to one set.
                targetSets = if (working.isNotEmpty()) {
                    working.size.coerceAtLeast(1)
                } else {
                    plannedItem?.targetSets?.coerceAtLeast(1) ?: 1
                },
                targetReps = working.lastOrNull()?.reps
                    ?: plannedItem?.targetReps
                    ?: FALLBACK_REPS,
                restSeconds = plannedItem?.restSeconds ?: FALLBACK_REST_SECONDS,
            )
        }
    }
}
