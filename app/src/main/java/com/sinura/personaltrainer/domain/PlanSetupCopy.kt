package com.sinura.personaltrainer.domain

/**
 * The Settings door back into guided setup.
 *
 * Kept as words a test can pin, because "rebuild my plan" is the phrase someone would
 * expect to replace what they have — and it does not. [OnboardingApplier] adds a block
 * alongside; the title and caption say so before they tap.
 */
object PlanSetupCopy {
    const val CAPTION =
        "This adds a new block. It does not delete history. " +
            "Answer the setup questions again to generate a fresh week."

    const val ROW_TITLE = "Add a new block"

    const val ROW_SUBTITLE = "Seven questions, then a preview before anything changes"

    /** Settings → Week generator → Generate a week asks this before it writes anything. */
    const val GENERATE_TITLE = "Generate a new week?"

    const val GENERATE_BODY =
        "This makes a fresh set of routines from your answers and puts them on your week. " +
            "Your current routines stay in your list, and your training block and history " +
            "stay as they are."

    const val GENERATE_CONFIRM = "Generate"
}
