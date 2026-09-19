package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.util.JvmTime

/**
 * [TimePort] whose elapsed-realtime is a [ControllableElapsedRealtime].
 * Civil time still follows [JvmTime].
 */
class ControllableTimePort(
    private val elapsed: ControllableElapsedRealtime = ControllableElapsedRealtime(),
    private val delegate: TimePort = JvmTime,
) : TimePort by delegate {
    override fun elapsedRealtimeMillis(): Long = elapsed.nowMs

    fun advance(ms: Long) {
        elapsed.advance(ms)
    }
}
