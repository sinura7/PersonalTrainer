package com.sinura.personaltrainer.domain

/** Starting targets for a lift being added to a routine or a live session. */
data class TargetDefaults(
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)

/**
 * What a lift's targets should start at, given what kind of lift it is.
 *
 * Every add path in the app used the same literal 3 × 5 with 90 seconds' rest. That is a
 * defensible number for a barbell squat and a wrong one for everything else: 3 × 5 on a cable
 * lateral raise is not a set scheme anybody runs, and 90 seconds between heavy deadlifts is not
 * enough rest. The user could always fix it, which is exactly the problem — the app was making
 * them correct a guess it had no reason to make badly.
 *
 * Two axes decide it. [LoadType] says how the resistance behaves: a selectorized stack moves in
 * fixed pin jumps and rewards higher reps, bodyweight has no jumps at all. Compound-ness says
 * how much of you is working — and it is derived from the junction credits rather than stored,
 * because a lift that meaningfully loads a second muscle already says so in its credits.
 *
 * These are opinions, not physiology. They are here so the app has one opinion instead of one
 * number pretending not to be an opinion.
 */
object AddDefaults {
    /** A lift is compound when at least one secondary muscle takes half a set's credit. */
    fun isCompound(exercise: Exercise): Boolean =
        exercise.muscles.drop(1).any { it.weight >= COMPOUND_SECONDARY_WEIGHT }

    fun forExercise(exercise: Exercise): TargetDefaults =
        forExercise(exercise.loadType, isCompound(exercise))

    fun forExercise(loadType: LoadType?, isCompound: Boolean): TargetDefaults = when (loadType) {
        LoadType.EXTERNAL ->
            if (isCompound) TargetDefaults(3, 5, 150) else TargetDefaults(3, 10, 90)
        LoadType.STACK ->
            if (isCompound) TargetDefaults(3, 10, 90) else TargetDefaults(3, 12, 60)
        // Added load is the point of these, so they sit in the low-rep range whether or not a
        // second muscle is credited: a weighted pull-up is trained like a loaded lift.
        LoadType.BODYWEIGHT_PLUS -> TargetDefaults(3, 6, 120)
        LoadType.BODYWEIGHT ->
            if (isCompound) TargetDefaults(3, 8, 90) else TargetDefaults(3, 12, 60)
        LoadType.ASSISTED -> TargetDefaults(3, 8, 90)
        // A custom the user typed in, or a row from a backup this build does not understand.
        // The isolation row is the safer wrong answer: too many reps at too little rest is a
        // bad set, while too few reps at too much rest is a wasted afternoon.
        null -> FALLBACK
    }

    private const val COMPOUND_SECONDARY_WEIGHT = 0.5
    private val FALLBACK = TargetDefaults(3, 10, 90)
}
