package com.sinura.personaltrainer.domain

/**
 * The preview in one sentence a stranger can read.
 *
 * Kept pure so a test can pin the words without composing the screen. The week under it
 * still carries the lifts; this is only the line that says *why* that week exists.
 */
object OnboardingPreviewCopy {
    fun headline(answers: OnboardingAnswers, plan: PlanBlueprint): String {
        if (answers.focus == TrainingFocus.CARDIO) {
            return "Cardio logging is ready. A lift week is not generated."
        }
        val bits = buildList {
            add("${plan.trainingDayCount} days")
            if (answers.emphasis != TrainingEmphasis.BALANCED) {
                add("${answers.emphasis.displayName} emphasis")
            }
            add(answers.goal.displayName)
            add(TrainingPlace.label(answers.resolvedPlaces()))
        }
        return bits.joinToString(" · ")
    }

    fun why(answers: OnboardingAnswers, plan: PlanBlueprint): String {
        if (answers.focus == TrainingFocus.CARDIO) return ""
        return plan.trace?.facts?.firstOrNull { it.name == "why" }?.value
            ?: SplitDerivation.why(answers)
    }

    fun dayLine(day: BlueprintDay, routine: BlueprintRoutine?): Pair<String, String> {
        val title = day.dayOfWeek.shortLabel()
        if (routine == null) return title to "Rest"
        val lifts = routine.lifts.take(3).joinToString(", ") { it.name }
        return "$title · ${routine.name}" to lifts
    }

    const val FOOTER =
        "Rest days stay rest days. Swap a lift, or change sets and reps, any time after you accept."

    const val FULL_WEEK_TITLE = "No rest day this week."
    const val FULL_WEEK_BODY =
        "Seven training days is allowed. Rest is a choice, not a cap."
    const val FULL_WEEK_CONFIRM_PLAN = "Use this plan"
    const val FULL_WEEK_CONFIRM_WEEK = "Use this week"
}
