package com.sinura.personaltrainer.domain

/**
 * The empty-week recovery that is not Suggest and is not a Settings rebuild.
 *
 * Suggest invents a heat-shaped week from the routines on the phone. Rebuild from Settings
 * runs the questionnaire again and *creates* routines. This path pins the routines they
 * already have to the days they already picked. The words have to say that before they tap,
 * because "use my answers again" is easy to hear as "start over".
 */
object WeekTwoCopy {
    const val VOLT = "Use my answers again"

    const val CAPTION =
        "Pins the routines you already have to the same days. " +
            "Does not add routines or delete history."

    const val MATCH_FAILED = "Couldn’t match your routines. Rebuild from Settings."

    const val PROPOSAL_REASON = "Pinned from your answers."
}
