package com.sinura.personaltrainer.domain

/**
 * Visible set identity on the gym floor.
 *
 * The repository still writes one contiguous [SetLog.setNumber] through
 * warm-ups, working sets, and extras. That storage order is the record.
 * What the lifter reads is derived: warm-ups are WU n, working sets are
 * Set n of the target, and anything past the target is Extra n — never
 * "Set 6 of 5".
 */
object SetOrdinalCopy {
    fun warmup(n: Int): String = "WU $n"

    fun working(n: Int, targetSets: Int): String =
        if (targetSets > 0) "Set $n of $targetSets" else "Set $n"

    fun extra(n: Int): String = "Extra $n"

    /** The mark inside a set-history chip's ring: `W` for a warm-up, else the working count. */
    const val WARMUP_MARK = "W"

    fun marks(warmupFlags: List<Boolean>): List<String> {
        var working = 0
        return warmupFlags.map { isWarmup ->
            if (isWarmup) WARMUP_MARK else { working += 1; working.toString() }
        }
    }

    fun draftMark(isWarmup: Boolean, workingLogged: Int): String =
        if (isWarmup) WARMUP_MARK else (workingLogged.coerceAtLeast(0) + 1).toString()

    /**
     * The set about to be logged, as the exercise identity states it:
     * `Set 3 of 4` / `WU 2` / `Extra 1`. Rendered in sentence case, and also
     * the label under the current chip in the set history.
     */
    fun draftLine(
        isWarmup: Boolean,
        warmupLogged: Int,
        workingLogged: Int,
        targetSets: Int,
    ): String {
        if (isWarmup) return warmup(warmupLogged.coerceAtLeast(0) + 1)
        val nextWorking = workingLogged.coerceAtLeast(0) + 1
        if (targetSets > 0 && workingLogged >= targetSets) {
            return extra(workingLogged - targetSets + 1)
        }
        return working(nextWorking, targetSets)
    }

    /**
     * One label per logged row, in list order. Delete and restore re-run
     * this over the remaining rows; the database numbers stay contiguous
     * and are not shown here.
     */
    fun loggedLines(
        warmupFlags: List<Boolean>,
        targetSets: Int,
    ): List<String> {
        var warmupCount = 0
        var workingCount = 0
        return warmupFlags.map { isWarmup ->
            if (isWarmup) {
                warmupCount += 1
                warmup(warmupCount)
            } else {
                workingCount += 1
                if (targetSets > 0 && workingCount > targetSets) {
                    extra(workingCount - targetSets)
                } else {
                    working(workingCount, targetSets)
                }
            }
        }
    }
}
