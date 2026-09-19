package com.sinura.personaltrainer.domain

/**
 * One in-set decision: load, reps, RPE, rest, and whether another set is
 * worth asking for.
 *
 * Nothing outside `domain/` may be imported (ADR-008, check-domain-seams).
 */
data class CoachDecision(
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val restSeconds: Int,
    val anotherSetAdvised: Boolean,
    val reasonCode: String,
    val trace: RuleTrace,
    val previewOnly: Boolean = false,
    val showApply: Boolean = false,
    val warmupSets: List<WarmupSet> = emptyList(),
    val equipment: EquipmentType? = null,
    val loadType: LoadType? = null,
)

/**
 * The ordered ladder the gym floor already ran in pieces.
 *
 * First match wins:
 * 1. Honesty gates — editing, no history, lift done.
 * 2. Macro overrides — lighter week, RPE hold (via [ProgressionCalculator.adjusted]).
 * 3. Effort — the in-set RPE reason codes.
 * 4. Load or rep step.
 * 5. Rest — [RestPrescription], starting duration only.
 * 6. Extra-set advice — "you have another in you", never a [targetSets] write.
 *
 * [SetMicroRecCalculator] is the rungs of this ladder, not a parallel
 * engine. [ProgressionCalculator.adjusted] is the next-session rung.
 * Behaviour is unchanged; the suite is the proof.
 */
object Coach {
    fun decide(inputs: SetMicroRecInputs): CoachDecision? {
        val rec = SetMicroRecCalculator.suggest(inputs) ?: return null
        return rec.toDecision()
    }

    fun adjusted(
        exerciseId: String,
        exerciseName: String,
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        loadType: LoadType?,
        unit: WeightUnit,
        rpeEvidenceNewestFirst: List<Int?>,
        lighterWeek: Boolean,
        equipment: EquipmentType? = null,
    ): ProgressionHint = ProgressionCalculator.adjusted(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        lastWeightKg = lastWeightKg,
        lastWorkingReps = lastWorkingReps,
        targetReps = targetReps,
        loadType = loadType,
        unit = unit,
        rpeEvidenceNewestFirst = rpeEvidenceNewestFirst,
        lighterWeek = lighterWeek,
        equipment = equipment,
    )
}

fun SetMicroRec.toDecision(): CoachDecision = CoachDecision(
    weightKg = nextWeightKg,
    reps = nextReps,
    rpe = nextRpe,
    restSeconds = restSeconds,
    anotherSetAdvised = anotherSetAdvised,
    reasonCode = reasonCode,
    trace = trace,
    previewOnly = previewOnly,
    showApply = showApply,
    warmupSets = warmupSets,
    equipment = equipment,
    loadType = loadType,
)

fun CoachDecision.toMicroRec(): SetMicroRec = SetMicroRec(
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
    equipment = equipment,
    loadType = loadType,
)
