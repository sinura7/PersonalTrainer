package com.sinura.personaltrainer.domain

/**
 * The Settings door back into guided setup.
 *
 * Kept as words a test can pin, because "rebuild my plan" is the phrase someone would
 * expect to replace what they have — and it does not. [OnboardingApplier] adds a block
 * alongside; this is the sentence that says so before they tap.
 */
object PlanSetupCopy {
    const val CAPTION =
        "This adds a new block. It does not delete history. " +
            "Answer the setup questions again to generate a fresh week."

    const val ROW_TITLE = "Rebuild my plan"

    const val ROW_SUBTITLE = "Seven questions, then a preview before anything changes"
}
