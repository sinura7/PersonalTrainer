package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.util.AppClock

/** Controllable wall clock. Advance it; do not sleep. */
class FakeClock(initialNowMs: Long = 1_700_000_000_000L) : AppClock {
    var nowMs: Long = initialNowMs

    override fun nowMs(): Long = nowMs

    fun advance(ms: Long) {
        require(ms >= 0) { "clocks do not run backwards via advance(); assign nowMs" }
        nowMs += ms
    }
}
