package com.sinura.personaltrainer.domain

/**
 * What a recommendation tap means (P9.5 / FND-036).
 *
 * The card emits this. Navigation mapping is a when on the intent, not
 * a UI helper that knows every destination callback.
 */
sealed class RecommendationIntent {
    data class OpenLibrary(val muscle: CanonicalMuscle?) : RecommendationIntent()
    data class OpenExercise(val exerciseId: String) : RecommendationIntent()
    data object StartWorkout : RecommendationIntent()
    data object OpenRoutines : RecommendationIntent()
    data object OpenBodyMap : RecommendationIntent()
    data object MarkLighterWeek : RecommendationIntent()
}

object RecommendationIntents {
    fun from(recommendation: TrainingRecommendation): RecommendationIntent? {
        if (!recommendation.hasDestination) return null
        return when (recommendation.action) {
            RecommendationAction.OPEN_LIBRARY_MUSCLE ->
                RecommendationIntent.OpenLibrary(recommendation.actionMuscle)
            RecommendationAction.OPEN_EXERCISE ->
                recommendation.actionExerciseId?.let { RecommendationIntent.OpenExercise(it) }
                    ?: RecommendationIntent.OpenBodyMap
            RecommendationAction.START_WORKOUT -> RecommendationIntent.StartWorkout
            RecommendationAction.OPEN_ROUTINES -> RecommendationIntent.OpenRoutines
            RecommendationAction.OPEN_BODY_MAP -> RecommendationIntent.OpenBodyMap
            RecommendationAction.MARK_LIGHTER_WEEK -> RecommendationIntent.MarkLighterWeek
            null -> RecommendationIntent.OpenBodyMap
        }
    }

    fun actionLabel(recommendation: TrainingRecommendation): String =
        when (from(recommendation)) {
            is RecommendationIntent.OpenLibrary -> {
                val muscle = recommendation.actionMuscle ?: CanonicalMuscle.OTHER
                "Find ${muscle.catalogLabel.lowercase()} lifts"
            }
            is RecommendationIntent.OpenExercise ->
                recommendation.actionExerciseName?.let { "Open $it" } ?: "Show on the map"
            RecommendationIntent.StartWorkout -> "Start a workout"
            RecommendationIntent.OpenRoutines -> "Open routines"
            RecommendationIntent.OpenBodyMap -> "Show on the map"
            RecommendationIntent.MarkLighterWeek -> "Mark this week lighter"
            null -> "Show on the map"
        }
}
