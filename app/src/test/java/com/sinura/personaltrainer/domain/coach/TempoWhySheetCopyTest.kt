package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
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
}
