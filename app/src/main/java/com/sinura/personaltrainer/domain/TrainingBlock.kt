package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
            today: LocalDate,
            weekStart: DayOfWeek,
            weeks: Int = DEFAULT_WEEKS,
        ): TrainingBlock = TrainingBlock(
            startEpochDay = today.with(TemporalAdjusters.previousOrSame(weekStart)).toEpochDay(),
            weeks = weeks.coerceIn(MIN_WEEKS, MAX_WEEKS),
        )
    }
}
