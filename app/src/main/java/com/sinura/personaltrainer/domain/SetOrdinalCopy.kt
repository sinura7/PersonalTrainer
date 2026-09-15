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

    /**
     * The line above the wells for the set about to be logged.
     *
     * [Kicker] uppercases this, so the entry surface reads SET 3 OF 4 /
     * WU 2 / EXTRA 1.
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
