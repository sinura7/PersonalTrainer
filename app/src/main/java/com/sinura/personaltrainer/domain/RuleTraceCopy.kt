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
