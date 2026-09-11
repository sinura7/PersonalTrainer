package com.sinura.personaltrainer.domain

/**
 * When the live log should stop being Log and start being Next, and which
 * lift Next opens.
 *
 * Extra sets are a UI ask ([wantAnother]), not a database flag. While that
 * ask is live the lift is not complete, so Log stays the Volt.
 */
object WorkoutAdvance {
    /**
     * Prescribed working sets are in and the lifter has not asked for another.
     * A free lift ([targetSets] ≤ 0) is never auto-complete: Log stays.
     */
    fun liftComplete(
        workingLogged: Int,
        targetSets: Int,
        wantAnother: Boolean,
    ): Boolean {
        if (wantAnother) return false
        if (targetSets <= 0) return false
        return workingLogged >= targetSets
    }

    /**
     * The next lift in session order, or null on the last lift / unknown id.
     *
     * [exerciseIds] must already be session order. This does not skip lifts
     * that still have remaining sets — Next is "this lift is done", not a hunt
     * for unfinished work.
     */
    fun nextExerciseId(exerciseIds: List<String>, currentId: String?): String? {
        if (currentId == null) return null
        val index = exerciseIds.indexOf(currentId)
        if (index < 0 || index >= exerciseIds.lastIndex) return null
        return exerciseIds[index + 1]
    }

    /**
     * Everything the log dock needs to decide Log-or-Next, from the session itself.
     *
     * The screen used to work this out in its own composition body: count the selected lift's
     * working sets, call [liftComplete], map every lift to its id, call [nextExerciseId], then
     * `&&` the two against whether a set was being edited. Four rule decisions in a function
     * whose job is to draw, re-run on every recomposition — and the header above it redraws once
     * a second as the elapsed clock ticks.
     */
    fun forSelection(
        session: WorkoutSession?,
        selectedExerciseId: String?,
        wantAnother: Boolean,
        editing: Boolean,
    ): WorkoutAdvanceState {
        val selected = session?.exercises?.firstOrNull { it.exercise.id == selectedExerciseId }
        val workingLogged = selected
            ?.let { lift -> session.setsFor(lift.exercise.id).count { !it.isWarmup } }
            ?: 0
        val complete = liftComplete(
            workingLogged = workingLogged,
            targetSets = selected?.targetSets ?: 0,
            wantAnother = wantAnother,
        )
        val next = nextExerciseId(
            session?.exercises.orEmpty().map { it.exercise.id },
            selectedExerciseId,
        )
        return WorkoutAdvanceState(
            workingLogged = workingLogged,
            liftComplete = complete,
            nextExerciseId = next,
            showNext = complete && next != null && !editing,
        )
    }

    /** Whether a card's Add set row shows: its prescribed sets are in, nothing asked for more. */
    fun cardOffersAnotherSet(loggedSets: List<SetLog>, targetSets: Int): Boolean = liftComplete(
        workingLogged = loggedSets.count { !it.isWarmup },
        targetSets = targetSets,
        wantAnother = false,
    )

    /** The most recently completed set, which the card rules and marks as the latest. */
    fun latestSetId(loggedSets: List<SetLog>): String? =
        loggedSets.maxByOrNull { it.completedAt }?.id
}

/** The Log-or-Next decision for the selected lift. See [WorkoutAdvance.forSelection]. */
data class WorkoutAdvanceState(
    val workingLogged: Int,
    val liftComplete: Boolean,
    val nextExerciseId: String?,
    val showNext: Boolean,
)
