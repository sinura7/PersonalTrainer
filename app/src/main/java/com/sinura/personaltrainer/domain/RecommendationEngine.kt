package com.sinura.personaltrainer.domain

enum class RecommendationPriority {
    HIGH,
    ATTENTION,
    INFO,
}

enum class RecommendationAction {
    OPEN_LIBRARY_MUSCLE,
    START_WORKOUT,
    OPEN_ROUTINES,
    OPEN_BODY_MAP,
}

data class TrainingRecommendation(
    val id: String,
    val title: String,
    val reason: String,
    val priority: RecommendationPriority,
    val action: RecommendationAction? = null,
    val actionMuscle: CanonicalMuscle? = null,
    val rankScore: Int,
)

object RecommendationEngine {
    const val NEGLECT_DAYS = 7
    const val HIGH_NEGLECT_DAYS = 10
    const val MAX_NEGLECTED = 2
    const val IMBALANCE_RATIO = 2.0
    const val STRONG_IMBALANCE_RATIO = 3.0
    const val MIN_VOLUME_FOR_IMBALANCE_KG = 250.0
    const val HIGH_HEAT = 0.8
    const val LOW_REGION_HEAT = 0.4
    const val HIGH_UPPER_COUNT = 3
    const val MAX_RESULTS = 5
    const val MAX_PROGRESSION_NAMES = 3

    private val imbalancePairs = listOf(
        CanonicalMuscle.CHEST to CanonicalMuscle.BACK,
        CanonicalMuscle.QUADRICEPS to CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.BICEPS to CanonicalMuscle.TRICEPS,
    )

    fun recommend(
        snapshot: BodyHeatSnapshot,
        progression: List<ProgressionHint>,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): List<TrainingRecommendation> {
        if (!snapshot.hasAnyWorkingSets) return emptyList()

        val recovery = recoverySignal(snapshot)
        val suppressedUpper = recovery != null
        val neglected = neglectedMuscles(snapshot, suppressUpper = suppressedUpper)
        val imbalances = imbalances(snapshot, weightUnit)
        val progressionRec = progressionOpportunity(progression, weightUnit)
        val coreGap = coreCoverageGap(snapshot)

        val suppressedByImbalance = buildSet {
            imbalances.forEach { rec ->
                rec.actionMuscle?.let { add(it) }
                rec.actionMuscle?.let { weaker ->
                    strongerOfPair(snapshot, weaker)?.let { add(it) }
                }
            }
        }

        val filteredNeglect = neglected.filter { it.actionMuscle !in suppressedByImbalance }

        return rank(
            listOfNotNull(recovery) +
                imbalances +
                filteredNeglect +
                listOfNotNull(coreGap) +
                listOfNotNull(progressionRec),
        )
    }

    internal fun neglectedMuscles(
        snapshot: BodyHeatSnapshot,
        suppressUpper: Boolean,
    ): List<TrainingRecommendation> {
        return snapshot.mapLoads
            .filter { load ->
                if (suppressUpper && load.muscle.region == MuscleRegion.UPPER) return@filter false
                val days = load.daysSinceLastTrained
                days == null || days >= NEGLECT_DAYS
            }
            .sortedWith(
                compareByDescending<MuscleLoadSummary> { it.daysSinceLastTrained ?: Int.MAX_VALUE }
                    .thenBy { it.muscle.displayName },
            )
            .take(MAX_NEGLECTED)
            .map { load ->
                val days = load.daysSinceLastTrained
                val high = days == null || days >= HIGH_NEGLECT_DAYS
                val title = if (days == null) {
                    "${load.muscle.displayName} has no logged work"
                } else {
                    "${load.muscle.displayName} hasn’t been trained in $days days"
                }
                val reason = if (days == null) {
                    "Nothing in history maps to ${load.muscle.displayName}. Add a lift or log a set."
                } else {
                    "Last working set was $days days ago. A session here would close the gap."
                }
                TrainingRecommendation(
                    id = "neglect-${load.muscle.name}",
                    title = title,
                    reason = reason,
                    priority = if (high) RecommendationPriority.HIGH else RecommendationPriority.ATTENTION,
                    action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
                    actionMuscle = load.muscle,
                    rankScore = 40 + (days ?: 21).coerceAtMost(21),
                )
            }
    }

    internal fun imbalances(
        snapshot: BodyHeatSnapshot,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): List<TrainingRecommendation> {
        if (!snapshot.hasWindowWorkingSets) return emptyList()
        return imbalancePairs.mapNotNull { (left, right) ->
            val a = snapshot.load(left)
            val b = snapshot.load(right)
            val high = maxOf(a.volumeKg, b.volumeKg)
            val low = minOf(a.volumeKg, b.volumeKg)
            if (high < MIN_VOLUME_FOR_IMBALANCE_KG) return@mapNotNull null
            if (low <= 0.0) {
                val missing = if (a.volumeKg <= 0.0) left else right
                val heavy = if (a.volumeKg <= 0.0) right else left
                return@mapNotNull TrainingRecommendation(
                    id = "imbalance-${left.name}-${right.name}",
                    title = "No ${missing.displayName} vs ${heavy.displayName} this window",
                    reason = "${heavy.displayName} has ${high.toWeightLabel(weightUnit)} volume and ${missing.displayName} has none.",
                    priority = RecommendationPriority.HIGH,
                    action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
                    actionMuscle = missing,
                    rankScore = 55,
                )
            }
            val ratio = high / low
            if (ratio < IMBALANCE_RATIO) return@mapNotNull null
            val heavy = if (a.volumeKg >= b.volumeKg) a.muscle else b.muscle
            val light = if (heavy == a.muscle) b.muscle else a.muscle
            TrainingRecommendation(
                id = "imbalance-${left.name}-${right.name}",
                title = "${heavy.displayName} volume is much higher than ${light.displayName}",
                reason = "${heavy.displayName} is ${formatRatio(ratio)}× ${light.displayName} over ${snapshot.window.shortLabel.lowercase()}.",
                priority = if (ratio >= STRONG_IMBALANCE_RATIO) {
                    RecommendationPriority.HIGH
                } else {
                    RecommendationPriority.ATTENTION
                },
                action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
                actionMuscle = light,
                rankScore = 30 + (ratio * 8).toInt().coerceAtMost(40),
            )
        }
    }

    internal fun recoverySignal(snapshot: BodyHeatSnapshot): TrainingRecommendation? {
        if (!snapshot.hasWindowWorkingSets) return null
        val upperHot = snapshot.mapLoads.count {
            it.muscle.region == MuscleRegion.UPPER && it.heat >= HIGH_HEAT
        }
        if (upperHot < HIGH_UPPER_COUNT) return null
        val lowerHeat = snapshot.mapLoads
            .filter { it.muscle.region == MuscleRegion.LOWER }
            .map { it.heat }
            .average()
        if (lowerHeat >= LOW_REGION_HEAT) return null
        return TrainingRecommendation(
            id = "recovery-upper",
            title = "Upper-body load is very high",
            reason = "Chest, back, and arms are carrying most of the last ${snapshot.window.shortLabel.lowercase()}. Consider lower body or a lighter day.",
            priority = RecommendationPriority.HIGH,
            action = RecommendationAction.OPEN_ROUTINES,
            rankScore = 70,
        )
    }

    internal fun coreCoverageGap(snapshot: BodyHeatSnapshot): TrainingRecommendation? {
        if (!snapshot.hasWindowWorkingSets) return null
        val core = snapshot.load(CanonicalMuscle.CORE)
        if (core.trainedInWindow) return null
        val days = core.daysSinceLastTrained
        if (days != null && days < NEGLECT_DAYS) return null
        val windowWord = if (snapshot.window == HeatWindow.CURRENT_WEEK) "this week" else "this window"
        return TrainingRecommendation(
            id = "coverage-core",
            title = "No direct core work $windowWord",
            reason = "Finished sessions in ${snapshot.window.label.lowercase()} don’t include a core lift.",
            priority = RecommendationPriority.ATTENTION,
            action = RecommendationAction.OPEN_LIBRARY_MUSCLE,
            actionMuscle = CanonicalMuscle.CORE,
            rankScore = 22,
        )
    }

    internal fun progressionOpportunity(
        progression: List<ProgressionHint>,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): TrainingRecommendation? {
        val ready = progression
            .filter { it.action == ProgressionAction.INCREASE }
            .distinctBy { it.exerciseId }
        if (ready.isEmpty()) return null
        val names = ready.take(MAX_PROGRESSION_NAMES).map { it.exerciseName }
        val extra = ready.size - names.size
        val listed = names.joinToString(" and ")
        val increment = ProgressionCalculator.INCREMENT_KG.toWeightLabel(weightUnit)
        val title = if (ready.size == 1) {
            "${names.first()} is ready to progress (+$increment)"
        } else {
            val label = if (extra > 0) "$listed and $extra more" else listed
            "$label are ready to progress (+$increment)"
        }
        return TrainingRecommendation(
            id = "progression-ready",
            title = title,
            reason = "Last working set hit the target reps. Next session, add $increment.",
            priority = RecommendationPriority.INFO,
            action = RecommendationAction.START_WORKOUT,
            rankScore = 18 + ready.size.coerceAtMost(5),
        )
    }

    private fun rank(items: List<TrainingRecommendation>): List<TrainingRecommendation> {
        return items
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<TrainingRecommendation> { it.priority.rank }
                    .thenByDescending { it.rankScore }
                    .thenBy { it.title },
            )
            .take(MAX_RESULTS)
    }

    private fun strongerOfPair(snapshot: BodyHeatSnapshot, weaker: CanonicalMuscle): CanonicalMuscle? {
        val pair = imbalancePairs.firstOrNull { it.first == weaker || it.second == weaker } ?: return null
        val other = if (pair.first == weaker) pair.second else pair.first
        return if (snapshot.load(other).volumeKg > snapshot.load(weaker).volumeKg) other else null
    }

    private fun formatRatio(ratio: Double): String {
        val rounded = kotlin.math.round(ratio * 10.0) / 10.0
        return if (kotlin.math.abs(rounded % 1.0) < 0.05) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private val RecommendationPriority.rank: Int
        get() = when (this) {
            RecommendationPriority.HIGH -> 3
            RecommendationPriority.ATTENTION -> 2
            RecommendationPriority.INFO -> 1
        }
}
