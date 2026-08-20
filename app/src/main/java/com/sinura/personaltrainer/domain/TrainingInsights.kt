package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.recoverWith
import java.time.ZoneId

private const val TAG = "PT/Insights"

/** Which part of the analytics pass failed, so a screen can say so instead of showing zeroes. */
enum class InsightFailure {
    /** The muscle heat snapshot could not be built; [TrainingInsights.snapshot] is null. */
    HEAT,

    /** The progression query failed. Not the same as "no lift is ready to progress". */
    PROGRESSION,

    /** Recommendations could not be derived from an otherwise-valid snapshot. */
    RECOMMENDATIONS,

    /** The weekly plan could not be built. */
    PLAN,
}

/**
 * Everything Home, Schedule and Progress derive from one history read.
 *
 * These three screens each used to run their own copy of this pipeline, and the copies had
 * drifted: Progress passed the exercise catalog to [MuscleLoadCalculator] and honoured the
 * user's week-start preference, Home and Schedule passed neither. A set logged against an
 * exercise that is no longer attached to its session resolves its muscle only through that
 * catalog — so the same history produced a different body map on Progress than the one the
 * weekly plan was built from. One pipeline, one snapshot, one answer.
 */
data class TrainingInsights(
    /**
     * The finished sessions this pass was computed from, echoed back.
     *
     * Home renders a "recent sessions" strip beside the body map. Collecting the history flow
     * a second time to get it would run the full deep-graph query twice and — worse — let the
     * strip and the map disagree, showing a session that the heat beside it has not counted.
     */
    val history: List<WorkoutSession> = emptyList(),

    /** Likewise: the routines this pass was computed from. */
    val routines: List<Routine> = emptyList(),
    val snapshot: BodyHeatSnapshot? = null,
    val hints: List<ProgressionHint> = emptyList(),
    val recommendations: List<TrainingRecommendation> = emptyList(),
    val weekPlan: WeeklySchedulePlan? = null,
    val failures: Set<InsightFailure> = emptySet(),
) {
    fun failed(part: InsightFailure): Boolean = part in failures
}

data class TrainingInsightsInput(
    val history: List<WorkoutSession>,
    val routines: List<Routine>,
    /**
     * Fallback muscle mapping for sets whose exercise is no longer attached to their session.
     * Without it those sets are attributed to nothing at all.
     */
    val exerciseCatalog: Map<String, Exercise>,
    /**
     * Progression hints, which come from a suspending query and so are computed by the caller.
     * Null means that query *failed* — distinct from an empty list, which means nothing is ready.
     */
    val hints: List<ProgressionHint>?,
    val preferences: SchedulePreferences,
    val unit: WeightUnit,
    val window: HeatWindow,
    val nowMs: Long,
    val zone: ZoneId,
    /** Progress has no week plan to show; skipping it keeps a wide history window cheap. */
    val includeWeekPlan: Boolean = true,
)

/**
 * Pure, so the analytics the whole app trusts can actually be tested. Every stage degrades
 * independently: a planner fault must not blank the body map that was already computed
 * correctly.
 */
object TrainingInsightsCalculator {
    fun compute(input: TrainingInsightsInput): TrainingInsights {
        val failures = linkedSetOf<InsightFailure>()

        val hints = input.hints ?: run {
            failures += InsightFailure.PROGRESSION
            emptyList()
        }

        val snapshot = recoverWith(TAG, "The muscle heat snapshot", null) {
            MuscleLoadCalculator.snapshot(
                sessions = input.history,
                window = input.window,
                nowMs = input.nowMs,
                zone = input.zone,
                exerciseCatalog = input.exerciseCatalog,
                weekStart = input.preferences.weekStart,
            )
        }
        if (snapshot == null) failures += InsightFailure.HEAT

        val recommendations: List<TrainingRecommendation> = if (snapshot == null) {
            emptyList()
        } else {
            val derived = recoverWith(TAG, "The training recommendations", null) {
                RecommendationEngine.recommend(snapshot, hints, input.unit)
            }
            if (derived == null) failures += InsightFailure.RECOMMENDATIONS
            derived.orEmpty()
        }

        val weekPlan = if (snapshot == null || !input.includeWeekPlan) {
            null
        } else {
            recoverWith(TAG, "The weekly schedule plan", null) {
                WeeklySchedulePlanner.plan(
                    preferences = input.preferences,
                    snapshot = snapshot,
                    recommendations = recommendations,
                    routines = input.routines,
                    recentSessions = input.history,
                    nowMs = input.nowMs,
                    zone = input.zone,
                )
            }.also { if (it == null) failures += InsightFailure.PLAN }
        }

        return TrainingInsights(
            history = input.history,
            routines = input.routines,
            snapshot = snapshot,
            hints = hints,
            recommendations = recommendations,
            weekPlan = weekPlan,
            failures = failures,
        )
    }
}
