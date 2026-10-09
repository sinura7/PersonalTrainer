package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.LoadClass
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
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoWhySheetCopyTest {
    @Test
    fun topSetAfterRpe10ExplainsPlanTargetIsNotARepCap() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                setMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = WeightUnit.LBS,
                    targetSets = 3,
                    targetReps = 4,
                    targetWeightKg = 61.2,
                    working = listOf(LoggedSetView(61.2, 6, 10, false)),
                    lastAnySetWasWarmup = false,
                    hint = null,
                    lighterWeek = false,
                    draftWeightKg = 61.2,
                    draftReps = 6,
                    draftRpe = null,
                    nowMs = 0L,
                    todayEpochDay = 0L,
                    rpeIntent = false,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.TOP_SET, rec.reasonCode)
        val suggestion = CoachEngine.fromMicroRec(rec)
        val model = TempoWhySheetCopy.forNextSet(rec, suggestion, LoadClass.LOADED, WeightUnit.LBS)
        assertEquals("Hold", model.callout.verb)
        assertTrue(model.summary.contains("progression band"))
        assertTrue(
            model.decisionRows.any { it.label == TempoWhySheetCopy.LABEL_PLAN_TARGET && it.value == "4" },
        )
    }

    @Test
    fun doiUrlUsesHttpsDoiOrg() {
        assertEquals(
            "https://doi.org/10.1519/JSC.0000000000001049",
            TempoWhySheetCopy.doiUrl("10.1519/JSC.0000000000001049"),
        )
    }

    @Test fun extraSetExplainsActualSignalsWithoutInventingEarlierImprovement() {
        val tip = addTip()
        val model = TempoWhySheetCopy.forAddASet(tip, tip.seedRec, LoadClass.LOADED, WeightUnit.KG)
        val rows = model.decisionRows.associate { it.label to it.value }
        assertEquals("Your planned sets are complete. 4 of 5 readiness checks support one extra set.", model.summary)
        assertEquals("Average RPE 7 · within the 7.5 guide.", rows[TempoWhySheetCopy.LABEL_ADD_EFFORT])
        assertEquals("No failed or cut-short sets flagged.", rows[TempoWhySheetCopy.LABEL_ADD_COMPLETION])
        assertEquals("No earlier session to compare.", rows[TempoWhySheetCopy.LABEL_ADD_COMPARISON])
        assertEquals("Estimate 2 sets · below the 10-set guide.", rows[TempoWhySheetCopy.LABEL_ADD_WEEKLY])
        assertEquals("No recent-volume or block-week support.", rows[TempoWhySheetCopy.LABEL_ADD_TIMING])
        assertEquals("Add 1 set", model.callout.verb)
        assertEquals("Rest 1:30", model.callout.restLabel)
        assertFalse(model.summary.contains("Tempo checked"))
    }

    @Test fun currentTargetBlockSupportNeverClaimsAHistoricalOpenerWasRead() {
        val tip = addTip { it.copy(blockWeekIndex = 4) }
        val model = TempoWhySheetCopy.forAddASet(tip, tip.seedRec, LoadClass.LOADED, WeightUnit.KG)
        assertEquals("Block week 4 fits today's planned set count.",
            model.decisionRows.single { it.label == TempoWhySheetCopy.LABEL_ADD_TIMING }.value)
        assertFalse(model.decisionRows.any { it.value.contains("opener", ignoreCase = true) })
    }

    @Test fun trendNamesCurrentWeekAndPriorTwoWhileWeeklyEstimateKeepsStrictBoundary() {
        val tip = addTip { it.copy(
            muscleWeeklySetsByWeek = listOf(12.0, 11.0, 9.999), weeklyHardSetsForMuscle = 9.999,
        ) }
        val model = TempoWhySheetCopy.forAddASet(tip, tip.seedRec, LoadClass.LOADED, WeightUnit.KG)
        assertEquals("Steady or lower across three weeks, including this week.",
            model.decisionRows.single { it.label == TempoWhySheetCopy.LABEL_ADD_TIMING }.value)
        assertEquals("Estimate just under 10 sets · below the 10-set guide.",
            model.decisionRows.single { it.label == TempoWhySheetCopy.LABEL_ADD_WEEKLY }.value)
    }

    @Test fun failedReadinessRowsDoNotImplyEveryCheckPassed() {
        val tip = addTip { it.copy(
            todayWorking = listOf(LoggedSetView(70.0, 10, 8, false), LoggedSetView(70.0, 10, 8, false)),
            lastSessionMatchingSets = listOf(LoggedSetView(72.5, 10, 8, false)),
            blockWeekIndex = 4,
        ) }
        val model = TempoWhySheetCopy.forAddASet(tip, tip.seedRec, LoadClass.LOADED, WeightUnit.KG)
        assertEquals("Your planned sets are complete. 3 of 5 readiness checks support one extra set.", model.summary)
        assertEquals("Average RPE 8 · above the 7.5 guide.",
            model.decisionRows.single { it.label == TempoWhySheetCopy.LABEL_ADD_EFFORT }.value)
        assertEquals("Compared sets fell below last time.",
            model.decisionRows.single { it.label == TempoWhySheetCopy.LABEL_ADD_COMPARISON }.value)
    }

    private fun addTip(change: (AddASetPolicy.Context) -> AddASetPolicy.Context = { it }): TempoCoachTip.AddASet {
        val working = listOf(LoggedSetView(70.0, 10, 6, false), LoggedSetView(70.0, 10, 8, false))
        val seed = checkNotNull(SetMicroRecCalculator.suggest(setMicroRecInputs(
            editing = false, loadType = LoadType.EXTERNAL, unit = WeightUnit.KG,
            targetSets = 2, targetReps = 10, targetWeightKg = 70.0, working = working,
            lastAnySetWasWarmup = false, hint = null, lighterWeek = false,
            draftWeightKg = 87.5, draftReps = 12, draftRpe = 8, nowMs = 42L, todayEpochDay = 100L,
            allowExtra = true,
        )))
        val ctx = AddASetPolicy.Context(
            goal = TrainingGoal.HYPERTROPHY, primaryMuscle = CanonicalMuscle.QUADRICEPS,
            targetSets = 2, workingLogged = 2, todayWorking = working,
            lighterWeek = false, extraSetAlreadyAccepted = false, addASetDismissedForExercise = false,
            weeklyHardSetsForMuscle = 2.0, muscleWeeklySetsByWeek = listOf(0.0, 0.0, 2.0),
            blockWeekIndex = 0, blockOpenerSetsForLift = 2, lastSessionMatchingSets = emptyList(),
            lastWorkingSet = working.last(), targetReps = 10, extraSetReasonCode = seed.reasonCode,
            loadProgressionBlocked = false, manualExtraAfterLastPlanned = false,
            blockComparisonSource = AddASetTrace.CURRENT_PLAN_TARGET,
        )
        return TempoCoach.resolve(null, CoachPreferences.DEFAULT, change(ctx), seed) as TempoCoachTip.AddASet
    }
}
