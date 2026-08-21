package com.sinura.personaltrainer.domain

/**
 * The words the active workout screen says about where you are in a lift.
 *
 * In the domain rather than in the composable for the same reason as [MastheadCopy]: this is a
 * rule about counting, it is worth testing, and nothing in `ui/` is compiled or exercised
 * outside a full Gradle build.
 */
object WorkoutCopy {
    /**
     * The line under the set dots: which set you are on, and what you were aiming for.
     *
     * The overshoot case is the reason this exists. The caption used to be built as
     * `"Set ${logged + 1} of $target"` with nothing stopping the first number passing the
     * second, so a sixth set on a five-set lift announced **"Set 6 of 5"** — the app doing
     * arithmetic in front of someone who had just done more work than they planned and getting
     * it visibly wrong. Past the target the count stands on its own and the prescription moves
     * into the past tense, which is what actually happened.
     *
     * @param workingLogged working sets already logged for this lift; warm-ups do not count.
     * @param targetWeightLabel already formatted in the user's unit, or null for no target.
     */
    fun setProgress(
        workingLogged: Int,
        targetSets: Int,
        targetReps: Int,
        targetWeightLabel: String? = null,
    ): String {
        val logged = workingLogged.coerceAtLeast(0)
        val sets = targetSets.coerceAtLeast(1)
        val reps = targetReps.coerceAtLeast(1)
        val past = logged >= sets
        return buildString {
            append("Set ")
            append(logged + 1)
            if (!past) {
                append(" of ")
                append(sets)
            }
            append(if (past) " · target was " else " · target ")
            append(sets)
            append(" × ")
            append(reps)
            if (targetWeightLabel != null) {
                append(" @ ")
                append(targetWeightLabel)
            }
        }
    }
}
