package com.sinura.personaltrainer.domain


/**
 * In-set next-load / next-reps. Local and deterministic (ADR-008).
 *
 * [ProgressionCalculator] +step is next *session*. A hit this session repeats
 * the load, except RPE 6–7 in-tank (loaded), a loaded 1–2-rep hold (add a
 * rep), and bodyweight +1. Never an LLM.
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
    /**
     * Lift is past the plan and the lifter asked for another set. Skip
     * [SetMicroRecCalculator.LIFT_DONE] so extras still get a next load.
     */
    val allowExtra: Boolean = false,
    /**
     * Selected RPE is the effort for *this* set, computed from last working
     * (or the first-set hint), not a preview of logging the draft wells.
     * Default false keeps the locked v1 "If you log this" rows.
     */
    val rpeIntent: Boolean = false,
    /**
     * Prior finished working sets of this lift, newest session last.
     * First-set Next / RPE chips read this instead of inventing 6–9.
     */
    val historyWorking: List<LoggedSetView> = emptyList(),
    val equipment: EquipmentType? = null,
)

data class SetMicroRec(
    val nextWeightKg: Double,
    val nextReps: Int,
    val nextRpe: Int?,
    val previewOnly: Boolean,
    val showApply: Boolean,
    val reasonCode: String,
    val trace: RuleTrace,
    /** Starting rest for the next clock. Not written onto the stored routine. */
    val restSeconds: Int = RestPrescription.STANDARD_SECONDS,
    /**
     * The Add-set row may invite one more. Never a change to [targetSets].
     * Stop-early ("that's enough") is not this field.
     */
    val anotherSetAdvised: Boolean = false,
    /**
     * Percentage ladder off the working weight. Empty once working sets
     * start. Never counted toward [targetSets].
     */
    val warmupSets: List<WarmupSet> = emptyList(),
    /**
     * Kit in hand, so the HOLD / +N kicker can use a pin stack's jump
     * rather than the barbell's. Null means the table keys off load class.
     */
    val equipment: EquipmentType? = null,
    val loadType: LoadType? = null,
    /**
     * The coach's own words for this call, the lifter's training goal included, when the
     * rec came from a coach call ([com.sinura.personaltrainer.domain.coach.CoachSuggestion.toMicroRec]).
     * The Why sheet's Rule line shows these ([SetMicroRecCopy.whyLines]); the card's two-line
     * reason keeps the goal-free rule so Target RPE always fits beside it.
     */
    val explanation: String? = null,
) {
    /**
     * True once the entry already holds this set exactly as Apply would write it, so the
     * control can read Applied: the same coercions as the ViewModel's applyMicroRec, the
     * weight compared at the unit's display precision, and the effort matched exactly
     * (Apply clears a chosen RPE when the suggestion carries none).
     */
    fun isApplied(weightKg: Double, reps: Int, rpe: Int?, unit: WeightUnit): Boolean =
        WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(weightKg, unit)) ==
            WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(nextWeightKg.coerceAtLeast(0.0), unit)) &&
            reps == nextReps.coerceAtLeast(1) &&
            rpe == nextRpe
}

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
    const val CLIMB_REPS = "CLIMB_REPS"
    const val FAILED_DROP = "FAILED_DROP"
    const val LIGHTER_HOLD = "LIGHTER_HOLD"
    const val BW_ADD_REP = "BW_ADD_REP"
    const val BW_HOLD = "BW_HOLD"
    const val BW_DROP_REP = "BW_DROP_REP"
    const val ANOTHER_SET_RPE_CEILING = 7

    fun suggest(inputs: SetMicroRecInputs): SetMicroRec? {
        if (inputs.editing) return null
        val previewOnly = inputs.draftRpe != null && !inputs.rpeIntent
        val workingAfter = if (previewOnly) {
            inputs.workingLogged + 1
        } else {
            inputs.workingLogged
        }
        if (
            inputs.targetSets > 0 &&
            inputs.workingLogged >= inputs.targetSets &&
            !inputs.allowExtra
        ) {
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
        val displayStep = IncrementTable.displayStep(
            inputs.loadType ?: LoadType.EXTERNAL,
            inputs.unit,
            inputs.equipment,
        )
            .takeUnless { loadClass == LoadClass.BODYWEIGHT }
        val meaning = loadClass.weightMeaning
        val bodyweight = displayStep == null || meaning == WeightMeaning.NONE

        val intentRpe = inputs.draftRpe.takeIf { inputs.rpeIntent }
        if (intentRpe != null) {
            val lastWorking = inputs.thisSessionWorking.lastOrNull() ?: return firstSet(inputs)
            return fromBasis(
                inputs = inputs,
                basis = lastWorking.copy(rpe = intentRpe),
                workingIncludingBasis = inputs.workingLogged,
                previewOnly = false,
                bodyweight = bodyweight,
                displayStep = displayStep,
                meaning = meaning,
            )
        }

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
                displayStep = displayStep,
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
            displayStep = displayStep,
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
            rpe = historyRpe(inputs),
            previewOnly = false,
            showApply = true,
            reason = reason,
        )
    }

    private fun historyRpe(inputs: SetMicroRecInputs): Int? =
        inputs.historyWorking.lastOrNull { it.rpe != null }?.rpe
            ?: inputs.historyWorking.lastOrNull()?.rpe

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
        displayStep: Double?,
        meaning: WeightMeaning,
    ): SetMicroRec {
        if (
            !inputs.allowExtra &&
            inputs.targetSets > 0 &&
            workingIncludingBasis >= inputs.targetSets
        ) {
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
        val rpes = buildList {
            addAll(inputs.thisSessionWorking.map { it.rpe })
            if (previewOnly) add(basis.rpe)
        }
        val afterLight = ProgressionCalculator.adjusted(
            exerciseId = inputs.hint?.exerciseId.orEmpty(),
            exerciseName = inputs.hint?.exerciseName.orEmpty(),
            lastWeightKg = basis.weightKg,
            lastWorkingReps = basis.reps,
            targetReps = targetReps,
            loadType = inputs.loadType,
            unit = inputs.unit,
            rpeEvidenceNewestFirst = rpes.takeLast(RpeModifier.RPE_HOLD_SESSIONS).asReversed(),
            lighterWeek = inputs.lighterWeek,
            equipment = inputs.equipment,
        )
        val effortRpe = basis.rpe
        val reason = reasonCode(
            action = action,
            rpe = effortRpe,
            rpeHold = afterLight.rpeHold,
            lighterHold = afterLight.lighterHold,
            bodyweight = bodyweight,
        )
        val (weight, reps) = nextLoad(
            lastWeightKg = basis.weightKg,
            lastReps = basis.reps,
            targetReps = targetReps,
            displayStep = displayStep,
            meaning = meaning,
            bodyweight = bodyweight,
            reason = reason,
            unit = inputs.unit,
        )
        val showApply = !previewOnly && reason != LIFT_DONE
        return rec(
            inputs = inputs,
            weight = weight,
            reps = reps,
            rpe = effortRpe ?: historyRpe(inputs),
            previewOnly = previewOnly,
            showApply = showApply,
            reason = reason,
            extraCodes = buildList {
                if (afterLight.rpeHold) add(RPE_HOLD)
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
            return if (bodyweight) BW_HOLD else CLIMB_REPS
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
        displayStep: Double?,
        meaning: WeightMeaning,
        bodyweight: Boolean,
        reason: String,
        unit: WeightUnit,
    ): Pair<Double, Int> {
        val climb = reason == IN_TANK || reason == BW_ADD_REP
        val drop = reason == FAILED_DROP || reason == SKIP_RPE_DROP || reason == BW_DROP_REP
        if (reason == CLIMB_REPS) {
            return lastWeightKg to (lastReps + 1).coerceAtMost(targetReps.coerceAtLeast(1))
        }
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
                displayStep = displayStep,
                weightMeaning = meaning,
                unit = unit,
            )
            return nextWeight to lastReps
        }
        return lastWeightKg to lastReps
    }

    /**
     * Last working sets all came in at or under [ANOTHER_SET_RPE_CEILING].
     * Missing RPE is not evidence of an easy set.
     */
    internal fun adviseAnother(inputs: SetMicroRecInputs): Boolean {
        if (inputs.lighterWeek) return false
        val working = inputs.thisSessionWorking
        if (working.isEmpty()) return false
        if (working.any { it.rpe == null }) return false
        return working.all { (it.rpe ?: 99) <= ANOTHER_SET_RPE_CEILING }
    }

    internal fun alternativesFor(reason: String): List<String> {
        val addRep = SetMicroRecCopy.ALT_ADD_REP
        val addWeight = SetMicroRecCopy.ALT_ADD_WEIGHT
        val backOff = SetMicroRecCopy.ALT_BACK_OFF
        return when (reason) {
            CLIMB_REPS, BW_ADD_REP -> listOf(addWeight, backOff)
            IN_TANK -> listOf(addRep, backOff)
            FAILED_DROP, SKIP_RPE_DROP, BW_DROP_REP -> listOf(addRep, addWeight)
            LIFT_DONE, EDITING, FIRST_SET, WARMUP_DONE, NO_HISTORY -> emptyList()
            else -> listOf(addRep, addWeight, backOff)
        }
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
        val restSeconds = RestPrescription.seconds(
            reasonCode = reason,
            loadType = inputs.loadType,
            reps = reps,
        )
        val loadClass = LoadClass.of(inputs.loadType)
        val last = inputs.thisSessionWorking.lastOrNull()
        val lastLine = last?.let { set ->
            buildString {
                append(SetCopy.setLine(set.weightKg, set.reps, loadClass, inputs.unit))
                set.rpe?.let { append(" at RPE $it") }
            }
        }
        val increment = inputs.loadType?.let { load ->
            IncrementTable.stepLabel(load, inputs.unit, inputs.equipment)
        }
        val trace = RuleTrace.forMicroRec(
            reasonCodes = codes,
            nextWeightKg = weight,
            nextReps = reps,
            nextRpe = rpe,
            nowMs = inputs.nowMs,
            todayEpochDay = inputs.todayEpochDay,
            lastWeightKg = last?.weightKg,
            lastReps = last?.reps,
            lastRpe = last?.rpe,
            lastSetLine = lastLine,
            incrementLabel = increment,
            targetReps = inputs.targetReps.takeIf { it > 0 },
            targetSets = inputs.targetSets.takeIf { it > 0 },
            restSeconds = restSeconds,
            call = SetMicroRecCopy.callLine(reason, weight, reps, loadClass, inputs.unit),
            rule = SetMicroRecCopy.ruleLine(reason),
            alternatives = alternativesFor(reason),
        )
        return SetMicroRec(
            nextWeightKg = weight,
            nextReps = reps,
            nextRpe = rpe,
            previewOnly = previewOnly,
            showApply = showApply && !previewOnly,
            reasonCode = reason,
            trace = trace,
            restSeconds = restSeconds,
            anotherSetAdvised = reason == LIFT_DONE && adviseAnother(inputs),
            warmupSets = warmupSetsFor(inputs, reason, weight),
            equipment = inputs.equipment,
            loadType = inputs.loadType,
        )
    }

    private fun warmupSetsFor(
        inputs: SetMicroRecInputs,
        reason: String,
        workingWeightKg: Double,
    ): List<WarmupSet> {
        if (reason != FIRST_SET && reason != WARMUP_DONE) return emptyList()
        return WarmupRamp.sets(
            workingWeightKg = workingWeightKg,
            loadType = inputs.loadType,
            unit = inputs.unit,
            equipment = inputs.equipment,
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
    allowExtra: Boolean = false,
    rpeIntent: Boolean = false,
    historyWorking: List<LoggedSetView> = emptyList(),
    equipment: EquipmentType? = null,
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
    allowExtra = allowExtra,
    rpeIntent = rpeIntent,
    historyWorking = historyWorking,
    equipment = equipment,
)

object SetMicroRecCopy {
    const val USE_SUGGESTION = "Use suggestion"
    const val KEEP_MY_NUMBERS = "Keep my numbers"
    const val ALT_ADD_REP = "Add a rep"
    const val ALT_ADD_WEIGHT = "Add weight"
    const val ALT_BACK_OFF = "Back off"

    fun payload(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String {
        return buildString {
            append(SetCopy.setLine(rec.nextWeightKg, rec.nextReps, loadClass, unit))
            rec.nextRpe?.let { append(" · RPE $it") }
        }
    }

    fun numbers(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String =
        SetCopy.setLine(rec.nextWeightKg, rec.nextReps, loadClass, unit)

    fun visibleOnEntry(rec: SetMicroRec): Boolean =
        rec.reasonCode != SetMicroRecCalculator.LIFT_DONE &&
            rec.reasonCode != SetMicroRecCalculator.EDITING

    fun callLine(
        reason: String,
        weightKg: Double,
        reps: Int,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): String {
        val numbers = SetCopy.setLine(weightKg, reps, loadClass, unit)
        return when (reason) {
            SetMicroRecCalculator.RPE_HOLD,
            SetMicroRecCalculator.CLOSE_HOLD,
            SetMicroRecCalculator.LIGHTER_HOLD,
            SetMicroRecCalculator.BW_HOLD,
            SetMicroRecCalculator.SKIP_RPE_HOLD,
            SetMicroRecCalculator.QUALITY,
            SetMicroRecCalculator.TOP_SET,
            -> "Hold $numbers"
            SetMicroRecCalculator.CLIMB_REPS,
            SetMicroRecCalculator.BW_ADD_REP,
            -> "Add a rep · $numbers"
            SetMicroRecCalculator.FAILED_DROP,
            SetMicroRecCalculator.SKIP_RPE_DROP,
            SetMicroRecCalculator.BW_DROP_REP,
            -> "Back off to $numbers"
            else -> numbers
        }
    }

    /**
     * Why the next set is what it is, in one line: what happened, then what to do.
     *
     * Every line takes that shape, so after a few sessions the eye knows where to look
     * without reading the whole thing — the same sentence in the same place every set. The
     * reason codes and the rule that picks them are untouched; only the words are here.
     */
    fun ruleLine(reason: String): String = when (reason) {
        SetMicroRecCalculator.TOP_SET,
        SetMicroRecCalculator.RPE_HOLD,
        -> "Hard set — hold the weight"
        SetMicroRecCalculator.QUALITY -> "Clean set — hold the weight"
        SetMicroRecCalculator.IN_TANK -> "Had more in you — add weight"
        SetMicroRecCalculator.CLIMB_REPS,
        SetMicroRecCalculator.BW_ADD_REP,
        -> "Close — add a rep"
        SetMicroRecCalculator.FAILED_DROP,
        SetMicroRecCalculator.SKIP_RPE_DROP,
        SetMicroRecCalculator.BW_DROP_REP,
        -> "Missed target — go lighter"
        SetMicroRecCalculator.CLOSE_HOLD -> "Close — hold the weight"
        else -> RuleTraceCopy.reasonLabel(reason)
    }

    fun line(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String {
        val payload = payload(rec, loadClass, unit)
        return if (rec.previewOnly) payload else "Next: $payload"
    }

    /**
     * Packet 4: the Next row shows this instead of a watermark. Null when
     * the rec is not a load call (editing, lift done).
     */
    fun kicker(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String? =
        ProgressionKickerCopy.fromMicroRec(rec, loadClass, unit)

    fun caption(rec: SetMicroRec): String? =
        if (rec.previewOnly) "If you log this: …" else null

    /**
     * The change in words for the Next-set card: `+1 rep`, `+5 lbs`, `Hold the load`,
     * `Back off`. Null when the rec is not a load call.
     */
    fun deltaLine(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String? {
        val code = rec.reasonCode
        // These repeat last set's numbers, whatever the kicker's step label would say.
        if (code == SetMicroRecCalculator.QUALITY || code == SetMicroRecCalculator.TOP_SET) return "Hold the load"
        // A first set has nothing to move from.
        if (code == SetMicroRecCalculator.FIRST_SET || code == SetMicroRecCalculator.WARMUP_DONE) return null
        val kicker = kicker(rec, loadClass, unit) ?: return null
        return DELTA_WORDS[kicker] ?: kicker
    }

    /** The kicker's shorthand said in words; a step label such as `+5` stands as it is. */
    private val DELTA_WORDS = mapOf(
        ProgressionKickerCopy.PLUS_REP to "+1 rep",
        ProgressionKickerCopy.HOLD to "Hold the load",
        ProgressionKickerCopy.BACK_OFF to "Back off",
    )

    /**
     * The Why sheet. Its Rule line carries the coach's own words for this call
     * ([SetMicroRec.explanation], the training goal included), which the card's two-line
     * reason has no room for.
     */
    fun whyLines(rec: SetMicroRec): List<String> = RuleTraceCopy.whySheet(rec.trace, ruleLine = rec.explanation)
        .ifEmpty { RuleTraceCopy.lines(rec.trace) }

    fun anotherSetLine(rec: SetMicroRec): String? =
        if (rec.anotherSetAdvised) ANOTHER_IN_YOU else null

    fun warmupLine(rec: SetMicroRec, unit: WeightUnit): String? {
        if (rec.warmupSets.isEmpty()) return null
        val numbers = rec.warmupSets.joinToString(" · ") { set ->
            WeightConverter.formatDisplayNumber(
                WeightConverter.toDisplayValue(set.weightKg, unit),
            )
        }
        return "Warm up $numbers ${unit.suffix}"
    }

    const val ANOTHER_IN_YOU = "You have another in you"
}

/**
 * The three floor verbs for what to do with the load: hold it, add the
 * plate step, or back off. Sourced from the in-set rec, which already ran
 * [RpeModifier].
 */
object ProgressionKickerCopy {
    const val HOLD = "HOLD"
    const val BACK_OFF = "BACK OFF"
    const val PLUS_REP = "+1"

    private val HOLD_CODES = setOf(
        SetMicroRecCalculator.RPE_HOLD,
        SetMicroRecCalculator.CLOSE_HOLD,
        SetMicroRecCalculator.LIGHTER_HOLD,
        SetMicroRecCalculator.BW_HOLD,
        SetMicroRecCalculator.SKIP_RPE_HOLD,
    )

    private val BACK_OFF_CODES = setOf(
        SetMicroRecCalculator.FAILED_DROP,
        SetMicroRecCalculator.SKIP_RPE_DROP,
        SetMicroRecCalculator.BW_DROP_REP,
    )

    fun fromMicroRec(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String? {
        if (rec.reasonCode == SetMicroRecCalculator.EDITING ||
            rec.reasonCode == SetMicroRecCalculator.LIFT_DONE
        ) {
            return null
        }
        if (rec.reasonCode == SetMicroRecCalculator.CLIMB_REPS ||
            rec.reasonCode == SetMicroRecCalculator.BW_ADD_REP
        ) {
            return PLUS_REP
        }
        if (rec.reasonCode in HOLD_CODES) return HOLD
        if (rec.reasonCode in BACK_OFF_CODES) return BACK_OFF
        return plusLabel(
            loadClass = loadClass,
            unit = unit,
            equipment = rec.equipment,
            loadType = rec.loadType,
        )
    }

    fun plusLabel(
        loadClass: LoadClass,
        unit: WeightUnit,
        equipment: EquipmentType? = null,
        loadType: LoadType? = null,
    ): String {
        val resolved = loadType ?: when (loadClass) {
            LoadClass.LOADED -> LoadType.EXTERNAL
            LoadClass.BODYWEIGHT -> LoadType.BODYWEIGHT
            LoadClass.BODYWEIGHT_ADDED -> LoadType.BODYWEIGHT_PLUS
            LoadClass.BODYWEIGHT_ASSISTED -> LoadType.ASSISTED
        }
        val step = IncrementTable.displayStep(resolved, unit, equipment) ?: return PLUS_REP
        return "+${WeightConverter.formatDisplayNumber(step)}"
    }
}
