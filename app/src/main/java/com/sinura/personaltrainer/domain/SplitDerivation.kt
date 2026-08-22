package com.sinura.personaltrainer.domain

/**
 * Picks the split so the lifter does not have to.
 *
 * [WeeklySchedulePlanner.resolveSplit] already does this, but it reasons from routines the
 * lifter has already built — which at setup is an empty list, and would land everyone on the
 * same fallback. This reasons from the three answers that actually determine it.
 *
 * The rule underneath: **frequency first, then recovery.** How many days you train decides how
 * finely the week can be divided, and experience decides how much of that division you can
 * recover from. Goal only breaks ties, because a split is a scheduling structure and not a
 * training philosophy — nobody has ever failed to gain muscle because they ran upper/lower.
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
            answers.place == TrainingPlace.BODYWEIGHT_ONLY && days >= 4 -> SplitStyle.UPPER_LOWER
            // Two or three sessions cannot cover the body in parts. Splitting them would mean
            // training chest once every ten days, which is how people spend a year on a
            // program that never worked.
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
}
