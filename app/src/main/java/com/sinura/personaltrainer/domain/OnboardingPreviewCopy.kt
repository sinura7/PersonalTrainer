package com.sinura.personaltrainer.domain

/**
 * The preview in one sentence a stranger can read.
 *
 * Kept pure so a test can pin the words without composing the screen. The week under it
 * still carries the lifts; this is only the line that says *why* that week exists.
 */
object OnboardingPreviewCopy {
    fun headline(answers: OnboardingAnswers, plan: PlanBlueprint): String {
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

    fun dayLine(day: BlueprintDay, routine: BlueprintRoutine?): Pair<String, String> {
        val title = day.dayOfWeek.shortLabel()
        if (routine == null) return title to "Rest"
        val lifts = routine.lifts.take(3).joinToString(", ") { it.name }
        return "$title · ${routine.name}" to lifts
    }

    const val FOOTER =
        "Rest days stay rest days. Swap a lift, or change sets and reps, any time after you accept."
}
