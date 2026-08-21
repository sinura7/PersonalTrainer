package com.sinura.personaltrainer.domain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coach, rewritten around three claims.
 *
 * It counts sets, not kilograms — a deadlift session and a curl session produce wildly
 * different tonnage for the same amount of training, so a tonnage ratio told people training
 * evenly that they were three times out of balance.
 *
 * It reasons from a fixed 14 days, never from the display window — advice that changed when
 * you tapped a chip on the body map made the app look like it was guessing.
 *
 * And it names lifts you already own, so "train your hamstrings" becomes something you can act
 * on without translating it yourself.
 */
class RecommendationEngineTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun emptyHistoryProducesNoRecommendations() {
        assertTrue(RecommendationEngine.recommend(inputs()).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Imbalance, counted in sets
    // -----------------------------------------------------------------------

    @Test
    fun imbalanceIsMeasuredInWeightedSetsNotTonnage() {
        // Ten chest sets against four back sets is a real 2.5x gap...
        val sets = liftSets("ex-bench", "Bench", "Chest", 10, 100.0) +
            liftSets("ex-row", "Row", "Back", 4, 80.0)
        val cards = RecommendationEngine.imbalances(inputs(sets))

        val chestBack = cards.first { it.id == "imbalance-CHEST-BACK" }
        assertEquals(RecommendationEngine.KICKER_BALANCE, chestBack.kicker)
        assertEquals("Back is behind Chest", chestBack.title)
        assertEquals(CanonicalMuscle.BACK, chestBack.actionMuscle)
    }

    @Test
    fun equalSetsAtWildlyDifferentWeightsAreNotAnImbalance() {
        // ...and this is the case tonnage got wrong. Same number of sets each; the deadlift
        // moves five times the weight of the curl, which the old ratio read as neglect.
        val sets = liftSets("ex-row", "Row", "Back", 8, 150.0) +
            liftSets("ex-curl", "Curl", "Biceps", 8, 25.0)
        val cards = RecommendationEngine.imbalances(inputs(sets))
        assertNull(cards.firstOrNull { it.actionMuscle == CanonicalMuscle.BICEPS })
    }

    @Test
    fun imbalanceNeedsTheHeavySideToBeDoingRealWork() {
        // Two sets against zero is not an imbalance; it is a quiet fortnight.
        val sets = liftSets("ex-bench", "Bench", "Chest", 2, 100.0)
        assertTrue(RecommendationEngine.imbalances(inputs(sets)).isEmpty())
    }

    @Test
    fun aZeroSideIsCalledOutExplicitly() {
        val sets = liftSets("ex-bench", "Bench", "Chest", 12, 100.0)
        val card = RecommendationEngine.imbalances(inputs(sets))
            .first { it.actionMuscle == CanonicalMuscle.BACK }
        assertEquals("No Back work against Chest", card.title)
        assertTrue(card.reason, card.reason.contains("has none"))
        assertEquals(RecommendationPriority.HIGH, card.priority)
    }

    // -----------------------------------------------------------------------
    // Do less
    // -----------------------------------------------------------------------

    @Test
    fun restSignalFiresOnlyWhenEveryMuscleIsProductive() {
        val everything = CanonicalMuscle.bodyMapOrder.flatMap { muscle ->
            liftSets("ex-${muscle.name}", muscle.displayName, muscle.catalogLabel, 24, 60.0)
        }
        val card = RecommendationEngine.restSignal(inputs(everything))
        assertNotNull(card)
        assertEquals(RecommendationEngine.KICKER_RECOVERY, card!!.kicker)
        assertEquals("Every muscle is at productive volume", card.title)

        // One muscle short of the floor and the advice is no longer "add nothing".
        val short = CanonicalMuscle.bodyMapOrder.flatMap { muscle ->
            val count = if (muscle == CanonicalMuscle.CALVES) 4 else 24
            liftSets("ex-${muscle.name}", muscle.displayName, muscle.catalogLabel, count, 60.0)
        }
        assertNull(RecommendationEngine.restSignal(inputs(short)))
    }

    // -----------------------------------------------------------------------
    // Ranking
    // -----------------------------------------------------------------------

    @Test
    fun recommendCapsAndRanksHighFirst() {
        val sets = liftSets("ex-bench", "Bench", "Chest", 14, 140.0) +
            liftSets("ex-row", "Row", "Back", 3, 40.0)
        val recs = RecommendationEngine.recommend(
            inputs(
                sets = sets,
                hints = listOf(
                    ProgressionHint("ex-bench", "Bench", 140.0, 5, 5, 142.5, ProgressionAction.INCREASE),
                ),
            ),
        )
        assertTrue(recs.size <= RecommendationEngine.MAX_RESULTS)
        assertEquals(RecommendationPriority.HIGH, recs.first().priority)
        val priorities = recs.map { it.priority.ordinal }
        assertEquals(priorities.sorted(), priorities)
    }

    @Test
    fun theGoalReordersButNeverAddsOrRemovesACard() {
        val sets = liftSets("ex-bench", "Bench", "Chest", 14, 140.0) +
            liftSets("ex-row", "Row", "Back", 3, 40.0)
        val hints = listOf(
            ProgressionHint("ex-bench", "Bench", 140.0, 5, 5, 142.5, ProgressionAction.INCREASE),
        )
        val general = RecommendationEngine.recommend(inputs(sets, hints = hints))
        val strength = RecommendationEngine.recommend(
            inputs(sets, hints = hints, goal = TrainingGoal.STRENGTH),
        )
        val hypertrophy = RecommendationEngine.recommend(
            inputs(sets, hints = hints, goal = TrainingGoal.HYPERTROPHY),
        )

        assertEquals(general.map { it.id }.toSet(), strength.map { it.id }.toSet())
        assertEquals(general.map { it.id }.toSet(), hypertrophy.map { it.id }.toSet())
        // Strength lifts the progression card above the other INFO-priority cards it ties with.
        val strengthScore = strength.first { it.id == "progression-ready" }.rankScore
        val generalScore = general.first { it.id == "progression-ready" }.rankScore
        assertTrue("strength must weight progression higher", strengthScore > generalScore)
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun inputs(
        sets: List<Pair<SetLog, String>> = emptyList(),
        hints: List<ProgressionHint> = emptyList(),
        goal: TrainingGoal = TrainingGoal.GENERAL,
        routines: List<Routine> = emptyList(),
        catalog: Map<String, Exercise> = emptyMap(),
    ): CoachInputs {
        val history = sessionsFrom(sets)
        return CoachInputs(
            basis = MuscleLoadCalculator.coachBasis(history, now, zone, catalog),
            history = history,
            routines = routines,
            hints = hints,
            exerciseCatalog = catalog,
            preferences = CoachPreferences(goal = goal),
            nowMs = now,
            zone = zone,
        )
    }

    private fun sessionsFrom(sets: List<Pair<SetLog, String>>): List<WorkoutSession> =
        sets.groupBy { it.first.sessionId }.map { (sessionId, rows) ->
            session(
                id = sessionId,
                finishedAt = rows.maxOf { it.first.completedAt },
                sets = rows.map { it.first },
                exercises = rows
                    .map { (set, muscle) -> sessionExercise(set.exerciseId, set.exerciseName, muscle) }
                    .distinctBy { it.exercise.id },
            )
        }

    /**
     * [count] working sets of one lift, spread over the last few days so they all land inside
     * the coach's trailing window.
     */
    private fun liftSets(
        exerciseId: String,
        name: String,
        muscle: String,
        count: Int,
        weightKg: Double,
    ): List<Pair<SetLog, String>> = (0 until count).map { index ->
        set(
            id = "$exerciseId-$index",
            sessionId = "s-$exerciseId",
            exerciseId = exerciseId,
            name = name,
            weightKg = weightKg,
            reps = 5,
            at = now - days(1),
        ) to muscle
    }

    private fun days(count: Long): Long = count * 24L * 60L * 60L * 1000L

    // ---- call-to-action ----

    private fun rec(
        action: RecommendationAction? = null,
        actionMuscle: CanonicalMuscle? = null,
        actionExerciseId: String? = null,
    ) = TrainingRecommendation(
        id = "r",
        kicker = "BALANCE",
        title = "t",
        reason = "r",
        priority = RecommendationPriority.INFO,
        action = action,
        actionMuscle = actionMuscle,
        actionExerciseId = actionExerciseId,
        rankScore = 1,
    )

    @Test
    fun adviceWithNowhereToGoOffersNoCallToAction() {
        // Both of these are complete sentences with no destination. They used to render a Volt
        // "Show on the map →" whose tap cleared the map selection, because the only thing it
        // could dispatch to was a muscle that was never set.
        val everything = CanonicalMuscle.bodyMapOrder.flatMap { muscle ->
            liftSets("ex-${muscle.name}", muscle.displayName, muscle.catalogLabel, 24, 60.0)
        }
        val rest = RecommendationEngine.restSignal(inputs(everything))
        assertNotNull(rest)
        assertFalse(rest!!.hasDestination)
    }

    @Test
    fun aBodyMapCardNeedsAMuscleToBeADestination() {
        assertFalse(rec(action = RecommendationAction.OPEN_BODY_MAP).hasDestination)
        assertTrue(
            rec(
                action = RecommendationAction.OPEN_BODY_MAP,
                actionMuscle = CanonicalMuscle.QUADRICEPS,
            ).hasDestination,
        )
    }

    @Test
    fun aNamedLiftIsADestinationAndSoIsItsFallback() {
        // OPEN_EXERCISE falls back to the body map when the lift id did not resolve, so either
        // the id or the muscle is enough — but not neither.
        assertTrue(rec(action = RecommendationAction.OPEN_EXERCISE, actionExerciseId = "ex-1").hasDestination)
        assertTrue(
            rec(
                action = RecommendationAction.OPEN_EXERCISE,
                actionMuscle = CanonicalMuscle.QUADRICEPS,
            ).hasDestination,
        )
        assertFalse(rec(action = RecommendationAction.OPEN_EXERCISE).hasDestination)
    }

    @Test
    fun theActionsThatAlwaysGoSomewhereAlwaysDo() {
        assertTrue(rec(action = RecommendationAction.OPEN_LIBRARY_MUSCLE).hasDestination)
        assertTrue(rec(action = RecommendationAction.START_WORKOUT).hasDestination)
        assertTrue(rec(action = RecommendationAction.OPEN_ROUTINES).hasDestination)
    }
}
