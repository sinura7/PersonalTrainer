package com.sinura.personaltrainer.domain

/** Where the live draft's effort value came from (Temper redesign R1, lane B). */
enum class EffortSource {
    /** No effort on a working set, or effort not applicable (warm-up / hold). */
    NONE,

    /** Suggested from the last logged working set of this lift (session first, else prior). */
    PREFILL_LAST,

    /** Copied from an explicit coach Apply (Next card, Tempo Use, add-a-set seed). */
    COACH,

    /** The lifter picked or cleared a chip. */
    USER,
    ;

    companion object {
        /** Legacy checkpoints and SavedState lifts without [effortSource] stored. */
        fun decodeStored(name: String?, rpe: Int?): EffortSource {
            if (name != null) {
                return runCatching { valueOf(name) }.getOrDefault(NONE)
            }
            return if (rpe != null) USER else NONE
        }
    }
}
