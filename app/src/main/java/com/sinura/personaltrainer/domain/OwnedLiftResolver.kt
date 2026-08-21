package com.sinura.personaltrainer.domain

/**
 * Turns "train your hamstrings" into "open Romanian Deadlift".
 *
 * Advice that names a muscle is advice you have to translate before you can act on it, and the
 * translation is exactly the part the app is better at than you are: it knows which lifts you
 * already own, which ones you have actually been doing, and which ones the gym you go to has
 * equipment for. A card that says "find hamstring lifts" hands all of that back.
 *
 * The ordering encodes what "owns" means, strongest claim first:
 *
 * 1. **In a routine you maintain** — you have already decided this lift belongs in your
 *    training, and the most recently edited routine is the one you are thinking about.
 * 2. **Trained in the last 60 days** — not planned, but done, and recently enough to still be
 *    a lift you do rather than a lift you did.
 * 3. **Nothing** — and then the card falls back to naming the muscle, honestly, rather than
 *    suggesting something arbitrary out of a catalog of a hundred.
 *
 * Equipment filtering runs across all of it: suggesting a barbell row to someone who has told
 * the app they have no barbell is worse than suggesting nothing.
 */
object OwnedLiftResolver {
    const val OWNED_RECENT_DAYS = 60L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun resolve(
        muscle: CanonicalMuscle,
        routines: List<Routine>,
        history: List<WorkoutSession>,
        exerciseCatalog: Map<String, Exercise>,
        preferences: CoachPreferences,
        nowMs: Long,
    ): Exercise? {
        fun eligible(exerciseId: String): Exercise? {
            val exercise = exerciseCatalog[exerciseId] ?: return null
            if (primaryMuscleOf(exercise) != muscle) return null
            if (!preferences.allows(exercise.equipment)) return null
            return exercise
        }

        routines
            .sortedByDescending { it.updatedAt }
            .forEach { routine ->
                routine.exercises
                    .sortedBy { it.sortOrder }
                    .forEach { item -> eligible(item.exercise.id)?.let { return it } }
            }

        val cutoff = nowMs - OWNED_RECENT_DAYS * DAY_MS
        history
            .filter { it.isFinished }
            .flatMap { session -> session.sets.map { session to it } }
            .filter { (session, set) -> MuscleLoadCalculator.trainedAtMs(session, set) >= cutoff }
            .sortedByDescending { (session, set) -> MuscleLoadCalculator.trainedAtMs(session, set) }
            .forEach { (_, set) -> eligible(set.exerciseId)?.let { return it } }

        return null
    }

    /**
     * A lift's primary muscle, from its junction credits.
     *
     * Falls back to the free-text muscle group only when the junction is missing — a custom
     * exercise created before its credits were derived, or a lift restored from a v1 backup
     * mid-reconciliation.
     */
    fun primaryMuscleOf(exercise: Exercise): CanonicalMuscle? {
        val primaryKey = exercise.muscles.maxByOrNull { it.weight }?.muscleKey
        if (primaryKey != null) return MuscleNormalizer.resolveKey(primaryKey)
        return MuscleNormalizer.primaryOf(exercise.muscleGroup).takeIf { it != CanonicalMuscle.OTHER }
    }
}
