package com.sinura.personaltrainer.testutil

/**
 * Stand-in for `SystemClock.elapsedRealtime()` in tests that drive
 * [com.sinura.personaltrainer.timer.RestTimerStore] directly.
 *
 * Rest deadlines are elapsed-realtime, not civil time. Advancing this clock
 * is how a test expires a rest without waiting or touching AlarmManager.
 */
class ControllableElapsedRealtime(initialMs: Long = 0L) {
    var nowMs: Long = initialMs

    fun advance(ms: Long) {
        require(ms >= 0) { "elapsed realtime does not run backwards via advance()" }
        nowMs += ms
    }
}
