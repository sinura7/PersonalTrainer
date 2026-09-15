package com.sinura.personaltrainer.domain

/**
 * How long to rest before the next working set.
 *
 * [AddDefaults] stamps rest once when the lift is added. That number is a
 * starting guess, not a live opinion. This is the live opinion: reason,
 * how the lift is loaded, and how many reps just happened. It is a starting
 * duration only — it does not write back onto the routine, and a manual
 * change on the dock still wins for the rest of the session.
 */
object RestPrescription {
    const val HEAVY_REPS = 5
    const val MODERATE_REPS = 8
    const val HIGH_REPS = 12

    const val HEAVY_SECONDS = 150
    const val MODERATE_SECONDS = 120
    const val STANDARD_SECONDS = 90
    const val SHORT_SECONDS = 60
    const val GRIND_EXTRA = 30
    const val MAX_SECONDS = 180

    fun seconds(reasonCode: String, loadType: LoadType?, reps: Int): Int {
        val safeReps = reps.coerceAtLeast(0)
        val base = when {
            safeReps <= HEAVY_REPS -> HEAVY_SECONDS
            safeReps <= MODERATE_REPS -> MODERATE_SECONDS
            safeReps <= HIGH_REPS -> STANDARD_SECONDS
            else -> SHORT_SECONDS
        }
        val byLoad = when (LoadClass.of(loadType)) {
            LoadClass.BODYWEIGHT, LoadClass.BODYWEIGHT_ASSISTED ->
                (base - GRIND_EXTRA).coerceAtLeast(SHORT_SECONDS)
            else -> base
        }
        val adjusted = when (reasonCode) {
            SetMicroRecCalculator.TOP_SET,
            SetMicroRecCalculator.RPE_HOLD,
            SetMicroRecCalculator.FAILED_DROP,
            SetMicroRecCalculator.SKIP_RPE_DROP,
            SetMicroRecCalculator.BW_DROP_REP,
            -> byLoad + GRIND_EXTRA
            SetMicroRecCalculator.IN_TANK,
            SetMicroRecCalculator.BW_ADD_REP,
            -> (byLoad - GRIND_EXTRA).coerceAtLeast(SHORT_SECONDS)
            else -> byLoad
        }
        return adjusted.coerceIn(RestTimerPreferences.MIN_SECONDS, MAX_SECONDS)
    }
}
