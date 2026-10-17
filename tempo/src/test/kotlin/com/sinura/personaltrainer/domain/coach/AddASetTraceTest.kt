package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundary expectations belong to the published rule, not to the new trace factory. */
class AddASetTraceTest {
    @Test fun exactlyTwoSignalsStillRejectAndExactlyThreeStillOffer() {
        val three = context()
        val two = three.copy(lastSessionMatchingSets = listOf(set(weight = 101.0), set(weight = 101.0)))
        assertNull(AddASetPolicy.evaluate(two))
        val decision = checkNotNull(AddASetPolicy.evaluate(three)).decision
        assertEquals(3, decision.readinessCount)
        assertFalse(decision.effortSignal)
        assertTrue(decision.completionSignal)
        assertTrue(decision.performanceSignal)
        assertTrue(decision.weeklyVolumeSignal)
        assertFalse(decision.trendOrBlockSignal)
    }

    @Test fun trendAndBlockTogetherAreOnlyOneOfTheFiveSignals() {
        val ctx = context().copy(
            todayWorking = listOf(set(rpe = 7), set(rpe = 7)),
            lastWorkingSet = set(rpe = 7),
            lastSessionMatchingSets = listOf(set(weight = 101.0), set(weight = 101.0)),
            weeklyHardSetsForMuscle = 10.0,
            muscleWeeklySetsByWeek = listOf(12.0, 11.0, 10.0),
            blockWeekIndex = 4,
        )
        val d = checkNotNull(AddASetPolicy.evaluate(ctx)).decision
        assertTrue(d.trendSignal)
        assertTrue(d.blockSignal)
        assertEquals(3, d.readinessCount) // effort + completion + (trend OR block)
        assertFalse(d.performanceSignal)
        assertFalse(d.weeklyVolumeSignal)
    }

    @Test fun inclusiveEffortAndStrictWeeklyVolumeBoundariesStayPublished() {
        val needsEffort = context().copy(
            lastSessionMatchingSets = listOf(set(weight = 101.0), set(weight = 101.0)),
            todayWorking = listOf(set(rpe = 7), set(rpe = 8)),
        )
        assertEquals(7.5, checkNotNull(AddASetPolicy.evaluate(needsEffort)).decision.meanRpe, 0.0)
        assertNull(AddASetPolicy.evaluate(needsEffort.copy(todayWorking = listOf(set(rpe = 8), set(rpe = 8)))))
        assertNotNull(AddASetPolicy.evaluate(context().copy(weeklyHardSetsForMuscle = 9.999)))
        assertNull(AddASetPolicy.evaluate(context().copy(weeklyHardSetsForMuscle = 10.0)))
    }

    @Test fun hardGatesAndPreferLoadRoutesStillRejectTheSameInputs() {
        val ctx = context()
        val noOffers = listOf(
            ctx.copy(goal = TrainingGoal.STRENGTH),
            ctx.copy(primaryMuscle = null),
            ctx.copy(primaryMuscle = CanonicalMuscle.OTHER),
            ctx.copy(targetSets = 4, workingLogged = 4, todayWorking = List(4) { set() }),
            ctx.copy(targetSets = 0),
            ctx.copy(workingLogged = 1),
            ctx.copy(extraSetAlreadyAccepted = true),
            ctx.copy(lighterWeek = true),
            ctx.copy(todayWorking = listOf(set(rpe = null), set())),
            ctx.copy(addASetDismissedForExercise = true),
            ctx.copy(manualExtraAfterLastPlanned = true),
        )
        noOffers.forEach { assertNull(AddASetPolicy.evaluate(it)) }
        assertNotNull(AddASetPolicy.evaluate(ctx.copy(targetSets = 3, workingLogged = 3, todayWorking = List(3) { set() })))
        val prefersLoad = ctx.copy(
            todayWorking = listOf(set(rpe = 7), set(rpe = 7)), lastWorkingSet = set(rpe = 7),
            extraSetReasonCode = SetMicroRecCalculator.IN_TANK, loadProgressionBlocked = false,
        )
        assertNull(AddASetPolicy.evaluate(prefersLoad))
        assertNotNull(AddASetPolicy.evaluate(prefersLoad.copy(loadProgressionBlocked = true)))
        assertNotNull(AddASetPolicy.evaluate(prefersLoad.copy(
            todayWorking = listOf(set(rpe = 7), set(rpe = 8)), lastWorkingSet = set(rpe = 8),
        )))
    }

    @Test fun noHistoryIsNeutralSupportAndTheBlockInputNamesItsActualSource() {
        val d = checkNotNull(AddASetPolicy.evaluate(context().copy(lastSessionMatchingSets = emptyList()))).decision
        assertFalse(d.comparisonAvailable)
        assertTrue(d.performanceSignal)
        assertFalse(d.blockSignal)
        val block = context().copy(
            lastSessionMatchingSets = listOf(set(weight = 101.0), set(weight = 101.0)),
            blockWeekIndex = 4, blockComparisonSource = AddASetTrace.CURRENT_PLAN_TARGET,
        )
        val trace = AddASetTrace.from(checkNotNull(AddASetPolicy.evaluate(block)), seed().trace)
        assertEquals("true", trace.facts.single { it.name == "blockSignal" }.value)
        assertEquals("2", trace.facts.single { it.name == "blockComparisonSets" }.value)
        assertEquals(AddASetTrace.CURRENT_PLAN_TARGET, trace.facts.single { it.name == "blockComparisonSource" }.value)
    }

    @Test fun acceptedTraceHasItsOwnActionFactsThresholdsAndThreeCalendarWeekWindow() {
        val ctx = context().copy(traceMetadata = AddASetTrace.Metadata(42L, 100L, 96L, "America/Toronto"))
        val trace = AddASetTrace.from(checkNotNull(AddASetPolicy.evaluate(ctx)), seed().trace)
        val facts = trace.facts.associate { it.name to it.value }
        val thresholds = trace.thresholds.associate { it.name to it.value }
        assertEquals("tempo-add-a-set", trace.ruleId)
        assertEquals("ADD_A_SET", trace.action)
        assertEquals(listOf("PLAN_COMPLETE", "READINESS_MET", "LOAD_NOT_PREFERRED"), trace.reasonCodes)
        assertEquals(82L, trace.evidenceStartEpochDay)
        assertEquals(100L, trace.evidenceEndEpochDay)
        assertEquals(42L, trace.generatedAtMs)
        assertEquals("3", facts["readinessCount"])
        assertEquals("2", facts["rpeLogged"])
        assertEquals("false", facts["preferLoad"])
        assertEquals("false", facts["dismissed"])
        assertEquals("false", facts["manualExtra"])
        assertEquals("4.0", facts["weeklySets"])
        assertEquals("unavailable", facts["comparisonDate"])
        assertEquals("3", thresholds["readinessRequired"])
        assertEquals("5", thresholds["readinessSignals"])
        assertEquals("7.5", thresholds["meanRpeCeiling"])
        assertEquals("10", thresholds["weeklySetGuide"])
    }

    @Test fun explanationMetadataCannotChangeAnOfferAndUnknownDatesStayUnknown() {
        val ctx = context()
        val metadata = AddASetTrace.Metadata(42L, 100L, 96L, "UTC")
        val plain = checkNotNull(AddASetPolicy.evaluate(ctx))
        val dated = checkNotNull(AddASetPolicy.evaluate(ctx.copy(traceMetadata = metadata)))
        assertEquals(plain.copy(decision = plain.decision.copy(traceMetadata = metadata)), dated)
        val trace = AddASetTrace.from(plain, seed().trace)
        assertEquals(1L, trace.evidenceStartEpochDay)
        assertEquals(0L, trace.evidenceEndEpochDay)
        assertEquals("unavailable", trace.facts.single { it.name == "currentWeekStartEpochDay" }.value)
    }

    @Test fun resolverPreservesTheExactSeedRecommendationAndItsMathTrace() {
        val seed = seed()
        val original = seed.copy()
        val tip = TempoCoach.resolve(null, CoachPreferences.DEFAULT, context().copy(extraSetReasonCode = seed.reasonCode), seed)
            as TempoCoachTip.AddASet
        assertSame(seed, tip.seedRec)
        assertSame(seed.trace, tip.seedRec.trace)
        assertEquals(original, tip.seedRec) // weight, reps, effort, rest, flags and seed trace
        assertEquals(AddASetTrace.ACTION, tip.trace.action)
        assertEquals(seed.trace.facts.firstOrNull { it.name == "lastSet" }, tip.trace.facts.firstOrNull { it.name == "lastSet" })
    }

    private fun set(weight: Double = 100.0, rpe: Int? = 8) = LoggedSetView(weight, 8, rpe, false)

    private fun context(): AddASetPolicy.Context {
        val working = listOf(set(), set())
        return AddASetPolicy.Context(
            goal = TrainingGoal.HYPERTROPHY, primaryMuscle = CanonicalMuscle.CHEST,
            targetSets = 2, workingLogged = 2, todayWorking = working,
            lighterWeek = false, extraSetAlreadyAccepted = false, addASetDismissedForExercise = false,
            weeklyHardSetsForMuscle = 4.0, muscleWeeklySetsByWeek = listOf(2.0, 3.0, 4.0),
            blockWeekIndex = 0, blockOpenerSetsForLift = 2, lastSessionMatchingSets = working,
            lastWorkingSet = working.last(), targetReps = 8,
            extraSetReasonCode = SetMicroRecCalculator.RPE_HOLD, loadProgressionBlocked = true,
            manualExtraAfterLastPlanned = false,
        )
    }

    private fun seed() = checkNotNull(SetMicroRecCalculator.suggest(setMicroRecInputs(
        editing = false, loadType = LoadType.EXTERNAL, unit = WeightUnit.KG,
        targetSets = 2, targetReps = 8, targetWeightKg = 100.0, working = listOf(set(), set()),
        lastAnySetWasWarmup = false, hint = null, lighterWeek = false,
        draftWeightKg = 87.5, draftReps = 12, draftRpe = 6,
        nowMs = 17L, todayEpochDay = 150L, allowExtra = true,
    )))
}
