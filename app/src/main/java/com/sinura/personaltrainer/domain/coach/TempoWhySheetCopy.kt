package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.ProgressionKickerCopy
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RuleTrace
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit

/**
 * Deterministic copy for Tempo's Why sheet (no LLM). Summaries explain policy in
 * plain language; decision rows use readable labels.
 */
object TempoWhySheetCopy {
    const val TITLE_NEXT = "Why Tempo suggests this"
    const val TITLE_ADD = "Why Tempo suggests an extra set"
    const val SECTION_DECISION = "How Tempo decided"
    const val SECTION_EVIDENCE = "Evidence"
    const val LABEL_LAST_SET = "Last working set"
    const val LABEL_RULE = "Rule applied"
    const val LABEL_PLAN_TARGET = "Plan rep target"
    const val LABEL_PLAN_TARGET_HINT = "Progression band on the lift card, not a cap on this set"
    const val LABEL_ALTERNATIVES = "Also considered"
    const val LABEL_REST = "Suggested rest"
    const val LABEL_ADD_EFFORT = "Effort"
    const val LABEL_ADD_COMPLETION = "Today's sets"
    const val LABEL_ADD_COMPARISON = "Last session"
    const val LABEL_ADD_WEEKLY = "Weekly volume"
    const val LABEL_ADD_TIMING = "Recent volume"
    const val OPENS_STUDY = "Opens study link in browser"
    const val HEURISTIC_BADGE = "Heuristic"

    data class Callout(
        val verb: String,
        val numbers: String,
        val restLabel: String?,
    )

    data class DecisionRow(
        val label: String,
        val value: String,
        val hint: String? = null,
    )

    data class SheetModel(
        val title: String,
        val callout: Callout,
        val summary: String,
        val decisionRows: List<DecisionRow>,
        val evidenceIds: List<String>,
    )

    fun forNextSet(
        rec: SetMicroRec,
        suggestion: CoachSuggestion,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): SheetModel {
        val trace = suggestion.trace
        val callRaw = trace.facts.firstOrNull { it.name == "call" }?.value
            ?: SetMicroRecCopy.callLine(rec.reasonCode, rec.nextWeightKg, rec.nextReps, loadClass, unit)
        val callout = parseCall(callRaw, rec, loadClass, unit)
        val lastSet = trace.facts.firstOrNull { it.name == "lastSet" }?.value
        val targetReps = trace.thresholds.firstOrNull { it.name == "targetReps" }?.value
        val ruleLine = rec.explanation?.takeIf { it.isNotBlank() } ?: suggestion.explanationShort
        return SheetModel(
            title = TITLE_NEXT,
            callout = callout,
            summary = summaryFor(rec.reasonCode, lastSet, targetReps, rec.nextReps, ruleLine),
            decisionRows = decisionRows(trace, ruleLine, targetReps),
            evidenceIds = suggestion.evidenceIds,
        )
    }

    fun forAddASet(
        tip: TempoCoachTip.AddASet,
        seedRec: SetMicroRec,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): SheetModel {
        val callRaw = tip.seedRec.trace.facts.firstOrNull { it.name == "call" }?.value
            ?: SetMicroRecCopy.callLine(
                seedRec.reasonCode,
                seedRec.nextWeightKg,
                seedRec.nextReps,
                loadClass,
                unit,
            )
        val callout = parseCall(callRaw, seedRec, loadClass, unit)
        val lastSet = tip.trace.facts.firstOrNull { it.name == "lastSet" }?.value
            ?: seedRec.trace.facts.firstOrNull { it.name == "lastSet" }?.value
        return SheetModel(
            title = TITLE_ADD,
            callout = callout.copy(
                verb = "Add 1 set",
                numbers = SetMicroRecCopy.numbers(seedRec, loadClass, unit),
            ),
            summary = tip.trace.fact("readinessCount")?.toIntOrNull()?.let { count ->
                "Your planned sets are complete. $count of 5 readiness checks support one extra set."
            } ?: tip.tipShort,
            decisionRows = addASetDecisionRows(tip.trace, lastSet),
            evidenceIds = tip.evidenceIds,
        )
    }

    private fun RuleTrace.fact(name: String): String? = facts.firstOrNull { it.name == name }?.value

    /** Present the frozen offer's actual signals, never re-evaluate the current draft. */
    private fun addASetDecisionRows(trace: RuleTrace, lastSet: String?): List<DecisionRow> = buildList {
        lastSet?.let { add(DecisionRow(LABEL_LAST_SET, it)) }
        trace.fact("meanRpe")?.toDoubleOrNull()?.let { mean ->
            val relation = if (trace.fact("effortSignal") == "true") "within" else "above"
            add(DecisionRow(LABEL_ADD_EFFORT, "Average RPE ${WeightConverter.formatDisplayNumber(mean)} · $relation the 7.5 guide."))
        }
        trace.fact("completionSignal")?.let { signal ->
            add(DecisionRow(LABEL_ADD_COMPLETION, if (signal == "true") {
                "No failed or cut-short sets flagged."
            } else {
                "A failed or cut-short set reduced support."
            }))
        }
        trace.fact("comparisonAvailable")?.let { available ->
            add(DecisionRow(LABEL_ADD_COMPARISON, when {
                available != "true" -> "No earlier session to compare."
                trace.fact("performanceSignal") == "true" -> "Compared sets held their weight and reps."
                else -> "Compared sets fell below last time."
            }))
        }
        trace.fact("weeklySets")?.toDoubleOrNull()?.let { weekly ->
            // Keep copy compact without rounding a below-guide value up to the guide.
            // The full evaluated estimate remains in the trace.
            val below = trace.fact("weeklyVolumeSignal") == "true"
            val rounded = WeightConverter.formatDisplayNumber(weekly)
            val estimate = if (below && rounded == "10") "just under 10" else rounded
            val relation = if (below) "below" else "at or above"
            add(DecisionRow(LABEL_ADD_WEEKLY, "Estimate $estimate sets · $relation the 10-set guide."))
        }
        trace.fact("trendOrBlockSignal")?.let {
            add(DecisionRow(LABEL_ADD_TIMING, when {
                trace.fact("trendSignal") == "true" ->
                    "Steady or lower across three weeks, including this week."
                trace.fact("blockSignal") == "true" &&
                    trace.fact("blockComparisonSource") == AddASetTrace.CURRENT_PLAN_TARGET ->
                    "Block week ${trace.fact("blockWeekIndex")} fits today's planned set count."
                trace.fact("blockSignal") == "true" ->
                    "Block week ${trace.fact("blockWeekIndex")} supports the set count."
                else -> "No recent-volume or block-week support."
            }))
        }
    }

    fun doiUrl(doi: String): String = "https://doi.org/${doi.trim()}"

    internal fun parseCall(
        callRaw: String,
        rec: SetMicroRec,
        loadClass: LoadClass,
        unit: WeightUnit,
    ): Callout {
        val numbers = SetMicroRecCopy.numbers(rec, loadClass, unit)
        val rest = rec.restSeconds.takeIf { it > 0 }?.let { seconds ->
            "Rest ${RestTimer.formatClock(seconds)}"
        }
        val verb = when {
            callRaw.startsWith("Hold ", ignoreCase = true) -> "Hold"
            callRaw.startsWith("Add a rep", ignoreCase = true) -> "Add a rep"
            callRaw.startsWith("Back off to ", ignoreCase = true) -> "Back off"
            else -> verbFromKicker(rec, loadClass, unit) ?: "Repeat"
        }
        val parsedNumbers = when {
            callRaw.startsWith("Hold ", ignoreCase = true) -> callRaw.removePrefix("Hold ").trim()
            callRaw.startsWith("Add a rep · ", ignoreCase = true) -> callRaw.substringAfter("·").trim()
            callRaw.startsWith("Back off to ", ignoreCase = true) -> callRaw.removePrefix("Back off to ").trim()
            else -> numbers
        }
        return Callout(
            verb = verb,
            numbers = parsedNumbers.ifBlank { numbers },
            restLabel = rest,
        )
    }

    private fun verbFromKicker(rec: SetMicroRec, loadClass: LoadClass, unit: WeightUnit): String? =
        when (ProgressionKickerCopy.fromMicroRec(rec, loadClass, unit)) {
            ProgressionKickerCopy.HOLD -> "Hold"
            ProgressionKickerCopy.BACK_OFF -> "Back off"
            ProgressionKickerCopy.PLUS_REP -> "Add a rep"
            null -> null
            else -> "Add weight"
        }

    internal fun decisionRows(
        trace: RuleTrace,
        ruleLine: String,
        targetReps: String?,
    ): List<DecisionRow> = buildList {
        trace.facts.firstOrNull { it.name == "lastSet" }?.value?.let { value ->
            add(DecisionRow(LABEL_LAST_SET, value))
        }
        add(DecisionRow(LABEL_RULE, ruleLine))
        targetReps?.let { value ->
            add(DecisionRow(LABEL_PLAN_TARGET, value, hint = LABEL_PLAN_TARGET_HINT))
        }
        if (trace.alternatives.isNotEmpty()) {
            add(DecisionRow(LABEL_ALTERNATIVES, trace.alternatives.joinToString(" · ")))
        }
    }

    internal fun summaryFor(
        reasonCode: String,
        lastSetLine: String?,
        targetReps: String?,
        nextReps: Int,
        ruleLine: String,
    ): String {
        val last = lastSetLine?.takeIf { it.isNotBlank() }
        val target = targetReps?.toIntOrNull()
        val overPlan = target != null && nextReps > target
        return when (reasonCode) {
            SetMicroRecCalculator.TOP_SET,
            SetMicroRecCalculator.RPE_HOLD,
            -> buildString {
                if (last != null) {
                    append("Your last set was $last — very hard effort. ")
                } else {
                    append("Your last set was near max effort. ")
                }
                append(
                    "Tempo keeps the same weight and reps so the next set stays productive " +
                        "instead of jumping load right after a grind.",
                )
                if (overPlan && target != null) {
                    append(
                        " The $target-rep target on your lift card is the progression band Tempo " +
                            "tracks, not a limit on how many reps to log this session.",
                    )
                }
            }
            SetMicroRecCalculator.QUALITY,
            SetMicroRecCalculator.CLOSE_HOLD,
            -> buildString {
                if (last != null) append("After $last, ") else append("After a solid set, ")
                append("Tempo holds the load so you can repeat with good form before adding weight.")
            }
            SetMicroRecCalculator.IN_TANK -> buildString {
                if (last != null) append("You had more in the tank on $last. ") else append("You had reps in reserve. ")
                append("Tempo adds a small weight step while keeping reps steady.")
            }
            SetMicroRecCalculator.CLIMB_REPS,
            SetMicroRecCalculator.BW_ADD_REP,
            -> buildString {
                if (last != null) append("You were close on $last. ") else append("You were close to the target. ")
                append("Tempo adds one rep before raising weight.")
            }
            SetMicroRecCalculator.FAILED_DROP,
            SetMicroRecCalculator.SKIP_RPE_DROP,
            SetMicroRecCalculator.BW_DROP_REP,
            -> buildString {
                append("The last set missed the plan. Tempo backs off load or reps to get you back on track.")
            }
            SetMicroRecCalculator.LIGHTER_HOLD -> buildString {
                append("This is a lighter week — Tempo holds load so you keep moving without extra stress.")
            }
            SetMicroRecCalculator.FIRST_SET,
            SetMicroRecCalculator.WARMUP_DONE,
            -> "Tempo starts from your plan or last time on this lift — nothing invented."
            else -> ruleLine.ifBlank { "Tempo applied the usual progression rules for this set." }
        }
    }
}
