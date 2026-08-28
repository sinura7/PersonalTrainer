package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SetMicroRecCalculatorTest(
    private val row: Row,
) {
    @Test
    fun v1Row() {
        val rec = SetMicroRecCalculator.suggest(row.inputs)
        if (row.hidden) {
            assertNull(row.name, rec)
            return
        }
        val got = checkNotNull(rec) { row.name }
        assertEquals(row.name, row.reason, got.reasonCode)
        assertEquals(row.name, row.showApply, got.showApply)
        assertEquals(row.name, row.previewOnly, got.previewOnly)
        assertEquals(row.name, row.nextWeightKg, got.nextWeightKg, 0.0001)
        assertEquals(row.name, row.nextReps, got.nextReps)
        assertEquals(row.name, SetMicroRecCalculator.RULE_ID, got.trace.ruleId)
        assertTrue(row.name, got.trace.reasonCodes.contains(row.reason))
        if (row.previewOnly) {
            assertEquals("If you log this: …", SetMicroRecCopy.caption(got))
            assertFalse(got.showApply)
        }
    }

    data class Row(
        val name: String,
        val inputs: SetMicroRecInputs,
        val hidden: Boolean = false,
        val reason: String = "",
        val showApply: Boolean = true,
        val previewOnly: Boolean = false,
        val nextWeightKg: Double = 0.0,
        val nextReps: Int = 0,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Row> = listOf(
            Row("editing hidden", inputs(editing = true), hidden = true),
            Row(
                "lift done",
                inputs(
                    targetSets = 3,
                    workingLogged = 3,
                    working = listOf(set(100.0, 5, rpe = 8), set(100.0, 5, rpe = 8), set(100.0, 5, rpe = 8)),
                ),
                reason = SetMicroRecCalculator.LIFT_DONE,
                showApply = false,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row("no history", inputs(hint = null, targetWeightKg = null), hidden = true),
            Row(
                "first set from hint",
                inputs(hint = hint(suggested = 102.5), workingLogged = 0),
                reason = SetMicroRecCalculator.FIRST_SET,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
            Row(
                "warmup done",
                inputs(
                    hint = hint(suggested = 102.5),
                    workingLogged = 0,
                    lastAnySetWasWarmup = true,
                ),
                reason = SetMicroRecCalculator.WARMUP_DONE,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
            Row(
                "skip-RPE hold",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = null))),
                reason = SetMicroRecCalculator.SKIP_RPE_HOLD,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row(
                "skip-RPE drop",
                inputs(workingLogged = 1, working = listOf(set(100.0, 2, rpe = null))),
                reason = SetMicroRecCalculator.SKIP_RPE_DROP,
                nextWeightKg = 97.5,
                nextReps = 2,
            ),
            Row(
                "RPE 6-7 in-tank",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 6))),
                reason = SetMicroRecCalculator.IN_TANK,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
            Row(
                "RPE 8 quality",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 8))),
                reason = SetMicroRecCalculator.QUALITY,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row(
                "first 9-10 top",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 10))),
                reason = SetMicroRecCalculator.TOP_SET,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row(
                "two 9+ RPE_HOLD",
                inputs(
                    workingLogged = 2,
                    working = listOf(set(100.0, 5, rpe = 9), set(100.0, 5, rpe = 10)),
                ),
                reason = SetMicroRecCalculator.RPE_HOLD,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row(
                "close hold",
                inputs(workingLogged = 1, working = listOf(set(100.0, 4, rpe = 8))),
                reason = SetMicroRecCalculator.CLOSE_HOLD,
                nextWeightKg = 100.0,
                nextReps = 4,
            ),
            Row(
                "failed drop",
                inputs(workingLogged = 1, working = listOf(set(100.0, 2, rpe = 9))),
                reason = SetMicroRecCalculator.FAILED_DROP,
                nextWeightKg = 97.5,
                nextReps = 2,
            ),
            Row(
                "lighter hold",
                inputs(
                    lighterWeek = true,
                    workingLogged = 1,
                    working = listOf(set(100.0, 5, rpe = 6)),
                ),
                reason = SetMicroRecCalculator.LIGHTER_HOLD,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            Row(
                "bodyweight +1",
                inputs(
                    loadType = LoadType.BODYWEIGHT,
                    targetReps = 10,
                    workingLogged = 1,
                    working = listOf(set(0.0, 10, rpe = null)),
                    hint = hint(suggested = 0.0, lastReps = 10, targetReps = 10),
                ),
                reason = SetMicroRecCalculator.BW_ADD_REP,
                nextWeightKg = 0.0,
                nextReps = 11,
            ),
            Row(
                "bodyweight hold",
                inputs(
                    loadType = LoadType.BODYWEIGHT,
                    targetReps = 10,
                    workingLogged = 1,
                    working = listOf(set(0.0, 9, rpe = 8)),
                ),
                reason = SetMicroRecCalculator.BW_HOLD,
                nextWeightKg = 0.0,
                nextReps = 9,
            ),
            Row(
                "bodyweight -1",
                inputs(
                    loadType = LoadType.BODYWEIGHT,
                    targetReps = 10,
                    workingLogged = 1,
                    working = listOf(set(0.0, 6, rpe = 8)),
                ),
                reason = SetMicroRecCalculator.BW_DROP_REP,
                nextWeightKg = 0.0,
                nextReps = 5,
            ),
            Row(
                "preview only, no Use",
                inputs(
                    workingLogged = 1,
                    working = listOf(set(100.0, 5, rpe = 8)),
                    draftWeightKg = 100.0,
                    draftReps = 5,
                    draftRpe = 6,
                ),
                reason = SetMicroRecCalculator.IN_TANK,
                showApply = false,
                previewOnly = true,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
        )

        private fun inputs(
            editing: Boolean = false,
            loadType: LoadType? = LoadType.EXTERNAL,
            targetSets: Int = 3,
            targetReps: Int = 5,
            targetWeightKg: Double? = 100.0,
            workingLogged: Int = 0,
            working: List<LoggedSetView> = emptyList(),
            lastAnySetWasWarmup: Boolean = false,
            hint: ProgressionHint? = hint(),
            lighterWeek: Boolean = false,
            draftWeightKg: Double = 100.0,
            draftReps: Int = 5,
            draftRpe: Int? = null,
        ) = SetMicroRecInputs(
            editing = editing,
            loadType = loadType,
            unit = WeightUnit.KG,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeightKg = targetWeightKg,
            workingLogged = workingLogged,
            thisSessionWorking = working,
            lastAnySetWasWarmup = lastAnySetWasWarmup,
            hint = hint,
            lighterWeek = lighterWeek,
            draftWeightKg = draftWeightKg,
            draftReps = draftReps,
            draftRpe = draftRpe,
            nowMs = 1L,
            todayEpochDay = 10L,
        )

        private fun set(weightKg: Double, reps: Int, rpe: Int?) =
            LoggedSetView(weightKg = weightKg, reps = reps, rpe = rpe, isWarmup = false)

        private fun hint(
            suggested: Double = 102.5,
            lastReps: Int = 5,
            targetReps: Int = 5,
        ) = ProgressionHint(
            exerciseId = "squat",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = lastReps,
            targetReps = targetReps,
            suggestedWeightKg = suggested,
            action = ProgressionAction.INCREASE,
            loadType = LoadType.EXTERNAL,
        )
    }
}

class SetMicroRecCopyTest {
    @Test
    fun lineAndWhyStayHuman() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                SetMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = WeightUnit.KG,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    workingLogged = 1,
                    thisSessionWorking = listOf(
                        LoggedSetView(100.0, 5, 8, false),
                    ),
                    lastAnySetWasWarmup = false,
                    hint = null,
                    lighterWeek = false,
                    draftWeightKg = 100.0,
                    draftReps = 5,
                    draftRpe = null,
                ),
            ),
        )
        val line = SetMicroRecCopy.line(rec, LoadClass.LOADED, WeightUnit.KG)
        assertEquals("Next: 100 kg × 5 · RPE 8", line)
        val why = SetMicroRecCopy.whyLines(rec)
        assertTrue(why.any { it.contains("Quality set") })
        assertFalse(why.joinToString().contains("QUALITY"))
        assertFalse(why.joinToString().contains("nextWeightKg"))
        assertTrue(why.any { it.startsWith("Next weight (kg):") })
    }

    @Test
    fun assistMissAddsAssist() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                SetMicroRecInputs(
                    editing = false,
                    loadType = LoadType.ASSISTED,
                    unit = WeightUnit.KG,
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 20.0,
                    workingLogged = 1,
                    thisSessionWorking = listOf(
                        LoggedSetView(20.0, 4, 9, false),
                    ),
                    lastAnySetWasWarmup = false,
                    hint = null,
                    lighterWeek = false,
                    draftWeightKg = 20.0,
                    draftReps = 8,
                    draftRpe = null,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.FAILED_DROP, rec.reasonCode)
        assertEquals(22.5, rec.nextWeightKg, 0.0001)
    }
}
