package com.sinura.personaltrainer.ui.workout

/**
 * After a set lands, the weight and reps wells stay on screen.
 *
 * The requester used to sit on the logged-sets panel below those wells, so
 * every log scrolled the next entry off the top of a gym-floor phone.
 */
object LogLoopBringIntoView {
    const val ANCHOR_TAG = WorkoutTestTags.SET_ENTRY
    /** Packet C: after resume or a lift switch, the one card is at list offset 0. */
    fun entryListIndex(): Int = 0

    fun shouldScrollEntryToTop(
        previousLiftId: String?,
        nextLiftId: String?,
    ): Boolean = nextLiftId != null && previousLiftId != nextLiftId
}
