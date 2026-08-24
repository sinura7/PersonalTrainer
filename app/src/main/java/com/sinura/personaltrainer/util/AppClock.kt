package com.sinura.personaltrainer.util

/**
 * Wall-clock milliseconds. Production uses [SystemClock]. Tests use a fake so
 * week boundaries, staleness, and rest math do not depend on the host clock.
 *
 * This is the P1.1 test seam. Phase 5 replaces civil-time call sites with
 * injected ports that do not import `java.time` into shared-target types.
 */
fun interface AppClock {
    fun nowMs(): Long

    companion object {
        val System: AppClock = AppClock { java.lang.System.currentTimeMillis() }
    }
}
