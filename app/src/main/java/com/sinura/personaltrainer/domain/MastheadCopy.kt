package com.sinura.personaltrainer.domain

/**
 * The one line Home exists to say.
 *
 * Home used to open with the app's own name under a greeting. A product's face states today's
 * answer; its name is on the launcher icon. So this is the answer — what today is, in the
 * words a lifter would use, and with the lift count when the app actually knows it rather
 * than a plausible-looking guess.
 *
 * Notably absent: a "workout in progress" state. The live session bar owns live, everywhere
 * in the app, and a masthead that switched to describing the session would be a second answer
 * to "where is my workout" on the one screen that already had three.
 */
object MastheadCopy {

    /**
     * @param day today's slot from the derived week, or null when the week is empty.
     * @param loggedToday whether a session was finished today. Outranks the plan: once you
     * have trained, "PUSH DAY" is a statement about a thing you already did.
     * @param liftCount how many lifts the day's routine holds, or null when it cannot be
     * resolved. Null drops the count rather than inventing one.
     */
    fun headline(
        day: SuggestedTrainingDay?,
        loggedToday: Boolean,
        liftCount: Int?,
        hasPlan: Boolean = true,
    ): String {
        if (loggedToday) return "TRAINED TODAY"
        // Before the plan question, because the week derivation ALWAYS returns seven days and
        // fills every unpinned one with a rest day. So a brand-new install — no slots, no
        // routines, nothing — produced a non-null day whose isRest was true, and the largest
        // type on the first screen a new user ever sees read REST DAY. The app opened by
        // telling them not to train. The `day == null` branch below could never fire.
        if (!hasPlan) return "READY TO TRAIN"
        if (day == null) return "READY TO TRAIN"
        if (day.isRest) return "REST DAY"
        val noun = nounFor(day.focusKind)
        if (liftCount == null || liftCount <= 0) return noun
        return "$noun · $liftCount ${if (liftCount == 1) "LIFT" else "LIFTS"}"
    }

    private fun nounFor(kind: SessionFocusKind): String = when (kind) {
        SessionFocusKind.UPPER -> "UPPER DAY"
        SessionFocusKind.LOWER -> "LOWER BODY DAY"
        SessionFocusKind.PUSH -> "PUSH DAY"
        SessionFocusKind.PULL -> "PULL DAY"
        SessionFocusKind.LEGS -> "LEG DAY"
        SessionFocusKind.FULL_BODY -> "FULL BODY DAY"
        SessionFocusKind.RECOVERY -> "RECOVERY DAY"
    }
}

/**
 * One line saying why today's session is worth doing.
 *
 * Prefers the coach when the coach is talking about *this* session — a reason that names the
 * muscle you are about to train reads as coaching, and the same reason next to an unrelated
 * session reads as the app pattern-matching. Everything else falls back to the plan's own
 * reason, which is at worst "Pinned to your week": true, unexciting, and never wrong.
 */
fun nextSessionReason(
    day: SuggestedTrainingDay?,
    recommendations: List<TrainingRecommendation>,
): String? {
    if (day == null || day.isRest) return null
    val matching = recommendations.firstOrNull { rec ->
        rec.actionMuscle != null && rec.actionMuscle in musclesOf(day.focusKind)
    }
    return matching?.reason ?: day.reason.takeIf { it.isNotBlank() }
}

private fun musclesOf(kind: SessionFocusKind): Set<CanonicalMuscle> = when (kind) {
    SessionFocusKind.PUSH -> setOf(CanonicalMuscle.CHEST, CanonicalMuscle.SHOULDERS, CanonicalMuscle.TRICEPS)
    SessionFocusKind.PULL -> setOf(CanonicalMuscle.BACK, CanonicalMuscle.BICEPS)
    SessionFocusKind.UPPER -> setOf(
        CanonicalMuscle.CHEST, CanonicalMuscle.SHOULDERS, CanonicalMuscle.TRICEPS,
        CanonicalMuscle.BACK, CanonicalMuscle.BICEPS,
    )
    SessionFocusKind.LOWER, SessionFocusKind.LEGS -> setOf(
        CanonicalMuscle.QUADRICEPS, CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.GLUTES, CanonicalMuscle.CALVES,
    )
    SessionFocusKind.FULL_BODY, SessionFocusKind.RECOVERY -> CanonicalMuscle.mapped.toSet()
}
