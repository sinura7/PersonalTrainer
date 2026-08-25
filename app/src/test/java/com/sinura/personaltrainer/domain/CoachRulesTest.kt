package com.sinura.personaltrainer.domain

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RPE rule: stop telling someone to add weight to a lift that is already grinding.
 */
class RpeModifierTest {

    @Test
    fun twoHardSessionsInARowBecomeAHold() {
        val held = RpeModifier.apply(increaseHint(), listOf(9, 9))
        assertEquals(ProgressionAction.HOLD, held.action)
        assertTrue(held.rpeHold)
        assertEquals(100.0, held.suggestedWeightKg, 1e-9)
    }

    @Test
    fun justUnderTheThresholdChangesNothing() {
        // 8 and 9 averages 8.5. Hard, but not at the edge two sessions running.
        val hint = RpeModifier.apply(increaseHint(), listOf(9, 8))
        assertEquals(ProgressionAction.INCREASE, hint.action)
        assertFalse(hint.rpeHold)
    }

    @Test
    fun anUnrecordedRpeBlocksTheRuleRatherThanCountingAsEasy() {
        assertEquals(ProgressionAction.INCREASE, RpeModifier.apply(increaseHint(), listOf(10, null)).action)
        assertEquals(ProgressionAction.INCREASE, RpeModifier.apply(increaseHint(), listOf(10)).action)
        assertEquals(ProgressionAction.INCREASE, RpeModifier.apply(increaseHint(), emptyList()).action)
    }

    @Test
    fun itNeverTouchesAHoldOrADecrease() {
        val hold = increaseHint().copy(action = ProgressionAction.HOLD)
        val decrease = increaseHint().copy(action = ProgressionAction.DECREASE)
        assertEquals(hold, RpeModifier.apply(hold, listOf(10, 10)))
        assertEquals(decrease, RpeModifier.apply(decrease, listOf(10, 10)))
    }

    private fun increaseHint() = ProgressionHint(
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        lastWeightKg = 100.0,
        lastReps = 5,
        targetReps = 5,
        suggestedWeightKg = 102.5,
        action = ProgressionAction.INCREASE,
    )
}

/**
 * The deload signal: volume climbing while nothing on the bar moves.
 *
 * Both halves are required, and the tests exist because either one alone is completely normal
 * training — telling someone to back off because their volume went up would be wrong most weeks.
 */
class DeloadSignalTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun risingVolumeWithFlatStrengthFires() {
        val finding = DeloadSignal.detect(history(risingVolume = true, improving = false), now, zone)
        assertNotNull(finding)
        assertTrue("the rise must be reportable", finding!!.setRisePercent > 0)
        assertTrue(finding.topLifts.isNotEmpty())
    }

    @Test
    fun risingVolumeWithARealPrDoesNotFire() {
        // The volume is buying something. That is training working, not overreaching.
        assertNull(DeloadSignal.detect(history(risingVolume = true, improving = true), now, zone))
    }

    @Test
    fun flatVolumeDoesNotFire() {
        assertNull(DeloadSignal.detect(history(risingVolume = false, improving = false), now, zone))
    }

    @Test
    fun anEmptyBaselineDoesNotFire() {
        // Coming back from nothing always "rises". It is not overreaching.
        val comeback = (0 until 4).map { index ->
            squatSession("s$index", now - index * DAY - HOUR, sets = 4, weightKg = 100.0)
        }
        assertNull(DeloadSignal.detect(comeback, now, zone))
    }

    // ---------------------------------------------------------------------------------------
    // Calendar-week attribution (N6). The three-week window is anchored to the calendar week
    // that contains now, in the caller's zone — not to three rolling 168-hour slices ending at
    // the instant now. 2024-01-01 is a Monday, so these weeks line up cleanly on the default
    // Monday week-start: week 1 = Jan 1-7, week 2 = Jan 8-14, week 3 = Jan 15-21.
    // ---------------------------------------------------------------------------------------

    @Test
    fun threeConsecutiveCalendarWeeksRisingFires() {
        val now = ms(LocalDate.of(2024, 1, 17), 12) // Wednesday of week 3
        val history =
            setsOn(LocalDate.of(2024, 1, 16), count = 9, tag = "w3") + // week 3
                setsOn(LocalDate.of(2024, 1, 10), count = 6, tag = "w2") + // week 2
                setsOn(LocalDate.of(2024, 1, 3), count = 4, tag = "w1") // week 1
        val finding = DeloadSignal.detect(history, now, ZoneOffset.UTC)
        assertNotNull(finding)
        // 9 vs 4 sets is a 125% rise from oldest to newest.
        assertEquals(125, finding!!.setRisePercent)
    }

    @Test
    fun aLateWeekSessionStaysInItsCalendarWeekNotTheRollingWindow() {
        // The exact shape the old rolling window got wrong. now is Wednesday of week 3; the
        // three Sunday-night sets sit in the last hours of week 2. A calendar bucket keeps them
        // in week 2, so weekly sets read 5 / 9 / 4 — this week DIPPED, nothing to flag. The old
        // rolling 168h window counted anything within ~62h of now as "this week", pulling those
        // Sunday sets forward to read 8 / 6 / 4 and wrongly firing the deload card.
        val now = ms(LocalDate.of(2024, 1, 17), 12)
        val history =
            setsOn(LocalDate.of(2024, 1, 16), count = 5, tag = "w3") +
                setsOn(LocalDate.of(2024, 1, 14), hour = 22, count = 3, tag = "sun") + // week 2, late
                setsOn(LocalDate.of(2024, 1, 10), count = 6, tag = "w2") +
                setsOn(LocalDate.of(2024, 1, 3), count = 4, tag = "w1")
        assertNull(DeloadSignal.detect(history, now, ZoneOffset.UTC))
    }

    @Test
    fun aGapWeekBreaksTheStreak() {
        // Volume in week 3 and week 1 but nothing in week 2. Three CONSECUTIVE weeks is the
        // rule; an empty middle week is not a rising streak.
        val now = ms(LocalDate.of(2024, 1, 17), 12)
        val history =
            setsOn(LocalDate.of(2024, 1, 16), count = 9, tag = "w3") +
                setsOn(LocalDate.of(2024, 1, 3), count = 4, tag = "w1")
        assertNull(DeloadSignal.detect(history, now, ZoneOffset.UTC))
    }

    @Test
    fun weekAttributionRespectsTheZone() {
        // One fixed set of instants, read in two zones. The three "boundary" sets happen at
        // 23:30 UTC on the last Sunday of week 2. In UTC they belong to week 2, so the weekly
        // totals dip this week (5 / 9 / 4) and nothing fires. Shift the reader an hour east and
        // the same instants are 00:30 Monday — week 3 — so the totals rise (8 / 6 / 4) and the
        // card fires. The bucketing follows the zone, exactly as the rest of the domain does.
        val now = ms(LocalDate.of(2024, 1, 17), 12)
        val history =
            setsOn(LocalDate.of(2024, 1, 16), count = 5, tag = "w3") +
                setsOn(LocalDate.of(2024, 1, 14), hour = 23, minute = 30, count = 3, tag = "sun") +
                setsOn(LocalDate.of(2024, 1, 10), count = 6, tag = "w2") +
                setsOn(LocalDate.of(2024, 1, 3), count = 4, tag = "w1")
        assertNull(DeloadSignal.detect(history, now, ZoneOffset.UTC))
        assertNotNull(DeloadSignal.detect(history, now, ZoneOffset.ofHours(1)))
    }

    @Test
    fun detectionIsDeterministic() {
        val now = ms(LocalDate.of(2024, 1, 17), 12)
        val history =
            setsOn(LocalDate.of(2024, 1, 16), count = 9, tag = "w3") +
                setsOn(LocalDate.of(2024, 1, 10), count = 6, tag = "w2") +
                setsOn(LocalDate.of(2024, 1, 3), count = 4, tag = "w1")
        assertEquals(
            DeloadSignal.detect(history, now, ZoneOffset.UTC),
            DeloadSignal.detect(history, now, ZoneOffset.UTC),
        )
    }

    private fun ms(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** [count] one-set squat sessions on [date], each an hour apart, all 100 kg for 5 reps. */
    private fun setsOn(
        date: LocalDate,
        count: Int,
        hour: Int = 10,
        minute: Int = 0,
        tag: String,
    ): List<WorkoutSession> = (0 until count).map { index ->
        squatSession(
            id = "$tag-$index",
            at = ms(date, hour, minute) + index * 60_000L,
            sets = 1,
            weightKg = 100.0,
        )
    }

    /**
     * Three weeks of squats. [risingVolume] adds sets week over week; [improving] makes the
     * most recent fortnight's top set heavier than the fortnight before it.
     */
    private fun history(risingVolume: Boolean, improving: Boolean): List<WorkoutSession> {
        val perWeek = if (risingVolume) listOf(9, 6, 4) else listOf(6, 6, 6)
        return perWeek.flatMapIndexed { weekIndex: Int, sets: Int ->
            (0 until sets).map { setIndex ->
                val at = now - weekIndex * 7 * DAY - setIndex * HOUR - HOUR
                val heavier = improving && weekIndex == 0
                squatSession(
                    id = "w$weekIndex-$setIndex",
                    at = at,
                    sets = 1,
                    weightKg = if (heavier) 140.0 else 100.0,
                )
            }
        }
    }

    private fun squatSession(id: String, at: Long, sets: Int, weightKg: Double): WorkoutSession =
        session(
            id = id,
            finishedAt = at,
            sets = (0 until sets).map { index ->
                set("$id-$index", id, "ex-squat", "Squat", weightKg, 5, at = at)
            },
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
            date = at,
        )

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}

/**
 * Turning a muscle into a lift the user actually owns.
 */
class OwnedLiftResolverTest {
    private val now = 1_700_000_000_000L

    @Test
    fun aRoutineBeatsHistory() {
        val resolved = OwnedLiftResolver.resolve(
            muscle = CanonicalMuscle.HAMSTRINGS,
            routines = listOf(routineWith("rdl")),
            history = listOf(historyWith("leg-curl")),
            exerciseCatalog = catalog(),
            preferences = CoachPreferences.DEFAULT,
            nowMs = now,
        )
        assertEquals("rdl", resolved?.id)
    }

    @Test
    fun historyIsUsedWhenNoRoutineCovers() {
        val resolved = OwnedLiftResolver.resolve(
            muscle = CanonicalMuscle.HAMSTRINGS,
            routines = emptyList(),
            history = listOf(historyWith("leg-curl")),
            exerciseCatalog = catalog(),
            preferences = CoachPreferences.DEFAULT,
            nowMs = now,
        )
        assertEquals("leg-curl", resolved?.id)
    }

    @Test
    fun aLiftYouHaveNotTouchedInMonthsIsNotOwned() {
        val resolved = OwnedLiftResolver.resolve(
            muscle = CanonicalMuscle.HAMSTRINGS,
            routines = emptyList(),
            history = listOf(historyWith("leg-curl", at = now - 90L * 24 * 60 * 60 * 1000)),
            exerciseCatalog = catalog(),
            preferences = CoachPreferences.DEFAULT,
            nowMs = now,
        )
        assertNull(resolved)
    }

    @Test
    fun equipmentYouDoNotHaveIsNeverNamed() {
        // The RDL is a barbell lift; with barbells switched off the resolver falls through to
        // the machine curl rather than suggesting something the user cannot do.
        val resolved = OwnedLiftResolver.resolve(
            muscle = CanonicalMuscle.HAMSTRINGS,
            routines = listOf(routineWith("rdl")),
            history = listOf(historyWith("leg-curl")),
            exerciseCatalog = catalog(),
            preferences = CoachPreferences(availableEquipment = setOf(EquipmentType.MACHINE.name)),
            nowMs = now,
        )
        assertEquals("leg-curl", resolved?.id)
    }

    @Test
    fun nothingOwnedResolvesToNull() {
        assertNull(
            OwnedLiftResolver.resolve(
                muscle = CanonicalMuscle.CALVES,
                routines = listOf(routineWith("rdl")),
                history = listOf(historyWith("leg-curl")),
                exerciseCatalog = catalog(),
                preferences = CoachPreferences.DEFAULT,
                nowMs = now,
            ),
        )
    }

    private fun catalog(): Map<String, Exercise> = mapOf(
        "rdl" to Exercise(
            id = "rdl", name = "Romanian Deadlift", muscleGroup = "Hamstrings", notes = "",
            isCustom = false, equipment = EquipmentType.BARBELL,
            muscles = listOf(MuscleCredit("hamstrings", 1.0), MuscleCredit("glutes", 0.5)),
        ),
        "leg-curl" to Exercise(
            id = "leg-curl", name = "Leg Curl", muscleGroup = "Hamstrings", notes = "",
            isCustom = false, equipment = EquipmentType.MACHINE,
            muscles = listOf(MuscleCredit("hamstrings", 1.0)),
        ),
    )

    private fun routineWith(exerciseId: String): Routine = Routine(
        id = "r1",
        name = "Legs",
        notes = "",
        createdAt = 0L,
        updatedAt = now,
        exercises = listOf(
            RoutineExercise(
                id = "re-1",
                routineId = "r1",
                exercise = catalog().getValue(exerciseId),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        ),
    )

    private fun historyWith(exerciseId: String, at: Long = 1_700_000_000_000L - 60_000): WorkoutSession =
        session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("x", "s1", exerciseId, catalog().getValue(exerciseId).name, 100.0, 8, at = at)),
            exercises = emptyList(),
            date = at,
        )
}

/**
 * The voice, enforced mechanically.
 *
 * A style guide nobody can run is a style guide that decays. This drives the engine across
 * every card type and asserts the rules that make the copy read as an instrument rather than
 * as encouragement.
 */
class RecommendationVoiceTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun noCardPraisesTheUserOrSpeaksInTheFirstPerson() {
        val cards = everyCardType()
        assertTrue("the scenarios must actually produce cards", cards.isNotEmpty())
        cards.forEach { card ->
            val text = "${card.kicker} ${card.title} ${card.reason}".lowercase()
            BANNED.forEach { banned ->
                assertFalse("'$banned' in: $text", text.contains(banned))
            }
        }
    }

    @Test
    fun everyKickerIsAnUppercaseCategoryFromTheKnownSet() {
        everyCardType().forEach { card ->
            assertEquals(card.kicker, card.kicker.uppercase())
            assertTrue("unknown kicker '${card.kicker}'", card.kicker in KICKERS)
        }
    }

    @Test
    fun noCardQuotesTheDisplayWindow() {
        // The advice is computed over 14 days and says so. If a card ever said "this week" or
        // "last 30 days" it would be describing a window it did not measure.
        everyCardType().forEach { card ->
            val text = "${card.title} ${card.reason}".lowercase()
            assertFalse(text, text.contains("this week"))
            assertFalse(text, text.contains("30 days"))
            assertFalse(text, text.contains("7 days"))
        }
    }

    private fun everyCardType(): List<TrainingRecommendation> {
        val imbalanced = coachInputs(
            sets = liftSets("ex-bench", "Bench", "Chest", 14) + liftSets("ex-row", "Row", "Back", 3),
            hints = listOf(
                ProgressionHint("ex-bench", "Bench", 100.0, 5, 5, 102.5, ProgressionAction.INCREASE),
            ),
        )
        val everythingProductive = coachInputs(
            sets = CanonicalMuscle.bodyMapOrder.flatMap { muscle ->
                liftSets("ex-${muscle.name}", muscle.displayName, muscle.catalogLabel, 24)
            },
        )
        val neglected = coachInputs(
            sets = liftSets("ex-bench", "Bench", "Chest", 12, daysAgo = 9),
        )
        return RecommendationEngine.recommend(imbalanced) +
            RecommendationEngine.recommend(everythingProductive) +
            RecommendationEngine.recommend(neglected)
    }

    private fun coachInputs(
        sets: List<Pair<SetLog, String>>,
        hints: List<ProgressionHint> = emptyList(),
    ): CoachInputs {
        val history = sets.groupBy { it.first.sessionId }.map { (sessionId, rows) ->
            session(
                id = sessionId,
                finishedAt = rows.maxOf { it.first.completedAt },
                sets = rows.map { it.first },
                exercises = rows
                    .map { (set, muscle) -> sessionExercise(set.exerciseId, set.exerciseName, muscle) }
                    .distinctBy { it.exercise.id },
            )
        }
        return CoachInputs(
            basis = MuscleLoadCalculator.coachBasis(history, now, zone),
            history = history,
            routines = emptyList(),
            hints = hints,
            exerciseCatalog = emptyMap(),
            nowMs = now,
            zone = zone,
        )
    }

    private fun liftSets(
        exerciseId: String,
        name: String,
        muscle: String,
        count: Int,
        daysAgo: Long = 1,
    ): List<Pair<SetLog, String>> = (0 until count).map { index ->
        set(
            id = "$exerciseId-$index",
            sessionId = "s-$exerciseId",
            exerciseId = exerciseId,
            name = name,
            weightKg = 100.0,
            reps = 5,
            at = now - daysAgo * 24 * 60 * 60 * 1000,
        ) to muscle
    }

    private companion object {
        val BANNED = listOf("!", " i ", "we ", "great", "nice", "good job", "well done", "amazing", "keep it up")
        val KICKERS = setOf(
            RecommendationEngine.KICKER_BALANCE,
            RecommendationEngine.KICKER_COVERAGE,
            RecommendationEngine.KICKER_PROGRESSION,
            RecommendationEngine.KICKER_RECOVERY,
            RecommendationEngine.KICKER_LOAD,
        )
    }
}

/**
 * The guards that stop a live-session edit from rewriting what already happened.
 */
class SwapRemoveGuardsTest {

    @Test
    fun aLiftWithLoggedSetsCannotBeRemovedOrSwapped() {
        assertEquals(
            SessionEditRules.HAS_LOGGED_SETS,
            SessionEditRules.refusalForRemove(sessionFinished = false, itemExists = true, loggedSetCount = 1),
        )
        assertEquals(
            SessionEditRules.HAS_LOGGED_SETS,
            SessionEditRules.refusalForSwap(
                sessionFinished = false,
                itemExists = true,
                loggedSetCount = 1,
                replacementAlreadyPresent = false,
            ),
        )
    }

    @Test
    fun aFinishedSessionIsNotEditableThisWay() {
        assertEquals(
            SessionEditRules.FINISHED_SESSION,
            SessionEditRules.refusalForRemove(sessionFinished = true, itemExists = true, loggedSetCount = 0),
        )
    }

    @Test
    fun aMissingItemIsRefusedRatherThanIgnored() {
        assertEquals(
            SessionEditRules.ITEM_MISSING,
            SessionEditRules.refusalForRemove(sessionFinished = false, itemExists = false, loggedSetCount = 0),
        )
    }

    @Test
    fun swappingInALiftAlreadyInTheSessionIsRefused() {
        assertEquals(
            SessionEditRules.ALREADY_PRESENT,
            SessionEditRules.refusalForSwap(
                sessionFinished = false,
                itemExists = true,
                loggedSetCount = 0,
                replacementAlreadyPresent = true,
            ),
        )
    }

    @Test
    fun anUntouchedLiftInALiveSessionIsFreeToChange() {
        assertNull(
            SessionEditRules.refusalForRemove(sessionFinished = false, itemExists = true, loggedSetCount = 0),
        )
        assertNull(
            SessionEditRules.refusalForSwap(
                sessionFinished = false,
                itemExists = true,
                loggedSetCount = 0,
                replacementAlreadyPresent = false,
            ),
        )
    }
}
