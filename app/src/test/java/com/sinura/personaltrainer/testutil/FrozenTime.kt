package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.util.JvmTime
import java.time.ZoneId

/**
 * [TimePort] that freezes [nowMillis] and [defaultZoneId]. Capture, DST, and
 * elapsed-realtime still go through [JvmTime]. Rest deadlines must not use this
 * [nowMillis].
 */
class FrozenTime(
    private val nowMs: Long,
    private val zoneId: String = ZoneId.systemDefault().id,
) : TimePort by JvmTime {
    override fun nowMillis(): Long = nowMs

    override fun defaultZoneId(): String = zoneId

    override fun captureNow(zoneId: String): CapturedCivilTime = capture(nowMs, zoneId)
}
