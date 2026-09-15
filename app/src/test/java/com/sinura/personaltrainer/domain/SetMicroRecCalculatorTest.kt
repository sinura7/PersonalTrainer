package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetMicroRecCalculatorTest {
    @Test
    fun v1Rows() {
        for (row in cases()) {
            val rec = SetMicroRecCalculator.suggest(row.inputs)
            if (row.hidden) {
                assertNull(row.name, rec)
                continue
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
    }

    @Test
    fun extraSetPastThePlanStillSuggests() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    targetSets = 3,
                    workingLogged = 3,
                    working = listOf(
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 8),
                    ),
                    allowExtra = true,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.QUALITY, rec.reasonCode)
        assertTrue(rec.showApply)
        assertFalse(rec.previewOnly)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
    }

    @Test
    fun rpeIntentUsesLastWorkingNotTheDraftWells() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    workingLogged = 1,
                    working = listOf(set(100.0, 5, rpe = 8)),
                    draftWeightKg = 90.0,
                    draftReps = 3,
                    draftRpe = 6,
                    rpeIntent = true,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.IN_TANK, rec.reasonCode)
        assertTrue(rec.showApply)
        assertFalse(rec.previewOnly)
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
        assertEquals(6, rec.nextRpe)
        assertNull(SetMicroRecCopy.caption(rec))
    }

    @Test
    fun firstSetUsesThisLiftHistoryRpe() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    hint = hint(suggested = 102.5),
                    workingLogged = 0,
                    historyWorking = listOf(set(90.0, 12, rpe = 7)),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.FIRST_SET, rec.reasonCode)
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertEquals(7, rec.nextRpe)
    }

    @Test
    fun firstSetWithoutHistoryDoesNotInventAnRpe() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    hint = hint(suggested = 102.5),
                    workingLogged = 0,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.FIRST_SET, rec.reasonCode)
        assertEquals(null, rec.nextRpe)
    }

    @Test
    fun rpeIntentOnTheOpenerStaysTheFirstSetHint() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    hint = hint(suggested = 102.5),
                    workingLogged = 0,
                    draftRpe = 6,
                    rpeIntent = true,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.FIRST_SET, rec.reasonCode)
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
        assertFalse(rec.previewOnly)
    }

    @Test
    fun extraSetWithInTankRpeClimbs() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    targetSets = 3,
                    workingLogged = 3,
                    working = listOf(
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 7),
                    ),
                    draftRpe = 6,
                    rpeIntent = true,
                    allowExtra = true,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.IN_TANK, rec.reasonCode)
        assertEquals(102.5, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
        assertTrue(rec.showApply)
    }

    @Test
    fun twoRepsShortAlsoClimbsOneRepNotTheWeight() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(workingLogged = 1, working = listOf(set(100.0, 3, rpe = 8))),
            ),
        )
        assertEquals(SetMicroRecCalculator.CLIMB_REPS, rec.reasonCode)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
        assertEquals(4, rec.nextReps)
    }

    @Test
    fun climbCapsAtTheTarget() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(workingLogged = 1, working = listOf(set(100.0, 4, rpe = 7))),
            ),
        )
        assertEquals(SetMicroRecCalculator.CLIMB_REPS, rec.reasonCode)
        assertEquals(5, rec.nextReps)
    }

    @Test
    fun rpeHoldDoesNotClimbReps() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    workingLogged = 2,
                    working = listOf(set(100.0, 5, rpe = 9), set(100.0, 5, rpe = 10)),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.RPE_HOLD, rec.reasonCode)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
        assertEquals(5, rec.nextReps)
    }

    @Test
    fun lighterWeekDoesNotClimbReps() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    lighterWeek = true,
                    workingLogged = 1,
                    working = listOf(set(100.0, 4, rpe = 8)),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.LIGHTER_HOLD, rec.reasonCode)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
        assertEquals(4, rec.nextReps)
    }

    @Test
    fun assistedHoldClimbsRepsAtTheSameAssist() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    loadType = LoadType.ASSISTED,
                    targetReps = 8,
                    workingLogged = 1,
                    working = listOf(set(20.0, 7, rpe = 8)),
                    hint = hint(suggested = 20.0, lastReps = 7, targetReps = 8),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.CLIMB_REPS, rec.reasonCode)
        assertEquals(20.0, rec.nextWeightKg, 0.0001)
        assertEquals(8, rec.nextReps)
    }

    @Test
    fun aOneRepHoldPlaceholderDoesNotInventAClimb() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    loadType = LoadType.BODYWEIGHT,
                    targetReps = HoldWork.HOLD_REPS_PLACEHOLDER,
                    workingLogged = 1,
                    working = listOf(set(0.0, HoldWork.HOLD_REPS_PLACEHOLDER, rpe = 8)),
                    hint = hint(
                        suggested = 0.0,
                        lastReps = HoldWork.HOLD_REPS_PLACEHOLDER,
                        targetReps = HoldWork.HOLD_REPS_PLACEHOLDER,
                    ),
                ),
            ),
        )
        assertTrue(rec.reasonCode != SetMicroRecCalculator.CLIMB_REPS)
        assertEquals(0.0, rec.nextWeightKg, 0.0001)
        assertEquals(HoldWork.HOLD_REPS_PLACEHOLDER, rec.nextReps)
    }

    @Test
    fun climbRaisesEstimatedOneRepMax() {
        val before = checkNotNull(PersonalRecords.estimatedOneRepMaxKg(100.0, 4))
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(workingLogged = 1, working = listOf(set(100.0, 4, rpe = 8))),
            ),
        )
        val after = checkNotNull(PersonalRecords.estimatedOneRepMaxKg(rec.nextWeightKg, rec.nextReps))
        assertTrue(after > before)
        assertEquals(100.0, rec.nextWeightKg, 0.0001)
    }

    @Test
    fun nextRestFollowsTheLoggedReps() {
        val heavy = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(workingLogged = 1, working = listOf(set(100.0, 3, rpe = 8))),
            ),
        )
        val highRep = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    targetReps = 15,
                    workingLogged = 1,
                    working = listOf(set(40.0, 15, rpe = 8)),
                ),
            ),
        )
        assertTrue(heavy.restSeconds > highRep.restSeconds)
        assertEquals(
            RestPrescription.seconds(heavy.reasonCode, LoadType.EXTERNAL, heavy.nextReps),
            heavy.restSeconds,
        )
    }

    @Test
    fun easySetsInviteAnother() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    targetSets = 3,
                    workingLogged = 3,
                    working = listOf(
                        set(100.0, 5, rpe = 6),
                        set(100.0, 5, rpe = 7),
                        set(100.0, 5, rpe = 7),
                    ),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.LIFT_DONE, rec.reasonCode)
        assertTrue(rec.anotherSetAdvised)
        assertEquals(SetMicroRecCopy.ANOTHER_IN_YOU, SetMicroRecCopy.anotherSetLine(rec))
    }

    @Test
    fun grindingSetsDoNotInviteAnother() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                inputs(
                    targetSets = 3,
                    workingLogged = 3,
                    working = listOf(
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 8),
                        set(100.0, 5, rpe = 8),
                    ),
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.LIFT_DONE, rec.reasonCode)
        assertEquals(false, rec.anotherSetAdvised)
        assertEquals(null, SetMicroRecCopy.anotherSetLine(rec))
    }

    data class V1Case(
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
        fun cases(): List<V1Case> = listOf(
            V1Case("editing hidden", inputs(editing = true), hidden = true),
            V1Case(
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
            V1Case("no history", inputs(hint = null, targetWeightKg = null), hidden = true),
            V1Case(
                "first set from hint",
                inputs(hint = hint(suggested = 102.5), workingLogged = 0),
                reason = SetMicroRecCalculator.FIRST_SET,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
            V1Case(
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
            V1Case(
                "skip-RPE hold",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = null))),
                reason = SetMicroRecCalculator.SKIP_RPE_HOLD,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            V1Case(
                "skip-RPE drop",
                inputs(workingLogged = 1, working = listOf(set(100.0, 2, rpe = null))),
                reason = SetMicroRecCalculator.SKIP_RPE_DROP,
                nextWeightKg = 97.5,
                nextReps = 2,
            ),
            V1Case(
                "RPE 6-7 in-tank",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 6))),
                reason = SetMicroRecCalculator.IN_TANK,
                nextWeightKg = 102.5,
                nextReps = 5,
            ),
            V1Case(
                "RPE 8 quality",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 8))),
                reason = SetMicroRecCalculator.QUALITY,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            V1Case(
                "first 9-10 top",
                inputs(workingLogged = 1, working = listOf(set(100.0, 5, rpe = 10))),
                reason = SetMicroRecCalculator.TOP_SET,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            V1Case(
                "two 9+ RPE_HOLD",
                inputs(
                    workingLogged = 2,
                    working = listOf(set(100.0, 5, rpe = 9), set(100.0, 5, rpe = 10)),
                ),
                reason = SetMicroRecCalculator.RPE_HOLD,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            V1Case(
                "close hold climbs a rep",
                inputs(workingLogged = 1, working = listOf(set(100.0, 4, rpe = 8))),
                reason = SetMicroRecCalculator.CLIMB_REPS,
                nextWeightKg = 100.0,
                nextReps = 5,
            ),
            V1Case(
                "failed drop",
                inputs(workingLogged = 1, working = listOf(set(100.0, 2, rpe = 9))),
                reason = SetMicroRecCalculator.FAILED_DROP,
                nextWeightKg = 97.5,
                nextReps = 2,
            ),
            V1Case(
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
            V1Case(
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
            V1Case(
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
            V1Case(
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
            V1Case(
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
            allowExtra: Boolean = false,
            rpeIntent: Boolean = false,
            historyWorking: List<LoggedSetView> = emptyList(),
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
            allowExtra = allowExtra,
            rpeIntent = rpeIntent,
            historyWorking = historyWorking,
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
        assertEquals(
            ProgressionKickerCopy.BACK_OFF,
            SetMicroRecCopy.kicker(rec, LoadClass.BODYWEIGHT_ASSISTED, WeightUnit.KG),
        )
    }

    @Test
    fun qualitySetKickerIsThePlateStep() {
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
        assertEquals("+2.5", SetMicroRecCopy.kicker(rec, LoadClass.LOADED, WeightUnit.KG))
        assertEquals("+5", ProgressionKickerCopy.plusLabel(LoadClass.LOADED, WeightUnit.LBS))
        assertEquals(
            ProgressionKickerCopy.PLUS_REP,
            ProgressionKickerCopy.plusLabel(LoadClass.BODYWEIGHT, WeightUnit.KG),
        )
        assertEquals("100 kg × 5 · RPE 8", SetMicroRecCopy.payload(rec, LoadClass.LOADED, WeightUnit.KG))
    }
}
