package com.sinura.personaltrainer.domain

/**
 * Gym-floor labels for [RuleTrace] (ADR-008). Machine keys stay in the
 * object; Why never prints them. No LLM prose.
 */
object RuleTraceCopy {
    fun lines(trace: RuleTrace): List<String> = buildList {
        evidenceLine(trace)?.let(::add)
        val codes = trace.reasonCodes.map(::reasonLabel).filter { it.isNotBlank() }
        if (codes.isNotEmpty()) add(codes.joinToString(" · "))
        trace.facts
            .filterNot { it.name == "title" }
            .forEach { fact -> add("${factLabel(fact.name)}: ${fact.value}") }
        trace.thresholds.forEach { threshold ->
            add("${thresholdLabel(threshold.name)}: ${threshold.value}")
        }
        if (trace.alternatives.isNotEmpty()) {
            add("Alternatives considered: ${trace.alternatives.joinToString(", ")}")
        }
    }

    /**
     * Why sheet order (report §5.12): Call, Evidence, Rule, Threshold, Rest,
     * Alternatives. Actions live on the dialog, not in these lines.
     */
    /**
     * The Why sheet's lines for [trace]. [ruleLine], when given, is the coach's own words for
     * the rule (the lifter's training goal included) in place of the goal-free rule the trace
     * recorded.
     */
    fun whySheet(trace: RuleTrace, ruleLine: String? = null): List<String> = buildList {
        factValue(trace, "call")?.let { add("Call: $it") }
        factValue(trace, "lastSet")?.let { add("Evidence: Last set: $it") }
        val rule = ruleLine?.takeIf { it.isNotBlank() }
            ?: factValue(trace, "rule")
            ?: reasonLabel(trace.reasonCodes.firstOrNull().orEmpty())
        if (rule.isNotBlank()) add("Rule: $rule")
        trace.thresholds.firstOrNull()?.let { threshold ->
            add("Threshold: ${thresholdLabel(threshold.name)}: ${threshold.value}")
        }
        factValue(trace, "rest")?.let { add("Rest: $it") }
        if (trace.alternatives.isNotEmpty()) {
            add("Alternatives considered: ${trace.alternatives.joinToString(", ")}")
        }
    }

    private fun factValue(trace: RuleTrace, name: String): String? =
        trace.facts.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() }

    fun reasonLabel(code: String): String = when (code) {
        RecommendationEngine.KICKER_BALANCE -> "Balance"
        RecommendationEngine.KICKER_COVERAGE -> "Coverage"
        RecommendationEngine.KICKER_PROGRESSION -> "Progression"
        RecommendationEngine.KICKER_RECOVERY -> "Recovery"
        RecommendationEngine.KICKER_LOAD -> "Load"
        "RPE_HOLD" -> "Held for RPE"
        "LIGHTER_HOLD" -> "Held for a lighter week"
        SetMicroRecCalculator.IN_TANK -> "In the tank"
        SetMicroRecCalculator.QUALITY -> "Quality set"
        SetMicroRecCalculator.TOP_SET -> "Top set"
        SetMicroRecCalculator.SKIP_RPE_HOLD -> "No RPE · hold"
        SetMicroRecCalculator.SKIP_RPE_DROP -> "No RPE · drop"
        SetMicroRecCalculator.CLOSE_HOLD -> "Close. Hold."
        SetMicroRecCalculator.CLIMB_REPS -> "Add a rep"
        RuleTrace.STALL -> "No progress"
        RuleTrace.VOLUME_RAMP -> "More volume"
        SetMicroRecCalculator.FAILED_DROP -> "Missed target"
        SetMicroRecCalculator.LIFT_DONE -> "This lift is done"
        SetMicroRecCalculator.FIRST_SET -> "First set"
        SetMicroRecCalculator.WARMUP_DONE -> "Warm-up done"
        SetMicroRecCalculator.BW_ADD_REP -> "Add a rep"
        SetMicroRecCalculator.BW_HOLD -> "Hold reps"
        SetMicroRecCalculator.BW_DROP_REP -> "Drop a rep"
        ProgressionAction.INCREASE.name -> "Increase"
        ProgressionAction.HOLD.name -> "Hold"
        ProgressionAction.DECREASE.name -> "Decrease"
        else -> humanizeKey(code)
    }

    fun factLabel(name: String): String = when (name) {
        "title" -> "Call"
        "reason" -> "Because"
        "exercise" -> "Lift"
        "muscle" -> "Muscle"
        "lastWeightKg" -> "Last weight (kg)"
        "suggestedWeightKg" -> "Suggested weight (kg)"
        "lastReps" -> "Last reps"
        "suggestedReps" -> "Suggested reps"
        "sessionsHeld" -> "Sessions held"
        "lastWeekSets" -> "Last week sets"
        "suggestedSets" -> "Suggested sets"
        "nextWeightKg" -> "Next weight (kg)"
        "nextReps" -> "Next reps"
        "nextRpe" -> "Next RPE"
        "lastRpe" -> "Last RPE"
        "lastSet" -> "Last set"
        "increment" -> "Increment"
        "rest" -> "Rest"
        "call" -> "Call"
        "rule" -> "Rule"
        else -> humanizeKey(name)
    }

    fun thresholdLabel(name: String): String = when (name) {
        "targetReps" -> "Target reps"
        "stallSessions" -> "Stall after"
        "rpeCeiling" -> "RPE ceiling"
        "addSets" -> "Add sets"
        "highMinSets" -> "High band"
        "targetSets" -> "Target sets"
        "increment" -> "Increment"
        "rpeHold" -> "Hold at average RPE"
        else -> humanizeKey(name)
    }

    fun evidenceLine(trace: RuleTrace): String? {
        val span = trace.evidenceEndEpochDay - trace.evidenceStartEpochDay
        if (span < 0L) return null
        val days = span + 1L
        return if (days <= 1L) "Looked at one day of training." else "Looked at $days days of training."
    }

    private fun humanizeKey(raw: String): String {
        val spaced = raw.replace('_', ' ').lowercase()
        return spaced.replaceFirstChar { it.titlecase() }
    }
}
