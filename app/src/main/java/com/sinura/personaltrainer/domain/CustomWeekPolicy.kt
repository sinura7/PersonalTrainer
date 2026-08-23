package com.sinura.personaltrainer.domain

import java.time.DayOfWeek

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
    fun canConfirm(days: Map<DayOfWeek, List<CustomWeekLift>>): Boolean =
        days.values.any { it.isNotEmpty() }

    fun trainingDayCount(days: Map<DayOfWeek, List<CustomWeekLift>>): Int =
        days.count { it.value.isNotEmpty() }.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)

    fun routineName(day: DayOfWeek): String =
        day.name.lowercase().replaceFirstChar { it.titlecase() }

    /**
     * Preferred days from the questionnaire stay marked even with zero lifts.
     * Confirm still needs at least one lift somewhere.
     */
    fun dayMark(
        day: DayOfWeek,
        filled: Set<DayOfWeek>,
        preferred: Set<DayOfWeek>,
    ): CustomWeekDayMark = when {
        day in filled -> CustomWeekDayMark.FILLED
        day in preferred -> CustomWeekDayMark.PREFERRED
        else -> CustomWeekDayMark.EMPTY
    }

    /** Week start, unless a preferred weekday exists — then the first of those in week order. */
    fun initialSelectedDay(weekStart: DayOfWeek, preferred: Set<DayOfWeek>): DayOfWeek {
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
                targetSets = sets?.coerceAtLeast(1) ?: lift.targetSets,
                targetReps = reps?.coerceAtLeast(1) ?: lift.targetReps,
                restSeconds = restSeconds?.coerceAtLeast(0) ?: lift.restSeconds,
                targetWeightKg = weightKg?.takeIf { it > 0.0 },
            )
        }
    }
}
