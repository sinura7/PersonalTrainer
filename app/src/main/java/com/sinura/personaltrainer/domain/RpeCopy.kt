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
    /**
     * The track's heading. "RPE" alone was unfamiliar (design audit D12); effort is the word
     * a lifter uses, and "optional" says the set logs without it. The help keeps the term.
     */
    const val LABEL = "Effort · optional"
    const val HELP_SPOKEN = "Effort help"
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
     * Under the track once a value is chosen, in place of its Easy / Max effort ends: what the
     * choice means, so it can be read without opening the help (D12). The same row, so it
     * costs no height on every set.
     */
    fun selectedLine(value: Int): String? = when (val body = meaning(value)) {
        null -> null
        "max" -> "RPE $value · max effort"
        else -> "RPE $value · about $body"
    }

    /**
     * TalkBack for one chip: `RPE 8, about two reps left`.
     *
     * The chip is a radio button, so its Selected state already says "selected" or "not
     * selected"; saying it in the words as well read it out twice (W1a).
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
        val outline = if (recommended && !selected) ", recommended" else ""
        return "RPE $value$effort$outline"
    }

    fun helperSpoken(): String = "$HELPER. $HELPER_DISMISS."
}
