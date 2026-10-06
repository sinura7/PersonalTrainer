package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingGoal
/**
 * Offline add-a-set gate for Tempo (locked product rules, October 2026).
 * Returns an offer only after the last *planned* working set is logged.
 */
object AddASetPolicy {
    const val SOFT_WEEKLY_SET_TARGET = 10
    const val READINESS_SIGNALS = 5
    const val READINESS_THRESHOLD = 3
    const val MEAN_RPE_CEILING = 7.5
    const val PREFER_LOAD_RPE_CEILING = 7

    data class Context(
        val goal: TrainingGoal,
        val primaryMuscle: CanonicalMuscle?,
        val targetSets: Int,
        val workingLogged: Int,
        val todayWorking: List<LoggedSetView>,
        val lighterWeek: Boolean,
        val extraSetAlreadyAccepted: Boolean,
        val addASetDismissedForExercise: Boolean,
        val weeklyHardSetsForMuscle: Double,
        val muscleWeeklySetsByWeek: List<Double>,
        val blockWeekIndex: Int,
        val blockOpenerSetsForLift: Int,
        val lastSessionMatchingSets: List<LoggedSetView>,
        val lastWorkingSet: LoggedSetView,
        val targetReps: Int,
        /** Next-set math if an extra working set were allowed (load/reps/RPE). */
        val extraSetReasonCode: String?,
        val loadProgressionBlocked: Boolean,
    )

    data class Offer(
        val tipShort: String,
        val evidenceIds: List<String>,
        val heuristicEvidenceIds: List<String>,
    )

    fun evaluate(ctx: Context): Offer? {
        if (!atLastPlannedWorkingSet(ctx)) return null
        if (!passesHardGates(ctx)) return null
        if (ctx.addASetDismissedForExercise) return null
        if (!readinessMet(ctx)) return null
        if (preferLoadFirst(ctx)) return null
        return Offer(
            tipShort = "Room for one more working set on this lift today.",
            evidenceIds = evidenceIds(),
            heuristicEvidenceIds = heuristicEvidenceIds(),
        )
    }

    internal fun atLastPlannedWorkingSet(ctx: Context): Boolean =
        ctx.targetSets > 0 &&
            ctx.workingLogged == ctx.targetSets &&
            ctx.todayWorking.size == ctx.targetSets

    internal fun passesHardGates(ctx: Context): Boolean {
        if (!goalAllowsAddASet(ctx.goal)) return false
        if (ctx.primaryMuscle == null || ctx.primaryMuscle == CanonicalMuscle.OTHER) return false
        if (ctx.targetSets > 3) return false
        if (ctx.extraSetAlreadyAccepted) return false
        if (ctx.lighterWeek) return false
        val rpeLogged = ctx.todayWorking.count { it.rpe != null }
        if (rpeLogged < 2) return false
        return true
    }

    internal fun goalAllowsAddASet(goal: TrainingGoal): Boolean = when (goal) {
        TrainingGoal.STRENGTH -> false
        TrainingGoal.HYPERTROPHY,
        TrainingGoal.GENERAL,
        TrainingGoal.ATHLETIC,
        TrainingGoal.RESILIENCE,
        -> true
    }

    internal fun readinessMet(ctx: Context): Boolean {
        var signals = 0
        if (meanRpe(ctx.todayWorking) <= MEAN_RPE_CEILING) signals++
        if (!failedOrCutShortToday(ctx)) signals++
        if (loadOrRepsHeldOrRose(ctx.todayWorking, ctx.lastSessionMatchingSets)) signals++
        if (ctx.weeklyHardSetsForMuscle < SOFT_WEEKLY_SET_TARGET) signals++
        if (volumeFlatOrDown(ctx.muscleWeeklySetsByWeek) ||
            blockWeekTimingAllows(ctx.blockWeekIndex, ctx.workingLogged, ctx.blockOpenerSetsForLift)
        ) {
            signals++
        }
        return signals >= READINESS_THRESHOLD
    }

    internal fun preferLoadFirst(ctx: Context): Boolean {
        if (!readinessMet(ctx)) return false
        val last = ctx.lastWorkingSet
        val rpe = last.rpe ?: return false
        if (rpe > PREFER_LOAD_RPE_CEILING) return false
        if (last.reps < ctx.targetReps) return false
        val reason = ctx.extraSetReasonCode ?: return false
        if (reason == SetMicroRecCalculator.LIFT_DONE) return false
        if (ctx.loadProgressionBlocked) return false
        return reason == SetMicroRecCalculator.IN_TANK ||
            reason == SetMicroRecCalculator.CLIMB_REPS ||
            reason == SetMicroRecCalculator.BW_ADD_REP ||
            reason == SetMicroRecCalculator.QUALITY
    }

    internal fun meanRpe(working: List<LoggedSetView>): Double {
        val logged = working.mapNotNull { it.rpe }
        if (logged.isEmpty()) return 99.0
        return logged.average()
    }

    internal fun failedOrCutShortToday(ctx: Context): Boolean {
        val failedCodes = setOf(
            SetMicroRecCalculator.FAILED_DROP,
            SetMicroRecCalculator.SKIP_RPE_DROP,
            SetMicroRecCalculator.BW_DROP_REP,
        )
        if (ctx.extraSetReasonCode in failedCodes) return true
        return ctx.todayWorking.any { set ->
            set.reps < ctx.targetReps.coerceAtLeast(1) &&
                (set.rpe ?: 0) >= 8
        }
    }

    internal fun loadOrRepsHeldOrRose(
        today: List<LoggedSetView>,
        lastSession: List<LoggedSetView>,
    ): Boolean {
        if (lastSession.isEmpty()) return true
        val pairs = today.zip(lastSession)
        if (pairs.isEmpty()) return true
        return pairs.all { (now, prev) ->
            now.weightKg >= prev.weightKg - 0.001 && now.reps >= prev.reps
        }
    }

    internal fun volumeFlatOrDown(weeklySets: List<Double>): Boolean {
        if (weeklySets.size < 3) return false
        val w1 = weeklySets[weeklySets.size - 3]
        val w2 = weeklySets[weeklySets.size - 2]
        val w3 = weeklySets[weeklySets.size - 1]
        return w3 <= w2 && w2 <= w1
    }

    internal fun blockWeekTimingAllows(
        blockWeekIndex: Int,
        plannedSetsToday: Int,
        blockOpenerSets: Int,
    ): Boolean =
        blockWeekIndex in 4..5 && plannedSetsToday <= blockOpenerSets

    fun evidenceIds(): List<String> = listOf(
        "schoenfeld-2017-volume",
        "helms-2016-rpe-application",
        "zourdos-2016-rpe-rir",
        "heuristic-add-a-set-cap",
        "heuristic-block-week-timing",
    )

    fun heuristicEvidenceIds(): List<String> = listOf(
        "heuristic-add-a-set-cap",
        "heuristic-block-week-timing",
    )

    fun primaryMuscleFor(
        muscles: List<com.sinura.personaltrainer.domain.MuscleCredit>,
        muscleGroup: String,
    ): CanonicalMuscle? {
        if (muscles.isNotEmpty()) {
            val primary = muscles.maxByOrNull { it.weight } ?: return null
            return MuscleNormalizer.resolveKey(primary.muscleKey)
                ?: MuscleNormalizer.primaryOf(muscleGroup)
        }
        return MuscleNormalizer.primaryOf(muscleGroup)?.takeIf { it != CanonicalMuscle.OTHER }
    }

    fun blockWeekIndex(block: TrainingBlock?, todayEpochDay: Long): Int =
        block?.weekIndexOn(todayEpochDay) ?: 0
}
