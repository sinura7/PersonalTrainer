package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
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

    @Test
    fun aStillPlannedOccurrenceOutranksHavingAlreadyTrained() {
        val evening = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "s",
                ruleId = "r-s",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
                hour = 18,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "r-s",
                weekday = Weekday.MONDAY,
                hour = 18,
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                focusKind = SessionFocusKind.PUSH,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        assertEquals(
            "PUSH DAY · 4 LIFTS",
            MastheadCopy.headline(
                day(SessionFocusKind.PUSH),
                loggedToday = true,
                liftCount = 4,
                agenda = listOf(evening),
            ),
        )
    }

    @Test
    fun plannedAgendaOutranksAShiftedSlotRestDay() {
        val cardio = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "c",
                ruleId = "r-c",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, 20_000L),
                hour = 7,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "r-c",
                weekday = Weekday.MONDAY,
                hour = 7,
                minute = 0,
                modality = ScheduleModality.CARDIO,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        assertEquals(
            "CARDIO DAY",
            MastheadCopy.headline(
                day(SessionFocusKind.RECOVERY).copy(isRest = true),
                loggedToday = false,
                liftCount = null,
                agenda = listOf(cardio),
            ),
        )
    }

    private fun day(kind: SessionFocusKind): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = Weekday.MONDAY,
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
        dayOfWeek = Weekday.MONDAY,
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

/**
 * The two fixes that make a brand-new install truthful: what the masthead says before there is
 * a plan, and whether a bodyweight lift can be logged at all.
 */
class ColdStartCopyTest {
    private fun restDay(): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = Weekday.MONDAY,
        isRest = true,
        focusKind = SessionFocusKind.RECOVERY,
        focusTitle = "Rest",
        routineId = null,
        routineName = null,
        reason = "No session pinned.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.LOW,
    )

    @Test
    fun aFreshInstallIsReadyToTrainNotResting() {
        // The derivation always returns seven days and fills every unpinned one with a rest
        // day, so a fresh install DOES have a non-null day and it IS a rest day. Without the
        // hasPlan signal the first screen a new user sees reads REST DAY.
        val emptyWeekDay = restDay()
        assertEquals(
            "READY TO TRAIN",
            MastheadCopy.headline(emptyWeekDay, loggedToday = false, liftCount = null, hasPlan = false),
        )
    }

    @Test
    fun aPlannedRestDayStillSaysRestDay() {
        assertEquals(
            "REST DAY",
            MastheadCopy.headline(restDay(), loggedToday = false, liftCount = null, hasPlan = true),
        )
    }

    @Test
    fun havingTrainedOutranksHavingNoPlan() {
        assertEquals(
            "TRAINED TODAY",
            MastheadCopy.headline(null, loggedToday = true, liftCount = null, hasPlan = false),
        )
    }
}

class BodyweightLoggingTest {
    @Test
    fun aPushUpCanBeLoggedAtZero() {
        assertNull(SetLogRules.validate(0.0, 12, isWarmup = false, loadType = LoadType.BODYWEIGHT))
    }

    @Test
    fun anUnweightedPullUpCanBeLoggedAtZero() {
        // BODYWEIGHT_PLUS *can* take added load; it does not have to.
        assertNull(SetLogRules.validate(0.0, 8, isWarmup = false, loadType = LoadType.BODYWEIGHT_PLUS))
        assertNull(SetLogRules.validate(0.0, 8, isWarmup = false, loadType = LoadType.ASSISTED))
    }

    @Test
    fun aBarbellSetAtZeroIsStillRefused() {
        // The rule still earns its keep where it was right: 0 kg on a loaded lift is a typo.
        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            SetLogRules.validate(0.0, 5, isWarmup = false, loadType = LoadType.EXTERNAL),
        )
        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            SetLogRules.validate(0.0, 5, isWarmup = false, loadType = LoadType.STACK),
        )
    }

    @Test
    fun anUnknownLoadTypeTakesTheStricterReading() {
        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            SetLogRules.validate(0.0, 5, isWarmup = false, loadType = null),
        )
    }

    @Test
    fun everyBodyweightLiftInTheCatalogCanBeLogged() {
        // 18 of the 98 are loaded by bodyweight. Not one of them may be unrecordable.
        val blocked = DefaultExercises.catalog()
            .filter { SetLogRules.requiresWeight(it.loadType) }
            .filter { it.equipment == EquipmentType.BODYWEIGHT }
            .map { it.id }
        assertEquals(emptyList<String>(), blocked)
    }

    @Test
    fun aBodyweightSetIsNotPricedInKilogramsAtAll() {
        // What this replaced: an assertion that ten bodyweight reps were worth 400 kg, or the
        // lifter's own weight times ten once they had told the app what they weighed. Neither
        // number was a measurement. Reps are.
        assertEquals(0.0, MuscleLoadCalculator.setVolumeKg(0.0, 10, LoadClass.BODYWEIGHT), 0.001)
        assertEquals(
            SetWork(volumeKg = 0.0, bodyweightReps = 10),
            SetWork.of(0.0, 10, LoadClass.BODYWEIGHT),
        )
        // A loaded set is exactly what was on the bar, as it always was.
        assertEquals(500.0, MuscleLoadCalculator.setVolumeKg(100.0, 5, LoadClass.LOADED), 0.001)
    }
}

/**
 * Which session Home is talking about.
 *
 * One rule, because Home used to have two cards deriving it separately and naming the same
 * routine twice on one screen.
 */
class FeaturedSessionTest {
    @Test
    fun todayWinsWhenTodayIsATrainingDay() {
        val today = day(isRest = false, name = "Push")
        val next = day(isRest = false, name = "Pull")
        assertEquals("Push", featuredSession(today = today, next = next)?.routineName)
    }

    @Test
    fun aRestDayLooksAhead() {
        // The card's headline already does this — it says "Next · Wed" and names that session.
        // The lifts listed under it have to come from the same day, or the card describes one
        // session and lists another's exercises.
        val today = day(isRest = true, name = null)
        val next = day(isRest = false, name = "Pull")
        assertEquals("Pull", featuredSession(today = today, next = next)?.routineName)
    }

    @Test
    fun nothingPlannedIsNull() {
        assertNull(featuredSession(today = null, next = null))
        assertNull(featuredSession(today = day(isRest = true, name = null), next = null))
    }

    @Test
    fun aFirstRunWithNoWeekStillFindsTheNextSession() {
        assertEquals("Pull", featuredSession(today = null, next = day(isRest = false, name = "Pull"))?.routineName)
    }

    private fun day(isRest: Boolean, name: String?): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = 20_000L,
        dayOfWeek = Weekday.MONDAY,
        isRest = isRest,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = name?.let { "r-$it" },
        routineName = name,
        reason = "Pinned to your week.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )
}
