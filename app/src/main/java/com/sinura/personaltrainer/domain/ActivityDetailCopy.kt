package com.sinura.personaltrainer.domain

/**
 * Gym-floor words for a completed activity's receipt.
 *
 * History detail and the post-finish celebration share one screen. Celebration
 * leads with the number; history leads with identity and a Back control.
 * Weights go through [SetCopy] and [WeightUnit] — never raw `weightKg`.
 */
object ActivityDetailCopy {
    const val DONE = "Done"
    const val BACK = "Back"
    const val MISSING_TITLE = "Session not found"
    const val MISSING_BODY = "That activity is no longer on this phone."
    const val COMPLETE_CARDIO = "Cardio complete"
    const val COMPLETE_MIXED = "Session complete"
    const val COMPLETE_STRENGTH = "Workout complete"
    const val STRENGTH_CAPTION = "Strength."
    const val CARDIO_CAPTION = "Run, ride, or walk."
    const val MIXED_CAPTION = "Lifts and a run in the same session."
    const val STRENGTH = "Strength"
    const val CARDIO = "Cardio"

    const val VOLT = DONE

    fun kicker(celebration: Boolean, session: ActivitySession): String {
        if (!celebration) return modalityCaption(session)
        return when {
            session.isCardioOnly -> COMPLETE_CARDIO
            session.isMixed -> COMPLETE_MIXED
            else -> COMPLETE_STRENGTH
        }
    }

    fun modalityCaption(session: ActivitySession): String = when {
        session.isMixed -> MIXED_CAPTION
        session.isCardioOnly -> CARDIO_CAPTION
        else -> STRENGTH_CAPTION
    }

    fun missingAction(celebration: Boolean): String = if (celebration) DONE else BACK

    fun setLine(block: StrengthBlock, set: StrengthSet, unit: WeightUnit): String {
        val work = SetCopy.setLine(
            weightKg = set.weightKg,
            reps = set.reps,
            loadClass = LoadClass.of(block.loadType),
            unit = unit,
        )
        return if (set.isWarmup) "Warm-up · $work" else work
    }

    fun cardioSubtitle(
        block: CardioBlock,
        distance: DistanceUnit = DistanceUnit.KM,
    ): String {
        val minutes = ((block.elapsedSeconds + 30) / 60).toInt()
        val distanceKm = block.distanceMeters?.let { it / 1_000.0 }
        return ComposerCopy.cardioLineSubtitle(minutes, distanceKm, block.indoor, distance)
    }

    fun volumeLabel(volumeKg: Double, unit: WeightUnit): String =
        WeightConverter.formatVolumeNumber(volumeKg, unit)

    /**
     * Receipt duration. Cardio minutes win. A noon-stamped backdate
     * (start == end, no cardio clock) is not a zero-minute workout — the
     * tile is omitted when this returns 0.
     */
    fun receiptDurationMinutes(session: ActivitySession, cardioMinutes: Int): Int {
        if (cardioMinutes > 0) return cardioMinutes
        val end = session.performedEnd?.instantMillis ?: return 0
        val elapsed = end - session.performedStart.instantMillis
        if (elapsed <= 0L) return 0
        return ((elapsed + 30_000L) / 60_000L).toInt()
    }
}
