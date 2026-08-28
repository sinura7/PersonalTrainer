package com.sinura.personaltrainer.domain

/**
 * How hard a generated session should be, given the questionnaire.
 *
 * [AddDefaults] sizes a *hand-added* lift from load type and role alone — the
 * right guess when the lifter is building a session themselves. A generated
 * week also knows training age, goal, and how many days the week carries, and
 * those change the dose:
 *
 * - Novices grow on 1–3 sets and learn compounds in an 8–12 range
 *   (ACSM 2009 resistance-training position stand).
 * - Hypertrophy tracks weekly hard sets more than session length; ~10+ sets
 *   per muscle per week is the landmark (Schoenfeld, Ogborn & Krieger 2017,
 *   *J Sports Sci*). Fewer days therefore keep 4 sets on the main lifts so
 *   the week still lands in range; six or seven days cut accessories to 2
 *   sets so the same week does not bury the lifter in junk volume.
 * - Strength work for trained lifters stays heavy and long-rested (≈1–6 RM,
 *   2–5 min) on the openers; accessories stay higher-rep.
 *
 * This is a closed local table (ADR-008). It is not a paper lookup, not a
 * medical prescription, and the lifter can edit every number after accept.
 */
data class SessionDose(
    val age: TrainingAge,
    val goal: TrainingGoal,
    val daysPerWeek: Int,
) {
    companion object {
        fun from(answers: OnboardingAnswers): SessionDose = SessionDose(
            age = answers.trainingAge,
            goal = answers.goal,
            daysPerWeek = answers.daysPerWeek,
        )
    }
}

object ProgramDose {
    fun forExercise(
        exercise: Exercise,
        role: LiftRole,
        dose: SessionDose,
    ): TargetDefaults = apply(
        table = AddDefaults.forExercise(exercise, role),
        isCompound = AddDefaults.isCompound(exercise),
        role = role,
        dose = dose,
    )

    fun apply(
        table: TargetDefaults,
        isCompound: Boolean,
        role: LiftRole,
        dose: SessionDose,
    ): TargetDefaults {
        val days = dose.daysPerWeek.coerceIn(
            SchedulePreferences.MIN_DAYS,
            SchedulePreferences.MAX_DAYS,
        )
        return TargetDefaults(
            sets = setsFor(role, dose.age, days),
            reps = repsFor(table.reps, isCompound, role, dose),
            restSeconds = restFor(table.restSeconds, isCompound, role, dose),
        )
    }

    /**
     * Novices stay at 3 sets. Trained lifters add a set when the week is
     * short, and drop accessory sets when the week is long.
     */
    internal fun setsFor(role: LiftRole, age: TrainingAge, days: Int): Int {
        if (age == TrainingAge.NEW) return NOVICE_SETS
        return when {
            role == LiftRole.PRIMARY && days <= SHORT_WEEK_DAYS -> SHORT_WEEK_PRIMARY_SETS
            role == LiftRole.ACCESSORY && days >= LONG_WEEK_DAYS -> LONG_WEEK_ACCESSORY_SETS
            else -> STANDARD_SETS
        }
    }

    private fun repsFor(
        tableReps: Int,
        isCompound: Boolean,
        role: LiftRole,
        dose: SessionDose,
    ): Int {
        if (dose.age == TrainingAge.NEW && isCompound) {
            return tableReps.coerceIn(NOVICE_COMPOUND_REPS_MIN, NOVICE_COMPOUND_REPS_MAX)
        }
        return when (dose.goal) {
            TrainingGoal.STRENGTH -> if (role == LiftRole.PRIMARY && isCompound) {
                STRENGTH_PRIMARY_REPS
            } else {
                tableReps.coerceAtLeast(STRENGTH_ACCESSORY_REPS_FLOOR)
            }
            TrainingGoal.HYPERTROPHY, TrainingGoal.RESILIENCE -> if (role == LiftRole.PRIMARY && isCompound) {
                HYPERTROPHY_PRIMARY_REPS
            } else {
                tableReps.coerceAtLeast(HYPERTROPHY_ACCESSORY_REPS_FLOOR)
            }
            TrainingGoal.ATHLETIC -> if (role == LiftRole.PRIMARY && isCompound) {
                ATHLETIC_PRIMARY_REPS
            } else {
                tableReps.coerceAtLeast(ATHLETIC_ACCESSORY_REPS_FLOOR)
            }
            TrainingGoal.GENERAL -> tableReps
        }
    }

    private fun restFor(
        tableRest: Int,
        isCompound: Boolean,
        role: LiftRole,
        dose: SessionDose,
    ): Int {
        if (dose.age == TrainingAge.NEW && isCompound) {
            return if (role == LiftRole.PRIMARY) NOVICE_PRIMARY_REST else NOVICE_ACCESSORY_REST
        }
        return when (dose.goal) {
            TrainingGoal.STRENGTH -> if (role == LiftRole.PRIMARY && isCompound) {
                STRENGTH_PRIMARY_REST
            } else {
                STRENGTH_ACCESSORY_REST
            }
            TrainingGoal.HYPERTROPHY, TrainingGoal.RESILIENCE -> if (role == LiftRole.PRIMARY && isCompound) {
                HYPERTROPHY_PRIMARY_REST
            } else {
                tableRest.coerceAtMost(HYPERTROPHY_ACCESSORY_REST_CAP)
            }
            TrainingGoal.ATHLETIC -> if (role == LiftRole.PRIMARY && isCompound) {
                ATHLETIC_PRIMARY_REST
            } else {
                ATHLETIC_ACCESSORY_REST
            }
            TrainingGoal.GENERAL -> tableRest
        }
    }

    private const val NOVICE_SETS = 3
    private const val STANDARD_SETS = 3
    private const val SHORT_WEEK_DAYS = 2
    private const val SHORT_WEEK_PRIMARY_SETS = 4
    private const val LONG_WEEK_DAYS = 6
    private const val LONG_WEEK_ACCESSORY_SETS = 2

    private const val NOVICE_COMPOUND_REPS_MIN = 8
    private const val NOVICE_COMPOUND_REPS_MAX = 10
    private const val NOVICE_PRIMARY_REST = 120
    private const val NOVICE_ACCESSORY_REST = 90

    private const val STRENGTH_PRIMARY_REPS = 5
    private const val STRENGTH_ACCESSORY_REPS_FLOOR = 8
    private const val STRENGTH_PRIMARY_REST = 180
    private const val STRENGTH_ACCESSORY_REST = 90

    private const val HYPERTROPHY_PRIMARY_REPS = 8
    private const val HYPERTROPHY_ACCESSORY_REPS_FLOOR = 10
    private const val HYPERTROPHY_PRIMARY_REST = 90
    private const val HYPERTROPHY_ACCESSORY_REST_CAP = 75

    private const val ATHLETIC_PRIMARY_REPS = 5
    private const val ATHLETIC_ACCESSORY_REPS_FLOOR = 8
    private const val ATHLETIC_PRIMARY_REST = 150
    private const val ATHLETIC_ACCESSORY_REST = 90
}
