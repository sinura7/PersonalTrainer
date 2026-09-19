package com.sinura.personaltrainer.domain

/**
 * Session resolved is not the same as this lift being safe to log.
 *
 * The live log used to enable Log as soon as the workout row existed, so a
 * fast launch could write a transient 0 × 5 that was not the planned target.
 * Prefill (plan / last time / coach) must finish or degrade first. A dirty
 * draft is the lifter's own numbers and may log without waiting.
 */
enum class LiftEntryReadiness {
    /** No selected lift (empty free workout, or the row has not been chosen). */
    NONE,

    /** This lift is selected; plan/history/coach has not answered yet. */
    RESOLVING,

    /** Prefill completed; the draft is the planned or suggested load. */
    READY,

    /** Prefill failed; planned/manual numbers; suggestion is unavailable. */
    DEGRADED,
    ;

    fun allowsCommit(): Boolean = this == READY || this == DEGRADED
}
