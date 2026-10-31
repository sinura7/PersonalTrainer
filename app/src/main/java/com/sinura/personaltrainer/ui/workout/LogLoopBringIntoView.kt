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
     * Quiet puts the entry immediately after exercise-header, so the numerals are item 1.
     */
    fun editRevealIndex(restBarShown: Boolean = false): Int = if (restBarShown) 2 else 1

    /** v4 V4: when rest starts, the header and the inline RestBar under it come back into view. */
    fun restBarRevealIndex(): Int = 0
}
