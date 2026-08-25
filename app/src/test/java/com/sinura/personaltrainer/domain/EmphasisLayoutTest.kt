package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the emphasis rearrangement contract that [EmphasisLayout] promises: turning "I care
 * about upper / lower" into a majority-of-that-family week WITHOUT dropping the split's shape
 * and WITHOUT ever stealing the last day of the other family — "Legs stay in the week" /
 * "Upper stays in the week". Audit finding N13 flagged that this logic had no direct JVM test;
 * it was only ever exercised transitively through [WeeklySchedulePlanner], so a regression in
 * the promotion loop or the push/pull balancing could slip through the plumbing untouched.
 *
 * Expected orderings below are derived from the code's own rules (majority = size/2 + 1,
 * demote from the last "other" day, keep at least one "other" day) rather than from incidental
 * output, so these fail loudly if the emphasis behaviour changes.
 */
class EmphasisLayoutTest {

    @Test
    fun balancedEmphasisReturnsTheWeekUntouched() {
        val week = listOf(
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
        )
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.BALANCED))
    }

    @Test
    fun fullBodyOnlyWeekIsLeftAloneForBothEmphases() {
        val week = List(4) { SessionFocusKind.FULL_BODY }
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.UPPER))
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.LOWER))
    }

    @Test
    fun recoveryOnlyWeekHasNoFamilyToFavourSoIsUnchanged() {
        val week = List(3) { SessionFocusKind.RECOVERY }
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.UPPER))
    }

    @Test
    fun upperEmphasisMakesFourDayUpperLowerMajorityUpperKeepingOneLowerDay() {
        val week = listOf(
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
        )
        assertEquals(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.UPPER,
                SessionFocusKind.UPPER,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.UPPER),
        )
    }

    @Test
    fun lowerEmphasisMakesFourDayUpperLowerMajorityLowerKeepingOneUpperDay() {
        val week = listOf(
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
        )
        assertEquals(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.LOWER,
                SessionFocusKind.LOWER,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.LOWER),
        )
    }

    @Test
    fun upperEmphasisOnSixDayUpperLowerConvertsOnlyEnoughToReachMajority() {
        val week = listOf(
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
        )
        assertEquals(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.UPPER,
                SessionFocusKind.UPPER,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.UPPER),
        )
    }

    @Test
    fun lowerEmphasisPromotesToLowerWhenTheWeekHasNoLegsDay() {
        val week = List(4) { SessionFocusKind.UPPER }
        assertEquals(
            listOf(
                SessionFocusKind.UPPER,
                SessionFocusKind.LOWER,
                SessionFocusKind.LOWER,
                SessionFocusKind.LOWER,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.LOWER),
        )
    }

    @Test
    fun pushPullLegsIsAlreadyUpperMajoritySoUpperEmphasisChangesNothing() {
        val week = listOf(
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
            SessionFocusKind.LEGS,
        )
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.UPPER))
    }

    @Test
    fun lowerEmphasisOnSixDayPushPullLegsPromotesTheExtraDaysToLegs() {
        val week = listOf(
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
            SessionFocusKind.LEGS,
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
            SessionFocusKind.LEGS,
        )
        assertEquals(
            listOf(
                SessionFocusKind.PUSH,
                SessionFocusKind.PULL,
                SessionFocusKind.LEGS,
                SessionFocusKind.LEGS,
                SessionFocusKind.LEGS,
                SessionFocusKind.LEGS,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.LOWER),
        )
    }

    @Test
    fun lowerEmphasisOnFiveDayPushPullLegsPromotesLegsToMajority() {
        val week = listOf(
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
            SessionFocusKind.LEGS,
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
        )
        assertEquals(
            listOf(
                SessionFocusKind.PUSH,
                SessionFocusKind.PULL,
                SessionFocusKind.LEGS,
                SessionFocusKind.LEGS,
                SessionFocusKind.LEGS,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.LOWER),
        )
    }

    @Test
    fun upperEmphasisBalancesPushAndPullWhenPromotingIntoAPplStyleWeek() {
        // A PUSH already present makes the week use the PPL vocabulary, so promotions become
        // PUSH/PULL (kept balanced) rather than the generic UPPER day.
        val week = listOf(
            SessionFocusKind.PUSH,
            SessionFocusKind.LEGS,
            SessionFocusKind.LEGS,
            SessionFocusKind.LEGS,
        )
        assertEquals(
            listOf(
                SessionFocusKind.PUSH,
                SessionFocusKind.LEGS,
                SessionFocusKind.PUSH,
                SessionFocusKind.PULL,
            ),
            EmphasisLayout.apply(week, TrainingEmphasis.UPPER),
        )
    }

    @Test
    fun singleDayWeekIsNeverStrippedOfItsOnlyFamily() {
        val upperDay = listOf(SessionFocusKind.UPPER)
        assertEquals(upperDay, EmphasisLayout.apply(upperDay, TrainingEmphasis.LOWER))

        val lowerDay = listOf(SessionFocusKind.LOWER)
        assertEquals(lowerDay, EmphasisLayout.apply(lowerDay, TrainingEmphasis.UPPER))
    }

    @Test
    fun twoDayWeekKeepsBothFamiliesBecauseMajorityWouldEmptyTheOther() {
        val week = listOf(SessionFocusKind.UPPER, SessionFocusKind.LOWER)
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.UPPER))
        assertEquals(week, EmphasisLayout.apply(week, TrainingEmphasis.LOWER))
    }

    @Test
    fun emphasisNeverRemovesTheLastDayOfTheOppositeFamily() {
        val weeks = listOf(
            listOf(SessionFocusKind.UPPER, SessionFocusKind.LOWER, SessionFocusKind.UPPER, SessionFocusKind.LOWER),
            listOf(SessionFocusKind.LEGS, SessionFocusKind.LEGS, SessionFocusKind.LEGS),
            listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, SessionFocusKind.LEGS, SessionFocusKind.LEGS),
        )
        for (week in weeks) {
            val upper = EmphasisLayout.apply(week, TrainingEmphasis.UPPER)
            val lower = EmphasisLayout.apply(week, TrainingEmphasis.LOWER)
            if (week.any { it.isLowerFamily }) {
                assertTrue(
                    "upper emphasis on $week kept no lower day",
                    upper.any { it.isLowerFamily },
                )
            }
            if (week.any { it.isUpperFamily }) {
                assertTrue(
                    "lower emphasis on $week kept no upper day",
                    lower.any { it.isUpperFamily },
                )
            }
        }
    }

    @Test
    fun applyIsDeterministicAcrossRepeatedCalls() {
        val week = listOf(
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
            SessionFocusKind.LEGS,
            SessionFocusKind.PUSH,
            SessionFocusKind.PULL,
        )
        val first = EmphasisLayout.apply(week, TrainingEmphasis.LOWER)
        val second = EmphasisLayout.apply(week, TrainingEmphasis.LOWER)
        assertEquals(first, second)
    }

    @Test
    fun applyDoesNotMutateTheCallersWeek() {
        val week = mutableListOf(
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
            SessionFocusKind.UPPER,
            SessionFocusKind.LOWER,
        )
        val snapshot = week.toList()
        EmphasisLayout.apply(week, TrainingEmphasis.UPPER)
        assertEquals("apply must not rearrange the list it was handed", snapshot, week)
    }
}
