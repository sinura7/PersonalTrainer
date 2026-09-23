package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.Coach
import com.sinura.personaltrainer.domain.CoachDecision
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.RuleTrace
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.SetMicroRecInputs
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WarmupSet
/**
 * Named in-workout coach façade (ADR-029). Delegates math to [Coach.decide];
 * attaches literature evidence and a short explanation.
 */
data class CoachSuggestion(
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val restSeconds: Int,
    val reasonCode: String,
    val explanationShort: String,
    val evidenceIds: List<String>,
    val trace: RuleTrace,
    val previewOnly: Boolean,
    val showApply: Boolean,
    val anotherSetAdvised: Boolean,
    val warmupSets: List<WarmupSet>,
) {
    val literatureEvidenceIds: List<String>
        get() = evidenceIds.filter { id ->
            EvidenceCatalog.byId(id)?.heuristic == false
        }

    val heuristicEvidenceIds: List<String>
        get() = evidenceIds.filter { id ->
            EvidenceCatalog.byId(id)?.heuristic == true
        }

    fun toMicroRec(): SetMicroRec = SetMicroRec(
        nextWeightKg = weightKg,
        nextReps = reps,
        nextRpe = rpe,
        previewOnly = previewOnly,
        showApply = showApply,
        reasonCode = reasonCode,
        trace = trace,
        restSeconds = restSeconds,
        anotherSetAdvised = anotherSetAdvised,
        warmupSets = warmupSets,
        explanation = explanationShort,
    )

    fun primaryEvidence(): EvidenceEntry? =
        EvidenceCatalog.resolve(evidenceIds).firstOrNull { !it.heuristic }
            ?: EvidenceCatalog.resolve(evidenceIds).firstOrNull()
}

object CoachEngine {
    const val VERSION = 1

    fun suggest(
        inputs: SetMicroRecInputs,
        prefs: CoachPreferences = CoachPreferences.DEFAULT,
    ): CoachSuggestion? {
        val decision = Coach.decide(inputs) ?: return null
        return enrich(decision, prefs)
    }

    fun enrich(
        decision: CoachDecision,
        prefs: CoachPreferences = CoachPreferences.DEFAULT,
    ): CoachSuggestion = fromDecision(decision, prefs)

    fun fromMicroRec(
        rec: SetMicroRec,
        prefs: CoachPreferences = CoachPreferences.DEFAULT,
    ): CoachSuggestion {
        val decision = CoachDecision(
            weightKg = rec.nextWeightKg,
            reps = rec.nextReps,
            rpe = rec.nextRpe,
            restSeconds = rec.restSeconds,
            anotherSetAdvised = rec.anotherSetAdvised,
            reasonCode = rec.reasonCode,
            trace = rec.trace,
            previewOnly = rec.previewOnly,
            showApply = rec.showApply,
            warmupSets = rec.warmupSets,
            equipment = rec.equipment,
            loadType = rec.loadType,
        )
        val suggestion = fromDecision(decision, prefs)
        // A rec made by a coach call keeps that call's words, goal included; rebuilding them
        // here from [prefs] (DEFAULT on the card) is what dropped the Settings goal (C-1).
        return rec.explanation?.let { suggestion.copy(explanationShort = it) } ?: suggestion
    }

    private fun fromDecision(
        decision: CoachDecision,
        prefs: CoachPreferences,
    ): CoachSuggestion {
        val evidenceIds = CoachPolicyEvidence.evidenceIdsForReason(decision.reasonCode)
        val explanation = explanationShort(decision.reasonCode, prefs.goal)
        return CoachSuggestion(
            weightKg = decision.weightKg,
            reps = decision.reps,
            rpe = decision.rpe,
            restSeconds = decision.restSeconds,
            reasonCode = decision.reasonCode,
            explanationShort = explanation,
            evidenceIds = evidenceIds,
            trace = decision.trace,
            previewOnly = decision.previewOnly,
            showApply = decision.showApply,
            anotherSetAdvised = decision.anotherSetAdvised,
            warmupSets = decision.warmupSets,
        )
    }

    internal fun explanationShort(reasonCode: String, goal: TrainingGoal): String {
        val base = SetMicroRecCopy.ruleLine(reasonCode)
        return when {
            reasonCode == SetMicroRecCalculator.IN_TANK && goal == TrainingGoal.STRENGTH ->
                "$base · strength bias keeps reps before big jumps"
            reasonCode == SetMicroRecCalculator.CLIMB_REPS && goal == TrainingGoal.HYPERTROPHY ->
                "$base · volume-first for muscle goal"
            else -> base
        }
    }
}

fun CoachDecision.toCoachSuggestion(
    prefs: CoachPreferences = CoachPreferences.DEFAULT,
): CoachSuggestion = CoachEngine.enrich(this, prefs)

fun CoachSuggestion.toDecision(): CoachDecision = CoachDecision(
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    restSeconds = restSeconds,
    anotherSetAdvised = anotherSetAdvised,
    reasonCode = reasonCode,
    trace = trace,
    previewOnly = previewOnly,
    showApply = showApply,
    warmupSets = warmupSets,
)
