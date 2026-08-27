package com.sinura.personaltrainer.domain

/**
 * Display sentences for [MeasurableGoal]. No streak language.
 */
object GoalCopy {
    fun featured(snapshots: List<GoalSnapshot>): GoalSnapshot? =
        snapshots.firstOrNull { !it.goal.paused } ?: snapshots.firstOrNull()

    fun progressLine(snapshot: GoalSnapshot, unit: WeightUnit = WeightUnit.KG): String {
        val current = formatCurrent(snapshot, unit)
        val target = formatTarget(snapshot.goal, unit)
        return if (snapshot.goal.paused) {
            "Paused · $target"
        } else {
            "$current of $target"
        }
    }

    fun formatCurrent(snapshot: GoalSnapshot, unit: WeightUnit = WeightUnit.KG): String =
        formatValue(snapshot.goal.kind, snapshot.currentValue, unit)

    fun formatTarget(goal: MeasurableGoal, unit: WeightUnit = WeightUnit.KG): String =
        formatValue(goal.kind, goal.targetValue, unit)

    const val DELETE_TITLE = "Delete this goal?"
    const val DELETE_BODY = "The target is removed. Logged sessions stay."
    const val DELETE_CONFIRM = "Delete"

    private fun formatValue(kind: GoalKind, value: Double, unit: WeightUnit): String = when (kind) {
        GoalKind.ADHERENCE -> "${(value * 100.0).toInt()}%"
        GoalKind.SESSION_COUNT -> "${value.toInt()} sessions"
        GoalKind.ACTIVE_MINUTES -> "${value.toInt()} min"
        GoalKind.LIFT_TARGET -> {
            val shown = WeightConverter.toDisplayValue(value, unit)
            "${WeightConverter.formatDisplayNumber(shown)} ${unit.suffix}"
        }
        GoalKind.CARDIO_DURATION -> "${value.toInt()} min"
        GoalKind.CARDIO_DISTANCE -> distanceLabel(value)
        GoalKind.BODYWEIGHT -> {
            val shown = WeightConverter.toDisplayValue(value, unit)
            "${WeightConverter.formatDisplayNumber(shown)} ${unit.suffix}"
        }
    }

    private fun distanceLabel(meters: Double): String =
        if (meters >= 1000.0) {
            "${WeightConverter.formatDisplayNumber(meters / 1000.0)} km"
        } else {
            "${meters.toInt()} m"
        }
}
