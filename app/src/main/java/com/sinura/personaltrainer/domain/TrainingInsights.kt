package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.recoverWith

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
     * Windowed full graphs for heat, coach, and week derivation. Home's
     * last-session tiles must not read this — a workout older than the
     * 30-day window would vanish while [summaries] still knows it.
     */
    val history: List<WorkoutSession> = emptyList(),

    /**
     * All-time finished sessions without set graphs (P8.1). Home and Plan
     * read dates, last-session recency, and counts from here, never from
     * [history].
     */
    val summaries: List<SessionSummary> = emptyList(),

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
    val summaries: List<SessionSummary> = emptyList(),
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
    val slots: List<ScheduleSlot> = emptyList(),
    val coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
    val unit: WeightUnit,
    val window: HeatWindow,
    val nowMs: Long,
    val time: TimePort = JvmTime,
    val zoneId: String = time.defaultZoneId(),
    /** Progress has no week plan to show; skipping it keeps a wide history window cheap. */
    val includeWeekPlan: Boolean = true,
    /** Last set per exercise, unwindowed. Overlay for Body recency older than 30 days. */
    val lastLoggedAtByExerciseId: Map<String, Long> = emptyMap(),
)

/**
 * Pure, so the analytics the whole app trusts can actually be tested. Every stage degrades
 * independently: a planner fault must not blank the body map that was already computed
 * correctly.
 */
object TrainingInsightsCalculator {
    fun compute(input: TrainingInsightsInput): TrainingInsights {
        val failures = linkedSetOf<InsightFailure>()

        val todayEpochDay = input.time.civilDate(input.nowMs, input.zoneId).epochDay
        val evidenceStart = todayEpochDay - 13
        val hints = (input.hints ?: run {
            failures += InsightFailure.PROGRESSION
            emptyList()
        }).map { hint ->
            hint.copy(trace = hint.trace ?: RuleTrace.forHint(hint, input.nowMs, todayEpochDay))
        }

        val snapshot = recoverWith(TAG, "The muscle heat snapshot", null) {
            MuscleLoadCalculator.snapshot(
                sessions = input.history,
                window = input.window,
                nowMs = input.nowMs,
                time = input.time,
                zoneId = input.zoneId,
                exerciseCatalog = input.exerciseCatalog,
                weekStart = input.preferences.weekStart,
            )
        }?.rememberLifetimeWork(input.summaries)
            ?.rememberLifetimeRecency(
                lastTrainedByMuscle = MuscleRecency.byMuscle(
                    input.lastLoggedAtByExerciseId,
                    input.exerciseCatalog,
                ),
                nowMs = input.nowMs,
                time = input.time,
                zoneId = input.zoneId,
            )
        if (snapshot == null) failures += InsightFailure.HEAT

        // The coach reasons from its own fixed 14-day basis, not from the display snapshot —
        // so flipping a window chip changes the numbers on the map and nothing about the
        // advice. It is also independent of the snapshot's success: a failed heat computation
        // used to blank the recommendations as collateral damage.
        val recommendations: List<TrainingRecommendation> = recoverWith(
            TAG,
            "The training recommendations",
            null,
        ) {
            val basis = MuscleLoadCalculator.coachBasis(
                sessions = input.history,
                nowMs = input.nowMs,
                time = input.time,
                zoneId = input.zoneId,
                exerciseCatalog = input.exerciseCatalog,
            )
            RecommendationEngine.recommend(
                CoachInputs(
                    basis = basis,
                    history = input.history,
                    routines = input.routines,
                    hints = hints,
                    exerciseCatalog = input.exerciseCatalog,
                    preferences = input.coachPrefs,
                    unit = input.unit,
                    nowMs = input.nowMs,
                    time = input.time,
                    zoneId = input.zoneId,
                ),
            )
        }.let { derived ->
            if (derived == null) failures += InsightFailure.RECOMMENDATIONS
            derived.orEmpty().map { rec ->
                rec.copy(
                    trace = rec.trace ?: RuleTrace.forRecommendation(
                        recommendation = rec,
                        nowMs = input.nowMs,
                        evidenceStartEpochDay = evidenceStart,
                        evidenceEndEpochDay = todayEpochDay,
                    ),
                )
            }
        }

        // The week is READ here, not invented. The planner used to run on this line, from a
        // fresh clock, on every emission — which is why the plan reshuffled whenever anything
        // was logged and nothing the user chose about their own week survived. It is now
        // derived from stored slots, and it no longer depends on the heat snapshot at all:
        // a failed snapshot used to blank the plan as collateral.
        val weekPlan = if (!input.includeWeekPlan) {
            null
        } else {
            recoverWith(TAG, "The weekly schedule plan", null) {
                val derived = WeekDerivation.derive(
                    slots = input.slots,
                    history = input.history,
                    preferences = input.preferences,
                    nowMs = input.nowMs,
                    time = input.time,
                    zoneId = input.zoneId,
                )
                WeekDerivation.toWeeklySchedulePlan(
                    week = derived,
                    routines = input.routines,
                    preferences = input.preferences,
                    nowMs = input.nowMs,
                )
            }.also { if (it == null) failures += InsightFailure.PLAN }
        }

        return TrainingInsights(
            history = input.history,
            summaries = input.summaries.ifEmpty {
                input.history.filter { it.isFinished }.map {
                    it.toSummary(input.time, input.zoneId)
                }
            },
            routines = input.routines,
            snapshot = snapshot,
            hints = hints,
            recommendations = recommendations,
            weekPlan = weekPlan,
            failures = failures,
        )
    }
}
