package com.sinura.personaltrainer.domain

/**
 * The twelve weeks you are in the middle of.
 *
 * A horizon and a review point, and deliberately nothing more. The block does **not** prescribe
 * loads: it never says "week four, take 20% off", because the app already decides progression
 * from what was actually logged — a lift is offered more weight when the sets say it is ready,
 * and a deload is raised when volume climbs with nothing to show for it. A calendar that also
 * had an opinion would be a second voice contradicting the first in the weeks they disagreed,
 * and the one reading real numbers is the one worth keeping.
 *
 * What a block buys instead is the thing a stored week cannot give you on its own: somewhere to
 * be. "Week 3 of 12" says the plan is going somewhere and how far along it is, and week twelve
 * is a natural moment to look at what moved and decide what the next twelve are for. Without it
 * the week simply repeats forever, which is true and reads as the app having nothing to say.
 *
 * Anchored to a date rather than counted up as sessions are logged, so a fortnight off does not
 * pause the block. Twelve weeks is twelve weeks whether or not you trained through them, and a
 * counter that quietly waits for you is a counter that cannot tell you that you stopped.
 */
data class TrainingBlock(
    /** The first day of the block's first week, already normalised to the lifter's week start. */
    val startEpochDay: Long,
    val weeks: Int = DEFAULT_WEEKS,
) {
    /**
     * Which week of the block a date falls in, 1-based.
     *
     * Zero for a date before the block began — which is a real case, not a guard: history
     * predating the block is still history, and it belongs to no week of it.
     */
    fun weekIndexOn(epochDay: Long): Int {
        if (epochDay < startEpochDay) return 0
        return ((epochDay - startEpochDay) / DAYS_IN_WEEK).toInt() + 1
    }

    /** The day after the block's last, so a block that ends is not also a block you are in. */
    val endExclusiveEpochDay: Long get() = startEpochDay + weeks.toLong() * DAYS_IN_WEEK

    fun isCompleteOn(epochDay: Long): Boolean = epochDay >= endExclusiveEpochDay

    /**
     * How far through, 0..1, for a progress readout.
     *
     * Whole weeks rather than days: the app plans in weeks and a bar that crept forward every
     * morning would imply a precision the plan does not have.
     */
    fun progressOn(epochDay: Long): Float {
        if (weeks <= 0) return 1f
        val week = weekIndexOn(epochDay)
        return (week.toFloat() / weeks).coerceIn(0f, 1f)
    }

    /**
     * The week to show, clamped into the block.
     *
     * A finished block keeps reading as its final week rather than counting into a thirteenth,
     * because "Week 13 of 12" is the same arithmetic failure as "Set 6 of 5".
     */
    fun displayWeekOn(epochDay: Long): Int = weekIndexOn(epochDay).coerceIn(1, weeks.coerceAtLeast(1))

    companion object {
        const val DEFAULT_WEEKS = 12
        /** Roughly three years of blocks. Past this the list is an archive nobody scrolls. */
        const val MAX_KEPT = 12
        const val MIN_WEEKS = 4
        const val MAX_WEEKS = 24
        private const val DAYS_IN_WEEK = 7L

        /**
         * A block starting in the week that contains [today].
         *
         * Normalised back to the lifter's own week start, so week one is a whole week rather
         * than the four days left of the one they happened to finish setup in — and so the
         * block's weeks line up with every other week in the app: the planner's, the heat
         * map's, the tonnage buckets'.
         */
        fun startingIn(
            today: CivilDate,
            weekStart: Weekday,
            weeks: Int = DEFAULT_WEEKS,
        ): TrainingBlock = TrainingBlock(
            startEpochDay = today.previousOrSame(weekStart).epochDay,
            weeks = weeks.coerceIn(MIN_WEEKS, MAX_WEEKS),
        )
    }
}

/**
 * The blocks you have finished, as boundaries rather than as summaries.
 *
 * A finished block is two numbers — when it started and how long it ran — and every session it
 * contained is still in the database, so its review can be rebuilt from those two numbers on
 * demand. Storing the computed summary instead would be a second source of truth about work the
 * database already holds, and it would go stale the moment an old session was edited.
 *
 * Encoded by hand into one preference string for the same reason [CatalogCollision] is: the
 * shape is a pair of numbers, and a serializer dependency for that is one more thing that can
 * be configured wrong. Tolerant on the way in — a malformed entry is dropped, never thrown,
 * because a corrupted archive must not be able to stop the app knowing what week it is on.
 */
object BlockArchive {
    private const val ENTRY = ","
    private const val FIELD = ":"

    fun encode(blocks: List<TrainingBlock>): String = blocks
        .sortedBy { it.startEpochDay }
        .takeLast(TrainingBlock.MAX_KEPT)
        .joinToString(ENTRY) { "${it.startEpochDay}$FIELD${it.weeks}" }

    fun decode(raw: String?): List<TrainingBlock> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ENTRY).mapNotNull { entry ->
            val parts = entry.split(FIELD)
            if (parts.size != 2) return@mapNotNull null
            val start = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val weeks = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (weeks < TrainingBlock.MIN_WEEKS || weeks > TrainingBlock.MAX_WEEKS) {
                return@mapNotNull null
            }
            TrainingBlock(startEpochDay = start, weeks = weeks)
        }
            .distinctBy { it.startEpochDay }
            .sortedBy { it.startEpochDay }
    }

    /**
     * Add [finished] to [existing], newest kept, oldest dropped past the cap.
     *
     * Deduplicated by start day so archiving the same block twice — a double tap, a restore
     * landing on top of a local archive — cannot list it twice.
     */
    fun archive(existing: List<TrainingBlock>, finished: TrainingBlock): List<TrainingBlock> =
        (existing + finished)
            .distinctBy { it.startEpochDay }
            .sortedBy { it.startEpochDay }
            .takeLast(TrainingBlock.MAX_KEPT)
}
