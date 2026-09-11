package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetCopyTest {
    @Test
    fun aLoadedSetReadsAsWeightByReps() {
        assertEquals("100 kg × 5", SetCopy.setLine(100.0, 5, LoadClass.LOADED, WeightUnit.KG))
    }

    @Test
    fun aBodyweightSetReadsAsReps() {
        // What this replaces: "0 kg × 12" — the app reporting that nothing was lifted, twelve
        // times, on a set of push-ups.
        assertEquals("12 reps", SetCopy.setLine(0.0, 12, LoadClass.BODYWEIGHT, WeightUnit.KG))
        assertEquals("1 rep", SetCopy.setLine(0.0, 1, LoadClass.BODYWEIGHT, WeightUnit.KG))
    }

    @Test
    fun addedLoadAndAssistanceCarryOppositeSigns() {
        // The same field, the same number, opposite claims about how hard the set was.
        assertEquals(
            "8 reps +20 kg",
            SetCopy.setLine(20.0, 8, LoadClass.BODYWEIGHT_ADDED, WeightUnit.KG),
        )
        assertEquals(
            "8 reps −20 kg",
            SetCopy.setLine(20.0, 8, LoadClass.BODYWEIGHT_ASSISTED, WeightUnit.KG),
        )
    }

    @Test
    fun aWeightedLiftWithNoWeightOnIsJustReps() {
        assertEquals("8 reps", SetCopy.setLine(0.0, 8, LoadClass.BODYWEIGHT_ADDED, WeightUnit.KG))
    }

    @Test
    fun aSessionReportsOnlyTheUnitsItActuallyHas() {
        assertEquals(
            "1000 kg",
            SetCopy.workLine(SetWork(volumeKg = 1000.0, bodyweightReps = 0), WeightUnit.KG),
        )
        assertEquals(
            "42 bodyweight reps",
            SetCopy.workLine(SetWork(volumeKg = 0.0, bodyweightReps = 42), WeightUnit.KG),
        )
    }

    @Test
    fun aMixedSessionReportsBoth() {
        val line = SetCopy.workLine(SetWork(volumeKg = 1000.0, bodyweightReps = 42), WeightUnit.KG)
        assertEquals("1000 kg  ·  42 bodyweight reps", line)
    }

    @Test
    fun anEmptySessionSaysSoRatherThanClaimingZeroKilograms() {
        assertEquals(SetCopy.NOTHING_YET, SetCopy.workLine(SetWork.NONE, WeightUnit.KG))
    }

    @Test
    fun onlyTheClassesWithSomethingToExplainGetAHint() {
        assertNull(SetCopy.weightFieldHint(LoadClass.LOADED))
        // No field at all for a plain bodyweight lift, so nothing to caption. A labelled empty
        // box invites a number, and the number someone would put there is their bodyweight.
        assertNull(SetCopy.weightFieldHint(LoadClass.BODYWEIGHT))
        assertEquals(
            "Vest, belt or plate. Leave empty for bodyweight only.",
            SetCopy.weightFieldHint(LoadClass.BODYWEIGHT_ADDED),
        )
        assertEquals(
            "How much the machine took off. More assist is an easier set.",
            SetCopy.weightFieldHint(LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun bodyweightReadsWithItsDirection() {
        // The direction is the whole content: the same four kilos is the point of a bulk and
        // the failure of a cut, and the app has no business deciding which one this was.
        assertEquals(
            "78 kg → 82 kg · +4 kg",
            SetCopy.bodyweightLine(BodyweightChange(fromKg = 78.0, toKg = 82.0), WeightUnit.KG),
        )
        assertEquals(
            "82 kg → 78 kg · −4 kg",
            SetCopy.bodyweightLine(BodyweightChange(fromKg = 82.0, toKg = 78.0), WeightUnit.KG),
        )
    }

    @Test
    fun halfAKiloReadsAsHeldRatherThanAsAChange() {
        assertEquals(
            "78 kg → 78.3 kg · held",
            SetCopy.bodyweightLine(BodyweightChange(fromKg = 78.0, toKg = 78.3), WeightUnit.KG),
        )
    }

    @Test
    fun nothingLoggedSaysNothing() {
        assertNull(SetCopy.bodyweightLine(null, WeightUnit.KG))
    }

    @Test
    fun tableExtrasNameTheSetThenRpe() {
        assertEquals("Set 3", SetCopy.tableExtras(3, null))
        assertEquals("Set 3 · RPE 8", SetCopy.tableExtras(3, 8))
    }
}
