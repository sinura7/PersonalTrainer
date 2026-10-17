package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.Coach
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecInputs
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachEngineTest {
    @Test
    fun suggestMatchesCoachDecideNumbers() {
        val inputs = inTankInputs()
        val decision = Coach.decide(inputs)
        val suggestion = CoachEngine.suggest(inputs)
        checkNotNull(decision)
        checkNotNull(suggestion)
        assertEquals(decision.weightKg, suggestion.weightKg, 0.0001)
        assertEquals(decision.reps, suggestion.reps)
        assertEquals(decision.reasonCode, suggestion.reasonCode)
    }

    @Test
    fun inTankCitesRpeLiteratureAndPlateHeuristic() {
        val suggestion = checkNotNull(CoachEngine.suggest(inTankInputs()))
        assertEquals(SetMicroRecCalculator.IN_TANK, suggestion.reasonCode)
        assertTrue(suggestion.literatureEvidenceIds.contains("helms-2016-rpe-application"))
        assertTrue(suggestion.heuristicEvidenceIds.contains("heuristic-plate-increment"))
        assertNotNull(suggestion.primaryEvidence()?.doi)
    }

    @Test
    fun hardRpeHoldCitesNearFailureAccuracy() {
        val inputs = setMicroRecInputs(
            editing = false,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 100.0,
            working = listOf(
                LoggedSetView(100.0, 5, 10, false),
            ),
            lastAnySetWasWarmup = false,
            hint = null,
            lighterWeek = false,
            draftWeightKg = 0.0,
            draftReps = 0,
            draftRpe = null,
            nowMs = 0L,
            todayEpochDay = 0L,
            rpeIntent = true,
        )
        val suggestion = checkNotNull(CoachEngine.suggest(inputs))
        assertTrue(
            suggestion.reasonCode == SetMicroRecCalculator.RPE_HOLD ||
                suggestion.reasonCode == SetMicroRecCalculator.TOP_SET,
        )
        assertTrue(suggestion.literatureEvidenceIds.contains("hackett-2017-rtf-accuracy"))
    }

    @Test
    fun missingHistoryUsesConservativeHeuristicOnFirstSet() {
        val inputs = setMicroRecInputs(
            editing = false,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 60.0,
            working = emptyList(),
            lastAnySetWasWarmup = false,
            hint = null,
            lighterWeek = false,
            draftWeightKg = 0.0,
            draftReps = 0,
            draftRpe = null,
            nowMs = 0L,
            todayEpochDay = 0L,
            historyWorking = emptyList(),
        )
        val suggestion = checkNotNull(CoachEngine.suggest(inputs))
        assertEquals(SetMicroRecCalculator.FIRST_SET, suggestion.reasonCode)
        assertTrue(suggestion.evidenceIds.contains("heuristic-conservative-first-set"))
    }

    private fun inTankInputs(): SetMicroRecInputs = setMicroRecInputs(
        editing = false,
        loadType = LoadType.EXTERNAL,
        unit = WeightUnit.KG,
        targetSets = 3,
        targetReps = 10,
        targetWeightKg = 60.0,
        working = listOf(
            LoggedSetView(60.0, 12, 7, false),
        ),
        lastAnySetWasWarmup = false,
        hint = null,
        lighterWeek = false,
        draftWeightKg = 0.0,
        draftReps = 0,
        draftRpe = null,
        nowMs = 0L,
        todayEpochDay = 0L,
        rpeIntent = true,
    )
}
