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
     * @param liveReps in-set rec next-rep count; replaces prescribed reps so RPE intent
     *   can tell the lifter how many to do.
     * @param liveWeightLabel same for the load, already formatted.
     */
    fun setProgress(
        workingLogged: Int,
        targetSets: Int,
        targetReps: Int,
        targetWeightLabel: String? = null,
        liveReps: Int? = null,
        liveWeightLabel: String? = null,
    ): String {
        val logged = workingLogged.coerceAtLeast(0)
        val sets = targetSets.coerceAtLeast(1)
        val reps = (liveReps ?: targetReps).coerceAtLeast(1)
        val weightLabel = liveWeightLabel ?: targetWeightLabel
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
            if (weightLabel != null) {
                append(" @ ")
                append(weightLabel)
            }
        }
    }

    /**
     * The commit button when this lift is done and another one follows.
     *
     * It used to read **"Next"**, and "Next" alone does not say what the tap commits. The
     * button in that slot has said `Log 60 kg × 5` a moment earlier and will say
     * `Save 60 kg × 5` while an edit is open, so the one state that moves the lifter to a
     * different exercise was also the one state that named nothing. Standing at a rack with a
     * phone at arm's length, "Next" could as easily mean the next set.
     *
     * The name goes on a second line rather than into the same sentence: `PrimaryGymButton`
     * already allows two lines, so `Next\nBench Press` uses the room the button has instead of
     * pushing a long name into an ellipsis. [nextSpoken] is what a screen reader gets, and it
     * is never abbreviated — a name too long to draw is still a name that must be heard.
     *
     * A blank name falls back to the bare word, because a button reading `Next\n` with nothing
     * under it is worse than the ambiguity it was meant to fix.
     */
    fun nextLift(nextExerciseName: String): String {
        val name = nextExerciseName.trim()
        return if (name.isEmpty()) NEXT else "$NEXT\n$name"
    }

    /** The same action, said in full for a screen reader. Never truncated. */
    fun nextSpoken(nextExerciseName: String): String {
        val name = nextExerciseName.trim()
        return if (name.isEmpty()) NEXT else "$NEXT lift: $name"
    }

    /** The bare word, kept in one place so the label and the spoken form cannot drift apart. */
    const val NEXT = "Next"
}
