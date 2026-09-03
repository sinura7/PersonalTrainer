package com.sinura.personaltrainer.ui.workout

/**
 * After a set lands, the weight and reps wells stay on screen.
 *
 * The requester used to sit on the logged-sets panel below those wells, so
 * every log scrolled the next entry off the top of a gym-floor phone.
 */
object LogLoopBringIntoView {
    const val ANCHOR_TAG = WorkoutTestTags.SET_ENTRY

    fun shouldBringIntoView(previousSetCount: Int, nextSetCount: Int): Boolean =
        previousSetCount in 0 until nextSetCount
}
