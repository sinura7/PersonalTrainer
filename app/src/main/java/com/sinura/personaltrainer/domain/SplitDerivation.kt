package com.sinura.personaltrainer.domain

/**
 * Picks the split so the lifter does not have to.
 *
 * [WeeklySchedulePlanner.resolveSplit] already does this, but it reasons from routines the
 * lifter has already built — which at setup is an empty list, and would land everyone on the
 * same fallback. This reasons from the three answers that actually determine it.
 *
 * The rule underneath: **frequency first, then recovery.** Muscle protein synthesis after a
 * session typically settles within ~48 hours in novices, so each muscle wants to be trained
 * about two to three times a week (ACSM 2009; Schoenfeld, Ogborn & Krieger 2016 frequency
 * meta-analysis). How many days you train decides how finely the week can be divided, and
 * experience decides how much of that division you can recover from. Goal only breaks ties
 * at high frequency — a split is a scheduling structure, not a training philosophy.
 */
object SplitDerivation {
    fun forAnswers(answers: OnboardingAnswers): SplitStyle {
        val days = answers.daysPerWeek.coerceIn(
            SchedulePreferences.MIN_DAYS,
            SchedulePreferences.MAX_DAYS,
        )
        return when {
            // You cannot build six distinct pushing lifts out of a living room. Push/pull/legs
            // divides the body finely enough that each session needs real depth behind it, and
            // a bodyweight catalog does not have that depth in any one direction — the sessions
            // would come out short, or padded with a fourth push-up variant.
            answers.resolvedPlaces() == setOf(TrainingPlace.BODYWEIGHT_ONLY) && days >= 4 ->
                SplitStyle.UPPER_LOWER
            // One, two or three sessions cannot cover the body in parts. Splitting them would
            // mean training chest once every ten days, which is how people spend a year on a
            // program that never worked. Full body at this frequency is ~2–3 hits per muscle.
            days <= 3 -> SplitStyle.FULL_BODY

            // A new lifter gets upper/lower at any frequency. Push/pull/legs at five days is
            // a lot of separate sessions to learn at once, and the extra division buys nothing
            // when the working weights are still light enough to recover from easily.
            answers.trainingAge == TrainingAge.NEW -> SplitStyle.UPPER_LOWER

            days == 4 -> SplitStyle.UPPER_LOWER

            // Five or six days, and enough training behind them to use it. Strength and
            // athletic both want fewer, bigger sessions: heavier work and unilateral work
            // recover worse across a six-way split than upper/lower does.
            answers.goal == TrainingGoal.STRENGTH ||
                answers.goal == TrainingGoal.ATHLETIC -> SplitStyle.UPPER_LOWER
            else -> SplitStyle.PUSH_PULL_LEGS
        }
    }

    fun reasonCodes(answers: OnboardingAnswers): List<String> {
        val days = answers.daysPerWeek.coerceIn(
            SchedulePreferences.MIN_DAYS,
            SchedulePreferences.MAX_DAYS,
        )
        return buildList {
            when {
                answers.resolvedPlaces() == setOf(TrainingPlace.BODYWEIGHT_ONLY) && days >= 4 ->
                    add("BW_NO_PPL")
                days <= 3 -> add("FREQ_LOW_FULL_BODY")
                answers.trainingAge == TrainingAge.NEW -> add("NOVICE_NO_PPL")
                days == 4 -> add("FOUR_DAY_UPPER_LOWER")
                answers.goal == TrainingGoal.STRENGTH ||
                    answers.goal == TrainingGoal.ATHLETIC -> add("STRENGTH_UPPER_LOWER")
                else -> add("HIGH_FREQ_PPL")
            }
        }
    }

    fun alternatives(answers: OnboardingAnswers): List<String> {
        val chosen = forAnswers(answers)
        return listOf(
            SplitStyle.FULL_BODY,
            SplitStyle.UPPER_LOWER,
            SplitStyle.PUSH_PULL_LEGS,
        ).filter { it != chosen }.map { it.name }
    }

    fun why(answers: OnboardingAnswers): String {
        val days = answers.daysPerWeek.coerceIn(
            SchedulePreferences.MIN_DAYS,
            SchedulePreferences.MAX_DAYS,
        )
        return when {
            answers.focus == TrainingFocus.CARDIO ->
                "Cardio logging does not invent a lift week."
            answers.resolvedPlaces() == setOf(TrainingPlace.BODYWEIGHT_ONLY) && days >= 4 ->
                "Bodyweight kit is not deep enough for push/pull/legs, so the week is upper/lower."
            days <= 3 ->
                "At $days days, every session is full body so each muscle is trained two to three times a week."
            answers.trainingAge == TrainingAge.NEW ->
                "New lifters run upper/lower, not a six-way split, while the lifts are still being learned."
            days == 4 ->
                "Four days is two upper and two lower sessions — about 48 hours between the same pattern."
            answers.goal == TrainingGoal.STRENGTH ||
                answers.goal == TrainingGoal.ATHLETIC ->
                "Heavier and athletic work recovers better on upper/lower than on push/pull/legs."
            else ->
                "Five or more days can rotate push, pull, and legs without hitting the same pattern twice in a row."
        }
    }
}
