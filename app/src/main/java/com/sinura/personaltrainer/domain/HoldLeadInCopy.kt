package com.sinura.personaltrainer.domain

/**
 * The Settings row for GET READY before a hold (P2b, owner decision of 29 September 2026).
 * Holds only: the set stopwatch is started by hand, when the lifter is ready.
 */
object HoldLeadInCopy {
    const val TITLE = "Get ready before a hold"
    const val CAPTION = "A plank or a hang counts GET READY first, so you can get into position. " +
        "The set clock you start yourself has no countdown."

    fun choice(seconds: Int): String = "$seconds s"
}
