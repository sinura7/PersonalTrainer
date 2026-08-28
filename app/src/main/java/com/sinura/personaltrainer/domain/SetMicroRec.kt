package com.sinura.personaltrainer.domain

/**
 * In-set next-load / next-reps. Local and deterministic (ADR-008).
 *
 * [ProgressionCalculator] +step is next *session*. A hit this session repeats
 * the load, except RPE 6–7 in-tank (loaded) and bodyweight +1. Never an LLM.
 */
data class LoggedSetView(
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
)

data class SetMicroRecInputs(
    val editing: Boolean,
    val loadType: LoadType?,
    val unit: WeightUnit,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val workingLogged: Int,
    val thisSessionWorking: List<LoggedSetView>,
    val lastAnySetWasWarmup: Boolean,
    val hint: ProgressionHint?,
    val lighterWeek: Boolean,
    val draftWeightKg: Double,
    val draftReps: Int,
    val draftRpe: Int?,
    val nowMs: Long = 0L,
    val todayEpochDay: Long = 0L,
)

data class SetMicroRec(
    val nextWeightKg: Double,
    val nextReps: Int,
    val nextRpe: Int?,
    val previewOnly: Boolean,
    val showApply: Boolean,
    val reasonCode: String,
    val trace: RuleTrace,
)

object SetMicroRecCalculator {
    const val RULE_ID = "micro-rec"

    const val EDITING = "EDITING"
    const val LIFT_DONE = "LIFT_DONE"
    const val NO_HISTORY = "NO_HISTORY"
    const val FIRST_SET = "FIRST_SET"
    const val WARMUP_DONE = "WARMUP_DONE"
    const val SKIP_RPE_HOLD = "SKIP_RPE_HOLD"
    const val SKIP_RPE_DROP = "SKIP_RPE_DROP"
    const val IN_TANK = "IN_TANK"
    const val QUALITY = "QUALITY"
    const val TOP_SET = "TOP_SET"
    const val RPE_HOLD = "RPE_HOLD"
    const val CLOSE_HOLD = "CLOSE_HOLD"
    const val FAILED_DROP = "FAILED_DROP"
    const val LIGHTER_HOLD = "LIGHTER_HOLD"
    const val BW_ADD_REP = "BW_ADD_REP"
    const val BW_HOLD = "BW_HOLD"
    const val BW_DROP_REP = "BW_DROP_REP"

    fun suggest(inputs: SetMicroRecInputs): SetMicroRec? {
        if (inputs.editing) return null
        val previewOnly = inputs.draftRpe != null
        val workingAfter = if (previewOnly) {
            inputs.workingLogged + 1
        } else {
            inputs.workingLogged
        }
        if (inputs.targetSets > 0 && inputs.workingLogged >= inputs.targetSets) {
            val last = inputs.thisSessionWorking.lastOrNull() ?: return hiddenDone(inputs)
            return rec(
                inputs = inputs,
                weight = last.weightKg,
                reps = last.reps,
                rpe = last.rpe,
                previewOnly = false,
                showApply = false,
                reason = LIFT_DONE,
            )
        }
        val loadClass = LoadClass.of(inputs.loadType)
        val stepKg = IncrementTable.stepKg(inputs.loadType ?: LoadType.EXTERNAL, inputs.unit)
            .takeUnless { loadClass == LoadClass.BODYWEIGHT }
        val meaning = loadClass.weightMeaning
        val bodyweight = stepKg == null || meaning == WeightMeaning.NONE

        if (previewOnly) {
            return fromBasis(
                inputs = inputs,
                basis = LoggedSetView(
                    weightKg = inputs.draftWeightKg,
                    reps = inputs.draftReps,
                    rpe = inputs.draftRpe,
                    isWarmup = false,
                ),
                workingIncludingBasis = workingAfter,
                previewOnly = true,
                bodyweight = bodyweight,
                stepKg = stepKg,
                meaning = meaning,
            )
        }

        val lastWorking = inputs.thisSessionWorking.lastOrNull()
        if (lastWorking == null) {
            return firstSet(inputs)
        }
        return fromBasis(
            inputs = inputs,
            basis = lastWorking,
            workingIncludingBasis = inputs.workingLogged,
            previewOnly = false,
            bodyweight = bodyweight,
            stepKg = stepKg,
            meaning = meaning,
        )
    }

    private fun firstSet(inputs: SetMicroRecInputs): SetMicroRec? {
        val hint = inputs.hint
        val weight = hint?.suggestedWeightKg
            ?: inputs.targetWeightKg
            ?: return null
        val reps = (hint?.targetReps ?: inputs.targetReps).coerceAtLeast(1)
        val reason = if (inputs.lastAnySetWasWarmup) WARMUP_DONE else FIRST_SET
        return rec(
            inputs = inputs,
            weight = weight.coerceAtLeast(0.0),
            reps = reps,
            rpe = null,
            previewOnly = false,
            showApply = true,
            reason = reason,
        )
    }

    private fun hiddenDone(inputs: SetMicroRecInputs): SetMicroRec =
        rec(
            inputs = inputs,
            weight = 0.0,
            reps = inputs.targetReps.coerceAtLeast(1),
            rpe = null,
            previewOnly = false,
            showApply = false,
            reason = LIFT_DONE,
        )

    private fun fromBasis(
        inputs: SetMicroRecInputs,
        basis: LoggedSetView,
        workingIncludingBasis: Int,
        previewOnly: Boolean,
        bodyweight: Boolean,
        stepKg: Double?,
        meaning: WeightMeaning,
    ): SetMicroRec {
        if (inputs.targetSets > 0 && workingIncludingBasis >= inputs.targetSets) {
            return rec(
                inputs = inputs,
                weight = basis.weightKg,
                reps = basis.reps,
                rpe = basis.rpe,
                previewOnly = previewOnly,
                showApply = false,
                reason = LIFT_DONE,
            )
        }
        val targetReps = inputs.targetReps.coerceAtLeast(1)
        val action = ProgressionCalculator.action(basis.reps, targetReps)
        val probe = ProgressionCalculator.hint(
            exerciseId = inputs.hint?.exerciseId.orEmpty(),
            exerciseName = inputs.hint?.exerciseName.orEmpty(),
            lastWeightKg = basis.weightKg,
            lastWorkingReps = basis.reps,
            targetReps = targetReps,
            stepKg = stepKg,
            loadType = inputs.loadType,
        )
        val rpes = buildList {
            addAll(inputs.thisSessionWorking.map { it.rpe })
            if (previewOnly) add(basis.rpe)
        }
        val recent = rpes.takeLast(RpeModifier.RPE_HOLD_SESSIONS).asReversed()
        val afterRpe = RpeModifier.apply(probe, recent)
        val afterLight = LighterWeekModifier.apply(afterRpe, inputs.lighterWeek)
        val rpe = basis.rpe
        val reason = reasonCode(
            action = action,
            rpe = rpe,
            rpeHold = afterRpe.rpeHold,
            lighterHold = afterLight.lighterHold,
            bodyweight = bodyweight,
        )
        val (weight, reps) = nextLoad(
            lastWeightKg = basis.weightKg,
            lastReps = basis.reps,
            targetReps = targetReps,
            stepKg = stepKg,
            meaning = meaning,
            bodyweight = bodyweight,
            reason = reason,
        )
        val showApply = !previewOnly && reason != LIFT_DONE
        return rec(
            inputs = inputs,
            weight = weight,
            reps = reps,
            rpe = suggestedRpe(reason, rpe),
            previewOnly = previewOnly,
            showApply = showApply,
            reason = reason,
            extraCodes = buildList {
                if (afterRpe.rpeHold) add(RPE_HOLD)
                if (afterLight.lighterHold) add(LIGHTER_HOLD)
            },
        )
    }

    internal fun reasonCode(
        action: ProgressionAction,
        rpe: Int?,
        rpeHold: Boolean,
        lighterHold: Boolean,
        bodyweight: Boolean,
    ): String {
        if (rpeHold) return RPE_HOLD
        if (lighterHold && action != ProgressionAction.DECREASE) return LIGHTER_HOLD
        if (action == ProgressionAction.DECREASE) {
            return if (rpe == null) SKIP_RPE_DROP else if (bodyweight) BW_DROP_REP else FAILED_DROP
        }
        if (action == ProgressionAction.HOLD) {
            return if (bodyweight) BW_HOLD else CLOSE_HOLD
        }
        // INCREASE: in-session loaded is repeat unless in-tank.
        if (rpe != null && rpe >= 9) return TOP_SET
        if (rpe in 6..7) return IN_TANK
        if (rpe == 8) return QUALITY
        if (bodyweight) return BW_ADD_REP
        return if (rpe == null) SKIP_RPE_HOLD else QUALITY
    }

    private fun nextLoad(
        lastWeightKg: Double,
        lastReps: Int,
        targetReps: Int,
        stepKg: Double?,
        meaning: WeightMeaning,
        bodyweight: Boolean,
        reason: String,
    ): Pair<Double, Int> {
        val climb = reason == IN_TANK || reason == BW_ADD_REP
        val drop = reason == FAILED_DROP || reason == SKIP_RPE_DROP || reason == BW_DROP_REP
        if (bodyweight) {
            val reps = when {
                climb -> lastReps + 1
                drop -> (lastReps - 1).coerceAtLeast(1)
                else -> lastReps
            }
            return 0.0 to reps
        }
        if (drop || climb) {
            val nextWeight = ProgressionCalculator.suggestWeightKg(
                lastWeightKg = lastWeightKg,
                lastWorkingReps = lastReps,
                targetReps = targetReps,
                stepKg = stepKg,
                weightMeaning = meaning,
            )
            return nextWeight to lastReps
        }
        return lastWeightKg to lastReps
    }

    private fun suggestedRpe(reason: String, lastRpe: Int?): Int? = when (reason) {
        IN_TANK, QUALITY, SKIP_RPE_HOLD, FIRST_SET, WARMUP_DONE -> 8
        TOP_SET, RPE_HOLD, CLOSE_HOLD, LIGHTER_HOLD, BW_HOLD, BW_ADD_REP -> 8
        FAILED_DROP, SKIP_RPE_DROP, BW_DROP_REP, LIFT_DONE -> lastRpe
        else -> lastRpe
    }

    private fun rec(
        inputs: SetMicroRecInputs,
        weight: Double,
        reps: Int,
        rpe: Int?,
        previewOnly: Boolean,
        showApply: Boolean,
        reason: String,
        extraCodes: List<String> = emptyList(),
    ): SetMicroRec {
        val codes = listOf(reason) + extraCodes.filter { it != reason }
        val trace = RuleTrace.forMicroRec(
            reasonCodes = codes,
            nextWeightKg = weight,
            nextReps = reps,
            nextRpe = rpe,
            nowMs = inputs.nowMs,
            todayEpochDay = inputs.todayEpochDay,
        )
        return SetMicroRec(
            nextWeightKg = weight,
            nextReps = reps,
            nextRpe = rpe,
            previewOnly = previewOnly,
            showApply = showApply && !previewOnly,
            reasonCode = reason,
            trace = trace,
        )
    }
}

fun setMicroRecInputs(
    editing: Boolean,
    loadType: LoadType?,
    unit: WeightUnit,
    targetSets: Int,
    targetReps: Int,
    targetWeightKg: Double?,
    working: List<LoggedSetView>,
    lastAnySetWasWarmup: Boolean,
    hint: ProgressionHint?,
    lighterWeek: Boolean,
    draftWeightKg: Double,
    draftReps: Int,
    draftRpe: Int?,
    nowMs: Long,
    todayEpochDay: Long,
): SetMicroRecInputs = SetMicroRecInputs(
    editing = editing,
    loadType = loadType,
    unit = unit,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    workingLogged = working.size,
    thisSessionWorking = working,
    lastAnySetWasWarmup = lastAnySetWasWarmup,
    hint = hint,
    lighterWeek = lighterWeek,
    draftWeightKg = draftWeightKg,
    draftReps = draftReps,
    draftRpe = draftRpe,
    nowMs = nowMs,
    todayEpochDay = todayEpochDay,
)

object SetMicroRecCopy {
    fun line(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String {
        val payload = buildString {
            append(SetCopy.setLine(rec.nextWeightKg, rec.nextReps, loadClass, unit))
            rec.nextRpe?.let { append(" · RPE $it") }
        }
        return if (rec.previewOnly) payload else "Next: $payload"
    }

    fun caption(rec: SetMicroRec): String? =
        if (rec.previewOnly) "If you log this: …" else null

    fun whyLines(rec: SetMicroRec): List<String> = RuleTraceCopy.lines(rec.trace)
}
