package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.util.JvmTime

/**
 * [TimePort] whose wall clock moves only when the test says so ([advance]). [FrozenTime] never
 * moves and [ControllableTimePort] moves only elapsed-realtime; a test about what a second of
 * wall clock does to a screen needs this one. Elapsed-realtime still goes through [JvmTime], so
 * rest deadlines must not use this [nowMillis].
 */
class SteppingTime(
    @Volatile private var nowMs: Long,
    private val zoneId: String = "UTC",
) : TimePort by JvmTime {
    override fun nowMillis(): Long = nowMs

    override fun defaultZoneId(): String = zoneId

    override fun captureNow(zoneId: String): CapturedCivilTime = capture(nowMs, zoneId)

    fun advance(ms: Long) {
        nowMs += ms
    }
}
