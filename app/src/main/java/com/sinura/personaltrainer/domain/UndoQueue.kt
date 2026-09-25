package com.sinura.personaltrainer.domain

/**
 * Packet G: undo is a short LIFO queue, not one volatile slot.
 *
 * Two rapid deletes used to clobber each other — the second delete expired the first offer,
 * so only the latest delete could ever come back. Tokens now stack: undoing the latest
 * reveals the next offer underneath.
 *
 * Pure list ops so the ordering and cap are unit-testable without a database. The workout
 * floor's `FloorUndoOffers` holds the actual snapshots and the SavedState mirror.
 */
object UndoQueue {
    /** How many undo tokens survive. Oldest drops silently when a newer destructive lands. */
    const val MAX_DEPTH = 5

    /** Newest last. Drops the oldest when the stack is already full. */
    fun <T> push(stack: List<T>, item: T): List<T> = (stack + item).takeLast(MAX_DEPTH)

    /** Removes the newest token; anything underneath is revealed, not expired. */
    fun <T> pop(stack: List<T>): List<T> =
        if (stack.isEmpty()) stack else stack.dropLast(1)
}

/** Which cheap destructive an undo token reverses. */
enum class UndoKind {
    DELETED_SET,
    REMOVED_LIFT,
}

/**
 * Packet G: the undo dwell honours the accessibility timeout.
 *
 * The base dwell stays 6 s (callers pass [Motion.STATUS_DWELL_MS]); when the platform reports
 * a longer recommended timeout (TalkBack / switch access), the offer lives at least that
 * long. Reduced motion never collapses this — the offer is time, not animation.
 *
 * The base arrives as a parameter because `domain` is the bottom of the graph and may not
 * reach out to `ui.theme` for the constant.
 */
object UndoDwell {
    fun dwellMs(baseMs: Long, recommendedTimeoutMs: Long?): Long {
        if (recommendedTimeoutMs == null) return baseMs
        return maxOf(baseMs, recommendedTimeoutMs.coerceAtLeast(0L))
    }
}
