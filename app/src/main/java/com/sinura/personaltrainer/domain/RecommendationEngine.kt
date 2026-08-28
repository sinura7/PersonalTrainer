package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

enum class RecommendationPriority {
    HIGH,
    ATTENTION,
    INFO,
}

enum class RecommendationAction {
    OPEN_LIBRARY_MUSCLE,
    /** Deep-links a named lift the user already owns. See [OwnedLiftResolver]. */
    OPEN_EXERCISE,
    START_WORKOUT,
    OPEN_ROUTINES,
    OPEN_BODY_MAP,
    /** Writes the Job 3 lighter-week marker. The destination is the mark, not a screen. */
    MARK_LIGHTER_WEEK,
}

/**
 * One piece of advice.
 *
 * [kicker] is the ALL-CAPS category label the card leads with — BALANCE, COVERAGE, PROGRESSION,
 * RECOVERY, LOAD. It exists so a stack of cards can be skimmed by kind before any of them is
 * read, and so the category is a word rather than a colour.
 */
data class TrainingRecommendation(
    val id: String,
    val kicker: String,
    val title: String,
    val reason: String,
    val priority: RecommendationPriority,
    val action: RecommendationAction? = null,
    val actionMuscle: CanonicalMuscle? = null,
    val actionExerciseId: String? = null,
    val actionExerciseName: String? = null,
    val rankScore: Int,
    val trace: RuleTrace? = null,
) {
    /**
     * Whether tapping this card can actually take the user somewhere.
     *
     * Not every piece of advice has a destination. "Every muscle is at productive volume"
     * is complete as a sentence and carries no action. It used to render a Volt
     * "Show on the map →" whose tap cleared the selection. The deload card is the
     * exception that gained a real destination in Job 4: [RecommendationAction.MARK_LIGHTER_WEEK]
     * writes the week marker; it does not open the silhouette.
     *
     * The card reads this to decide whether to draw a call to action at all. Advice with
     * nowhere to go is still worth showing; it is the arrow that has to go.
     */
    val hasDestination: Boolean
        get() = when (action) {
            RecommendationAction.OPEN_LIBRARY_MUSCLE,
            RecommendationAction.START_WORKOUT,
            RecommendationAction.OPEN_ROUTINES,
            RecommendationAction.MARK_LIGHTER_WEEK,
            -> true
            // Falls back to the body map when the lift is unresolved, so either is enough.
            RecommendationAction.OPEN_EXERCISE -> actionExerciseId != null || actionMuscle != null
            // The body map is only a destination if there is something on it to select.
            RecommendationAction.OPEN_BODY_MAP, null -> actionMuscle != null
        }
}

/**
 * Everything the coach reasons from.
 *
 * [basis] is a fixed trailing-14-day window, deliberately NOT the display snapshot: advice
 * that changed when the user tapped a different chip on the body map was the single worst
 * thing about the old engine, because it made the app look like it was guessing.
 */
data class CoachInputs(
    val basis: CoachBasis,
    val history: List<WorkoutSession>,
    val routines: List<Routine>,
    val hints: List<ProgressionHint>,
    val exerciseCatalog: Map<String, Exercise>,
    val preferences: CoachPreferences = CoachPreferences.DEFAULT,
    val unit: WeightUnit = WeightUnit.KG,
    val nowMs: Long,
    val time: TimePort = JvmTime,
    val zoneId: String = time.defaultZoneId(),
    /**
     * The first day of the user's week. The deload signal buckets volume into calendar weeks,
     * so it has to honour the same week-start the heat window and planner do — one source of
     * truth, or three surfaces disagree about which week a Sunday-night set belongs to.
     */
    val weekStart: Weekday = Weekday.MONDAY,
)

/**
 * What to do next, and why.
 *
 * Three things changed here and they are all about honesty.
 *
 * **Imbalance is counted in sets, not tonnage.** A deadlift session and a curl session produce
 * wildly different kilograms for the same amount of training, so a tonnage ratio said "your
 * biceps are three times behind your back" to someone training both perfectly evenly. Sets are
 * the unit training is prescribed in and the unit the fix is expressed in.
 *
 * **Nothing quotes the display window.** Every sentence that mentions time says "the last 14
 * days", because that is what was actually measured.
 *
 * **Every muscle card names a lift you own.** "Add hamstring work" is a translation exercise
 * left to the user; "Add Romanian Deadlift" is an instruction.
 *
 * The voice is spec'd, not stylistic: no praise, no first person, no exclamation marks,
 * numerals rather than number-words. A coach that congratulates you is a coach you stop
 * reading.
 */
object RecommendationEngine {
    const val NEGLECT_DAYS = 7
    const val HIGH_NEGLECT_DAYS = 10
    const val MAX_NEGLECTED = 2
    const val IMBALANCE_RATIO = 2.0
    const val STRONG_IMBALANCE_RATIO = 3.0
    const val MAX_RESULTS = 5
    const val MAX_PROGRESSION_NAMES = 3

    /** The heavier side must be doing real work before "behind" means anything. */
    const val MIN_WEEKLY_SETS_FOR_IMBALANCE = 4.0

    const val KICKER_BALANCE = "BALANCE"
    const val KICKER_COVERAGE = "COVERAGE"
    const val KICKER_PROGRESSION = "PROGRESSION"
    const val KICKER_RECOVERY = "RECOVERY"
    const val KICKER_LOAD = "LOAD"

    private const val BASIS_PHRASE = "the last 14 days"

    private val imbalancePairs = listOf(
        CanonicalMuscle.CHEST to CanonicalMuscle.BACK,
        CanonicalMuscle.QUADRICEPS to CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.BICEPS to CanonicalMuscle.TRICEPS,
    )

    fun recommend(inputs: CoachInputs): List<TrainingRecommendation> {
        if (!inputs.basis.hasAnyWorkingSets) return emptyList()

        val deload = deloadSignal(inputs)
        // Both say "do less", so only the more specific one is worth the slot.
        val rest = if (deload == null) restSignal(inputs) else null
        val imbalances = imbalances(inputs)
        val suppressedByImbalance = buildSet {
            imbalances.forEach { rec ->
                rec.actionMuscle?.let { light ->
                    add(light)
                    strongerOfPair(inputs.basis, light)?.let { add(it) }
                }
            }
        }
        val neglected = neglectedMuscles(inputs).filter { it.actionMuscle !in suppressedByImbalance }

        return rank(
            listOfNotNull(deload, rest) +
                imbalances +
                neglected +
                listOfNotNull(coreCoverageGap(inputs)) +
                listOfNotNull(progressionOpportunity(inputs)),
            inputs.preferences.goal,
        )
    }

    // -----------------------------------------------------------------------
    // Do more
    // -----------------------------------------------------------------------

    internal fun imbalances(inputs: CoachInputs): List<TrainingRecommendation> {
        if (!inputs.basis.hasBasisWorkingSets) return emptyList()
        return imbalancePairs.mapNotNull { (left, right) ->
            val a = inputs.basis.load(left)
            val b = inputs.basis.load(right)
            val heavy = if (a.weeklySets >= b.weeklySets) a else b
            val light = if (heavy.muscle == a.muscle) b else a
            if (heavy.weeklySets < MIN_WEEKLY_SETS_FOR_IMBALANCE) return@mapNotNull null

            val lift = resolveLift(light.muscle, inputs)
            val id = "imbalance-${left.name}-${right.name}"
            if (light.weeklySets <= 0.0) {
                return@mapNotNull card(
                    id = id,
                    kicker = KICKER_BALANCE,
                    title = "No ${light.muscle.displayName} work against ${heavy.muscle.displayName}",
                    reason = "${heavy.muscle.displayName} has ${sets(heavy.weeklySets)} weighted " +
                        "sets in $BASIS_PHRASE and ${light.muscle.displayName} has none." +
                        addSentence(lift),
                    priority = RecommendationPriority.HIGH,
                    muscle = light.muscle,
                    lift = lift,
                    rankScore = 55,
                )
            }
            val ratio = heavy.weeklySets / light.weeklySets
            if (ratio < IMBALANCE_RATIO) return@mapNotNull null
            card(
                id = id,
                kicker = KICKER_BALANCE,
                title = "${light.muscle.displayName} is behind ${heavy.muscle.displayName}",
                reason = "${heavy.muscle.displayName} ${sets(heavy.weeklySets)} weighted sets vs " +
                    "${light.muscle.displayName} ${sets(light.weeklySets)} in $BASIS_PHRASE " +
                    "(${formatRatio(ratio)}×)." + addSentence(lift),
                priority = if (ratio >= STRONG_IMBALANCE_RATIO) {
                    RecommendationPriority.HIGH
                } else {
                    RecommendationPriority.ATTENTION
                },
                muscle = light.muscle,
                lift = lift,
                rankScore = 30 + (ratio * 8).toInt().coerceAtMost(40),
            )
        }
    }

    internal fun neglectedMuscles(inputs: CoachInputs): List<TrainingRecommendation> =
        CanonicalMuscle.bodyMapOrder
            .map { inputs.basis.load(it) }
            .filter { load ->
                val days = load.daysSinceLastTrained
                days == null || days >= NEGLECT_DAYS
            }
            .sortedWith(
                compareByDescending<CoachMuscleLoad> { it.daysSinceLastTrained ?: Int.MAX_VALUE }
                    .thenBy { it.muscle.displayName },
            )
            .take(MAX_NEGLECTED)
            .map { load ->
                val days = load.daysSinceLastTrained
                val lift = resolveLift(load.muscle, inputs)
                card(
                    id = "neglect-${load.muscle.name}",
                    kicker = KICKER_COVERAGE,
                    title = if (days == null) {
                        "${load.muscle.displayName} has no logged work"
                    } else {
                        "${load.muscle.displayName}: $days days since a working set"
                    },
                    reason = if (days == null) {
                        "Nothing in history maps to ${load.muscle.displayName}." + coversSentence(lift)
                    } else {
                        "Last working set was $days days ago." + coversSentence(lift)
                    },
                    priority = if (days == null || days >= HIGH_NEGLECT_DAYS) {
                        RecommendationPriority.HIGH
                    } else {
                        RecommendationPriority.ATTENTION
                    },
                    muscle = load.muscle,
                    lift = lift,
                    rankScore = 40 + (days ?: 21).coerceAtMost(21),
                )
            }

    internal fun coreCoverageGap(inputs: CoachInputs): TrainingRecommendation? {
        if (!inputs.basis.hasBasisWorkingSets) return null
        val core = inputs.basis.load(CanonicalMuscle.CORE)
        if (core.weeklySets > 0.0) return null
        val days = core.daysSinceLastTrained
        if (days != null && days < NEGLECT_DAYS) return null
        val lift = resolveLift(CanonicalMuscle.CORE, inputs)
        return card(
            id = "coverage-core",
            kicker = KICKER_COVERAGE,
            title = "No direct core work in $BASIS_PHRASE",
            reason = "Finished sessions in $BASIS_PHRASE include no core lift." + coversSentence(lift),
            priority = RecommendationPriority.ATTENTION,
            muscle = CanonicalMuscle.CORE,
            lift = lift,
            rankScore = 22,
        )
    }

    internal fun progressionOpportunity(inputs: CoachInputs): TrainingRecommendation? {
        val ready = inputs.hints
            .filter { it.action == ProgressionAction.INCREASE }
            .distinctBy { it.exerciseId }
        if (ready.isEmpty()) return null
        val first = ready.first()
        val extra = ready.size - 1
        return TrainingRecommendation(
            id = "progression-ready",
            kicker = KICKER_PROGRESSION,
            title = if (extra > 0) {
                "${first.exerciseName} and $extra more: ready to progress"
            } else {
                "${first.exerciseName}: ready to progress"
            },
            reason = ProgressionCopy.coachReason(first, inputs.unit),
            priority = RecommendationPriority.INFO,
            action = RecommendationAction.OPEN_EXERCISE,
            actionExerciseId = first.exerciseId,
            actionExerciseName = first.exerciseName,
            rankScore = 18 + ready.size.coerceAtMost(5),
        )
    }

    // -----------------------------------------------------------------------
    // Do less
    // -----------------------------------------------------------------------

    /**
     * Everything is at productive volume, so the honest advice is to add nothing.
     *
     * The old engine had no way to say this. Its only "do less" rule fired on an upper-body
     * heat imbalance and told you to train legs — advice to do MORE, wearing the costume of
     * restraint — and it read relative heat, so it could fire on a week of almost no training.
     */
    internal fun restSignal(inputs: CoachInputs): TrainingRecommendation? {
        if (!inputs.basis.hasBasisWorkingSets) return null
        val allProductive = CanonicalMuscle.bodyMapOrder.all { muscle ->
            inputs.basis.load(muscle).band >= HeatBand.PRODUCTIVE
        }
        if (!allProductive) return null
        return TrainingRecommendation(
            id = "rest-all-productive",
            kicker = KICKER_RECOVERY,
            title = "Every muscle is at productive volume",
            reason = "All mapped muscles are at 10+ weighted sets per week over $BASIS_PHRASE. " +
                "Nothing needs adding.",
            priority = RecommendationPriority.HIGH,
            rankScore = 72,
        )
    }

    internal fun deloadSignal(inputs: CoachInputs): TrainingRecommendation? {
        val finding = DeloadSignal.detect(
            inputs.history,
            inputs.nowMs,
            inputs.time,
            inputs.zoneId,
            inputs.weekStart,
        ) ?: return null
        return TrainingRecommendation(
            id = "deload-volume-flat-strength",
            kicker = KICKER_LOAD,
            title = "Sets up 3 weeks, e1RM flat",
            reason = "Weekly working sets rose ${finding.setRisePercent}% over three weeks while " +
                "top-lift e1RMs did not move. Schedule a lighter week.",
            priority = RecommendationPriority.HIGH,
            action = RecommendationAction.MARK_LIGHTER_WEEK,
            rankScore = 75,
        )
    }

    // -----------------------------------------------------------------------
    // Plumbing
    // -----------------------------------------------------------------------

    private fun card(
        id: String,
        kicker: String,
        title: String,
        reason: String,
        priority: RecommendationPriority,
        muscle: CanonicalMuscle,
        lift: Exercise?,
        rankScore: Int,
    ): TrainingRecommendation = TrainingRecommendation(
        id = id,
        kicker = kicker,
        title = title,
        reason = reason,
        priority = priority,
        // A named lift is a deep link; without one the card falls back to the muscle filter,
        // which is still useful and is at least honest about knowing less.
        action = if (lift != null) {
            RecommendationAction.OPEN_EXERCISE
        } else {
            RecommendationAction.OPEN_LIBRARY_MUSCLE
        },
        actionMuscle = muscle,
        actionExerciseId = lift?.id,
        actionExerciseName = lift?.name,
        rankScore = rankScore,
    )

    private fun resolveLift(muscle: CanonicalMuscle, inputs: CoachInputs): Exercise? =
        OwnedLiftResolver.resolve(
            muscle = muscle,
            routines = inputs.routines,
            history = inputs.history,
            exerciseCatalog = inputs.exerciseCatalog,
            preferences = inputs.preferences,
            nowMs = inputs.nowMs,
        )

    private fun addSentence(lift: Exercise?): String =
        if (lift == null) "" else " Add ${lift.name}."

    private fun coversSentence(lift: Exercise?): String =
        if (lift == null) "" else " ${lift.name} covers it."

    /**
     * The goal reorders; it never adds or removes a card. A rule that only fires for one goal
     * is a rule that is wrong for the others. Athletic changes the generated week, not this list.
     */
    private fun goalBonus(recommendation: TrainingRecommendation, goal: TrainingGoal): Int =
        when (goal) {
            TrainingGoal.STRENGTH -> when {
                recommendation.id == "progression-ready" -> 10
                recommendation.id.startsWith("deload") -> 10
                else -> 0
            }
            TrainingGoal.HYPERTROPHY -> when {
                recommendation.id.startsWith("imbalance") -> 10
                recommendation.id.startsWith("neglect") -> 10
                recommendation.id == "coverage-core" -> 10
                else -> 0
            }
            TrainingGoal.RESILIENCE -> when {
                recommendation.id.startsWith("neglect") -> 10
                recommendation.id == "coverage-core" -> 10
                else -> 0
            }
            TrainingGoal.ATHLETIC -> 0
            TrainingGoal.GENERAL -> 0
        }

    private fun rank(
        items: List<TrainingRecommendation>,
        goal: TrainingGoal,
    ): List<TrainingRecommendation> = items
        .distinctBy { it.id }
        .map { it.copy(rankScore = it.rankScore + goalBonus(it, goal)) }
        .sortedWith(
            compareByDescending<TrainingRecommendation> { it.priority.rank }
                .thenByDescending { it.rankScore }
                .thenBy { it.title },
        )
        .take(MAX_RESULTS)

    private fun strongerOfPair(basis: CoachBasis, weaker: CanonicalMuscle): CanonicalMuscle? {
        val pair = imbalancePairs.firstOrNull { it.first == weaker || it.second == weaker } ?: return null
        val other = if (pair.first == weaker) pair.second else pair.first
        return if (basis.load(other).weeklySets > basis.load(weaker).weeklySets) other else null
    }

    /** Weighted sets read as whole numbers unless the fraction actually matters. */
    internal fun sets(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (kotlin.math.abs(rounded - kotlin.math.round(rounded)) < 0.05) {
            kotlin.math.round(rounded).toInt().toString()
        } else {
            rounded.toString()
        }
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
