package com.sinura.personaltrainer.domain

/**
 * What the RPE chips mean, in gym English.
 *
 * Five equal values, 6–10. Optional. Warm-up is not an RPE value — that
 * chip lives outside this track. A recommendation may outline a chip; it
 * never selects one.
 */
object RpeCopy {
    const val OPTIONAL = "Optional."
    const val LABEL = "RPE · OPTIONAL"
    const val HELPER = "6 = four reps left · 10 = max"
    const val HELPER_DISMISS = "Got it"
    const val WARMUP_REASON = "Warm-up"
    /** Ends of the 6–10 track. Only the ends: every value already has a spoken meaning. */
    const val EASY_END = "Easy"
    const val MAX_END = "Max effort"
    const val HELP_TITLE = "Effort (RPE)"
    const val HELP_INTRO =
        "Optional. Choose how hard your working set felt. Tap the selected value again or Clear to remove it."
    val VALUES: IntRange = 6..10

    /** The help sheet body: one line per value, in gym English. */
    fun helpBody(): String = HELP_INTRO + "\n\n" + VALUES.joinToString("\n") { "$it · ${meaning(it)}" }

    fun blurb(lastRpe: Int?): String {
        val history = lastRpe?.let { "Last time RPE $it. " }.orEmpty()
        return history + OPTIONAL
    }

    fun recommended(lastRpe: Int?): Int? = lastRpe?.takeIf { it in VALUES }

    /**
     * How many reps are still in the tank at this number.
     *
     * Ten is max, not "zero reps left" — that phrasing sounds like a
     * failed set. Six is four left. The first-use helper quotes the ends.
     */
    fun meaning(value: Int): String? = when (value) {
        6 -> "four reps left"
        7 -> "three reps left"
        8 -> "two reps left"
        9 -> "one rep left"
        10 -> "max"
        else -> null
    }

    /**
     * TalkBack for one chip: `RPE 8, about two reps left, not selected`.
     *
     * Packet H: a recommendation outlines the chip without selecting it, so the
     * outline needs the word `recommended` — the non-colour channel (ADR-023).
     */
    fun spoken(value: Int, selected: Boolean, recommended: Boolean = false): String {
        val effort = when (val body = meaning(value)) {
            null -> ""
            "max" -> ", max"
            else -> ", about $body"
        }
        val state = if (selected) "selected" else "not selected"
        val outline = if (recommended && !selected) ", recommended" else ""
        return "RPE $value$effort, $state$outline"
    }

    fun helperSpoken(): String = "$HELPER. $HELPER_DISMISS."
}
