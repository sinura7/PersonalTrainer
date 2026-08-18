package com.sinura.personaltrainer.timer

import com.sinura.personaltrainer.domain.RestTimerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RestTimerStore {
    private val snapshotState = MutableStateFlow(RestTimerSnapshot())
    val snapshot: StateFlow<RestTimerSnapshot> = snapshotState.asStateFlow()

    fun current(): RestTimerSnapshot = snapshotState.value

    fun start(totalSeconds: Int, sessionId: String?, nowElapsedRealtime: Long) {
        val safe = totalSeconds.coerceAtLeast(1)
        snapshotState.value = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = nowElapsedRealtime + safe * 1000L,
            totalSeconds = safe,
            sessionId = sessionId,
        )
    }

    fun adjust(deltaSeconds: Int, nowElapsedRealtime: Long) {
        val current = snapshotState.value
        if (!current.running && deltaSeconds <= 0) return
        val remaining = current.remainingSeconds(nowElapsedRealtime)
        val next = (remaining + deltaSeconds).coerceAtLeast(0)
        if (next == 0) {
            clear()
            return
        }
        snapshotState.value = RestTimerSnapshot(
            running = true,
            endsAtElapsedRealtime = nowElapsedRealtime + next * 1000L,
            totalSeconds = maxOf(current.totalSeconds, next),
            sessionId = current.sessionId,
        )
    }

    fun clear() {
        snapshotState.value = RestTimerSnapshot()
    }
}
