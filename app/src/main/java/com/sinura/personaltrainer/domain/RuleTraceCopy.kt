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
    }

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
        "lastWeightKg" -> "Last weight (kg)"
        "suggestedWeightKg" -> "Suggested weight (kg)"
        "lastReps" -> "Last reps"
        "nextWeightKg" -> "Next weight (kg)"
        "nextReps" -> "Next reps"
        "nextRpe" -> "Next RPE"
        else -> humanizeKey(name)
    }

    fun thresholdLabel(name: String): String = when (name) {
        "targetReps" -> "Target reps"
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
