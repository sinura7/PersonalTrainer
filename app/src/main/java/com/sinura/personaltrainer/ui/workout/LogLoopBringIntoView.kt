package com.sinura.personaltrainer.ui.workout

/**
 * After a set lands, the weight and reps wells stay on screen.
 *
 * The requester used to sit on the logged-sets panel below those wells, so
 * every log scrolled the next entry off the top of a gym-floor phone.
 */
object LogLoopBringIntoView {
    /** Packet C: after resume or a lift switch, the one card is at list offset 0. */
    fun entryListIndex(): Int = 0

    /**
     * ADR-027: an edit scrolls the entry itself under the header, not the identity. The
     * floor's list is exercise-header, stats, then the entry, so the numerals are item 2.
     */
    fun editRevealIndex(): Int = 2
}
