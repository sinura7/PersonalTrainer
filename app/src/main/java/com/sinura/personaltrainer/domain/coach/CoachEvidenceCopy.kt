package com.sinura.personaltrainer.domain.coach

/**
 * User-visible strings for literature citations on the gym floor.
 */
object CoachEvidenceCopy {
    fun chipLabel(entry: EvidenceEntry): String =
        buildString {
            append(entry.authorsShort)
            entry.year?.let { append(" ($it)") }
        }

    fun basedOnLine(suggestion: CoachSuggestion): String? {
        val primary = suggestion.primaryEvidence() ?: return heuristicOnlyLine(suggestion)
        return "Based on ${chipLabel(primary)}"
    }

    fun heuristicOnlyLine(suggestion: CoachSuggestion): String? {
        if (suggestion.heuristicEvidenceIds.isEmpty()) return null
        return "Includes Temper heuristics (no DOI)"
    }

    fun detailLines(suggestion: CoachSuggestion): List<String> {
        val resolved = EvidenceCatalog.resolve(suggestion.evidenceIds)
        if (resolved.isEmpty()) return emptyList()
        return resolved.flatMap { entry -> entryDetail(entry) }
    }

    fun whySheetAppendix(suggestion: CoachSuggestion): List<String> {
        val lines = mutableListOf<String>()
        lines += "Evidence"
        lines += detailLines(suggestion).ifEmpty { listOf("No curated sources for this rule yet.") }
        return lines
    }

    private fun entryDetail(entry: EvidenceEntry): List<String> = buildList {
        add(chipLabel(entry))
        add(entry.claim)
        when {
            entry.heuristic -> add("Heuristic — not peer-reviewed.")
            entry.doi != null -> add("DOI: ${entry.doi}")
            else -> add("No DOI on file — ${entry.title}.")
        }
    }
}
