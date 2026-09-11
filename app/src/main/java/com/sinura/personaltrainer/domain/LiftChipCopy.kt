package com.sinura.personaltrainer.domain

/**
 * What a live lift chip says: how far you are through the sets, and rest.
 *
 * W-06: the switcher used to be a still and a name. [setProgress] is `2/5`.
 * [restClock] is the prescribed clock, or remaining time while rest is
 * running on this lift. Idle rest is a label, never a live-looking clock
 * (G-05).
 */
data class LiftChipMarks(
    val setProgress: String,
    val restClock: String?,
    val restLive: Boolean,
)

object LiftChipCopy {
    const val SETS = "Sets"
    const val REST = "Rest"
    const val REST_REMAINING = "Rest remaining"

    fun marks(
        workingLogged: Int,
        targetSets: Int,
        restSeconds: Int,
        restRunningOnThisLift: Boolean,
        remainingSeconds: Int,
    ): LiftChipMarks {
        val restLive = restRunningOnThisLift
        val restClock = when {
            restLive -> RestTimer.formatClock(remainingSeconds.coerceAtLeast(0))
            restSeconds > 0 -> RestTimer.formatClock(restSeconds)
            else -> null
        }
        return LiftChipMarks(
            setProgress = SessionOrderCopy.filledCount(
                workingLogged.coerceAtLeast(0),
                targetSets,
            ),
            restClock = restClock,
            restLive = restLive && restClock != null,
        )
    }

    fun spoken(
        name: String,
        setProgress: String,
        restClock: String?,
        restLive: Boolean,
    ): String = buildString {
        append(name)
        append(". ")
        append(SETS)
        append(" ")
        append(setProgress)
        if (!restClock.isNullOrBlank()) {
            append(". ")
            append(if (restLive) REST_REMAINING else REST)
            append(" ")
            append(restClock)
        }
    }
}
