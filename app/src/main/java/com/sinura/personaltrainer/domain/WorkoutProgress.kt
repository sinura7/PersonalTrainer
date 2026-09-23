package com.sinura.personaltrainer.domain

/**
 * Where the session stands, for the header of the active workout.
 *
 * Two fractions, both derived from the saved rows and nothing else: which lift the
 * lifter is on out of how many, and how many working sets are in out of how many the
 * plan asked for. Extra sets past a target grow the planned count rather than
 * overflowing it, so the line never reads "17 of 16" and never hides work that was
 * done. Warm-ups are not sets here, for the same reason they do not count toward a
 * lift's target ([WorkoutSession.isTargetMet]).
 *
 * One segment per lift feeds the header's progress bar: done, current, or upcoming,
 * with the current lift's own fill so a half-finished lift shows as half a segment.
 */
enum class ProgressSegmentState {
    DONE,
    CURRENT,
    UPCOMING,
}

data class ProgressSegment(
    val exerciseId: String,
    val state: ProgressSegmentState,
    /** Working sets in over the target, 0..1. A free lift is 1 once it has a set. */
    val fraction: Float,
)

data class WorkoutProgress(
    /** 1-based position of the selected lift in session order; 0 when nothing is selected. */
    val exerciseNumber: Int,
    val exerciseCount: Int,
    /** Lifts whose prescribed sets are all in. Free lifts never count as done. */
    val exercisesDone: Int,
    /** Working sets saved across the whole session. */
    val setsDone: Int,
    /** Prescribed working sets, grown by any extras already logged. 0 for a free workout. */
    val setsPlanned: Int,
    val segments: List<ProgressSegment>,
) {
    val hasExercises: Boolean get() = exerciseCount > 0

    companion object {
        val EMPTY = WorkoutProgress(
            exerciseNumber = 0,
            exerciseCount = 0,
            exercisesDone = 0,
            setsDone = 0,
            setsPlanned = 0,
            segments = emptyList(),
        )
    }
}

object WorkoutProgressCalculator {
    fun of(session: WorkoutSession?, selectedExerciseId: String?): WorkoutProgress {
        if (session == null || session.exercises.isEmpty()) return WorkoutProgress.EMPTY
        val selected = session.resolveSelectedExerciseId(selectedExerciseId)
        var setsDone = 0
        var setsPlanned = 0
        var exercisesDone = 0
        val segments = session.exercises.map { lift ->
            val id = lift.exercise.id
            val logged = session.workingSetsFor(id)
            val target = lift.targetSets.coerceAtLeast(0)
            setsDone += logged
            // Only a prescribed lift has a plan; a free lift's sets count as done, not planned.
            if (target > 0) setsPlanned += maxOf(target, logged)
            // A free lift (no target) has nothing to complete, so it never counts as done;
            // its segment still fills once it has been worked.
            val met = target > 0 && logged >= target
            val worked = target == 0 && logged > 0
            if (met) exercisesDone += 1
            val fraction = when {
                target > 0 -> (logged.toFloat() / target).coerceIn(0f, 1f)
                logged > 0 -> 1f
                else -> 0f
            }
            ProgressSegment(
                exerciseId = id,
                state = when {
                    id == selected -> ProgressSegmentState.CURRENT
                    met -> ProgressSegmentState.DONE
                    worked -> ProgressSegmentState.DONE
                    else -> ProgressSegmentState.UPCOMING
                },
                fraction = fraction,
            )
        }
        val number = session.exercises.indexOfFirst { it.exercise.id == selected } + 1
        return WorkoutProgress(
            exerciseNumber = number.coerceAtLeast(0),
            exerciseCount = session.exercises.size,
            exercisesDone = exercisesDone,
            setsDone = setsDone,
            setsPlanned = setsPlanned,
            segments = segments,
        )
    }

    /**
     * The header's second line: `4 of 7 exercises · 9 of 16 sets`.
     *
     * A free workout has no prescribed count, so it reads `9 sets logged` instead of
     * `9 of 0`. Nothing selected drops the lift half. Empty is empty.
     */
    fun headline(progress: WorkoutProgress): String {
        val parts = buildList {
            if (progress.exerciseNumber > 0 && progress.exerciseCount > 0) {
                // The same voice as the branch below, which already counted this way: the
                // noun last, so `4 of 7 exercises · 2 of 3 sets` reads as two counts of the
                // same shape rather than a heading followed by one. Singular when there is
                // one lift, as that branch has always done — `1 of 1 exercises` is not a
                // sentence this app would say.
                val noun = if (progress.exerciseCount == 1) "exercise" else "exercises"
                add("${progress.exerciseNumber} of ${progress.exerciseCount} $noun")
            } else if (progress.exerciseCount > 0) {
                add(if (progress.exerciseCount == 1) "1 exercise" else "${progress.exerciseCount} exercises")
            }
            add(setsLine(progress))
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun setsLine(progress: WorkoutProgress): String = when {
        progress.setsPlanned > 0 -> "${progress.setsDone} of ${progress.setsPlanned} sets"
        progress.setsDone == 1 -> "1 set logged"
        progress.setsDone > 0 -> "${progress.setsDone} sets logged"
        progress.hasExercises -> "No sets yet"
        else -> ""
    }

    /** TalkBack for the whole header block, including the lifts already finished. */
    fun spoken(progress: WorkoutProgress): String {
        val line = headline(progress)
        if (line.isBlank()) return ""
        val done = when (progress.exercisesDone) {
            0 -> null
            1 -> "1 exercise complete"
            else -> "${progress.exercisesDone} exercises complete"
        }
        return listOfNotNull(line, done).joinToString(". ")
    }
}
