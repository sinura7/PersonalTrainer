package com.sinura.personaltrainer.domain

/**
 * When the live log should stop being Log and start being Next / Finish,
 * and which lift Next opens.
 *
 * Extra sets are a UI ask ([wantAnother]), not a database flag. While that
 * ask is live the lift is not complete, so Log stays the Volt.
 *
 * Next is always the next *unfinished* lift in session order, wrapping to
 * a lift skipped earlier. Post-log and resume share [nextUnfinishedExerciseId]
 * so they cannot disagree.
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
     * Prefer [nextUnfinishedExerciseId] on the gym floor. This sequential
     * helper remains for callers that already hold a bare id list.
     */
    fun nextExerciseId(exerciseIds: List<String>, currentId: String?): String? {
        if (currentId == null) return null
        val index = exerciseIds.indexOf(currentId)
        if (index < 0 || index >= exerciseIds.lastIndex) return null
        return exerciseIds[index + 1]
    }

    /**
     * The lift Next should open: the next one in session order that has not
     * met its own target, wrapping to earlier skipped lifts.
     *
     * Null means there is nowhere useful to go — every other lift is finished,
     * or this is the only one — and the dock becomes Finish workout.
     */
    fun nextUnfinishedExerciseId(session: WorkoutSession?, currentId: String?): String? {
        if (session == null || currentId == null) return null
        val ids = session.exercises.map { it.exercise.id }
        val from = ids.indexOf(currentId)
        if (from < 0) return null
        val order = (1 until ids.size).map { step -> ids[(from + step) % ids.size] }
        return order.firstOrNull { !session.isTargetMet(it) }
    }

    /**
     * Everything the log dock needs to decide Log, Next, or Finish.
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
        val next = nextUnfinishedExerciseId(session, selectedExerciseId)
        val nextLift = next?.let { id -> session?.exercises?.firstOrNull { it.exercise.id == id } }
        return WorkoutAdvanceState(
            workingLogged = workingLogged,
            liftComplete = complete,
            nextExerciseId = next,
            nextName = nextLift?.exercise?.name,
            nextLift = nextLift,
            showNext = complete && next != null && !editing,
            showFinish = complete && next == null && !editing,
            showAnother = complete && !editing,
        )
    }

    /** Whether a card's Add set row shows: its prescribed sets are in, nothing asked for more. */
    fun cardOffersAnotherSet(loggedSets: List<SetLog>, targetSets: Int): Boolean = liftComplete(
        workingLogged = loggedSets.count { !it.isWarmup },
        targetSets = targetSets,
        wantAnother = false,
    )

    /**
     * The most recently completed set, which the card rules and marks as the latest. Two sets
     * stamped in one millisecond go to the later set number, as the Last set cell reads them
     * ([ExerciseFloorStatsCalculator]); the first of a tie would mark set 1 latest beside set 2.
     */
    fun latestSetId(loggedSets: List<SetLog>): String? =
        loggedSets.maxWithOrNull(compareBy<SetLog> { it.completedAt }.thenBy { it.setNumber })?.id

    fun plannedWork(targetSets: Int, targetReps: Int): String =
        if (targetSets > 0) "$targetSets × $targetReps" else ""
}

/** The Log / Next / Finish decision for the selected lift. See [WorkoutAdvance.forSelection]. */
data class WorkoutAdvanceState(
    val workingLogged: Int,
    val liftComplete: Boolean,
    val nextExerciseId: String?,
    val nextName: String? = null,
    val nextLift: SessionExercise? = null,
    val showNext: Boolean,
    val showFinish: Boolean = false,
    val showAnother: Boolean = false,
)
