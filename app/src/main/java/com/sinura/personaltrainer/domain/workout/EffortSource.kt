package com.sinura.personaltrainer.domain.workout

/** Where the live set's effort came from. Lane B (policy) writes these; Lane A only renders them. */
enum class EffortSource {
    NONE,
    PREFILL_LAST,
    COACH,
    USER,
}
