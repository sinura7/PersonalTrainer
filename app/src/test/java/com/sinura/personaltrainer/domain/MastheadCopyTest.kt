package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every state Home's headline can be in. One assert per row of the settled table, because the
 * headline is the single most-read string in the app and a wrong one is wrong all day.
 */
class MastheadCopyTest {

    @Test
    fun trainingTodayNamesTheFocusAndTheLiftCount() {
        assertEquals(
            "PUSH DAY · 4 LIFTS",
            MastheadCopy.headline(day(SessionFocusKind.PUSH), loggedToday = false, liftCount = 4),
        )
    }

    @Test
    fun oneLiftIsSingular() {
        assertEquals(
            "LEG DAY · 1 LIFT",
            MastheadCopy.headline(day(SessionFocusKind.LEGS), loggedToday = false, liftCount = 1),
        )
    }

    @Test
    fun anUnresolvableRoutineDropsTheCountRatherThanGuessingIt() {
        assertEquals(
            "PULL DAY",
            MastheadCopy.headline(day(SessionFocusKind.PULL), loggedToday = false, liftCount = null),
        )
        assertEquals(
            "PULL DAY",
            MastheadCopy.headline(day(SessionFocusKind.PULL), loggedToday = false, liftCount = 0),
        )
    }

    @Test
    fun everyFocusHasItsOwnNoun() {
        val nouns = SessionFocusKind.entries.associateWith { kind ->
            MastheadCopy.headline(day(kind), loggedToday = false, liftCount = null)
        }
        assertEquals("UPPER DAY", nouns.getValue(SessionFocusKind.UPPER))
        assertEquals("LOWER BODY DAY", nouns.getValue(SessionFocusKind.LOWER))
        assertEquals("PUSH DAY", nouns.getValue(SessionFocusKind.PUSH))
        assertEquals("PULL DAY", nouns.getValue(SessionFocusKind.PULL))
        assertEquals("LEG DAY", nouns.getValue(SessionFocusKind.LEGS))
        assertEquals("FULL BODY DAY", nouns.getValue(SessionFocusKind.FULL_BODY))
        assertEquals("RECOVERY DAY", nouns.getValue(SessionFocusKind.RECOVERY))
    }

    @Test
    fun aRestDaySaysSo() {
        assertEquals(
            "REST DAY",
            MastheadCopy.headline(
                day(SessionFocusKind.RECOVERY).copy(isRest = true),
                loggedToday = false,
                liftCount = null,
            ),
        )
    }

    @Test
    fun anEmptyWeekIsNotAFailureState() {
        assertEquals("READY TO TRAIN", MastheadCopy.headline(null, loggedToday = false, liftCount = null))
    }

    @Test
    fun havingTrainedOutranksTheePlan() {
        // Once the session is done, "PUSH DAY" is a statement about something already behind you.
        assertEquals(
            "TRAINED TODAY",
            MastheadCopy.headline(day(SessionFocusKind.PUSH), loggedToday = true, liftCount = 4),
        )
        assertEquals("TRAINED TODAY", MastheadCopy.headline(null, loggedToday = true, liftCount = null))
    }

    private fun day(kind: SessionFocusKind): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = DayOfWeek.MONDAY,
        isRest = false,
        focusKind = kind,
        focusTitle = kind.label,
        routineId = "r1",
        routineName = "Push",
        reason = "Pinned to your week.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )
}

/**
 * The one line under the next-session module.
 */
class NextSessionReasonTest {

    @Test
    fun aCoachReasonAboutThisSessionWins() {
        val day = pushDay()
        val reason = nextSessionReason(
            day,
            listOf(card(CanonicalMuscle.CHEST, "Chest is behind Back.")),
        )
        assertEquals("Chest is behind Back.", reason)
    }

    @Test
    fun aCoachReasonAboutSomethingElseIsNotBorrowed() {
        // A hamstring card next to a push session reads as the app pattern-matching.
        val reason = nextSessionReason(
            pushDay(),
            listOf(card(CanonicalMuscle.HAMSTRINGS, "Hamstrings are behind Quadriceps.")),
        )
        assertEquals("Pinned to your week.", reason)
    }

    @Test
    fun restDaysAndEmptyWeeksHaveNoReason() {
        assertNull(nextSessionReason(null, emptyList()))
        assertNull(nextSessionReason(pushDay().copy(isRest = true), emptyList()))
    }

    private fun pushDay(): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = DayOfWeek.MONDAY,
        isRest = false,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = "r1",
        routineName = "Push",
        reason = "Pinned to your week.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun card(muscle: CanonicalMuscle, reason: String) = TrainingRecommendation(
        id = "imbalance-test",
        kicker = RecommendationEngine.KICKER_BALANCE,
        title = "test",
        reason = reason,
        priority = RecommendationPriority.HIGH,
        action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
        actionMuscle = muscle,
        rankScore = 50,
    )
}
