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
}
