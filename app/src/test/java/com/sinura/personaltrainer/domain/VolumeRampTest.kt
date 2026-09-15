package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeRampTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L
    private val zone = ZoneOffset.UTC

    @Test
    fun twelveEasyChestSetsNameTheMuscle() {
        val finding = VolumeRamp.detect(
            history = listOf(chestSession("s1", now - day, setCount = 12, rpe = 7)),
            nowMs = now,
            time = JvmTime,
            zoneId = zone.id,
        )
        assertNotNull(finding)
        assertEquals(CanonicalMuscle.CHEST, finding!!.muscle)
        assertEquals(12, finding.lastWeekSets)
        assertEquals(14, finding.suggestedSets)
    }

    @Test
    fun missingRpeIsNotEasyWork() {
        assertNull(
            VolumeRamp.detect(
                history = listOf(chestSession("s1", now - day, setCount = 12, rpe = null)),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }

    @Test
    fun aGrindBlocksTheRamp() {
        assertNull(
            VolumeRamp.detect(
                history = listOf(chestSession("s1", now - day, setCount = 12, rpe = 9)),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }

    @Test
    fun threeSetsAreNotEnough() {
        assertNull(
            VolumeRamp.detect(
                history = listOf(chestSession("s1", now - day, setCount = 3, rpe = 7)),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }

    @Test
    fun nineteenSetsWouldLeaveTheProductiveBand() {
        assertNull(
            VolumeRamp.detect(
                history = listOf(chestSession("s1", now - day, setCount = 19, rpe = 7)),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }

    @Test
    fun warmupSetsDoNotCount() {
        val at = now - day
        val working = (0 until 3).map { index ->
            set(
                id = "w$index",
                sessionId = "s1",
                exerciseId = "ex-bench",
                name = "Bench",
                weightKg = 100.0,
                reps = 8,
                warmup = false,
                at = at,
                rpe = 7,
            )
        }
        val warmups = (0 until 10).map { index ->
            set(
                id = "wu$index",
                sessionId = "s1",
                exerciseId = "ex-bench",
                name = "Bench",
                weightKg = 60.0,
                reps = 8,
                warmup = true,
                at = at,
                rpe = 5,
            )
        }
        assertNull(
            VolumeRamp.detect(
                history = listOf(
                    session(
                        id = "s1",
                        finishedAt = at,
                        date = at,
                        sets = working + warmups,
                        exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
                    ),
                ),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }

    @Test
    fun setsOlderThanAWeekDoNotCount() {
        assertNull(
            VolumeRamp.detect(
                history = listOf(chestSession("s1", now - 8 * day, setCount = 12, rpe = 7)),
                nowMs = now,
                time = JvmTime,
                zoneId = zone.id,
            ),
        )
    }
}

class VolumeRampRecommendationTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L
    private val week = 7L * day
    private val zone = ZoneOffset.UTC

    @Test
    fun theCardNamesTheMuscleAndOpensTheMap() {
        val history = listOf(chestSession("s1", now - day, setCount = 12, rpe = 7))
        val card = RecommendationEngine.volumeRamp(coachInputs(history))
        assertNotNull(card)
        assertEquals("volume-ramp-CHEST", card!!.id)
        assertEquals(RecommendationEngine.KICKER_LOAD, card.kicker)
        assertEquals(RecommendationAction.OPEN_BODY_MAP, card.action)
        assertEquals(CanonicalMuscle.CHEST, card.actionMuscle)
        assertTrue(card.hasDestination)
        assertEquals(
            "Chest ran 12 sets last week, all under RPE 8; 14 would be productive.",
            card.reason,
        )
        val trace = checkNotNull(card.trace)
        assertEquals("volume-ramp-CHEST", trace.ruleId)
        assertTrue(trace.reasonCodes.contains(RuleTrace.VOLUME_RAMP))
        assertEquals("More volume", RuleTraceCopy.reasonLabel(RuleTrace.VOLUME_RAMP))
        assertTrue(trace.alternatives.contains("write the extra sets into the plan"))
    }

    @Test
    fun hypertrophyLiftsTheVolumeCard() {
        val history = listOf(chestSession("s1", now - day, setCount = 12, rpe = 7))
        val general = RecommendationEngine.recommend(coachInputs(history))
            .first { it.id == "volume-ramp-CHEST" }
        val hypertrophy = RecommendationEngine.recommend(
            coachInputs(history, goal = TrainingGoal.HYPERTROPHY),
        ).first { it.id == "volume-ramp-CHEST" }
        assertTrue(hypertrophy.rankScore > general.rankScore)
    }

    @Test
    fun aDeloadCardSuppressesTheVolumeRamp() {
        val perWeek = listOf(9, 6, 4)
        val history = perWeek.flatMapIndexed { weekIndex, sets ->
            (0 until sets).map { setIndex ->
                val at = now - weekIndex * week - setIndex * 3_600_000L - 3_600_000L
                chestSession("w$weekIndex-$setIndex", at, setCount = 1, rpe = 7)
            }
        }
        val inputs = coachInputs(history)
        assertNotNull(RecommendationEngine.deloadSignal(inputs))
        val cards = RecommendationEngine.recommend(inputs)
        assertTrue(cards.any { it.id.startsWith("deload") })
        assertTrue(cards.none { it.id.startsWith("volume-ramp-") })
    }

    private fun coachInputs(
        history: List<WorkoutSession>,
        goal: TrainingGoal = TrainingGoal.GENERAL,
    ): CoachInputs =
        CoachInputs(
            basis = MuscleLoadCalculator.coachBasis(history, now, zone, emptyMap()),
            history = history,
            routines = emptyList(),
            hints = emptyList(),
            exerciseCatalog = emptyMap(),
            preferences = CoachPreferences(goal = goal),
            nowMs = now,
            zone = zone,
        )
}

private fun chestSession(
    id: String,
    at: Long,
    setCount: Int,
    rpe: Int?,
): WorkoutSession = session(
    id = id,
    finishedAt = at,
    date = at,
    sets = (0 until setCount).map { index ->
        set(
            id = "$id-$index",
            sessionId = id,
            exerciseId = "ex-bench",
            name = "Bench",
            weightKg = 100.0,
            reps = 8,
            at = at,
            rpe = rpe,
        )
    },
    exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
)
