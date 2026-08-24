package com.sinura.personaltrainer.domain

enum class GoalKind(val label: String) {
    ADHERENCE("Adherence"),
    SESSION_COUNT("Sessions"),
    ACTIVE_MINUTES("Active minutes"),
    LIFT_TARGET("Lift target"),
    CARDIO_DURATION("Cardio time"),
    CARDIO_DISTANCE("Cardio distance"),
    BODYWEIGHT("Bodyweight"),
}

enum class GoalPeriod(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL_TIME("All time"),
}

/**
 * An explicit target, not [TrainingGoal] (that enum only ranks coach cards).
 *
 * No streak. Pause is a first-class field so illness, travel, or rest
 * does not look like failure.
 */
data class MeasurableGoal(
    val id: String,
    val kind: GoalKind,
    val targetValue: Double,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val period: GoalPeriod,
    val captured: CapturedCivilTime,
    val paused: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class GoalSnapshot(
    val goal: MeasurableGoal,
    val currentValue: Double,
    val ratio: Double,
    val met: Boolean,
)

object GoalProgress {
    fun measure(
        goal: MeasurableGoal,
        projections: List<DailyProjection>,
        today: CivilDate,
        weekStart: Weekday,
        trainingDaysPerWeek: Int,
        latestBodyweightKg: Double?,
        liftBestKg: Double?,
    ): GoalSnapshot {
        if (goal.paused) {
            return GoalSnapshot(goal, currentValue = 0.0, ratio = 0.0, met = false)
        }
        val horizon = when (goal.period) {
            GoalPeriod.WEEK -> AnalyticsHorizon.WEEK
            GoalPeriod.MONTH -> AnalyticsHorizon.MONTH
            GoalPeriod.YEAR -> AnalyticsHorizon.YEAR
            GoalPeriod.ALL_TIME -> AnalyticsHorizon.ALL_TIME
        }
        val totals = HorizonMath.totals(horizon, projections, today, weekStart)
        val current = when (goal.kind) {
            GoalKind.ADHERENCE -> adherenceRatio(totals, trainingDaysPerWeek, goal.period)
            GoalKind.SESSION_COUNT -> totals.sessionCount.toDouble()
            GoalKind.ACTIVE_MINUTES -> totals.activeMinutes.toDouble()
            GoalKind.LIFT_TARGET -> liftBestKg ?: 0.0
            GoalKind.CARDIO_DURATION -> totals.cardioSeconds / 60.0
            GoalKind.CARDIO_DISTANCE -> totals.cardioDistanceMeters
            GoalKind.BODYWEIGHT -> latestBodyweightKg ?: 0.0
        }
        val target = goal.targetValue.coerceAtLeast(0.0)
        val ratio = if (target <= 0.0) 0.0 else (current / target).coerceAtLeast(0.0)
        val met = when (goal.kind) {
            GoalKind.BODYWEIGHT -> latestBodyweightKg != null &&
                kotlin.math.abs(current - target) <= 0.5
            else -> target > 0.0 && current + 1e-6 >= target
        }
        return GoalSnapshot(goal, currentValue = current, ratio = ratio, met = met)
    }

    /**
     * Trained days over expected training days in the period. Rest days
     * are not a miss. There is no consecutive-day streak.
     */
    private fun adherenceRatio(
        totals: HorizonTotals,
        trainingDaysPerWeek: Int,
        period: GoalPeriod,
    ): Double {
        val expected = expectedTrainingDays(
            start = totals.startEpochDay,
            end = totals.endEpochDay,
            trainingDaysPerWeek = trainingDaysPerWeek.coerceIn(1, 7),
            period = period,
        )
        if (expected <= 0) return 0.0
        return totals.trainedDays.toDouble() / expected.toDouble()
    }

    internal fun expectedTrainingDays(
        start: Long,
        end: Long,
        trainingDaysPerWeek: Int,
        period: GoalPeriod,
    ): Int {
        if (end < start) return 0
        val days = (end - start + 1).toInt().coerceAtLeast(1)
        return when (period) {
            GoalPeriod.WEEK -> trainingDaysPerWeek
            GoalPeriod.MONTH, GoalPeriod.YEAR, GoalPeriod.ALL_TIME ->
                ((days / 7.0) * trainingDaysPerWeek).toInt().coerceAtLeast(trainingDaysPerWeek)
        }
    }
}
