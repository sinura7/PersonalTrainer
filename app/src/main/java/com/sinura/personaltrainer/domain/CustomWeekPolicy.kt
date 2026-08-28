package com.sinura.personaltrainer.domain

/**
 * One lift staged on a custom week day, before anything is written.
 *
 * Ids are local to the draft. They exist so reorder, expand and remove can name a row
 * without waiting on Room.
 */
data class CustomWeekLift(
    val id: String,
    val exercise: Exercise,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val targetWeightKg: Double? = null,
)

enum class CustomWeekDayMark {
    EMPTY,
    PREFERRED,
    FILLED,
}

/**
 * Rules for building a week by hand.
 *
 * The guided path derives a split and fills it. This path is the other fork: the lifter
 * names the days and the lifts, and the app writes what they built. The policy stays here
 * so the screen cannot invent a week with no work in it, and so a test can pin the names
 * the routines land under.
 */
object CustomWeekPolicy {
    fun canConfirm(days: Map<Weekday, List<CustomWeekLift>>): Boolean =
        days.values.any { it.isNotEmpty() }

    fun trainingDayCount(days: Map<Weekday, List<CustomWeekLift>>): Int =
        days.count { it.value.isNotEmpty() }.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)

    /** Raw filled days. Empty week is zero, not the coerced training-day floor. */
    fun filledDayCount(days: Map<Weekday, List<CustomWeekLift>>): Int =
        days.count { it.value.isNotEmpty() }

    fun restDayCount(days: Map<Weekday, List<CustomWeekLift>>): Int =
        (SchedulePreferences.MAX_DAYS - filledDayCount(days)).coerceAtLeast(0)

    fun isFullWeek(days: Map<Weekday, List<CustomWeekLift>>): Boolean =
        filledDayCount(days) == SchedulePreferences.MAX_DAYS

    fun isFullWeek(daysPerWeek: Int): Boolean =
        daysPerWeek == SchedulePreferences.MAX_DAYS

    fun confirmCta(trainingDays: Int): String =
        "Use this week · $trainingDays training"

    fun restCaption(restDays: Int): String? =
        if (restDays > 0) "$restDays rest" else null

    fun routineName(day: Weekday): String =
        day.name.lowercase().replaceFirstChar { it.titlecase() }

    /** A later session on a weekday that already has a named routine. */
    fun extraRoutineName(day: Weekday): String = "${routineName(day)} extra"

    /**
     * Preferred days from the questionnaire stay marked even with zero lifts.
     * Confirm still needs at least one lift somewhere.
     */
    fun dayMark(
        day: Weekday,
        filled: Set<Weekday>,
        preferred: Set<Weekday>,
    ): CustomWeekDayMark = when {
        day in filled -> CustomWeekDayMark.FILLED
        day in preferred -> CustomWeekDayMark.PREFERRED
        else -> CustomWeekDayMark.EMPTY
    }

    /** Week start, unless a preferred weekday exists — then the first of those in week order. */
    fun initialSelectedDay(weekStart: Weekday, preferred: Set<Weekday>): Weekday {
        val ordered = (0 until 7).map { weekStart.plus(it.toLong()) }
        return ordered.firstOrNull { it in preferred } ?: weekStart
    }

    fun addLifts(
        existing: List<CustomWeekLift>,
        incoming: List<Exercise>,
        idFactory: () -> String,
    ): List<CustomWeekLift> {
        val have = existing.map { it.exercise.id }.toSet()
        val added = incoming.filter { it.id !in have }.map { exercise ->
            val defaults = AddDefaults.forExercise(exercise)
            CustomWeekLift(
                id = idFactory(),
                exercise = exercise,
                targetSets = defaults.sets,
                targetReps = defaults.reps,
                restSeconds = defaults.restSeconds,
            )
        }
        return existing + added
    }

    fun move(lifts: List<CustomWeekLift>, itemId: String, direction: Int): List<CustomWeekLift> {
        val index = lifts.indexOfFirst { it.id == itemId }
        if (index < 0) return lifts
        val target = index + direction
        if (target !in lifts.indices) return lifts
        val next = lifts.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        return next
    }

    fun updateTargets(
        lifts: List<CustomWeekLift>,
        itemId: String,
        sets: Int?,
        reps: Int?,
        restSeconds: Int?,
        weightKg: Double?,
    ): List<CustomWeekLift> = lifts.map { lift ->
        if (lift.id != itemId) {
            lift
        } else {
            lift.copy(
                targetSets = sets?.takeIf { it >= 1 } ?: lift.targetSets,
                targetReps = reps?.takeIf { it >= 1 } ?: lift.targetReps,
                restSeconds = restSeconds?.coerceAtLeast(0) ?: lift.restSeconds,
                targetWeightKg = weightKg?.takeIf { it > 0.0 },
            )
        }
    }
}
