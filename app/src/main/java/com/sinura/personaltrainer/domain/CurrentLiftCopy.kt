package com.sinura.personaltrainer.domain

/**
 * Packet C current-lift card and lift switcher copy.
 *
 * The floor shows one lift. TalkBack must still say it is current, how far
 * through the session, and working-set progress. Pictures stay decorative.
 */
object CurrentLiftCopy {
    const val CURRENT = "Current"
    const val SWITCHER_TITLE = "Lifts"
    const val SESSION_NOTES = "Session notes"
    const val SWAP = "Swap lift…"
    const val REMOVE = "Remove lift"
    const val SKIP = "Skip for now"

    /**
     * Packet G: swap/remove stay visible on a logged lift, disabled with this reason.
     * The lift is part of what happened — take its sets out first, or skip it for now.
     */
    const val EDIT_BLOCKED_REASON = "Delete its sets first"

    /** Skip asked to go nowhere: every other lift already met its target. */
    const val SKIP_NOWHERE = "Every other lift is already finished."

    fun liftOrdinal(number: Int, total: Int): String = "Lift $number/$total"

    fun workingProgress(workingLogged: Int, targetSets: Int): String =
        SessionOrderCopy.filledCount(workingLogged.coerceAtLeast(0), targetSets)

    fun secondaryLine(equipmentLabel: String, meaning: WeightMeaning): String {
        val kit = equipmentLabel.trim()
        return when (meaning) {
            WeightMeaning.LIFTED -> kit.ifBlank { meaning.fieldLabel }
            WeightMeaning.NONE -> kit.ifBlank { "Bodyweight" }
            WeightMeaning.ADDED -> if (kit.isBlank()) meaning.fieldLabel else "$kit · ${meaning.fieldLabel}"
            WeightMeaning.ASSISTANCE -> if (kit.isBlank()) meaning.fieldLabel else "$kit · ${meaning.fieldLabel}"
        }
    }

    fun cardSpoken(
        name: String,
        number: Int,
        total: Int,
        workingLogged: Int,
        targetSets: Int,
        equipmentLabel: String,
        meaning: WeightMeaning,
    ): String = buildString {
        append(CURRENT)
        append(". ")
        append(name)
        append(". ")
        append(SessionOrderCopy.liftIndex(number, total))
        append(". Working ")
        append(workingProgress(workingLogged, targetSets))
        val secondary = secondaryLine(equipmentLabel, meaning)
        if (secondary.isNotBlank()) {
            append(". ")
            append(secondary)
        }
    }

    fun switcherSpoken(
        name: String,
        number: Int,
        total: Int,
        workingLogged: Int,
        targetSets: Int,
        restClock: String?,
        restLive: Boolean,
        current: Boolean,
    ): String = buildString {
        if (current) {
            append(CURRENT)
            append(". ")
        }
        append(name)
        append(". ")
        append(SessionOrderCopy.liftIndex(number, total))
        append(". Working ")
        append(workingProgress(workingLogged, targetSets))
        if (!restClock.isNullOrBlank()) {
            append(". ")
            append(if (restLive) LiftChipCopy.REST_REMAINING else LiftChipCopy.REST)
            append(" ")
            append(restClock)
        }
    }
}
